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
 * Foreground Service to monitor device unlock events.
 * 
 * Required for Android 8.0+ (API 26+) because apps cannot register for
 * ACTION_USER_PRESENT in the manifest. This service keeps the app alive
 * (foreground) so it can dynamically register the receiver.
 * 
 * IMPORTANT: This service ONLY handles "unlock" interval mode.
 * The "15min" interval uses AlarmManager (not this service).
 * 
 * OPTIMIZED: Executes wallpaper change logic directly to avoid WorkManager latency.
 * 
 * SAMSUNG FIX (Dec 2025): Added wakelock and Samsung-specific receiver registration
 * to handle aggressive One UI power management on S23 and newer devices.
 * 
 * @see me.avinas.vanderwaals.worker.WorkScheduler
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
    
    /**
     * Wakelock to prevent CPU from sleeping during unlock detection.
     * SAMSUNG FIX: Helps keep the service alive on aggressive One UI power management.
     */
    private var wakeLock: PowerManager.WakeLock? = null
    
    // Internal receiver for unlock events
    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT) {
                Log.d(TAG, "Device unlocked (received via Service)")
                handleUnlock()
            }
        }
    }

    companion object {
        private const val TAG = "WallpaperMonitorService"
        private const val CHANNEL_ID = "wallpaper_monitor_channel"
        private const val NOTIFICATION_ID = 999
        
        // Rate limiting
        private const val PREF_NAME = "vanderwaals_unlock"
        private const val KEY_LAST_TRIGGER = "last_trigger_time"
        private const val KEY_FAILURE_COUNT = "failure_count"
        private const val KEY_LAST_HEALTH_CHECK = "last_health_check"
        private const val MIN_INTERVAL_MS = 60_000L // 1 minute
        
        // Health check interval
        private const val HEALTH_CHECK_INTERVAL_MS = 300_000L // 5 minutes
        private const val MAX_CONSECUTIVE_FAILURES = 5
        
        // Wakelock timeout (10 minutes - refreshed periodically)
        private const val WAKELOCK_TIMEOUT_MS = 10 * 60 * 1000L
    }
    
    // Service state
    private enum class ServiceState {
        IDLE,           // Waiting for unlock
        PROCESSING,     // Changing wallpaper
        RATE_LIMITED,   // Rate limit active
        ERROR           // Error state
    }
    
    @Volatile
    private var currentState: ServiceState = ServiceState.IDLE

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created - Lifecycle: onCreate()")
        
        // Log Samsung device info for debugging
        SamsungPowerHelper.logDeviceInfo()
        
        // Warn if battery restricted on Samsung
        if (SamsungPowerHelper.isBatteryRestricted(this)) {
            Log.w(TAG, "⚠️ SAMSUNG BATTERY RESTRICTION DETECTED - unlock events may be delayed or missed!")
            Log.w(TAG, SamsungPowerHelper.getSamsungPowerInstructions())
        }
        
        createNotificationChannel()
        currentState = ServiceState.IDLE
        startForeground(NOTIFICATION_ID, createNotification("Monitoring device unlock"))
        
        // SAMSUNG FIX: Acquire wakelock to prevent CPU sleep
        acquireWakeLock()
        
        // Register receiver for USER_PRESENT with high priority for Samsung
        val filter = IntentFilter(Intent.ACTION_USER_PRESENT).apply {
            priority = IntentFilter.SYSTEM_HIGH_PRIORITY
        }
        
        // Use appropriate registration method based on API level
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(unlockReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(unlockReceiver, filter)
        }
        Log.d(TAG, "Unlock receiver registered successfully (priority=${filter.priority})")
        
        // Perform initial health check
        performHealthCheck()
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started - Lifecycle: onStartCommand()")
        
        // Verify settings to ensure service should be running
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
        
        // Ensure we are in foreground with updated notification
        startForeground(NOTIFICATION_ID, createNotification("Monitoring device unlock"))
        
        // Return START_STICKY to restart service if killed
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed - Lifecycle: onDestroy()")
        
        // Unregister broadcast receiver
        try {
            unregisterReceiver(unlockReceiver)
            Log.d(TAG, "Unlock receiver unregistered successfully")
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Receiver was not registered")
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
        
        // Cancel all coroutines and cleanup
        try {
            serviceScope.cancel("Service destroyed")
            Log.d(TAG, "Service scope cancelled")
        } catch (e: Exception) {
        Log.e(TAG, "Error cancelling service scope", e)
        }
        
        // SAMSUNG FIX: Release wakelock
        releaseWakeLock()
        
        // Update state
        currentState = ServiceState.IDLE
        Log.d(TAG, "Service cleanup completed")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun handleUnlock() {
        serviceScope.launch {
            var targetScreen = "both" // Default value
            try {
                // Check rate limiting
                if (!shouldTriggerChange()) {
                    currentState = ServiceState.RATE_LIMITED
                    updateNotification("Rate limited - waiting")
                    Log.d(TAG, "Skipping change (rate limited)")
                    return@launch
                }

                // Verify settings (double check)
                val settings = settingsDataStore.settings.first()
                if (settings.changeInterval != "unlock") {
                    Log.w(TAG, "CRITICAL: Interval is ${settings.changeInterval}, NOT 'unlock'. Stopping service and aborting.")
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

                // Update state and notification
                currentState = ServiceState.PROCESSING
                updateNotification("Changing wallpaper...")
                Log.d(TAG, "Triggering wallpaper change directly for: $targetScreen (Settings: ${settings.applyTo})")
                
                // DIRECT EXECUTION: Bypass WorkManager for immediate response
                changeWallpaper(targetScreen)
                
                // Success - reset failure count and update state
                resetFailureCount()
                currentState = ServiceState.IDLE
                updateNotification("Monitoring device unlock")
                updateLastTriggerTime()
                
            } catch (e: Exception) {
                // Update state and track failure
                currentState = ServiceState.ERROR
                incrementFailureCount()
                updateNotification("Error occurred")
                
                // Use ErrorHandler for consistent error logging
                me.avinas.vanderwaals.core.ErrorHandler.handleWorkerError(
                    exception = e,
                    context = "UnlockWallpaperChange",
                    attemptCount = getFailureCount(),
                    metadata = mapOf(
                        "target_screen" to targetScreen,
                        "service_state" to currentState.name
                    )
                )
                
                // Check if we should stop service due to excessive failures
                if (shouldStopDueToFailures()) {
                    Log.e(TAG, "Stopping service due to excessive failures (${getFailureCount()} consecutive failures)")
                    stopSelf()
                }
            }
        }
    }
    
    private suspend fun changeWallpaper(targetScreen: String) {
        try {
            // Handle "Both But Different" mode
            if (targetScreen == "both_different") {
                applyBothDifferentWallpapers()
                return
            }
            
            // Standard mode
            val wallpaperResult = selectNextWallpaperUseCase()
            if (wallpaperResult.isFailure) {
                Log.w(TAG, "No wallpaper selected: ${wallpaperResult.exceptionOrNull()?.message}")
                return
            }
            
            val wallpaper = wallpaperResult.getOrNull()!!
            
            // Download or get cached
            var selectedWallpaper = wallpaper
            var wallpaperFile: File?
            
            val downloadResult = wallpaperRepository.downloadWallpaper(wallpaper)
            if (downloadResult.isFailure) {
                Log.w(TAG, "Failed to download wallpaper: ${downloadResult.exceptionOrNull()?.message}")
                
                // OFFLINE FALLBACK: Try to find a cached wallpaper
                Log.d(TAG, "Attempting offline fallback - searching for cached wallpapers...")
                val cachedWallpaperResult = findCachedWallpaperUseCase(excludeWallpaperId = wallpaper.id)
                
                if (cachedWallpaperResult != null) {
                    val (cachedWallpaper, cachedFile) = cachedWallpaperResult
                    Log.d(TAG, "Offline fallback successful - using cached wallpaper: ${cachedWallpaper.id}")
                    selectedWallpaper = cachedWallpaper
                    wallpaperFile = cachedFile
                } else {
                    // No cached wallpapers available
                    Log.e(TAG, "No cached wallpapers available for offline fallback")
                    return
                }
            } else {
                wallpaperFile = downloadResult.getOrNull()!!
            }
            
            
            // Apply
            val applied = applyWallpaperToScreen(wallpaperFile!!, targetScreen)
            if (applied) {
                wallpaperRepository.recordWallpaperApplied(selectedWallpaper)
                engagementTracker.recordWallpaperChange()
                Log.d(TAG, "Wallpaper applied successfully: ${selectedWallpaper.id}")
            }
            
        } catch (e: Exception) {
            // Use ErrorHandler for consistent error logging
            me.avinas.vanderwaals.core.ErrorHandler.handleWorkerError(
                exception = e,
                context = "ServiceWallpaperChange",
                attemptCount = 0,
                metadata = mapOf("target_screen" to targetScreen)
            )
        }
    }
    
    private suspend fun applyBothDifferentWallpapers() {
        // Track home wallpaper ID for excluding in lock screen selection
        var homeWallpaperId: String? = null
        
        // Home wallpaper
        val homeResult = selectNextWallpaperUseCase()
        if (homeResult.isSuccess) {
            var actualHomeWallpaper = homeResult.getOrNull()!!
            homeWallpaperId = actualHomeWallpaper.id  // Store for lock screen exclusion
            var homeFile: File?
            
            val homeDownload = wallpaperRepository.downloadWallpaper(actualHomeWallpaper)
            if (homeDownload.isSuccess) {
                homeFile = homeDownload.getOrNull()!!
            } else {
                // OFFLINE FALLBACK for home wallpaper
                Log.d(TAG, "Home wallpaper download failed, attempting offline fallback")
                val cachedHomeResult = findCachedWallpaperUseCase(excludeWallpaperId = actualHomeWallpaper.id)
                if (cachedHomeResult != null) {
                    val (cachedWallpaper, cachedFile) = cachedHomeResult
                    Log.d(TAG, "Offline fallback for home - using cached: ${cachedWallpaper.id}")
                    actualHomeWallpaper = cachedWallpaper
                    homeWallpaperId = cachedWallpaper.id  // Update to cached ID
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
        
        // Lock wallpaper - CRITICAL: exclude home wallpaper ID to ensure different wallpapers
        val lockResult = selectNextWallpaperUseCase(excludeWallpaperId = homeWallpaperId)
        if (lockResult.isSuccess) {
            var actualLockWallpaper = lockResult.getOrNull()!!
            var lockFile: File?
            
            val lockDownload = wallpaperRepository.downloadWallpaper(actualLockWallpaper)
            if (lockDownload.isSuccess) {
                lockFile = lockDownload.getOrNull()!!
            } else {
                // OFFLINE FALLBACK for lock wallpaper
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
            
            // SmartCrop logic (simplified for service)
            val screenSize = me.avinas.vanderwaals.core.getDeviceScreenSize(applicationContext)
            processedBitmap = me.avinas.vanderwaals.core.SmartCrop.smartCropBitmap(
                source = bitmap,
                targetWidth = screenSize.width,
                targetHeight = screenSize.height,
                mode = me.avinas.vanderwaals.core.SmartCrop.CropMode.AUTO
            )
            
            // Recycle original bitmap if different from processed
            if (bitmap !== processedBitmap) {
                me.avinas.vanderwaals.core.BitmapManager.recycleSafely(bitmap)
                bitmap = null // Clear reference
            }
            
            when (targetScreen) {
                "home" -> wallpaperManager.setBitmap(processedBitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                "lock" -> wallpaperManager.setBitmap(processedBitmap, null, true, WallpaperManager.FLAG_LOCK)
                "both" -> wallpaperManager.setBitmap(processedBitmap, null, true, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
            }
            
            // Recycle processed bitmap after successful application
            me.avinas.vanderwaals.core.BitmapManager.recycleSafely(processedBitmap)
            processedBitmap = null // Clear reference
            
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
                
                // Log health status
                Log.d(TAG, "Health check passed - State: $currentState, Failures: ${getFailureCount()}")
                
                // Update last health check time
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
     * Acquires a partial wakelock to prevent CPU from sleeping.
     * SAMSUNG FIX: Helps keep the service alive on aggressive One UI power management.
     * 
     * Uses PARTIAL_WAKE_LOCK with timeout to prevent battery drain.
     * The wakelock is refreshed on each unlock event.
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
