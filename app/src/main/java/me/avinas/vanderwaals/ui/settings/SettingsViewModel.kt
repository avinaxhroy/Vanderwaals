package me.avinas.vanderwaals.ui.settings

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
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
    private val userPreferenceDao: me.avinas.vanderwaals.data.dao.UserPreferenceDao,
    private val bingManifestRepository: me.avinas.vanderwaals.data.repository.BingManifestRepository,
    private val vanderwaalsCollectionRepository: me.avinas.vanderwaals.data.repository.VanderwaalsCollectionRepository,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    // Persistent settings state
    private val _mode = MutableStateFlow("")
    private val _interval = MutableStateFlow(ChangeInterval.DAILY)
    private val _dailyTime = MutableStateFlow(DailyTime(8, 0))
    private val _applyTo = MutableStateFlow(ApplyTo.BOTH)
    private val _sourcesEnabled = MutableStateFlow(buildSourceState(true, false, false))
    private val _lastSyncTimestamp = MutableStateFlow(0L)
    private val _cacheRefreshTrigger = MutableStateFlow(0L) // Trigger for cache size recalculation
    private val _isSyncing = MutableStateFlow(false)
    private val _syncError = MutableStateFlow<String?>(null)
    private val _toastMessage = MutableStateFlow<String?>(null)
    private val _needsAlarmPermission = MutableStateFlow(false)
    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    private val _dailyPlaylistSize = MutableStateFlow(15)
    private val _isPlaylistDownloading = MutableStateFlow(false)
    private val _playlistDownloadProgress = MutableStateFlow(PlaylistDownloadProgress())
    
    // Bing sync state
    private val _isBingSyncing = MutableStateFlow(false)
    private val _bingSyncProgress = MutableStateFlow(0f)
    private val _bingSyncMessage = MutableStateFlow("")
    private val _bingWallpaperCount = MutableStateFlow(0)
    private val _bingManifestType = MutableStateFlow("lite")  // "lite" or "full"
    private val _showBingTypeDialog = MutableStateFlow(false)

    // Vanderwaals Collection sync state
    private val _isVanderwaalsCollectionSyncing = MutableStateFlow(false)
    private val _vanderwaalsCollectionSyncProgress = MutableStateFlow(0f)
    private val _vanderwaalsCollectionSyncMessage = MutableStateFlow("")
    private val _vanderwaalsCollectionWallpaperCount = MutableStateFlow(0)
    private val _vanderwaalsCollectionManifestType = MutableStateFlow("lite")
    
    // Public toast message flow
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()
    
    // Public syncing state flow
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()
    
    // Public alarm permission needed flow
    val needsAlarmPermission: StateFlow<Boolean> = _needsAlarmPermission.asStateFlow()
    // Public Bing sync state flows
    val isBingSyncing: StateFlow<Boolean> = _isBingSyncing.asStateFlow()
    val bingSyncProgress: StateFlow<Float> = _bingSyncProgress.asStateFlow()
    val bingSyncMessage: StateFlow<String> = _bingSyncMessage.asStateFlow()
    val bingWallpaperCount: StateFlow<Int> = _bingWallpaperCount.asStateFlow()
    val bingManifestType: StateFlow<String> = _bingManifestType.asStateFlow()
    val showBingTypeDialog: StateFlow<Boolean> = _showBingTypeDialog.asStateFlow()

    // Public Vanderwaals Collection sync state flows
    val isVanderwaalsCollectionSyncing: StateFlow<Boolean> = _isVanderwaalsCollectionSyncing.asStateFlow()
    val vanderwaalsCollectionSyncProgress: StateFlow<Float> = _vanderwaalsCollectionSyncProgress.asStateFlow()
    val vanderwaalsCollectionSyncMessage: StateFlow<String> = _vanderwaalsCollectionSyncMessage.asStateFlow()
    val vanderwaalsCollectionWallpaperCount: StateFlow<Int> = _vanderwaalsCollectionWallpaperCount.asStateFlow()
    val vanderwaalsCollectionManifestType: StateFlow<String> = _vanderwaalsCollectionManifestType.asStateFlow()
    
    /**
     * Clears the toast message after it's been shown.
     */
    fun clearToastMessage() {
        _toastMessage.value = null
    }
    
    // Flag to track if user went to permission settings
    private var _waitingForAlarmPermission = false
    
    /**
     * Opens alarm permission settings.
     */
    fun openAlarmPermissionSettings() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            try {
                _waitingForAlarmPermission = true
                val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Failed to open alarm permission settings", e)
                _waitingForAlarmPermission = false
            }
        }
        _needsAlarmPermission.value = false
    }
    
    /**
     * Called when the app resumes (user returns from permission settings).
     * Re-checks permission and schedules appropriately.
     */
    fun onResume() {
        if (_waitingForAlarmPermission && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            _waitingForAlarmPermission = false
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager
            if (alarmManager != null && alarmManager.canScheduleExactAlarms()) {
                _toastMessage.value = "Alarm permission granted! Wallpapers will change on time."
                viewModelScope.launch {
                    updateWorkSchedule()
                }
            } else {
                _toastMessage.value = "Permission not granted. Wallpaper timing may be inexact."
                viewModelScope.launch {
                    updateWorkSchedule()
                }
            }
        }
    }
    
    /**
     * Dismisses the alarm permission dialog.
     * WARNING: This will proceed with inexact scheduling!
     */
    fun dismissAlarmPermissionDialog() {
        _needsAlarmPermission.value = false
        // Warn the user that timing will be inexact
        _toastMessage.value = "Wallpaper timing may be inexact without alarm permission"
        // Still schedule, but with inexact timing (WorkManager fallback)
        viewModelScope.launch {
            updateWorkSchedule()
        }
    }

    /**
     * Opens battery optimization settings.
     */
    fun openBatterySettings() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            try {
                val intent = android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Failed to open battery settings", e)
                _toastMessage.value = "Could not open battery settings"
            }
        }
    }

    /**
     * Resets onboarding status and navigates to onboarding.
     */
    fun reopenOnboarding() {
        viewModelScope.launch {
            // Reset DataStore flag
            settingsDataStore.resetOnboarding()
            
            // Clear User Preferences (this is what MainActivity checks)
            userPreferenceDao.deleteAll()
            
            _toastMessage.value = "Restarting onboarding..."
        }
    }
    
    init {
        // Load settings from DataStore
        viewModelScope.launch {
            settingsDataStore.settings.collect { settings ->
                _mode.value = settings.mode
                
                _interval.value = when (settings.changeInterval) {
                    "unlock" -> ChangeInterval.EVERY_UNLOCK
                    "15min" -> ChangeInterval.FIFTEEN_MINUTES
                    "hourly" -> ChangeInterval.HOURLY
                    "3hours" -> ChangeInterval.THREE_HOURS
                    "6hours" -> ChangeInterval.SIX_HOURS
                    "12hours" -> ChangeInterval.TWELVE_HOURS
                    "daily" -> ChangeInterval.DAILY
                    "3days" -> ChangeInterval.THREE_DAYS
                    "7days" -> ChangeInterval.SEVEN_DAYS
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
                    "both_different" -> ApplyTo.BOTH_DIFFERENT
                    else -> ApplyTo.BOTH
                }
                
                // Load from DataStore instead of hardcoding
                _sourcesEnabled.value = buildSourceState(
                    githubEnabled = settings.githubEnabled,
                    bingEnabled = settings.bingEnabled,
                    vanderwaalsCollectionEnabled = settings.vanderwaalsCollectionEnabled
                )
                
                // Load last sync timestamp
                _lastSyncTimestamp.value = settings.lastSyncTimestamp
                
                _themeMode.value = when (settings.themeMode) {
                    "light" -> ThemeMode.LIGHT
                    "dark" -> ThemeMode.DARK
                    else -> ThemeMode.SYSTEM
                }
                
                _dailyPlaylistSize.value = settings.dailyPlaylistSize
                
                // Load Bing manifest type
                _bingManifestType.value = settings.bingManifestType

                // Load Vanderwaals Collection manifest type
                _vanderwaalsCollectionManifestType.value = settings.vanderwaalsCollectionManifestType
            }
        }
        
        // Load Bing wallpaper count
        loadBingWallpaperCount()

        // Load Vanderwaals Collection wallpaper count
        loadVanderwaalsCollectionWallpaperCount()
        
        // Observe Daily Playlist Worker status (both scheduled and manual)
        viewModelScope.launch {
            workManager.getWorkInfosByTagLiveData("manual_playlist_download")
                .asFlow()
                .collect { workInfos ->
                    val runningWork = workInfos?.find { 
                        it.state == androidx.work.WorkInfo.State.RUNNING 
                    }
                    
                    if (runningWork != null) {
                        _isPlaylistDownloading.value = true
                        
                        // Extract progress from worker
                        val downloadedCount = runningWork.progress.getInt(
                            me.avinas.vanderwaals.worker.DailyPlaylistWorker.KEY_DOWNLOADED_COUNT, 0
                        )
                        val totalCount = runningWork.progress.getInt(
                            me.avinas.vanderwaals.worker.DailyPlaylistWorker.KEY_TOTAL_COUNT, 0
                        )
                        val status = runningWork.progress.getString(
                            me.avinas.vanderwaals.worker.DailyPlaylistWorker.KEY_STATUS
                        ) ?: ""
                        
                        _playlistDownloadProgress.value = PlaylistDownloadProgress(
                            downloadedCount = downloadedCount,
                            totalCount = totalCount,
                            status = status
                        )
                    } else {
                        // Check if work succeeded
                        val succeededWork = workInfos?.find { 
                            it.state == androidx.work.WorkInfo.State.SUCCEEDED 
                        }
                        
                        if (succeededWork != null) {
                            val downloadedCount = succeededWork.outputData.getInt(
                                me.avinas.vanderwaals.worker.DailyPlaylistWorker.KEY_DOWNLOADED_COUNT, 0
                            )
                            val appliedId = succeededWork.outputData.getString(
                                me.avinas.vanderwaals.worker.DailyPlaylistWorker.KEY_APPLIED_WALLPAPER_ID
                            )
                            
                            // Only show toast if we haven't shown it for this specific work ID yet
                            // or simply prune it to avoid showing it again
                            if (downloadedCount > 0) {
                                val appliedMsg = if (!appliedId.isNullOrEmpty()) " Wallpaper applied!" else ""
                                _toastMessage.value = "Downloaded $downloadedCount wallpapers!$appliedMsg"
                                
                                // Prune work to prevent showing this message again on next screen open
                                workManager.pruneWork()
                            }
                        }
                        
                        _isPlaylistDownloading.value = false
                        _playlistDownloadProgress.value = PlaylistDownloadProgress()
                    }
                }
        }
    }
    
    /**
     * Combined settings state for the UI.
     * CRITICAL: All StateFlows that affect UI must be included in combine() to trigger recomposition.
     */
    val settings: StateFlow<SettingsState> = combine(
        _mode,
        _interval,
        _dailyTime,
        _applyTo,
        _sourcesEnabled,
        _lastSyncTimestamp,
        _cacheRefreshTrigger,
        _themeMode,
        _dailyPlaylistSize,
        combine(_isPlaylistDownloading, _playlistDownloadProgress) { downloading, progress -> 
            Pair(downloading, progress) 
        }
    ) { values: Array<Any?> ->
        val mode = values[0] as String
        val interval = values[1] as ChangeInterval
        val dailyTime = values[2] as DailyTime
        val applyTo = values[3] as ApplyTo
        @Suppress("UNCHECKED_CAST")
        val sources = values[4] as Map<String, Boolean>
        val lastSync = values[5] as Long
        // values[6] is cacheRefreshTrigger
        val themeMode = values[7] as ThemeMode
        val dailyPlaylistSize = values[8] as Int
        @Suppress("UNCHECKED_CAST")
        val downloadState = values[9] as Pair<Boolean, PlaylistDownloadProgress>
        val isPlaylistDownloading = downloadState.first
        val downloadProgress = downloadState.second
        
        SettingsState(
            mode = mode,
            interval = interval,
            dailyTime = if (interval == ChangeInterval.DAILY) dailyTime else null,
            applyTo = applyTo,
            sourcesEnabled = sources,
            cacheSize = calculateCacheSize(),
            lastSynced = formatLastSyncTime(lastSync),
            themeMode = themeMode,
            dailyPlaylistSize = dailyPlaylistSize,
            isPlaylistDownloading = isPlaylistDownloading,
            playlistDownloadProgress = downloadProgress
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
            sourcesEnabled = buildSourceState(true, false, false),
            cacheSize = "Calculating...",
            lastSynced = "Never synced",
            themeMode = ThemeMode.SYSTEM,
            dailyPlaylistSize = 15,
            isPlaylistDownloading = false,
            playlistDownloadProgress = PlaylistDownloadProgress()
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
                ChangeInterval.EVERY_UNLOCK -> "unlock"
                ChangeInterval.FIFTEEN_MINUTES -> "15min"
                ChangeInterval.HOURLY -> "hourly"
                ChangeInterval.THREE_HOURS -> "3hours"
                ChangeInterval.SIX_HOURS -> "6hours"
                ChangeInterval.TWELVE_HOURS -> "12hours"
                ChangeInterval.DAILY -> "daily"
                ChangeInterval.THREE_DAYS -> "3days"
                ChangeInterval.SEVEN_DAYS -> "7days"
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
                ApplyTo.BOTH_DIFFERENT -> "both_different"
            }
            settingsDataStore.updateApplyTo(applyToString)
            
            // CRITICAL: Reschedule worker with new target screen
            updateWorkSchedule()
        }
    }

    /**
     * Toggles a wallpaper source on or off.
     * When enabling Bing, shows manifest type selection dialog first.
     */
    fun toggleSource(source: String, enabled: Boolean) {
        viewModelScope.launch {
            val sourceKey = when {
                source.contains("GitHub") -> "github"
                source.contains("Bing") -> "bing"
                source.contains("Vanderwaals") -> "vanderwaals"
                else -> {
                    android.util.Log.e("SettingsViewModel", "toggleSource: unknown source key '$source'")
                    return@launch
                }
            }

            // If enabling Bing, check if we need to show type selection dialog
            if (sourceKey == "bing" && enabled) {
                val bingCount = bingManifestRepository.getBingWallpaperCount()
                if (bingCount == 0) {
                    _showBingTypeDialog.value = true
                    return@launch
                }
            }

            // If enabling Vanderwaals Collection with no wallpapers, start sync
            if (sourceKey == "vanderwaals" && enabled) {
                val vcCount = vanderwaalsCollectionRepository.getVanderwaalsCollectionWallpaperCount()
                if (vcCount == 0) {
                    // Enable source and start sync immediately
                    val updated = _sourcesEnabled.value.toMutableMap()
                    updated[source] = enabled
                    _sourcesEnabled.value = updated
                    settingsDataStore.toggleSource(sourceKey, enabled)
                    syncVanderwaalsCollectionWallpapers(forceUpdate = true)
                    return@launch
                }
            }

            // For all sources, proceed normally
            val updated = _sourcesEnabled.value.toMutableMap()
            updated[source] = enabled
            _sourcesEnabled.value = updated
            settingsDataStore.toggleSource(sourceKey, enabled)
        }
    }
    
    /**
     * Dismisses the Bing manifest type selection dialog.
     */
    fun dismissBingTypeDialog() {
        _showBingTypeDialog.value = false
    }
    
    /**
     * Called when user selects a Bing manifest type from the dialog.
     * Enables Bing source, saves the type, and starts download.
     */
    fun onBingTypeSelected(type: String) {
        viewModelScope.launch {
            _showBingTypeDialog.value = false
            
            // Enable Bing source
            val updated = _sourcesEnabled.value.toMutableMap()
            updated["Bing Wallpapers"] = true
            _sourcesEnabled.value = updated
            settingsDataStore.toggleSource("bing", true)
            
            // Save manifest type
            _bingManifestType.value = type
            settingsDataStore.updateBingManifestType(type)
            
            // Start download
            syncBingWallpapers(forceUpdate = true)
        }
    }
    
    /**
     * Syncs Bing wallpapers with progress tracking.
     * @param forceUpdate If true, downloads fresh manifest ignoring If-Modified-Since
     */
    fun syncBingWallpapers(forceUpdate: Boolean = false) {
        viewModelScope.launch {
            _isBingSyncing.value = true
            _bingSyncProgress.value = 0f
            _bingSyncMessage.value = "Starting sync..."
            
            bingManifestRepository.syncBingManifest(
                manifestType = _bingManifestType.value,
                onProgress = { message, progress, count ->
                    _bingSyncMessage.value = message
                    _bingSyncProgress.value = progress
                    _bingWallpaperCount.value = count
                },
                forceUpdate = forceUpdate
            ).fold(
                onSuccess = { count ->
                    _bingWallpaperCount.value = count
                    _isBingSyncing.value = false
                    _bingSyncProgress.value = 1f
                    _bingSyncMessage.value = "Sync complete!"
                    _toastMessage.value = "Synced $count Bing wallpapers"
                    
                    // Update last sync timestamp
                    settingsDataStore.updateBingLastSyncTimestamp(System.currentTimeMillis())
                },
                onFailure = { error ->
                    _isBingSyncing.value = false
                    _bingSyncProgress.value = 0f
                    _bingSyncMessage.value = "Sync failed"
                    _toastMessage.value = "Bing sync failed: ${error.message}"
                }
            )
        }
    }
    
    /**
     * Updates the Bing manifest type (lite vs full).
     * If Bing is enabled and type changes, will trigger a resync.
     */
    fun updateBingManifestType(type: String) {
        viewModelScope.launch {
            val oldType = _bingManifestType.value
            _bingManifestType.value = type
            settingsDataStore.updateBingManifestType(type)
            
            // If Bing is enabled and type changed, resync
            val bingEnabled = _sourcesEnabled.value["Bing Wallpapers"] == true
            if (bingEnabled && oldType != type) {
                // Clear existing Bing wallpapers before syncing new type
                bingManifestRepository.clearBingWallpapers()
                syncBingWallpapers(forceUpdate = true)
            }
        }
    }
    
    /**
     * Loads Bing wallpaper count on init.
     */
    private fun loadBingWallpaperCount() {
        viewModelScope.launch {
            _bingWallpaperCount.value = bingManifestRepository.getBingWallpaperCount()
        }
    }

    /**
     * Syncs Vanderwaals Collection wallpapers with progress tracking.
     * @param forceUpdate If true, downloads fresh manifest ignoring If-Modified-Since
     */
    fun syncVanderwaalsCollectionWallpapers(forceUpdate: Boolean = false) {
        viewModelScope.launch {
            _isVanderwaalsCollectionSyncing.value = true
            _vanderwaalsCollectionSyncProgress.value = 0f
            _vanderwaalsCollectionSyncMessage.value = "Starting sync..."

            vanderwaalsCollectionRepository.syncVanderwaalsCollectionManifest(
                manifestType = _vanderwaalsCollectionManifestType.value,
                onProgress = { message, progress, count ->
                    _vanderwaalsCollectionSyncMessage.value = message
                    _vanderwaalsCollectionSyncProgress.value = progress
                    _vanderwaalsCollectionWallpaperCount.value = count
                },
                forceUpdate = forceUpdate
            ).fold(
                onSuccess = { count ->
                    _vanderwaalsCollectionWallpaperCount.value = count
                    _isVanderwaalsCollectionSyncing.value = false
                    _vanderwaalsCollectionSyncProgress.value = 1f
                    _vanderwaalsCollectionSyncMessage.value = "Sync complete!"
                    _toastMessage.value = "Synced $count Vanderwaals Collection wallpapers"
                },
                onFailure = { error ->
                    _isVanderwaalsCollectionSyncing.value = false
                    _vanderwaalsCollectionSyncProgress.value = 0f
                    _vanderwaalsCollectionSyncMessage.value = "Sync failed"
                    _toastMessage.value = "Vanderwaals Collection sync failed: ${error.message}"
                }
            )
        }
    }

    /**
     * Updates the Vanderwaals Collection manifest type (lite vs full).
     * If VC is enabled and type changes, will trigger a resync.
     */
    fun updateVanderwaalsCollectionManifestType(type: String) {
        viewModelScope.launch {
            val oldType = _vanderwaalsCollectionManifestType.value
            _vanderwaalsCollectionManifestType.value = type
            settingsDataStore.updateVanderwaalsCollectionManifestType(type)

            // If VC is enabled and type changed, resync
            val vcEnabled = _sourcesEnabled.value["Vanderwaals Collection"] == true
            if (vcEnabled && oldType != type) {
                vanderwaalsCollectionRepository.clearVanderwaalsCollectionWallpapers()
                syncVanderwaalsCollectionWallpapers(forceUpdate = true)
            }
        }
    }

    /**
     * Loads Vanderwaals Collection wallpaper count on init.
     */
    private fun loadVanderwaalsCollectionWallpaperCount() {
        viewModelScope.launch {
            _vanderwaalsCollectionWallpaperCount.value =
                vanderwaalsCollectionRepository.getVanderwaalsCollectionWallpaperCount()
        }
    }
    
    /**
     * Updates the app theme mode.
     */
    fun updateThemeMode(themeMode: ThemeMode) {
        android.util.Log.d("SettingsViewModel", "=== THEME UPDATE STARTED ===")
        android.util.Log.d("SettingsViewModel", "Requested theme: $themeMode")
        
        viewModelScope.launch {
            _themeMode.value = themeMode
            android.util.Log.d("SettingsViewModel", "_themeMode updated to: $themeMode")
            
            val themeString = when (themeMode) {
                ThemeMode.LIGHT -> "light"
                ThemeMode.DARK -> "dark"
                ThemeMode.SYSTEM -> "system"
            }
            
            android.util.Log.d("SettingsViewModel", "Writing to DataStore: $themeString")
            try {
                settingsDataStore.updateThemeMode(themeString)
                android.util.Log.d("SettingsViewModel", "✓ DataStore update completed")
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "✗ DataStore update failed", e)
            }
            android.util.Log.d("SettingsViewModel", "=== THEME UPDATE FINISHED ===")
        }
    }

    /**
     * Updates daily playlist size.
     */
    fun updateDailyPlaylistSize(size: Int) {
        viewModelScope.launch {
            _dailyPlaylistSize.value = size
            settingsDataStore.updateDailyPlaylistSize(size)
        }
    }

    /**
     * Triggers immediate download of daily playlist.
     * Called when user applies "Every Unlock" mode from settings.
     * 
     * This ensures wallpapers are available immediately instead of waiting until 2 AM
     * when the scheduled DailyPlaylistWorker normally runs.
     */
    fun triggerDailyPlaylistDownload() {
        viewModelScope.launch {
            android.util.Log.d("SettingsViewModel", "Triggering immediate daily playlist download")
            _isPlaylistDownloading.value = true
            
            val playlistRequest = OneTimeWorkRequestBuilder<me.avinas.vanderwaals.worker.DailyPlaylistWorker>()
                .setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                        .build()
                )
                .addTag("manual_playlist_download")
                .build()
            
            workManager.enqueue(playlistRequest)
            _toastMessage.value = "Downloading daily playlist..."
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
                        Log.i("SettingsViewModel", "Catalog sync completed: $count wallpapers")
                    },
                    onFailure = { error ->
                        _isSyncing.value = false
                        _syncError.value = error.message
                        _toastMessage.value = "Sync failed: ${error.message}"
                        Log.e("SettingsViewModel", "Catalog sync failed", error)
                    }
                )
            } catch (e: Exception) {
                _isSyncing.value = false
                _syncError.value = e.message
                _toastMessage.value = "Error syncing: ${e.message}"
                Log.e("SettingsViewModel", "Unexpected error during catalog sync", e)
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
                Log.e("SettingsViewModel", "Error clearing wallpaper cache", e)
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

    private fun buildSourceState(
        githubEnabled: Boolean,
        bingEnabled: Boolean,
        vanderwaalsCollectionEnabled: Boolean = false
    ): Map<String, Boolean> {
        return buildMap {
            put("GitHub Collections", githubEnabled)
            put("Bing Wallpapers", bingEnabled)
            put("Vanderwaals Collection", vanderwaalsCollectionEnabled)
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
                ApplyTo.BOTH_DIFFERENT -> "both_different"
            }
            
            // Convert UI ChangeInterval to Worker ChangeInterval
            val workerInterval = when (_interval.value) {
                ChangeInterval.EVERY_UNLOCK -> me.avinas.vanderwaals.worker.ChangeInterval.EVERY_UNLOCK
                ChangeInterval.FIFTEEN_MINUTES -> me.avinas.vanderwaals.worker.ChangeInterval.FIFTEEN_MINUTES
                ChangeInterval.HOURLY -> me.avinas.vanderwaals.worker.ChangeInterval.HOURLY
                ChangeInterval.THREE_HOURS -> me.avinas.vanderwaals.worker.ChangeInterval.THREE_HOURS
                ChangeInterval.SIX_HOURS -> me.avinas.vanderwaals.worker.ChangeInterval.SIX_HOURS
                ChangeInterval.TWELVE_HOURS -> me.avinas.vanderwaals.worker.ChangeInterval.TWELVE_HOURS
                ChangeInterval.DAILY -> me.avinas.vanderwaals.worker.ChangeInterval.DAILY
                ChangeInterval.THREE_DAYS -> me.avinas.vanderwaals.worker.ChangeInterval.THREE_DAYS
                ChangeInterval.SEVEN_DAYS -> me.avinas.vanderwaals.worker.ChangeInterval.SEVEN_DAYS
                ChangeInterval.NEVER -> me.avinas.vanderwaals.worker.ChangeInterval.NEVER
            }
            
            val result = when (_interval.value) {
                ChangeInterval.EVERY_UNLOCK -> {
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
                ChangeInterval.HOURLY,
                ChangeInterval.THREE_HOURS,
                ChangeInterval.SIX_HOURS,
                ChangeInterval.TWELVE_HOURS -> {
                    workScheduler.scheduleWallpaperChange(
                        interval = workerInterval,
                        targetScreen = targetScreen
                    )
                }
                ChangeInterval.FIFTEEN_MINUTES -> {
                    workScheduler.scheduleWallpaperChange(
                        interval = workerInterval,
                        targetScreen = targetScreen
                    )
                }
                ChangeInterval.THREE_DAYS,
                ChangeInterval.SEVEN_DAYS -> {
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
            
            // Handle scheduling result
            when (result) {
                is me.avinas.vanderwaals.worker.SchedulingResult.Success -> {
                    // Success - no message needed
                }
                is me.avinas.vanderwaals.worker.SchedulingResult.PermissionDenied -> {
                    _toastMessage.value = result.message
                    _needsAlarmPermission.value = true
                }
                is me.avinas.vanderwaals.worker.SchedulingResult.BatteryOptimizationWarning -> {
                    _toastMessage.value = result.message
                }
                is me.avinas.vanderwaals.worker.SchedulingResult.Error -> {
                    _toastMessage.value = "Error: ${result.message}"
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
    val lastSynced: String,
    val themeMode: ThemeMode,
    val dailyPlaylistSize: Int,
    val isPlaylistDownloading: Boolean,
    val playlistDownloadProgress: PlaylistDownloadProgress = PlaylistDownloadProgress()
)

/**
 * Progress state for playlist download.
 */
data class PlaylistDownloadProgress(
    val downloadedCount: Int = 0,
    val totalCount: Int = 0,
    val status: String = ""
) {
    val progressText: String
        get() = if (totalCount > 0) "$downloadedCount/$totalCount downloaded" else "Starting download..."
    
    val isApplying: Boolean
        get() = status == me.avinas.vanderwaals.worker.DailyPlaylistWorker.STATUS_APPLYING
}

// Enums for settings
enum class ChangeInterval(val displayName: String) {
    EVERY_UNLOCK("Every Unlock"),
    FIFTEEN_MINUTES("15 Minutes"),
    HOURLY("Hourly"),
    THREE_HOURS("3 Hours"),
    SIX_HOURS("6 Hours"),
    TWELVE_HOURS("12 Hours"),
    DAILY("Daily"),
    THREE_DAYS("3 Days"),
    SEVEN_DAYS("7 Days"),
    NEVER("Never")
}

// Extension to convert LiveData to Flow
fun <T> androidx.lifecycle.LiveData<T>.asFlow(): kotlinx.coroutines.flow.Flow<T> = kotlinx.coroutines.flow.callbackFlow {
    val observer = androidx.lifecycle.Observer<T> { value -> trySend(value) }
    observeForever(observer)
    awaitClose { removeObserver(observer) }
}

enum class ApplyTo(val displayName: String) {
    LOCK_SCREEN("Lock Screen"),
    HOME_SCREEN("Home Screen"),
    BOTH("Both"),
    BOTH_DIFFERENT("Both But Different")
}

data class DailyTime(
    val hour: Int,
    val minute: Int
)

enum class ThemeMode(val displayName: String) {
    SYSTEM("System Default"),
    LIGHT("Light"),
    DARK("Dark")
}
