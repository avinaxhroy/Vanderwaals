package me.avinas.vanderwaals.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.avinas.vanderwaals.core.NetworkRetry
import me.avinas.vanderwaals.data.dao.WallpaperMetadataDao
import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import me.avinas.vanderwaals.network.BingArchiveService
import me.avinas.vanderwaals.network.dto.BingArchiveWallpaperDto
import me.avinas.vanderwaals.network.dto.toWallpaperMetadata
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Syncs Bing wallpapers from the daily API (last 8 days) and the
 * npanuhin/Bing-Wallpaper-Archive into the local database.
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
    
    suspend fun syncDailyWallpapers(): Result<Int> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting Bing daily sync...")
        
        try {
            val response = NetworkRetry.retryWithBackoff {
                bingArchiveService.getDailyWallpaper(count = DAILY_FETCH_COUNT)
            }
            
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
                    embedding = FloatArray(1280) { 0f },  // Zero embedding (compute later)
                    resolution = "3840x2160",
                    attribution = bingImage.copyright
                )
            }
            
            val existingIds = entities.map { it.id }
            val existing = wallpaperDao.getByIds(existingIds)
            val existingIdSet = existing.map { it.id }.toSet()
            
            val newEntities = entities.filterNot { it.id in existingIdSet }
            
            if (newEntities.isEmpty()) {
                Log.d(TAG, "No new Bing wallpapers to sync")
                return@withContext Result.success(0)
            }
            
            wallpaperDao.insertAll(newEntities)
            
            Log.d(TAG, "✓ Synced ${newEntities.size} new Bing wallpapers")
            Result.success(newEntities.size)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync Bing daily wallpapers", e)
            Result.failure(Exception("Bing sync failed: ${e.message}", e))
        }
    }
    
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
                        val response = NetworkRetry.retryWithBackoff {
                            bingArchiveService.getArchiveManifestYear(
                                country = region.country,
                                language = region.language,
                                year = year
                            )
                        }
                        
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
                        
                        val entities: List<WallpaperMetadata> = wallpapers.map { dto -> dto.toWallpaperMetadata() }
                        
                        val existingIds: List<String> = entities.map { it.id }
                        val existing: List<WallpaperMetadata> = wallpaperDao.getByIds(existingIds)
                        val existingIdSet: Set<String> = existing.map { it.id }.toSet()
                        
                        val newEntities: List<WallpaperMetadata> = entities.filterNot { it.id in existingIdSet }
                        
                        if (newEntities.isEmpty()) {
                            Log.d(TAG, "No new wallpapers for ${region.getApiPath()}/$year")
                            continue
                        }
                        
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
    
    suspend fun syncArchiveRegion(
        region: me.avinas.vanderwaals.network.BingRegionConfig.Region,
        yearsToSync: Int = 3
    ): Result<Int> {
        return syncArchiveWallpapers(listOf(region), yearsToSync)
    }
    
    suspend fun getBingWallpaperCount(): Int = withContext(Dispatchers.IO) {
        try {
            wallpaperDao.countBySource("bing")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get Bing wallpaper count", e)
            0
        }
    }
    
    // Heuristic: more than 100 Bing wallpapers implies the archive was imported.
    suspend fun isArchiveImported(): Boolean = withContext(Dispatchers.IO) {
        getBingWallpaperCount() > 100
    }
}
