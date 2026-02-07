package me.avinas.vanderwaals.worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalTime
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized AlarmManager scheduling for wallpaper changes.
 * 
 * Extracted from WorkScheduler to improve:
 * - **Single Responsibility**: Alarm scheduling is now isolated and testable
 * - **Reusability**: Alarm logic can be used by other scheduling mechanisms
 * - **Maintainability**: AlarmManager-specific code is localized here
 * 
 * **Key Methods:**
 * - `scheduleDailyAlarm()`: Exact daily alarm at specified time
 * - `scheduleRepeatingAlarm()`: Self-rescheduling exact interval alarms
 * - `cancelAllAlarms()`: Cancels both daily and repeating alarms
 * 
 * **Why AlarmManager over WorkManager?**
 * - WorkManager cannot guarantee exact-time execution
 * - AlarmManager with `setExactAndAllowWhileIdle` provides precise timing
 * - Essential for user-configured daily wallpaper change times
 * 
 * @see WorkScheduler
 * @see WallpaperAlarmReceiver
 */
@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager: AlarmManager? = 
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    
    companion object {
        private const val TAG = "AlarmScheduler"
        const val ALARM_REQUEST_CODE_DAILY = 1001
        const val ALARM_REQUEST_CODE_REPEATING = 1002
    }
    
    /**
     * Checks if exact alarm scheduling is available.
     * @return true if AlarmManager is available and can schedule exact alarms
     */
    fun canScheduleExactAlarms(): Boolean {
        if (alarmManager == null) return false
        
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true // Pre-Android 12 doesn't need this permission
        }
    }
    
    /**
     * Checks if battery optimization is disabled (recommended for reliable alarms).
     */
    fun isBatteryOptimizationExempt(): Boolean {
        return me.avinas.vanderwaals.core.BatteryOptimizationHelper
            .isIgnoringBatteryOptimizations(context)
    }
    
    /**
     * Schedules a daily alarm at the specified time.
     * 
     * Uses `setExactAndAllowWhileIdle` for precise timing that works during Doze mode.
     * 
     * @param time Target time for daily wallpaper change
     * @param targetScreen Target screen (home, lock, both)
     * @return SchedulingResult indicating success or failure
     */
    fun scheduleDailyAlarm(time: LocalTime, targetScreen: String): SchedulingResult {
        android.util.Log.d(TAG, "Scheduling daily alarm at ${time.hour}:${String.format("%02d", time.minute)}")
        
        if (alarmManager == null) {
            android.util.Log.e(TAG, "AlarmManager not available!")
            return SchedulingResult.Error("AlarmManager not available on this device")
        }
        
        if (!canScheduleExactAlarms()) {
            android.util.Log.w(TAG, "⚠️ SCHEDULE_EXACT_ALARM permission not granted!")
            return SchedulingResult.PermissionDenied(
                "Exact alarm permission required for precise daily timing. Grant in Settings."
            )
        }
        
        val hasBatteryWarning = !isBatteryOptimizationExempt()
        if (hasBatteryWarning) {
            android.util.Log.w(TAG, "⚠️ Battery optimization enabled - alarms may be delayed")
        }
        
        // Cancel any existing daily alarm
        cancelDailyAlarm()
        
        // Create alarm intent
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
        
        // Calculate next execution time
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, time.hour)
            set(Calendar.MINUTE, time.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        
        return try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
            
            val delayMinutes = (calendar.timeInMillis - System.currentTimeMillis()) / 60000
            android.util.Log.d(TAG, "✅ Daily alarm scheduled: ~$delayMinutes minutes from now")
            
            if (hasBatteryWarning) {
                SchedulingResult.BatteryOptimizationWarning(
                    "Scheduled successfully. Battery optimization may delay changes."
                )
            } else {
                SchedulingResult.Success
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ Failed to set daily alarm: ${e.message}", e)
            SchedulingResult.Error("Failed to schedule alarm: ${e.message}")
        }
    }
    
    /**
     * Schedules a repeating alarm at the specified interval.
     * 
     * Uses `setExactAndAllowWhileIdle` with self-rescheduling for precise intervals.
     * First alarm triggers after 5 seconds, then WallpaperAlarmReceiver reschedules.
     * 
     * @param intervalMillis Interval in milliseconds (e.g., 900000 for 15 min)
     * @param targetScreen Target screen (home, lock, both)
     * @return SchedulingResult indicating success or failure
     */
    fun scheduleRepeatingAlarm(intervalMillis: Long, targetScreen: String): SchedulingResult {
        val intervalMinutes = intervalMillis / 60000
        android.util.Log.d(TAG, "Scheduling repeating alarm: every $intervalMinutes minutes")
        
        if (alarmManager == null) {
            android.util.Log.e(TAG, "AlarmManager not available!")
            return SchedulingResult.Error("AlarmManager not available on this device")
        }
        
        if (!canScheduleExactAlarms()) {
            android.util.Log.w(TAG, "⚠️ SCHEDULE_EXACT_ALARM permission not granted!")
            return SchedulingResult.PermissionDenied(
                "Exact alarm permission required for precise $intervalMinutes-minute intervals."
            )
        }
        
        val hasBatteryWarning = !isBatteryOptimizationExempt()
        
        // Cancel any existing repeating alarm
        cancelRepeatingAlarm()
        
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
        
        // Schedule first alarm for 5 seconds from now
        val triggerTime = System.currentTimeMillis() + 5000
        
        return try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
            
            android.util.Log.d(TAG, "✅ Repeating alarm scheduled:")
            android.util.Log.d(TAG, "  Interval: $intervalMinutes minutes")
            android.util.Log.d(TAG, "  First run: ~5 seconds from now")
            
            if (hasBatteryWarning) {
                SchedulingResult.BatteryOptimizationWarning(
                    "Scheduled successfully. Battery optimization may delay changes."
                )
            } else {
                SchedulingResult.Success
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ Failed to set repeating alarm: ${e.message}", e)
            SchedulingResult.Error("Failed to schedule alarm: ${e.message}")
        }
    }
    
    /**
     * Reschedules the next repeating alarm after the current one fires.
     * Called by WallpaperAlarmReceiver to maintain the chain of exact alarms.
     * 
     * @param intervalMillis Interval in milliseconds
     * @param targetScreen Target screen
     */
    fun rescheduleNextRepeatingAlarm(intervalMillis: Long, targetScreen: String) {
        if (alarmManager == null) {
            android.util.Log.e(TAG, "Cannot reschedule - AlarmManager not available")
            return
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
        
        val triggerTime = System.currentTimeMillis() + intervalMillis
        
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
            android.util.Log.d(TAG, "✅ Next repeating alarm scheduled: ${intervalMillis / 60000} min from now")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ Failed to reschedule alarm: ${e.message}", e)
        }
    }
    
    /**
     * Reschedules daily alarm for the next day.
     * Called by WallpaperAlarmReceiver after daily alarm fires.
     */
    fun rescheduleNextDailyAlarm(hour: Int, minute: Int, targetScreen: String) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_MONTH, 1) // Always schedule for tomorrow
        }
        
        if (alarmManager == null) {
            android.util.Log.e(TAG, "Cannot reschedule - AlarmManager not available")
            return
        }
        
        val alarmIntent = Intent(context, WallpaperAlarmReceiver::class.java).apply {
            putExtra("targetScreen", targetScreen)
            putExtra("mode", WallpaperChangeWorker.MODE_VANDERWAALS)
            putExtra("targetHour", hour)
            putExtra("targetMinute", minute)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE_DAILY,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
            android.util.Log.d(TAG, "✅ Next daily alarm scheduled for tomorrow at $hour:${String.format("%02d", minute)}")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ Failed to reschedule daily alarm: ${e.message}", e)
        }
    }
    
    /**
     * Cancels the daily alarm.
     */
    fun cancelDailyAlarm() {
        if (alarmManager == null) return
        
        val intent = Intent(context, WallpaperAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE_DAILY,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            android.util.Log.d(TAG, "Cancelled daily alarm")
        }
    }
    
    /**
     * Cancels the repeating alarm.
     */
    fun cancelRepeatingAlarm() {
        if (alarmManager == null) return
        
        val intent = Intent(context, WallpaperAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE_REPEATING,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            android.util.Log.d(TAG, "Cancelled repeating alarm")
        }
    }
    
    /**
     * Cancels all wallpaper change alarms (daily and repeating).
     */
    fun cancelAllAlarms() {
        cancelDailyAlarm()
        cancelRepeatingAlarm()
        android.util.Log.d(TAG, "✅ Cancelled all wallpaper change alarms")
    }
}
