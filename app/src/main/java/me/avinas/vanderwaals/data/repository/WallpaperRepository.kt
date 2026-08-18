package me.avinas.vanderwaals.data.repository

import kotlinx.coroutines.flow.Flow
import me.avinas.vanderwaals.data.entity.DownloadQueueItem
import me.avinas.vanderwaals.data.entity.WallpaperHistory
import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import me.avinas.vanderwaals.domain.usecase.FeedbackType
import java.io.File

interface WallpaperRepository {
    
    fun getAllWallpapers(): Flow<List<WallpaperMetadata>>

    // Lightweight summaries without embeddings - optimized for UI display to reduce memory usage.
    fun getAllWallpaperSummaries(): Flow<List<WallpaperMetadata>>
    
    fun getDownloadedWallpapers(): Flow<List<WallpaperMetadata>>
    
    // Categories come from the GitHub repo folder structure (e.g. "gruvbox", "nord", "nature", "minimal").
    fun getWallpapersByCategory(category: String): Flow<List<WallpaperMetadata>>
    
    suspend fun addToDownloadQueue(wallpapers: List<WallpaperMetadata>)
    
    // Uses REPLACE strategy, so existing items with the same wallpaperId are updated.
    suspend fun insertQueueItems(queueItems: List<DownloadQueueItem>)
    
    // Returns items within the retry limit (< 3 attempts), sorted by priority (highest first).
    suspend fun getNextToDownload(limit: Int = 10): List<DownloadQueueItem>
    
    suspend fun markAsDownloaded(wallpaperId: String)
    
    // Returns a history ID that can later be used to attach feedback.
    suspend fun recordWallpaperApplied(wallpaper: WallpaperMetadata): Long
    
    suspend fun updateHistory(historyId: Long, feedback: FeedbackType)
    
    // Captures time, battery, and brightness for future contextual recommendations.
    suspend fun updateHistoryWithContext(historyId: Long, feedback: FeedbackType, context: me.avinas.vanderwaals.data.entity.FeedbackContext)
    
    // History is limited to the last 100 entries, newest first.
    fun getHistory(): Flow<List<WallpaperHistory>>
    
    suspend fun downloadWallpaper(wallpaper: WallpaperMetadata): Result<File>
    
    suspend fun deleteWallpaper(wallpaper: WallpaperMetadata): Result<Unit>
    
    // Setting removedAt enables duration calculation for implicit feedback.
    suspend fun markWallpaperRemoved(historyId: Long, timestamp: Long)
    
    // Used after marking removed to read the completed entry's duration.
    suspend fun getHistoryEntry(historyId: Long): WallpaperHistory?

    // File path: cache/wallpapers/{wallpaperId}_cropped.jpg
    fun getCroppedWallpaperFile(wallpaper: WallpaperMetadata): File
    
    // Loads a single wallpaper with its full embedding - more efficient than loading all.
    suspend fun getWallpaperById(id: String): WallpaperMetadata?
    
    // Fast count query that does not load wallpaper data.
    suspend fun getWallpaperCount(): Int
    
    // Fast count query that does not load wallpaper data.
    suspend fun getDownloadedWallpaperCount(): Int
}
