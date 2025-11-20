package me.avinas.vanderwaals.domain.usecase

import android.util.Log
import kotlinx.coroutines.flow.first
import me.avinas.vanderwaals.data.datastore.SettingsDataStore
import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import me.avinas.vanderwaals.data.repository.ManifestRepository
import me.avinas.vanderwaals.network.BingApiService
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
    private val bingApiService: BingApiService,
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
            
            Log.d(TAG, "Sources enabled - GitHub: $githubEnabled, Bing: $bingEnabled")
            
            var totalCount = 0
            
            // Sync GitHub manifest if enabled
            if (githubEnabled) {
                Log.d(TAG, "Syncing GitHub manifest...")
                onProgress?.invoke("Connecting to server...", 0.1f, 0)
                
                manifestRepository.syncManifest { message, progress, count ->
                    // Pass through progress from repository
                    onProgress?.invoke(message, progress, count)
                }.fold(
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
            
            // Sync Bing wallpapers if enabled
            if (bingEnabled) {
                Log.d(TAG, "Syncing Bing wallpapers...")
                syncBingWallpapers().fold(
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
                Result.success(totalCount)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed with exception", e)
            Result.failure(e)
        }
    }
    
    /**
     * Syncs Bing daily wallpapers.
     * Currently fetches the latest daily wallpaper.
     * 
     * @return Result<Int> containing count of synced Bing wallpapers
     */
    private suspend fun syncBingWallpapers(): Result<Int> {
        return try {
            Log.d(TAG, "Fetching Bing daily wallpaper...")
            
            // Fetch today's wallpaper
            val response = bingApiService.getWallpapers(
                format = "js",
                idx = 0,
                count = 1,
                market = "en-US"
            )
            
            if (!response.isSuccessful) {
                return Result.failure(Exception("Bing API returned ${response.code()}"))
            }
            
            val bingWallpapers = response.body()
            if (bingWallpapers == null || bingWallpapers.images.isEmpty()) {
                return Result.failure(Exception("No Bing wallpapers returned"))
            }
            
            Log.d(TAG, "Fetched ${bingWallpapers.images.size} Bing wallpapers")
            
            // Convert Bing wallpapers to WallpaperMetadata entities
            val wallpaperEntities = bingWallpapers.images.map { bingImage ->
                WallpaperMetadata(
                    id = "bing_${bingImage.hsh}",
                    url = "https://www.bing.com${bingImage.urlbase}_UHD.jpg",
                    thumbnailUrl = "https://www.bing.com${bingImage.url}",
                    source = "bing",
                    category = "daily",
                    colors = extractColorsPlaceholder(), // Placeholder colors
                    brightness = 50, // Default brightness
                    contrast = 50, // Default contrast
                    embedding = FloatArray(576) { 0.5f }, // Placeholder embedding - will be computed on-demand
                    resolution = "1920x1080",
                    attribution = bingImage.copyright
                )
            }
            
            // Insert into database using manifestRepository's DAO
            manifestRepository.insertBingWallpapers(wallpaperEntities)
            
            Log.d(TAG, "Inserted ${wallpaperEntities.size} Bing wallpapers into database")
            Result.success(wallpaperEntities.size)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync Bing wallpapers", e)
            Result.failure(e)
        }
    }
    
    /**
     * Generates placeholder colors for Bing wallpapers.
     * In production, these should be extracted from the actual image.
     */
    private fun extractColorsPlaceholder(): List<String> {
        return listOf("#2E3440", "#3B4252", "#434C5E", "#4C566A", "#D8DEE9")
    }
}
