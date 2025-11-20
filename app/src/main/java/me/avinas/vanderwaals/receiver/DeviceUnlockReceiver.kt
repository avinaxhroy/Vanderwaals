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
 * BroadcastReceiver for device unlock events to trigger wallpaper changes.
 * 
 * Listens for ACTION_USER_PRESENT broadcast which is sent when the user
 * unlocks their device (after entering PIN/pattern/password).
 * 
 * **Usage:**
 * When user selects "Every unlock" as wallpaper change frequency,
 * this receiver is enabled in AndroidManifest and triggers wallpaper
 * change on each unlock.
 * 
 * **Registration:**
 * Must be registered in AndroidManifest.xml with:
 * ```xml
 * <receiver
 *     android:name=".receiver.DeviceUnlockReceiver"
 *     android:enabled="true"
 *     android:exported="false">
 *     <intent-filter>
 *         <action android:name="android.intent.action.USER_PRESENT" />
 *     </intent-filter>
 * </receiver>
 * ```
 * 
 * **Behavior:**
 * - Triggers WallpaperChangeWorker immediately
 * - Only active when "Every unlock" mode is enabled
 * - Uses goAsync() for asynchronous work
 * - Respects rate limiting (max once per minute)
 * 
 * **Notes:**
 * - ACTION_USER_PRESENT is only sent after user authentication
 * - ACTION_SCREEN_ON is sent on every screen wake (too frequent)
 * - This approach balances freshness with battery/data usage
 * 
 * @see me.avinas.vanderwaals.worker.WallpaperChangeWorker
 * @see me.avinas.vanderwaals.worker.WorkScheduler
 */
@AndroidEntryPoint
class DeviceUnlockReceiver : BroadcastReceiver() {
    
    @Inject
    lateinit var workScheduler: WorkScheduler
    
    @Inject
    lateinit var settingsDataStore: me.avinas.vanderwaals.data.datastore.SettingsDataStore
    
    companion object {
        private const val TAG = "DeviceUnlockReceiver"
        
        /**
         * Shared preferences key for last trigger time.
         */
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
        
        // Use goAsync() for asynchronous work
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        
        scope.launch {
            try {
                // CRITICAL: Check if user has "Every unlock" mode enabled
                val settings = settingsDataStore.settings.first()
                val intervalSetting = settings.changeInterval
                
                if (intervalSetting != "unlock") {
                    Log.d(TAG, "Skipping change - user interval is '$intervalSetting', not 'unlock'")
                    return@launch
                }
                
                Log.d(TAG, "User has 'Every unlock' mode enabled")
                
                // Check rate limiting
                if (!shouldTriggerChange(context)) {
                    Log.d(TAG, "Skipping change (rate limited)")
                    return@launch
                }
                
                // CRITICAL FIX: Load user's "Apply To" setting and pass to worker
                val targetScreen = when (settings.applyTo) {
                    "lock_screen" -> "lock"
                    "home_screen" -> "home"
                    "both" -> "both"
                    else -> "both"  // Default to both if not set
                }
                
                Log.d(TAG, "Triggering wallpaper change for target screen: $targetScreen")
                
                // Trigger immediate wallpaper change with correct target screen
                workScheduler.triggerImmediateWallpaperChange(targetScreen)
                
                // Update last trigger time
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
    
    /**
     * Checks if enough time has passed since last trigger.
     * 
     * Implements rate limiting to prevent excessive wallpaper changes
     * from rapid lock/unlock cycles.
     * 
     * @param context Application context
     * @return true if change should be triggered, false otherwise
     */
    private fun shouldTriggerChange(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val lastTrigger = prefs.getLong(KEY_LAST_TRIGGER, 0L)
        val now = System.currentTimeMillis()
        
        return (now - lastTrigger) >= MIN_INTERVAL_MS
    }
    
    /**
     * Updates the last trigger time in shared preferences.
     * 
     * @param context Application context
     */
    private fun updateLastTriggerTime(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_TRIGGER, System.currentTimeMillis()).apply()
    }
}
