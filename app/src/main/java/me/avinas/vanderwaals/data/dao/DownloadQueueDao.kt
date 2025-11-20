package me.avinas.vanderwaals.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.avinas.vanderwaals.data.entity.DownloadQueueItem

/**
 * Room DAO for managing the wallpaper download queue.
 * 
 * Provides queries for:
 * - Populating the queue based on similarity scores
 * - Retrieving top-priority wallpapers for download
 * - Updating download status and retry counts
 * - Re-ranking the queue after user feedback
 * 
 * **Queue Management:**
 * - Maintains top 50 wallpapers based on priority (similarity score)
 * - Automatically handles failed downloads with exponential backoff
 * - Re-ranks after each feedback event to reflect updated preferences
 * 
 * **Usage:**
 * ```kotlin
 * // Populate queue with top matches
 * val topMatches = calculateSimilarities(allWallpapers, preferenceVector)
 *     .take(50)
 *     .map { DownloadQueueItem.create(it.id, it.similarity) }
 * dao.insertAll(topMatches)
 * 
 * // Get wallpapers to download
 * val toDownload = dao.getTopUndownloaded(limit = 10)
 * downloadWallpapers(toDownload)
 * 
 * // Mark as downloaded
 * dao.markDownloaded(wallpaperId)
 * ```
 * 
 * @see me.avinas.vanderwaals.data.entity.DownloadQueueItem
 */
@Dao
interface DownloadQueueDao {
    
    /**
     * Inserts or replaces a list of download queue items.
     * 
     * Uses REPLACE strategy to update priorities when re-ranking the queue
     * after user feedback or preference updates.
     * 
     * **Re-ranking:** Call this after each feedback event with updated priorities.
     * 
     * @param items List of queue items to insert/update
     * 
     * Example:
     * ```kotlin
     * // After user feedback, recalculate similarities
     * val updatedPreferences = preferencesDao.getOnce() ?: return
     * val allWallpapers = metadataDao.getAllOnce()
     * 
     * val reranked = allWallpapers
     *     .map { wallpaper ->
     *         val similarity = cosineSimilarity(
     *             updatedPreferences.preferenceVector,
     *             wallpaper.embedding
     *         )
     *         DownloadQueueItem.create(wallpaper.id, similarity)
     *     }
     *     .sortedByDescending { it.priority }
     *     .take(50)
     * 
     * dao.deleteAll()
     * dao.insertAll(reranked)
     * ```
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<DownloadQueueItem>)
    
    /**
     * Inserts or replaces a single queue item.
     * 
     * @param item Queue item to insert/update
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: DownloadQueueItem)
    
    /**
     * Retrieves the entire download queue as a reactive Flow.
     * 
     * Ordered by priority (descending) so highest-priority wallpapers
     * are first in the list.
     * 
     * @return Flow emitting list of queue items ordered by priority
     */
    @Query("SELECT * FROM download_queue ORDER BY priority DESC")
    fun getQueue(): Flow<List<DownloadQueueItem>>
    
    /**
     * Retrieves the entire queue as a one-shot list (non-reactive).
     * 
     * @return List of queue items ordered by priority
     */
    @Query("SELECT * FROM download_queue ORDER BY priority DESC")
    suspend fun getQueueOnce(): List<DownloadQueueItem>
    
    /**
     * Retrieves top N undownloaded wallpapers ordered by priority.
     * 
     * This is the primary query for the background download worker.
     * Returns wallpapers that haven't been downloaded yet, sorted by
     * similarity score (priority).
     * 
     * @param limit Maximum number of items to return
     * @return List of undownloaded queue items (max [limit])
     * 
     * Example:
     * ```kotlin
     * // In DownloadWorker
     * val toDownload = dao.getTopUndownloaded(limit = 10)
     * toDownload.forEach { item ->
     *     if (item.shouldRetry()) {
     *         downloadWallpaper(item.wallpaperId)
     *     }
     * }
     * ```
     */
    @Query("""
        SELECT * FROM download_queue 
        WHERE downloaded = 0 
        ORDER BY priority DESC 
        LIMIT :limit
    """)
    suspend fun getTopUndownloaded(limit: Int): List<DownloadQueueItem>
    
