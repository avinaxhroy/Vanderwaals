package me.avinas.vanderwaals.worker

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.os.BatteryManager
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import me.avinas.vanderwaals.data.entity.UserPreferences
import me.avinas.vanderwaals.data.repository.PreferenceRepository
import me.avinas.vanderwaals.data.repository.WallpaperRepository
import me.avinas.vanderwaals.domain.usecase.SelectNextWallpaperUseCase
import me.avinas.vanderwaals.network.NetworkStateTracker
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker for automatic wallpaper rotation.
 * 
 * Executes wallpaper changes based on user's auto-change frequency:
 * - Every unlock: Triggered by screen unlock broadcast
 * - Hourly: Scheduled via PeriodicWorkRequest (1 hour interval)
 * - Daily: Scheduled via OneTimeWorkRequest at specific time
 * - Never: Worker not scheduled
 * 
 * Workflow:
 * 1. Get next wallpaper from ranked queue
 * 2. Download wallpaper image if not cached
 * 3. Apply to lock/home screen per user settings
 * 4. Record application in feedback history
 * 5. Update notification with current wallpaper
 * 
 * Work constraints:
 * - NetworkType.CONNECTED (for downloading new wallpapers)
 * - Runs as expedited work for immediate changes
 * 
 * Integrates with Paperize's existing:
 * - WallpaperAlarmScheduler for exact timing
 * - HomeWallpaperService and LockWallpaperService
 * - WallpaperUtil for image processing
 * 
 * @see me.avinas.vanderwaals.domain.usecase.SelectNextWallpaperUseCase
 * @see me.avinas.vanderwaals.feature.wallpaper.wallpaper_service.HomeWallpaperService
 * @see me.avinas.vanderwaals.feature.wallpaper.wallpaper_service.LockWallpaperService
 */
