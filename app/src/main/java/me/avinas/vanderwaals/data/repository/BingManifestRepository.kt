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
    
    suspend fun isSyncNeeded(): Boolean {
        val settings = settingsDataStore.settings.first()
        
        if (!settings.bingEnabled) return false
        
        val lastSync = settings.bingLastSyncTimestamp
        if (lastSync == 0L) return true  // Never synced
        
        val daysSinceSync = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - lastSync)
        return daysSinceSync >= SYNC_INTERVAL_DAYS
    }
    
    suspend fun getBingWallpaperCount(): Int {
        return wallpaperDao.countBySource("bing")
    }
    
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
                
                val lastModified = if (forceUpdate) {
                    null
                } else {
                    settingsDataStore.settings.first().bingManifestLastModified
                }
                
                val response = fetchManifest(manifestType, lastModified)
                
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
                
                settingsDataStore.updateBingLastSyncTimestamp(System.currentTimeMillis())
                settingsDataStore.updateBingManifestLastModified(
                    response.headers()["Last-Modified"]
                )
                settingsDataStore.updateBingManifestType(manifestType)
                
                Log.i(TAG, "Bing sync complete: ${wallpapers.size} wallpapers")
                onProgress?.invoke("Sync complete!", 1f, wallpapers.size)
                
                return Result.success(wallpapers.size)
                
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "OutOfMemoryError parsing Bing manifest", e)
                return Result.failure(Exception("Out of memory parsing manifest"))
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
    
    // Useful for refreshing or switching manifest types.
    suspend fun clearBingWallpapers() {
        wallpaperDao.deleteBySource("bing")
        settingsDataStore.updateBingManifestLastModified(null)
        Log.d(TAG, "Cleared all Bing wallpapers")
    }
}
