package me.avinas.vanderwaals.ui.onboarding

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    private val bingManifestRepository: me.avinas.vanderwaals.data.repository.BingManifestRepository,
    private val vanderwaalsCollectionRepository: me.avinas.vanderwaals.data.repository.VanderwaalsCollectionRepository,
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
                // Get enabled sources
                val settings = settingsDataStore.settings.first()
                val githubEnabled = settings.githubEnabled
                val bingEnabled = settings.bingEnabled
                val bingManifestType = settings.bingManifestType // "lite" or "full"
                val vanderwaalsCollectionEnabled = settings.vanderwaalsCollectionEnabled
                val vanderwaalsCollectionManifestType = settings.vanderwaalsCollectionManifestType

                // If nothing enabled (shouldn't happen due to UI validation), fail early
                if (!githubEnabled && !bingEnabled && !vanderwaalsCollectionEnabled) {
                    _syncState.value = SyncState.Error("No wallpaper sources selected")
                    return@launch
                }

                var totalCount = 0

                // Compute per-source progress slice so each enabled source fills its share
                val activeSources = listOfNotNull(
                    "github".takeIf { githubEnabled },
                    "bing".takeIf { bingEnabled },
                    "vanderwaals".takeIf { vanderwaalsCollectionEnabled }
                )
                val slice = 1f / activeSources.size
                val updateUnifiedProgress = { source: String, msg: String, subProgress: Float ->
                    val offset = activeSources.indexOf(source) * slice
                    val finalProgress = offset + (subProgress * slice)
                    _syncState.value = SyncState.Loading(msg, finalProgress)
                }

                // Phase 1: GitHub / Community Manifest
                if (githubEnabled) {
                    Log.d(TAG, "Starting GitHub sync...")
                    val result = manifestRepository.syncManifest(
                        onProgress = { message, progress, count ->
                            updateUnifiedProgress("github", "Community: $message", progress)
                            if (count > 0) _wallpaperCount.value = totalCount + count
                        }
                    )
                    
                    result.fold(
                        onSuccess = { count -> 
                            totalCount += count 
                            Log.d(TAG, "GitHub sync complete: $count")
                        },
                        onFailure = { error ->
                            Log.e(TAG, "GitHub sync failed", error)
                            _syncState.value = SyncState.Error("Community sync failed: ${error.message}")
                            return@launch
                        }
                    )
                }
                
                // Phase 2: Bing Manifest
                if (bingEnabled) {
                    Log.d(TAG, "Starting Bing sync ($bingManifestType)...")
                    val result = bingManifestRepository.syncBingManifest(
                        manifestType = bingManifestType,
                        onProgress = { message, progress, count ->
                            updateUnifiedProgress("bing", "Bing: $message", progress)
                            if (count > 0) _wallpaperCount.value = totalCount + count
                        }
                    )
                    
                    result.fold(
                        onSuccess = { count -> 
                            totalCount += count
                            Log.d(TAG, "Bing sync complete: $count")
                        },
                        onFailure = { error ->
                            Log.e(TAG, "Bing sync failed", error)
                            _syncState.value = SyncState.Error("Bing sync failed: ${error.message}")
                            return@launch
                        }
                    )
                }

                // Phase 3: Vanderwaals Collection Manifest
                if (vanderwaalsCollectionEnabled) {
                    Log.d(TAG, "Starting Vanderwaals Collection sync ($vanderwaalsCollectionManifestType)...")
                    val result = vanderwaalsCollectionRepository.syncVanderwaalsCollectionManifest(
                        manifestType = vanderwaalsCollectionManifestType,
                        onProgress = { message, progress, count ->
                            updateUnifiedProgress("vanderwaals", "Vanderwaals: $message", progress)
                            if (count > 0) _wallpaperCount.value = totalCount + count
                        }
                    )

                    result.fold(
                        onSuccess = { count ->
                            totalCount += count
                            Log.d(TAG, "Vanderwaals Collection sync complete: $count")
                        },
                        onFailure = { error ->
                            Log.e(TAG, "Vanderwaals Collection sync failed", error)
                            _syncState.value = SyncState.Error("Vanderwaals Collection sync failed: ${error.message}")
                            return@launch
                        }
                    )
                }

                // Final Success
                _wallpaperCount.value = totalCount
                settingsDataStore.updateLastSyncTimestamp(System.currentTimeMillis())
                _syncState.value = SyncState.Success(totalCount)
                
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
