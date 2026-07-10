package me.avinas.vanderwaals.ui.onboarding

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.avinas.vanderwaals.data.datastore.SettingsDataStore
import me.avinas.vanderwaals.data.entity.UserPreferences
import me.avinas.vanderwaals.data.repository.PreferenceRepository
import me.avinas.vanderwaals.domain.usecase.SelectNextWallpaperUseCase
import me.avinas.vanderwaals.worker.ChangeInterval
import me.avinas.vanderwaals.worker.WallpaperChangeWorker
import me.avinas.vanderwaals.worker.WorkScheduler
import java.time.LocalTime
import javax.inject.Inject

/**
 * ViewModel for application settings screen.
 * 
 * Configures:
 * - **Apply To**: Lock Screen, Home Screen, or Both
 * - **Change Interval**: Every unlock, Hourly, Daily, or Never
 * - **Daily Time**: Time for daily changes (if Daily selected)
 * 
 * **On Start:**
 * 1. Initialize user preferences in database
 * 2. Save settings to preferences
 * 3. Schedule wallpaper changes with WorkManager
 * 4. Apply first wallpaper immediately
 * 5. Navigate to main screen
 * 
 * @param workScheduler WorkManager scheduler
 * @param selectNextWallpaperUseCase Selects first wallpaper
 * @param preferenceRepository Repository for user preferences
 */
