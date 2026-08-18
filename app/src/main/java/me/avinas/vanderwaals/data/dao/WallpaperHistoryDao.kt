package me.avinas.vanderwaals.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.avinas.vanderwaals.data.entity.WallpaperHistory

data class FeedbackStat(
    val userFeedback: String,
    val count: Int
)

/**
 * DAO for wallpaper application history.
 * Tracks applied/removed timestamps, user feedback, and implicit duration signals.
 * Auto-cleans entries beyond 100 per device.
 */
@Dao
interface WallpaperHistoryDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: WallpaperHistory): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(histories: List<WallpaperHistory>): List<Long>
    
    @Query("SELECT * FROM wallpaper_history ORDER BY appliedAt DESC LIMIT 100")
    fun getHistory(): Flow<List<WallpaperHistory>>
    
    @Query("SELECT * FROM wallpaper_history ORDER BY appliedAt DESC LIMIT 100")
    suspend fun getHistoryOnce(): List<WallpaperHistory>
    
    @Query("SELECT * FROM wallpaper_history WHERE removedAt IS NULL ORDER BY appliedAt DESC LIMIT 1")
    suspend fun getActiveWallpaper(): WallpaperHistory?
    
    @Query("SELECT * FROM wallpaper_history WHERE removedAt IS NULL ORDER BY appliedAt DESC LIMIT 1")
    fun getActiveWallpaperFlow(): Flow<WallpaperHistory?>
    
    @Query("SELECT * FROM wallpaper_history WHERE id = :id")
    suspend fun getById(id: Long): WallpaperHistory?
    
    @Query("SELECT * FROM wallpaper_history WHERE wallpaperId = :wallpaperId ORDER BY appliedAt DESC")
    suspend fun getByWallpaperId(wallpaperId: String): List<WallpaperHistory>
    
    @Query("SELECT EXISTS(SELECT 1 FROM wallpaper_history WHERE wallpaperId = :wallpaperId)")
    suspend fun hasBeenApplied(wallpaperId: String): Boolean
    
    @Query("SELECT * FROM wallpaper_history WHERE userFeedback IS NOT NULL ORDER BY appliedAt DESC")
    suspend fun getEntriesWithFeedback(): List<WallpaperHistory>
    
    @Query("SELECT * FROM wallpaper_history WHERE downloadedToStorage = 1 ORDER BY appliedAt DESC")
    fun getDownloadedWallpapers(): Flow<List<WallpaperHistory>>
    
    /** Enables duration calculation for implicit feedback. */
    @Query("UPDATE wallpaper_history SET removedAt = :timestamp WHERE id = :id")
    suspend fun markRemoved(id: Long, timestamp: Long)
    
    @Transaction
    @Query("UPDATE wallpaper_history SET userFeedback = :feedback WHERE id = :id")
    suspend fun setFeedback(id: Long, feedback: String)
    
    @Transaction
    @Query("UPDATE wallpaper_history SET userFeedback = :feedback, feedbackContext = :feedbackContext WHERE id = :id")
    suspend fun setFeedbackWithContext(id: Long, feedback: String, feedbackContext: String?)
    
    @Query("UPDATE wallpaper_history SET downloadedToStorage = 1 WHERE id = :id")
    suspend fun markDownloaded(id: Long)
    
    @Update
    suspend fun update(history: WallpaperHistory)
    
    @Query("DELETE FROM wallpaper_history WHERE id = :id")
    suspend fun delete(id: Long)
    
    @Query("DELETE FROM wallpaper_history WHERE wallpaperId = :wallpaperId")
    suspend fun deleteByWallpaperId(wallpaperId: String)
    
    @Query("""
        DELETE FROM wallpaper_history 
        WHERE id NOT IN (
            SELECT id FROM wallpaper_history 
            ORDER BY appliedAt DESC 
            LIMIT 100
        )
    """)
    suspend fun cleanupOldEntries()
    
    @Query("DELETE FROM wallpaper_history")
    suspend fun deleteAll()
    
    @Query("SELECT COUNT(*) FROM wallpaper_history")
    suspend fun getCount(): Int
    
    @Query("""
        SELECT userFeedback, COUNT(*) as count 
        FROM wallpaper_history 
        WHERE userFeedback IS NOT NULL 
        GROUP BY userFeedback
    """)
    suspend fun getFeedbackStats(): List<FeedbackStat>
}

