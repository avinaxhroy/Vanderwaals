package me.avinas.vanderwaals.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.avinas.vanderwaals.data.repository.WallpaperRepository
import me.avinas.vanderwaals.worker.CatalogSyncWorker
import me.avinas.vanderwaals.worker.WorkScheduler
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * ViewModel for settings screen state management.
 * 
 * Manages:
 * - All user preferences (mode, frequency, apply to, sources)
 * - Settings persistence via DataStore
 * - Manual catalog sync trigger
 * - Cache management operations
 * - Re-personalization flow
 * 
 * StateFlow emissions:
 * - SettingsState: All current settings values
 * - SyncState: Catalog sync progress and status
 * - CacheState: Cache size, wallpaper count, storage info
 * - ValidationState: Settings validation errors
 * 
 * Preference flows:
 * - mode: "personalized" or "auto"
 * - frequency: "unlock", "hourly", "daily", "never"
 * - dailyTime: Time in HH:mm format (if frequency is daily)
 * - applyTo: "lock", "home", "both"
 * - sourcesEnabled: Set of enabled sources ("github", "bing")
 * - lastSyncTimestamp: Unix timestamp of last successful sync
 * 
 * Operations:
 * - triggerSync(): Manually start catalog sync
 * - clearCache(): Delete cached wallpapers (confirmation required)
 * - reopenOnboarding(): Navigate to onboarding flow
 * - updateWorkSchedule(): Reschedule WorkManager jobs after settings change
 * 
 * Coordinates with:
 * - SyncWallpaperCatalogUseCase: Manual sync
 * - SettingsDataStore: Persistence layer
 * - WorkManager: Reschedule workers on settings change
 * - Paperize WallpaperAlarmScheduler: Update alarm schedule
 * 
 * @see SettingsScreen
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val wallpaperRepository: WallpaperRepository,
    private val workManager: WorkManager,
    private val workScheduler: me.avinas.vanderwaals.worker.WorkScheduler,
    private val settingsDataStore: me.avinas.vanderwaals.data.datastore.SettingsDataStore,
    private val syncWallpaperCatalogUseCase: me.avinas.vanderwaals.domain.usecase.SyncWallpaperCatalogUseCase,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    // Persistent settings state
    private val _mode = MutableStateFlow("")
    private val _interval = MutableStateFlow(ChangeInterval.DAILY)
    private val _dailyTime = MutableStateFlow(DailyTime(8, 0))
    private val _applyTo = MutableStateFlow(ApplyTo.BOTH)
    private val _sourcesEnabled = MutableStateFlow(
        mapOf(
            "GitHub Collections" to true,
            "Bing Wallpapers" to false  // Only enabled in auto mode
        )
    )
    private val _lastSyncTimestamp = MutableStateFlow(0L)
    private val _cacheRefreshTrigger = MutableStateFlow(0L) // Trigger for cache size recalculation
    private val _isSyncing = MutableStateFlow(false)
    private val _syncError = MutableStateFlow<String?>(null)
    private val _toastMessage = MutableStateFlow<String?>(null)
    private val _needsAlarmPermission = MutableStateFlow(false)
    
    // Public toast message flow
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()
    
    // Public syncing state flow
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()
    
    // Public alarm permission needed flow
    val needsAlarmPermission: StateFlow<Boolean> = _needsAlarmPermission.asStateFlow()
    
    /**
     * Clears the toast message after it's been shown.
     */
    fun clearToastMessage() {
        _toastMessage.value = null
    }
    
    /**
     * Opens alarm permission settings.
     */
    fun openAlarmPermissionSettings() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            try {
                val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Failed to open alarm permission settings", e)
            }
        }
        _needsAlarmPermission.value = false
    }
    
    /**
     * Dismisses the alarm permission dialog.
     */
    fun dismissAlarmPermissionDialog() {
        _needsAlarmPermission.value = false
    }
    
    init {
        // Load settings from DataStore
        viewModelScope.launch {
            settingsDataStore.settings.collect { settings ->
                _mode.value = settings.mode
                
                _interval.value = when (settings.changeInterval) {
                    "unlock", "15min" -> ChangeInterval.EVERY_15_MINUTES  // Support old "unlock" value
                    "hourly" -> ChangeInterval.HOURLY
                    "daily" -> ChangeInterval.DAILY
                    "never" -> ChangeInterval.NEVER
                    else -> ChangeInterval.DAILY
                }
                
                settings.dailyTime?.let {
                    _dailyTime.value = DailyTime(it.hour, it.minute)
                }
                
                _applyTo.value = when (settings.applyTo) {
                    "lock_screen" -> ApplyTo.LOCK_SCREEN
                    "home_screen" -> ApplyTo.HOME_SCREEN
                    "both" -> ApplyTo.BOTH
                    else -> ApplyTo.BOTH
                }
                
                // Load from DataStore instead of hardcoding
                _sourcesEnabled.value = mapOf(
                    "GitHub Collections" to settings.githubEnabled,
                    "Bing Wallpapers" to settings.bingEnabled
                )
                
                // Load last sync timestamp
                _lastSyncTimestamp.value = settings.lastSyncTimestamp
            }
        }
    }
    
    /**
     * Combined settings state for the UI.
     */
    val settings: StateFlow<SettingsState> = combine(
        _mode,
        _interval,
        _dailyTime,
        _applyTo,
        _sourcesEnabled,
        _lastSyncTimestamp,
        _cacheRefreshTrigger
    ) { values: Array<Any?> ->
        val mode = values[0] as String
        val interval = values[1] as ChangeInterval
        val dailyTime = values[2] as DailyTime
        val applyTo = values[3] as ApplyTo
        @Suppress("UNCHECKED_CAST")
        val sources = values[4] as Map<String, Boolean>
        val lastSync = values[5] as Long
        // values[6] is cacheRefreshTrigger - just used to trigger recalculation
        
        SettingsState(
            mode = mode,
            interval = interval,
            dailyTime = if (interval == ChangeInterval.DAILY) dailyTime else null,
            applyTo = applyTo,
            sourcesEnabled = sources,
            cacheSize = calculateCacheSize(),
            lastSynced = formatLastSyncTime(lastSync)
        )
    }.flowOn(Dispatchers.IO) // CRITICAL: Move heavy calculation to IO thread
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SettingsState(
            mode = "personalized",
            interval = ChangeInterval.DAILY,
            dailyTime = DailyTime(8, 0),
            applyTo = ApplyTo.BOTH,
            sourcesEnabled = mapOf(
                "GitHub Collections" to true,
                "Bing Wallpapers" to false  // Only enabled in auto mode
            ),
            cacheSize = "Calculating...",
            lastSynced = "Never synced"
        )
    )

    /**
     * Updates the personalization mode.
     * When switching to auto mode, automatically enables Bing Wallpapers.
     */
    fun updateMode(mode: String) {
        viewModelScope.launch {
            _mode.value = mode
            settingsDataStore.updateMode(mode)
            
            // Auto-enable Bing when switching to auto mode
            if (mode == "auto") {
                val updated = _sourcesEnabled.value.toMutableMap()
                updated["Bing Wallpapers"] = true
                _sourcesEnabled.value = updated
                settingsDataStore.toggleSource("bing", true)
            }
        }
    }

    /**
     * Updates the wallpaper change interval.
     */
    fun updateInterval(interval: ChangeInterval) {
        viewModelScope.launch {
            _interval.value = interval
            val intervalString = when (interval) {
                ChangeInterval.EVERY_15_MINUTES -> "15min"
                ChangeInterval.HOURLY -> "hourly"
                ChangeInterval.DAILY -> "daily"
                ChangeInterval.NEVER -> "never"
            }
            settingsDataStore.updateInterval(intervalString, if (interval == ChangeInterval.DAILY) java.time.LocalTime.of(_dailyTime.value.hour, _dailyTime.value.minute) else null)
            
            // Check if alarm permission is needed before scheduling (for all intervals except NEVER)
            if (interval != ChangeInterval.NEVER && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager
                if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                    _needsAlarmPermission.value = true
                    return@launch
                }
            }
            
            updateWorkSchedule()
        }
    }

    /**
     * Updates the daily change time.
     */
    fun updateDailyTime(time: DailyTime) {
        viewModelScope.launch {
            _dailyTime.value = time
            settingsDataStore.updateInterval(_interval.value.name.lowercase(), java.time.LocalTime.of(time.hour, time.minute))
            updateWorkSchedule()
        }
    }

    /**
     * Updates where the wallpaper should be applied.
     * CRITICAL FIX: Must reschedule worker with new target screen.
     */
    fun updateApplyTo(applyTo: ApplyTo) {
        viewModelScope.launch {
            _applyTo.value = applyTo
            val applyToString = when (applyTo) {
                ApplyTo.LOCK_SCREEN -> "lock_screen"
                ApplyTo.HOME_SCREEN -> "home_screen"
                ApplyTo.BOTH -> "both"
            }
            settingsDataStore.updateApplyTo(applyToString)
            
            // CRITICAL: Reschedule worker with new target screen
            updateWorkSchedule()
        }
    }

    /**
     * Toggles a wallpaper source on or off.
     */
    fun toggleSource(source: String, enabled: Boolean) {
        viewModelScope.launch {
            val updated = _sourcesEnabled.value.toMutableMap()
            updated[source] = enabled
            _sourcesEnabled.value = updated
            val sourceKey = if (source.contains("GitHub")) "github" else "bing"
            settingsDataStore.toggleSource(sourceKey, enabled)
        }
    }

    /**
     * Triggers immediate manifest sync.
     */
    fun syncNow() {
        viewModelScope.launch {
            try {
                _isSyncing.value = true
                _syncError.value = null
                
                // Use the SyncWallpaperCatalogUseCase which respects source settings
                syncWallpaperCatalogUseCase.syncCatalog().fold(
                    onSuccess = { count ->
                        val timestamp = System.currentTimeMillis()
                        _lastSyncTimestamp.value = timestamp
                        // Persist timestamp to DataStore
                        settingsDataStore.updateLastSyncTimestamp(timestamp)
                        _isSyncing.value = false
                        _toastMessage.value = "Sync successful: $count wallpapers"
                        println("Sync successful: $count wallpapers")
                    },
                    onFailure = { error ->
                        _isSyncing.value = false
                        _syncError.value = error.message
                        _toastMessage.value = "Sync failed: ${error.message}"
                        println("Sync failed: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                _isSyncing.value = false
                _syncError.value = e.message
                _toastMessage.value = "Error syncing: ${e.message}"
                println("Error syncing: ${e.message}")
            }
        }
    }

    /**
     * Clears the wallpaper cache.
     */
    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) { // CRITICAL: Run on IO thread
            try {
                // Get cache directory
                val cacheDir = context.cacheDir
                val wallpaperCacheDir = File(cacheDir, "wallpapers")
                
                if (wallpaperCacheDir.exists()) {
                    val files = wallpaperCacheDir.listFiles() ?: emptyArray()
                    val sizeMB = files.sumOf { it.length() } / (1024.0 * 1024.0)
                    
                    wallpaperCacheDir.deleteRecursively()
                    wallpaperCacheDir.mkdirs()
                    
                    _toastMessage.value = String.format("Cache cleared: %.1f MB freed", sizeMB)
                    
                    // Trigger cache size recalculation
                    _cacheRefreshTrigger.value = System.currentTimeMillis()
                } else {
                    _toastMessage.value = "Cache already empty"
                }
                
            } catch (e: Exception) {
                _toastMessage.value = "Error clearing cache: ${e.message}"
                println("Error clearing cache: ${e.message}")
            }
        }
    }

    /**
     * Calculates the current cache size.
     */
    private fun calculateCacheSize(): String {
        return try {
            val cacheDir = context.cacheDir
            val wallpaperCacheDir = File(cacheDir, "wallpapers")
            
            if (!wallpaperCacheDir.exists()) {
                return "0 MB, 0 wallpapers"
            }
            
            val files = wallpaperCacheDir.listFiles() ?: emptyArray()
            val totalSize = files.sumOf { it.length() }
            val sizeMB = totalSize / (1024.0 * 1024.0)
            val count = files.size
            
            "${String.format("%.0f", sizeMB)} MB, $count wallpapers"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    /**
     * Formats the last sync timestamp as relative time.
     */
    private fun formatLastSyncTime(timestamp: Long): String {
        if (timestamp == 0L) {
            return "Never synced"
        }
        
        val now = System.currentTimeMillis()
        val diff = (now - timestamp).milliseconds
        
        return when {
            diff.inWholeMinutes < 1 -> "Just now"
            diff.inWholeMinutes < 60 -> "${diff.inWholeMinutes} minutes ago"
            diff.inWholeHours < 24 -> "${diff.inWholeHours} hours ago"
            diff.inWholeDays == 1L -> "Yesterday"
            diff.inWholeDays < 7 -> "${diff.inWholeDays} days ago"
            else -> {
                val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
                dateFormat.format(Date(timestamp))
            }
        }
    }

    /**
     * Updates WorkManager schedules based on new settings.
     */
    private fun updateWorkSchedule() {
        viewModelScope.launch {
            val targetScreen = when (_applyTo.value) {
                ApplyTo.LOCK_SCREEN -> "lock"
                ApplyTo.HOME_SCREEN -> "home"
                ApplyTo.BOTH -> "both"
            }
            
            // Convert UI ChangeInterval to Worker ChangeInterval
            val workerInterval = when (_interval.value) {
                ChangeInterval.EVERY_15_MINUTES -> me.avinas.vanderwaals.worker.ChangeInterval.EVERY_15_MINUTES
                ChangeInterval.HOURLY -> me.avinas.vanderwaals.worker.ChangeInterval.HOURLY
                ChangeInterval.DAILY -> me.avinas.vanderwaals.worker.ChangeInterval.DAILY
                ChangeInterval.NEVER -> me.avinas.vanderwaals.worker.ChangeInterval.NEVER
            }
            
            when (_interval.value) {
                ChangeInterval.EVERY_15_MINUTES -> {
                    workScheduler.scheduleWallpaperChange(
                        interval = workerInterval,
                        targetScreen = targetScreen
                    )
                }
                ChangeInterval.DAILY -> {
                    val time = java.time.LocalTime.of(_dailyTime.value.hour, _dailyTime.value.minute)
                    workScheduler.scheduleWallpaperChange(
                        interval = workerInterval,
                        time = time,
                        targetScreen = targetScreen
                    )
                }
                ChangeInterval.HOURLY -> {
                    workScheduler.scheduleWallpaperChange(
                        interval = workerInterval,
                        targetScreen = targetScreen
                    )
                }
                ChangeInterval.NEVER -> {
                    workScheduler.scheduleWallpaperChange(
                        interval = workerInterval,
                        targetScreen = targetScreen
                    )
                }
            }
        }
    }
}

/**
 * UI state for settings screen.
 */
data class SettingsState(
    val mode: String,
    val interval: ChangeInterval,
    val dailyTime: DailyTime?,
    val applyTo: ApplyTo,
    val sourcesEnabled: Map<String, Boolean>,
    val cacheSize: String,
    val lastSynced: String
)

// Enums for settings
enum class ChangeInterval(val displayName: String) {
    EVERY_15_MINUTES("Every 15 Minutes"),
    HOURLY("Hourly"),
    DAILY("Daily"),
    NEVER("Never")
}

enum class ApplyTo(val displayName: String) {
    LOCK_SCREEN("Lock Screen"),
    HOME_SCREEN("Home Screen"),
    BOTH("Both")
}

data class DailyTime(
    val hour: Int,
    val minute: Int
)
