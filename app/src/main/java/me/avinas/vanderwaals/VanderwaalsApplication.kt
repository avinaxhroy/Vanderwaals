package me.avinas.vanderwaals

import android.annotation.SuppressLint
import android.app.Application
import android.app.Activity
import android.os.Bundle
import android.app.NotificationChannel
import android.app.NotificationManager
import android.database.CursorWindow
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import me.avinas.vanderwaals.data.repository.ManifestRepository
import me.avinas.vanderwaals.worker.CleanupWorker
import me.avinas.vanderwaals.worker.CatalogSyncWorker
import java.lang.reflect.Field
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class VanderwaalsApplication : Application(), Configuration.Provider {
    
    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    
    @Inject
    lateinit var workManagerConfig: Configuration
    
    @Inject
    lateinit var manifestRepository: ManifestRepository
    
    @Inject
    lateinit var workScheduler: me.avinas.vanderwaals.worker.WorkScheduler
    
    @Inject
    lateinit var settingsDataStore: me.avinas.vanderwaals.data.datastore.SettingsDataStore
    
    /**
     * Application-scoped CoroutineScope with SupervisorJob.
     * SupervisorJob ensures one child failure doesn't cancel siblings.
     * CoroutineExceptionHandler prevents unhandled exceptions from crashing the app.
     */
    private val applicationScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "Uncaught coroutine exception in application scope", throwable)
        }
    )
    
    companion object {
        private const val TAG = "VanderwaalsApp"
        private const val MANIFEST_SYNC_WORK = "manifest_sync_periodic"
        private const val CATALOG_SYNC_INITIAL_WORK = "catalog_sync_initial"
        private const val CLEANUP_WORK = "cleanup_periodic"
    }
    
    override fun onCreate() {
        super.onCreate()
        // Enable edge-to-edge display - WindowInsets will be handled manually in each screen
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                try {
                    // Disable decorFitsSystemWindows to enable edge-to-edge with manual insets handling
                    WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to set decorFitsSystemWindows", e)
                }
            }

            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
        
        // Install global uncaught exception handler to log crashes
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "FATAL: Uncaught exception on thread ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
        
        me.avinas.vanderwaals.core.BitmapManager.init(this)

        initializeLogging()
        
        increaseCursorWindowSize()
        
        createNotificationChannels()
        
        schedulePeriodicWorkers()
        
        ensureInitialCatalogSync()
        
        initializeWallpaperScheduling()
        
        Log.d(TAG, "Vanderwaals application initialized successfully")
    }
    
    override val workManagerConfiguration: Configuration
        get() = workManagerConfig
    
    private fun initializeLogging() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Debug logging enabled")
        }
    }
    
    private fun increaseCursorWindowSize() {
        try {
            val field: Field = CursorWindow::class.java.getDeclaredField("sCursorWindowSize")
            field.isAccessible = true
            field.set(null, 10 * 1024 * 1024)
            Log.d(TAG, "CursorWindow size increased to 10MB")
        } catch (e: Exception) {
            try {
                val field: Field = CursorWindow::class.java.getDeclaredField("CURSOR_WINDOW_SIZE")
                field.isAccessible = true
                field.set(null, 10 * 1024 * 1024)
                Log.d(TAG, "CursorWindow size increased to 10MB (fallback)")
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to increase CursorWindow size", e2)
            }
        }
    }
    
    private fun createNotificationChannels() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        
        val wallpaperChannel = NotificationChannel(
            me.avinas.vanderwaals.core.NotificationConstants.CHANNEL_WALLPAPER_SERVICE,
            "Wallpaper Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notifications for wallpaper change operations"
            setShowBadge(false)
        }
        
        val syncChannel = NotificationChannel(
            me.avinas.vanderwaals.core.NotificationConstants.CHANNEL_SYNC,
            "Wallpaper Sync",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notifications for wallpaper manifest synchronization"
            setShowBadge(false)
        }
        
        // Channel for WallpaperMonitorService (unlock monitoring)
        val monitorChannel = NotificationChannel(
            me.avinas.vanderwaals.core.NotificationConstants.CHANNEL_WALLPAPER_MONITOR,
            "Wallpaper Monitor",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps app alive to detect device unlock"
            setShowBadge(false)
        }
        
        // Channel for WallpaperChangeService (scheduled changes)
        val changeChannel = NotificationChannel(
            me.avinas.vanderwaals.core.NotificationConstants.CHANNEL_WALLPAPER_CHANGE,
            "Wallpaper Changes",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when wallpaper is being changed"
            setShowBadge(false)
        }
        
        notificationManager.createNotificationChannels(
            listOf(wallpaperChannel, syncChannel, monitorChannel, changeChannel)
        )
        Log.d(TAG, "Notification channels created")
    }
    
    private fun schedulePeriodicWorkers() {
        val workManager = WorkManager.getInstance(this)
        
        val manifestSyncRequest = PeriodicWorkRequestBuilder<CatalogSyncWorker>(
            repeatInterval = 7,
            repeatIntervalTimeUnit = TimeUnit.DAYS,
            flexTimeInterval = 1,
            flexTimeIntervalUnit = TimeUnit.DAYS
        ).build()
        
        workManager.enqueueUniquePeriodicWork(
            MANIFEST_SYNC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            manifestSyncRequest
        )
        
        val cleanupRequest = PeriodicWorkRequestBuilder<CleanupWorker>(
            repeatInterval = 1,
            repeatIntervalTimeUnit = TimeUnit.DAYS,
            flexTimeInterval = 6,
            flexTimeIntervalUnit = TimeUnit.HOURS
        ).build()
        
        workManager.enqueueUniquePeriodicWork(
            CLEANUP_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            cleanupRequest
        )
        
        Log.d(TAG, "Periodic workers scheduled: ManifestSync (weekly), Cleanup (daily)")
    }
    
    /**
     * Ensures that wallpaper catalog is synced on first app startup.
     * 
     * If the database is empty (fresh install), schedules an immediate one-time
     * sync to populate it with wallpapers. This prevents "No wallpapers available"
     * errors when the user triggers wallpaper changes before the weekly sync runs.
     */
    private fun ensureInitialCatalogSync() {
        applicationScope.launch {
            try {
                val isDatabaseInitialized = manifestRepository.isDatabaseInitialized()
                
                if (!isDatabaseInitialized) {
                    Log.d(TAG, "Database is empty, scheduling immediate catalog sync")
                    
                    val workManager = WorkManager.getInstance(this@VanderwaalsApplication)
                    val initialSyncRequest = OneTimeWorkRequestBuilder<CatalogSyncWorker>().build()
                    
                    workManager.enqueueUniqueWork(
                        CATALOG_SYNC_INITIAL_WORK,
                        ExistingWorkPolicy.KEEP,
                        initialSyncRequest
                    )
                    
                    Log.d(TAG, "Immediate catalog sync scheduled")
                } else {
                    Log.d(TAG, "Database already initialized, skipping initial sync")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking database initialization", e)
            }
        }
    }
    
    /**
     * Initializes wallpaper auto-change scheduling based on user settings.
     * Restores schedules on every app startup (after reboot or restart).
     * Only runs if onboarding is complete and alarm permission is granted.
     */
    private fun initializeWallpaperScheduling() {
        applicationScope.launch {
            try {
                val settings = settingsDataStore.settings.first()
                
                // Skip if onboarding not complete (first launch)
                if (!settings.onboardingCompleted) {
                    Log.d(TAG, "Skipping wallpaper scheduling - onboarding not complete")
                    return@launch
                }
                
                val interval = when (settings.changeInterval) {
                    "unlock" -> me.avinas.vanderwaals.worker.ChangeInterval.EVERY_UNLOCK
                    "15min" -> me.avinas.vanderwaals.worker.ChangeInterval.FIFTEEN_MINUTES
                    "hourly" -> me.avinas.vanderwaals.worker.ChangeInterval.HOURLY
                    "3hours" -> me.avinas.vanderwaals.worker.ChangeInterval.THREE_HOURS
                    "6hours" -> me.avinas.vanderwaals.worker.ChangeInterval.SIX_HOURS
                    "12hours" -> me.avinas.vanderwaals.worker.ChangeInterval.TWELVE_HOURS
                    "daily" -> me.avinas.vanderwaals.worker.ChangeInterval.DAILY
                    "3days" -> me.avinas.vanderwaals.worker.ChangeInterval.THREE_DAYS
                    "7days" -> me.avinas.vanderwaals.worker.ChangeInterval.SEVEN_DAYS
                    "never" -> me.avinas.vanderwaals.worker.ChangeInterval.NEVER
                    else -> me.avinas.vanderwaals.worker.ChangeInterval.NEVER
                }
                
                if (interval == me.avinas.vanderwaals.worker.ChangeInterval.NEVER) {
                    Log.d(TAG, "Skipping wallpaper scheduling - interval set to NEVER")
                    return@launch
                }
                
                // Exact alarms require permission check on Android 12+
                if (interval == me.avinas.vanderwaals.worker.ChangeInterval.DAILY || 
                    interval == me.avinas.vanderwaals.worker.ChangeInterval.HOURLY ||
                    interval == me.avinas.vanderwaals.worker.ChangeInterval.THREE_HOURS ||
                    interval == me.avinas.vanderwaals.worker.ChangeInterval.SIX_HOURS ||
                    interval == me.avinas.vanderwaals.worker.ChangeInterval.TWELVE_HOURS ||
                    interval == me.avinas.vanderwaals.worker.ChangeInterval.THREE_DAYS ||
                    interval == me.avinas.vanderwaals.worker.ChangeInterval.SEVEN_DAYS ||
                    interval == me.avinas.vanderwaals.worker.ChangeInterval.FIFTEEN_MINUTES) {
                    
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        val alarmManager = getSystemService(android.content.Context.ALARM_SERVICE) as? android.app.AlarmManager
                        if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                            Log.w(TAG, "⚠️ Exact alarm permission not granted - wallpaper timing may be inaccurate")
                            Log.w(TAG, "   User should grant SCHEDULE_EXACT_ALARM permission in Settings")
                            // Don't return - still try to schedule, but it may use inexact alarms
                            // The scheduling code will handle this and potentially fall back
                        }
                    }
                }
                
                Log.d(TAG, "========================================")
                Log.d(TAG, "Initializing wallpaper scheduling on app startup")
                Log.d(TAG, "  User settings:")
                Log.d(TAG, "    - Change Interval: ${settings.changeInterval}")
                Log.d(TAG, "    - Daily Time: ${settings.dailyTime}")
                Log.d(TAG, "    - Apply To: ${settings.applyTo}")
                Log.d(TAG, "========================================")
                
                val targetScreen = when (settings.applyTo) {
                    "lock_screen" -> "lock"
                    "home_screen" -> "home"
                    "both" -> "both"
                    "both_different" -> "both_different"
                    else -> "both"
                }
                
                workScheduler.scheduleWallpaperChange(
                    interval = interval,
                    time = settings.dailyTime,
                    targetScreen = targetScreen
                )
                
                Log.d(TAG, "Wallpaper scheduling initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing wallpaper scheduling", e)
            }
        }
    }
}
