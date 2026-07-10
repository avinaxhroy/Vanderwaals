package me.avinas.vanderwaals.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.avinas.vanderwaals.data.datastore.SettingsDataStore
import me.avinas.vanderwaals.worker.WorkScheduler
import me.avinas.vanderwaals.worker.ChangeInterval

/**
 * Reschedules periodic workers on BOOT_COMPLETED and MY_PACKAGE_REPLACED.
 * Uses EntryPointAccessors (not @AndroidEntryPoint) because the system
 * instantiates manifest-declared receivers before Hilt may be ready.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    
    /**
     * Entry point interface for Hilt dependency injection.
     * This allows us to manually retrieve dependencies from the Hilt component
     * in manifest-declared receivers where @AndroidEntryPoint is unreliable.
     */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BootReceiverEntryPoint {
        fun workScheduler(): WorkScheduler
        fun settingsDataStore(): SettingsDataStore
    }
    
    companion object {
        private const val TAG = "BootCompletedReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.d(TAG, "Device boot completed - rescheduling workers")
                handleBootCompleted(context)
            }
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.d(TAG, "App updated - rescheduling workers")
                handleBootCompleted(context)
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
     * 
     * Dependencies are retrieved via EntryPointAccessors for reliable injection.
     */
    private fun handleBootCompleted(context: Context) {
        // Use goAsync() to extend receiver lifetime for background work
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                Log.d(TAG, "Starting worker rescheduling...")
                
                // Get dependencies via EntryPointAccessors (more reliable than @AndroidEntryPoint)
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    BootReceiverEntryPoint::class.java
                )
                val workScheduler = entryPoint.workScheduler()
                val settingsDataStore = entryPoint.settingsDataStore()
                
                Log.d(TAG, "✓ Dependencies retrieved via EntryPointAccessors")
                
                // Step 1: Initialize periodic workers (sync, cleanup)
                workScheduler.initializePeriodicWorkers()
                Log.d(TAG, "✓ Periodic workers initialized")
                
                // Step 2: Reschedule wallpaper change if auto-change is enabled
                val settings = settingsDataStore.settings.first()
                
                if (settings.changeInterval != "never") {
                    Log.d(TAG, "Auto-change enabled (${settings.changeInterval}), rescheduling wallpaper change worker")
                    
                    // Parse interval and reschedule
                    when (settings.changeInterval) {
                        "unlock" -> {
                            // ANDROID 15+ FIX: Use fromBootReceiver flag to enable deferred start
                            // This prevents ForegroundServiceStartNotAllowedException on API 35+
                            Log.d(TAG, "Unlock interval - starting monitor service with boot flag")
                            workScheduler.startWallpaperMonitorService(fromBootReceiver = true)
                        }
                        "15min" -> {
                            workScheduler.scheduleWallpaperChange(
                                interval = ChangeInterval.FIFTEEN_MINUTES,
                                targetScreen = settings.applyTo
                            )
                        }
                        "hourly" -> {
                            workScheduler.scheduleWallpaperChange(
                                interval = ChangeInterval.HOURLY,
                                targetScreen = settings.applyTo
                            )
                        }
                        "3hours" -> {
                            workScheduler.scheduleWallpaperChange(
                                interval = ChangeInterval.THREE_HOURS,
                                targetScreen = settings.applyTo
                            )
                        }
                        "6hours" -> {
                            workScheduler.scheduleWallpaperChange(
                                interval = ChangeInterval.SIX_HOURS,
                                targetScreen = settings.applyTo
                            )
                        }
                        "12hours" -> {
                            workScheduler.scheduleWallpaperChange(
                                interval = ChangeInterval.TWELVE_HOURS,
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
                        "3days" -> {
                            workScheduler.scheduleWallpaperChange(
                                interval = ChangeInterval.THREE_DAYS,
                                targetScreen = settings.applyTo
                            )
                        }
                        "7days" -> {
                            workScheduler.scheduleWallpaperChange(
                                interval = ChangeInterval.SEVEN_DAYS,
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
