package me.avinas.vanderwaals.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.avinas.vanderwaals.data.entity.WallpaperMetadata

/**
 * Room DAO for accessing wallpaper metadata from the local database.
 * 
 * Provides queries for:
 * - Retrieving all wallpapers or filtering by source/category
 * - Inserting/updating wallpapers from manifest sync
 * - Searching wallpapers by similarity score
 * - Managing wallpaper queue for rotation
 * 
 * All queries return Flow for reactive updates in the UI layer.
 * 
 * **Usage:**
 * - Call [insertAll] after downloading manifest.json (weekly sync)
 * - Use [getAll] for loading all wallpapers into memory for similarity calculations
 * - Use [getByCategory] for category-filtered browsing
 * - Use [getBySource] to separate GitHub vs Bing wallpapers
 * - Use [getByBrightnessRange] for contextual filtering (time-based)
 * 
 * @see me.avinas.vanderwaals.data.entity.WallpaperMetadata
 */
@Dao
interface WallpaperMetadataDao {
    
    /**
     * Inserts or replaces a list of wallpaper metadata entries.
     * 
     * Used during weekly manifest sync to update the local database with
     * the latest wallpaper catalog from GitHub.
     * 
     * Uses REPLACE conflict strategy to update existing wallpapers if
     * their metadata has changed (e.g., updated attribution, new thumbnail URL).
     * 
     * @param wallpapers List of wallpaper metadata to insert/update
     * 
     * Example:
     * ```kotlin
     * val manifest = downloadManifest()
     * dao.insertAll(manifest.wallpapers)
     * ```
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(wallpapers: List<WallpaperMetadata>)
    
    /**
     * Inserts or replaces a single wallpaper metadata entry.
     * 
     * @param wallpaper Wallpaper metadata to insert/update
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(wallpaper: WallpaperMetadata)
    
    /**
     * Retrieves all wallpaper metadata as a reactive Flow.
     * 
     * Returns a Flow that emits the complete wallpaper catalog whenever
     * the database is updated. This is used for:
     * - Loading embeddings for similarity calculations
     * - Populating the download queue
     * - Category browsing
     * 
     * **Performance:** This query loads all 6000+ wallpapers (~26 MB).
     * Consider using filtered queries ([getByCategory], [getBySource]) for UI.
     * 
     * @return Flow emitting list of all wallpapers
     */
    @Query("SELECT * FROM wallpaper_metadata")
    fun getAll(): Flow<List<WallpaperMetadata>>
    
    /**
     * Retrieves all wallpaper metadata as a one-shot suspend function.
     * 
     * Use this for background processing where reactive updates aren't needed
     * (e.g., calculating similarities, populating queue).
     * 
     * @return List of all wallpapers
     */
    @Query("SELECT * FROM wallpaper_metadata")
    suspend fun getAllOnce(): List<WallpaperMetadata>
    
    /**
     * Retrieves wallpapers filtered by category as a reactive Flow.
     * 
     * Categories come from GitHub repo folder structure (e.g., "gruvbox",
     * "nord", "nature", "minimal").
     * 
     * @param category Category name to filter by
     * @return Flow emitting list of wallpapers in the category
     * 
     * Example:
     * ```kotlin
     * dao.getByCategory("gruvbox").collect { wallpapers ->
     *     // Display gruvbox wallpapers in UI
     * }
     * ```
     */
    @Query("SELECT * FROM wallpaper_metadata WHERE category = :category")
    fun getByCategory(category: String): Flow<List<WallpaperMetadata>>
    
    /**
     * Retrieves wallpapers filtered by source.
     * 
     * @param source Source identifier ("github" or "bing")
     * @return Flow emitting list of wallpapers from the source
     * 
     * Example:
     * ```kotlin
     * dao.getBySource("bing").collect { bingWallpapers ->
     *     // Display Bing daily wallpapers
     * }
     * ```
     */
    @Query("SELECT * FROM wallpaper_metadata WHERE source = :source")
    fun getBySource(source: String): Flow<List<WallpaperMetadata>>
    
