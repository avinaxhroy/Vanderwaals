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
 * Pre-downloads upcoming wallpapers based on user preferences to eliminate change delays.
 *
 * Download count scales with change frequency:
 * - Every Unlock: 5, Hourly: 3, Daily: 2, Never: skip
 *
 * Selects top-scoring undownloaded wallpapers and adds them to the queue
 * for BatchDownloadWorker to process.
 *
 * Called automatically after each wallpaper change by WallpaperChangeWorker.
 * 
 * Example:
 * ```kotlin
 * // In WallpaperChangeWorker after applying wallpaper
 * queueNextWallpapersUseCase()
 * ```
 * 
 * @property wallpaperRepository Repository for wallpaper metadata and downloads
 * @property preferenceRepository Repository for user preference vector
 * @property similarityCalculator Utility for computing similarity scores
 * @property settingsDataStore DataStore for user settings (change interval)
 * 
 * @see SelectNextWallpaperUseCase
 * @see me.avinas.vanderwaals.worker.BatchDownloadWorker
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
        
        /**
         * Number of wallpapers to pre-download for each frequency.
         */
        private const val PREDOWNLOAD_COUNT_EVERY_UNLOCK = 5  // High frequency
        private const val PREDOWNLOAD_COUNT_HOURLY = 3        // Medium frequency
        private const val PREDOWNLOAD_COUNT_DAILY = 2         // Low frequency
        
        /**
         * Number of recent wallpapers to exclude from selection.
         */
        private const val RECENT_HISTORY_LIMIT = 20
    }
    
    /**
     * Queues the next set of wallpapers for pre-downloading.
     * 
     * Determines how many wallpapers to queue based on user's change frequency,
     * then selects the best matches from the catalog and adds them to download queue.
     * 
     * @return Result<Int> Number of wallpapers queued for download
     */
    suspend operator fun invoke(): Result<Int> {
        return try {
            Log.d(TAG, "Starting smart pre-download queue")
            
            // Step 1: Get user's change interval to determine queue size
            val settings = settingsDataStore.settings.first()
            val interval = settings.changeInterval
            
            val queueSize = when (interval) {
                "unlock" -> PREDOWNLOAD_COUNT_EVERY_UNLOCK
                "hourly" -> PREDOWNLOAD_COUNT_HOURLY
                "3hours" -> PREDOWNLOAD_COUNT_HOURLY   // 3-hour interval - medium frequency
                "6hours" -> PREDOWNLOAD_COUNT_DAILY    // 6-hour interval - low-medium frequency
                "12hours" -> PREDOWNLOAD_COUNT_DAILY   // 12-hour interval - low frequency
                "daily" -> PREDOWNLOAD_COUNT_DAILY
                "3days" -> PREDOWNLOAD_COUNT_DAILY     // 3-day interval - low frequency
                "7days" -> PREDOWNLOAD_COUNT_DAILY     // 7-day interval - low frequency
                "never" -> {
                    Log.d(TAG, "User has 'Never' interval, skipping pre-download")
                    return Result.success(0)
                }
                else -> PREDOWNLOAD_COUNT_HOURLY // Default to medium
            }
            
            Log.d(TAG, "User interval: $interval, queue size: $queueSize")
            
            // Step 2: Get user preferences
            val preferences = preferenceRepository.getUserPreferences().first()
            if (preferences == null) {
                Log.w(TAG, "User preferences not initialized, skipping pre-download")
                return Result.success(0)
            }
            
            // Step 3: FAST PATH - Check if all wallpapers already downloaded
            // This avoids loading 8K wallpaper metadata when there's nothing to pre-download
            val catalogCount = wallpaperRepository.getWallpaperCount()
            val downloadedCount = wallpaperRepository.getDownloadedWallpaperCount()
            
            if (downloadedCount >= catalogCount) {
                Log.d(TAG, "All $catalogCount wallpapers already downloaded, skipping queue")
                return Result.success(0)
            }
            
            // Step 4: Get all wallpapers from catalog (only if not all downloaded)
            val allWallpapers = wallpaperRepository.getAllWallpapers().first()
            if (allWallpapers.isEmpty()) {
                Log.w(TAG, "No wallpapers in catalog")
                return Result.success(0)
            }
            
            // Step 3b: Filter by enabled sources (github/bing user settings)
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
            
            Log.d(TAG, "Catalog has ${allWallpapers.size} total, ${sourceFilteredWallpapers.size} from enabled sources: $enabledSources")
            
            // Step 4: Get already downloaded wallpapers
            val downloadedIds = wallpaperRepository.getDownloadedWallpapers()
                .first()
                .map { it.id }
                .toSet()
            
            Log.d(TAG, "${downloadedIds.size} wallpapers already downloaded")
            
            // Step 5: Get recently shown wallpapers to avoid repetition
            val recentIds = wallpaperRepository.getHistory()
                .first()
                .take(RECENT_HISTORY_LIMIT)
                .map { it.wallpaperId }
                .toSet()
            
            Log.d(TAG, "${recentIds.size} wallpapers shown recently")
            
            // Step 6: Filter candidates (not downloaded, not recent)
            val candidates = sourceFilteredWallpapers.filter { wallpaper ->
                wallpaper.id !in downloadedIds && wallpaper.id !in recentIds
            }
            
            if (candidates.isEmpty()) {
                Log.w(TAG, "No candidates available for pre-download")
                return Result.success(0)
            }
            
            Log.d(TAG, "${candidates.size} candidates available")
            
            // Step 7: Calculate similarity scores for candidates
            val scoredCandidates = candidates.map { wallpaper ->
                val embedding = wallpaper.embedding
                val score = if (embedding.isNotEmpty()) {
                    similarityCalculator.calculateSimilarity(
                        preferences.preferenceVector,
                        embedding
                    )
                } else {
                    0.5f // Default score for wallpapers without embeddings
                }
                Pair(wallpaper, score)
            }
            
            // Step 8: Sort by similarity score and take top N
            val topMatches = scoredCandidates
                .sortedByDescending { it.second }
                .take(queueSize)
            
            if (topMatches.isEmpty()) {
                Log.w(TAG, "No top matches found")
                return Result.success(0)
            }
            
            Log.d(TAG, "Selected ${topMatches.size} wallpapers for queue")
            
            // Step 9: Create download queue items
            val queueItems = topMatches.map { (wallpaper, score) ->
                DownloadQueueItem(
                    wallpaperId = wallpaper.id,
                    priority = score,
                    downloaded = false,
                    retryCount = 0
                )
            }
            
            // Step 10: Add to download queue (without clearing existing items)
            wallpaperRepository.insertQueueItems(queueItems)
            
            Log.d(TAG, "Successfully queued ${queueItems.size} wallpapers for download")
            Log.d(TAG, "Priority scores: ${queueItems.map { String.format("%.3f", it.priority) }}")
            
            Result.success(queueItems.size)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to queue wallpapers for pre-download", e)
            Result.failure(Exception("Pre-download queue failed: ${e.message}", e))
        }
    }
}
