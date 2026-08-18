package me.avinas.vanderwaals.domain.usecase

import android.util.Log
import kotlinx.coroutines.flow.first
import me.avinas.vanderwaals.algorithm.SimilarityCalculator
import me.avinas.vanderwaals.data.datastore.SettingsDataStore
import me.avinas.vanderwaals.data.entity.DownloadQueueItem
import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import me.avinas.vanderwaals.data.repository.PreferenceRepository
import me.avinas.vanderwaals.data.repository.WallpaperRepository
import me.avinas.vanderwaals.worker.ChangeInterval
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pre-downloads upcoming wallpapers based on user preferences.
 */
@Singleton
class QueueNextWallpapersUseCase @Inject constructor(
    private val wallpaperRepository: WallpaperRepository,
    private val preferenceRepository: PreferenceRepository,
    private val similarityCalculator: SimilarityCalculator,
    private val settingsDataStore: SettingsDataStore
) {
    
    companion object {
        private const val TAG = "QueueNextWallpapers"
        
        private const val PREDOWNLOAD_COUNT_EVERY_UNLOCK = 5
        private const val PREDOWNLOAD_COUNT_HOURLY = 3
        private const val PREDOWNLOAD_COUNT_DAILY = 2
        private const val RECENT_HISTORY_LIMIT = 20
    }
    
    suspend operator fun invoke(): Result<Int> {
        return try {
            Log.d(TAG, "Starting smart pre-download queue")
            
            val settings = settingsDataStore.settings.first()
            val interval = settings.changeInterval
            
            val queueSize = when (interval) {
                "unlock" -> PREDOWNLOAD_COUNT_EVERY_UNLOCK
                "hourly" -> PREDOWNLOAD_COUNT_HOURLY
                "3hours" -> PREDOWNLOAD_COUNT_HOURLY
                "6hours" -> PREDOWNLOAD_COUNT_DAILY
                "12hours" -> PREDOWNLOAD_COUNT_DAILY
                "daily" -> PREDOWNLOAD_COUNT_DAILY
                "3days" -> PREDOWNLOAD_COUNT_DAILY
                "7days" -> PREDOWNLOAD_COUNT_DAILY
                "never" -> {
                    Log.d(TAG, "User has 'Never' interval, skipping pre-download")
                    return Result.success(0)
                }
                else -> PREDOWNLOAD_COUNT_HOURLY
            }
            
            Log.d(TAG, "User interval: $interval, queue size: $queueSize")
            
            val preferences = preferenceRepository.getUserPreferences().first()
            if (preferences == null) {
                Log.w(TAG, "User preferences not initialized, skipping pre-download")
                return Result.success(0)
            }
            
            val catalogCount = wallpaperRepository.getWallpaperCount()
            val downloadedCount = wallpaperRepository.getDownloadedWallpaperCount()
            
            if (downloadedCount >= catalogCount) {
                Log.d(TAG, "All $catalogCount wallpapers already downloaded, skipping queue")
                return Result.success(0)
            }
            
            val allWallpapers = wallpaperRepository.getAllWallpapers().first()
            if (allWallpapers.isEmpty()) {
                Log.w(TAG, "No wallpapers in catalog")
                return Result.success(0)
            }
            
            val enabledSources = mutableListOf<String>()
            if (settings.githubEnabled) enabledSources.add("github")
            if (settings.bingEnabled) enabledSources.add("bing")
            if (settings.vanderwaalsCollectionEnabled) enabledSources.add("vanderwaals")
            
            val sourceFilteredWallpapers = if (enabledSources.isEmpty()) {
                allWallpapers
            } else {
                allWallpapers.filter { it.source.lowercase() in enabledSources }
            }
            
            if (sourceFilteredWallpapers.isEmpty()) {
                Log.w(TAG, "No wallpapers in catalog for enabled sources: $enabledSources")
                return Result.success(0)
            }
            
            val downloadedIds = wallpaperRepository.getDownloadedWallpapers()
                .first()
                .map { it.id }
                .toSet()
            
            val recentIds = wallpaperRepository.getHistory()
                .first()
                .take(RECENT_HISTORY_LIMIT)
                .map { it.wallpaperId }
                .toSet()
            
            val candidates = sourceFilteredWallpapers.filter { wallpaper ->
                wallpaper.id !in downloadedIds && wallpaper.id !in recentIds
            }
            
            if (candidates.isEmpty()) {
                Log.w(TAG, "No candidates available for pre-download")
                return Result.success(0)
            }
            
            val scoredCandidates = candidates.map { wallpaper ->
                val embedding = wallpaper.embedding
                val score = if (embedding.isNotEmpty()) {
                    similarityCalculator.calculateSimilarity(
                        preferences.preferenceVector,
                        embedding
                    )
                } else {
                    0.5f
                }
                Pair(wallpaper, score)
            }
            
            val topMatches = scoredCandidates
                .sortedByDescending { it.second }
                .take(queueSize)
            
            if (topMatches.isEmpty()) {
                Log.w(TAG, "No top matches found")
                return Result.success(0)
            }
            
            val queueItems = topMatches.map { (wallpaper, score) ->
                DownloadQueueItem(
                    wallpaperId = wallpaper.id,
                    priority = score,
                    downloaded = false,
                    retryCount = 0
                )
            }
            
            wallpaperRepository.insertQueueItems(queueItems)
            
            Log.d(TAG, "Successfully queued ${queueItems.size} wallpapers for download")
            
            Result.success(queueItems.size)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to queue wallpapers for pre-download", e)
            Result.failure(Exception("Pre-download queue failed: ${e.message}", e))
        }
    }
}
