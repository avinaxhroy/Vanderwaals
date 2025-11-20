package me.avinas.vanderwaals.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import me.avinas.vanderwaals.data.entity.ColorPreference

/**
 * Data Access Object for color preference tracking.
 * 
 * Provides queries to:
 * - Track user preferences for specific colors (hex codes)
 * - Record likes, dislikes, and views per color
 * - Calculate color scores for personalization
 * - Enable fallback personalization when categories are missing
 * 
 * **Usage in Algorithm:**
 * 1. Extract top 3 colors from wallpaper metadata
 * 2. Increment views when wallpaper shown
 * 3. Increment likes/dislikes on user feedback for all colors in palette
 * 4. Use color similarity to boost/penalize wallpapers without categories
 * 5. Apply lower weight (10%) than category boost (15%)
 * 
 * **Example:**
 * ```kotlin
 * // When showing wallpaper with colors ["#FF5733", "#3498DB", "#2ECC71"]
 * wallpaper.colors.forEach { color ->
 *     colorPreferenceDao.incrementViews(color)
 * }
 * 
 * // When user likes wallpaper
 * wallpaper.colors.forEach { color ->
 *     colorPreferenceDao.incrementLikes(color)
 * }
 * 
 * // Get color score for ranking
 * val score = colorPreferenceDao.getByColor(colorHex)?.calculateScore() ?: 0f
 * ```
 * 
 * @see ColorPreference
 */
@Dao
interface ColorPreferenceDao {
    
    /**
     * Gets color preference by hex code.
     * 
     * @param colorHex Hex color code (e.g., "#FF5733")
     * @return ColorPreference if exists, null otherwise
     */
    @Query("SELECT * FROM color_preferences WHERE colorHex = :colorHex")
    suspend fun getByColor(colorHex: String): ColorPreference?
    
    /**
     * Gets color preference as Flow for reactive updates.
     * 
     * @param colorHex Hex color code
     * @return Flow of ColorPreference (null if doesn't exist)
     */
    @Query("SELECT * FROM color_preferences WHERE colorHex = :colorHex")
    fun getByColorFlow(colorHex: String): Flow<ColorPreference?>
    
    /**
     * Gets all color preferences.
     * 
     * @return List of all tracked colors
     */
    @Query("SELECT * FROM color_preferences ORDER BY colorHex ASC")
    suspend fun getAll(): List<ColorPreference>
    
    /**
     * Gets all color preferences as Flow.
     * 
     * @return Flow of all color preferences
     */
    @Query("SELECT * FROM color_preferences ORDER BY colorHex ASC")
    fun getAllFlow(): Flow<List<ColorPreference>>
    
    /**
     * Gets colors sorted by preference score (in-memory sorting required).
     * 
     * @return List of colors sorted by calculated score (high to low)
     */
    @Query("SELECT * FROM color_preferences")
    suspend fun getAllByScore(): List<ColorPreference>
    
    /**
     * Gets underexplored colors (views < 3).
     * 
     * @return List of colors that need more exploration
     */
    @Query("SELECT * FROM color_preferences WHERE views < 3")
    suspend fun getUnderexplored(): List<ColorPreference>
    
    /**
     * Gets recently shown colors (within specified time).
     * 
     * @param since Timestamp (milliseconds) to filter from
     * @return List of recently shown colors
     */
    @Query("SELECT * FROM color_preferences WHERE lastShown > :since")
    suspend fun getRecentlyShown(since: Long): List<ColorPreference>
    
    /**
     * Gets liked colors (likes > dislikes).
     * Useful for building user's preferred color palette.
     * 
     * @return List of colors the user tends to like
     */
    @Query("SELECT * FROM color_preferences WHERE likes > dislikes ORDER BY (likes - dislikes) DESC")
    suspend fun getLikedColors(): List<ColorPreference>
    
    /**
     * Gets disliked colors (dislikes > likes).
     * Useful for filtering out colors user tends to dislike.
     * 
     * @return List of colors the user tends to dislike
     */
    @Query("SELECT * FROM color_preferences WHERE dislikes > likes ORDER BY (dislikes - likes) DESC")
    suspend fun getDislikedColors(): List<ColorPreference>
    
    /**
     * Inserts or replaces a color preference.
     * 
     * @param colorPreference Color preference to insert/update
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(colorPreference: ColorPreference)
    
    /**
     * Inserts or replaces multiple color preferences.
     * 
     * @param colorPreferences List of color preferences to insert/update
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(colorPreferences: List<ColorPreference>)
    
    /**
     * Deletes a color preference.
     * 
     * @param colorHex Hex color code to delete
     */
    @Query("DELETE FROM color_preferences WHERE colorHex = :colorHex")
    suspend fun delete(colorHex: String)
    
    /**
     * Deletes all color preferences.
     * Useful for reset functionality.
     */
    @Query("DELETE FROM color_preferences")
    suspend fun deleteAll()
    
    /**
     * Increments view count and updates last shown timestamp for a color.
     * Creates entry with views=1 if color doesn't exist.
     * 
     * @param colorHex Hex color code being viewed
     */
    @Transaction
    suspend fun incrementViews(colorHex: String) {
        val existing = getByColor(colorHex)
        if (existing != null) {
            insert(
                existing.copy(
                    views = existing.views + 1,
                    lastShown = System.currentTimeMillis()
                )
            )
        } else {
            insert(
                ColorPreference(
                    colorHex = colorHex,
                    views = 1,
                    lastShown = System.currentTimeMillis()
                )
            )
        }
    }
    
    /**
     * Increments like count for a color.
     * Also increments views and updates last shown.
     * Creates entry with likes=1, views=1 if color doesn't exist.
     * 
     * @param colorHex Hex color code being liked
     */
    @Transaction
    suspend fun incrementLikes(colorHex: String) {
        val existing = getByColor(colorHex)
        if (existing != null) {
            insert(
                existing.copy(
                    likes = existing.likes + 1,
                    views = existing.views + 1,
                    lastShown = System.currentTimeMillis()
                )
            )
        } else {
            insert(
                ColorPreference(
                    colorHex = colorHex,
                    likes = 1,
                    views = 1,
                    lastShown = System.currentTimeMillis()
                )
            )
        }
    }
    
    /**
     * Increments dislike count for a color.
     * Also increments views and updates last shown.
     * Creates entry with dislikes=1, views=1 if color doesn't exist.
     * 
     * @param colorHex Hex color code being disliked
     */
    @Transaction
    suspend fun incrementDislikes(colorHex: String) {
        val existing = getByColor(colorHex)
        if (existing != null) {
            insert(
                existing.copy(
                    dislikes = existing.dislikes + 1,
                    views = existing.views + 1,
                    lastShown = System.currentTimeMillis()
                )
            )
        } else {
            insert(
                ColorPreference(
                    colorHex = colorHex,
                    dislikes = 1,
                    views = 1,
                    lastShown = System.currentTimeMillis()
                )
            )
        }
    }
    
    /**
     * Gets total number of tracked colors.
     * 
     * @return Count of color entries
     */
    @Query("SELECT COUNT(*) FROM color_preferences")
    suspend fun getCount(): Int
}
