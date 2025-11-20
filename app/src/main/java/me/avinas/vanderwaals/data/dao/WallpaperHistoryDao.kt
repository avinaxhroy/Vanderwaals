package me.avinas.vanderwaals.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.avinas.vanderwaals.data.entity.WallpaperHistory

data class FeedbackStat(
    val userFeedback: String,
    val count: Int
)

/**
 * Room DAO for managing wallpaper application history.
 * 
 * Provides queries for:
 * - Recording when wallpapers are applied/removed
 * - Tracking user feedback (likes/dislikes)
 * - Displaying history in UI
 * - Learning from implicit feedback (duration)
 * - Preventing duplicate wallpapers in rotation
 * 
 * **Auto-cleanup:**
 * Automatically maintains only the last 100 history entries per device.
 * Older entries are deleted to prevent database bloat.
 * 
 * **Usage:**
 * ```kotlin
 * // Record wallpaper application
 * val historyId = dao.insert(
 *     WallpaperHistory(
 *         wallpaperId = "wall123",
 *         appliedAt = System.currentTimeMillis(),
 *         removedAt = null,
 *         userFeedback = null,
 *         downloadedToStorage = false
 *     )
 * )
 * 
 * // User likes the wallpaper
 * dao.setFeedback(historyId, "like")
 * 
 * // Wallpaper is removed
 * dao.markRemoved(historyId, System.currentTimeMillis())
 * 
 * // Display history in UI
 * dao.getHistory().collect { history ->
 *     displayHistory(history)
 * }
 * ```
 * 
 * @see me.avinas.vanderwaals.data.entity.WallpaperHistory
 */
@Dao
interface WallpaperHistoryDao {
    
    /**
     * Inserts a new history entry when a wallpaper is applied.
     * 
     * Returns the auto-generated ID which can be used for later updates
     * (setting feedback, marking removed).
     * 
     * @param history History entry to insert
     * @return Auto-generated ID of the inserted row
     * 
     * Example:
     * ```kotlin
     * val id = dao.insert(
     *     WallpaperHistory(
     *         wallpaperId = wallpaperId,
     *         appliedAt = System.currentTimeMillis(),
     *         removedAt = null,
     *         userFeedback = null,
     *         downloadedToStorage = false
     *     )
     * )
     * // Store this ID to update later
     * currentWallpaperHistoryId = id
     * ```
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: WallpaperHistory): Long
    
    /**
     * Inserts multiple history entries.
     * 
     * @param histories List of history entries to insert
     * @return List of auto-generated IDs
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(histories: List<WallpaperHistory>): List<Long>
    
    /**
     * Retrieves wallpaper history ordered by application time (newest first).
     * 
     * Returns a Flow that emits the latest 100 history entries whenever
     * the database is updated. Perfect for displaying in the History UI screen.
     * 
     * **Auto-limited:** Returns maximum 100 entries to match cleanup policy.
     * 
     * @return Flow emitting list of history entries (max 100)
     * 
     * Example:
     * ```kotlin
     * dao.getHistory().collect { history ->
     *     historyList.value = history.groupBy { entry ->
     *         formatTimestamp(entry.appliedAt) // "Today", "Yesterday", etc.
     *     }
     * }
     * ```
     */
    @Query("SELECT * FROM wallpaper_history ORDER BY appliedAt DESC LIMIT 100")
    fun getHistory(): Flow<List<WallpaperHistory>>
    
    /**
     * Retrieves history as a one-shot list (non-reactive).
     * 
     * Use for background processing where Flow isn't needed.
     * 
     * @return List of history entries (max 100)
     */
    @Query("SELECT * FROM wallpaper_history ORDER BY appliedAt DESC LIMIT 100")
    suspend fun getHistoryOnce(): List<WallpaperHistory>
    
    /**
     * Retrieves the currently active wallpaper (not yet removed).
     * 
     * Returns the history entry with removedAt = null, representing
     * the wallpaper currently displayed.
     * 
     * @return Active wallpaper history entry or null if none active
     * 
     * Example:
     * ```kotlin
     * val active = dao.getActiveWallpaper()
     * if (active != null) {
     *     // Wallpaper has been active for: System.currentTimeMillis() - active.appliedAt
     * }
     * ```
     */
    @Query("SELECT * FROM wallpaper_history WHERE removedAt IS NULL ORDER BY appliedAt DESC LIMIT 1")
    suspend fun getActiveWallpaper(): WallpaperHistory?
    
