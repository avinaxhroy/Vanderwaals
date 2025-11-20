package me.avinas.vanderwaals.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import me.avinas.vanderwaals.data.dao.WallpaperHistoryDao
import me.avinas.vanderwaals.data.entity.WallpaperHistory
import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import me.avinas.vanderwaals.data.repository.WallpaperRepository
import me.avinas.vanderwaals.domain.usecase.FeedbackType
import me.avinas.vanderwaals.domain.usecase.UpdatePreferencesUseCase
import me.avinas.vanderwaals.worker.WallpaperChangeWorker
import javax.inject.Inject

/**
 * ViewModel for main screen state management.
 * 
 * Manages:
 * - Current wallpaper display (URI, metadata, source attribution)
 * - Wallpaper queue (ranked list of next wallpapers)
 * - Manual "Change Now" action
 * - Quick actions (like, dislike, download)
 * - Bottom sheet visibility state
 * - Loading states for wallpaper changes
 * 
 * StateFlow emissions:
 * - CurrentWallpaper: Currently displayed wallpaper with metadata
 * - QueueState: Number of wallpapers in queue, queue health
 * - LoadingState: Processing state for wallpaper changes
 * - BottomSheetState: Overlay visibility
 * 
 * Coordinates with:
 * - GetRankedWallpapersUseCase: Populate queue
 * - ProcessFeedbackUseCase: Handle likes/dislikes
 * - WallpaperChangeWorker: Trigger manual changes
 * - Paperize's WallpaperService: Apply wallpaper
 * 
 * Observes:
 * - Wallpaper change events from WorkManager
 * - Feedback updates that require queue reranking
 * - Settings changes (mode, frequency, apply to)
 * 
 * @see MainScreen
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val wallpaperRepository: WallpaperRepository,
    private val historyDao: WallpaperHistoryDao,
    private val updatePreferencesUseCase: UpdatePreferencesUseCase,
    private val workManager: WorkManager,
    private val application: android.app.Application
) : ViewModel() {

    /**
     * Current wallpaper metadata being displayed.
     * Null if no wallpaper has been set yet.
     * Reactively updates when wallpaper history changes.
     */
    val currentWallpaper: StateFlow<WallpaperMetadata?> = historyDao.getActiveWallpaperFlow()
        .combine(wallpaperRepository.getAllWallpapers()) { activeHistory, wallpapers ->
            if (activeHistory != null) {
                wallpapers.find { it.id == activeHistory.wallpaperId }
            } else {
                null
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    /**
     * Whether the bottom sheet overlay is visible.
     * Starts as false, user taps screen to toggle.
     */
    private val _showOverlay = MutableStateFlow(false)
    val showOverlay: StateFlow<Boolean> = _showOverlay.asStateFlow()

    /**
     * Loading state for wallpaper change operations.
     * True while WallpaperChangeWorker is executing.
     */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * Error message state for displaying errors via Snackbar.
     */
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /**
     * Live wallpaper detection state.
     * True when a blocking live wallpaper service is detected.
     */
    private val _showLiveWallpaperDialog = MutableStateFlow(false)
    val  showLiveWallpaperDialog: StateFlow<Boolean> = _showLiveWallpaperDialog.asStateFlow()

    /**
     * Live wallpaper service details for dialog display.
     */
    private val _liveWallpaperInfo = MutableStateFlow<Pair<String, String?>>("" to null)
    val liveWallpaperInfo: StateFlow<Pair<String, String?>> = _liveWallpaperInfo.asStateFlow()

    /**
     * Show instructions dialog state.
     */
    private val _showInstructionsDialog = MutableStateFlow(false)
    val showInstructionsDialog: StateFlow<Boolean> = _showInstructionsDialog.asStateFlow()

    /**
     * Clears the error message after it's been shown.
     */
    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    /**
     * Checks if live wallpaper is active and updates dialog state.
     */
    fun checkForLiveWallpaper() {
        try {
            if (me.avinas.vanderwaals.core.LiveWallpaperDetector.isLiveWallpaperActive(application)) {
                val (isBlocking, serviceName) = me.avinas.vanderwaals.core.LiveWallpaperDetector.isKnownBlockingService(application)
                val packageName = me.avinas.vanderwaals.core.LiveWallpaperDetector.getLiveWallpaperPackageName(application)
                val displayName = serviceName ?: me.avinas.vanderwaals.core.LiveWallpaperDetector.getLiveWallpaperDisplayName(application)
                
                _liveWallpaperInfo.value = displayName to packageName
                _showLiveWallpaperDialog.value = true
                
                android.util.Log.d("MainViewModel", "Live wallpaper detected: $displayName ($packageName)")
            }
        } catch (e: Exception) {
            android.util.Log.e("MainViewModel", "Error checking for live wallpaper", e)
        }
    }

    /**
     * Dismisses the live wallpaper dialog.
     */
    fun dismissLiveWallpaperDialog() {
        _showLiveWallpaperDialog.value = false
    }

    /**
     * Shows the instructions dialog.
     */
    fun showInstructions() {
        _showInstructionsDialog.value = true
        _showLiveWallpaperDialog.value = false  // Hide main dialog when showing instructions
    }

    /**
     * Dismisses the instructions dialog.
     */
    fun dismissInstructionsDialog() {
        _showInstructionsDialog.value = false
    }

    /**
     * Handles successful settings navigation.
     */
    fun onSettingsOpened() {
        // Record that user opened settings
        android.util.Log.d("MainViewModel", "User opened live wallpaper settings")
    }

    init {
        // Current wallpaper is now reactive via StateFlow above
        // Check for live wallpaper on init
        checkForLiveWallpaper()
    }

    /**
     * Toggles the visibility of the bottom sheet overlay.
     * 
     * Called when user taps anywhere on the screen.
     */
    fun toggleOverlay() {
        _showOverlay.value = !_showOverlay.value
    }

    /**
     * Triggers immediate wallpaper change via WorkManager.
     * 
     * **Flow:**
     * 1. Show loading indicator
     * 2. Create OneTimeWorkRequest for WallpaperChangeWorker
     * 3. Enqueue work with WorkManager
     * 4. Observe work status
     * 5. Update currentWallpaper when complete
     * 6. Hide overlay and loading indicator
     * 
     * **Error Handling:**
     * - If no downloaded wallpapers available, show error
     * - If WorkManager fails, show error toast
     * - Network errors handled by worker retry logic
     * 
     * CRITICAL FIX: Worker will dynamically load Apply To setting from DataStore,
     * so no need to pass targetScreen here - worker handles it.
     */
    fun changeNow() {
        viewModelScope.launch {
            try {
                _isLoading.value = true

                // Create and enqueue wallpaper change work
                // Worker will load current Apply To setting from DataStore dynamically
                // Mark as manual change for implicit feedback processing
                val workRequest = OneTimeWorkRequestBuilder<WallpaperChangeWorker>()
                    .setInputData(androidx.work.workDataOf(
                        WallpaperChangeWorker.KEY_MODE to WallpaperChangeWorker.MODE_VANDERWAALS,
                        WallpaperChangeWorker.KEY_IS_MANUAL_CHANGE to true
                    ))
                    .addTag("manual_change")
                    .build()

                workManager.enqueue(workRequest)

                android.util.Log.d("MainViewModel", "Manual wallpaper change triggered - worker will load Apply To setting")

                // Observe work completion
                workManager.getWorkInfoByIdFlow(workRequest.id)
                    .collect { workInfo ->
                        when {
                            workInfo?.state?.isFinished == true -> {
                                _isLoading.value = false
                                
                                // Check if wallpaper change failed due to live wallpaper
                                if (workInfo.state == androidx.work.WorkInfo.State.FAILED) {
                                    // Check if failure was due to live wallpaper
                                    checkForLiveWallpaper()
                                }
                                
                                // Current wallpaper will update reactively via StateFlow
                                // Auto-hide overlay after successful change
                                if (workInfo.state == androidx.work.WorkInfo.State.SUCCEEDED) {
                                    _showOverlay.value = false
                                }
                            }
                        }
                    }
            } catch (e: Exception) {
                _isLoading.value = false
                _errorMessage.value = "Error changing wallpaper: ${e.localizedMessage ?: "Unknown error"}"
            }
        }
    }
    
    /**
     * Records like feedback for the current wallpaper.
     * 
     * Updates the preference vector and history with positive feedback.
     * This makes the algorithm show more wallpapers similar to this one.
     */
    fun likeCurrentWallpaper() {
        viewModelScope.launch {
            try {
                val wallpaper = currentWallpaper.value
                if (wallpaper == null) {
                    _errorMessage.value = "No wallpaper to like"
                    return@launch
                }
                
                // Update preferences with positive feedback
                val result = updatePreferencesUseCase(wallpaper, FeedbackType.LIKE)
                
                result.fold(
                    onSuccess = {
                        // Update history with feedback and context
                        val activeHistory = historyDao.getActiveWallpaper()
                        if (activeHistory != null) {
                            // Capture contextual information
                            val context = me.avinas.vanderwaals.data.entity.FeedbackContext.fromCurrentState(
                                application
                            )
                            
                            // Update history with feedback and context using repository
                            wallpaperRepository.updateHistoryWithContext(
                                activeHistory.id,
                                FeedbackType.LIKE,
                                context
                            )
                        }
                        
                        // Show success message
                        _errorMessage.value = "✓ Learning your taste"
                        android.util.Log.d("MainViewModel", "Liked wallpaper: ${wallpaper.id}, category: ${wallpaper.category}")
                    },
                    onFailure = { error ->
                        _errorMessage.value = "Failed to record feedback"
                        android.util.Log.e("MainViewModel", "Failed to like wallpaper", error)
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "Error recording like: ${e.localizedMessage}"
                android.util.Log.e("MainViewModel", "Exception in likeCurrentWallpaper", e)
            }
        }
    }
    
    /**
     * Records dislike feedback for the current wallpaper.
     * 
     * Updates the preference vector and history with negative feedback.
     * This makes the algorithm avoid wallpapers similar to this one.
     */
    fun dislikeCurrentWallpaper() {
        viewModelScope.launch {
            try {
                val wallpaper = currentWallpaper.value
                if (wallpaper == null) {
                    _errorMessage.value = "No wallpaper to dislike"
                    return@launch
                }
                
                // Update preferences with negative feedback
                val result = updatePreferencesUseCase(wallpaper, FeedbackType.DISLIKE)
                
                result.fold(
                    onSuccess = {
                        // Update history with feedback and context
                        val activeHistory = historyDao.getActiveWallpaper()
                        if (activeHistory != null) {
                            // Capture contextual information
                            val context = me.avinas.vanderwaals.data.entity.FeedbackContext.fromCurrentState(
                                application
                            )
                            
                            // Update history with feedback and context using repository
                            wallpaperRepository.updateHistoryWithContext(
                                activeHistory.id,
                                FeedbackType.DISLIKE,
                                context
                            )
                        }
                        
                        // Show success message
                        _errorMessage.value = "✓ Will show less like this"
                        android.util.Log.d("MainViewModel", "Disliked wallpaper: ${wallpaper.id}, category: ${wallpaper.category}")
                    },
                    onFailure = { error ->
                        _errorMessage.value = "Failed to record feedback"
                        android.util.Log.e("MainViewModel", "Failed to dislike wallpaper", error)
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "Error recording dislike: ${e.localizedMessage}"
                android.util.Log.e("MainViewModel", "Exception in dislikeCurrentWallpaper", e)
            }
        }
    }
}
