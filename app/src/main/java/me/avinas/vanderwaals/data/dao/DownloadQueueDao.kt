package me.avinas.vanderwaals.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.avinas.vanderwaals.data.entity.DownloadQueueItem

/**
 * DAO for managing the wallpaper download queue.
 * Tracks priority (similarity score), download status, and retry counts.
 * Maintains up to 50 items, re-ranked after feedback events.
 */
@Dao
interface DownloadQueueDao {
    
    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<DownloadQueueItem>)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: DownloadQueueItem)
    
    @Query("SELECT * FROM download_queue ORDER BY priority DESC")
    fun getQueue(): Flow<List<DownloadQueueItem>>
    
    @Query("SELECT * FROM download_queue ORDER BY priority DESC")
    suspend fun getQueueOnce(): List<DownloadQueueItem>
    
    /** Primary query for the background download worker. */
    @Query("""
        SELECT * FROM download_queue 
        WHERE downloaded = 0 
        ORDER BY priority DESC 
        LIMIT :limit
    """)
    suspend fun getTopUndownloaded(limit: Int): List<DownloadQueueItem>
    
    @Query("SELECT * FROM download_queue WHERE downloaded = 1 ORDER BY priority DESC")
    fun getDownloaded(): Flow<List<DownloadQueueItem>>
    
    @Query("SELECT * FROM download_queue WHERE wallpaperId = :wallpaperId")
    suspend fun getByWallpaperId(wallpaperId: String): DownloadQueueItem?
    
    @Query("""
        SELECT * FROM download_queue 
        WHERE downloaded = 0 AND retryCount < 3
        ORDER BY priority DESC
    """)
    suspend fun getRetryableItems(): List<DownloadQueueItem>
    
    @Query("""
        SELECT * FROM download_queue 
        WHERE downloaded = 0 AND retryCount >= 3
        ORDER BY priority DESC
    """)
    suspend fun getFailedItems(): List<DownloadQueueItem>
    
    @Query("UPDATE download_queue SET downloaded = 1 WHERE wallpaperId = :wallpaperId")
    suspend fun markDownloaded(wallpaperId: String)
    
    @Query("UPDATE download_queue SET retryCount = retryCount + 1 WHERE wallpaperId = :wallpaperId")
    suspend fun incrementRetryCount(wallpaperId: String)
    
    @Query("UPDATE download_queue SET retryCount = 0 WHERE wallpaperId = :wallpaperId")
    suspend fun resetRetryCount(wallpaperId: String)
    
    /** Fine-grained priority update without re-inserting the whole queue. */
    @Query("UPDATE download_queue SET priority = :priority WHERE wallpaperId = :wallpaperId")
    suspend fun updatePriority(wallpaperId: String, priority: Float)
    
    @Update
    suspend fun update(item: DownloadQueueItem)
    
    @Query("DELETE FROM download_queue WHERE wallpaperId = :wallpaperId")
    suspend fun delete(wallpaperId: String)
    
    @Transaction
    @Query("DELETE FROM download_queue WHERE downloaded = 1")
    suspend fun deleteDownloaded()
    
    @Query("DELETE FROM download_queue WHERE retryCount >= 3")
    suspend fun deleteFailed()
    
    @Query("DELETE FROM download_queue WHERE priority < :threshold")
    suspend fun deleteBelowThreshold(threshold: Float)
    
    @Query("DELETE FROM download_queue")
    suspend fun deleteAll()
    
    @Query("""
        DELETE FROM download_queue 
        WHERE wallpaperId NOT IN (
            SELECT wallpaperId FROM download_queue 
            ORDER BY priority DESC 
            LIMIT :limit
        )
    """)
    suspend fun keepTopN(limit: Int)
    
    @Query("SELECT COUNT(*) FROM download_queue")
    suspend fun getCount(): Int
    
    @Query("SELECT COUNT(*) FROM download_queue WHERE downloaded = 1")
    suspend fun getDownloadedCount(): Int
    
    @Query("SELECT COUNT(*) FROM download_queue WHERE downloaded = 0")
    suspend fun getPendingCount(): Int
    
    @Query("SELECT EXISTS(SELECT 1 FROM download_queue WHERE wallpaperId = :wallpaperId)")
    suspend fun isInQueue(wallpaperId: String): Boolean
    
    @Query("SELECT downloaded FROM download_queue WHERE wallpaperId = :wallpaperId")
    suspend fun isDownloaded(wallpaperId: String): Boolean?
}