    /**
     * Retrieves the currently active wallpaper as a reactive Flow.
     * 
     * Emits whenever the active wallpaper changes (new wallpaper applied, current removed, etc.).
     * Returns null if no wallpaper is currently active.
     * 
     * @return Flow emitting the active WallpaperHistory or null
     * 
     * Example:
     * ```kotlin
     * dao.getActiveWallpaperFlow().collect { active ->
     *     if (active != null) {
     *         // Show active wallpaper
     *     } else {
     *         // Show "no wallpaper set" placeholder
     *     }
     * }
     * ```
     */
    @Query("SELECT * FROM wallpaper_history WHERE removedAt IS NULL ORDER BY appliedAt DESC LIMIT 1")
    fun getActiveWallpaperFlow(): Flow<WallpaperHistory?>
    
    /**
     * Retrieves a specific history entry by ID.
     * 
     * @param id History entry ID
     * @return History entry or null if not found
     */
    @Query("SELECT * FROM wallpaper_history WHERE id = :id")
    suspend fun getById(id: Long): WallpaperHistory?
    
    /**
     * Retrieves all history entries for a specific wallpaper.
     * 
     * Shows how many times a wallpaper has been applied and user's
     * feedback history for it.
     * 
     * @param wallpaperId Wallpaper ID
     * @return List of history entries for this wallpaper
     */
    @Query("SELECT * FROM wallpaper_history WHERE wallpaperId = :wallpaperId ORDER BY appliedAt DESC")
    suspend fun getByWallpaperId(wallpaperId: String): List<WallpaperHistory>
    
    /**
     * Checks if a wallpaper has been applied before.
     * 
     * Used to prevent showing the same wallpaper too frequently.
     * 
     * @param wallpaperId Wallpaper ID
     * @return true if wallpaper has been applied before, false otherwise
     * 
     * Example:
     * ```kotlin
     * // Filter out recently used wallpapers
     * val candidates = allWallpapers.filterNot { wallpaper ->
     *     dao.hasBeenApplied(wallpaper.id)
     * }
     * ```
     */
    @Query("SELECT EXISTS(SELECT 1 FROM wallpaper_history WHERE wallpaperId = :wallpaperId)")
    suspend fun hasBeenApplied(wallpaperId: String): Boolean
    
    /**
     * Retrieves history entries with explicit feedback (liked or disliked).
     * 
     * Used for learning user preferences and category tracking.
     * 
     * @return List of entries where userFeedback is not null
     */
    @Query("SELECT * FROM wallpaper_history WHERE userFeedback IS NOT NULL ORDER BY appliedAt DESC")
    suspend fun getEntriesWithFeedback(): List<WallpaperHistory>
    
    /**
     * Retrieves history entries that were downloaded to storage.
     * 
     * @return Flow emitting list of downloaded wallpapers
     */
    @Query("SELECT * FROM wallpaper_history WHERE downloadedToStorage = 1 ORDER BY appliedAt DESC")
    fun getDownloadedWallpapers(): Flow<List<WallpaperHistory>>
    
    /**
     * Marks a history entry as removed at the specified timestamp.
     * 
     * Updates the removedAt field when user changes the wallpaper.
     * This enables duration calculation for implicit feedback.
     * 
     * @param id History entry ID
     * @param timestamp Removal timestamp in milliseconds
     * 
     * Example:
     * ```kotlin
     * // When changing wallpaper
     * val currentHistoryId = getCurrentWallpaperHistoryId()
     * dao.markRemoved(currentHistoryId, System.currentTimeMillis())
     * 
     * // Then insert new history entry
     * dao.insert(WallpaperHistory(...))
     * ```
     */
    @Query("UPDATE wallpaper_history SET removedAt = :timestamp WHERE id = :id")
    suspend fun markRemoved(id: Long, timestamp: Long)
    
