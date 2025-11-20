package me.avinas.vanderwaals.worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.AndroidEntryPoint
import java.util.Calendar
import javax.inject.Inject

/**
 * BroadcastReceiver that handles daily wallpaper change alarms.
 * 
 * When AlarmManager fires the daily alarm, this receiver:
 * 1. Enqueues a OneTimeWorkRequest to WallpaperChangeWorker
 * 2. Reschedules the alarm for the next day
 * 
 * This ensures daily wallpaper changes happen at the exact scheduled time.
 */
@AndroidEntryPoint
class WallpaperAlarmReceiver : BroadcastReceiver() {
    
    @Inject
    lateinit var workManager: WorkManager
    
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
        
        // Create work request data
        val inputData = workDataOf(
            WallpaperChangeWorker.KEY_TARGET_SCREEN to targetScreen,
            WallpaperChangeWorker.KEY_MODE to mode
        )
        
        // Enqueue wallpaper change work
        val changeWork = OneTimeWorkRequestBuilder<WallpaperChangeWorker>()
            .setInputData(inputData)
            .build()
        
        workManager.enqueue(changeWork)
        android.util.Log.d(TAG, "WallpaperChangeWorker enqueued from alarm")
        
        // Check if this is a repeating alarm (15-min or hourly) or daily alarm
        val intervalMillis = intent.getLongExtra("intervalMillis", 0L)
        if (intervalMillis > 0) {
            // This is a repeating alarm (15-min or hourly), it will auto-repeat
            android.util.Log.d(TAG, "Repeating alarm - will auto-trigger in ${intervalMillis / 60000} minutes")
        } else {
            // This is a daily alarm, reschedule for next day
            rescheduleAlarm(context, targetScreen, mode, intent)
        }
    }
    
    private fun rescheduleAlarm(context: Context, targetScreen: String, mode: String, originalIntent: Intent) {
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
    }
}
