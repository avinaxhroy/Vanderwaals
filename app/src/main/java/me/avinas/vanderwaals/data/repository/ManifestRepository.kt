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
 * Handles download, JSON parsing, entity conversion, and batch insert with retry logic.
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
    
    /**
     * Synchronizes the wallpaper manifest from the network to local database.
     * 
     * **Smart Update Logic:**
     * Uses HTTP If-Modified-Since header to check if manifest has changed.
     * If unchanged (304 response), skips download entirely.
     * 
     * **Process:**
     * 1. Check if manifest has been modified since last sync (If-Modified-Since)
     * 2. If not modified (304): Return existing count, skip download
     * 3. If modified (200): Download, validate, and save manifest
     * 
     * **Retry Logic:**
     * - Retries network failures up to 3 times
     * - Uses exponential backoff: 1s, 2s, 4s
     * - Immediate failure for parse/validation errors
     * 
     * @param onProgress Optional progress callback with (message, progress 0.0-1.0, count)
     * @param forceUpdate If true, skip If-Modified-Since check and always download
     * @return Result<Int> Success with wallpaper count, or Failure with error
     */
    suspend fun syncManifest(
        onProgress: ((message: String, progress: Float, count: Int) -> Unit)? = null,
        forceUpdate: Boolean = false
    ): Result<Int> {
        Log.d(TAG, "Starting manifest sync... (forceUpdate=$forceUpdate)")
        
        var lastError: Exception? = null
        
        // Get stored Last-Modified timestamp for conditional request
        val prefs = context.getSharedPreferences("vanderwaals_sync", android.content.Context.MODE_PRIVATE)
        val lastModified = if (forceUpdate) null else prefs.getString("manifest_last_modified", null)
        
        // Retry loop with exponential backoff
        repeat(MAX_RETRIES) { attempt ->
            try {
                Log.d(TAG, "Sync attempt ${attempt + 1}/$MAX_RETRIES")
                onProgress?.invoke("Connecting to server...", 0.05f, 0)
                
                // Download or load manifest based on configuration
                val manifest = if (BuildConfig.USE_LOCAL_MANIFEST) {
                    Log.d(TAG, "Loading manifest from local assets")
                    onProgress?.invoke("Loading wallpaper catalog...", 0.2f, 0)
                    localManifestService.getManifest()
                } else {
                    Log.d(TAG, "Downloading manifest from network")
                    onProgress?.invoke("Checking for updates...", 0.1f, 0)
                    
                    // Use conditional request if we have a previous Last-Modified
                    val response = if (lastModified != null) {
                        manifestService.getManifestConditional(lastModified)
                    } else {
                        manifestService.getManifest()
                    }
                    
                    // Check for "Not Modified" response - manifest unchanged
                    if (response.code() == 304) {
                        Log.d(TAG, "Manifest not modified since last sync, skipping download")
                        onProgress?.invoke("Catalog is up to date!", 1.0f, wallpaperDao.getCount())
                        saveLastSyncTimestamp(System.currentTimeMillis())
                        return Result.success(wallpaperDao.getCount())
                    }
                    
                    // Check HTTP response
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
                    
                    // Save new Last-Modified header for next sync
                    response.headers()["Last-Modified"]?.let { newLastModified ->
                        prefs.edit().putString("manifest_last_modified", newLastModified).apply()
                        Log.d(TAG, "Saved Last-Modified: $newLastModified")
                    }
                    
                    // Extract manifest
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
                
                // Validate manifest has content
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
                    
                    // Save sync timestamp
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
                
            } catch (e: Exception) {
                // Unexpected errors - fail immediately
                val errorMessage = "Unexpected error: ${e.message}"
                Log.e(TAG, errorMessage, e)
                return Result.failure(Exception(errorMessage, e))
            }
        }
        
        // All retries exhausted
        val errorMessage = "Sync failed after $MAX_RETRIES attempts: ${lastError?.message}"
        Log.e(TAG, errorMessage)
        return Result.failure(Exception(errorMessage, lastError))
    }

    
    /**
     * Applies exponential backoff delay before retry.
     * 
     * Delay formula: min(BASE_DELAY * (2 ^ attempt), MAX_DELAY)
     * - Attempt 0: 1 second
     * - Attempt 1: 2 seconds
     * - Attempt 2: 4 seconds
     * - Max: 30 seconds
     * 
     * @param attempt Current retry attempt (0-indexed)
     */
    private suspend fun applyExponentialBackoff(attempt: Int) {
        val delayMs = minOf(
            BASE_DELAY_MS * (2.0.pow(attempt.toDouble())).toLong(),
            MAX_DELAY_MS
        )
        Log.d(TAG, "Retrying after ${delayMs}ms...")
        delay(delayMs)
    }
    
    /**
     * Gets the last sync timestamp from SharedPreferences.
     * 
     * @return Last sync timestamp in milliseconds, or null if never synced
     */
    suspend fun getLastSyncTimestamp(): Long? {
        val prefs = context.getSharedPreferences("vanderwaals_sync", android.content.Context.MODE_PRIVATE)
        val timestamp = prefs.getLong("last_sync_timestamp", 0L)
        return if (timestamp > 0) timestamp else null
    }
    
    /**
     * Saves the sync timestamp to SharedPreferences.
     * 
     * @param timestamp Sync timestamp in milliseconds
     */
    suspend fun saveLastSyncTimestamp(timestamp: Long) {
        val prefs = context.getSharedPreferences("vanderwaals_sync", android.content.Context.MODE_PRIVATE)
        prefs.edit().putLong("last_sync_timestamp", timestamp).apply()
    }
    
    /**
     * Checks if a sync is needed based on last sync time.
     * 
     * Sync is needed if:
     * - Never synced before
     * - Last sync was more than 7 days ago
     * 
     * @return true if sync is needed, false otherwise
     */
    suspend fun isSyncNeeded(): Boolean {
        val lastSync = getLastSyncTimestamp() ?: return true
        val now = System.currentTimeMillis()
        val weekInMs = 7 * 24 * 60 * 60 * 1000L
        return (now - lastSync) > weekInMs
    }
    
    /**
     * Gets the current wallpaper count from database.
     * 
     * @return Number of wallpapers in local database
     */
    suspend fun getWallpaperCount(): Int {
        return wallpaperDao.getCount()
    }
    
    /**
     * Checks if the database has been initialized with wallpapers.
     * 
     * @return true if database has wallpapers, false if empty
     */
    suspend fun isDatabaseInitialized(): Boolean {
        return getWallpaperCount() > 0
    }
    
    /**
     * Inserts Bing wallpapers into the database.
     * 
     * Unlike the manifest sync which replaces all wallpapers, this method
     * only inserts new Bing wallpapers or updates existing ones.
     * 
     * @param wallpapers List of WallpaperMetadata entities to insert
     */
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
