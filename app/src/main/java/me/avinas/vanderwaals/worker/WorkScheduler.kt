package me.avinas.vanderwaals.worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.avinas.vanderwaals.domain.usecase.UserEngagementTracker
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of scheduling operations.
 * Used to communicate scheduling success or failure back to ViewModels.
 */
sealed class SchedulingResult {
    object Success : SchedulingResult()
    data class PermissionDenied(val message: String) : SchedulingResult()
    data class BatteryOptimizationWarning(val message: String) : SchedulingResult()
    data class Error(val message: String) : SchedulingResult()
}

/**
 * Manages WorkManager initialization and scheduling for Vanderwaals workers.
 * 
 * Responsibilities:
 * - Schedule periodic workers (manifest sync, cleanup)
 * - Schedule wallpaper change workers with different intervals
 * - Configure work constraints and backoff policies
 * - Provide methods for manual triggering
 * 
 * **Periodic Workers:**
 * - ManifestSyncWorker: Adaptive (based on user engagement)
 *   - HIGH engagement: Daily (24 hours)
 *   - MEDIUM engagement: Every 3 days (72 hours)
 *   - LOW engagement: Weekly (168 hours)
 *   - MINIMAL engagement: Bi-weekly (336 hours)
 * - CleanupWorker: Daily (24 hours)
 * 
 * **Dynamic Workers:**
 * - WallpaperChangeWorker: Every unlock, hourly, daily, or never
 * - BatchDownloadWorker: On-demand after sync
 * 
 * **Usage:**
 * ```kotlin
 * @Inject lateinit var workScheduler: WorkScheduler
 * 
 * // Initialize periodic workers
 * workScheduler.initializePeriodicWorkers()
 * 
 * // Schedule wallpaper change
 * workScheduler.scheduleWallpaperChange(
 *     interval = ChangeInterval.DAILY,
 *     time = LocalTime.of(9, 0)
 * )
 * ```
 * 
 * @property context Application context
 * @property workManager WorkManager instance
 */
