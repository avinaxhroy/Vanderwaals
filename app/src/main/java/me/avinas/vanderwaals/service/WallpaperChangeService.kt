package me.avinas.vanderwaals.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import me.avinas.vanderwaals.R
import me.avinas.vanderwaals.data.datastore.SettingsDataStore
import me.avinas.vanderwaals.data.repository.WallpaperRepository
import me.avinas.vanderwaals.domain.usecase.FindCachedWallpaperUseCase
import me.avinas.vanderwaals.domain.usecase.SelectNextWallpaperUseCase
import me.avinas.vanderwaals.domain.usecase.UserEngagementTracker
import me.avinas.vanderwaals.feature.wallpaper.presentation.MainActivity
import me.avinas.vanderwaals.worker.WallpaperAlarmReceiver
import me.avinas.vanderwaals.worker.WallpaperChangeWorker
import java.io.File
import javax.inject.Inject

/**
 * Short-lived foreground service for scheduled wallpaper changes.
 * 
 * Started by [WallpaperAlarmReceiver] when an AlarmManager alarm fires. A foreground
 * service is used instead of WorkManager so the change still runs reliably even when
 * the app was killed by the user.
 * 
 * @see me.avinas.vanderwaals.worker.WallpaperAlarmReceiver
 */
@AndroidEntryPoint
class WallpaperChangeService : Service() {
    
    @Inject
    lateinit var settingsDataStore: SettingsDataStore
    
    @Inject
    lateinit var selectNextWallpaperUseCase: SelectNextWallpaperUseCase
    
    @Inject
    lateinit var wallpaperRepository: WallpaperRepository
    
    @Inject
    lateinit var engagementTracker: UserEngagementTracker
    
    @Inject
    lateinit var findCachedWallpaperUseCase: FindCachedWallpaperUseCase
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null
    
