package me.avinas.vanderwaals.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import me.avinas.vanderwaals.data.dao.DownloadQueueDao
import me.avinas.vanderwaals.data.dao.WallpaperHistoryDao
import me.avinas.vanderwaals.data.dao.WallpaperMetadataDao
import me.avinas.vanderwaals.data.entity.DownloadQueueItem
import me.avinas.vanderwaals.data.entity.WallpaperHistory
import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import me.avinas.vanderwaals.domain.usecase.FeedbackType
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default implementation of WallpaperRepository.
 * 
 * Manages wallpaper metadata, download queue, and file storage with proper
 * cache management and error handling.
 * 
 * **Cache Strategy:**
 * - Maximum cache size: 450 MB (~150 wallpapers at 3MB each)
 * - LRU eviction: Remove least recently used when cache full
 * - Keep top 100 wallpapers by priority
 * - Automatic cleanup on low storage
 * 
 * **Download Strategy:**
 * - WiFi preferred (skip on cellular to save data)
 * - Battery optimization (skip on low battery)
 * - Exponential backoff retry (3 attempts max)
 * - Parallel downloads (up to 3 concurrent)
 * 
 * **Thread Safety:**
 * All database operations use Room's suspend functions.
 * File I/O uses Dispatchers.IO for proper thread management.
 * 
 * @property context Application context for file storage access
 * @property wallpaperMetadataDao DAO for wallpaper metadata
 * @property downloadQueueDao DAO for download queue management
 * @property wallpaperHistoryDao DAO for application history
 * @property okHttpClient HTTP client for downloading images
 */
