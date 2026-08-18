package me.avinas.vanderwaals.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import me.avinas.vanderwaals.data.entity.ColorPreference

/**
 * DAO for tracking per-color preference scores (likes, dislikes, views).
 * Used as a fallback signal when category data is missing.
 */
@Dao
interface ColorPreferenceDao {
    
    /** Score calculated in-memory (Room can't sort by a computed value). */
    @Query("SELECT * FROM color_preferences")
    suspend fun getAllByScore(): List<ColorPreference>
    
    @Query("SELECT * FROM color_preferences WHERE colorHex = :colorHex")
    suspend fun getByColor(colorHex: String): ColorPreference?
    
    @Query("SELECT * FROM color_preferences WHERE colorHex = :colorHex")
    fun getByColorFlow(colorHex: String): Flow<ColorPreference?>
    
    @Query("SELECT * FROM color_preferences ORDER BY colorHex ASC")
    suspend fun getAll(): List<ColorPreference>
    
    @Query("SELECT * FROM color_preferences ORDER BY colorHex ASC")
    fun getAllFlow(): Flow<List<ColorPreference>>
    
    @Query("SELECT * FROM color_preferences WHERE views < 3")
    suspend fun getUnderexplored(): List<ColorPreference>
    
    @Query("SELECT * FROM color_preferences WHERE lastShown > :since")
    suspend fun getRecentlyShown(since: Long): List<ColorPreference>
    
    @Query("SELECT * FROM color_preferences WHERE likes > dislikes ORDER BY (likes - dislikes) DESC")
    suspend fun getLikedColors(): List<ColorPreference>
    
    @Query("SELECT * FROM color_preferences WHERE dislikes > likes ORDER BY (dislikes - likes) DESC")
    suspend fun getDislikedColors(): List<ColorPreference>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(colorPreference: ColorPreference)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(colorPreferences: List<ColorPreference>)
    
    @Query("DELETE FROM color_preferences WHERE colorHex = :colorHex")
    suspend fun delete(colorHex: String)
    
    @Query("DELETE FROM color_preferences")
    suspend fun deleteAll()
    
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
    
    @Query("SELECT COUNT(*) FROM color_preferences")
    suspend fun getCount(): Int
}
