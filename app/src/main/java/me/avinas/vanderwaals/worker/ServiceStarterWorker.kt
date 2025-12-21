package me.avinas.vanderwaals.worker

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors
import me.avinas.vanderwaals.data.datastore.SettingsDataStore
import kotlinx.coroutines.flow.first

/**
 * One-shot worker that starts the WallpaperMonitorService.
 * 
 * **Purpose:**
 * On Android 15+ (API 35+), foreground services with `dataSync` type cannot be started
 * directly from BOOT_COMPLETED broadcast receivers. This worker provides a deferred
 * mechanism to start the service after boot completes.
 * 
 * **How it works:**
 * 1. BootCompletedReceiver schedules this worker with a short delay (5-10 seconds)
 * 2. Worker runs after boot completes (when foreground services are allowed)
 * 3. Worker starts WallpaperMonitorService for "Every Unlock" mode
 * 
 * **Key Benefits:**
 * - Avoids ForegroundServiceStartNotAllowedException on Android 15+
 * - Service starts on first user interaction (unlock)
 * - All subsequent unlocks trigger wallpaper changes as expected
 * 
 * @see me.avinas.vanderwaals.receiver.BootCompletedReceiver
 * @see me.avinas.vanderwaals.service.WallpaperMonitorService
 */
class ServiceStarterWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "ServiceStarterWorker"
        const val WORK_NAME = "service_starter_work"
    }

    /**
     * Entry point for Hilt dependency injection.
     */
    @dagger.hilt.EntryPoint
    @dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
    interface ServiceStarterEntryPoint {
        fun settingsDataStore(): SettingsDataStore
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "ServiceStarterWorker running - attempting to start WallpaperMonitorService")
        
        return try {
            // Get settings to verify we should still start the service
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                ServiceStarterEntryPoint::class.java
            )
            val settingsDataStore = entryPoint.settingsDataStore()
            val settings = settingsDataStore.settings.first()
            
            if (settings.changeInterval != "unlock") {
                Log.d(TAG, "Interval is '${settings.changeInterval}', not 'unlock' - skipping service start")
                return Result.success()
            }
            
            // Start the foreground service
            val intent = Intent(applicationContext, me.avinas.vanderwaals.service.WallpaperMonitorService::class.java)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(intent)
            } else {
                applicationContext.startService(intent)
            }
            
            Log.d(TAG, "✅ WallpaperMonitorService started successfully via deferred worker")
            Result.success()
            
        } catch (e: android.app.ForegroundServiceStartNotAllowedException) {
            // This can still happen if the device is in a restricted state
            // Retry with backoff - the service will eventually start when conditions allow
            Log.w(TAG, "⚠️ ForegroundServiceStartNotAllowedException - will retry", e)
            Result.retry()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start WallpaperMonitorService", e)
            // Don't retry on other exceptions - log and give up
            Result.failure()
        }
    }
}