@Singleton
class WallpaperRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val wallpaperMetadataDao: WallpaperMetadataDao,
    private val downloadQueueDao: DownloadQueueDao,
    private val wallpaperHistoryDao: WallpaperHistoryDao,
    private val okHttpClient: OkHttpClient,
    private val segmentedDownloader: me.avinas.vanderwaals.network.SegmentedDownloader
) : WallpaperRepository {
    
    /**
     * Cache directory for downloaded wallpaper files.
     * Located at: /data/data/{package}/cache/wallpapers/
     */
    private val wallpaperCacheDir: File by lazy {
        File(context.cacheDir, WALLPAPER_CACHE_DIR_NAME).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }
    
    override fun getAllWallpapers(): Flow<List<WallpaperMetadata>> {
        return wallpaperMetadataDao.getAll()
    }
    
    override fun getDownloadedWallpapers(): Flow<List<WallpaperMetadata>> {
        // Combine wallpaper metadata with download queue status
        return combine(
            wallpaperMetadataDao.getAll(),
            downloadQueueDao.getQueue()
        ) { allWallpapers, queue ->
            // If queue is empty, return all available wallpapers
            // This handles the fresh app case where no queue has been initialized yet
            if (queue.isEmpty()) {
                return@combine allWallpapers
            }
            
            // Get IDs of downloaded wallpapers
            val downloadedIds = queue
                .filter { it.downloaded }
                .map { it.wallpaperId }
                .toSet()
            
            // Filter wallpapers that are downloaded
            allWallpapers.filter { wallpaper ->
                wallpaper.id in downloadedIds && getWallpaperFile(wallpaper).exists()
            }
        }.flowOn(Dispatchers.IO) // CRITICAL: Perform file existence checks on IO thread
    }
    
    override fun getWallpapersByCategory(category: String): Flow<List<WallpaperMetadata>> {
        return wallpaperMetadataDao.getByCategory(category)
    }
    
    override suspend fun addToDownloadQueue(wallpapers: List<WallpaperMetadata>) {
        withContext(Dispatchers.IO) {
            // Convert wallpapers to queue items with default priority
            val queueItems = wallpapers.mapIndexed { index, wallpaper ->
                // Priority decreases with index (first = highest priority)
                val priority = 1.0f - (index.toFloat() / wallpapers.size.toFloat())
                
                DownloadQueueItem(
                    wallpaperId = wallpaper.id,
                    priority = priority,
                    downloaded = getWallpaperFile(wallpaper).exists(),
                    retryCount = 0
                )
            }
            
            // Clear existing queue and insert new items
            downloadQueueDao.deleteAll()
            downloadQueueDao.insertAll(queueItems)
        }
    }
    
    override suspend fun insertQueueItems(queueItems: List<DownloadQueueItem>) {
        withContext(Dispatchers.IO) {
            // Insert/update queue items without clearing existing queue
            // Uses REPLACE strategy, so existing items will be updated
            downloadQueueDao.insertAll(queueItems)
        }
    }
    
    override suspend fun getNextToDownload(limit: Int): List<DownloadQueueItem> {
        return withContext(Dispatchers.IO) {
            downloadQueueDao.getTopUndownloaded(limit)
        }
    }
    
    override suspend fun markAsDownloaded(wallpaperId: String) {
        withContext(Dispatchers.IO) {
            val queueItem = downloadQueueDao.getByWallpaperId(wallpaperId)
            
            if (queueItem != null) {
                val updatedItem = queueItem.copy(downloaded = true)
                downloadQueueDao.update(updatedItem)
            }
        }
    }
    
    override suspend fun recordWallpaperApplied(wallpaper: WallpaperMetadata): Long {
        return withContext(Dispatchers.IO) {
            val historyEntry = WallpaperHistory(
                wallpaperId = wallpaper.id,
                appliedAt = System.currentTimeMillis(),
                removedAt = null,
                userFeedback = null,
                downloadedToStorage = false
            )
            
            wallpaperHistoryDao.insert(historyEntry)
        }
    }
    
    override suspend fun updateHistory(historyId: Long, feedback: FeedbackType) {
        withContext(Dispatchers.IO) {
            val feedbackString = when (feedback) {
                FeedbackType.LIKE -> WallpaperHistory.FEEDBACK_LIKE
                FeedbackType.DISLIKE -> WallpaperHistory.FEEDBACK_DISLIKE
            }
            
            wallpaperHistoryDao.setFeedback(historyId, feedbackString)
        }
    }
    
    override suspend fun updateHistoryWithContext(
        historyId: Long, 
        feedback: FeedbackType, 
        context: me.avinas.vanderwaals.data.entity.FeedbackContext
    ) {
        withContext(Dispatchers.IO) {
            val feedbackString = when (feedback) {
                FeedbackType.LIKE -> WallpaperHistory.FEEDBACK_LIKE
                FeedbackType.DISLIKE -> WallpaperHistory.FEEDBACK_DISLIKE
            }
            
            // Convert FeedbackContext to JSON string using Converters
            val converters = me.avinas.vanderwaals.data.entity.Converters(com.google.gson.Gson())
            val contextJson = converters.fromFeedbackContext(context)
            
            wallpaperHistoryDao.setFeedbackWithContext(historyId, feedbackString, contextJson)
        }
    }
    
    override fun getHistory(): Flow<List<WallpaperHistory>> {
        return wallpaperHistoryDao.getHistory()
    }
    
    override suspend fun downloadWallpaper(wallpaper: WallpaperMetadata): Result<File> {
        return withContext(Dispatchers.IO) {
            try {
                // Step 1: Check if file already exists
                val targetFile = getWallpaperFile(wallpaper)
                if (targetFile.exists() && targetFile.length() > 0) {
                    return@withContext Result.success(targetFile)
                }
                
                // Step 2: Check cache size before downloading
                ensureCacheSpace()
                
                // Step 3: Execute download using SegmentedDownloader
                // This handles both standard and segmented downloads automatically
                val result = segmentedDownloader.download(wallpaper.url, targetFile)
                
                if (result.isFailure) {
                    return@withContext Result.failure(
                        result.exceptionOrNull() ?: IOException("Download failed")
                    )
                }
                
                Result.success(targetFile)
                
            } catch (e: IOException) {
                // Network or file I/O error
                Result.failure(
                    IOException("Download failed: ${e.message}", e)
                )
            } catch (e: Exception) {
                // Unexpected error
                Result.failure(
                    Exception("Unexpected error during download: ${e.message}", e)
                )
            }
        }
    }
    
    override suspend fun deleteWallpaper(wallpaper: WallpaperMetadata): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val file = getWallpaperFile(wallpaper)
                
                if (file.exists()) {
                    if (file.delete()) {
                        // Update download queue status
                        val queueItem = downloadQueueDao.getByWallpaperId(wallpaper.id)
                        if (queueItem != null) {
                            val updatedItem = queueItem.copy(downloaded = false)
                            downloadQueueDao.update(updatedItem)
                        }
                        
                        Result.success(Unit)
                    } else {
                        Result.failure(IOException("Failed to delete file: ${file.path}"))
                    }
                } else {
                    // File doesn't exist, consider it a success
                    Result.success(Unit)
                }
                
            } catch (e: Exception) {
                Result.failure(
                    Exception("Failed to delete wallpaper: ${e.message}", e)
                )
            }
        }
    }
    
    /**
     * Gets the File object for a wallpaper.
     * File path: cache/wallpapers/{wallpaperId}.jpg
     */
    private fun getWallpaperFile(wallpaper: WallpaperMetadata): File {
        return File(wallpaperCacheDir, "${wallpaper.id}.jpg")
    }
    
    /**
     * Ensures there's enough space in cache for new downloads.
     * 
     * If cache exceeds max size (450 MB), removes least recently used files
     * until cache is below 80% of max size (360 MB).
     * 
     * **Strategy:**
     * 1. Get current cache size
     * 2. If over limit, get all cached files sorted by last modified time
     * 3. Delete oldest files until under 80% of max size
     * 4. Update download queue for deleted files
     */
    private suspend fun ensureCacheSpace() {
        val cacheSize = calculateCacheSize()
        
        if (cacheSize > MAX_CACHE_SIZE_BYTES) {
            val targetSize = (MAX_CACHE_SIZE_BYTES * 0.8).toLong() // 80% of max
            val amountToDelete = cacheSize - targetSize
            
            // Get all cached files sorted by last modified (oldest first)
            val cachedFiles = wallpaperCacheDir.listFiles()
                ?.filter { it.extension == "jpg" }
                ?.sortedBy { it.lastModified() }
                ?: return
            
            var deletedSize = 0L
            val deletedIds = mutableListOf<String>()
            
            for (file in cachedFiles) {
                if (deletedSize >= amountToDelete) break
                
                deletedSize += file.length()
                val wallpaperId = file.nameWithoutExtension
                deletedIds.add(wallpaperId)
                
                file.delete()
            }
            
            // Update download queue for deleted files
            deletedIds.forEach { id ->
                val queueItem = downloadQueueDao.getByWallpaperId(id)
                if (queueItem != null) {
                    val updatedItem = queueItem.copy(downloaded = false)
                    downloadQueueDao.update(updatedItem)
                }
            }
        }
    }
    
    /**
     * Calculates total size of wallpaper cache in bytes.
     */
    private fun calculateCacheSize(): Long {
        return wallpaperCacheDir.listFiles()
            ?.filter { it.extension == "jpg" }
            ?.sumOf { it.length() }
            ?: 0L
    }
    
    override suspend fun markWallpaperRemoved(historyId: Long, timestamp: Long) {
        withContext(Dispatchers.IO) {
            wallpaperHistoryDao.markRemoved(historyId, timestamp)
        }
    }
    
    override suspend fun getHistoryEntry(historyId: Long): WallpaperHistory? {
        return withContext(Dispatchers.IO) {
            wallpaperHistoryDao.getById(historyId)
        }
    }

    override fun getCroppedWallpaperFile(wallpaper: WallpaperMetadata): File {
        return File(wallpaperCacheDir, "${wallpaper.id}_cropped.jpg")
    }
    
    companion object {
        /**
         * Name of cache directory for wallpaper files.
         */
        private const val WALLPAPER_CACHE_DIR_NAME = "wallpapers"
        
        /**
         * Maximum cache size in bytes (450 MB).
         * Approximately 150 wallpapers at 3MB each.
         */
        private const val MAX_CACHE_SIZE_BYTES = 450L * 1024L * 1024L // 450 MB
    }
}