    /**
     * Retrieves wallpapers within a brightness range.
     * 
     * Used for contextual filtering based on time of day:
     * - Morning/Day: brightness > 50 (bright wallpapers)
     * - Evening/Night: brightness < 50 (dark wallpapers)
     * 
     * @param minBrightness Minimum brightness (0-100)
     * @param maxBrightness Maximum brightness (0-100)
     * @return List of wallpapers in brightness range
     * 
     * Example:
     * ```kotlin
     * // Get dark wallpapers for night mode
     * val darkWallpapers = dao.getByBrightnessRange(0, 50)
     * ```
     */
    @Query("SELECT * FROM wallpaper_metadata WHERE brightness BETWEEN :minBrightness AND :maxBrightness")
    suspend fun getByBrightnessRange(minBrightness: Int, maxBrightness: Int): List<WallpaperMetadata>
    
    /**
     * Retrieves a single wallpaper by ID.
     * 
     * @param id Wallpaper ID
     * @return Wallpaper metadata or null if not found
     */
    @Query("SELECT * FROM wallpaper_metadata WHERE id = :id")
    suspend fun getById(id: String): WallpaperMetadata?
    
    /**
     * Retrieves wallpapers by multiple IDs.
     * 
     * Useful for batch operations (e.g., loading liked wallpapers).
     * 
     * @param ids List of wallpaper IDs
     * @return List of wallpapers matching the IDs
     * 
     * Example:
     * ```kotlin
     * val preferences = preferencesDao.get().first()
     * val likedWallpapers = dao.getByIds(preferences.likedWallpaperIds)
     * ```
     */
    @Query("SELECT * FROM wallpaper_metadata WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<WallpaperMetadata>
    
    /**
     * Updates an existing wallpaper metadata entry.
     * 
     * @param wallpaper Wallpaper metadata to update
     */
    @Update
    suspend fun update(wallpaper: WallpaperMetadata)
    
    /**
     * Deletes a wallpaper by ID.
     * 
     * Note: This also removes the wallpaper from any download queue entries
     * that reference it (via foreign key constraints if configured).
     * 
     * @param id Wallpaper ID to delete
     */
    @Query("DELETE FROM wallpaper_metadata WHERE id = :id")
    suspend fun delete(id: String)
    
    /**
     * Deletes all wallpapers from a specific source.
     * 
     * Useful for removing old Bing wallpapers or clearing a specific
     * GitHub repo's content.
     * 
     * @param source Source identifier ("github" or "bing")
     */
    @Query("DELETE FROM wallpaper_metadata WHERE source = :source")
    suspend fun deleteBySource(source: String)
    
    /**
     * Deletes all wallpapers from the database.
     * 
     * Used when performing a full re-sync from manifest.
     */
    @Query("DELETE FROM wallpaper_metadata")
    suspend fun deleteAll()
    
    /**
     * Counts total number of wallpapers in the database.
     * 
     * @return Total wallpaper count
     */
    @Query("SELECT COUNT(*) FROM wallpaper_metadata")
    suspend fun getCount(): Int
    
    /**
     * Counts wallpapers by category.
     * 
     * @param category Category name
     * @return Number of wallpapers in the category
     */
    @Query("SELECT COUNT(*) FROM wallpaper_metadata WHERE category = :category")
    suspend fun getCountByCategory(category: String): Int
    
    /**
     * Gets all unique categories from the database.
     * 
     * @return Flow emitting list of category names
     */
    @Query("SELECT DISTINCT category FROM wallpaper_metadata ORDER BY category")
    fun getAllCategories(): Flow<List<String>>
    
    /**
     * Gets all unique sources from the database.
     * 
     * @return List of source identifiers
     */
    @Query("SELECT DISTINCT source FROM wallpaper_metadata")
    suspend fun getAllSources(): List<String>
    
    /**
     * Counts wallpapers by source.
     * 
     * @param source Source identifier ("github" or "bing")
     * @return Number of wallpapers from the source
     */
    @Query("SELECT COUNT(*) FROM wallpaper_metadata WHERE source = :source")
    suspend fun countBySource(source: String): Int
}
