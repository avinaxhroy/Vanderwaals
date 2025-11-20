package me.avinas.vanderwaals.worker

import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import me.avinas.vanderwaals.data.entity.UserPreferences
import me.avinas.vanderwaals.data.repository.PreferenceRepository
import me.avinas.vanderwaals.data.repository.WallpaperRepository
import me.avinas.vanderwaals.domain.usecase.SelectNextWallpaperUseCase
import java.io.File

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
    private val processImplicitFeedbackUseCase: me.avinas.vanderwaals.domain.usecase.ProcessImplicitFeedbackUseCase
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
    }
    
    override suspend fun doWork(): Result {
        return try {
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
            Log.e(TAG, "Error changing wallpaper", e)
            
            // Retry once on failure
            if (runAttemptCount == 0) {
                Result.retry()
            } else {
                Result.failure()
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
        
        // Step 3: Download wallpaper if not cached
        val downloadResult = wallpaperRepository.downloadWallpaper(wallpaper)
        
        if (downloadResult.isFailure) {
            Log.e(TAG, "Failed to download wallpaper: ${downloadResult.exceptionOrNull()?.message}")
            return Result.retry()
        }
        
        val wallpaperFile = downloadResult.getOrNull()!!
        
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
        val applied = applyWallpaperToScreen(wallpaperFile, targetScreen)
        
        if (!applied) {
            Log.e(TAG, "Failed to apply wallpaper")
            return Result.retry()
        }
        
        // Step 5: Record wallpaper application in history
        val historyId = wallpaperRepository.recordWallpaperApplied(wallpaper)
        Log.d(TAG, "Applied wallpaper ${wallpaper.id}, history ID: $historyId")
        
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
            workDataOf(KEY_WALLPAPER_ID to wallpaper.id)
        )
    }
    
    /**
     * Applies wallpaper file to the specified screen(s) with SmartCrop processing.
     * 
     * @param wallpaperFile File containing the wallpaper image
     * @param targetScreen Target screen: "home", "lock", or "both"
     * @return true if successfully applied, false otherwise
     */
    private suspend fun applyWallpaperToScreen(wallpaperFile: File, targetScreen: String): Boolean {
        return try {
            // CRITICAL: Check for live wallpaper BEFORE attempting to apply
            // Live wallpaper services (Glance, Dynamic Wallpaper) prevent setBitmap() from working
            if (me.avinas.vanderwaals.core.LiveWallpaperDetector.isLiveWallpaperActive(applicationContext)) {
                val (isBlocking, serviceName) = me.avinas.vanderwaals.core.LiveWallpaperDetector.isKnownBlockingService(applicationContext)
                val packageName = me.avinas.vanderwaals.core.LiveWallpaperDetector.getLiveWallpaperPackageName(applicationContext)
                
                Log.e(TAG, "Live wallpaper detected: $serviceName ($packageName) - blocking wallpaper change")
                
                // Return false to indicate failure - UI will detect this and show dialog
                return false
            }
            
            val wallpaperManager = WallpaperManager.getInstance(applicationContext)
            val originalBitmap = BitmapFactory.decodeFile(wallpaperFile.absolutePath)


            
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
            val processedBitmap = me.avinas.vanderwaals.core.SmartCrop.smartCropBitmap(
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
            
            // Recycle original bitmap to save memory
            if (processedBitmap !== originalBitmap) {
                originalBitmap.recycle()
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
            
            processedBitmap.recycle()
            
            // Record wallpaper change for engagement tracking
            engagementTracker.recordWallpaperChange()
            
            Log.d(TAG, "Successfully applied wallpaper with SmartCrop processing")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error applying wallpaper", e)
            false
        }
    }
}
