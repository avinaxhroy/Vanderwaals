package me.avinas.vanderwaals.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.avinas.vanderwaals.worker.WorkScheduler
import javax.inject.Inject

/**
 * Triggers a wallpaper change on ACTION_USER_PRESENT when
 * "Every unlock" mode is enabled. Rate-limited to once per minute.
 */
@AndroidEntryPoint
class DeviceUnlockReceiver : BroadcastReceiver() {
    
    @Inject
    lateinit var workScheduler: WorkScheduler
    
    @Inject
    lateinit var settingsDataStore: me.avinas.vanderwaals.data.datastore.SettingsDataStore
    
    companion object {
        private const val TAG = "DeviceUnlockReceiver"
        
        private const val PREF_NAME = "vanderwaals_unlock"
        private const val KEY_LAST_TRIGGER = "last_trigger_time"
        
        /**
         * Minimum interval between wallpaper changes (1 minute).
         * Prevents excessive changes from rapid lock/unlock cycles.
         */
        private const val MIN_INTERVAL_MS = 60_000L // 1 minute
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_USER_PRESENT) {
            return
        }
        
        Log.d(TAG, "Device unlocked, checking if wallpaper change is needed")
        
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        
        scope.launch {
            try {
                val settings = settingsDataStore.settings.first()
                val intervalSetting = settings.changeInterval
                
                if (intervalSetting != "unlock") {
                    Log.d(TAG, "Skipping change - user interval is '$intervalSetting'")
                    return@launch
                }
                
                Log.d(TAG, "User has 'Every unlock' mode enabled")
                
                if (!shouldTriggerChange(context)) {
                    Log.d(TAG, "Skipping change (rate limited)")
                    return@launch
                }
                
                val targetScreen = when (settings.applyTo) {
                    "lock_screen" -> "lock"
                    "home_screen" -> "home"
                    "both" -> "both"
                    "both_different" -> "both_different"
                    else -> "both"  // Default to both if not set
                }
                
                Log.d(TAG, "Triggering wallpaper change for target screen: $targetScreen")
                
                workScheduler.triggerImmediateWallpaperChange(targetScreen)
                
                updateLastTriggerTime(context)
                
                Log.d(TAG, "Wallpaper change triggered")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error triggering wallpaper change", e)
            } finally {
                // Must call finish() to indicate broadcast is complete
                pendingResult.finish()
                scope.cancel()
            }
        }
    }
    
    private fun shouldTriggerChange(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val lastTrigger = prefs.getLong(KEY_LAST_TRIGGER, 0L)
        val now = System.currentTimeMillis()
        
        return (now - lastTrigger) >= MIN_INTERVAL_MS
    }
    
    private fun updateLastTriggerTime(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_TRIGGER, System.currentTimeMillis()).apply()
    }
}