    /**
     * Retrieves downloaded wallpapers from the queue.
     * 
     * @return Flow emitting list of downloaded queue items
     */
    @Query("SELECT * FROM download_queue WHERE downloaded = 1 ORDER BY priority DESC")
    fun getDownloaded(): Flow<List<DownloadQueueItem>>
    
    /**
     * Retrieves a specific queue item by wallpaper ID.
     * 
     * @param wallpaperId Wallpaper ID
     * @return Queue item or null if not in queue
     */
    @Query("SELECT * FROM download_queue WHERE wallpaperId = :wallpaperId")
    suspend fun getByWallpaperId(wallpaperId: String): DownloadQueueItem?
    
    /**
     * Retrieves queue items that are ready for retry.
     * 
     * Returns items that:
     * - Are not downloaded (downloaded = false)
     * - Have retry count < MAX_RETRY_COUNT (3)
     * 
     * @return List of items ready for retry
     * 
     * Example:
     * ```kotlin
     * val retryItems = dao.getRetryableItems()
     * retryItems.forEach { item ->
     *     val delay = item.getRetryDelayMs()
     *     scheduleRetry(item.wallpaperId, delay)
     * }
     * ```
     */
    @Query("""
        SELECT * FROM download_queue 
        WHERE downloaded = 0 AND retryCount < 3
        ORDER BY priority DESC
    """)
    suspend fun getRetryableItems(): List<DownloadQueueItem>
    
    /**
     * Retrieves queue items that have exceeded max retries.
     * 
     * These items should be marked as failed and potentially removed
     * from the queue or flagged for manual intervention.
     * 
     * @return List of failed items (retryCount >= 3)
     */
    @Query("""
        SELECT * FROM download_queue 
        WHERE downloaded = 0 AND retryCount >= 3
        ORDER BY priority DESC
    """)
    suspend fun getFailedItems(): List<DownloadQueueItem>
    
    /**
     * Marks a wallpaper as successfully downloaded.
     * 
     * @param wallpaperId Wallpaper ID
     * 
     * Example:
     * ```kotlin
     * // After successful download
     * downloadWallpaper(wallpaperId)
     * dao.markDownloaded(wallpaperId)
     * ```
     */
    @Query("UPDATE download_queue SET downloaded = 1 WHERE wallpaperId = :wallpaperId")
    suspend fun markDownloaded(wallpaperId: String)
    
    /**
     * Increments the retry count for a failed download.
     * 
     * Call this when a download fails so the worker can apply
     * exponential backoff on the next attempt.
     * 
     * @param wallpaperId Wallpaper ID
     * 
     * Example:
     * ```kotlin
     * try {
     *     downloadWallpaper(wallpaperId)
     *     dao.markDownloaded(wallpaperId)
     * } catch (e: Exception) {
     *     dao.incrementRetryCount(wallpaperId)
     *     val item = dao.getByWallpaperId(wallpaperId)
     *     if (item?.shouldRetry() == true) {
     *         scheduleRetry(wallpaperId, item.getRetryDelayMs())
     *     }
     * }
     * ```
     */
    @Query("UPDATE download_queue SET retryCount = retryCount + 1 WHERE wallpaperId = :wallpaperId")
    suspend fun incrementRetryCount(wallpaperId: String)
    
    /**
     * Resets the retry count for a wallpaper.
     * 
     * Useful after network conditions improve or app is restarted.
     * 
     * @param wallpaperId Wallpaper ID
     */
    @Query("UPDATE download_queue SET retryCount = 0 WHERE wallpaperId = :wallpaperId")
    suspend fun resetRetryCount(wallpaperId: String)
    
    /**
     * Updates the priority (similarity score) for a queue item.
     * 
     * Used for fine-grained priority updates without re-inserting
     * the entire queue.
     * 
     * @param wallpaperId Wallpaper ID
     * @param priority New priority value (0.0 to 1.0)
     */
    @Query("UPDATE download_queue SET priority = :priority WHERE wallpaperId = :wallpaperId")
    suspend fun updatePriority(wallpaperId: String, priority: Float)
    