@HiltViewModel
class ApplicationSettingsViewModel @Inject constructor(
    private val workScheduler: WorkScheduler,
    private val workManager: WorkManager,
    private val selectNextWallpaperUseCase: SelectNextWallpaperUseCase,
    private val settingsDataStore: SettingsDataStore,
    private val preferenceRepository: PreferenceRepository,
    private val manifestRepository: me.avinas.vanderwaals.data.repository.ManifestRepository,
    private val wallpaperRepository: me.avinas.vanderwaals.data.repository.WallpaperRepository,
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {
    
    private val _applyTo = MutableStateFlow(ApplyTo.BOTH)
    val applyTo: StateFlow<ApplyTo> = _applyTo.asStateFlow()
    
    private val _changeInterval = MutableStateFlow(ChangeInterval.EVERY_UNLOCK)
    val changeInterval: StateFlow<ChangeInterval> = _changeInterval.asStateFlow()
    
    private val _dailyTime = MutableStateFlow(LocalTime.of(9, 0))
    val dailyTime: StateFlow<LocalTime> = _dailyTime.asStateFlow()
    
    private val _dailyPlaylistSize = MutableStateFlow(15)
    val dailyPlaylistSize: StateFlow<Int> = _dailyPlaylistSize.asStateFlow()
    
    private val _startState = MutableStateFlow<StartState>(StartState.Idle)
    val startState: StateFlow<StartState> = _startState.asStateFlow()
    
    private val _needsAlarmPermission = MutableStateFlow(false)
    val needsAlarmPermission: StateFlow<Boolean> = _needsAlarmPermission.asStateFlow()
    
    /**
     * Set apply to preference.
     * 
     * @param applyTo Target screen(s)
     */
    fun setApplyTo(applyTo: ApplyTo) {
        _applyTo.value = applyTo
    }
    
    /**
     * Set change interval preference.
     * 
     * @param interval Change frequency
     */
    fun setChangeInterval(interval: ChangeInterval) {
        _changeInterval.value = interval
    }
    
    /**
     * Set daily change time.
     * 
     * @param time Time of day for daily changes
     */
    fun setDailyTime(time: LocalTime) {
        _dailyTime.value = time
    }
    
    fun setDailyPlaylistSize(size: Int) {
        _dailyPlaylistSize.value = size
    }
    
    /**
     * Start using app with configured settings.
     * 
     * Steps:
     * 1. Initialize user preferences in database with selected mode
     * 2. Save settings to DataStore
     * 3. Schedule wallpaper changes with WorkScheduler
     * 4. Select and apply first wallpaper
     * 5. Navigate to main screen
     * 
     * @param selectedMode Selected mode from onboarding (Auto or Personalize)
     */
    fun startUsing(selectedMode: OnboardingMode? = null) {
        viewModelScope.launch {
            _startState.value = StartState.Starting("Preparing...", 0.0f)
            
            try {
                // Database should already be synced from InitialSyncScreen
                // Just verify it has wallpapers
                Log.d("ApplicationSettings", "Verifying wallpaper catalog...")
                _startState.value = StartState.Starting("Preparing wallpapers...", 0.1f)
                
                val isDatabaseInitialized = manifestRepository.isDatabaseInitialized()

                // Get enabled sources
                val settings = settingsDataStore.settings.first()
                val githubEnabled = settings.githubEnabled
                val bingEnabled = settings.bingEnabled
                val vanderwaalsCollectionEnabled = settings.vanderwaalsCollectionEnabled

                if (!isDatabaseInitialized) {
                    _startState.value = StartState.Error(
                        "Wallpaper catalog not available. Please restart the app."
                    )
                    return@launch
                }

                val wallpaperCount = manifestRepository.getWallpaperCount()
                Log.d("ApplicationSettings", "Catalog ready with $wallpaperCount wallpapers")

                // Download first wallpaper for immediate display (10% -> 40%)
                // PRIORITY: Use first liked wallpaper from confirmation gallery if available
                Log.d("ApplicationSettings", "Preparing first wallpaper for immediate display...")
                _startState.value = StartState.Starting("Preparing your first wallpaper...", 0.2f)

                val existingPrefs = preferenceRepository.getUserPreferences().first()
                val likedIds = existingPrefs?.likedWallpaperIds ?: emptySet()

                val allWallpapers = wallpaperRepository.getAllWallpapers().first()
                var preDownloadedWallpaperId: String? = null
                if (allWallpapers.isNotEmpty()) {
                    // Build the set of enabled source names to filter candidates
                    val enabledSources = buildSet<String> {
                        if (githubEnabled) add("github")
                        if (bingEnabled) add("bing")
                        if (vanderwaalsCollectionEnabled) add("vanderwaals")
                    }
                    
                    val sourceMatchingWallpapers = if (enabledSources.isEmpty()) {
                        allWallpapers
                    } else {
                        allWallpapers.filter { it.source.lowercase() in enabledSources }
                    }

                    val pool = sourceMatchingWallpapers.ifEmpty { allWallpapers }
                    
                    // Try to use first liked wallpaper, fallback to random
                    val firstWallpaper = if (likedIds.isNotEmpty()) {
                        val firstLikedId = likedIds.first()
                        pool.find { it.id == firstLikedId }
                            ?: pool.random()
                            .also { Log.w("ApplicationSettings", "Liked wallpaper $firstLikedId not found, using random") }
                    } else {
                        pool.random()
                    }
                    
                    Log.d("ApplicationSettings", "Selected wallpaper: ${firstWallpaper.id} (liked: ${likedIds.contains(firstWallpaper.id)})")
                    
                    val downloadResult = wallpaperRepository.downloadWallpaper(firstWallpaper)
                    if (downloadResult.isSuccess) {
                        wallpaperRepository.markAsDownloaded(firstWallpaper.id)
                        preDownloadedWallpaperId = firstWallpaper.id
                        Log.d("ApplicationSettings", "Downloaded first wallpaper: ${firstWallpaper.id}")
                    } else {
                        Log.w("ApplicationSettings", "Failed to download first wallpaper: ${downloadResult.exceptionOrNull()?.message}")
                    }
                }
                
                _startState.value = StartState.Starting("Configuring preferences...", 0.4f)
                
                // Step 1: Initialize user preferences ONLY if not already set (40% -> 60%)
                // CRITICAL: Do NOT overwrite preferences if user completed personalization
                // (InitializePreferencesUseCase already saved them with learned vectors)
                val existingPreferences = preferenceRepository.getUserPreferences().first()
                if (existingPreferences == null) {
                    // User chose AUTO mode - create default preferences
                    val mode = when (selectedMode) {
                        OnboardingMode.AUTO -> UserPreferences.MODE_AUTO
                        OnboardingMode.PERSONALIZE -> UserPreferences.MODE_PERSONALIZED
                        null -> UserPreferences.MODE_AUTO // Default to auto if not specified
                    }
                    
                    val defaultPreferences = UserPreferences.createDefault().copy(mode = mode)
                    preferenceRepository.insertUserPreferences(defaultPreferences)
                    Log.d("ApplicationSettings", "Created default preferences for AUTO mode")
                } else {
                    Log.d("ApplicationSettings", "Preferences already exist (feedbackCount=${existingPreferences.feedbackCount}), not overwriting")
                }
                
                // Step 2: Save settings to DataStore (60% -> 70%)
                _startState.value = StartState.Starting("Saving settings...", 0.6f)
                
                // Set which screen(s) to apply wallpaper to
                val applyToString = when (_applyTo.value) {
                    ApplyTo.LOCK_SCREEN -> "lock_screen"
                    ApplyTo.HOME_SCREEN -> "home_screen"
                    ApplyTo.BOTH -> "both"
                    ApplyTo.BOTH_DIFFERENT -> "both_different"
                }
                
                // Set wallpaper change interval
                val intervalString = when (_changeInterval.value) {
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
                
                // Save settings
                settingsDataStore.updateApplyTo(applyToString)
                settingsDataStore.updateInterval(intervalString, _dailyTime.value)
                settingsDataStore.updateDailyPlaylistSize(_dailyPlaylistSize.value)
                settingsDataStore.markOnboardingComplete()
                settingsDataStore.updateEmbeddingDimension(SettingsDataStore.EMBEDDING_DIM_CURRENT)
                
                // Add small delay to ensure database transaction completes
                // This prevents race condition where worker runs before preferences are written
                delay(500L)
                
                // Step 3: Schedule wallpaper changes (70% -> 85%)
                _startState.value = StartState.Starting("Setting up auto-change...", 0.7f)
                
                // Check if alarm permission is needed for auto-change (all intervals except NEVER)
                if (_changeInterval.value != ChangeInterval.NEVER && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    val alarmManager = context.getSystemService(android.content.Context.ALARM_SERVICE) as? android.app.AlarmManager
                    if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                        _needsAlarmPermission.value = true
                        _startState.value = StartState.Error("Alarm permission required")
                        return@launch
                    }
                }
                
                // IMPORTANT: This must come AFTER initializing preferences in the database
                // so the worker can access them when it executes
                val targetScreen = when (_applyTo.value) {
                    ApplyTo.LOCK_SCREEN -> "lock"
                    ApplyTo.HOME_SCREEN -> "home"
                    ApplyTo.BOTH -> "both"
                    ApplyTo.BOTH_DIFFERENT -> "both_different"
                }
                
                // Schedule Daily Playlist if "Every Unlock" is selected
                if (_changeInterval.value == ChangeInterval.EVERY_UNLOCK) {
                    _startState.value = StartState.Starting("Downloading daily playlist...", 0.75f)
                    // Trigger immediate download for playlist
                    val playlistRequest = OneTimeWorkRequestBuilder<me.avinas.vanderwaals.worker.DailyPlaylistWorker>()
                        .setConstraints(
                            androidx.work.Constraints.Builder()
                                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                                .build()
                        )
                        .addTag("initial_playlist_download")
                        .build()
                    workManager.enqueue(playlistRequest)
                    
                    // Wait a bit for download to start/progress
                    delay(1000L)
                }
                
                when (_changeInterval.value) {
                    ChangeInterval.EVERY_UNLOCK -> {
                        workScheduler.scheduleWallpaperChange(
                            interval = ChangeInterval.EVERY_UNLOCK,
                            targetScreen = targetScreen
                        )
                    }
                    ChangeInterval.DAILY -> {
                        val schedulingResult = workScheduler.scheduleWallpaperChange(
                            interval = ChangeInterval.DAILY,
                            time = LocalTime.of(_dailyTime.value.hour, _dailyTime.value.minute),
                            targetScreen = targetScreen
                        )
                        // Handle permission denial for daily alarms
                        if (schedulingResult is me.avinas.vanderwaals.worker.SchedulingResult.PermissionDenied) {
                            _needsAlarmPermission.value = true
                            _startState.value = StartState.Error(schedulingResult.message)
                            return@launch
                        }
                    }
                    ChangeInterval.HOURLY -> {
                        val schedulingResult = workScheduler.scheduleWallpaperChange(
                            interval = ChangeInterval.HOURLY,
                            targetScreen = targetScreen
                        )
                        // Handle permission denial for hourly alarms
                        if (schedulingResult is me.avinas.vanderwaals.worker.SchedulingResult.PermissionDenied) {
                            _needsAlarmPermission.value = true
                            _startState.value = StartState.Error(schedulingResult.message)
                            return@launch
                        }
                    }
                    ChangeInterval.THREE_HOURS -> {
                        val schedulingResult = workScheduler.scheduleWallpaperChange(
                            interval = ChangeInterval.THREE_HOURS,
                            targetScreen = targetScreen
                        )
                        if (schedulingResult is me.avinas.vanderwaals.worker.SchedulingResult.PermissionDenied) {
                            _needsAlarmPermission.value = true
                            _startState.value = StartState.Error(schedulingResult.message)
                            return@launch
                        }
                    }
                    ChangeInterval.SIX_HOURS -> {
                        val schedulingResult = workScheduler.scheduleWallpaperChange(
                            interval = ChangeInterval.SIX_HOURS,
                            targetScreen = targetScreen
                        )
                        if (schedulingResult is me.avinas.vanderwaals.worker.SchedulingResult.PermissionDenied) {
                            _needsAlarmPermission.value = true
                            _startState.value = StartState.Error(schedulingResult.message)
                            return@launch
                        }
                    }
                    ChangeInterval.TWELVE_HOURS -> {
                        val schedulingResult = workScheduler.scheduleWallpaperChange(
                            interval = ChangeInterval.TWELVE_HOURS,
                            targetScreen = targetScreen
                        )
                        if (schedulingResult is me.avinas.vanderwaals.worker.SchedulingResult.PermissionDenied) {
                            _needsAlarmPermission.value = true
                            _startState.value = StartState.Error(schedulingResult.message)
                            return@launch
                        }
                    }
                    ChangeInterval.THREE_DAYS -> {
                        val schedulingResult = workScheduler.scheduleWallpaperChange(
                            interval = ChangeInterval.THREE_DAYS,
                            targetScreen = targetScreen
                        )
                        if (schedulingResult is me.avinas.vanderwaals.worker.SchedulingResult.PermissionDenied) {
                            _needsAlarmPermission.value = true
                            _startState.value = StartState.Error(schedulingResult.message)
                            return@launch
                        }
                    }
                    ChangeInterval.SEVEN_DAYS -> {
                        val schedulingResult = workScheduler.scheduleWallpaperChange(
                            interval = ChangeInterval.SEVEN_DAYS,
                            targetScreen = targetScreen
                        )
                        if (schedulingResult is me.avinas.vanderwaals.worker.SchedulingResult.PermissionDenied) {
                            _needsAlarmPermission.value = true
                            _startState.value = StartState.Error(schedulingResult.message)
                            return@launch
                        }
                    }
                    ChangeInterval.FIFTEEN_MINUTES -> {
                        val schedulingResult = workScheduler.scheduleWallpaperChange(
                            interval = ChangeInterval.FIFTEEN_MINUTES,
                            targetScreen = targetScreen
                        )
                        // Handle permission denial for 15-minute alarms
                        if (schedulingResult is me.avinas.vanderwaals.worker.SchedulingResult.PermissionDenied) {
                            _needsAlarmPermission.value = true
                            _startState.value = StartState.Error(schedulingResult.message)
                            return@launch
                        }
                    }
                    ChangeInterval.NEVER -> {
                        workScheduler.scheduleWallpaperChange(
                            interval = ChangeInterval.NEVER,
                            targetScreen = targetScreen
                        )
                    }
                }
                
               // Step 4: Trigger immediate wallpaper change (85% -> 95%)
                _startState.value = StartState.Starting("Applying your first wallpaper...", 0.85f)
                
                // IMPORTANT: Queue immediate one-time wallpaper change
                // This ensures wallpaper is applied and visible when user returns to main screen
                // CRITICAL FIX: Pass targetScreen parameter to worker
                // CRITICAL FIX: Pass pre-downloaded wallpaper ID so the worker skips the
                // expensive SelectNextWallpaperUseCase (16+ seconds) and re-download —
                // the wallpaper is already cached on disk from the pre-download above.
                val immediateChangeRequest = OneTimeWorkRequestBuilder<WallpaperChangeWorker>()
                    .setInputData(workDataOf(
                        WallpaperChangeWorker.KEY_TARGET_SCREEN to targetScreen,
                        WallpaperChangeWorker.KEY_MODE to WallpaperChangeWorker.MODE_VANDERWAALS,
                        WallpaperChangeWorker.KEY_SELECTED_WALLPAPER_ID to preDownloadedWallpaperId
                    ))
                    .addTag("manual_change")
                    .addTag("immediate_onboarding")
                    .build()
                val workId = immediateChangeRequest.id
                workManager.enqueue(immediateChangeRequest)
                
                Log.d("ApplicationSettings", "Immediate wallpaper change triggered for target: $targetScreen")
                
                // Step 5: Wait for wallpaper change to complete before finishing onboarding
                // This ensures wallpaper is applied before user sees main screen
                var wallpaperChangeCompleted = false
                viewModelScope.launch {
                    workManager.getWorkInfoByIdFlow(workId).collect { workInfo ->
                        if (workInfo != null && workInfo.state.isFinished) {
                            if (workInfo.state == androidx.work.WorkInfo.State.SUCCEEDED) {
                                Log.d("ApplicationSettings", "Immediate wallpaper change completed successfully")
                            } else {
                                Log.w("ApplicationSettings", "Immediate wallpaper change failed, continuing anyway")
                            }
                            wallpaperChangeCompleted = true
                            return@collect
                        }
                    }
                }
                
                // Wait up to 90 seconds for wallpaper change to complete
                var waitTime = 0
                while (!wallpaperChangeCompleted && waitTime < 90) {
                    delay(1000)
                    waitTime++
                    if (waitTime % 10 == 0) {
                        Log.d("ApplicationSettings", "Waiting for wallpaper change... ${waitTime}s")
                    }
                }
                
                if (!wallpaperChangeCompleted) {
                    Log.w("ApplicationSettings", "Timeout waiting for wallpaper change, continuing anyway")
                } else {
                    Log.d("ApplicationSettings", "Wallpaper change completed, proceeding to main screen")
                }
                
                // Step 6: Finish onboarding (95% -> 100%)
                _startState.value = StartState.Starting("Finishing setup...", 0.95f)
                
                // Small delay for smooth UX
                delay(500L)
                
                _startState.value = StartState.Success
            } catch (e: Exception) {
                _startState.value = StartState.Error(
                    e.message ?: "Failed to start app"
                )
            }
        }
    }
    
    /**
     * Reset start state.
     */
    fun resetStartState() {
        _startState.value = StartState.Idle
    }
    
    /**
     * Reset state for back navigation.
     * 
     * Resets the start state to Idle so user can modify settings
     * and try again. Does NOT reset user's setting selections
     * (applyTo, changeInterval, dailyTime) - those are preserved.
     */
    fun resetStateForBackNavigation() {
        android.util.Log.d("ApplicationSettingsVM", "Resetting state for back navigation")
        _startState.value = StartState.Idle
        _needsAlarmPermission.value = false
    }
    
    // Flag to track if user went to permission settings
    private var _waitingForAlarmPermission = false
    
    // Warning message for the user (observed by UI to show toast/snackbar)
    private val _warningMessage = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val warningMessage: kotlinx.coroutines.flow.StateFlow<String?> = _warningMessage.asStateFlow()
    
    fun clearWarningMessage() {
        _warningMessage.value = null
    }
    
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
                android.util.Log.e("ApplicationSettingsVM", "Failed to open alarm permission settings", e)
                _waitingForAlarmPermission = false
            }
        }
        _needsAlarmPermission.value = false
    }
    
    /**
     * Called when the app resumes (user returns from permission settings).
     * Re-checks permission and continues the start flow if granted.
     */
    fun onResume() {
        if (_waitingForAlarmPermission && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            _waitingForAlarmPermission = false
            val alarmManager = context.getSystemService(android.content.Context.ALARM_SERVICE) as? android.app.AlarmManager
            if (alarmManager != null && alarmManager.canScheduleExactAlarms()) {
                android.util.Log.d("ApplicationSettingsVM", "Alarm permission granted, continuing start flow")
                _warningMessage.value = "Permission granted! Setting up auto-change..."
                // Continue the start flow - user just granted permission
                startUsing()
            } else {
                android.util.Log.w("ApplicationSettingsVM", "Alarm permission still not granted")
                // Show the dialog again or let user decide
                _needsAlarmPermission.value = true
            }
        }
    }
    
    /**
     * Dismisses the alarm permission dialog.
     * WARNING: Proceeds with inexact scheduling (WorkManager fallback).
     */
    fun dismissAlarmPermissionDialog() {
        _needsAlarmPermission.value = false
        _warningMessage.value = "Wallpaper timing may be inexact without alarm permission"
        // Proceed with the start flow anyway - will use WorkManager fallback
        android.util.Log.w("ApplicationSettingsVM", "User dismissed alarm permission, proceeding with inexact scheduling")
        startAppWithInexactScheduling()
    }
    
    /**
     * Start app flow that bypasses the alarm permission check.
     * Used when user dismisses permission dialog and wants to proceed anyway.
     */
    private fun startAppWithInexactScheduling() {
        viewModelScope.launch {
            try {
                _startState.value = StartState.Starting("Saving settings...", 0.5f)
                
                // Get current values
                val applyToValue = when (_applyTo.value) {
                    ApplyTo.LOCK_SCREEN -> "lock_screen"
                    ApplyTo.HOME_SCREEN -> "home_screen"
                    ApplyTo.BOTH -> "both"
                    ApplyTo.BOTH_DIFFERENT -> "both_different"
                }
                
                val intervalValue = when (_changeInterval.value) {
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
                
                // Save settings
                settingsDataStore.updateApplyTo(applyToValue)
                settingsDataStore.updateInterval(
                    intervalValue,
                    if (_changeInterval.value == ChangeInterval.DAILY) {
                        java.time.LocalTime.of(_dailyTime.value.hour, _dailyTime.value.minute)
                    } else null
                )
                settingsDataStore.updateDailyPlaylistSize(_dailyPlaylistSize.value)
                settingsDataStore.markOnboardingComplete()
                
                delay(500L)
                
                _startState.value = StartState.Starting("Setting up auto-change (inexact)...", 0.7f)
                
                // Schedule with WorkManager fallback (inexact)
                val targetScreen = when (_applyTo.value) {
                    ApplyTo.LOCK_SCREEN -> "lock"
                    ApplyTo.HOME_SCREEN -> "home"
                    ApplyTo.BOTH -> "both"
                    ApplyTo.BOTH_DIFFERENT -> "both_different"
                }
                
                if (_changeInterval.value == ChangeInterval.EVERY_UNLOCK) {
                    _startState.value = StartState.Starting("Downloading daily playlist...", 0.75f)
                    val playlistRequest = androidx.work.OneTimeWorkRequestBuilder<me.avinas.vanderwaals.worker.DailyPlaylistWorker>()
                        .setConstraints(
                            androidx.work.Constraints.Builder()
                                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                                .build()
                        )
                        .addTag("initial_playlist_download")
                        .build()
                    workManager.enqueueUniqueWork(
                        "initial_playlist_download",
                        androidx.work.ExistingWorkPolicy.REPLACE,
                        playlistRequest
                    )
                }
                
                workScheduler.scheduleWallpaperChange(
                    interval = _changeInterval.value,
                    time = java.time.LocalTime.of(_dailyTime.value.hour, _dailyTime.value.minute),
                    targetScreen = targetScreen
                )
                
                _startState.value = StartState.Starting("Finalizing...", 0.95f)
                delay(300L)
                _startState.value = StartState.Success
                
            } catch (e: Exception) {
                android.util.Log.e("ApplicationSettingsVM", "Error in inexact scheduling flow", e)
                _startState.value = StartState.Error(e.message ?: "Unknown error")
            }
        }
    }
}

/**
 * Screen(s) to apply wallpaper to.
 */
enum class ApplyTo(val displayName: String) {
    LOCK_SCREEN("Lock Screen"),
    HOME_SCREEN("Home Screen"),
    BOTH("Both"),
    BOTH_DIFFERENT("Both But Different")
}

/**
 * App start state with progress tracking.
 */
sealed class StartState {
    /**
     * Idle, not started.
     */
    data object Idle : StartState()
    
    /**
     * Starting app with progress.
     * 
     * @param step Current step description
     * @param progress Progress from 0.0 to 1.0 (null if indeterminate)
     */
    data class Starting(
        val step: String,
        val progress: Float? = null
    ) : StartState()
    
    /**
     * Successfully started.
     */
    data object Success : StartState()
    
    /**
     * Error during start.
     * 
     * @param message Error description
     */
    data class Error(val message: String) : StartState()
}
