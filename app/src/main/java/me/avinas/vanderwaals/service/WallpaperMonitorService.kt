package me.avinas.vanderwaals.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.avinas.vanderwaals.R
import me.avinas.vanderwaals.core.SamsungPowerHelper
import me.avinas.vanderwaals.data.datastore.SettingsDataStore
import me.avinas.vanderwaals.data.repository.WallpaperRepository
import me.avinas.vanderwaals.domain.usecase.SelectNextWallpaperUseCase
import me.avinas.vanderwaals.feature.wallpaper.presentation.MainActivity
import java.io.File
import javax.inject.Inject

/**
 * Foreground service that registers a broadcast receiver for USER_PRESENT unlock events.
 * Used for the Every Unlock cadence mode on Android 8.0+.
 */
@AndroidEntryPoint
class WallpaperMonitorService : Service() {

    @Inject
    lateinit var settingsDataStore: SettingsDataStore
    
    @Inject
    lateinit var selectNextWallpaperUseCase: SelectNextWallpaperUseCase
    
    @Inject
    lateinit var wallpaperRepository: WallpaperRepository
    
    @Inject
    lateinit var engagementTracker: me.avinas.vanderwaals.domain.usecase.UserEngagementTracker
    
    @Inject
    lateinit var findCachedWallpaperUseCase: me.avinas.vanderwaals.domain.usecase.FindCachedWallpaperUseCase

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private var wakeLock: PowerManager.WakeLock? = null
    
    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT) {
                Log.d(TAG, "Device unlocked")
                handleUnlock()
            }
        }
    }

    companion object {
        private const val TAG = "WallpaperMonitorService"
        private const val CHANNEL_ID = me.avinas.vanderwaals.core.NotificationConstants.CHANNEL_WALLPAPER_MONITOR
        private const val NOTIFICATION_ID = me.avinas.vanderwaals.core.NotificationConstants.NOTIFICATION_ID_MONITOR
        
        private const val PREF_NAME = "vanderwaals_unlock"
        private const val KEY_LAST_TRIGGER = "last_trigger_time"
        private const val KEY_FAILURE_COUNT = "failure_count"
        private const val KEY_LAST_HEALTH_CHECK = "last_health_check"
        private const val MIN_INTERVAL_MS = 60_000L
        
        private const val HEALTH_CHECK_INTERVAL_MS = 300_000L
        private const val MAX_CONSECUTIVE_FAILURES = 5
        private const val WAKELOCK_TIMEOUT_MS = 2 * 60 * 1000L
    }
    
    private enum class ServiceState {
        IDLE,
        PROCESSING,
        RATE_LIMITED,
        ERROR
    }
    
    @Volatile
    private var currentState: ServiceState = ServiceState.IDLE

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created - Lifecycle: onCreate()")
        
        createNotificationChannel()
        currentState = ServiceState.IDLE

        // Call startForeground() early in onCreate to satisfy the 5-second system startup window.
        try {
            startForeground(NOTIFICATION_ID, createNotification("Monitoring device unlock"))
        } catch (e: Exception) {
            Log.e(TAG, "startForeground() failed in onCreate — stopping service", e)
            stopSelf()
            return
        }

        // Log Samsung device info for debugging
        SamsungPowerHelper.logDeviceInfo()
        
        if (SamsungPowerHelper.isBatteryRestricted(this)) {
            Log.w(TAG, "SAMSUNG BATTERY RESTRICTION DETECTED - unlock events may be delayed or missed!")
            Log.w(TAG, SamsungPowerHelper.getSamsungPowerInstructions())
        }
        
        acquireWakeLock()
        
        val filter = IntentFilter(Intent.ACTION_USER_PRESENT).apply {
            priority = IntentFilter.SYSTEM_HIGH_PRIORITY
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(unlockReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(unlockReceiver, filter)
        }
        Log.d(TAG, "Unlock receiver registered successfully (priority=${filter.priority})")
        
        performHealthCheck()
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started - Lifecycle: onStartCommand()")
        
        if (intent?.action == "STOP_SERVICE") {
            Log.d(TAG, "STOP_SERVICE action received - stopping service")
            stopSelf()
            return START_NOT_STICKY
        }
        
        serviceScope.launch {
            try {
                val settings = settingsDataStore.settings.first()
                if (settings.changeInterval != "unlock") {
                    Log.w(TAG, "Service started but interval is '${settings.changeInterval}', not 'unlock'. Stopping service.")
                    stopSelf()
                    return@launch
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking settings in onStartCommand", e)
            }
        }
        
        try {
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(NOTIFICATION_ID, createNotification("Monitoring device unlock"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update notification in onStartCommand", e)
        }
        
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed - Lifecycle: onDestroy()")
        
        try {
            unregisterReceiver(unlockReceiver)
            Log.d(TAG, "Unlock receiver unregistered successfully")
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Receiver was not registered")
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
        
        try {
            serviceScope.cancel("Service destroyed")
            Log.d(TAG, "Service scope cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling service scope", e)
        }
        
        releaseWakeLock()
        
        currentState = ServiceState.IDLE
        Log.d(TAG, "Service cleanup completed")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun handleUnlock() {
        serviceScope.launch {
            var targetScreen = "both"
            try {
                if (!shouldTriggerChange()) {
                    currentState = ServiceState.RATE_LIMITED
                    updateNotification("Rate limited - waiting")
                    Log.d(TAG, "Skipping change (rate limited)")
                    return@launch
                }

                val settings = settingsDataStore.settings.first()
                if (settings.changeInterval != "unlock") {
                    Log.w(TAG, "Interval is ${settings.changeInterval}, not 'unlock'. Stopping service.")
                    stopSelf()
                    return@launch
                }

                targetScreen = when (settings.applyTo) {
                    "lock_screen" -> "lock"
                    "home_screen" -> "home"
                    "both" -> "both"
                    "both_different" -> "both_different"
                    else -> "both"
                }

                currentState = ServiceState.PROCESSING
                updateNotification("Changing wallpaper...")
                Log.d(TAG, "Triggering wallpaper change for: $targetScreen")
                
                acquireWakeLock()
                changeWallpaper(targetScreen)
                
                resetFailureCount()
                currentState = ServiceState.IDLE
                updateNotification("Monitoring device unlock")
                updateLastTriggerTime()
                
            } catch (e: Exception) {
                currentState = ServiceState.ERROR
                incrementFailureCount()
                updateNotification("Error occurred")
                
                me.avinas.vanderwaals.core.ErrorHandler.handleWorkerError(
                    exception = e,
                    context = "UnlockWallpaperChange",
                    attemptCount = getFailureCount(),
                    metadata = mapOf(
                        "target_screen" to targetScreen,
                        "service_state" to currentState.name
                    )
                )
                
                if (shouldStopDueToFailures()) {
                    Log.e(TAG, "Stopping service due to excessive failures (${getFailureCount()} consecutive failures)")
                    stopSelf()
                }
            }
        }
    }
    
    private suspend fun changeWallpaper(targetScreen: String) {
        try {
            if (targetScreen == "both_different") {
                applyBothDifferentWallpapers()
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
                
                val cachedWallpaperResult = findCachedWallpaperUseCase(excludeWallpaperId = wallpaper.id)
                if (cachedWallpaperResult != null) {
                    val (cachedWallpaper, cachedFile) = cachedWallpaperResult
                    Log.d(TAG, "Using cached wallpaper fallback: ${cachedWallpaper.id}")
                    selectedWallpaper = cachedWallpaper
                    wallpaperFile = cachedFile
                } else {
                    Log.e(TAG, "No cached wallpapers available for offline fallback")
                    return
                }
            } else {
                wallpaperFile = downloadResult.getOrNull()!!
            }
            
            val applied = applyWallpaperToScreen(wallpaperFile!!, targetScreen)
            if (applied) {
                wallpaperRepository.recordWallpaperApplied(selectedWallpaper)
                engagementTracker.recordWallpaperChange()
                Log.d(TAG, "Wallpaper applied successfully: ${selectedWallpaper.id}")
            }
            
        } catch (e: Exception) {
            me.avinas.vanderwaals.core.ErrorHandler.handleWorkerError(
                exception = e,
                context = "ServiceWallpaperChange",
                attemptCount = 0,
                metadata = mapOf("target_screen" to targetScreen)
            )
        }
    }
    
    private suspend fun applyBothDifferentWallpapers() {
        var homeWallpaperId: String? = null
        
        val homeResult = selectNextWallpaperUseCase()
        if (homeResult.isSuccess) {
            var actualHomeWallpaper = homeResult.getOrNull()!!
            homeWallpaperId = actualHomeWallpaper.id
            var homeFile: File?
            
            val homeDownload = wallpaperRepository.downloadWallpaper(actualHomeWallpaper)
            if (homeDownload.isSuccess) {
                homeFile = homeDownload.getOrNull()!!
            } else {
                Log.d(TAG, "Home wallpaper download failed, attempting cached fallback")
                val cachedHomeResult = findCachedWallpaperUseCase(excludeWallpaperId = actualHomeWallpaper.id)
                if (cachedHomeResult != null) {
                    val (cachedWallpaper, cachedFile) = cachedHomeResult
                    Log.d(TAG, "Cached fallback for home: ${cachedWallpaper.id}")
                    actualHomeWallpaper = cachedWallpaper
                    homeWallpaperId = cachedWallpaper.id
                    homeFile = cachedFile
                } else {
                    Log.e(TAG, "No cached wallpapers for home screen")
                    return
                }
            }
            
            if (applyWallpaperToScreen(homeFile!!, "home")) {
                wallpaperRepository.recordWallpaperApplied(actualHomeWallpaper)
            }
        }
        
        val lockResult = selectNextWallpaperUseCase(excludeWallpaperId = homeWallpaperId)
        if (lockResult.isSuccess) {
            var actualLockWallpaper = lockResult.getOrNull()!!
            var lockFile: File?
            
            val lockDownload = wallpaperRepository.downloadWallpaper(actualLockWallpaper)
            if (lockDownload.isSuccess) {
                lockFile = lockDownload.getOrNull()!!
            } else {
                Log.d(TAG, "Lock wallpaper download failed, attempting cached fallback")
                val cachedLockResult = findCachedWallpaperUseCase(excludeWallpaperId = actualLockWallpaper.id)
                if (cachedLockResult != null) {
                    val (cachedWallpaper, cachedFile) = cachedLockResult
                    Log.d(TAG, "Cached fallback for lock: ${cachedWallpaper.id}")
                    actualLockWallpaper = cachedWallpaper
                    lockFile = cachedFile
                } else {
                    Log.e(TAG, "No cached wallpapers for lock screen")
                    return
                }
            }
            
            if (applyWallpaperToScreen(lockFile!!, "lock")) {
                wallpaperRepository.recordWallpaperApplied(actualLockWallpaper)
            }
        }
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
                "home" -> wallpaperManager.setBitmap(processedBitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                "lock" -> wallpaperManager.setBitmap(processedBitmap, null, true, WallpaperManager.FLAG_LOCK)
                "both" -> wallpaperManager.setBitmap(processedBitmap, null, true, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
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

    private fun shouldTriggerChange(): Boolean {
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val lastTrigger = prefs.getLong(KEY_LAST_TRIGGER, 0L)
        val now = System.currentTimeMillis()
        return (now - lastTrigger) >= MIN_INTERVAL_MS
    }

    private fun updateLastTriggerTime() {
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_TRIGGER, System.currentTimeMillis()).apply()
    }
    
    private fun getFailureCount(): Int {
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_FAILURE_COUNT, 0)
    }
    
    private fun incrementFailureCount() {
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val currentCount = prefs.getInt(KEY_FAILURE_COUNT, 0)
        prefs.edit().putInt(KEY_FAILURE_COUNT, currentCount + 1).apply()
        Log.w(TAG, "Failure count incremented to ${currentCount + 1}")
    }
    
    private fun resetFailureCount() {
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_FAILURE_COUNT, 0).apply()
    }
    
    private fun shouldStopDueToFailures(): Boolean {
        return getFailureCount() >= MAX_CONSECUTIVE_FAILURES
    }
    
    private fun performHealthCheck() {
        serviceScope.launch {
            val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val lastHealthCheck = prefs.getLong(KEY_LAST_HEALTH_CHECK, 0L)
            val now = System.currentTimeMillis()
            
            if ((now - lastHealthCheck) < HEALTH_CHECK_INTERVAL_MS) {
                return@launch
            }
            
            try {
                // Verify settings are still correct
                val settings = settingsDataStore.settings.first()
                if (settings.changeInterval != "unlock") {
                    Log.w(TAG, "Health check failed: interval changed to '${settings.changeInterval}'")
                    stopSelf()
                    return@launch
                }
                
                Log.d(TAG, "Health check passed - State: $currentState, Failures: ${getFailureCount()}")
                
                prefs.edit().putLong(KEY_LAST_HEALTH_CHECK, now).apply()
                
            } catch (e: Exception) {
                Log.e(TAG, "Health check error", e)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Wallpaper Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps app alive to detect device unlock"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(status: String = "Monitoring device unlock"): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        // Stop service action
        val stopIntent = Intent(this, WallpaperMonitorService::class.java).apply {
            action = "STOP_SERVICE"
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Wallpaper Monitor Active")
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
    
    /**
     * Acquires a partial wakelock.
     * SAMSUNG FIX: Helps keep the service alive on aggressive One UI power management.
     */
    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Vanderwaals:WallpaperMonitor"
            )
        }
        
        wakeLock?.let { wl ->
            if (!wl.isHeld) {
                wl.acquire(WAKELOCK_TIMEOUT_MS)
                Log.d(TAG, "Wakelock acquired (timeout=${WAKELOCK_TIMEOUT_MS}ms)")
            }
        }
    }
    
    /**
     * Releases the wakelock to allow CPU to sleep.
     * Called in onDestroy and when service is no longer needed.
     */
    private fun releaseWakeLock() {
        wakeLock?.let { wl ->
            if (wl.isHeld) {
                wl.release()
                Log.d(TAG, "Wakelock released")
            }
        }
        wakeLock = null
    }
}
