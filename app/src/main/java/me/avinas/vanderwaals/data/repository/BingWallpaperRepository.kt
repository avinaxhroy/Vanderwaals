package me.avinas.vanderwaals.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.avinas.vanderwaals.data.dao.WallpaperMetadataDao
import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import me.avinas.vanderwaals.network.BingArchiveService
import me.avinas.vanderwaals.network.dto.BingArchiveWallpaperDto
import me.avinas.vanderwaals.network.dto.toWallpaperMetadata
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for syncing Bing wallpapers from daily API and archive.
 * 
 * Handles:
 * - Fetching Bing's daily wallpaper (last 8 days for weekly coverage)
 * - Importing from Bing Wallpaper Archive (10,000+ historical wallpapers)
 * - Converting to WallpaperMetadata entities
 * - Storing in local database
 * 
 * **Sync Strategy** (from VanderwaalsStrategy.md):
 * - Tier 2 content: Professional photography
 * - Daily wallpaper: Fetch last 8 days (weekly)
 * - Archive: Import recent 500 wallpapers on first sync
 * - Incremental: Add only new daily wallpapers
 * 
 * **Sources**:
 * 1. Bing Daily API: https://www.bing.com/HPImageArchive.aspx
 * 2. Bing Archive: https://github.com/npanuhin/Bing-Wallpaper-Archive
 * 
 * @property bingArchiveService Service for Bing API calls
 * @property wallpaperDao DAO for database operations
 */
