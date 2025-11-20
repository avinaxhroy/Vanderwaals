package me.avinas.vanderwaals.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.avinas.vanderwaals.data.repository.ManifestRepository
import javax.inject.Inject

/**
 * ViewModel for managing app initialization state.
 * 
 * Tracks:
 * - Database initialization (wallpaper catalog synced)
 * - Loading screen visibility
 * - Status messages for user feedback
 * - WorkManager sync progress
 */
@HiltViewModel
class InitializationViewModel @Inject constructor(
    private val manifestRepository: ManifestRepository,
    private val workManager: WorkManager,
    private val downloadProgressManager: me.avinas.vanderwaals.network.DownloadProgressManager
) : ViewModel() {
    
    companion object {
        private const val TAG = "InitializationViewModel"
    }
    
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()
    
    private val _loadingMessage = MutableStateFlow("Loading Wallpapers...")
    val loadingMessage: StateFlow<String> = _loadingMessage.asStateFlow()
    
    private val _loadingSubMessage = MutableStateFlow("Please wait while we prepare your wallpapers")
    val loadingSubMessage: StateFlow<String> = _loadingSubMessage.asStateFlow()
    
    private val _loadingProgress = MutableStateFlow<Float?>(null)
    val loadingProgress: StateFlow<Float?> = _loadingProgress.asStateFlow()
    
    private val _syncFailed = MutableStateFlow(false)
    val syncFailed: StateFlow<Boolean> = _syncFailed.asStateFlow()
    
    init {
        checkInitialization()
        observeDownloadProgress()
    }
    
    /**
     * Observes real-time download progress from DownloadProgressManager.
     * Updates UI with actual bytes downloaded and progress percentage.
     */
    private fun observeDownloadProgress() {
        viewModelScope.launch {
            downloadProgressManager.progressState.collect { progress ->
                if (progress.bytesDownloaded > 0) {
                    // Update UI with real download progress
                    val formattedProgress = progress.formatProgress()
                    _loadingSubMessage.value = "Downloading: $formattedProgress"
                    _loadingProgress.value = progress.progress
                    
                    if (progress.isDone) {
                        _loadingMessage.value = "Processing Wallpapers..."
                    } else {
                        _loadingMessage.value = "Downloading Catalog"
                    }
                }
            }
        }
    }
    
    /**
     * Checks if the app is fully initialized (database has wallpapers).
     * Updates state accordingly.
     * 
     * Improved logic:
     * - Skips loading screen if database already has wallpapers
     * - Monitors actual WorkManager sync job for first launch
     * - Shows "Downloading wallpapers" instead of "syncing catalog"
     * - Provides real progress based on WorkManager state
     * - Waits for actual download to complete
     * - Shows clear error state if download failed
     */
    private fun checkInitialization() {
        viewModelScope.launch {
            try {
                // Check if database is initialized FIRST
                val isDbInitialized = manifestRepository.isDatabaseInitialized()
                
                // If database already has wallpapers, skip loading screen entirely
                if (isDbInitialized) {
                    Log.d(TAG, "Database already initialized, skipping loading screen")
                    _isInitialized.value = true
                    return@launch
                }
                
                // Only show loading for first-time users
                _loadingMessage.value = "Preparing Wallpapers"
                _loadingSubMessage.value = "Setting up your wallpaper collection..."
                _loadingProgress.value = 0.0f
                
                if (!isDbInitialized) {
                    Log.d(TAG, "Database not initialized, waiting for download...")
                    
                    // Initial state - download progress will be updated by observeDownloadProgress()
                    _loadingMessage.value = "Downloading Catalog"
                    _loadingSubMessage.value = "Preparing download..."
                    _loadingProgress.value = 0.0f
                    
                    // Monitor WorkManager for completion only (progress is tracked by DownloadProgressManager)
                    val startTime = System.currentTimeMillis()
                    val timeout = 300000L // 5 minute timeout (same as READ_TIMEOUT)
                    var downloadComplete = false
                    
                    // Wait for download to complete
                    while (!downloadComplete && (System.currentTimeMillis() - startTime) < timeout) {
                        val currentWorkInfos = workManager.getWorkInfosForUniqueWork("catalog_sync_initial").get()
                        
                        if (currentWorkInfos.isNotEmpty()) {
                            val workInfo = currentWorkInfos[0]
                            
                            when (workInfo.state) {
                                WorkInfo.State.SUCCEEDED -> {
                                    val finalCount = workInfo.outputData.getInt("synced_count", 0)
                                    _loadingProgress.value = 1.0f
                                    _loadingMessage.value = "Download Complete!"
                                    _loadingSubMessage.value = "Downloaded $finalCount wallpapers successfully"
                                    kotlinx.coroutines.delay(500L)
                                    downloadComplete = true
                                }
                                WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                                    Log.w(TAG, "WorkManager sync failed or cancelled")
                                    _syncFailed.value = true
                                    downloadComplete = true
                                }
                                else -> {
                                    // RUNNING, ENQUEUED, or BLOCKED - continue waiting
                                    // Progress is updated by observeDownloadProgress()
                                }
                            }
                        }
                        
                        // Check database periodically
                        if (manifestRepository.isDatabaseInitialized()) {
                            downloadComplete = true
                        }
                        
                        if (!downloadComplete) {
                            kotlinx.coroutines.delay(500L) // Check every 500ms
                        }
                    }
                    
                    // Final check
                    if (!manifestRepository.isDatabaseInitialized()) {
                        Log.w(TAG, "Initialization timeout - database still empty after 5 minutes")
                        _syncFailed.value = true
                        _loadingMessage.value = "Download Failed"
                        _loadingSubMessage.value = "Network timeout. Please check your internet connection and retry from settings."
                        _loadingProgress.value = null
                        kotlinx.coroutines.delay(3000L) // Show error for 3 seconds
                    }
                }
                
                // Mark as initialized (even if download failed - allow app usage)
                if (!_syncFailed.value) {
                    _loadingMessage.value = "All Set!"
                    _loadingSubMessage.value = "Opening your wallpaper collection..."
                    _loadingProgress.value = 1.0f
                }
                kotlinx.coroutines.delay(500L) // Brief delay for visual feedback
                
                _isInitialized.value = true
                Log.d(TAG, "App initialization complete")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during initialization", e)
                // Still mark as initialized to avoid indefinite loading
                _syncFailed.value = true
                _loadingMessage.value = "Error"
                _loadingSubMessage.value = "Please check your connection and retry from settings"
                kotlinx.coroutines.delay(2000L)
                _isInitialized.value = true
            }
        }
    }
    
    /**
     * Retry initialization (called from UI retry button).
     */
    fun retryInitialization() {
        _isInitialized.value = false
        _syncFailed.value = false
        checkInitialization()
    }
}