    companion object {
        private const val TAG = "WallpaperChangeService"
        private const val CHANNEL_ID = me.avinas.vanderwaals.core.NotificationConstants.CHANNEL_WALLPAPER_CHANGE
        private const val NOTIFICATION_ID = me.avinas.vanderwaals.core.NotificationConstants.NOTIFICATION_ID_CHANGE
        private const val WAKELOCK_TIMEOUT_MS = 60_000L // 1 minute max
        private const val CHANGE_TIMEOUT_MS = 45_000L // 45s max — prevents ForegroundServiceDidNotStopInTimeException
        
        // Intent action
        const val ACTION_CHANGE_WALLPAPER = "me.avinas.vanderwaals.ACTION_CHANGE_WALLPAPER"
        
        // Intent extras
        const val EXTRA_TARGET_SCREEN = "target_screen"
        const val EXTRA_MODE = "mode"
        
        // Target screen values
        const val TARGET_HOME = "home"
        const val TARGET_LOCK = "lock"
        const val TARGET_BOTH = "both"
        const val TARGET_BOTH_DIFFERENT = "both_different"
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Call startForeground() in onCreate within 5s window of startForegroundService().
        try {
            startForeground(NOTIFICATION_ID, createNotification("Changing wallpaper..."))
        } catch (e: Exception) {
            Log.e(TAG, "startForeground() failed in onCreate — stopping service", e)
            stopSelf()
            return
        }
        acquireWakeLock()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started - action: ${intent?.action}")
        
        if (intent?.action == ACTION_CHANGE_WALLPAPER) {
            val targetScreen = intent.getStringExtra(EXTRA_TARGET_SCREEN) ?: TARGET_BOTH
            val mode = intent.getStringExtra(EXTRA_MODE) ?: "vanderwaals"
            
            Log.d(TAG, "Starting wallpaper change - target: $targetScreen, mode: $mode")
            
            serviceScope.launch {
                try {
                    // ponytail: withTimeout guarantees stopSelf() is reached even if
                    // downloadWallpaper hangs on network I/O — prevents FGS timeout crash
                    withTimeout(CHANGE_TIMEOUT_MS) {
                        changeWallpaper(targetScreen)
                    }
                    Log.d(TAG, "Wallpaper change completed successfully")
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    Log.e(TAG, "Wallpaper change timed out after ${CHANGE_TIMEOUT_MS}ms")
                } catch (e: Exception) {
                    Log.e(TAG, "Wallpaper change failed", e)
                } finally {
                    withContext(Dispatchers.Main) {
                        stopSelf()
                    }
                }
            }
        } else {
            Log.w(TAG, "Unknown action: ${intent?.action}")
            stopSelf()
        }
        
        return START_NOT_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        releaseWakeLock()
        serviceScope.cancel("Service destroyed")
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private suspend fun changeWallpaper(targetScreen: String) {
        // "Both But Different" needs its own flow
        if (targetScreen == TARGET_BOTH_DIFFERENT) {
            changeBothDifferentWallpapers()
            return
        }
        
        // Re-check auto-change is still enabled; settings may have changed since scheduling
        val settings = settingsDataStore.settings.first()
        if (settings.changeInterval == "never") {
            Log.d(TAG, "Auto-change is disabled, skipping")
            return
        }
        
        val actualTarget = when (settings.applyTo) {
            "lock_screen" -> TARGET_LOCK
            "home_screen" -> TARGET_HOME
            "both" -> TARGET_BOTH
            "both_different" -> TARGET_BOTH_DIFFERENT
            else -> TARGET_BOTH
        }
        
        if (actualTarget == TARGET_BOTH_DIFFERENT) {
            changeBothDifferentWallpapers()
            return
        }
        
        val wallpaperResult = selectNextWallpaperUseCase()
        if (wallpaperResult.isFailure) {
            Log.w(TAG, "No wallpaper selected: ${wallpaperResult.exceptionOrNull()?.message}")
            return
        }
        
        val wallpaper = wallpaperResult.getOrNull()!!
        
        var selectedWallpaper = wallpaper
        var wallpaperFile: File?
        
        val downloadResult = wallpaperRepository.downloadWallpaper(wallpaper)
        if (downloadResult.isFailure) {
            Log.w(TAG, "Failed to download wallpaper: ${downloadResult.exceptionOrNull()?.message}")
            
            Log.d(TAG, "Attempting offline fallback - searching for cached wallpapers...")
            val cachedWallpaperResult = findCachedWallpaperUseCase(excludeWallpaperId = wallpaper.id)
            
            if (cachedWallpaperResult != null) {
                val (cachedWallpaper, cachedFile) = cachedWallpaperResult
                Log.d(TAG, "Offline fallback successful - using cached wallpaper: ${cachedWallpaper.id}")
                selectedWallpaper = cachedWallpaper
                wallpaperFile = cachedFile
            } else {
                Log.e(TAG, "No cached wallpapers available for offline fallback")
                return
            }
        } else {
            wallpaperFile = downloadResult.getOrNull()!!
        }
        
        val applied = applyWallpaperToScreen(wallpaperFile!!, actualTarget)
        if (applied) {
            wallpaperRepository.recordWallpaperApplied(selectedWallpaper)
            engagementTracker.recordWallpaperChange()
            Log.d(TAG, "Wallpaper applied successfully: ${selectedWallpaper.id}")
            
            updateNotification("Wallpaper changed!")
        }
    }
    
    private suspend fun changeBothDifferentWallpapers() {
        // Track home wallpaper ID for excluding in lock screen selection
        var homeWallpaperId: String? = null
        
        val homeResult = selectNextWallpaperUseCase()
        if (homeResult.isSuccess) {
            var actualHomeWallpaper = homeResult.getOrNull()!!
            homeWallpaperId = actualHomeWallpaper.id  // Store for lock screen exclusion
            var homeFile: File?
            
            val homeDownload = wallpaperRepository.downloadWallpaper(actualHomeWallpaper)
            if (homeDownload.isSuccess) {
                homeFile = homeDownload.getOrNull()!!
            } else {
                Log.d(TAG, "Home wallpaper download failed, attempting offline fallback")
                val cachedHomeResult = findCachedWallpaperUseCase(excludeWallpaperId = actualHomeWallpaper.id)
                if (cachedHomeResult != null) {
                    val (cachedWallpaper, cachedFile) = cachedHomeResult
                    Log.d(TAG, "Offline fallback for home - using cached: ${cachedWallpaper.id}")
                    actualHomeWallpaper = cachedWallpaper
                    homeWallpaperId = cachedWallpaper.id
                    homeFile = cachedFile
                } else {
                    Log.e(TAG, "No cached wallpapers for home screen")
                    return
                }
            }
            
            if (applyWallpaperToScreen(homeFile!!, TARGET_HOME)) {
                wallpaperRepository.recordWallpaperApplied(actualHomeWallpaper)
            }
        }
        
        // Lock wallpaper: exclude home wallpaper ID so the two differ
        val lockResult = selectNextWallpaperUseCase(excludeWallpaperId = homeWallpaperId)
        if (lockResult.isSuccess) {
            var actualLockWallpaper = lockResult.getOrNull()!!
            var lockFile: File?
            
            val lockDownload = wallpaperRepository.downloadWallpaper(actualLockWallpaper)
            if (lockDownload.isSuccess) {
                lockFile = lockDownload.getOrNull()!!
            } else {
                Log.d(TAG, "Lock wallpaper download failed, attempting offline fallback")
                val cachedLockResult = findCachedWallpaperUseCase(excludeWallpaperId = actualLockWallpaper.id)
                if (cachedLockResult != null) {
                    val (cachedWallpaper, cachedFile) = cachedLockResult
                    Log.d(TAG, "Offline fallback for lock - using cached: ${cachedWallpaper.id}")
                    actualLockWallpaper = cachedWallpaper
                    lockFile = cachedFile
                } else {
                    Log.e(TAG, "No cached wallpapers for lock screen")
                    return
                }
            }
            
            if (applyWallpaperToScreen(lockFile!!, TARGET_LOCK)) {
                wallpaperRepository.recordWallpaperApplied(actualLockWallpaper)
            }
        }
        
        engagementTracker.recordWallpaperChange()
        updateNotification("Wallpaper changed!")
    }
    
    private suspend fun applyWallpaperToScreen(file: File, targetScreen: String): Boolean {
        var bitmap: android.graphics.Bitmap? = null
        var processedBitmap: android.graphics.Bitmap? = null
        
        return try {
            val wallpaperManager = WallpaperManager.getInstance(applicationContext)
            
            // Use BitmapManager for safe bitmap loading with OOM protection
            bitmap = me.avinas.vanderwaals.core.BitmapManager.loadBitmap(file)
            if (bitmap == null) return false
            
            val screenSize = me.avinas.vanderwaals.core.getDeviceScreenSize(applicationContext)
            processedBitmap = me.avinas.vanderwaals.core.SmartCrop.smartCropBitmapAsync(
                source = bitmap,
                targetWidth = screenSize.width,
                targetHeight = screenSize.height,
                mode = me.avinas.vanderwaals.core.SmartCrop.CropMode.AUTO
            )
            
            // Recycle original bitmap if different from processed
            if (bitmap !== processedBitmap) {
                me.avinas.vanderwaals.core.BitmapManager.recycleSafely(bitmap)
                bitmap = null
            }
            
            when (targetScreen) {
                TARGET_HOME -> wallpaperManager.setBitmap(processedBitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                TARGET_LOCK -> wallpaperManager.setBitmap(processedBitmap, null, true, WallpaperManager.FLAG_LOCK)
                TARGET_BOTH -> wallpaperManager.setBitmap(processedBitmap, null, true, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
            }
            
            me.avinas.vanderwaals.core.BitmapManager.recycleSafely(processedBitmap)
            processedBitmap = null
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply wallpaper", e)
            false
        } finally {
            // Ensure bitmaps are recycled even if exception occurs
            me.avinas.vanderwaals.core.BitmapManager.recycleSafely(bitmap)
            me.avinas.vanderwaals.core.BitmapManager.recycleSafely(processedBitmap)
        }
    }
    
    private fun enqueueFallbackWork(targetScreen: String, mode: String) {
        try {
            val work = OneTimeWorkRequestBuilder<WallpaperChangeWorker>()
                .setInputData(workDataOf(
                    WallpaperChangeWorker.KEY_TARGET_SCREEN to targetScreen,
                    WallpaperChangeWorker.KEY_MODE to mode,
                    WallpaperChangeWorker.KEY_IS_MANUAL_CHANGE to false
                ))
                .build()
            WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                WallpaperAlarmReceiver.ALARM_TRIGGERED_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                work
            )
            Log.d(TAG, "Fallback WallpaperChangeWorker enqueued")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enqueue fallback work", e)
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Wallpaper Changes",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when wallpaper is being changed"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(status: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Vanderwaals")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        }
    
    private fun updateNotification(status: String) {
        val notification = createNotification(status)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Vanderwaals:WallpaperChange"
            )
        }
        
        wakeLock?.let { wl ->
            if (!wl.isHeld) {
                wl.acquire(WAKELOCK_TIMEOUT_MS)
                Log.d(TAG, "Wake lock acquired (timeout=${WAKELOCK_TIMEOUT_MS}ms)")
            }
        }
    }
    
    private fun releaseWakeLock() {
        wakeLock?.let { wl ->
            if (wl.isHeld) {
                wl.release()
                Log.d(TAG, "Wake lock released")
            }
        }
        wakeLock = null
    }
}
