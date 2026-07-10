package me.avinas.vanderwaals.domain.usecase

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import me.avinas.vanderwaals.data.repository.WallpaperRepository
import java.io.File
import javax.inject.Inject

/**
 * Finds an already-downloaded wallpaper to use as offline fallback
 * when network download fails. Verifies files exist on disk.
 */
class FindCachedWallpaperUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val wallpaperRepository: WallpaperRepository
) {
    companion object {
        private const val TAG = "FindCachedWallpaperUseCase"
        
        /**
         * Number of recent wallpapers to exclude from selection.
         * Smaller window for offline fallback to increase available options.
         */
        private const val RECENT_HISTORY_WINDOW = 5
    }
    
    /**
     * Finds a cached wallpaper to use as offline fallback.
     * 
     * @param excludeWallpaperId ID of wallpaper to exclude (typically the one that failed to download)
     * @return Pair of (WallpaperMetadata, File) if found, null otherwise
     */
    suspend operator fun invoke(
        excludeWallpaperId: String? = null
    ): Pair<WallpaperMetadata, File>? {
        return try {
            // Get all wallpapers marked as downloaded in the queue
            val downloadedWallpapers = wallpaperRepository.getDownloadedWallpapers().first()
            
            if (downloadedWallpapers.isEmpty()) {
                Log.d(TAG, "No downloaded wallpapers in queue")
                return null
            }
            
            // Get recent history to avoid showing same wallpapers
            val recentHistory = wallpaperRepository.getHistory().first()
                .take(RECENT_HISTORY_WINDOW)
                .map { it.wallpaperId }
                .toSet()
            
            // Filter candidates:
            // 1. Exclude the wallpaper that failed to download
            // 2. Exclude recently shown wallpapers
            // 3. Verify file actually exists on disk
            val cacheDir = File(context.cacheDir, "wallpapers")
            
            val validCachedWallpapers = downloadedWallpapers
                .filter { excludeWallpaperId == null || it.id != excludeWallpaperId }
                .filter { it.id !in recentHistory }
                .mapNotNull { wallpaper ->
                    val file = File(cacheDir, "${wallpaper.id}.jpg")
                    if (file.exists() && file.length() > 0) {
                        Pair(wallpaper, file)
                    } else {
                        null
                    }
                }
            
            if (validCachedWallpapers.isEmpty()) {
                // If no wallpapers after filtering recent history, try all cached (except excluded)
                Log.d(TAG, "No valid cached wallpapers after filtering, trying all cached...")
                val allCached = downloadedWallpapers
                    .filter { excludeWallpaperId == null || it.id != excludeWallpaperId }
                    .mapNotNull { wallpaper ->
                        val file = File(cacheDir, "${wallpaper.id}.jpg")
                        if (file.exists() && file.length() > 0) {
                            Pair(wallpaper, file)
                        } else {
                            null
                        }
                    }
                
                if (allCached.isEmpty()) {
                    Log.d(TAG, "No cached wallpapers available at all")
                    return null
                }
                
                // Return a random cached wallpaper
                val selected = allCached.random()
                Log.d(TAG, "Fallback: Selected random cached wallpaper: ${selected.first.id}")
                return selected
            }
            
            // Return a random wallpaper from valid candidates
            val selected = validCachedWallpapers.random()
            Log.d(TAG, "Fallback: Selected cached wallpaper: ${selected.first.id} from ${validCachedWallpapers.size} candidates")
            selected
            
        } catch (e: Exception) {
            Log.e(TAG, "Error finding cached wallpaper fallback", e)
            null
        }
    }
}
