package me.avinas.vanderwaals.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.avinas.vanderwaals.data.datastore.SettingsDataStore
import me.avinas.vanderwaals.worker.WorkScheduler
import me.avinas.vanderwaals.worker.ChangeInterval
import javax.inject.Inject

/**
 * Broadcast receiver that responds to device boot completion.
 * 
 * **Purpose:**
 * WorkManager's scheduled tasks are persisted across reboots, but on some
 * devices (especially with aggressive battery optimization), scheduled work
 * may not resume automatically. This receiver explicitly reschedules all
 * periodic workers to ensure reliability.
 * 
 * **Triggers:**
 * - ACTION_BOOT_COMPLETED: Device finished booting
 * - ACTION_MY_PACKAGE_REPLACED: App was updated
 * 
 * **Actions:**
 * 1. Read current settings from DataStore
 * 2. If auto-change is enabled, reschedule wallpaper change worker
 * 3. Reschedule periodic sync and cleanup workers
 * 4. Log diagnostic information
 * 
 * **Manifest Registration:**
 * ```xml
 * <receiver android:name=".receiver.BootCompletedReceiver"
 *     android:enabled="true"
 *     android:exported="false">
 *     <intent-filter>
 *         <action android:name="android.intent.action.BOOT_COMPLETED" />
 *         <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
 *     </intent-filter>
 * </receiver>
 * ```
 * 
 * **Permissions Required:**
 * ```xml
 * <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
 * ```
 * 
 * **Testing:**
 * ```bash
 * # Simulate boot completed
 * adb shell am broadcast -a android.intent.action.BOOT_COMPLETED
 * 
 * # Check logs
 * adb logcat | grep BootCompletedReceiver
 * ```
 * 
 * @see WorkScheduler
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {
    
    @Inject
    lateinit var workScheduler: WorkScheduler
    
    @Inject
    lateinit var settingsDataStore: SettingsDataStore
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    companion object {
        private const val TAG = "BootCompletedReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.d(TAG, "Device boot completed - rescheduling workers")
                handleBootCompleted()
            }
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.d(TAG, "App updated - rescheduling workers")
                handleBootCompleted()
            }
            else -> {
                Log.w(TAG, "Received unexpected action: ${intent.action}")
            }
        }
    }
    
    /**
     * Handles boot completion by rescheduling all workers.
     * 
     * Uses goAsync() to perform work in background coroutine since
     * BroadcastReceiver.onReceive() must return quickly.
     */
    private fun handleBootCompleted() {
        // Use goAsync() to extend receiver lifetime for background work
        val pendingResult = goAsync()
        
        scope.launch {
            try {
                Log.d(TAG, "Starting worker rescheduling...")
                
                // Step 1: Initialize periodic workers (sync, cleanup)
                workScheduler.initializePeriodicWorkers()
                Log.d(TAG, "✓ Periodic workers initialized")
                
                // Step 2: Reschedule wallpaper change if auto-change is enabled
                val settings = settingsDataStore.settings.first()
                
                if (settings.changeInterval != "never") {
                    Log.d(TAG, "Auto-change enabled (${settings.changeInterval}), rescheduling wallpaper change worker")
                    
                    // Parse interval and reschedule
                    when (settings.changeInterval) {
                        "15min", "unlock" -> {
                            workScheduler.scheduleWallpaperChange(
                                interval = ChangeInterval.EVERY_15_MINUTES,
                                targetScreen = settings.applyTo
                            )
                        }
                        "hourly" -> {
                            workScheduler.scheduleWallpaperChange(
                                interval = ChangeInterval.HOURLY,
                                targetScreen = settings.applyTo
                            )
                        }
                        "daily" -> {
                            // Use saved time if available, otherwise default to 9:00 AM
                            val time = settings.dailyTime ?: java.time.LocalTime.of(9, 0)
                            
                            workScheduler.scheduleWallpaperChange(
                                interval = ChangeInterval.DAILY,
                                time = time,
                                targetScreen = settings.applyTo
                            )
                        }
                    }
                    
                    Log.d(TAG, "✓ Wallpaper change worker rescheduled (${settings.changeInterval})")
                } else {
                    Log.d(TAG, "Auto-change disabled, skipping wallpaper change worker")
                }
                
                // Step 3: Log diagnostic information
                val dailyTimeStr = settings.dailyTime?.let { String.format("%02d:%02d", it.hour, it.minute) } ?: "Not set"
                Log.d(TAG, """
                    ╔═══════════════════════════════════════════════════════════╗
                    ║ BOOT COMPLETED - WORKERS RESCHEDULED                      ║
                    ╠═══════════════════════════════════════════════════════════╣
                    ║ Auto-Change: ${settings.changeInterval.padEnd(43)}║
                    ║ Apply To: ${settings.applyTo.padEnd(46)}║
                    ║ Daily Time: ${dailyTimeStr.padEnd(44)}║
                    ║ Mode: ${settings.mode.padEnd(50)}║
                    ╚═══════════════════════════════════════════════════════════╝
                """.trimIndent())
                
            } catch (e: Exception) {
                Log.e(TAG, "Error rescheduling workers after boot", e)
            } finally {
                // Signal completion to allow receiver to finish
                pendingResult.finish()
            }
        }
    }
}
