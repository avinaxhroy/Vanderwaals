package me.avinas.vanderwaals.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import me.avinas.vanderwaals.data.entity.CategoryPreference

/**
 * DAO for tracking per-category preference scores (likes, dislikes, views).
 * Scores are used to boost or penalize categories during wallpaper selection.
 */
@Dao
interface CategoryPreferenceDao {
    
    @Query("SELECT * FROM category_preferences WHERE category = :category")
    suspend fun getByCategory(category: String): CategoryPreference?
    
    @Query("SELECT * FROM category_preferences WHERE category = :category")
    fun getByCategoryFlow(category: String): Flow<CategoryPreference?>
    
    @Query("SELECT * FROM category_preferences ORDER BY category ASC")
    suspend fun getAll(): List<CategoryPreference>
    
    @Query("SELECT * FROM category_preferences ORDER BY category ASC")
    fun getAllFlow(): Flow<List<CategoryPreference>>
    
    @Query("SELECT * FROM category_preferences WHERE views < 3")
    suspend fun getUnderexplored(): List<CategoryPreference>
    
    /**
     * Score calculation happens in-memory: Room can't run custom functions in
     * ORDER BY. Use for small result sets.
     */
    @Query("SELECT * FROM category_preferences")
    suspend fun getAllByScore(): List<CategoryPreference>
    
    /** Gets recently shown categories (within last 24 hours). */
    @Query("SELECT * FROM category_preferences WHERE lastShown >= :since ORDER BY lastShown DESC")
    suspend fun getRecentlyShown(since: Long): List<CategoryPreference>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(categoryPreference: CategoryPreference)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categoryPreferences: List<CategoryPreference>)
    
    @Transaction
    suspend fun incrementViews(category: String) {
        val current = getByCategory(category) ?: CategoryPreference(category = category)
        insert(current.copy(
            views = current.views + 1,
            lastShown = System.currentTimeMillis()
        ))
    }
    
    @Transaction
    suspend fun incrementLikes(category: String) {
        val current = getByCategory(category) ?: CategoryPreference(category = category)
        insert(current.copy(likes = current.likes + 1))
    }
    
    @Transaction
    suspend fun incrementDislikes(category: String) {
        val current = getByCategory(category) ?: CategoryPreference(category = category)
        insert(current.copy(dislikes = current.dislikes + 1))
    }
    
    @Query("UPDATE category_preferences SET lastShown = :timestamp WHERE category = :category")
    suspend fun updateLastShown(category: String, timestamp: Long)
    
    @Query("DELETE FROM category_preferences WHERE category = :category")
    suspend fun delete(category: String)
    
    @Query("DELETE FROM category_preferences")
    suspend fun deleteAll()
    
    @Query("SELECT COUNT(*) FROM category_preferences")
    suspend fun getCount(): Int
}