@Singleton
class WorkScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val engagementTracker: UserEngagementTracker
) {
    private val workManager = WorkManager.getInstance(context)
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    companion object {
        private const val TAG = "WorkScheduler"
        private const val ALARM_REQUEST_CODE_DAILY = 1001
        private const val ALARM_REQUEST_CODE_REPEATING = 1002
    }
    
    /**
     * Initializes all periodic workers.
     * 
     * Should be called once on app startup or when Vanderwaals mode is activated.
     */
    fun initializePeriodicWorkers() {
        scheduleManifestSync()
        scheduleCleanup()
        scheduleDailyPlaylist()
    }
    
    /**
     * Schedules adaptive manifest sync worker based on user engagement.
     * 
     * Sync intervals:
     * - HIGH engagement (active user): Daily (24 hours)
     * - MEDIUM engagement (regular user): Every 3 days (72 hours)
     * - LOW engagement (occasional user): Weekly (168 hours)
     * - MINIMAL engagement (rare user): Bi-weekly (336 hours)
     * 
     * Constraints:
     * - Network connected
     * - Battery not low
     * - Storage not low
     */
    private fun scheduleManifestSync() {
        coroutineScope.launch {
            val engagement = engagementTracker.calculateEngagement()
            val intervalHours = engagementTracker.getSyncIntervalHours(engagement)
            
            android.util.Log.d(TAG, "Scheduling manifest sync with ${engagement.name} engagement: " +
                "$intervalHours hours (${engagementTracker.getEngagementDescription(engagement)})")
            
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .build()
            
            val syncWork = PeriodicWorkRequestBuilder<CatalogSyncWorker>(
                repeatInterval = intervalHours.toLong(),
                repeatIntervalTimeUnit = TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15,
                    TimeUnit.MINUTES
                )
                .build()
            
            workManager.enqueueUniquePeriodicWork(
                CatalogSyncWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE, // Update to apply new interval
                syncWork
            )
        }
    }
    
    /**
     * Reschedules manifest sync with updated engagement-based interval.
     * 
     * Call this after significant user activity (e.g., after wallpaper change,
     * feedback submission) to adapt sync frequency to current engagement level.
     */
    fun rescheduleManifestSyncBasedOnEngagement() {
        scheduleManifestSync()
    }
    
    /**
     * Schedules daily cleanup worker.
     * 
     * Constraints:
     * - Battery not low
     * - Device idle (if possible)
     */
    private fun scheduleCleanup() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresDeviceIdle(true)
            .build()
        
        val cleanupWork = PeriodicWorkRequestBuilder<CleanupWorker>(
            repeatInterval = 1,
            repeatIntervalTimeUnit = TimeUnit.DAYS
        )
            .setConstraints(constraints)
            .setInitialDelay(
                calculateDelayUntil3AM(),
                TimeUnit.MILLISECONDS
            )
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            CleanupWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            cleanupWork
        )
    }

    /**
     * Schedules daily playlist download worker.
     * 
     * Runs once a day, preferably when charging and on WiFi.
     */
    private fun scheduleDailyPlaylist() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()
        
        val playlistWork = PeriodicWorkRequestBuilder<DailyPlaylistWorker>(
            repeatInterval = 1,
            repeatIntervalTimeUnit = TimeUnit.DAYS
        )
            .setConstraints(constraints)
            .setInitialDelay(
                calculateDelayUntilTime(LocalTime.of(2, 0)), // Run at 2 AM
                TimeUnit.MILLISECONDS
            )
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            DailyPlaylistWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            playlistWork
        )
    }
    
    /**
     * Schedules wallpaper change worker based on user's preferred interval.
     * 
     * @param interval Change interval (EVERY_UNLOCK, HOURLY, DAILY, NEVER)
     * @param time Time of day for daily changes (optional)
     * @param targetScreen Target screen (home, lock, both)
     * @return SchedulingResult indicating success or failure reason
     */
    fun scheduleWallpaperChange(
        interval: ChangeInterval,
        time: LocalTime? = null,
        targetScreen: String = WallpaperChangeWorker.TARGET_BOTH
    ): SchedulingResult {
        android.util.Log.d(TAG, "========================================")
        android.util.Log.d(TAG, "scheduleWallpaperChange called")
        android.util.Log.d(TAG, "  Interval: ${interval.displayName}")
        android.util.Log.d(TAG, "  Target Screen: $targetScreen")
        android.util.Log.d(TAG, "  Time: ${time ?: "N/A"}")
        android.util.Log.d(TAG, "========================================")
        
        // Cancel any existing wallpaper change work
        cancelWallpaperChange()
        
        return when (interval) {
            ChangeInterval.NEVER -> {
                android.util.Log.d(TAG, "Auto-change disabled - no work scheduled")
                stopWallpaperMonitorService()
                SchedulingResult.Success
            }
            
            ChangeInterval.EVERY_UNLOCK -> {
                android.util.Log.d(TAG, "Interval is Every Unlock - starting WallpaperMonitorService")
                // Start foreground service to monitor unlock events
                startWallpaperMonitorService()
                SchedulingResult.Success
            }
            
            ChangeInterval.HOURLY -> {
                android.util.Log.d(TAG, "Scheduling hourly wallpaper change for target: $targetScreen")
                stopWallpaperMonitorService() // Ensure service is stopped
                scheduleRepeatingAlarm(60 * 60 * 1000L, targetScreen) // 1 hour in milliseconds
            }

            ChangeInterval.FIFTEEN_MINUTES -> {
                android.util.Log.d(TAG, "Scheduling 15-minute wallpaper change for target: $targetScreen")
                stopWallpaperMonitorService() // Ensure service is stopped
                scheduleRepeatingAlarm(15 * 60 * 1000L, targetScreen) // 15 minutes in milliseconds
            }
            
            ChangeInterval.DAILY -> {
                val changeTime = time ?: LocalTime.of(9, 0) // Default 9 AM
                android.util.Log.d(TAG, "Scheduling daily wallpaper change at ${changeTime.hour}:${changeTime.minute} for target: $targetScreen")
                stopWallpaperMonitorService() // Ensure service is stopped
                scheduleDailyWallpaperChange(changeTime, targetScreen)
            }
        }
    }
    
    /**
     * Schedules periodic wallpaper change.
     * 
     * CRITICAL FIX: Removed NetworkType.CONNECTED constraint.
     * Worker can run offline since it will:
     * 1. Try to use already downloaded/cached wallpapers first
     * 2. Only download if needed (with proper error handling)
     * This prevents hourly changes from being blocked when offline.
     */
    private fun schedulePeriodicWallpaperChange(
        interval: Long,
        timeUnit: TimeUnit,
        targetScreen: String
    ) {
        android.util.Log.d(TAG, "schedulePeriodicWallpaperChange called")
        android.util.Log.d(TAG, "  Interval: $interval ${timeUnit.name}")
        android.util.Log.d(TAG, "  Target Screen: $targetScreen")
        
        // No constraints - let WorkManager run whenever possible
        val constraints = Constraints.Builder()
            .build()
        
        val inputData = workDataOf(
            WallpaperChangeWorker.KEY_TARGET_SCREEN to targetScreen,
            WallpaperChangeWorker.KEY_MODE to WallpaperChangeWorker.MODE_VANDERWAALS
        )
        
        val changeWork = PeriodicWorkRequestBuilder<WallpaperChangeWorker>(
            repeatInterval = interval,
            repeatIntervalTimeUnit = timeUnit
        )
            .setConstraints(constraints)
            .setInputData(inputData)
            .build()
        
        // CRITICAL FIX: Use REPLACE policy to ensure schedule changes take effect
        // REPLACE cancels existing work and creates new work with fresh timing
        // This ensures frequency changes are applied immediately
        workManager.enqueueUniquePeriodicWork(
            WallpaperChangeWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            changeWork
        )
        
        android.util.Log.d(TAG, "Periodic work scheduled successfully (NO network constraint)")
        
        // CRITICAL DEBUG: Verify the work was actually scheduled
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            kotlinx.coroutines.delay(500) // Wait for WorkManager to process
            logWallpaperChangeStatus()
        }
    }
    
    /**
     * Schedules daily wallpaper change at specific time.
     * 
     * CRITICAL IMPLEMENTATION DETAIL:
     * Uses AlarmManager to set exact daily alarms at the target time, which triggers
     * a work request. This is the ONLY reliable way to guarantee exact-time daily execution
     * on Android.
     * 
     * WHY NOT JUST PeriodicWorkRequest?
     * - WorkManager's PeriodicWorkRequest cannot guarantee exact execution time
     * - Minimum period is 15 minutes, and flex window can add significant delays
     * - For daily scheduling, WorkManager adds the initial delay to the repeat interval,
     *   causing the first run to be delayed by up to 24 hours
     * 
     * HOW THIS WORKS:
     * 1. AlarmManager sets an exact alarm for target time (12:30 PM)
     * 2. When alarm fires, it broadcasts an intent received by AlarmReceiver
     * 3. AlarmReceiver enqueues a OneTimeWorkRequest to WallpaperChangeWorker
     * 4. Alarm is automatically rescheduled for next day by AlarmManager or system
     * 5. On device reboot, alarms are restored by system automatically
     * 
     * GUARANTEED BEHAVIOR:
     * ✅ First run: TODAY at target time (if not passed) or TOMORROW if already passed
     * ✅ Subsequent runs: EVERY DAY at exactly target time
     * ✅ No network requirement
     * ✅ Survives app restart and device reboot
     * 
     * CRITICAL FIX 2: Removed network constraint so it runs offline.
     * 
     * @return SchedulingResult indicating success or failure reason
     */
    private fun scheduleDailyWallpaperChange(time: LocalTime, targetScreen: String): SchedulingResult {
        android.util.Log.d(TAG, "Calculating daily schedule:")
        android.util.Log.d(TAG, "  Target time: ${time.hour}:${String.format("%02d", time.minute)}")
        
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        if (alarmManager == null) {
            android.util.Log.e(TAG, "AlarmManager not available!")
            return SchedulingResult.Error("AlarmManager not available on this device")
        }
        
        // Check if we have permission to schedule exact alarms (Android 12+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                android.util.Log.e(TAG, "❌ SCHEDULE_EXACT_ALARM permission not granted! Alarms will not fire at exact time.")
                android.util.Log.e(TAG, "   User needs to grant this permission in Settings > Apps > Vanderwaals > Alarms & reminders")
                return SchedulingResult.PermissionDenied(
                    "For daily changes at exact time, please grant alarm permission in Settings"
                )
            } else {
                android.util.Log.d(TAG, "✅ SCHEDULE_EXACT_ALARM permission granted")
            }
        }
        
        // Check battery optimization status - warn but don't fail
        val batteryOptimizationExempt = me.avinas.vanderwaals.core.BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
        val hasBatteryWarning = if (!batteryOptimizationExempt) {
            android.util.Log.w(TAG, "⚠️ Battery optimization is ENABLED - alarms may be delayed or skipped")
            android.util.Log.w(TAG, "   Recommend disabling battery optimization for reliable alarm execution")
            android.util.Log.w(TAG, "   Settings > Apps > Vanderwaals > Battery > Unrestricted")
            true
        } else {
            android.util.Log.d(TAG, "✅ Battery optimization disabled - alarms will run reliably")
            false
        }
        
        // Cancel any existing alarms for daily change
        val alarmIntent = Intent(context, WallpaperAlarmReceiver::class.java).apply {
            putExtra("targetScreen", targetScreen)
            putExtra("mode", WallpaperChangeWorker.MODE_VANDERWAALS)
            putExtra("targetHour", time.hour)
            putExtra("targetMinute", time.minute)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE_DAILY,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Cancel previous alarm
        alarmManager.cancel(pendingIntent)
        
        // Calculate next execution time
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, time.hour)
            set(Calendar.MINUTE, time.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            
            // If target time has passed today, schedule for tomorrow
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        
        val delayMillis = calendar.timeInMillis - System.currentTimeMillis()
        val delayMinutes = delayMillis / 60000
        val delayHours = delayMillis / 3600000
        
        android.util.Log.d(TAG, "  Current time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}")
        android.util.Log.d(TAG, "  Target time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(calendar.timeInMillis))}")
        android.util.Log.d(TAG, "  Initial delay: ${delayMillis}ms (~$delayMinutes minutes / ~$delayHours hours)")
        
        // Set exact alarm using setExactAndAllowWhileIdle (allows execution during Doze mode)
        // CRITICAL: setExactAndAllowWhileIdle guarantees exact-time execution for daily alarms
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
            android.util.Log.d(TAG, "✅ Alarm successfully scheduled with AlarmManager")
            android.util.Log.d(TAG, "  Alarm time (ms): ${calendar.timeInMillis}")
            android.util.Log.d(TAG, "  Current time (ms): ${System.currentTimeMillis()}")
            android.util.Log.d(TAG, "  Delay: $delayMinutes minutes")
            
            android.util.Log.d(TAG, "Scheduled daily wallpaper change at ${time.hour}:${String.format("%02d", time.minute)} for target: $targetScreen (using AlarmManager)")
            android.util.Log.d(TAG, "  First run: ~$delayMinutes minutes from now")
            android.util.Log.d(TAG, "  Subsequent runs: Every 24 hours at exactly ${time.hour}:${String.format("%02d", time.minute)}")
            
            return if (hasBatteryWarning) {
                SchedulingResult.BatteryOptimizationWarning(
                    "Scheduled successfully. Note: Battery optimization may delay or skip changes. Disable in Settings for best reliability."
                )
            } else {
                SchedulingResult.Success
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ Failed to set alarm: ${e.message}", e)
            return SchedulingResult.Error("Failed to schedule alarm: ${e.message}")
        }
    }
    
    /**
     * Schedules repeating alarm for 15-minute or hourly wallpaper changes.
     * 
     * Uses setRepeating with AlarmManager for precise interval-based execution.
     * This ensures wallpaper changes happen at exact intervals (15 min or 1 hour).
     * 
     * @param intervalMillis Interval in milliseconds (900000 for 15min, 3600000 for 1hr)
     * @param targetScreen Target screen (home, lock, both)
     * @return SchedulingResult indicating success or failure reason
     */
    private fun scheduleRepeatingAlarm(intervalMillis: Long, targetScreen: String): SchedulingResult {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        if (alarmManager == null) {
            android.util.Log.e(TAG, "AlarmManager not available!")
            return SchedulingResult.Error("AlarmManager not available on this device")
        }
        
        // Check if we have permission to schedule exact alarms (Android 12+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                android.util.Log.e(TAG, "❌ SCHEDULE_EXACT_ALARM permission not granted!")
                android.util.Log.e(TAG, "   User needs to grant this permission in Settings")
                return SchedulingResult.PermissionDenied(
                    "For reliable ${intervalMillis / 60000}-minute changes, please grant exact alarm permission in Settings"
                )
            } else {
                android.util.Log.d(TAG, "✅ SCHEDULE_EXACT_ALARM permission granted")
            }
        }
        
        // Check battery optimization status - warn but don't fail
        val batteryOptimizationExempt = me.avinas.vanderwaals.core.BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
        val hasBatteryWarning = if (!batteryOptimizationExempt) {
            android.util.Log.w(TAG, "⚠️ Battery optimization is ENABLED - alarms may be delayed")
            true
        } else {
            android.util.Log.d(TAG, "✅ Battery optimization disabled")
            false
        }
        
        val alarmIntent = Intent(context, WallpaperAlarmReceiver::class.java).apply {
            putExtra("targetScreen", targetScreen)
            putExtra("mode", WallpaperChangeWorker.MODE_VANDERWAALS)
            putExtra("intervalMillis", intervalMillis)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE_REPEATING,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Cancel previous alarm
        alarmManager.cancel(pendingIntent)
        
        // Schedule first alarm to trigger immediately or very soon
        val triggerTime = System.currentTimeMillis() + 5000 // 5 seconds from now
        
        try {
            // Use setRepeating for regular interval-based alarms
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                intervalMillis,
                pendingIntent
            )
            
            val intervalMinutes = intervalMillis / 60000
            android.util.Log.d(TAG, "✅ Repeating alarm scheduled successfully")
            android.util.Log.d(TAG, "  Interval: $intervalMinutes minutes")
            android.util.Log.d(TAG, "  Target screen: $targetScreen")
            android.util.Log.d(TAG, "  First run: ~5 seconds from now")
            android.util.Log.d(TAG, "  Subsequent runs: Every $intervalMinutes minutes")
            
            return if (hasBatteryWarning) {
                SchedulingResult.BatteryOptimizationWarning(
                    "Scheduled successfully. Note: Battery optimization may delay changes. Disable in Settings for best reliability."
                )
            } else {
                SchedulingResult.Success
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ Failed to set repeating alarm: ${e.message}", e)
            return SchedulingResult.Error("Failed to schedule alarm: ${e.message}")
        }
    }
    
    /**
     * Triggers batch download worker immediately.
     */
    fun triggerBatchDownload() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED) // WiFi only
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()
        
        val downloadWork = OneTimeWorkRequestBuilder<BatchDownloadWorker>()
            .setConstraints(constraints)
            .build()
        
        workManager.enqueueUniqueWork(
            BatchDownloadWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            downloadWork
        )
    }
    
    /**
     * Triggers immediate wallpaper change (e.g., from "Change Now" button).
     */
    fun triggerImmediateWallpaperChange(targetScreen: String = WallpaperChangeWorker.TARGET_BOTH) {
        val inputData = workDataOf(
            WallpaperChangeWorker.KEY_TARGET_SCREEN to targetScreen,
            WallpaperChangeWorker.KEY_MODE to WallpaperChangeWorker.MODE_VANDERWAALS
        )
        
        val changeWork = OneTimeWorkRequestBuilder<WallpaperChangeWorker>()
            .setInputData(inputData)
            .build()
        
        workManager.enqueue(changeWork)
    }
    
    /**
     * Cancels all scheduled wallpaper change work.
     */
    fun cancelWallpaperChange() {
        workManager.cancelUniqueWork(WallpaperChangeWorker.WORK_NAME)
        stopWallpaperMonitorService()
    }
    
    /**
     * Gets the status of the scheduled wallpaper change work.
     * DIAGNOSTIC METHOD: Use this to verify if auto-change is actually scheduled.
     */
    fun getWallpaperChangeWorkStatus(): androidx.lifecycle.LiveData<List<androidx.work.WorkInfo>> {
        return workManager.getWorkInfosForUniqueWorkLiveData(WallpaperChangeWorker.WORK_NAME)
    }
    
    /**
     * Logs the current status of wallpaper change work.
     * CRITICAL DEBUG: Call this to diagnose why auto-change isn't working.
     */
    fun logWallpaperChangeStatus() {
        // Use blocking call - this is for debugging only
        val workInfos = workManager.getWorkInfosForUniqueWork(WallpaperChangeWorker.WORK_NAME).get()
        
        if (workInfos.isEmpty()) {
            android.util.Log.e(TAG, "❌ NO WORK SCHEDULED! Auto-change will NOT work!")
            android.util.Log.e(TAG, "   This means scheduleWallpaperChange() was never called or failed silently")
        } else {
            workInfos.forEachIndexed { index: Int, workInfo: WorkInfo ->
                android.util.Log.d(TAG, "========================================")
                android.util.Log.d(TAG, "Work Status #${index + 1}:")
                android.util.Log.d(TAG, "  ID: ${workInfo.id}")
                android.util.Log.d(TAG, "  State: ${workInfo.state}")
                android.util.Log.d(TAG, "  Tags: ${workInfo.tags}")
                android.util.Log.d(TAG, "  Run Attempt: ${workInfo.runAttemptCount}")
                android.util.Log.d(TAG, "  Next Schedule Time: ${workInfo.nextScheduleTimeMillis}")
                
                when (workInfo.state) {
                    WorkInfo.State.ENQUEUED -> {
                        android.util.Log.d(TAG, "  ✅ Work is SCHEDULED and waiting to run")
                        val nextRun = workInfo.nextScheduleTimeMillis
                        if (nextRun > 0) {
                            val delay = nextRun - System.currentTimeMillis()
                            val delayMinutes = delay / 60000
                            android.util.Log.d(TAG, "  ⏰ Will run in ~$delayMinutes minutes")
                        }
                    }
                    WorkInfo.State.RUNNING -> {
                        android.util.Log.d(TAG, "  ▶️ Work is CURRENTLY RUNNING")
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        android.util.Log.d(TAG, "  ✅ Work completed successfully (one-time work)")
                    }
                    WorkInfo.State.FAILED -> {
                        android.util.Log.e(TAG, "  ❌ Work FAILED! Check worker logs for errors")
                    }
                    WorkInfo.State.BLOCKED -> {
                        android.util.Log.w(TAG, "  ⚠️ Work is BLOCKED (constraints not met or dependency not complete)")
                        android.util.Log.w(TAG, "     - Check if battery optimization is blocking it")
                        android.util.Log.w(TAG, "     - Check if device is in doze mode")
                    }
                    WorkInfo.State.CANCELLED -> {
                        android.util.Log.e(TAG, "  ❌ Work was CANCELLED")
                    }
                }
                android.util.Log.d(TAG, "========================================")
            }
        }
    }
    
    /**
     * Cancels all Vanderwaals workers.
     */
    fun cancelAllWorkers() {
        workManager.cancelUniqueWork(CatalogSyncWorker.WORK_NAME)
        workManager.cancelUniqueWork(CleanupWorker.WORK_NAME)
        workManager.cancelUniqueWork(WallpaperChangeWorker.WORK_NAME)
        workManager.cancelUniqueWork(WallpaperChangeWorker.WORK_NAME)
        workManager.cancelUniqueWork(BatchDownloadWorker.WORK_NAME)
        workManager.cancelUniqueWork(DailyPlaylistWorker.WORK_NAME)
    }
    
    /**
     * Calculates delay in milliseconds until 3:00 AM.
     */
    private fun calculateDelayUntil3AM(): Long {
        return calculateDelayUntilTime(LocalTime.of(3, 0))
    }
    
    /**
     * Calculates delay in milliseconds until specified time.
     */
    private fun calculateDelayUntilTime(targetTime: LocalTime): Long {
        val now = LocalDateTime.now()
        var target = now.withHour(targetTime.hour).withMinute(targetTime.minute).withSecond(0)
        
        // If target time has passed today, schedule for tomorrow
        if (target.isBefore(now)) {
            target = target.plusDays(1)
        }
        
        return Duration.between(now, target).toMillis()
    }
    
    /**
     * Starts the WallpaperMonitorService.
     */
    private fun startWallpaperMonitorService() {
        try {
            val intent = Intent(context, me.avinas.vanderwaals.service.WallpaperMonitorService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            android.util.Log.d(TAG, "✅ WallpaperMonitorService started")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ Failed to start WallpaperMonitorService", e)
        }
    }

    /**
     * Stops the WallpaperMonitorService.
     */
    private fun stopWallpaperMonitorService() {
        try {
            val intent = Intent(context, me.avinas.vanderwaals.service.WallpaperMonitorService::class.java)
            context.stopService(intent)
            android.util.Log.d(TAG, "⏹️ WallpaperMonitorService stopped")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ Failed to stop WallpaperMonitorService", e)
        }
    }
}

/**
 * Enum representing wallpaper change intervals.
 */
enum class ChangeInterval(val displayName: String) {
    /**
     * Change wallpaper on every unlock.
     * Relies on DeviceUnlockReceiver.
     */
    EVERY_UNLOCK("Every Unlock"),
    
    /**
     * Change wallpaper every hour.
     */
    HOURLY("Hourly"),

    /**
     * Change wallpaper every 15 minutes.
     */
    FIFTEEN_MINUTES("15 Minutes"),
    
    /**
     * Change wallpaper once per day at specific time.
     */
    DAILY("Daily"),
    
    /**
     * Never change wallpaper automatically.
     */
    NEVER("Never")
}
