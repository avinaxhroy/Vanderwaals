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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.avinas.vanderwaals.BuildConfig
import me.avinas.vanderwaals.data.datastore.SettingsDataStore
import me.avinas.vanderwaals.data.repository.ManifestRepository
import javax.inject.Inject

@HiltViewModel
class InitializationViewModel @Inject constructor(
    private val manifestRepository: ManifestRepository,
    private val bingManifestRepository: me.avinas.vanderwaals.data.repository.BingManifestRepository,
    private val vanderwaalsCollectionRepository: me.avinas.vanderwaals.data.repository.VanderwaalsCollectionRepository,
    private val workManager: WorkManager,
    private val downloadProgressManager: me.avinas.vanderwaals.network.DownloadProgressManager,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {
    
    companion object {
        private const val TAG = "InitializationViewModel"
    }
    
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()
    
    private val _loadingMessage = MutableStateFlow("Loading Wallpapers…")
    val loadingMessage: StateFlow<String> = _loadingMessage.asStateFlow()
    
    private val _loadingSubMessage = MutableStateFlow("Preparing your wallpaper library…")
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
                val databaseHasWallpapers = manifestRepository.isDatabaseInitialized()
                
                val migrationNeeded = settingsDataStore.checkAndSetMigrationNeeded(
                    currentVersionCode,
                    databaseHasWallpapers
                )
                
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
     * Triggers manifest re-sync with force update for both GitHub and Bing sources.
     */
    fun startMigration() {
        viewModelScope.launch {
            _migrationInProgress.value = true
            _migrationProgress.value = 0.0f
            _migrationMessage.value = "Connecting to server..."
            
            try {
                val settings = settingsDataStore.settings.first()
                val bingEnabled = settings.bingEnabled
                val bingManifestType = settings.bingManifestType
                val vanderwaalsCollectionEnabled = settings.vanderwaalsCollectionEnabled
                val vanderwaalsCollectionManifestType = settings.vanderwaalsCollectionManifestType

                var totalWallpapers = 0
                var githubSuccess = false
                var bingSuccess = false
                var vanderwaalsSuccess = false

                val activeSources = listOfNotNull(
                    "github".takeIf { settings.githubEnabled },
                    "bing".takeIf { bingEnabled },
                    "vanderwaals".takeIf { vanderwaalsCollectionEnabled }
                )
                val slice = 1f / activeSources.size
                fun scaledProgress(source: String, subProgress: Float): Float {
                    val offset = activeSources.indexOf(source) * slice
                    return offset + (subProgress * slice)
                }

                if (settings.githubEnabled) {
                    _migrationMessage.value = "Updating Community wallpapers..."
                    manifestRepository.syncManifest(
                        onProgress = { message, progress, count ->
                            _migrationMessage.value = "Community: $message"
                            _migrationProgress.value = scaledProgress("github", progress)
                        },
                        forceUpdate = true
                    ).fold(
                        onSuccess = { count ->
                            Log.i(TAG, "GitHub manifest migration: $count wallpapers")
                            totalWallpapers += count
                            githubSuccess = true
                        },
                        onFailure = { error ->
                            Log.e(TAG, "GitHub manifest migration failed", error)
                            // Continue with other sources even if GitHub fails
                        }
                    )
                }

                if (bingEnabled) {
                    _migrationMessage.value = "Updating Bing wallpapers..."
                    bingManifestRepository.syncBingManifest(
                        manifestType = bingManifestType,
                        onProgress = { message, progress, count ->
                            _migrationMessage.value = "Bing: $message"
                            _migrationProgress.value = scaledProgress("bing", progress)
                        },
                        forceUpdate = true
                    ).fold(
                        onSuccess = { count ->
                            Log.i(TAG, "Bing manifest migration: $count wallpapers")
                            totalWallpapers += count
                            bingSuccess = true
                        },
                        onFailure = { error ->
                            Log.e(TAG, "Bing manifest migration failed", error)
                        }
                    )
                }

                if (vanderwaalsCollectionEnabled) {
                    _migrationMessage.value = "Updating Vanderwaals Collection wallpapers..."
                    vanderwaalsCollectionRepository.syncVanderwaalsCollectionManifest(
                        manifestType = vanderwaalsCollectionManifestType,
                        onProgress = { message, progress, count ->
                            _migrationMessage.value = "Vanderwaals: $message"
                            _migrationProgress.value = scaledProgress("vanderwaals", progress)
                        },
                        forceUpdate = true
                    ).fold(
                        onSuccess = { count ->
                            Log.i(TAG, "Vanderwaals Collection migration: $count wallpapers")
                            totalWallpapers += count
                            vanderwaalsSuccess = true
                        },
                        onFailure = { error ->
                            Log.e(TAG, "Vanderwaals Collection migration failed", error)
                        }
                    )
                }

                if (githubSuccess || bingSuccess || vanderwaalsSuccess) {
                    Log.i(TAG, "Migration completed: $totalWallpapers total wallpapers")
                    _migrationMessage.value = "Updated $totalWallpapers wallpapers!"
                    _migrationProgress.value = 1.0f
                    
                    // Update manifest version to v3 (MobileNetV4 1280D)
                    settingsDataStore.updateManifestVersion(3)
                    settingsDataStore.clearMigrationFlags()
                    
                    kotlinx.coroutines.delay(1000L)
                    _showMigrationDialog.value = false
                    _migrationInProgress.value = false
                } else {
                    _migrationMessage.value = "Update failed. Please try again."
                    _migrationProgress.value = null
                    _migrationInProgress.value = false
                }
                
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
    
    fun dismissMigrationPermanently() {
        viewModelScope.launch {
            settingsDataStore.setManifestMigrationDismissed(true)
            _showMigrationDialog.value = false
        }
    }

    
    private fun observeDownloadProgress() {
        viewModelScope.launch {
            downloadProgressManager.progressState.collect { progress ->
                if (progress.bytesDownloaded > 0) {
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
    
    private fun checkInitialization() {
        viewModelScope.launch {
            try {
                val isDbInitialized = manifestRepository.isDatabaseInitialized()
                
                // If database already has wallpapers, skip loading screen entirely
                if (isDbInitialized) {
                    Log.d(TAG, "Database already initialized, skipping loading screen")
                    _isInitialized.value = true
                    return@launch
                }
                
                // Database not initialized (Fresh Install)
                // Skip auto-download effectively letting VanderwaalsNavGraph handle the Onboarding flow
                // which includes Source Selection and Initial Sync
                Log.d(TAG, "Database not initialized, proceeding to onboarding flow")
                _isInitialized.value = true
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during initialization", e)
                _syncFailed.value = true
                _loadingMessage.value = "Error"
                _loadingSubMessage.value = "Please check your connection and retry."
            }
        }
    }
    
    fun retryInitialization() {
        _isInitialized.value = false
        _syncFailed.value = false
        
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
