package me.avinas.vanderwaals.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import me.avinas.vanderwaals.data.entity.WallpaperSummary

/**
 * DAO for the wallpaper catalog. Supports filtering by source, category,
 * and brightness. Updated during manifest sync.
 */
@Dao
interface WallpaperMetadataDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(wallpapers: List<WallpaperMetadata>)

    /**
     * Atomically replaces all wallpapers from a given source.
     *
     * Deletes every existing row where `source = source`, then inserts the
     * new batch — all inside a single SQLite transaction. If the process is
     * killed between the delete and the inserts, the old data is kept intact.
     *
     * @param source Source identifier ("github", "bing", "vanderwaals", …)
     * @param wallpapers Replacement wallpapers for that source
     */
    @Transaction
    suspend fun replaceSourceWallpapers(source: String, wallpapers: List<WallpaperMetadata>) {
        deleteBySource(source)
        // Insert in batches to stay under SQLite's 999-variable-binding limit
        wallpapers.chunked(500).forEach { batch -> insertAll(batch) }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(wallpaper: WallpaperMetadata)
    
    /** Loads all 6000+ wallpapers (~26 MB); prefer filtered queries for UI. */
    @Query("SELECT * FROM wallpaper_metadata")
    fun getAll(): Flow<List<WallpaperMetadata>>
    
    @Query("SELECT * FROM wallpaper_metadata")
    suspend fun getAllOnce(): List<WallpaperMetadata>

    /** Excludes the large embedding array; use for UI lists to reduce memory. */
    @Query("SELECT id, url, thumbnailUrl, source, category, colors, brightness, contrast, resolution, attribution, aestheticScore, mood, style FROM wallpaper_metadata")
    fun getAllSummaries(): Flow<List<WallpaperSummary>>

    /** Excludes the embedding array but keeps the semantic signal (aestheticScore, mood, style). */
    @Query("SELECT id, url, thumbnailUrl, source, category, colors, brightness, contrast, resolution, attribution, aestheticScore, mood, style FROM wallpaper_metadata")
    suspend fun getAllSummariesOnce(): List<WallpaperSummary>
    
    @Query("SELECT * FROM wallpaper_metadata WHERE category = :category")
    fun getByCategory(category: String): Flow<List<WallpaperMetadata>>
    
    @Query("SELECT * FROM wallpaper_metadata WHERE source = :source")
    fun getBySource(source: String): Flow<List<WallpaperMetadata>>
    
    @Query("SELECT * FROM wallpaper_metadata WHERE brightness BETWEEN :minBrightness AND :maxBrightness")
    suspend fun getByBrightnessRange(minBrightness: Int, maxBrightness: Int): List<WallpaperMetadata>
    
    /** Served by the composite (category, brightness) index. */
    @Query("SELECT * FROM wallpaper_metadata WHERE category = :category AND brightness BETWEEN :minBrightness AND :maxBrightness")
    suspend fun getByCategoryAndBrightnessRange(category: String, minBrightness: Int, maxBrightness: Int): List<WallpaperMetadata>
    
    /** Served by the composite (source, brightness) index. */
    @Query("SELECT * FROM wallpaper_metadata WHERE source = :source AND brightness BETWEEN :minBrightness AND :maxBrightness")
    suspend fun getBySourceAndBrightnessRange(source: String, minBrightness: Int, maxBrightness: Int): List<WallpaperMetadata>
    
    @Query("SELECT * FROM wallpaper_metadata WHERE id = :id")
    suspend fun getById(id: String): WallpaperMetadata?
    
    @Query("SELECT * FROM wallpaper_metadata WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<WallpaperMetadata>
    
    @Update
    suspend fun update(wallpaper: WallpaperMetadata)
    
    @Query("DELETE FROM wallpaper_metadata WHERE id = :id")
    suspend fun delete(id: String)
    
    @Query("DELETE FROM wallpaper_metadata WHERE source = :source")
    suspend fun deleteBySource(source: String)
    
    @Query("DELETE FROM wallpaper_metadata")
    suspend fun deleteAll()
    
    @Query("SELECT COUNT(*) FROM wallpaper_metadata")
    suspend fun getCount(): Int
    
    @Query("SELECT COUNT(*) FROM wallpaper_metadata WHERE category = :category")
    suspend fun getCountByCategory(category: String): Int

    @Query("SELECT COUNT(*) FROM wallpaper_metadata WHERE source = :source")
    suspend fun getCountBySource(source: String): Int
    
    @Query("SELECT DISTINCT category FROM wallpaper_metadata ORDER BY category")
    fun getAllCategories(): Flow<List<String>>
    
    @Query("SELECT DISTINCT source FROM wallpaper_metadata")
    suspend fun getAllSources(): List<String>
    
    @Query("SELECT COUNT(*) FROM wallpaper_metadata WHERE source = :source")
    suspend fun countBySource(source: String): Int
}
