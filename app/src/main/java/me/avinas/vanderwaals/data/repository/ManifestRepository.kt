package me.avinas.vanderwaals.data.repository

import android.util.Log
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import me.avinas.vanderwaals.BuildConfig
import me.avinas.vanderwaals.data.dao.WallpaperMetadataDao
import me.avinas.vanderwaals.network.LocalManifestService
import me.avinas.vanderwaals.network.ManifestService
import me.avinas.vanderwaals.network.dto.toWallpaperEntities
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

/**
 * Syncs the wallpaper manifest from jsDelivr/GitHub into the local Room database.
 */
@Singleton
class ManifestRepository @Inject constructor(
    private val manifestService: ManifestService,
    private val localManifestService: LocalManifestService,
    private val wallpaperDao: WallpaperMetadataDao,
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) {
    
    companion object {
        private const val TAG = "ManifestRepository"
        
        /**
         * Maximum number of retry attempts.
         */
        private const val MAX_RETRIES = 3
        
        /**
         * Base delay for exponential backoff in milliseconds (1 second).
         */
        private const val BASE_DELAY_MS = 1000L
        
        /**
         * Maximum delay for exponential backoff (30 seconds).
         */
        private const val MAX_DELAY_MS = 30_000L
    }
    
    // Uses the HTTP If-Modified-Since header to skip the download when the manifest is unchanged (304).
    suspend fun syncManifest(
        onProgress: ((message: String, progress: Float, count: Int) -> Unit)? = null,
        forceUpdate: Boolean = false
    ): Result<Int> {
        Log.d(TAG, "Starting manifest sync... (forceUpdate=$forceUpdate)")
        
        var lastError: Exception? = null
        
        val prefs = context.getSharedPreferences("vanderwaals_sync", android.content.Context.MODE_PRIVATE)
        val lastModified = if (forceUpdate) null else prefs.getString("manifest_last_modified", null)
        
        repeat(MAX_RETRIES) { attempt ->
            try {
                Log.d(TAG, "Sync attempt ${attempt + 1}/$MAX_RETRIES")
                onProgress?.invoke("Connecting to server...", 0.05f, 0)
                
                val manifest = if (BuildConfig.USE_LOCAL_MANIFEST) {
                    Log.d(TAG, "Loading manifest from local assets")
                    onProgress?.invoke("Loading wallpaper catalog...", 0.2f, 0)
                    localManifestService.getManifest()
                } else {
                    Log.d(TAG, "Downloading manifest from network")
                    onProgress?.invoke("Checking for updates...", 0.1f, 0)
                    
                    val response = if (lastModified != null) {
                        manifestService.getManifestConditional(lastModified)
                    } else {
                        manifestService.getManifest()
                    }
                    
                    if (response.code() == 304) {
                        Log.d(TAG, "Manifest not modified since last sync, skipping download")
                        onProgress?.invoke("Catalog is up to date!", 1.0f, wallpaperDao.getCount())
                        saveLastSyncTimestamp(System.currentTimeMillis())
                        return Result.success(wallpaperDao.getCount())
                    }
                    
                    if (!response.isSuccessful) {
                        val errorMessage = "HTTP ${response.code()}: ${response.message()}"
                        Log.e(TAG, errorMessage)
                        lastError = HttpException(response)
                        
                        // Retry on server errors (5xx), fail on client errors (4xx)
                        if (response.code() in 500..599) {
                            applyExponentialBackoff(attempt)
                            return@repeat
                        } else {
                            return Result.failure(Exception(errorMessage))
                        }
                    }
                    
                    response.headers()["Last-Modified"]?.let { newLastModified ->
                        prefs.edit().putString("manifest_last_modified", newLastModified).apply()
                        Log.d(TAG, "Saved Last-Modified: $newLastModified")
                    }
                    
                    response.body()
                }
                
                if (manifest == null) {
                    val errorMessage = "Empty response body"
                    Log.e(TAG, errorMessage)
                    return Result.failure(Exception(errorMessage))
                }
                
                Log.d(TAG, "Downloaded manifest: version=${manifest.version}, " +
                        "updated=${manifest.lastUpdated}, " +
                        "quantized=${manifest.quantized}, " +
                        "wallpapers=${manifest.wallpapers.size}")
                
                onProgress?.invoke("Processing wallpapers...", 0.5f, manifest.wallpapers.size)
                
                if (manifest.wallpapers.isEmpty()) {
                    val errorMessage = "Invalid manifest: empty wallpapers list"
                    Log.e(TAG, errorMessage)
                    return Result.failure(Exception(errorMessage))
                }
                
                // Convert to entities (off Main: Base64 decode + dequantize for 6000+ embeddings)
                onProgress?.invoke("Processing wallpaper data...", 0.6f, manifest.wallpapers.size)
                val entities = withContext(Dispatchers.Default) { manifest.toWallpaperEntities() }
                Log.d(TAG, "Converted ${entities.size} wallpapers to entities")
                
                // Save to database (replace all)
                try {
                    onProgress?.invoke("Clearing old wallpapers...", 0.7f, entities.size)
                    wallpaperDao.deleteBySource("github")
                    Log.d(TAG, "Cleared old GitHub wallpapers")
                    
                    onProgress?.invoke("Saving ${entities.size} wallpapers...", 0.8f, entities.size)
                    wallpaperDao.insertAll(entities)
                    Log.d(TAG, "Inserted ${entities.size} wallpapers")
                    
                    val finalCount = wallpaperDao.getCount()
                    Log.d(TAG, "Sync successful: $finalCount wallpapers in database")
                    
                    saveLastSyncTimestamp(System.currentTimeMillis())
                    
                    onProgress?.invoke("Sync complete!", 1.0f, finalCount)
                    return Result.success(finalCount)
                } catch (e: Exception) {
                    val errorMessage = "Database error: ${e.message}"
                    Log.e(TAG, errorMessage, e)
                    return Result.failure(Exception(errorMessage, e))
                }
                
            } catch (e: IOException) {
                // Network errors - retry with backoff
                val errorMessage = "Network error: ${e.message}"
                Log.w(TAG, "$errorMessage (attempt ${attempt + 1}/$MAX_RETRIES)")
                lastError = e
                
                if (attempt < MAX_RETRIES - 1) {
                    applyExponentialBackoff(attempt)
                }
                
            } catch (e: JsonSyntaxException) {
                // Parse errors - fail immediately (bad manifest)
                val errorMessage = "Parse error: ${e.message}"
                Log.e(TAG, errorMessage, e)
                return Result.failure(Exception(errorMessage, e))
                
            } catch (e: OutOfMemoryError) {
                // ponytail: OOM parsing large manifest with 6000+ embeddings — largeHeap helps but low-RAM devices can still OOM
                Log.e(TAG, "OutOfMemoryError parsing manifest — device too low on memory", e)
                return Result.failure(Exception("Out of memory parsing manifest. Try closing other apps and retry."))
                
            } catch (e: Exception) {
                val errorMessage = "Unexpected error: ${e.message}"
                Log.e(TAG, errorMessage, e)
                return Result.failure(Exception(errorMessage, e))
            }
        }
        
        val errorMessage = "Sync failed after $MAX_RETRIES attempts: ${lastError?.message}"
        Log.e(TAG, errorMessage)
        return Result.failure(Exception(errorMessage, lastError))
    }

    
    private suspend fun applyExponentialBackoff(attempt: Int) {
        val delayMs = minOf(
            BASE_DELAY_MS * (2.0.pow(attempt.toDouble())).toLong(),
            MAX_DELAY_MS
        )
        Log.d(TAG, "Retrying after ${delayMs}ms...")
        delay(delayMs)
    }
    
    suspend fun getLastSyncTimestamp(): Long? {
        val prefs = context.getSharedPreferences("vanderwaals_sync", android.content.Context.MODE_PRIVATE)
        val timestamp = prefs.getLong("last_sync_timestamp", 0L)
        return if (timestamp > 0) timestamp else null
    }
    
    suspend fun saveLastSyncTimestamp(timestamp: Long) {
        val prefs = context.getSharedPreferences("vanderwaals_sync", android.content.Context.MODE_PRIVATE)
        prefs.edit().putLong("last_sync_timestamp", timestamp).apply()
    }
    
    // Sync when never synced or last synced more than 7 days ago.
    suspend fun isSyncNeeded(): Boolean {
        val lastSync = getLastSyncTimestamp() ?: return true
        val now = System.currentTimeMillis()
        val weekInMs = 7 * 24 * 60 * 60 * 1000L
        return (now - lastSync) > weekInMs
    }
    
    suspend fun getWallpaperCount(): Int {
        return wallpaperDao.getCount()
    }
    
    suspend fun isDatabaseInitialized(): Boolean {
        return getWallpaperCount() > 0
    }
    
    // Unlike the manifest sync (which replaces all wallpapers), this only inserts/updates the given entries.
    suspend fun insertBingWallpapers(wallpapers: List<me.avinas.vanderwaals.data.entity.WallpaperMetadata>) {
        try {
            wallpaperDao.insertAll(wallpapers)
            Log.d(TAG, "Inserted ${wallpapers.size} Bing wallpapers")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to insert Bing wallpapers", e)
            throw e
        }
    }
}
