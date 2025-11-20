package me.avinas.vanderwaals.ui.onboarding

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.avinas.vanderwaals.data.repository.ManifestRepository
import javax.inject.Inject

/**
 * ViewModel for initial sync screen.
 * 
 * Manages:
 * - Automatic manifest download on first launch
 * - Progress tracking (0% → 100%)
 * - Wallpaper count as sync progresses
 * - Error handling with retry
 * 
 * StateFlow emissions:
 * - syncState: Current sync status
 * - wallpaperCount: Number of wallpapers synced so far
 * 
 * **Sync Process:**
 * 1. Check if database already populated
 * 2. If empty, download manifest from CDN
 * 3. Parse JSON and insert into database
 * 4. Update progress as wallpapers are processed
 * 5. Navigate to next screen on success
 * 
 * @param manifestRepository Repository for manifest operations
 */
@HiltViewModel
class InitialSyncViewModel @Inject constructor(
    private val manifestRepository: ManifestRepository,
    private val settingsDataStore: me.avinas.vanderwaals.data.datastore.SettingsDataStore
) : ViewModel() {
    
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()
    
    private val _wallpaperCount = MutableStateFlow(0)
    val wallpaperCount: StateFlow<Int> = _wallpaperCount.asStateFlow()
    
    companion object {
        private const val TAG = "InitialSyncViewModel"
    }
    
    /**
     * Starts the manifest sync process.
     * 
     * Can be called multiple times (e.g., on retry).
     * If database already populated, skips download and succeeds immediately.
     */
    fun startSync() {
        viewModelScope.launch {
            try {
                _syncState.value = SyncState.Loading("Checking database...", 0.0f)
                
                // Check if database already has wallpapers
                val isInitialized = manifestRepository.isDatabaseInitialized()
                
                if (isInitialized) {
                    Log.d(TAG, "Database already initialized, skipping sync")
                    val count = manifestRepository.getWallpaperCount()
                    _wallpaperCount.value = count
                    _syncState.value = SyncState.Success(count)
                    return@launch
                }
                
                Log.d(TAG, "Database empty, starting sync...")
                _syncState.value = SyncState.Loading("Connecting to server...", 0.1f)
                
                // Perform sync with real-time progress updates
                val result = manifestRepository.syncManifest { message, progress, count ->
                    Log.d(TAG, "Sync progress: $message ($progress) - $count wallpapers")
                    _syncState.value = SyncState.Loading(message, progress)
                    _wallpaperCount.value = count
                }
                
                result.fold(
                    onSuccess = { count ->
                        Log.d(TAG, "Sync successful: $count wallpapers")
                        _wallpaperCount.value = count
                        // Update last sync timestamp to fix "Never synced" issue
                        settingsDataStore.updateLastSyncTimestamp(System.currentTimeMillis())
                        _syncState.value = SyncState.Success(count)
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Sync failed: ${error.message}", error)
                        _syncState.value = SyncState.Error(
                            message = error.message ?: "Unknown error occurred"
                        )
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during sync", e)
                _syncState.value = SyncState.Error(
                    message = "Unexpected error: ${e.message}"
                )
            }
        }
    }
}

/**
 * Sync state for initial catalog download.
 */
sealed class SyncState {
    /**
     * Idle state, sync not started.
     */
    data object Idle : SyncState()
    
    /**
     * Sync in progress.
     * 
     * @param message Status message (e.g., "Downloading wallpapers...")
     * @param progress Progress from 0.0 to 1.0 (null if indeterminate)
     */
    data class Loading(
        val message: String,
        val progress: Float? = null
    ) : SyncState()
    
    /**
     * Sync completed successfully.
     * 
     * @param count Number of wallpapers downloaded
     */
    data class Success(val count: Int) : SyncState()
    
    /**
     * Sync failed.
     * 
     * @param message Error description
     */
    data class Error(val message: String) : SyncState()
}