    /**
     * Sets or updates the user feedback for a history entry.
     * 
     * @param id History entry ID
     * @param feedback Feedback type ("like" or "dislike")
     * 
     * Example:
     * ```kotlin
     * // User taps heart icon
     * dao.setFeedback(historyId, WallpaperHistory.FEEDBACK_LIKE)
     * 
     * // User taps dislike icon
     * dao.setFeedback(historyId, WallpaperHistory.FEEDBACK_DISLIKE)
     * ```
     */
    @Query("UPDATE wallpaper_history SET userFeedback = :feedback WHERE id = :id")
    suspend fun setFeedback(id: Long, feedback: String)
    
    /**
     * Sets user feedback and contextual information for a history entry.
     * 
     * Use this method to record feedback with context (time, battery, brightness).
     * Enables future contextual recommendations.
     * 
     * @param id History entry ID
     * @param feedback Feedback type ("like" or "dislike")
     * @param feedbackContext JSON string of FeedbackContext (use Converters.fromFeedbackContext)
     * 
     * Example:
     * ```kotlin
     * val context = FeedbackContext.fromCurrentState(androidContext)
     * val contextJson = converters.fromFeedbackContext(context)
     * dao.setFeedbackWithContext(historyId, WallpaperHistory.FEEDBACK_LIKE, contextJson)
     * ```
     */
    @Query("UPDATE wallpaper_history SET userFeedback = :feedback, feedbackContext = :feedbackContext WHERE id = :id")
    suspend fun setFeedbackWithContext(id: Long, feedback: String, feedbackContext: String?)
    
    /**
     * Marks a wallpaper as downloaded to storage.
     * 
     * @param id History entry ID
     */
    @Query("UPDATE wallpaper_history SET downloadedToStorage = 1 WHERE id = :id")
    suspend fun markDownloaded(id: Long)
    
    /**
     * Updates an existing history entry.
     * 
     * Use this for complex updates that modify multiple fields.
     * 
     * @param history Updated history entry
     * 
     * Example:
     * ```kotlin
     * val existing = dao.getById(id) ?: return
     * val updated = existing.copy(
     *     removedAt = System.currentTimeMillis(),
     *     userFeedback = "like",
     *     downloadedToStorage = true
     * )
     * dao.update(updated)
     * ```
     */
    @Update
    suspend fun update(history: WallpaperHistory)
    
    /**
     * Deletes a specific history entry.
     * 
     * @param id History entry ID
     */
    @Query("DELETE FROM wallpaper_history WHERE id = :id")
    suspend fun delete(id: Long)
    
    /**
     * Deletes all history entries for a specific wallpaper.
     * 
     * @param wallpaperId Wallpaper ID
     */
    @Query("DELETE FROM wallpaper_history WHERE wallpaperId = :wallpaperId")
    suspend fun deleteByWallpaperId(wallpaperId: String)
    
    /**
     * Cleans up old history entries, keeping only the latest 100.
     * 
     * Should be called periodically (e.g., after each new insertion)
     * to maintain the 100-entry limit.
     * 
     * Example:
     * ```kotlin
     * // After inserting new entry
     * dao.insert(newHistory)
     * dao.cleanupOldEntries()
     * ```
     */
    @Query("""
        DELETE FROM wallpaper_history 
        WHERE id NOT IN (
            SELECT id FROM wallpaper_history 
            ORDER BY appliedAt DESC 
            LIMIT 100
        )
    """)
    suspend fun cleanupOldEntries()
    
    /**
     * Deletes all history entries.
     * 
     * Used for testing or complete app data reset.
     */
    @Query("DELETE FROM wallpaper_history")
    suspend fun deleteAll()
    
    /**
     * Gets the total count of history entries.
     * 
     * @return Total number of history entries
     */
    @Query("SELECT COUNT(*) FROM wallpaper_history")
    suspend fun getCount(): Int
    
    /**
     * Gets statistics about user feedback.
     * 
     * Returns a map of feedback types to counts.
     * 
     * @return Flow emitting feedback statistics
     */
    @Query("""
        SELECT userFeedback, COUNT(*) as count 
        FROM wallpaper_history 
        WHERE userFeedback IS NOT NULL 
        GROUP BY userFeedback
    """)
    suspend fun getFeedbackStats(): List<FeedbackStat>
}

