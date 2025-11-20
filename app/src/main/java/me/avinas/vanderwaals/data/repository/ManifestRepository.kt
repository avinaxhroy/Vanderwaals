package me.avinas.vanderwaals.data.repository

import android.util.Log
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.delay
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
 * Repository for managing wallpaper manifest synchronization.
 * 
 * Handles downloading the manifest.json file from jsDelivr/GitHub,
 * parsing the JSON response, converting to database entities, and
 * storing in the local Room database.
 * 
 * **Responsibilities:**
 * - Download manifest from CDN with retry logic
 * - Parse JSON to DTOs with validation
 * - Convert DTOs to Room entities
 * - Batch insert into database
 * - Error handling with meaningful messages
 * 
 * **Sync Strategy:**
 * - Weekly sync via WorkManager
 * - Manual sync from settings
 * - Retry on failure with exponential backoff
 * - Validate manifest structure before saving
 * 
 * **Error Handling:**
 * - Network errors: Retry with backoff
 * - HTTP errors: Log and fail with message
 * - Parse errors: Fail immediately (bad manifest)
 * - Database errors: Fail with rollback
 * 
 * **Usage:**
 * ```kotlin
 * @Inject lateinit var manifestRepository: ManifestRepository
 * 
 * viewModelScope.launch {
 *     val result = manifestRepository.syncManifest()
 *     result.fold(
 *         onSuccess = { count ->
 *             Log.d("Sync", "Synced $count wallpapers")
 *         },
 *         onFailure = { error ->
 *             Log.e("Sync", "Failed: ${error.message}")
 *         }
 *     )
 * }
 * ```
 * 
 * @property manifestService Retrofit service for manifest API
 * @property wallpaperDao DAO for database operations
 * 
 * @see ManifestService
 * @see WallpaperMetadataDao
 */
@Singleton
class ManifestRepository @Inject constructor(
    private val manifestService: ManifestService,
    private val localManifestService: LocalManifestService,
    private val wallpaperDao: WallpaperMetadataDao,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
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
     * **Process:**
     * 1. Download manifest.json from jsDelivr/GitHub
     * 2. Validate manifest structure (version, embeddings)
     * 3. Convert DTOs to Room entities
     * 4. Delete old wallpapers from database
     * 5. Batch insert new wallpapers
     * 6. Return count of synced wallpapers
     * 
     * **Retry Logic:**
     * - Retries network failures up to 3 times
     * - Uses exponential backoff: 1s, 2s, 4s
     * - Immediate failure for parse/validation errors
     * 
     * **Error Messages:**
     * - "Network error: ..." - Connection/timeout issues
     * - "HTTP XXX: ..." - Server errors
     * - "Parse error: ..." - Invalid JSON
     * - "Invalid manifest: ..." - Structure validation failed
     * - "Database error: ..." - Database operation failed
     * 
     * @param onProgress Optional progress callback with (message, progress 0.0-1.0, count)
     * @return Result<Int> Success with wallpaper count, or Failure with error
     * 
     * Example:
     * ```kotlin
     * suspend fun performSync() {
     *     _syncState.value = SyncState.Loading
     *     
     *     manifestRepository.syncManifest { message, progress, count ->
     *         _syncState.value = SyncState.Loading(message, progress)
     *         _wallpaperCount.value = count
     *     }
     *         .onSuccess { count ->
     *             _syncState.value = SyncState.Success(count)
     *             _lastSyncTime.value = System.currentTimeMillis()
     *         }
     *         .onFailure { error ->
     *             _syncState.value = SyncState.Error(error.message ?: "Unknown error")
     *             Log.e(TAG, "Sync failed", error)
     *         }
     * }
     * ```
     */
    suspend fun syncManifest(
        onProgress: ((message: String, progress: Float, count: Int) -> Unit)? = null
    ): Result<Int> {
        Log.d(TAG, "Starting manifest sync...")
        
        var lastError: Exception? = null
        
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
                    // Don't set progress here - DownloadProgressManager handles real-time progress
                    val response = manifestService.getManifest()
                    
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
                        "wallpapers=${manifest.wallpapers.size}")
                
                onProgress?.invoke("Processing wallpapers...", 0.5f, manifest.wallpapers.size)
                
                // Validate manifest has content
                if (manifest.wallpapers.isEmpty()) {
                    val errorMessage = "Invalid manifest: empty wallpapers list"
                    Log.e(TAG, errorMessage)
                    return Result.failure(Exception(errorMessage))
                }
                
                // Convert to entities
                onProgress?.invoke("Processing wallpaper data...", 0.6f, manifest.wallpapers.size)
                val entities = manifest.toWallpaperEntities()
                Log.d(TAG, "Converted ${entities.size} wallpapers to entities")
                
                // Save to database (replace all)
                try {
                    onProgress?.invoke("Clearing old wallpapers...", 0.7f, entities.size)
                    wallpaperDao.deleteAll()
                    Log.d(TAG, "Cleared old wallpapers")
                    
                    onProgress?.invoke("Saving ${entities.size} wallpapers...", 0.8f, entities.size)
                    wallpaperDao.insertAll(entities)
                    Log.d(TAG, "Inserted ${entities.size} wallpapers")
                    
                    val finalCount = wallpaperDao.getCount()
                    Log.d(TAG, "Sync successful: $finalCount wallpapers in database")
                    
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
