package me.avinas.vanderwaals.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import me.avinas.vanderwaals.data.entity.CategoryPreference

/**
 * Data Access Object for category preference tracking.
 * 
 * Provides queries to:
 * - Track user preferences for wallpaper categories
 * - Record likes, dislikes, and views per category
 * - Calculate category scores for personalization
 * - Identify underexplored categories
 * 
 * **Usage in Algorithm:**
 * 1. Increment views when wallpaper shown
 * 2. Increment likes/dislikes on user feedback
 * 3. Use scores to boost/penalize categories during selection
 * 4. Explore underexplored categories periodically
 * 
 * **Example:**
 * ```kotlin
 * // When showing wallpaper
 * categoryPreferenceDao.incrementViews(wallpaper.category)
 * 
 * // When user likes wallpaper
 * categoryPreferenceDao.incrementLikes(wallpaper.category)
 * 
 * // Get category score for ranking
 * val score = categoryPreferenceDao.getByCategory(category)?.calculateScore() ?: 0f
 * ```
 * 
 * @see CategoryPreference
 */
@Dao
interface CategoryPreferenceDao {
    
    /**
     * Gets category preference by category name.
     * 
     * @param category Category name (e.g., "nature", "minimal")
     * @return CategoryPreference if exists, null otherwise
     */
    @Query("SELECT * FROM category_preferences WHERE category = :category")
    suspend fun getByCategory(category: String): CategoryPreference?
    
    /**
     * Gets category preference as Flow for reactive updates.
     * 
     * @param category Category name
     * @return Flow of CategoryPreference (null if doesn't exist)
     */
    @Query("SELECT * FROM category_preferences WHERE category = :category")
    fun getByCategoryFlow(category: String): Flow<CategoryPreference?>
    
    /**
     * Gets all category preferences.
     * 
     * @return List of all tracked categories
     */
    @Query("SELECT * FROM category_preferences ORDER BY category ASC")
    suspend fun getAll(): List<CategoryPreference>
    
    /**
     * Gets all category preferences as Flow.
     * 
     * @return Flow of all category preferences
     */
    @Query("SELECT * FROM category_preferences ORDER BY category ASC")
    fun getAllFlow(): Flow<List<CategoryPreference>>
    
    /**
     * Gets categories sorted by preference score.
     * 
     * Note: Score calculation done in-memory as Room doesn't support
     * custom functions in ORDER BY. Use for small result sets.
     * 
     * @return List of categories sorted by score (high to low)
     */
    @Query("SELECT * FROM category_preferences")
    suspend fun getAllByScore(): List<CategoryPreference>
    
    /**
     * Gets underexplored categories (views < 3).
     * 
     * @return List of categories that need more exploration
     */
    @Query("SELECT * FROM category_preferences WHERE views < 3")
    suspend fun getUnderexplored(): List<CategoryPreference>
    
    /**
     * Gets recently shown categories (within last 24 hours).
     * 
     * @param since Timestamp (milliseconds) to filter from
     * @return List of recently shown categories
     */
    @Query("SELECT * FROM category_preferences WHERE lastShown >= :since ORDER BY lastShown DESC")
    suspend fun getRecentlyShown(since: Long): List<CategoryPreference>
    
    /**
     * Inserts or replaces a category preference.
     * 
     * @param categoryPreference Preference to insert/update
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(categoryPreference: CategoryPreference)
    
    /**
     * Inserts or replaces multiple category preferences.
     * 
     * @param categoryPreferences List of preferences to insert/update
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categoryPreferences: List<CategoryPreference>)
    
    /**
     * Increments view count for a category.
     * Creates category if doesn't exist.
     * 
     * @param category Category name
     */
    @Transaction
    suspend fun incrementViews(category: String) {
        val current = getByCategory(category) ?: CategoryPreference(category = category)
        insert(current.copy(
            views = current.views + 1,
            lastShown = System.currentTimeMillis()
        ))
    }
    
    /**
     * Increments like count for a category.
     * Creates category if doesn't exist.
     * 
     * @param category Category name
     */
    @Transaction
    suspend fun incrementLikes(category: String) {
        val current = getByCategory(category) ?: CategoryPreference(category = category)
        insert(current.copy(likes = current.likes + 1))
    }
    
    /**
     * Increments dislike count for a category.
     * Creates category if doesn't exist.
     * 
     * @param category Category name
     */
    @Transaction
    suspend fun incrementDislikes(category: String) {
        val current = getByCategory(category) ?: CategoryPreference(category = category)
        insert(current.copy(dislikes = current.dislikes + 1))
    }
    
    /**
     * Updates last shown timestamp for a category.
     * 
     * @param category Category name
     * @param timestamp Timestamp (milliseconds)
     */
    @Query("UPDATE category_preferences SET lastShown = :timestamp WHERE category = :category")
    suspend fun updateLastShown(category: String, timestamp: Long)
    
    /**
     * Deletes a specific category preference.
     * 
     * @param category Category name
     */
    @Query("DELETE FROM category_preferences WHERE category = :category")
    suspend fun delete(category: String)
    
    /**
     * Deletes all category preferences.
     * Useful for reset/testing.
     */
    @Query("DELETE FROM category_preferences")
    suspend fun deleteAll()
    
    /**
     * Gets total number of tracked categories.
     * 
     * @return Count of categories
     */
    @Query("SELECT COUNT(*) FROM category_preferences")
    suspend fun getCount(): Int
}
