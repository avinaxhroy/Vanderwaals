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
    
    private fun handleBootCompleted(context: Context) {
        // goAsync(): onReceive() must return quickly, so do work off the main thread
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                Log.d(TAG, "Starting worker rescheduling...")
                
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    BootReceiverEntryPoint::class.java
                )
                val workScheduler = entryPoint.workScheduler()
                val settingsDataStore = entryPoint.settingsDataStore()
                
                Log.d(TAG, "✓ Dependencies retrieved via EntryPointAccessors")
                
                workScheduler.initializePeriodicWorkers()
                
                val settings = settingsDataStore.settings.first()
                if (settings.changeInterval != "never") {
                    Log.d(TAG, "Auto-change enabled (${settings.changeInterval}), rescheduling wallpaper change worker")
                    
                    when (settings.changeInterval) {
                        "unlock" -> {
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
                pendingResult.finish()
            }
        }
    }
}
