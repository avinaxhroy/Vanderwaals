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
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {
    
    private val _applyTo = MutableStateFlow(ApplyTo.BOTH)
    val applyTo: StateFlow<ApplyTo> = _applyTo.asStateFlow()
    
    private val _changeInterval = MutableStateFlow(ChangeInterval.EVERY_15_MINUTES)
    val changeInterval: StateFlow<ChangeInterval> = _changeInterval.asStateFlow()
    
    private val _dailyTime = MutableStateFlow(LocalTime.of(9, 0))
    val dailyTime: StateFlow<LocalTime> = _dailyTime.asStateFlow()
    
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
                if (!isDatabaseInitialized) {
                    _startState.value = StartState.Error(
                        "Wallpaper catalog not available. Please restart the app."
                    )
                    return@launch
                }
                
                val wallpaperCount = manifestRepository.getWallpaperCount()
                Log.d("ApplicationSettings", "Catalog ready with $wallpaperCount wallpapers")
                
                // Download first wallpaper for immediate display (10% -> 40%)
                Log.d("ApplicationSettings", "Downloading first wallpaper for immediate display...")
                _startState.value = StartState.Starting("Preparing your first wallpaper...", 0.2f)
                
                val allWallpapers = wallpaperRepository.getAllWallpapers().first()
                if (allWallpapers.isNotEmpty()) {
                    val firstWallpaper = allWallpapers.random()
                    val downloadResult = wallpaperRepository.downloadWallpaper(firstWallpaper)
                    if (downloadResult.isSuccess) {
                        wallpaperRepository.markAsDownloaded(firstWallpaper.id)
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
                }
                
                // Set wallpaper change interval
                val intervalString = when (_changeInterval.value) {
                    ChangeInterval.EVERY_15_MINUTES -> "15min"
                    ChangeInterval.HOURLY -> "hourly"
                    ChangeInterval.DAILY -> "daily"
                    ChangeInterval.NEVER -> "never"
                }
                
                // Save settings
                settingsDataStore.updateApplyTo(applyToString)
                settingsDataStore.updateInterval(intervalString, _dailyTime.value)
                settingsDataStore.markOnboardingComplete()
                
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
                }
                
                when (_changeInterval.value) {
                    ChangeInterval.EVERY_15_MINUTES -> {
                        workScheduler.scheduleWallpaperChange(
                            interval = ChangeInterval.EVERY_15_MINUTES,
                            targetScreen = targetScreen
                        )
                    }
                    ChangeInterval.DAILY -> {
                        workScheduler.scheduleWallpaperChange(
                            interval = ChangeInterval.DAILY,
                            time = LocalTime.of(_dailyTime.value.hour, _dailyTime.value.minute),
                            targetScreen = targetScreen
                        )
                    }
                    ChangeInterval.HOURLY -> {
                        workScheduler.scheduleWallpaperChange(
                            interval = ChangeInterval.HOURLY,
                            targetScreen = targetScreen
                        )
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
                val immediateChangeRequest = OneTimeWorkRequestBuilder<WallpaperChangeWorker>()
                    .setInputData(workDataOf(
                        WallpaperChangeWorker.KEY_TARGET_SCREEN to targetScreen,
                        WallpaperChangeWorker.KEY_MODE to WallpaperChangeWorker.MODE_VANDERWAALS
                    ))
                    .addTag("manual_change")
                    .addTag("immediate_onboarding")
                    .build()
                workManager.enqueue(immediateChangeRequest)
                
                Log.d("ApplicationSettings", "Immediate wallpaper change triggered for target: $targetScreen")
                
                // Step 5: Finish onboarding (95% -> 100%)
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
                android.util.Log.e("ApplicationSettingsVM", "Failed to open alarm permission settings", e)
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
}

/**
 * Screen(s) to apply wallpaper to.
 */
enum class ApplyTo(val displayName: String) {
    LOCK_SCREEN("Lock Screen"),
    HOME_SCREEN("Home Screen"),
    BOTH("Both")
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
