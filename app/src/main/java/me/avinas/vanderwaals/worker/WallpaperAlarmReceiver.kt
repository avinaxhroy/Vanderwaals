package me.avinas.vanderwaals.worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.AndroidEntryPoint
import me.avinas.vanderwaals.service.WallpaperChangeService
import java.util.Calendar

/**
 * BroadcastReceiver that handles daily wallpaper change alarms.
 * 
 * When AlarmManager fires the daily alarm, this receiver:
 * 1. Starts a foreground service to handle the wallpaper change
 * 2. Reschedules the alarm for the next occurrence
 * 
 * **CRITICAL: Uses startForegroundService() instead of WorkManager**
 * 
 * This ensures reliable execution even when the app was killed by the user:
 * - When app is killed, process dies
 * - AlarmManager fires alarm, Android creates new process
 * - Receiver starts foreground service which properly initializes Hilt
 * - Service changes wallpaper and stops itself
 * 
 * Inspired by Paperize's WallpaperReceiver implementation.
 */
@AndroidEntryPoint
class WallpaperAlarmReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        
        val targetScreen = intent.getStringExtra("targetScreen") ?: WallpaperChangeWorker.TARGET_BOTH
        val mode = intent.getStringExtra("mode") ?: WallpaperChangeWorker.MODE_VANDERWAALS
        
        android.util.Log.d(TAG, "========================================")
        android.util.Log.d(TAG, "🔔 WallpaperAlarmReceiver TRIGGERED!")
        android.util.Log.d(TAG, "  Target Screen: $targetScreen")
        android.util.Log.d(TAG, "  Mode: $mode")
        android.util.Log.d(TAG, "  Timestamp: ${System.currentTimeMillis()}")
        android.util.Log.d(TAG, "========================================")
        
        // CRITICAL: Start foreground service instead of WorkManager
        // This ensures reliable execution even when the app was killed
        val serviceIntent = Intent(context, WallpaperChangeService::class.java).apply {
            action = WallpaperChangeService.ACTION_CHANGE_WALLPAPER
            putExtra(WallpaperChangeService.EXTRA_TARGET_SCREEN, targetScreen)
            putExtra(WallpaperChangeService.EXTRA_MODE, mode)
        }
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            android.util.Log.d(TAG, "✅ WallpaperChangeService started successfully")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ Failed to start WallpaperChangeService", e)
        }
        
        // Check if this is a repeating alarm (15-min or hourly) or daily alarm
        val intervalMillis = intent.getLongExtra("intervalMillis", 0L)
        if (intervalMillis > 0) {
            // CRITICAL FIX: setRepeating() is inexact on Android 5.1+
            // Must manually reschedule using setExactAndAllowWhileIdle() for precise timing
            android.util.Log.d(TAG, "Interval-based alarm - rescheduling for ${intervalMillis / 60000} minutes from now")
            rescheduleRepeatingAlarm(context, targetScreen, mode, intervalMillis)
        } else {
            // This is a daily alarm, reschedule for next day
            rescheduleDailyAlarm(context, targetScreen, mode, intent)
        }
    }
    
    /**
     * Reschedules a repeating alarm (15-min or hourly) using exact timing.
     * 
     * CRITICAL FIX: On Android 5.1+ (API 22+), setRepeating() became inexact and 
     * subject to batching, causing irregular intervals like 3min, 11min, 16min, etc.
     * 
     * Solution: Use setExactAndAllowWhileIdle() with manual rescheduling after each
     * alarm fires, just like we do for daily alarms. This guarantees exact intervals.
     */
    private fun rescheduleRepeatingAlarm(
        context: Context, 
        targetScreen: String, 
        mode: String, 
        intervalMillis: Long
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        if (alarmManager == null) {
            android.util.Log.e(TAG, "AlarmManager not available for rescheduling repeating alarm!")
            return
        }
        
        // Calculate next alarm time - exactly intervalMillis from now
        val nextTriggerTime = System.currentTimeMillis() + intervalMillis
        val intervalMinutes = intervalMillis / 60000
        
        android.util.Log.d(TAG, "Scheduling next alarm in exactly $intervalMinutes minutes")
        android.util.Log.d(TAG, "  Current time: ${System.currentTimeMillis()}")
        android.util.Log.d(TAG, "  Next trigger: $nextTriggerTime")
        
        // Create intent with same parameters for next alarm
        val alarmIntent = Intent(context, WallpaperAlarmReceiver::class.java).apply {
            putExtra("targetScreen", targetScreen)
            putExtra("mode", mode)
            putExtra("intervalMillis", intervalMillis)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE_REPEATING,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        try {
            // Use setExactAndAllowWhileIdle for precise timing even during Doze
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextTriggerTime,
                pendingIntent
            )
            
            android.util.Log.d(TAG, "✅ Repeating alarm rescheduled successfully")
            android.util.Log.d(TAG, "  Next alarm in: $intervalMinutes minutes")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ Failed to reschedule repeating alarm: ${e.message}", e)
        }
    }
    
    /**
     * Reschedules a daily alarm for the same time tomorrow.
     */
    private fun rescheduleDailyAlarm(context: Context, targetScreen: String, mode: String, originalIntent: Intent) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        if (alarmManager == null) {
            android.util.Log.e(TAG, "AlarmManager not available for rescheduling!")
            return
        }
        
        // Extract original target time from intent
        val targetHour = originalIntent.getIntExtra("targetHour", 9)
        val targetMinute = originalIntent.getIntExtra("targetMinute", 0)
        
        // Calculate next alarm time (same time tomorrow)
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, targetMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_MONTH, 1)
        }
        
        android.util.Log.d(TAG, "Rescheduling alarm for ${targetHour}:${String.format("%02d", targetMinute)} tomorrow")
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE_DAILY,
            originalIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        try {
            // Set alarm for next day at same time
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
            
            android.util.Log.d(TAG, "✅ Alarm rescheduled for next day")
            android.util.Log.d(TAG, "  Next alarm time (ms): ${calendar.timeInMillis}")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ Failed to reschedule alarm: ${e.message}", e)
        }
    }
    
    companion object {
        private const val TAG = "WallpaperAlarmReceiver"
        private const val ALARM_REQUEST_CODE_DAILY = 1001
        private const val ALARM_REQUEST_CODE_REPEATING = 1002
        
        /**
         * Unique work name for alarm-triggered wallpaper changes.
         * Using unique work with REPLACE policy prevents duplicate changes
         * if multiple alarms fire in quick succession.
         */
        const val ALARM_TRIGGERED_WORK_NAME = "alarm_triggered_wallpaper_change"
    }
}
