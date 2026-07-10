package me.avinas.vanderwaals.worker

import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import me.avinas.vanderwaals.algorithm.SimilarityCalculator
import me.avinas.vanderwaals.data.datastore.SettingsDataStore
import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import me.avinas.vanderwaals.data.repository.DailyPlaylistManager
import me.avinas.vanderwaals.data.repository.PreferenceRepository
import me.avinas.vanderwaals.data.repository.WallpaperRepository
import java.io.File
import kotlin.random.Random

/**
 * Worker to download the daily set of wallpapers.
 *
 * Responsibilities:
 * 1. Get all wallpapers from catalog
 * 2. Score and select based on user preferences
 * 3. Download them from the network with progress reporting
 * 4. Update DailyPlaylistManager with the new list
 * 5. Apply the first wallpaper immediately after download
 */
@HiltWorker
class DailyPlaylistWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val wallpaperRepository: WallpaperRepository,
    private val preferenceRepository: PreferenceRepository,
    private val similarityCalculator: SimilarityCalculator,
    private val dailyPlaylistManager: DailyPlaylistManager,
    private val settingsDataStore: SettingsDataStore
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "DailyPlaylistWorker"
        const val WORK_NAME = "daily_playlist_worker"
        const val WORK_NAME_MANUAL = "manual_playlist_download"
        
        // Progress data keys
        const val KEY_DOWNLOADED_COUNT = "downloaded_count"
        const val KEY_TOTAL_COUNT = "total_count"
        const val KEY_STATUS = "status"
        const val KEY_APPLIED_WALLPAPER_ID = "applied_wallpaper_id"
        
        // Status values
        const val STATUS_DOWNLOADING = "downloading"
        const val STATUS_APPLYING = "applying"
        const val STATUS_COMPLETE = "complete"
        const val STATUS_FAILED = "failed"
        
        // Exploration rate for diversity
        private const val EXPLORATION_RATE = 0.2f
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting Daily Playlist download job")
        
        // Check if this is a manual trigger (should bypass interval check)
        val isManualTrigger = tags.contains("manual_playlist_download")
        Log.d(TAG, "Is manual trigger: $isManualTrigger, tags: $tags")
        
        var playlistSize = 20 // Default playlist size
        
        return try {
            val settings = settingsDataStore.settings.first()
            
            // Only check interval if not manually triggered
            if (!isManualTrigger && settings.changeInterval != "unlock") {
                Log.d(TAG, "Daily playlist disabled (interval is ${settings.changeInterval}), skipping")
                return Result.success()
            }

            playlistSize = settings.dailyPlaylistSize
            Log.d(TAG, "Target playlist size: $playlistSize")
            
            // Report initial progress
            setProgress(workDataOf(
                KEY_DOWNLOADED_COUNT to 0,
                KEY_TOTAL_COUNT to playlistSize,
                KEY_STATUS to STATUS_DOWNLOADING
            ))
            
            // Step 1: Get all wallpapers from the catalog (with embeddings for scoring)
            val allWallpapers = wallpaperRepository.getAllWallpapers().first()
            if (allWallpapers.isEmpty()) {
                Log.e(TAG, "No wallpapers in catalog!")
                setProgress(workDataOf(
                    KEY_DOWNLOADED_COUNT to 0,
                    KEY_TOTAL_COUNT to playlistSize,
                    KEY_STATUS to STATUS_FAILED
                ))
                return Result.failure()
            }
            
            // Step 1b: Filter by enabled sources (github/bing user settings)
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
                Log.e(TAG, "No wallpapers available for enabled sources: $enabledSources")
                return Result.failure()
            }
            
            Log.d(TAG, "Catalog has ${allWallpapers.size} total, ${sourceFilteredWallpapers.size} from enabled sources: $enabledSources")
            
            // Step 2: Get user preferences for scoring (optional, fallback to random if not available)
            val preferences = preferenceRepository.getUserPreferences().first()
            
            // Step 3: Get recently shown wallpapers to avoid repetition
            val recentHistory = wallpaperRepository.getHistory()
                .first()
                .take(50)
                .map { it.wallpaperId }
                .toSet()
            Log.d(TAG, "${recentHistory.size} wallpapers shown recently")
            
            // Step 4: Filter candidates (not recently shown)
            val candidates = sourceFilteredWallpapers.filter { wallpaper ->
                wallpaper.id !in recentHistory
            }
            
            if (candidates.isEmpty()) {
                Log.w(TAG, "No new candidates available, using all source-filtered wallpapers")
            }
            
            val availableCandidates = if (candidates.isNotEmpty()) candidates else sourceFilteredWallpapers
            Log.d(TAG, "${availableCandidates.size} candidates available for selection")
            
            // Step 5: Score and select wallpapers
            val selectedWallpapers = selectWallpapers(
                availableCandidates, 
                playlistSize, 
                preferences?.preferenceVector
            )
            Log.d(TAG, "Selected ${selectedWallpapers.size} wallpapers for download")
            
            // Step 6: Download each wallpaper and track progress
            val newPlaylistIds = mutableListOf<String>()
            var firstWallpaper: WallpaperMetadata? = null
            var firstWallpaperFile: File? = null
            
            for ((index, wallpaper) in selectedWallpapers.withIndex()) {
                Log.d(TAG, "Downloading wallpaper ${index + 1}/${selectedWallpapers.size}: ${wallpaper.id}")
                
                val downloadResult = wallpaperRepository.downloadWallpaper(wallpaper)
                
                if (downloadResult.isSuccess) {
                    newPlaylistIds.add(wallpaper.id)
                    
                    // Mark as downloaded in the queue
                    wallpaperRepository.markAsDownloaded(wallpaper.id)
                    
                    // Store first wallpaper for immediate application
                    if (firstWallpaper == null) {
                        firstWallpaper = wallpaper
                        firstWallpaperFile = downloadResult.getOrNull()
                    }
                    
                    Log.d(TAG, "Downloaded and added to playlist: ${wallpaper.id} (${newPlaylistIds.size}/$playlistSize)")
                    
                    // Report progress after each successful download
                    setProgress(workDataOf(
                        KEY_DOWNLOADED_COUNT to newPlaylistIds.size,
                        KEY_TOTAL_COUNT to playlistSize,
                        KEY_STATUS to STATUS_DOWNLOADING
                    ))
                } else {
                    Log.w(TAG, "Failed to download ${wallpaper.id}: ${downloadResult.exceptionOrNull()?.message}")
                }
            }
            
            if (newPlaylistIds.isEmpty()) {
                Log.e(TAG, "Failed to download any wallpapers!")
                setProgress(workDataOf(
                    KEY_DOWNLOADED_COUNT to 0,
                    KEY_TOTAL_COUNT to playlistSize,
                    KEY_STATUS to STATUS_FAILED
                ))
                return Result.retry()
            }

            // Step 7: Update Playlist Manager
            dailyPlaylistManager.setPlaylist(newPlaylistIds)
            settingsDataStore.updateLastPlaylistUpdate(System.currentTimeMillis())
            
            Log.d(TAG, "Daily playlist updated with ${newPlaylistIds.size} wallpapers")
            
            // Step 8: Apply first wallpaper immediately if available
            var appliedWallpaperId: String? = null
            if (firstWallpaper != null && firstWallpaperFile != null && firstWallpaperFile.exists()) {
                setProgress(workDataOf(
                    KEY_DOWNLOADED_COUNT to newPlaylistIds.size,
                    KEY_TOTAL_COUNT to playlistSize,
                    KEY_STATUS to STATUS_APPLYING
                ))
                
                val applied = applyWallpaperImmediately(firstWallpaperFile, settings.applyTo)
                if (applied) {
                    appliedWallpaperId = firstWallpaper.id
                    Log.d(TAG, "Applied first wallpaper immediately: ${firstWallpaper.id}")
                    
                    // Record in history
                    wallpaperRepository.recordWallpaperApplied(firstWallpaper)
                } else {
                    Log.w(TAG, "Failed to apply first wallpaper immediately")
                }
            }
            
            // Report completion
            setProgress(workDataOf(
                KEY_DOWNLOADED_COUNT to newPlaylistIds.size,
                KEY_TOTAL_COUNT to playlistSize,
                KEY_STATUS to STATUS_COMPLETE,
                KEY_APPLIED_WALLPAPER_ID to (appliedWallpaperId ?: "")
            ))
            
            Result.success(workDataOf(
                KEY_DOWNLOADED_COUNT to newPlaylistIds.size,
                KEY_TOTAL_COUNT to playlistSize,
                KEY_APPLIED_WALLPAPER_ID to (appliedWallpaperId ?: "")
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Error in DailyPlaylistWorker", e)
            
            // Use ErrorHandler for consistent error handling
            val (error, action) = me.avinas.vanderwaals.core.ErrorHandler.handleWorkerError(
                exception = e,
                context = "DailyPlaylistDownload",
                attemptCount = runAttemptCount,
                metadata = mapOf(
                    "playlist_size" to playlistSize,
                    "is_manual" to isManualTrigger
                )
            )
            
            setProgress(workDataOf(
                KEY_DOWNLOADED_COUNT to 0,
                KEY_TOTAL_COUNT to 0,
                KEY_STATUS to STATUS_FAILED
            ))
            
            when (action) {
                is me.avinas.vanderwaals.core.ErrorRecoveryAction.Retry,
                is me.avinas.vanderwaals.core.ErrorRecoveryAction.RetryWithBackoff -> {
                    if (runAttemptCount < 3) Result.retry() else Result.failure()
                }
                else -> {
                    val errorData = me.avinas.vanderwaals.core.ErrorHandler.createWorkDataForError(
                        error = error,
                        attemptCount = runAttemptCount
                    )
                    Result.failure(androidx.work.workDataOf(*errorData.map { it.key to it.value }.toTypedArray()))
                }
            }
        }
    }
    
    /**
     * Selects wallpapers using epsilon-greedy strategy.
     * - 80% exploitation: pick highest similarity to user preference
     * - 20% exploration: random selection for diversity
     */
    private fun selectWallpapers(
        candidates: List<WallpaperMetadata>,
        count: Int,
        preferenceVector: FloatArray?
    ): List<WallpaperMetadata> {
        if (candidates.isEmpty()) return emptyList()
        
        val random = Random(System.currentTimeMillis())
        val selected = mutableListOf<WallpaperMetadata>()
        val remainingCandidates = candidates.toMutableList()
        
        // If no preference vector, just shuffle and take top N
        if (preferenceVector == null || preferenceVector.isEmpty()) {
            Log.d(TAG, "No user preferences, using random selection")
            remainingCandidates.shuffle(random)
            return remainingCandidates.take(count)
        }
        
        // Score all candidates by similarity
        val scoredCandidates = remainingCandidates.mapNotNull { wallpaper ->
            val embedding = wallpaper.embedding
            if (embedding.isNotEmpty()) {
                val score = similarityCalculator.calculateSimilarity(preferenceVector, embedding)
                Pair(wallpaper, score)
            } else {
                // Include wallpapers without embeddings with neutral score
                Pair(wallpaper, 0.5f)
            }
        }.toMutableList()
        
        while (selected.size < count && scoredCandidates.isNotEmpty()) {
            val wallpaper = if (random.nextFloat() < EXPLORATION_RATE) {
                // Explore: random selection
                val randomIndex = random.nextInt(scoredCandidates.size)
                scoredCandidates.removeAt(randomIndex).first
            } else {
                // Exploit: pick highest scoring
                val best = scoredCandidates.maxByOrNull { it.second }!!
                scoredCandidates.remove(best)
                best.first
            }
            selected.add(wallpaper)
        }
        
        return selected
    }
    
    /**
     * Applies wallpaper immediately to the specified screen(s).
     * Uses SmartCrop for optimal display.
     *
     * applyTo values: "lock_screen", "home_screen", "both", "both_different"
     */
    private suspend fun applyWallpaperImmediately(wallpaperFile: File, applyTo: String): Boolean {
        var originalBitmap: android.graphics.Bitmap? = null
        var processedBitmap: android.graphics.Bitmap? = null
        
        return try {
            val wallpaperManager = WallpaperManager.getInstance(applicationContext)
            
            // Use BitmapManager for safe bitmap loading with OOM protection
            originalBitmap = me.avinas.vanderwaals.core.BitmapManager.loadBitmap(wallpaperFile)
            
            if (originalBitmap == null) {
                Log.e(TAG, "Failed to decode wallpaper file")
                return false
            }
            
            // Use actual screen size for SmartCrop
            val screenSize = me.avinas.vanderwaals.core.getDeviceScreenSize(applicationContext)
            
            // Apply SmartCrop to actual screen dimensions
            processedBitmap = me.avinas.vanderwaals.core.SmartCrop.smartCropBitmapAsync(
                source = originalBitmap,
                targetWidth = screenSize.width,
                targetHeight = screenSize.height,
                mode = me.avinas.vanderwaals.core.SmartCrop.CropMode.AUTO
            )
            
            // Recycle original bitmap to save memory using BitmapManager
            if (processedBitmap !== originalBitmap) {
                me.avinas.vanderwaals.core.BitmapManager.recycleSafely(originalBitmap)
                originalBitmap = null // Clear reference
            }
            
            // Apply based on user settings (DataStore values: lock_screen, home_screen, both)
            when (applyTo) {
                "home_screen" -> {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        wallpaperManager.setBitmap(processedBitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                    } else {
                        wallpaperManager.setBitmap(processedBitmap)
                    }
                }
                "lock_screen" -> {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        wallpaperManager.setBitmap(processedBitmap, null, true, WallpaperManager.FLAG_LOCK)
                    } else {
                        wallpaperManager.setBitmap(processedBitmap)
                    }
                }
                "both", "both_different" -> {
                    // For "both_different", we still apply the same wallpaper here
                    // since this is just the initial playlist download
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        wallpaperManager.setBitmap(processedBitmap, null, true, 
                            WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
                    } else {
                        wallpaperManager.setBitmap(processedBitmap)
                    }
                }
                else -> {
                    // Default to lock screen only if unknown value
                    Log.w(TAG, "Unknown applyTo value: $applyTo, defaulting to lock screen")
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        wallpaperManager.setBitmap(processedBitmap, null, true, WallpaperManager.FLAG_LOCK)
                    } else {
                        wallpaperManager.setBitmap(processedBitmap)
                    }
                }
            }
            
            // Recycle processed bitmap after successful application
            me.avinas.vanderwaals.core.BitmapManager.recycleSafely(processedBitmap)
            processedBitmap = null // Clear reference
            
            Log.d(TAG, "Successfully applied wallpaper to $applyTo")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply wallpaper: ${e.message}", e)
            false
        } finally {
            // Ensure bitmaps are recycled even if exception occurs
            me.avinas.vanderwaals.core.BitmapManager.recycleSafely(originalBitmap)
            me.avinas.vanderwaals.core.BitmapManager.recycleSafely(processedBitmap)
        }
    }
}
