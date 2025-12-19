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
import me.avinas.vanderwaals.BuildConfig
import me.avinas.vanderwaals.data.datastore.SettingsDataStore
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
 * - Manifest migration state for version upgrades
 */
@HiltViewModel
class InitializationViewModel @Inject constructor(
    private val manifestRepository: ManifestRepository,
    private val workManager: WorkManager,
    private val downloadProgressManager: me.avinas.vanderwaals.network.DownloadProgressManager,
    private val settingsDataStore: SettingsDataStore
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
    
    // Migration state for users upgrading from older versions
    private val _showMigrationDialog = MutableStateFlow(false)
    val showMigrationDialog: StateFlow<Boolean> = _showMigrationDialog.asStateFlow()
    
    private val _migrationInProgress = MutableStateFlow(false)
    val migrationInProgress: StateFlow<Boolean> = _migrationInProgress.asStateFlow()
    
    private val _migrationProgress = MutableStateFlow<Float?>(null)
    val migrationProgress: StateFlow<Float?> = _migrationProgress.asStateFlow()
    
    private val _migrationMessage = MutableStateFlow<String?>(null)
    val migrationMessage: StateFlow<String?> = _migrationMessage.asStateFlow()
    
    init {
        checkMigrationNeeded()
        checkInitialization()
        observeDownloadProgress()
    }
    
    /**
     * Checks if a manifest migration is needed after app upgrade.
     * Shows migration dialog for users upgrading from v3.x to v4.0+
     */
    private fun checkMigrationNeeded() {
        viewModelScope.launch {
            try {
                val currentVersionCode = BuildConfig.VERSION_CODE
                val migrationNeeded = settingsDataStore.checkAndSetMigrationNeeded(currentVersionCode)
                
                if (migrationNeeded) {
                    Log.i(TAG, "Manifest migration needed - user upgraded from old version")
                    _showMigrationDialog.value = true
                } else {
                    Log.d(TAG, "No migration needed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking migration status", e)
            }
        }
    }
    
    /**
     * Called when user taps "Update Now" on migration dialog.
     * Triggers manifest re-sync with force update.
     */
    fun startMigration() {
        viewModelScope.launch {
            _migrationInProgress.value = true
            _migrationProgress.value = 0.0f
            _migrationMessage.value = "Connecting to server..."
            
            try {
                manifestRepository.syncManifest(
                    onProgress = { message, progress, count ->
                        _migrationMessage.value = message
                        _migrationProgress.value = progress
                    },
                    forceUpdate = true  // Force re-download even if not modified
                ).fold(
                    onSuccess = { count ->
                        Log.i(TAG, "Migration completed successfully: $count wallpapers")
                        _migrationMessage.value = "Updated $count wallpapers!"
                        _migrationProgress.value = 1.0f
                        
                        // Update manifest version to v2
                        settingsDataStore.updateManifestVersion(2)
                        settingsDataStore.clearMigrationFlags()
                        
                        // Close dialog after brief delay
                        kotlinx.coroutines.delay(1000L)
                        _showMigrationDialog.value = false
                        _migrationInProgress.value = false
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Migration failed", error)
                        _migrationMessage.value = "Update failed: ${error.message}"
                        _migrationProgress.value = null
                        _migrationInProgress.value = false
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Migration error", e)
                _migrationMessage.value = "Error: ${e.message}"
                _migrationProgress.value = null
                _migrationInProgress.value = false
            }
        }
    }
    
    /**
     * Called when user taps "Later" on migration dialog.
     * Dismisses dialog but keeps migration pending for next launch.
     */
    fun dismissMigrationDialog() {
        viewModelScope.launch {
            _showMigrationDialog.value = false
            // Don't mark as dismissed permanently - will show again next launch
            // User can manually sync from Settings
        }
    }
    
    /**
     * Called when user taps "Don't Show Again".
     * Permanently dismisses the migration dialog.
     */
    fun dismissMigrationPermanently() {
        viewModelScope.launch {
            settingsDataStore.setManifestMigrationDismissed(true)
            _showMigrationDialog.value = false
        }
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
                _syncFailed.value = false
                
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
                        Log.w(TAG, "Initialization timeout or failure - database still empty")
                        _syncFailed.value = true
                        _loadingMessage.value = "Download Failed"
                        _loadingSubMessage.value = "Network timeout. Please check your internet connection and retry."
                        _loadingProgress.value = null
                        // Do NOT set isInitialized = true here
                        return@launch
                    }
                }
                
                // Mark as initialized ONLY if successful
                if (!_syncFailed.value) {
                    _loadingMessage.value = "All Set!"
                    _loadingSubMessage.value = "Opening your wallpaper collection..."
                    _loadingProgress.value = 1.0f
                    kotlinx.coroutines.delay(500L) // Brief delay for visual feedback
                    _isInitialized.value = true
                    Log.d(TAG, "App initialization complete")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during initialization", e)
                _syncFailed.value = true
                _loadingMessage.value = "Error"
                _loadingSubMessage.value = "Please check your connection and retry."
                // Do NOT set isInitialized = true here
            }
        }
    }
    
    /**
     * Retry initialization (called from UI retry button).
     */
    fun retryInitialization() {
        _isInitialized.value = false
        _syncFailed.value = false
        
        // Trigger sync again via WorkManager
        val workRequest = androidx.work.OneTimeWorkRequestBuilder<me.avinas.vanderwaals.worker.CatalogSyncWorker>()
            .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
            
        workManager.enqueueUniqueWork(
            "catalog_sync_initial",
            androidx.work.ExistingWorkPolicy.REPLACE,
            workRequest
        )
        
        checkInitialization()
    }
}