    /**
     * Updates an existing queue item.
     * 
     * Use for complex updates that modify multiple fields.
     * 
     * @param item Updated queue item
     */
    @Update
    suspend fun update(item: DownloadQueueItem)
    
    /**
     * Deletes a queue item by wallpaper ID.
     * 
     * @param wallpaperId Wallpaper ID
     */
    @Query("DELETE FROM download_queue WHERE wallpaperId = :wallpaperId")
    suspend fun delete(wallpaperId: String)
    
    /**
     * Deletes all downloaded items from the queue.
     * 
     * Use to clean up the queue after wallpapers have been successfully
     * downloaded and cached.
     */
    @Query("DELETE FROM download_queue WHERE downloaded = 1")
    suspend fun deleteDownloaded()
    
    /**
     * Deletes items that have failed too many times.
     * 
     * Removes items with retryCount >= max retries.
     */
    @Query("DELETE FROM download_queue WHERE retryCount >= 3")
    suspend fun deleteFailed()
    
    /**
     * Deletes items below a priority threshold.
     * 
     * Used to remove low-priority wallpapers from the queue when
     * re-ranking results in better matches.
     * 
     * @param threshold Minimum priority to keep (0.0 to 1.0)
     * 
     * Example:
     * ```kotlin
     * // Remove wallpapers with similarity < 0.3
     * dao.deleteBelowThreshold(0.3f)
     * ```
     */
    @Query("DELETE FROM download_queue WHERE priority < :threshold")
    suspend fun deleteBelowThreshold(threshold: Float)
    
    /**
     * Deletes all queue items.
     * 
     * Used when completely re-populating the queue after preference changes.
     * 
     * Example:
     * ```kotlin
     * // Re-populate queue after major preference update
     * dao.deleteAll()
     * val newQueue = calculateTopMatches(updatedPreferences)
     * dao.insertAll(newQueue)
     * ```
     */
    @Query("DELETE FROM download_queue")
    suspend fun deleteAll()
    
    /**
     * Keeps only the top N items in the queue, deletes the rest.
     * 
     * Maintains the queue size limit (typically 50 items).
     * 
     * @param limit Maximum number of items to keep
     * 
     * Example:
     * ```kotlin
     * // After inserting new items, trim to 50
     * dao.keepTopN(50)
     * ```
     */
    @Query("""
        DELETE FROM download_queue 
        WHERE wallpaperId NOT IN (
            SELECT wallpaperId FROM download_queue 
            ORDER BY priority DESC 
            LIMIT :limit
        )
    """)
    suspend fun keepTopN(limit: Int)
    
    /**
     * Gets the total count of items in the queue.
     * 
     * @return Total queue size
     */
    @Query("SELECT COUNT(*) FROM download_queue")
    suspend fun getCount(): Int
    
    /**
     * Gets the count of downloaded items.
     * 
     * @return Number of downloaded wallpapers
     */
    @Query("SELECT COUNT(*) FROM download_queue WHERE downloaded = 1")
    suspend fun getDownloadedCount(): Int
    
    /**
     * Gets the count of pending downloads.
     * 
     * @return Number of wallpapers waiting to be downloaded
     */
    @Query("SELECT COUNT(*) FROM download_queue WHERE downloaded = 0")
    suspend fun getPendingCount(): Int
    
    /**
     * Checks if a wallpaper is in the queue.
     * 
     * @param wallpaperId Wallpaper ID
     * @return true if wallpaper is in queue, false otherwise
     */
    @Query("SELECT EXISTS(SELECT 1 FROM download_queue WHERE wallpaperId = :wallpaperId)")
    suspend fun isInQueue(wallpaperId: String): Boolean
    
    /**
     * Checks if a wallpaper has been downloaded.
     * 
     * @param wallpaperId Wallpaper ID
     * @return true if downloaded, false otherwise
     */
    @Query("SELECT downloaded FROM download_queue WHERE wallpaperId = :wallpaperId")
    suspend fun isDownloaded(wallpaperId: String): Boolean?
}