@Singleton
class BingWallpaperRepository @Inject constructor(
    private val bingArchiveService: BingArchiveService,
    private val wallpaperDao: WallpaperMetadataDao
) {
    
    companion object {
        private const val TAG = "BingWallpaperRepo"
        
        /**
         * Number of recent archive wallpapers to import on first sync.
         */
        private const val ARCHIVE_IMPORT_COUNT = 500
        
        /**
         * Number of days to fetch from daily API (weekly coverage).
         */
        private const val DAILY_FETCH_COUNT = 8
    }
    
    /**
     * Syncs Bing daily wallpapers from the last week.
     * 
     * Fetches the last 8 days of Bing homepage wallpapers and saves to database.
     * Only adds wallpapers that don't already exist (checks by ID).
     * 
     * **Process**:
     * 1. Fetch last 8 wallpapers from Bing API
     * 2. Convert to WallpaperMetadata entities
     * 3. Check if already exists in database
     * 4. Insert new wallpapers only
     * 5. Return count of new wallpapers added
     * 
     * @return Result<Int> Success with count of new wallpapers, or Failure
     * 
     * Example:
     * ```kotlin
     * viewModelScope.launch {
     *     bingRepo.syncDailyWallpapers()
     *         .onSuccess { count ->
     *             Log.d(TAG, "Synced $count new Bing wallpapers")
     *         }
     *         .onFailure { error ->
     *             Log.e(TAG, "Sync failed: ${error.message}")
     *         }
     * }
     * ```
     */
    suspend fun syncDailyWallpapers(): Result<Int> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting Bing daily sync...")
        
        try {
            // Fetch last 8 days from Bing API
            val response = bingArchiveService.getDailyWallpaper(count = DAILY_FETCH_COUNT)
            
            if (!response.isSuccessful) {
                val errorMessage = "HTTP ${response.code()}: ${response.message()}"
                Log.e(TAG, errorMessage)
                return@withContext Result.failure(Exception(errorMessage))
            }
            
            val bingWallpapers = response.body()?.images
            if (bingWallpapers.isNullOrEmpty()) {
                val errorMessage = "Empty response from Bing API"
                Log.e(TAG, errorMessage)
                return@withContext Result.failure(Exception(errorMessage))
            }
            
            Log.d(TAG, "Fetched ${bingWallpapers.size} Bing wallpapers")
            
            // Convert to entities
            val entities = bingWallpapers.map { bingImage ->
                val fullUrl = "https://www.bing.com${bingImage.urlbase}_UHD.jpg"
                val thumbnailUrl = "https://www.bing.com${bingImage.urlbase}_800x600.jpg"
                
                me.avinas.vanderwaals.data.entity.WallpaperMetadata(
                    id = "bing_daily_${bingImage.startdate}",
                    url = fullUrl,
                    thumbnailUrl = thumbnailUrl,
                    source = "bing",
                    category = "photography",
                    colors = listOf("#3a506b", "#5bc0be", "#6fffe9", "#0b132b", "#1c2541"),  // Default palette
                    brightness = 50,  // Default (compute later)
                    contrast = 50,  // Default (compute later)
                    embedding = FloatArray(576) { 0f },  // Zero embedding (compute later)
                    resolution = "3840x2160",
                    attribution = bingImage.copyright
                )
            }
            
            // Filter out existing wallpapers
            val existingIds = entities.map { it.id }
            val existing = wallpaperDao.getByIds(existingIds)
            val existingIdSet = existing.map { it.id }.toSet()
            
            val newEntities = entities.filterNot { it.id in existingIdSet }
            
            if (newEntities.isEmpty()) {
                Log.d(TAG, "No new Bing wallpapers to sync")
                return@withContext Result.success(0)
            }
            
            // Insert new wallpapers
            wallpaperDao.insertAll(newEntities)
            
            Log.d(TAG, "✓ Synced ${newEntities.size} new Bing wallpapers")
            Result.success(newEntities.size)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync Bing daily wallpapers", e)
            Result.failure(Exception("Bing sync failed: ${e.message}", e))
        }
    }
    
    /**
     * Syncs wallpapers from Bing Wallpaper Archive (multi-region, year-based).
     * 
     * Intelligently syncs wallpapers from multiple regions using year-based APIs for
     * efficiency. This provides a diverse catalog of high-quality professional photography
     * while minimizing bandwidth usage.
     * 
     * **Strategy**:
     * - Multi-region: Fetch from multiple regions for variety (US, GB, ROW by default)
     * - Year-based: Sync only recent years (current + last 2 years) for efficiency
     * - Incremental: Only insert new wallpapers (checks database before inserting)
     * - Bandwidth-friendly: Year APIs are 100-500 KB vs 2-5 MB for full archives
     * 
     * **Process**:
     * 1. Get enabled regions from settings (or use defaults)
     * 2. For each region, fetch last 3 years using year-based APIs
     * 3. Convert to WallpaperMetadata entities
     * 4. Filter out existing entries
     * 5. Batch insert into database
     * 6. Return total count of imported wallpapers
     * 
     * @param regions List of regions to sync (defaults to US, GB, ROW)
     * @param yearsToSync Number of recent years to sync (default 3)
     * @return Result<Int> Success with count of imported wallpapers, or Failure
     * 
     * Example:
     * ```kotlin
     * // Sync default regions (US, GB, ROW) for last 3 years
     * bingRepo.syncArchiveWallpapers()
     *     .onSuccess { count ->
     *         Log.d(TAG, "Imported $count wallpapers")
     *     }
     *     .onFailure { error ->
     *         Log.e(TAG, "Sync failed: ${error.message}")
     *     }
     * 
     * // Sync specific regions
     * val customRegions = listOf(
     *     BingRegionConfig.US_ENGLISH,
     *     BingRegionConfig.FR_FRENCH,
     *     BingRegionConfig.JP_JAPANESE
     * )
     * bingRepo.syncArchiveWallpapers(customRegions, yearsToSync = 2)
     * ```
     */
    suspend fun syncArchiveWallpapers(
        regions: List<me.avinas.vanderwaals.network.BingRegionConfig.Region> = me.avinas.vanderwaals.network.BingRegionConfig.DEFAULT_REGIONS,
        yearsToSync: Int = 3
    ): Result<Int> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting Bing archive sync for ${regions.size} regions, $yearsToSync years...")
        
        var totalImported = 0
        val errors = mutableListOf<String>()
        
        try {
            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            val yearsToFetch = (currentYear - yearsToSync + 1)..currentYear
            
            for (region in regions) {
                Log.d(TAG, "Syncing region: ${region.displayName} (${region.getApiPath()})")
                
                for (year in yearsToFetch) {
                    try {
                        val response = bingArchiveService.getArchiveManifestYear(
                            country = region.country,
                            language = region.language,
                            year = year
                        )
                        
                        if (!response.isSuccessful) {
                            if (response.code() == 404) {
                                Log.w(TAG, "No data for ${region.getApiPath()}/$year (404)")
                            } else {
                                errors.add("${region.getApiPath()}/$year: HTTP ${response.code()}")
                            }
                            continue
                        }
                        
                        val wallpapers: List<BingArchiveWallpaperDto>? = response.body()
                        if (wallpapers == null || wallpapers.isEmpty()) {
                            Log.w(TAG, "Empty response for ${region.getApiPath()}/$year")
                            continue
                        }
                        
                        Log.d(TAG, "Fetched ${wallpapers.size} wallpapers from ${region.getApiPath()}/$year")
                        
                        // Convert to entities
                        val entities: List<WallpaperMetadata> = wallpapers.map { dto -> dto.toWallpaperMetadata() }
                        
                        // Filter out existing wallpapers
                        val existingIds: List<String> = entities.map { it.id }
                        val existing: List<WallpaperMetadata> = wallpaperDao.getByIds(existingIds)
                        val existingIdSet: Set<String> = existing.map { it.id }.toSet()
                        
                        val newEntities: List<WallpaperMetadata> = entities.filterNot { it.id in existingIdSet }
                        
                        if (newEntities.isEmpty()) {
                            Log.d(TAG, "No new wallpapers for ${region.getApiPath()}/$year")
                            continue
                        }
                        
                        // Batch insert in chunks
                        val chunkSize = 100
                        newEntities.chunked(chunkSize).forEach { chunk ->
                            wallpaperDao.insertAll(chunk)
                        }
                        
                        totalImported += newEntities.size
                        Log.d(TAG, "✓ Imported ${newEntities.size} wallpapers from ${region.getApiPath()}/$year")
                        
                    } catch (e: Exception) {
                        val errorMsg = "${region.getApiPath()}/$year: ${e.message}"
                        errors.add(errorMsg)
                        Log.e(TAG, "Failed to sync $errorMsg", e)
                    }
                }
            }
            
            if (totalImported > 0) {
                Log.d(TAG, "✓ Archive sync complete: $totalImported wallpapers imported")
                if (errors.isNotEmpty()) {
                    Log.w(TAG, "Sync completed with ${errors.size} errors: ${errors.joinToString(", ")}")
                }
                Result.success(totalImported)
            } else if (errors.isNotEmpty()) {
                val errorMessage = "Archive sync failed: ${errors.joinToString("; ")}"
                Log.e(TAG, errorMessage)
                Result.failure(Exception(errorMessage))
            } else {
                Log.d(TAG, "No new wallpapers to import")
                Result.success(0)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error during archive sync", e)
            Result.failure(Exception("Archive sync failed: ${e.message}", e))
        }
    }
    
    /**
     * Syncs wallpapers from a single region (convenience method).
     * 
     * @param region Region to sync
     * @param yearsToSync Number of recent years to sync
     * @return Result<Int> Success with count of imported wallpapers, or Failure
     */
    suspend fun syncArchiveRegion(
        region: me.avinas.vanderwaals.network.BingRegionConfig.Region,
        yearsToSync: Int = 3
    ): Result<Int> {
        return syncArchiveWallpapers(listOf(region), yearsToSync)
    }
    
    /**
     * Gets the count of Bing wallpapers currently in database.
     * 
     * @return Count of wallpapers with source="bing"
     */
    suspend fun getBingWallpaperCount(): Int = withContext(Dispatchers.IO) {
        try {
            wallpaperDao.countBySource("bing")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get Bing wallpaper count", e)
            0
        }
    }
    
    /**
     * Checks if Bing archive has been imported.
     * 
     * Heuristic: If we have more than 100 Bing wallpapers, assume archive was imported.
     * 
     * @return true if archive likely imported, false otherwise
     */
    suspend fun isArchiveImported(): Boolean = withContext(Dispatchers.IO) {
        getBingWallpaperCount() > 100
    }
}
