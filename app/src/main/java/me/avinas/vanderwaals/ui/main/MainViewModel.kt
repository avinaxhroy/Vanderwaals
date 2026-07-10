package me.avinas.vanderwaals.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import me.avinas.vanderwaals.core.MediaSaver
import me.avinas.vanderwaals.data.dao.WallpaperHistoryDao
import me.avinas.vanderwaals.data.entity.WallpaperHistory
import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import me.avinas.vanderwaals.data.entity.UserPreferences
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
    private val mediaSaver: MediaSaver,
    private val application: android.app.Application,
    private val nextWallpaperCacheManager: me.avinas.vanderwaals.domain.NextWallpaperCacheManager,
    private val settingsDataStore: me.avinas.vanderwaals.data.datastore.SettingsDataStore,
    private val preferenceRepository: me.avinas.vanderwaals.data.repository.PreferenceRepository
) : ViewModel() {

    /**
     * UI State for the Main Screen.
     */
    sealed interface MainUiState {
        data object Loading : MainUiState
        data class Success(val wallpaper: WallpaperMetadata?) : MainUiState
    }

    /**
     * Granular loading state for wallpaper changes.
     */
    enum class KoalaLoadingState {
        IDLE,
        THINKING, // Initial state when button is pressed
        FINDING,  // Algorithm is searching/calculating
        APPLYING  // Downloading and setting bitmap
    }

    /**
     * Current wallpaper state.
     * Emits Loading initially, then Success with wallpaper or null.
     * Uses SharingStarted.Lazily to keep state stable across navigation.
     */
    val currentWallpaper: StateFlow<MainUiState> = historyDao.getActiveWallpaperFlow()
        // Load wallpapers (summaries only for UI performance)
        .combine(wallpaperRepository.getAllWallpaperSummaries().distinctUntilChanged()) { activeHistory: WallpaperHistory?, wallpapers: List<WallpaperMetadata> ->
            if (activeHistory != null) {
                // Wait for metadata to load before emitting Success
                if (wallpapers.isEmpty()) {
                    MainUiState.Loading
                } else {
                    val wallpaper = wallpapers.find { it.id == activeHistory.wallpaperId }
                    // If wallpaper is still null but list is not empty, it might be deleted or missing.
                    // In that case, we can emit Success(null) or keep Loading. 
                    // For now, let's treat it as Success(null) so the UI shows "No wallpaper set" 
                    // if the specific wallpaper is truly missing from the catalog.
                    MainUiState.Success(wallpaper)
                }
            } else {
                MainUiState.Success(null)
            }
        }
        .stateIn(
            scope = viewModelScope,
            // Lazily keeps the last value cached and avoids re-emission on navigation.
            started = SharingStarted.Lazily,
            initialValue = MainUiState.Loading
        )

    /**
     * Whether the bottom sheet overlay is visible.
     * Starts as false, user taps screen to toggle.
     */
    private val _showOverlay = MutableStateFlow(false)
    val showOverlay: StateFlow<Boolean> = _showOverlay.asStateFlow()

    /**
     * Loading state for wallpaper change operations.
     * Exposes granular state (Thinking -> Finding -> Applying).
     */
    private val _loadingState = MutableStateFlow(KoalaLoadingState.IDLE)
    val loadingState: StateFlow<KoalaLoadingState> = _loadingState.asStateFlow()

    /**
     * Error message state for displaying errors via Snackbar.
     */
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    /**
     * Success message state for displaying positive feedback via Snackbar.
     */
    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

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
     * Embedding migration dialog state.
     * Shows when user has legacy 576D preferences that need to be migrated to 1280D.
     */
    private val _showEmbeddingMigrationDialog = MutableStateFlow(false)
    val showEmbeddingMigrationDialog: StateFlow<Boolean> = _showEmbeddingMigrationDialog.asStateFlow()
    
    /**
     * Number of liked wallpapers to show in migration dialog.
     */
    private val _totalLikes = MutableStateFlow(0)
    val totalLikes: StateFlow<Int> = _totalLikes.asStateFlow()

    /**
     * Clears the error message after it's been shown.
     */
    fun clearErrorMessage() {
        _errorMessage.value = null
    }
    
    /**
     * Clears the success message after it's been shown.
     */
    fun clearSuccessMessage() {
        _successMessage.value = null
    }
    
    /**
     * Check for embedding migration needed and show dialog if so.
     * Called from init and can be called from UI to refresh.
     */
    fun checkEmbeddingMigration() {
        viewModelScope.launch {
            try {
                val prefs = preferenceRepository.getUserPreferencesOnce()
                val hasPreferences = prefs != null

                updateEmbeddingDimensionFromPreferences(prefs)
                
                // Get liked count for dialog
                _totalLikes.value = prefs?.likedWallpaperIds?.size ?: 0
                
                val migrationNeeded = settingsDataStore.checkEmbeddingMigrationNeeded(hasPreferences)
                
                if (migrationNeeded) {
                    android.util.Log.i("MainViewModel", "Embedding migration needed - showing dialog")
                    _showEmbeddingMigrationDialog.value = true
                } else {
                    android.util.Log.d("MainViewModel", "No embedding migration needed")
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error checking embedding migration", e)
            }
        }
    }

    private suspend fun updateEmbeddingDimensionFromPreferences(preferences: UserPreferences?) {
        if (preferences == null) {
            return
        }

        val dimension = listOf(
            preferences.preferenceVector,
            preferences.originalEmbedding,
            preferences.momentumVector
        ).firstOrNull { it.isNotEmpty() }?.size ?: return

        when (dimension) {
            me.avinas.vanderwaals.data.datastore.SettingsDataStore.EMBEDDING_DIM_CURRENT -> {
                settingsDataStore.updateEmbeddingDimension(
                    me.avinas.vanderwaals.data.datastore.SettingsDataStore.EMBEDDING_DIM_CURRENT
                )
            }
            me.avinas.vanderwaals.data.datastore.SettingsDataStore.EMBEDDING_DIM_LEGACY -> {
                settingsDataStore.updateEmbeddingDimension(
                    me.avinas.vanderwaals.data.datastore.SettingsDataStore.EMBEDDING_DIM_LEGACY
                )
            }
        }
    }
    
    /**
     * Called when user taps "Re-personalize Now" on the embedding migration dialog.
     * Navigates to the onboarding flow to re-personalize their preferences.
     */
    fun onRePersonalize(navigateToOnboarding: () -> Unit) {
        viewModelScope.launch {
            // Reset preferences for embedding migration (keeps liked/disliked IDs)
            preferenceRepository.resetForEmbeddingMigration(keepMode = true)
            
            // Clear migration flags and set to current dimension
            settingsDataStore.clearEmbeddingMigrationFlags()
            
            _showEmbeddingMigrationDialog.value = false
            
            // Navigate to onboarding
            navigateToOnboarding()
        }
    }
    
    /**
     * Called when user taps "Use Auto Mode" on embedding migration dialog.
     * Resets preferences and continues with auto mode, then triggers a wallpaper change.
     */
    fun onAutoMode() {
        viewModelScope.launch {
            // Reset preferences and switch to auto mode
            preferenceRepository.resetForEmbeddingMigration(keepMode = false)
            
            // Clear migration flags and set to current dimension
            settingsDataStore.clearEmbeddingMigrationFlags()
            
            _showEmbeddingMigrationDialog.value = false
            
            android.util.Log.i("MainViewModel", "User chose auto mode for embedding migration")
            
            // Show success feedback to user
            _successMessage.value = "Auto mode enabled! Finding your first wallpaper..."
            
            // Trigger a wallpaper change to demonstrate the new mode
            changeNow()
        }
    }
    
    /**
     * Called when user taps "Remind Me Later" on embedding migration dialog.
     * Dismisses dialog for this session but will show again next launch.
     */
    fun onRemindLater() {
        _showEmbeddingMigrationDialog.value = false
        android.util.Log.d("MainViewModel", "User dismissed embedding migration dialog - will show again next launch")
    }
    
    /**
     * Called when user taps "Don't Show Again" on embedding migration dialog.
     * Permanently dismisses the dialog.
     */
    fun onDontShowAgain() {
        viewModelScope.launch {
            settingsDataStore.setEmbeddingMigrationDismissed(true)
            _showEmbeddingMigrationDialog.value = false
            android.util.Log.i("MainViewModel", "User permanently dismissed embedding migration dialog")
        }
    }

    /**
     * Checks if live wallpaper is blocking after a wallpaper change failure.
     * 
     * This uses the post-failure detection approach which is more reliable:
     * - Only called when a wallpaper change actually fails
     * - Avoids false positives from manufacturer-specific system services
     * - Works across all devices without needing brand-specific exclusions
     */
    fun checkForLiveWallpaper() {
        try {
            // Use post-failure detection - more reliable across all devices
            val (isBlocking, serviceName) = me.avinas.vanderwaals.core.LiveWallpaperDetector.detectBlockingAfterFailure(application)
            
            if (isBlocking) {
                // Get package name for settings navigation
                val packageName = me.avinas.vanderwaals.core.LiveWallpaperDetector.getLiveWallpaperPackageName(application)
                
                _liveWallpaperInfo.value = (serviceName ?: "Live Wallpaper") to packageName
                _showLiveWallpaperDialog.value = true
                
                android.util.Log.d("MainViewModel", "Live wallpaper blocking detected: $serviceName ($packageName)")
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
        // Note: We don't check for live wallpaper on init anymore.
        // Instead, we detect it only when a wallpaper change actually fails.
        // This avoids false positives from manufacturer-specific system services.
        
        // Check for embedding migration (legacy 576D -> 1280D)
        checkEmbeddingMigration()
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
     * Worker dynamically loads Apply To setting from DataStore.
     */
    fun changeNow() {
        viewModelScope.launch {
            try {
                // Initial state: Koala is thinking...
                _loadingState.value = KoalaLoadingState.THINKING

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

                // Observe work completion and progress
                workManager.getWorkInfoByIdFlow(workRequest.id)
                    .collect { workInfo ->
                        if (workInfo == null) return@collect
                        
                        // Update state based on progress
                        val progressState = workInfo.progress.getString(WallpaperChangeWorker.KEY_PROGRESS_STATE)
                        if (progressState != null) {
                            when (progressState) {
                                WallpaperChangeWorker.PROGRESS_FINDING -> _loadingState.value = KoalaLoadingState.FINDING
                                WallpaperChangeWorker.PROGRESS_APPLYING -> _loadingState.value = KoalaLoadingState.APPLYING
                            }
                        }

                        when {
                            workInfo.state.isFinished -> {
                                _loadingState.value = KoalaLoadingState.IDLE
                                
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
                _loadingState.value = KoalaLoadingState.IDLE
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
    fun likeCurrentWallpaper(onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val state = currentWallpaper.value
                val wallpaper = (state as? MainUiState.Success)?.wallpaper
                if (wallpaper == null) {
                    onError("No wallpaper to like")
                    return@launch
                }

                // PERFORMANCE FIX: Use indexed lookup instead of loading entire catalog
                // This avoids O(n) scan of 5000+ wallpapers for a single ID lookup
                val fullWallpaper = wallpaperRepository.getWallpaperById(wallpaper.id)
                
                if (fullWallpaper == null) {
                    onError("Wallpaper not found in catalog")
                    return@launch
                }
                
                if (fullWallpaper.embedding.isEmpty()) {
                    onError("Wallpaper embedding not available")
                    return@launch
                }

                val result = updatePreferencesUseCase(fullWallpaper, FeedbackType.LIKE)

                result.fold(
                    onSuccess = {
                        val activeHistory = historyDao.getActiveWallpaper()
                        if (activeHistory != null) {
                            val context = me.avinas.vanderwaals.data.entity.FeedbackContext.fromCurrentState(
                                application
                            )
                            wallpaperRepository.updateHistoryWithContext(
                                activeHistory.id,
                                FeedbackType.LIKE,
                                context
                            )
                        }
                        
                        // Invalidate pre-computed cache since preferences changed
                        nextWallpaperCacheManager.invalidateCache("like_feedback")
                        
                        android.util.Log.d("MainViewModel", "Liked wallpaper: ${wallpaper.id}, category: ${wallpaper.category}")
                        onSuccess()
                    },
                    onFailure = { error ->
                        android.util.Log.e("MainViewModel", "Failed to like wallpaper", error)
                        onError(error.message ?: "Failed to record feedback")
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Exception in likeCurrentWallpaper", e)
                onError(e.message ?: "Error recording like")
            }
        }
    }

    /**
     * Records dislike feedback for the current wallpaper.
     * 
     * Updates the preference vector and history with negative feedback.
     * This makes the algorithm avoid wallpapers similar to this one.
     */
    fun dislikeCurrentWallpaper(onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val state = currentWallpaper.value
                val wallpaper = (state as? MainUiState.Success)?.wallpaper
                if (wallpaper == null) {
                    onError("No wallpaper to dislike")
                    return@launch
                }

                // PERFORMANCE FIX: Use indexed lookup instead of loading entire catalog
                // This avoids O(n) scan of 5000+ wallpapers for a single ID lookup
                val fullWallpaper = wallpaperRepository.getWallpaperById(wallpaper.id)
                
                if (fullWallpaper == null) {
                    onError("Wallpaper not found in catalog")
                    return@launch
                }
                
                if (fullWallpaper.embedding.isEmpty()) {
                    onError("Wallpaper embedding not available")
                    return@launch
                }

                val result = updatePreferencesUseCase(fullWallpaper, FeedbackType.DISLIKE)

                result.fold(
                    onSuccess = {
                        val activeHistory = historyDao.getActiveWallpaper()
                        if (activeHistory != null) {
                            val context = me.avinas.vanderwaals.data.entity.FeedbackContext.fromCurrentState(
                                application
                            )
                            wallpaperRepository.updateHistoryWithContext(
                                activeHistory.id,
                                FeedbackType.DISLIKE,
                                context
                            )
                        }
                        android.util.Log.d("MainViewModel", "Disliked wallpaper: ${wallpaper.id}")
                        
                        // IMPROVED: Use diversity-focused selection after dislike
                        // This ensures the next wallpaper is from a different category
                        // and visually distinct from the one the user disliked
                        changeAfterDislike(
                            dislikedWallpaperId = fullWallpaper.id,
                            dislikedCategory = fullWallpaper.category,
                            dislikedEmbedding = fullWallpaper.embedding
                        )
                        
                        onSuccess()
                    },
                    onFailure = { error ->
                        android.util.Log.e("MainViewModel", "Failed to dislike wallpaper", error)
                        onError(error.message ?: "Failed to record feedback")
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Exception in dislikeCurrentWallpaper", e)
                onError(e.message ?: "Error recording dislike")
            }
        }
    }
    
    /**
     * Changes wallpaper after a dislike using diversity-focused selection.
     * Prioritizes wallpapers from different categories and visually dissimilar ones.
     */
    private fun changeAfterDislike(
        dislikedWallpaperId: String,
        dislikedCategory: String,
        dislikedEmbedding: FloatArray
    ) {
        viewModelScope.launch {
            try {
                _loadingState.value = KoalaLoadingState.THINKING
                android.util.Log.d("MainViewModel", "=== CHANGE AFTER DISLIKE ===")
                android.util.Log.d("MainViewModel", "Disliked: $dislikedWallpaperId (category: $dislikedCategory)")
                
                // Use diversity-focused selection
                val result = nextWallpaperCacheManager.getNextWallpaperAfterDislike(
                    dislikedWallpaperId = dislikedWallpaperId,
                    dislikedCategory = dislikedCategory,
                    dislikedEmbedding = dislikedEmbedding
                )
                
                result.fold(
                    onSuccess = { selectedWallpaper ->
                        android.util.Log.d("MainViewModel", 
                            "Selected diverse wallpaper: ${selectedWallpaper.id} (category: ${selectedWallpaper.category})")
                        
                        // Create and enqueue wallpaper change work with selected wallpaper
                        val workRequest = OneTimeWorkRequestBuilder<WallpaperChangeWorker>()
                            .setInputData(androidx.work.workDataOf(
                                WallpaperChangeWorker.KEY_MODE to WallpaperChangeWorker.MODE_VANDERWAALS,
                                WallpaperChangeWorker.KEY_IS_MANUAL_CHANGE to true,
                                WallpaperChangeWorker.KEY_SELECTED_WALLPAPER_ID to selectedWallpaper.id
                            ))
                            .addTag("dislike_change")
                            .build()

                        workManager.enqueue(workRequest)

                        // Observe work completion
                        workManager.getWorkInfoByIdFlow(workRequest.id)
                            .collect { workInfo ->
                                if (workInfo == null) return@collect
                                
                                // Update state based on progress
                                val progressState = workInfo.progress.getString(WallpaperChangeWorker.KEY_PROGRESS_STATE)
                                if (progressState != null) {
                                    when (progressState) {
                                        WallpaperChangeWorker.PROGRESS_FINDING -> _loadingState.value = KoalaLoadingState.FINDING
                                        WallpaperChangeWorker.PROGRESS_APPLYING -> _loadingState.value = KoalaLoadingState.APPLYING
                                    }
                                }
                                
                                when {
                                    workInfo.state.isFinished -> {
                                        _loadingState.value = KoalaLoadingState.IDLE
                                        
                                        if (workInfo.state == androidx.work.WorkInfo.State.FAILED) {
                                            checkForLiveWallpaper()
                                        }
                                        
                                        if (workInfo.state == androidx.work.WorkInfo.State.SUCCEEDED) {
                                            _showOverlay.value = false
                                        }
                                    }
                                }
                            }
                    },
                    onFailure = { error ->
                        android.util.Log.e("MainViewModel", "Diversity selection failed, falling back to changeNow()", error)
                        // Fallback to normal change (which sets state itself)
                        changeNow()
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Exception in changeAfterDislike", e)
                _loadingState.value = KoalaLoadingState.IDLE
                // Fallback to normal change
                changeNow()
            }
        }
    }

    /**
     * Downloads the current wallpaper to the gallery.
     * 
     * This action has the HIGHEST learning weight because:
     * - User values the wallpaper enough to save it for future use
     * - It's a stronger signal than a simple "like"
     * - Learning rate is 1.5x compared to regular like
     * 
     * Updates the preference vector and saves the image to device gallery.
     */
    fun downloadCurrentWallpaper(onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val state = currentWallpaper.value
                val wallpaper = (state as? MainUiState.Success)?.wallpaper
                if (wallpaper == null) {
                    onError("No wallpaper to download")
                    return@launch
                }

                // PERFORMANCE FIX: Use indexed lookup instead of loading entire catalog
                val fullWallpaper = wallpaperRepository.getWallpaperById(wallpaper.id)
                
                if (fullWallpaper == null) {
                    onError("Wallpaper not found in catalog")
                    return@launch
                }

                // Download/Get from cache
                val downloadResult = wallpaperRepository.downloadWallpaper(fullWallpaper)
                
                downloadResult.onSuccess { file ->
                    // Validate file before saving
                    if (!file.exists() || file.length() <= 0) {
                        onError("Download failed: Empty file")
                        return@onSuccess
                    }

                    // Save to gallery
                    val saveResult = mediaSaver.saveImageToGallery(file, fullWallpaper.id)
                    if (saveResult.isSuccess) {
                        // IMPORTANT: Update preferences with DOWNLOAD feedback (highest weight)
                        if (fullWallpaper.embedding.isNotEmpty()) {
                            val preferenceResult = updatePreferencesUseCase(fullWallpaper, FeedbackType.DOWNLOAD)
                            preferenceResult.fold(
                                onSuccess = {
                                    // Also update history context
                                    val activeHistory = historyDao.getActiveWallpaper()
                                    if (activeHistory != null) {
                                        val context = me.avinas.vanderwaals.data.entity.FeedbackContext.fromCurrentState(
                                            application
                                        )
                                        wallpaperRepository.updateHistoryWithContext(
                                            activeHistory.id,
                                            FeedbackType.DOWNLOAD,
                                            context
                                        )
                                    }
                                    android.util.Log.d("MainViewModel", "Downloaded wallpaper: ${fullWallpaper.id} - strongest learning signal applied")
                                },
                                onFailure = { error ->
                                    android.util.Log.e("MainViewModel", "Failed to update preferences for download", error)
                                }
                            )
                        }
                        onSuccess()
                    } else {
                        onError("Failed to save to gallery")
                    }
                }
                downloadResult.onFailure {
                    onError("Download failed: ${it.message}")
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Exception in downloadCurrentWallpaper", e)
                onError(e.message ?: "Error downloading wallpaper")
            }
        }
    }
}