@HiltWorker
class WallpaperChangeWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val selectNextWallpaperUseCase: SelectNextWallpaperUseCase,
    private val queueNextWallpapersUseCase: me.avinas.vanderwaals.domain.usecase.QueueNextWallpapersUseCase,
    private val wallpaperRepository: WallpaperRepository,
    private val preferenceRepository: PreferenceRepository,
    private val engagementTracker: me.avinas.vanderwaals.domain.usecase.UserEngagementTracker,
    private val processImplicitFeedbackUseCase: me.avinas.vanderwaals.domain.usecase.ProcessImplicitFeedbackUseCase,
    private val findCachedWallpaperUseCase: me.avinas.vanderwaals.domain.usecase.FindCachedWallpaperUseCase,
    private val networkStateTracker: NetworkStateTracker
) : CoroutineWorker(appContext, workerParams) {
    
    companion object {
        private const val TAG = "WallpaperChangeWorker"
        
        /**
         * Unique work name for wallpaper change.
         */
        const val WORK_NAME = "wallpaper_change_work"
        
        /**
         * Input data key for target screen (home, lock, or both).
         */
        const val KEY_TARGET_SCREEN = "target_screen"
        
        /**
         * Input data key for mode (vanderwaals or paperize).
         */
        const val KEY_MODE = "mode"
        
        /**
         * Output data key for applied wallpaper ID.
         */
        const val KEY_WALLPAPER_ID = "wallpaper_id"
        
        /**
         * Screen target values.
         */
        const val TARGET_HOME = "home"
        const val TARGET_LOCK = "lock"
        const val TARGET_BOTH = "both"
        const val TARGET_BOTH_DIFFERENT = "both_different"
        
        /**
         * Mode values.
         */
        const val MODE_VANDERWAALS = "vanderwaals"
        const val MODE_PAPERIZE = "paperize"
        
        /**
         * Key for indicating if wallpaper change is manual (vs auto-change).
         * Used to determine if implicit feedback should be processed.
         */
        const val KEY_IS_MANUAL_CHANGE = "is_manual_change"
        
        /**
         * Key for indicating if this is a network retry after connectivity was restored.
         * When true, the worker should prioritize downloading fresh wallpapers over using cache.
         */
        const val KEY_IS_NETWORK_RETRY = "is_network_retry"
        
        /**
         * Unique work name for retry when network becomes available.
         */
        const val RETRY_WORK_NAME = "wallpaper_retry_when_online"
        
        /**
         * Battery threshold below which background work should be skipped.
         * When battery is below 20%, we skip non-essential background work.
         */
        private const val BATTERY_THRESHOLD_PERCENT = 20
    }
    
    override suspend fun doWork(): Result {
        return try {
            // BATTERY CHECK: Skip background work if battery is critically low
            // This respects user's battery life while still allowing manual changes
            val isManualChange = inputData.getBoolean(KEY_IS_MANUAL_CHANGE, false)
            if (!isManualChange && isBatteryLow()) {
                Log.d(TAG, "Skipping auto-change: Battery below ${BATTERY_THRESHOLD_PERCENT}%")
                // Return success to not retry immediately - next scheduled run will check again
                return Result.success(
                    workDataOf("skipped_reason" to "battery_low")
                )
            }
            
            // CRITICAL FIX: Always load current Apply To setting from DataStore
            // This ensures we respect the latest user preference, even if WorkManager
            // data is stale or was scheduled before the user changed settings
            val settingsDataStore = me.avinas.vanderwaals.data.datastore.SettingsDataStore(applicationContext)
            val currentSettings = settingsDataStore.settings.first()
            
            // Map DataStore setting to worker constant
            val actualTargetScreen = when (currentSettings.applyTo) {
                "lock_screen" -> TARGET_LOCK
                "home_screen" -> TARGET_HOME
                "both" -> TARGET_BOTH
                "both_different" -> TARGET_BOTH_DIFFERENT
                else -> TARGET_BOTH
            }
            
            // Use the actual current setting instead of potentially stale inputData
            val targetScreen = actualTargetScreen
            val mode = inputData.getString(KEY_MODE) ?: MODE_VANDERWAALS
            
            Log.d(TAG, "Starting wallpaper change - target: $targetScreen, mode: $mode")
            Log.d(TAG, "User's current 'Apply To' setting: ${currentSettings.applyTo}")
            
            // Check if Vanderwaals mode is active
            if (mode == MODE_VANDERWAALS) {
                // Vanderwaals algorithm mode
                applyVanderwaalsWallpaper(targetScreen)
            } else {
                // Paperize folder mode (delegate to existing services)
                Result.success()
            }
            
        } catch (e: Exception) {
            // Use ErrorHandler for consistent error handling
            val (error, action) = me.avinas.vanderwaals.core.ErrorHandler.handleWorkerError(
                exception = e,
                context = "WallpaperChange",
                attemptCount = runAttemptCount,
                metadata = mapOf(
                    "mode" to (inputData.getString(KEY_MODE) ?: "unknown"),
                    "is_manual" to inputData.getBoolean(KEY_IS_MANUAL_CHANGE, false)
                )
            )
            
            when (action) {
                is me.avinas.vanderwaals.core.ErrorRecoveryAction.Retry -> Result.retry()
                is me.avinas.vanderwaals.core.ErrorRecoveryAction.RetryWithBackoff -> {
                    // WorkManager doesn't support custom backoff, but log for debugging
                    Log.d(TAG, "Retrying with backoff delay: ${action.delayMs}ms")
                    Result.retry()
                }
                is me.avinas.vanderwaals.core.ErrorRecoveryAction.Fail -> {
                    val errorData = me.avinas.vanderwaals.core.ErrorHandler.createWorkDataForError(
                        error = error,
                        attemptCount = runAttemptCount,
                        additionalData = mapOf("failure_reason" to action.reason)
                    )
                    Result.failure(androidx.work.workDataOf(*errorData.map { it.key to it.value }.toTypedArray()))
                }
                is me.avinas.vanderwaals.core.ErrorRecoveryAction.SkipAndContinue -> {
                    Result.success(workDataOf("skipped_reason" to action.reason))
                }
                is me.avinas.vanderwaals.core.ErrorRecoveryAction.FallbackToCache -> {
                    // Already handled in download logic
                    Result.retry()
                }
            }
        }
    }
    
    /**
     * Applies wallpaper using Vanderwaals algorithm.
     */
    private suspend fun applyVanderwaalsWallpaper(targetScreen: String): Result {
        // Step 1: Get user preferences, or create defaults if not initialized
        var preferences = preferenceRepository.getUserPreferences().first()
        if (preferences == null) {
            Log.w(TAG, "User preferences not initialized, creating defaults")
            // Auto-initialize with default preferences to handle race conditions
            // This can happen when the worker runs before database transaction completes
            val defaultPreferences = UserPreferences.createDefault()
            preferenceRepository.insertUserPreferences(defaultPreferences)
            
            // Verify the insert actually worked by querying the database multiple times
            // Use separate variable to track DB state (not the local defaultPreferences object)
            var savedPreferences: UserPreferences? = null
            var retries = 0
            while (savedPreferences == null && retries < 5) {
                kotlinx.coroutines.delay(500L) // Wait before each retry
                savedPreferences = preferenceRepository.getUserPreferences().first()
                retries++
                if (savedPreferences != null) {
                    Log.d(TAG, "User preferences successfully initialized after ${retries - 1} retries")
                }
            }
            
            if (savedPreferences == null) {
                Log.e(TAG, "Failed to initialize user preferences after $retries retries - data not persisted to database")
                return Result.retry()
            }
            
            preferences = savedPreferences
        }
        
        // Handle "Both But Different" mode - apply different wallpapers to home and lock screen
        if (targetScreen == TARGET_BOTH_DIFFERENT) {
            return applyBothDifferentWallpapers()
        }
        
        // Step 2: Select next wallpaper using algorithm
        val wallpaperResult = selectNextWallpaperUseCase()
        
        if (wallpaperResult.isFailure) {
            val error = wallpaperResult.exceptionOrNull()
            Log.e(TAG, "Failed to select wallpaper: ${error?.message}")
            
            // Skip if no wallpapers available (don't retry)
            return if (error?.message?.contains("No wallpapers available") == true) {
                Result.success() // Skip this cycle
            } else {
                Result.retry()
            }
        }
        
        val wallpaper = wallpaperResult.getOrNull()!!
        
        // Step 3: Download wallpaper if not cached (with offline fallback)
        var downloadResult = wallpaperRepository.downloadWallpaper(wallpaper)
        var wallpaperFile: File? = null
        var selectedWallpaper = wallpaper
        var usedCachedFallback = false
        
        if (downloadResult.isFailure) {
            val downloadError = downloadResult.exceptionOrNull()
            Log.w(TAG, "Failed to download wallpaper ${wallpaper.id}: ${downloadError?.message}")
            
            // CRITICAL: Mark that we're in offline mode so NetworkStateTracker knows
            // to trigger fresh downloads when connectivity is restored
            networkStateTracker.markAsOfflineMode()
            
            // OFFLINE FALLBACK: Try to find a different wallpaper that's already cached on disk
            Log.d(TAG, "Attempting offline fallback - searching for cached wallpapers...")
            
            val cachedWallpaperResult = findCachedWallpaperUseCase(excludeWallpaperId = wallpaper.id)
            
            if (cachedWallpaperResult != null) {
                val (cachedWallpaper, cachedFile) = cachedWallpaperResult
                Log.d(TAG, "Offline fallback successful - using cached wallpaper: ${cachedWallpaper.id}")
                selectedWallpaper = cachedWallpaper
                wallpaperFile = cachedFile
                usedCachedFallback = true
                
                // Schedule a retry when internet becomes available to download new wallpapers
                // This ensures we refresh the cache when connectivity is restored
                scheduleRetryWhenOnline(targetScreen)
            } else {
                // No cached wallpapers available - schedule retry when network is available
                Log.e(TAG, "No cached wallpapers available for offline fallback")
                scheduleRetryWhenOnline(targetScreen)
                return Result.success(
                    workDataOf("skipped_reason" to "no_cache_no_network")
                )
            }
        } else {
            wallpaperFile = downloadResult.getOrNull()!!
            // CRITICAL: Mark successful download so we know cache is fresh
            networkStateTracker.markSuccessfulDownload()
            
            // Cancel any pending retry work since we successfully downloaded
            cancelPendingRetryWork()
        }
        
        // Step 3.5: Process implicit feedback for previous wallpaper (ONLY if manual change)
        // Check if this is a manual change (triggered by "Change Now" button)
        val isManualChange = inputData.getBoolean(KEY_IS_MANUAL_CHANGE, false)
        
        if (isManualChange) {
            Log.d(TAG, "Manual change detected - processing implicit feedback for previous wallpaper")
            
            // Get previous active wallpaper and mark it as removed
            val previousHistory = wallpaperRepository.getHistory().first().firstOrNull { it.isActive() }
            
            if (previousHistory != null) {
                Log.d(TAG, "Previous wallpaper: ${previousHistory.wallpaperId}, applied at: ${previousHistory.appliedAt}")
                
                // Mark as removed at current time
                wallpaperRepository.markWallpaperRemoved(previousHistory.id, System.currentTimeMillis())
                
                // Get updated history entry with removedAt timestamp
                val updatedHistory = wallpaperRepository.getHistoryEntry(previousHistory.id)
                
                if (updatedHistory != null) {
                    // Process implicit feedback based on duration
                    val implicitResult = processImplicitFeedbackUseCase(updatedHistory)
                    
                    implicitResult.fold(
                        onSuccess = {
                            Log.d(TAG, "Implicit feedback processed successfully")
                        },
                        onFailure = { error ->
                            Log.w(TAG, "Failed to process implicit feedback: ${error.message}")
                            // Don't fail the worker if implicit feedback fails
                        }
                    )
                } else {
                    Log.w(TAG, "Could not retrieve updated history entry for implicit feedback")
                }
            } else {
                Log.d(TAG, "No previous active wallpaper found")
            }
        } else {
            Log.d(TAG, "Auto-change detected - skipping implicit feedback processing")
            
            // For auto-change, just mark previous wallpaper as removed without implicit feedback
            val previousHistory = wallpaperRepository.getHistory().first().firstOrNull { it.isActive() }
            if (previousHistory != null) {
                wallpaperRepository.markWallpaperRemoved(previousHistory.id, System.currentTimeMillis())
            }
        }
        
        // Step 4: Apply wallpaper to specified screen(s)
        val applied = applyWallpaperToScreen(wallpaperFile!!, targetScreen)
        
        if (!applied) {
            Log.e(TAG, "Failed to apply wallpaper")
            return Result.retry()
        }
        
        // Step 5: Record wallpaper application in history
        val historyId = wallpaperRepository.recordWallpaperApplied(selectedWallpaper)
        Log.d(TAG, "Applied wallpaper ${selectedWallpaper.id}, history ID: $historyId")
        
        // Step 6: Smart pre-download next wallpapers
        try {
            val queueResult = queueNextWallpapersUseCase()
            queueResult.fold(
                onSuccess = { count ->
                    Log.d(TAG, "Queued $count wallpapers for pre-download")
                },
                onFailure = { error ->
                    Log.w(TAG, "Failed to queue next wallpapers: ${error.message}")
                    // Don't fail the worker if queuing fails
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Exception during pre-download queue: ${e.message}")
            // Continue even if queuing fails
        }
        
        // Step 7: Return success with wallpaper ID
        return Result.success(
            workDataOf(KEY_WALLPAPER_ID to selectedWallpaper.id)
        )
    }
    
    /**
     * Applies two different wallpapers - one to home screen and one to lock screen.
     * 
     * This function selects two different wallpapers using the Vanderwaals algorithm
     * and applies them separately to home and lock screens.
     * 
     * @return Result indicating success or failure
     */
    private suspend fun applyBothDifferentWallpapers(): Result {
        Log.d(TAG, "Applying 'Both But Different' - selecting two different wallpapers")
        
        // Process implicit feedback for previous wallpapers (if manual change)
        val isManualChange = inputData.getBoolean(KEY_IS_MANUAL_CHANGE, false)
        if (isManualChange) {
            // Mark previous active wallpapers as removed
            val previousHistory = wallpaperRepository.getHistory().first().firstOrNull { it.isActive() }
            if (previousHistory != null) {
                wallpaperRepository.markWallpaperRemoved(previousHistory.id, System.currentTimeMillis())
                val updatedHistory = wallpaperRepository.getHistoryEntry(previousHistory.id)
                if (updatedHistory != null) {
                    processImplicitFeedbackUseCase(updatedHistory)
                }
            }
        } else {
            val previousHistory = wallpaperRepository.getHistory().first().firstOrNull { it.isActive() }
            if (previousHistory != null) {
                wallpaperRepository.markWallpaperRemoved(previousHistory.id, System.currentTimeMillis())
            }
        }
        
        // Step 1: Select first wallpaper for home screen
        val homeWallpaperResult = selectNextWallpaperUseCase()
        if (homeWallpaperResult.isFailure) {
            val error = homeWallpaperResult.exceptionOrNull()
            Log.e(TAG, "Failed to select home wallpaper: ${error?.message}")
            return if (error?.message?.contains("No wallpapers available") == true) {
                Result.success()
            } else {
                Result.retry()
            }
        }
        val homeWallpaper = homeWallpaperResult.getOrNull()!!
        
        // Step 2: Select second (different) wallpaper for lock screen
        val lockWallpaperResult = selectNextWallpaperUseCase(excludeWallpaperId = homeWallpaper.id)
        if (lockWallpaperResult.isFailure) {
            val error = lockWallpaperResult.exceptionOrNull()
            Log.e(TAG, "Failed to select lock wallpaper: ${error?.message}")
            // Fall back to using the same wallpaper for both if only one available
            Log.w(TAG, "Falling back to same wallpaper for both screens")
        }
        val lockWallpaper = lockWallpaperResult.getOrNull() ?: homeWallpaper
        
        Log.d(TAG, "Selected wallpapers - Home: ${homeWallpaper.id}, Lock: ${lockWallpaper.id}")
        
        // Step 3: Download home wallpaper (with offline fallback)
        var actualHomeWallpaper = homeWallpaper
        var homeWallpaperFile: File
        var usedCachedFallback = false
        
        val homeDownloadResult = wallpaperRepository.downloadWallpaper(homeWallpaper)
        if (homeDownloadResult.isFailure) {
            Log.w(TAG, "Failed to download home wallpaper: ${homeDownloadResult.exceptionOrNull()?.message}")
            
            // CRITICAL: Mark offline mode for network state tracking
            networkStateTracker.markAsOfflineMode()
            usedCachedFallback = true
            
            // OFFLINE FALLBACK for home wallpaper
            val cachedHomeResult = findCachedWallpaperUseCase(excludeWallpaperId = homeWallpaper.id)
            if (cachedHomeResult != null) {
                val (cachedWallpaper, cachedFile) = cachedHomeResult
                Log.d(TAG, "Offline fallback for home - using cached: ${cachedWallpaper.id}")
                actualHomeWallpaper = cachedWallpaper
                homeWallpaperFile = cachedFile
                
                // Schedule retry when online to refresh cache
                scheduleRetryWhenOnline(TARGET_BOTH_DIFFERENT)
            } else {
                Log.e(TAG, "No cached wallpapers available for home screen fallback")
                scheduleRetryWhenOnline(TARGET_BOTH_DIFFERENT)
                return Result.success(
                    workDataOf("skipped_reason" to "no_cache_no_network")
                )
            }
        } else {
            homeWallpaperFile = homeDownloadResult.getOrNull()!!
        }
        
        // Step 4: Download lock wallpaper (with offline fallback)
        var actualLockWallpaper = lockWallpaper
        var lockWallpaperFile: File
        
        if (lockWallpaper.id != homeWallpaper.id) {
            val lockDownloadResult = wallpaperRepository.downloadWallpaper(lockWallpaper)
            if (lockDownloadResult.isFailure) {
                Log.w(TAG, "Failed to download lock wallpaper: ${lockDownloadResult.exceptionOrNull()?.message}")
                
                // Mark offline mode if not already
                if (!usedCachedFallback) {
                    networkStateTracker.markAsOfflineMode()
                    usedCachedFallback = true
                }
                
                // OFFLINE FALLBACK for lock wallpaper
                val cachedLockResult = findCachedWallpaperUseCase(excludeWallpaperId = actualHomeWallpaper.id)
                if (cachedLockResult != null) {
                    val (cachedWallpaper, cachedFile) = cachedLockResult
                    Log.d(TAG, "Offline fallback for lock - using cached: ${cachedWallpaper.id}")
                    actualLockWallpaper = cachedWallpaper
                    lockWallpaperFile = cachedFile
                } else {
                    // Fall back to using same as home if no other cached available
                    Log.w(TAG, "No different cached wallpaper for lock, using same as home")
                    actualLockWallpaper = actualHomeWallpaper
                    lockWallpaperFile = homeWallpaperFile
                }
            } else {
                lockWallpaperFile = lockDownloadResult.getOrNull()!!
            }
        } else {
            lockWallpaperFile = homeWallpaperFile
        }
        
        // Mark successful download if no cache fallback was used
        if (!usedCachedFallback) {
            networkStateTracker.markSuccessfulDownload()
            cancelPendingRetryWork()
        }
        
        // Step 5: Apply wallpaper to home screen
        val homeApplied = applyWallpaperToScreen(homeWallpaperFile, TARGET_HOME)
        if (!homeApplied) {
            Log.e(TAG, "Failed to apply home wallpaper")
            return Result.retry()
        }
        
        // Step 6: Apply wallpaper to lock screen
        val lockApplied = applyWallpaperToScreen(lockWallpaperFile, TARGET_LOCK)
        if (!lockApplied) {
            Log.e(TAG, "Failed to apply lock wallpaper")
            return Result.retry()
        }
        
        // Step 7: Record both wallpaper applications in history
        val homeHistoryId = wallpaperRepository.recordWallpaperApplied(actualHomeWallpaper)
        Log.d(TAG, "Applied home wallpaper ${actualHomeWallpaper.id}, history ID: $homeHistoryId")
        
        if (actualLockWallpaper.id != actualHomeWallpaper.id) {
            val lockHistoryId = wallpaperRepository.recordWallpaperApplied(actualLockWallpaper)
            Log.d(TAG, "Applied lock wallpaper ${actualLockWallpaper.id}, history ID: $lockHistoryId")
        }
        
        // Step 8: Smart pre-download next wallpapers
        try {
            val queueResult = queueNextWallpapersUseCase()
            queueResult.fold(
                onSuccess = { count ->
                    Log.d(TAG, "Queued $count wallpapers for pre-download")
                },
                onFailure = { error ->
                    Log.w(TAG, "Failed to queue next wallpapers: ${error.message}")
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Exception during pre-download queue: ${e.message}")
        }
        
        // Return success with home wallpaper ID
        return Result.success(
            workDataOf(KEY_WALLPAPER_ID to actualHomeWallpaper.id)
        )
    }
    
    /**
     * Applies wallpaper file to the specified screen(s) with SmartCrop processing.
     * 
     * Uses a "try then verify" approach for live wallpaper detection:
     * 1. Attempt to apply the wallpaper
     * 2. Verify the change was successful
     * 3. If failed, check if live wallpaper is blocking
     * 
     * @param wallpaperFile File containing the wallpaper image
     * @param targetScreen Target screen: "home", "lock", or "both"
     * @return true if successfully applied, false otherwise
     */
    private suspend fun applyWallpaperToScreen(wallpaperFile: File, targetScreen: String): Boolean {
        var originalBitmap: android.graphics.Bitmap? = null
        var processedBitmap: android.graphics.Bitmap? = null
        
        return try {
            val wallpaperManager = WallpaperManager.getInstance(applicationContext)
            
            // Use BitmapManager for safe bitmap loading with OOM protection
            originalBitmap = me.avinas.vanderwaals.core.BitmapManager.loadBitmap(wallpaperFile)
            
            if (originalBitmap == null) {
                Log.e(TAG, "Failed to decode wallpaper file")
                return false
            }
            
            // CRITICAL: Use actual screen size for SmartCrop, not WallpaperManager's desired size
            // WallpaperManager.desiredMinimumWidth/Height returns dimensions for SCROLLING wallpapers
            // (e.g., 4800x2400 for a 1080x2400 screen). This causes preview/applied mismatch.
            // Instead, we crop to actual screen size and let WallpaperManager handle scrolling.
            val screenSize = me.avinas.vanderwaals.core.getDeviceScreenSize(applicationContext)
            
            // Apply SmartCrop to actual screen dimensions (matches preview)
            processedBitmap = me.avinas.vanderwaals.core.SmartCrop.smartCropBitmap(
                source = originalBitmap,
                targetWidth = screenSize.width,
                targetHeight = screenSize.height,
                mode = me.avinas.vanderwaals.core.SmartCrop.CropMode.AUTO
            )
            
            // CRITICAL FIX: Save the cropped bitmap to a file so preview can load the EXACT same image
            // This guarantees preview and applied wallpaper are pixel-perfect identical
            val croppedFile = File(wallpaperFile.parentFile, "${wallpaperFile.nameWithoutExtension}_cropped.jpg")
            try {
                croppedFile.outputStream().use { out ->
                    processedBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)
                }
                Log.d(TAG, "Saved cropped wallpaper to: ${croppedFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save cropped wallpaper", e)
                // Continue anyway - we still have the bitmap in memory
            }
            
            // Recycle original bitmap to save memory using BitmapManager
            if (processedBitmap !== originalBitmap) {
                me.avinas.vanderwaals.core.BitmapManager.recycleSafely(originalBitmap)
                originalBitmap = null // Clear reference
            }
            
            when (targetScreen) {
                TARGET_HOME -> {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        wallpaperManager.setBitmap(
                            processedBitmap,
                            null,
                            true,
                            WallpaperManager.FLAG_SYSTEM
                        )
                    } else {
                        wallpaperManager.setBitmap(processedBitmap)
                    }
                }
                TARGET_LOCK -> {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        wallpaperManager.setBitmap(
                            processedBitmap,
                            null,
                            true,
                            WallpaperManager.FLAG_LOCK
                        )
                    } else {
                        // On older devices, just set system wallpaper
                        wallpaperManager.setBitmap(processedBitmap)
                    }
                }
                TARGET_BOTH -> {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        // Set both home and lock screen
                        wallpaperManager.setBitmap(
                            processedBitmap,
                            null,
                            true,
                            WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                        )
                    } else {
                        wallpaperManager.setBitmap(processedBitmap)
                    }
                }
                else -> {
                    Log.e(TAG, "Invalid target screen: $targetScreen")
                    processedBitmap.recycle()
                    return false
                }
            }
            
            // Recycle processed bitmap after successful application
            me.avinas.vanderwaals.core.BitmapManager.recycleSafely(processedBitmap)
            processedBitmap = null // Clear reference
            
            // VERIFICATION: Check if wallpaper was actually applied
            // Live wallpapers silently ignore setBitmap() calls, so we need to verify
            // by checking if a live wallpaper is still active after our attempt
            val wallpaperInfo = wallpaperManager.wallpaperInfo
            if (wallpaperInfo != null) {
                // A live wallpaper is still active - our setBitmap was ignored
                val (isBlocking, serviceName) = me.avinas.vanderwaals.core.LiveWallpaperDetector.detectBlockingAfterFailure(applicationContext)
                if (isBlocking) {
                    Log.e(TAG, "Wallpaper change blocked by live wallpaper: $serviceName")
                    return false
                }
            }
            
            // Record wallpaper change for engagement tracking
            engagementTracker.recordWallpaperChange()
            
            Log.d(TAG, "Successfully applied wallpaper with SmartCrop processing")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error applying wallpaper", e)
            false
        } finally {
            // Ensure bitmaps are recycled even if exception occurs
            me.avinas.vanderwaals.core.BitmapManager.recycleSafely(originalBitmap)
            me.avinas.vanderwaals.core.BitmapManager.recycleSafely(processedBitmap)
        }
    }
    
    
    /**
     * Checks if the device battery is below the threshold for background work.
     * 
     * When battery is below 20%, we should avoid non-essential background work
     * to preserve user's battery life. Manual wallpaper changes are still allowed.
     * 
     * @return true if battery is below threshold, false otherwise
     */
    private fun isBatteryLow(): Boolean {
        return try {
            val batteryStatus = applicationContext.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            
            if (batteryStatus == null) {
                Log.w(TAG, "Could not get battery status, assuming battery is OK")
                return false
            }
            
            val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            
            if (level == -1 || scale == -1) {
                Log.w(TAG, "Invalid battery level data, assuming battery is OK")
                return false
            }
            
            val batteryPercent = (level * 100) / scale
            val isCharging = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == 
                BatteryManager.BATTERY_STATUS_CHARGING ||
                batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == 
                BatteryManager.BATTERY_STATUS_FULL
            
            // Don't skip if device is charging
            if (isCharging) {
                Log.d(TAG, "Device is charging, battery check passed")
                return false
            }
            
            val isLow = batteryPercent < BATTERY_THRESHOLD_PERCENT
            if (isLow) {
                Log.d(TAG, "Battery is low: $batteryPercent% (threshold: $BATTERY_THRESHOLD_PERCENT%)")
            }
            isLow
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking battery level", e)
            false // Assume battery is OK if we can't check
        }
    }
    
    /**
     * Schedules a one-time work request to run when internet becomes available.
     * 
     * This is used after offline fallback to ensure we eventually:
     * 1. Download new wallpapers to refresh the cache
     * 2. Apply a fresh wallpaper if we had to skip due to no cache/no network
     * 
     * The work has constraints:
     * - Network connectivity required
     * - Battery not low (will wait until charged if battery is critical)
     * - Delayed by 5 minutes to avoid immediate retries in unstable network conditions
     * 
     * @param targetScreen The target screen for wallpaper application
     */
    private fun scheduleRetryWhenOnline(targetScreen: String) {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            
            val inputData = workDataOf(
                KEY_TARGET_SCREEN to targetScreen,
                KEY_MODE to MODE_VANDERWAALS,
                KEY_IS_MANUAL_CHANGE to false,
                KEY_IS_NETWORK_RETRY to true // Mark this as a network retry
            )
            
            val retryWork = OneTimeWorkRequestBuilder<WallpaperChangeWorker>()
                .setConstraints(constraints)
                .setInputData(inputData)
                .setInitialDelay(1, TimeUnit.MINUTES) // Reduced delay - network is already available
                .build()
            
            WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                RETRY_WORK_NAME,
                ExistingWorkPolicy.REPLACE, // Replace any existing retry work
                retryWork
            )
            
            Log.d(TAG, "Scheduled retry work for when network becomes available (with 1 min delay)")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule retry work", e)
        }
    }
    
    /**
     * Cancels any pending retry work when a fresh download succeeds.
     * 
     * This prevents the retry work from running unnecessarily after
     * we've already downloaded fresh wallpapers successfully.
     */
    private fun cancelPendingRetryWork() {
        try {
            WorkManager.getInstance(applicationContext).cancelUniqueWork(RETRY_WORK_NAME)
            Log.d(TAG, "Cancelled pending retry work - fresh download succeeded")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel retry work", e)
        }
    }
}
