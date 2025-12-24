package me.avinas.vanderwaals.domain.usecase

import android.util.Log
import kotlinx.coroutines.flow.first
import me.avinas.vanderwaals.data.datastore.SettingsDataStore
import me.avinas.vanderwaals.data.repository.BingManifestRepository
import me.avinas.vanderwaals.data.repository.ManifestRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for syncing wallpaper catalog from GitHub manifest and Bing API.
 * 
 * Weekly background synchronization process:
 * 
 * 1. Download manifest.json from GitHub repository (if enabled)
 * 2. Fetch Bing daily wallpapers (if enabled)
 * 3. Parse metadata for 6000+ wallpapers
 * 4. Compare with local database (check for new/updated wallpapers)
 * 5. Insert/update wallpaper metadata in Room database
 * 6. Clean up metadata for removed wallpapers
 * 7. Update last sync timestamp
 * 
 * Manifest contains pre-computed data from GitHub Actions curation pipeline:
 * - Download URLs (GitHub raw or jsDelivr CDN)
 * - MobileNetV3 embeddings (576 floats per wallpaper)
 * - Color palettes (5 colors per wallpaper)
 * - Categories and brightness levels
 * - Source attribution
 * 
 * Triggered by:
 * - App launch (if > 7 days since last sync)
 * - Manual "Sync Now" button in settings
 * - WorkManager periodic sync worker
 * 
 * @see me.avinas.vanderwaals.network.GitHubApiService
 * @see me.avinas.vanderwaals.data.repository.WallpaperRepository
 */
@Singleton
class SyncWallpaperCatalogUseCase @Inject constructor(
    private val manifestRepository: ManifestRepository,
    private val bingManifestRepository: BingManifestRepository,
    private val settingsDataStore: SettingsDataStore
) {
    
    companion object {
        private const val TAG = "SyncWallpaperCatalog"
    }
    
    /**
     * Performs a full sync of the wallpaper catalog based on enabled sources.
     * 
     * @param onProgress Optional progress callback with (message, progress 0.0-1.0, count)
     * @return Result<Int> containing total wallpaper count on success, or error on failure
     */
    suspend fun syncCatalog(
        onProgress: ((message: String, progress: Float, count: Int) -> Unit)? = null
    ): Result<Int> {
        return try {
            Log.d(TAG, "Starting catalog sync...")
            onProgress?.invoke("Starting sync...", 0.05f, 0)
            
            // Get enabled sources from settings
            val settings = settingsDataStore.settings.first()
            val githubEnabled = settings.githubEnabled
            val bingEnabled = settings.bingEnabled
            val bingManifestType = settings.bingManifestType
            
            Log.d(TAG, "Sources enabled - GitHub: $githubEnabled, Bing: $bingEnabled (type: $bingManifestType)")
            
            var totalCount = 0
            
            // Calculate progress ranges based on which sources are enabled
            val progressRanges = when {
                githubEnabled && bingEnabled -> Pair(0f to 0.5f, 0.5f to 1f) // GitHub: 0-50%, Bing: 50-100%
                githubEnabled -> Pair(0f to 1f, null)
                bingEnabled -> Pair(null, 0f to 1f)
                else -> Pair(null, null)
            }
            
            // Sync GitHub manifest if enabled
            if (githubEnabled) {
                Log.d(TAG, "Syncing GitHub manifest...")
                val range = progressRanges.first!!
                
                manifestRepository.syncManifest(
                    onProgress = { message, progress, count ->
                        // Scale progress to the GitHub range
                        val scaledProgress = range.first + (progress * (range.second - range.first))
                        onProgress?.invoke("Community: $message", scaledProgress, count)
                    }
                ).fold(
                    onSuccess = { count ->
                        totalCount += count
                        Log.d(TAG, "GitHub sync successful: $count wallpapers")
                    },
                    onFailure = { error ->
                        Log.e(TAG, "GitHub sync failed: ${error.message}", error)
                        // Continue with Bing if enabled, don't fail completely
                    }
                )
            }
            
            // Sync Bing manifest if enabled - uses curated manifest with ML embeddings
            if (bingEnabled) {
                Log.d(TAG, "Syncing Bing manifest ($bingManifestType)...")
                val range = progressRanges.second!!
                
                bingManifestRepository.syncBingManifest(
                    manifestType = bingManifestType,
                    onProgress = { message, progress, count ->
                        // Scale progress to the Bing range
                        val scaledProgress = range.first + (progress * (range.second - range.first))
                        onProgress?.invoke("Bing: $message", scaledProgress, totalCount + count)
                    }
                ).fold(
                    onSuccess = { count ->
                        totalCount += count
                        Log.d(TAG, "Bing sync successful: $count wallpapers")
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Bing sync failed: ${error.message}", error)
                        // Continue even if Bing fails
                    }
                )
            }
            
            if (totalCount == 0 && (githubEnabled || bingEnabled)) {
                Result.failure(Exception("No wallpapers synced from any source"))
            } else {
                Log.d(TAG, "Sync complete: $totalCount total wallpapers")
                onProgress?.invoke("Sync complete!", 1f, totalCount)
                Result.success(totalCount)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed with exception", e)
            Result.failure(e)
        }
    }
}

