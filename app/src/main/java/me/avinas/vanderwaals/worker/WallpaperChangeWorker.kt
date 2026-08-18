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
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.avinas.vanderwaals.data.entity.UserPreferences
import me.avinas.vanderwaals.data.repository.PreferenceRepository
import me.avinas.vanderwaals.data.repository.WallpaperRepository
import me.avinas.vanderwaals.domain.usecase.SelectNextWallpaperUseCase
import me.avinas.vanderwaals.network.NetworkStateTracker
import java.io.File
import java.util.concurrent.TimeUnit

@HiltWorker
class WallpaperChangeWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val selectNextWallpaperUseCase: SelectNextWallpaperUseCase,
    private val wallpaperRepository: WallpaperRepository,
    private val preferenceRepository: PreferenceRepository,
    private val engagementTracker: me.avinas.vanderwaals.domain.usecase.UserEngagementTracker,
    private val processImplicitFeedbackUseCase: me.avinas.vanderwaals.domain.usecase.ProcessImplicitFeedbackUseCase,
    private val findCachedWallpaperUseCase: me.avinas.vanderwaals.domain.usecase.FindCachedWallpaperUseCase,
    private val networkStateTracker: NetworkStateTracker,
    private val nextWallpaperCacheManager: me.avinas.vanderwaals.domain.NextWallpaperCacheManager,
    private val wallpaperApplicator: WallpaperApplicator,
    private val settingsDataStore: me.avinas.vanderwaals.data.datastore.SettingsDataStore
) : CoroutineWorker(appContext, workerParams) {
    
    companion object {
        private const val TAG = "WallpaperChangeWorker"
        
        const val WORK_NAME = "wallpaper_change_work"
        
        const val KEY_TARGET_SCREEN = "target_screen"
        
        const val KEY_MODE = "mode"
        
        const val KEY_WALLPAPER_ID = "wallpaper_id"
        
        const val TARGET_HOME = "home"
        const val TARGET_LOCK = "lock"
        const val TARGET_BOTH = "both"
        const val TARGET_BOTH_DIFFERENT = "both_different"
        
        const val MODE_VANDERWAALS = "vanderwaals"
        const val MODE_PAPERIZE = "paperize"
        
        /**
         * Manual changes process implicit feedback; auto-changes do not.
         */
        const val KEY_IS_MANUAL_CHANGE = "is_manual_change"
        
        /**
         * When true (network retry after connectivity restored), prefer downloading
         * a fresh wallpaper over using the cache.
         */
        const val KEY_IS_NETWORK_RETRY = "is_network_retry"
        
        const val RETRY_WORK_NAME = "wallpaper_retry_when_online"
        
        /**
         * Used after a dislike to apply a diversity-selected wallpaper.
         */
        const val KEY_SELECTED_WALLPAPER_ID = "selected_wallpaper_id"
        
        /**
         * Battery threshold below which background work is skipped (non-essential).
         */
        private const val BATTERY_THRESHOLD_PERCENT = 20
        
        const val KEY_PROGRESS_STATE = "progress_state"
        
        const val PROGRESS_FINDING = "finding"
        
        const val PROGRESS_APPLYING = "applying"
    }
    
    override suspend fun doWork(): Result {
        return try {
            val isManualChange = inputData.getBoolean(KEY_IS_MANUAL_CHANGE, false)
            if (!isManualChange && isBatteryLow()) {
                Log.d(TAG, "Skipping auto-change: Battery below ${BATTERY_THRESHOLD_PERCENT}%")
                return Result.success(
                    workDataOf("skipped_reason" to "battery_low")
                )
            }
            
            val currentSettings = settingsDataStore.settings.first()
            val actualTargetScreen = when (currentSettings.applyTo) {
                "lock_screen" -> TARGET_LOCK
                "home_screen" -> TARGET_HOME
                "both" -> TARGET_BOTH
                "both_different" -> TARGET_BOTH_DIFFERENT
                else -> TARGET_BOTH
            }
            
            val targetScreen = actualTargetScreen
            val mode = inputData.getString(KEY_MODE) ?: MODE_VANDERWAALS
            
            Log.d(TAG, "Starting wallpaper change - target: $targetScreen, mode: $mode")
            
            if (mode == MODE_VANDERWAALS) {
                applyVanderwaalsWallpaper(targetScreen)
            } else {
                Result.success()
            }
            
        } catch (e: Exception) {
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
                    Result.retry()
                }
            }
        }
    }
    
    private suspend fun applyVanderwaalsWallpaper(targetScreen: String): Result {
        setProgress(workDataOf(KEY_PROGRESS_STATE to PROGRESS_FINDING))

        var preferences = preferenceRepository.getUserPreferences().first()
        if (preferences == null) {
            Log.w(TAG, "User preferences not initialized, creating defaults")
            val defaultPreferences = UserPreferences.createDefault()
            preferenceRepository.insertUserPreferences(defaultPreferences)
            
            var savedPreferences: UserPreferences? = null
            var retries = 0
            while (savedPreferences == null && retries < 5) {
                kotlinx.coroutines.delay(500L)
                savedPreferences = preferenceRepository.getUserPreferences().first()
                retries++
                if (savedPreferences != null) {
                    Log.d(TAG, "User preferences initialized after ${retries - 1} retries")
                }
            }
            
            if (savedPreferences == null) {
                Log.e(TAG, "Failed to initialize user preferences after $retries retries")
                return Result.retry()
            }
            
            preferences = savedPreferences
        }
        
        if (targetScreen == TARGET_BOTH_DIFFERENT) {
            return applyBothDifferentWallpapers()
        }
        
        val preSelectedWallpaperId = inputData.getString(KEY_SELECTED_WALLPAPER_ID)
        
        val wallpaper = if (preSelectedWallpaperId != null) {
            Log.d(TAG, "Using pre-selected wallpaper: $preSelectedWallpaperId")
            val allWallpapers = wallpaperRepository.getAllWallpapers().first()
            val preSelectedWallpaper = allWallpapers.find { it.id == preSelectedWallpaperId }
            
            if (preSelectedWallpaper != null) {
                preSelectedWallpaper
            } else {
                Log.w(TAG, "Pre-selected wallpaper $preSelectedWallpaperId not found, falling back to algorithm")
                val result = nextWallpaperCacheManager.getNextWallpaper()
                if (result.isFailure) {
                    val error = result.exceptionOrNull()
                    Log.e(TAG, "Failed to select wallpaper: ${error?.message}")
                    return if (error?.message?.contains("No wallpapers available") == true) {
                        Result.success()
                    } else {
                        Result.retry()
                    }
                }
                result.getOrNull()!!
            }
        } else {
            val likedIds = preferences.likedWallpaperIds
            val existingHistory = wallpaperRepository.getHistory().first()
            
            if (existingHistory.isEmpty() && likedIds.isNotEmpty()) {
                val firstLikedId = likedIds.first()
                val allWallpapers = wallpaperRepository.getAllWallpapers().first()
                val firstLikedWallpaper = allWallpapers.find { it.id == firstLikedId }
                
                if (firstLikedWallpaper != null) {
                    firstLikedWallpaper
                } else {
                    val result = selectNextWallpaperUseCase()
                    if (result.isFailure) {
                        val error = result.exceptionOrNull()
                        Log.e(TAG, "Failed to select wallpaper: ${error?.message}")
                        return if (error?.message?.contains("No wallpapers available") == true) {
                            Result.success()
                        } else {
                            Result.retry()
                        }
                    }
                    result.getOrNull()!!
                }
            } else {
                val wallpaperResult = nextWallpaperCacheManager.getNextWallpaper()
                
                if (wallpaperResult.isFailure) {
                    val error = wallpaperResult.exceptionOrNull()
                    Log.e(TAG, "Failed to select wallpaper: ${error?.message}")
                    
                    return if (error?.message?.contains("No wallpapers available") == true) {
                        Result.success()
                    } else {
                        Result.retry()
                    }
                }
                
                wallpaperResult.getOrNull()!!
            }
        }
        
        setProgress(workDataOf(KEY_PROGRESS_STATE to PROGRESS_APPLYING))
        
        val downloadResult = wallpaperRepository.downloadWallpaper(wallpaper)
        var wallpaperFile: File? = null
        var selectedWallpaper = wallpaper
        
        if (downloadResult.isFailure) {
            val downloadError = downloadResult.exceptionOrNull()
            Log.w(TAG, "Failed to download wallpaper ${wallpaper.id}: ${downloadError?.message}")
            
            networkStateTracker.markAsOfflineMode()
            
            val cachedWallpaperResult = findCachedWallpaperUseCase(excludeWallpaperId = wallpaper.id)
            
            if (cachedWallpaperResult != null) {
                val (cachedWallpaper, cachedFile) = cachedWallpaperResult
                Log.d(TAG, "Offline fallback successful: ${cachedWallpaper.id}")
                selectedWallpaper = cachedWallpaper
                wallpaperFile = cachedFile
                
                scheduleRetryWhenOnline(targetScreen)
            } else {
                Log.e(TAG, "No cached wallpapers available for offline fallback")
                scheduleRetryWhenOnline(targetScreen)
                return Result.success(
                    workDataOf("skipped_reason" to "no_cache_no_network")
                )
            }
        } else {
            wallpaperFile = downloadResult.getOrNull()!!
            networkStateTracker.markSuccessfulDownload()
            cancelPendingRetryWork()
        }
        
        val isManualChange = inputData.getBoolean(KEY_IS_MANUAL_CHANGE, false)
        
        if (isManualChange) {
            val previousHistory = wallpaperRepository.getHistory().first().firstOrNull { it.isActive() }
            
            if (previousHistory != null) {
                wallpaperRepository.markWallpaperRemoved(previousHistory.id, System.currentTimeMillis())
                val updatedHistory = wallpaperRepository.getHistoryEntry(previousHistory.id)
                
                if (updatedHistory != null) {
                    val implicitResult = processImplicitFeedbackUseCase(updatedHistory)
                    implicitResult.fold(
                        onSuccess = {},
                        onFailure = { error ->
                            Log.w(TAG, "Failed to process implicit feedback: ${error.message}")
                        }
                    )
                }
            }
        } else {
            val previousHistory = wallpaperRepository.getHistory().first().firstOrNull { it.isActive() }
            if (previousHistory != null) {
                wallpaperRepository.markWallpaperRemoved(previousHistory.id, System.currentTimeMillis())
            }
        }
        
        if (wallpaperFile == null) {
            Log.e(TAG, "wallpaperFile is null after download/cache step")
            return Result.retry()
        }
        val applied = applyWallpaperToScreen(wallpaperFile, targetScreen)
        
        if (!applied) {
            Log.e(TAG, "Failed to apply wallpaper")
            return Result.retry()
        }
        
        val historyId = wallpaperRepository.recordWallpaperApplied(selectedWallpaper)
        Log.d(TAG, "Applied wallpaper ${selectedWallpaper.id}, history ID: $historyId")
        
        val selectedId = selectedWallpaper.id
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) {
            try {
                nextWallpaperCacheManager.precomputeNextWallpaper(appliedWallpaperId = selectedId)
            } catch (e: Exception) {
                Log.w(TAG, "Exception during pre-compute: ${e.message}")
            }
        }
        
        return Result.success(
            workDataOf(KEY_WALLPAPER_ID to selectedWallpaper.id)
        )
    }
    
    private suspend fun applyBothDifferentWallpapers(): Result {
        Log.d(TAG, "Applying 'Both But Different' - selecting two different wallpapers")
        
        setProgress(workDataOf(KEY_PROGRESS_STATE to PROGRESS_FINDING))
        
        val isManualChange = inputData.getBoolean(KEY_IS_MANUAL_CHANGE, false)
        if (isManualChange) {
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
        
        val cachedPair = nextWallpaperCacheManager.getNextWallpaperPair()
        
        val homeWallpaper: me.avinas.vanderwaals.data.entity.WallpaperMetadata
        val lockWallpaper: me.avinas.vanderwaals.data.entity.WallpaperMetadata
        
        if (cachedPair != null) {
            Log.d(TAG, "Using CACHED wallpaper pair for instant change")
            homeWallpaper = cachedPair.homeWallpaper
            lockWallpaper = cachedPair.lockWallpaper
        } else {
            // Cache miss - compute fresh (slower)
            Log.d(TAG, "No cached pair - computing fresh wallpaper selections")
            
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
            homeWallpaper = homeWallpaperResult.getOrNull()!!
            
            val lockWallpaperResult = selectNextWallpaperUseCase(excludeWallpaperId = homeWallpaper.id)
            if (lockWallpaperResult.isFailure) {
                val error = lockWallpaperResult.exceptionOrNull()
                Log.e(TAG, "Failed to select lock wallpaper: ${error?.message}")
                Log.w(TAG, "Falling back to same wallpaper for both screens")
            }
            lockWallpaper = lockWallpaperResult.getOrNull() ?: homeWallpaper
        }
        
        Log.d(TAG, "Selected wallpapers - Home: ${homeWallpaper.id}, Lock: ${lockWallpaper.id}")
        
        setProgress(workDataOf(KEY_PROGRESS_STATE to PROGRESS_APPLYING))
        
        var actualHomeWallpaper = homeWallpaper
        var homeWallpaperFile: File
        var usedCachedFallback = false
        
        val homeDownloadResult = wallpaperRepository.downloadWallpaper(homeWallpaper)
        if (homeDownloadResult.isFailure) {
            Log.w(TAG, "Failed to download home wallpaper: ${homeDownloadResult.exceptionOrNull()?.message}")
            
            networkStateTracker.markAsOfflineMode()
            usedCachedFallback = true
            
            val cachedHomeResult = findCachedWallpaperUseCase(excludeWallpaperId = homeWallpaper.id)
            if (cachedHomeResult != null) {
                val (cachedWallpaper, cachedFile) = cachedHomeResult
                Log.d(TAG, "Offline fallback for home: ${cachedWallpaper.id}")
                actualHomeWallpaper = cachedWallpaper
                homeWallpaperFile = cachedFile
                
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
        
        var actualLockWallpaper = lockWallpaper
        var lockWallpaperFile: File
        
        if (lockWallpaper.id != homeWallpaper.id) {
            val lockDownloadResult = wallpaperRepository.downloadWallpaper(lockWallpaper)
            if (lockDownloadResult.isFailure) {
                Log.w(TAG, "Failed to download lock wallpaper: ${lockDownloadResult.exceptionOrNull()?.message}")
                
                if (!usedCachedFallback) {
                    networkStateTracker.markAsOfflineMode()
                    usedCachedFallback = true
                }
                
                val cachedLockResult = findCachedWallpaperUseCase(excludeWallpaperId = actualHomeWallpaper.id)
                if (cachedLockResult != null) {
                    val (cachedWallpaper, cachedFile) = cachedLockResult
                    Log.d(TAG, "Offline fallback for lock: ${cachedWallpaper.id}")
                    actualLockWallpaper = cachedWallpaper
                    lockWallpaperFile = cachedFile
                } else {
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
        
        if (!usedCachedFallback) {
            networkStateTracker.markSuccessfulDownload()
            cancelPendingRetryWork()
        }
        
        val homeApplied = applyWallpaperToScreen(homeWallpaperFile, TARGET_HOME)
        if (!homeApplied) {
            Log.e(TAG, "Failed to apply home wallpaper")
            return Result.retry()
        }
        
        val lockApplied = applyWallpaperToScreen(lockWallpaperFile, TARGET_LOCK)
        if (!lockApplied) {
            Log.e(TAG, "Failed to apply lock wallpaper")
            return Result.retry()
        }
        
        val homeHistoryId = wallpaperRepository.recordWallpaperApplied(actualHomeWallpaper)
        Log.d(TAG, "Applied home wallpaper ${actualHomeWallpaper.id}, history ID: $homeHistoryId")
        
        if (actualLockWallpaper.id != actualHomeWallpaper.id) {
            val lockHistoryId = wallpaperRepository.recordWallpaperApplied(actualLockWallpaper)
            Log.d(TAG, "Applied lock wallpaper ${actualLockWallpaper.id}, history ID: $lockHistoryId")
        }
        
        val homeId = actualHomeWallpaper.id
        val lockId = actualLockWallpaper.id
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) {
            try {
                nextWallpaperCacheManager.precomputeNextWallpaperPair(
                    appliedHomeId = homeId,
                    appliedLockId = lockId
                )
            } catch (e: Exception) {
                Log.w(TAG, "Exception during pair pre-computation: ${e.message}")
            }
        }
        
        return Result.success(
            workDataOf(KEY_WALLPAPER_ID to actualHomeWallpaper.id)
        )
    }
    
    private suspend fun applyWallpaperToScreen(wallpaperFile: File, targetScreen: String): Boolean {
        return when (val result = wallpaperApplicator.apply(wallpaperFile, targetScreen)) {
            is WallpaperApplicator.ApplyResult.Success -> {
                Log.d(TAG, "Successfully applied wallpaper with SmartCrop processing")
                true
            }
            is WallpaperApplicator.ApplyResult.DecodeFailed -> {
                Log.e(TAG, "Failed to decode wallpaper: ${result.message}")
                false
            }
            is WallpaperApplicator.ApplyResult.BlockedByLiveWallpaper -> {
                Log.e(TAG, "Wallpaper change blocked by live wallpaper: ${result.serviceName}")
                false
            }
            is WallpaperApplicator.ApplyResult.InvalidTarget -> {
                Log.e(TAG, "Invalid target screen: ${result.target}")
                false
            }
            is WallpaperApplicator.ApplyResult.Error -> {
                Log.e(TAG, "Error applying wallpaper", result.exception)
                false
            }
        }
    }
    
    
    /**
     * Manual wallpaper changes are still allowed regardless of battery level.
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
     * Schedules a one-time retry (network-connected + battery-not-low) after offline
     * fallback, so the cache gets refreshed or a fresh wallpaper is applied once online.
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
                KEY_IS_NETWORK_RETRY to true
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
     * Cancels pending retry work once a fresh download has succeeded.
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
