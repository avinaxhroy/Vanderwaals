package me.avinas.vanderwaals.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import me.avinas.vanderwaals.data.dao.WallpaperMetadataDao
import me.avinas.vanderwaals.data.datastore.SettingsDataStore
import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import me.avinas.vanderwaals.network.ManifestService
import me.avinas.vanderwaals.network.dto.ManifestDto
import me.avinas.vanderwaals.network.dto.toEntity
import retrofit2.Response
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for syncing Bing wallpaper manifests.
 * 
 * Handles downloading and processing the curated Bing wallpaper manifests:
 * - **Lite manifest**: Last 2 years (~700 wallpapers, ~2MB) - recommended
 * - **Full manifest**: 2009-present (~5400+ wallpapers, ~15MB)
 * 
 * Features:
 * - Smart sync with If-Modified-Since headers (304 Not Modified)
 * - Monthly sync interval (30 days)
 * - Progress callbacks for UI feedback
 * - Retry logic with exponential backoff
 * - Quantized embedding dequantization (int8 → float32)
 * 
 * @see ManifestService.getBingManifestLite
 * @see ManifestService.getBingManifestFull
 */
@Singleton
class BingManifestRepository @Inject constructor(
    private val manifestService: ManifestService,
    private val wallpaperDao: WallpaperMetadataDao,
    private val settingsDataStore: SettingsDataStore
) {
    companion object {
        private const val TAG = "BingManifestRepository"
        private const val SYNC_INTERVAL_DAYS = 90L  // Quarterly sync
        private const val MAX_RETRIES = 3
        private const val INITIAL_BACKOFF_MS = 1000L
        private const val CHUNK_SIZE = 100  // Insert in chunks for progress
    }
    
    /**
     * Checks if a Bing sync is needed based on the last sync timestamp.
     * 
     * @return true if sync is needed (>30 days since last sync or never synced)
     */
    suspend fun isSyncNeeded(): Boolean {
        val settings = settingsDataStore.settings.first()
        
        // If Bing is not enabled, no sync needed
        if (!settings.bingEnabled) return false
        
        val lastSync = settings.bingLastSyncTimestamp
        if (lastSync == 0L) return true  // Never synced
        
        val daysSinceSync = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - lastSync)
        return daysSinceSync >= SYNC_INTERVAL_DAYS
    }
    
    /**
     * Gets the number of Bing wallpapers currently in the database.
     */
    suspend fun getBingWallpaperCount(): Int {
        return wallpaperDao.countBySource("bing")
    }
    
    /**
     * Syncs the Bing wallpaper manifest.
     * 
     * @param manifestType "lite" for 2-year manifest, "full" for complete archive
     * @param onProgress Progress callback: (message, progress 0-1, wallpaperCount)
     * @param forceUpdate If true, ignores If-Modified-Since and downloads fresh
     * @return Result with count of synced wallpapers or error
     */
    suspend fun syncBingManifest(
        manifestType: String = "lite",
        onProgress: ((message: String, progress: Float, count: Int) -> Unit)? = null,
        forceUpdate: Boolean = false
    ): Result<Int> {
        Log.d(TAG, "Starting Bing manifest sync (type: $manifestType, force: $forceUpdate)")
        
        var attempt = 0
        var backoffMs = INITIAL_BACKOFF_MS
        
        while (attempt < MAX_RETRIES) {
            attempt++
            
            try {
                onProgress?.invoke("Connecting to server...", 0.1f, 0)
                
                // Get last modified header for conditional request
                val lastModified = if (forceUpdate) {
                    null
                } else {
                    settingsDataStore.settings.first().bingManifestLastModified
                }
                
                // Fetch manifest with conditional request
                val response = fetchManifest(manifestType, lastModified)
                
                // Handle 304 Not Modified
                if (response.code() == 304) {
                    Log.d(TAG, "Manifest not modified, skipping download")
                    onProgress?.invoke("Already up to date", 1f, getBingWallpaperCount())
                    return Result.success(getBingWallpaperCount())
                }
                
                if (!response.isSuccessful) {
                    throw Exception("HTTP ${response.code()}: ${response.message()}")
                }
                
                val manifest = response.body()
                    ?: throw Exception("Empty response body")
                
                onProgress?.invoke("Processing wallpapers...", 0.3f, 0)
                
                // Convert and insert wallpapers (off Main: heavy Base64/dequantize per entry)
                var wallpapers = withContext(Dispatchers.Default) {
                    manifest.wallpapers.mapNotNull { dto -> convertToEntity(dto) }
                }
                
                Log.d(TAG, "Converted ${wallpapers.size} wallpapers")
                
                // Insert in chunks with progress updates
                var inserted = 0
                wallpapers.chunked(CHUNK_SIZE).forEachIndexed { chunkIndex, chunk ->
                wallpaperDao.insertAll(chunk)
                    inserted += chunk.size
                    
                    val progress = 0.3f + (0.7f * (inserted.toFloat() / wallpapers.size))
                    onProgress?.invoke("Saving wallpapers...", progress, inserted)
                }
                
                // Update sync metadata
                settingsDataStore.updateBingLastSyncTimestamp(System.currentTimeMillis())
                settingsDataStore.updateBingManifestLastModified(
                    response.headers()["Last-Modified"]
                )
                settingsDataStore.updateBingManifestType(manifestType)
                
                Log.i(TAG, "Bing sync complete: ${wallpapers.size} wallpapers")
                onProgress?.invoke("Sync complete!", 1f, wallpapers.size)
                
                return Result.success(wallpapers.size)
                
            } catch (e: Exception) {
                Log.e(TAG, "Sync attempt $attempt failed: ${e.message}", e)
                
                if (attempt < MAX_RETRIES) {
                    onProgress?.invoke("Retrying... ($attempt/$MAX_RETRIES)", 0.1f, 0)
                    delay(backoffMs)
                    backoffMs *= 2  // Exponential backoff
                } else {
                    return Result.failure(e)
                }
            }
        }
        
        return Result.failure(Exception("Max retries exceeded"))
    }
    
    /**
     * Fetches the manifest based on type with optional conditional request.
     */
    private suspend fun fetchManifest(
        manifestType: String,
        lastModified: String?
    ): Response<ManifestDto> {
        return when (manifestType) {
            "full" -> {
                if (lastModified != null) {
                    manifestService.getBingManifestFullConditional(lastModified)
                } else {
                    manifestService.getBingManifestFull()
                }
            }
            else -> {  // "lite" or default
                if (lastModified != null) {
                    manifestService.getBingManifestLiteConditional(lastModified)
                } else {
                    manifestService.getBingManifestLite()
                }
            }
        }
    }
    
    /**
     * Converts a DTO wallpaper to database entity.
     * Uses the existing toEntity() extension function.
     */
    private fun convertToEntity(
        dto: me.avinas.vanderwaals.network.dto.WallpaperMetadataDto
    ): WallpaperMetadata? {
        return try {
            dto.toEntity()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to convert wallpaper ${dto.id}: ${e.message}")
            null
        }
    }
    
    /**
     * Clears all Bing wallpapers from the database.
     * Useful for refreshing or switching manifest types.
     */
    suspend fun clearBingWallpapers() {
        wallpaperDao.deleteBySource("bing")
        settingsDataStore.updateBingManifestLastModified(null)
        Log.d(TAG, "Cleared all Bing wallpapers")
    }
}
