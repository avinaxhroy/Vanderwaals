package me.avinas.vanderwaals.data.repository

import kotlinx.coroutines.flow.Flow
import me.avinas.vanderwaals.data.entity.DownloadQueueItem
import me.avinas.vanderwaals.data.entity.WallpaperHistory
import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import me.avinas.vanderwaals.domain.usecase.FeedbackType
import java.io.File

/**
 * Repository for wallpaper metadata and content management.
 * 
 * Responsibilities:
 * - Syncing manifest.json from GitHub weekly
 * - Populating local database with wallpaper metadata
 * - Downloading wallpaper images from GitHub raw URLs or Bing API
 * - Managing local cache (450 MB, ~150 wallpapers)
 * - Providing wallpapers filtered by user preferences
 * 
 * Coordinates between:
 * - Network layer (API calls to GitHub/Bing)
 * - Local database (Room)
 * - Algorithm layer (similarity ranking)
 * 
 * @see me.avinas.vanderwaals.data.dao.WallpaperMetadataDao
 * @see me.avinas.vanderwaals.network.GitHubApiService
 */
interface WallpaperRepository {
    
    /**
     * Retrieves all wallpapers from the catalog as a reactive Flow.
     * 
     * Returns Flow that emits complete wallpaper list whenever database is updated
     * (e.g., after manifest sync, after adding new wallpapers).
     * 
     * **Use Cases:**
     * - Loading embeddings for similarity calculations
     * - Populating download queue with top matches
     * - Category filtering and browsing
     * 
     * @return Flow<List<WallpaperMetadata>> emitting all wallpapers from catalog
     */
    fun getAllWallpapers(): Flow<List<WallpaperMetadata>>
    
    /**
     * Retrieves only downloaded wallpapers that are ready for display.
     * 
     * Returns Flow that emits wallpapers whose files have been successfully
     * downloaded to device storage and are ready to be applied.
     * 
     * **Use Cases:**
     * - Selecting next wallpaper for display
     * - Checking available wallpapers before scheduling change
     * - UI display of ready wallpapers count
     * 
     * @return Flow<List<WallpaperMetadata>> emitting downloaded wallpapers
     */
    fun getDownloadedWallpapers(): Flow<List<WallpaperMetadata>>
    
    /**
     * Retrieves wallpapers filtered by category as a reactive Flow.
     * 
     * Categories come from GitHub repo folder structure (e.g., "gruvbox",
     * "nord", "nature", "minimal").
     * 
     * **Use Cases:**
     * - Category browsing in UI
     * - Filtering recommendations by preferred categories
     * - Category-specific statistics
     * 
     * @param category Category name to filter by
     * @return Flow<List<WallpaperMetadata>> emitting wallpapers in category
     */
    fun getWallpapersByCategory(category: String): Flow<List<WallpaperMetadata>>
    
    /**
     * Adds wallpapers to the download queue for background downloading.
     * 
     * Wallpapers are prioritized based on similarity scores and downloaded
     * by WorkManager in the background. Top 50 wallpapers are typically queued.
     * 
     * **Queue Management:**
     * - Replaces existing queue with new priorities
     * - Triggers background download worker
     * - Maintains download status and retry counts
     * 
     * @param wallpapers List of wallpapers to add to download queue
     */
    suspend fun addToDownloadQueue(wallpapers: List<WallpaperMetadata>)
    
    /**
     * Adds queue items to the download queue without clearing existing items.
     * 
     * Uses REPLACE strategy, so existing items with same wallpaperId will be updated.
     * This is useful for smart pre-downloading where we want to add new items
     * without disrupting the existing queue.
     * 
     * **Use Cases:**
     * - Smart pre-downloading after wallpaper change
     * - Adding high-priority wallpapers without clearing queue
     * - Updating priorities for specific wallpapers
     * 
     * @param queueItems List of queue items to insert/update
     */
    suspend fun insertQueueItems(queueItems: List<DownloadQueueItem>)
    
    /**
     * Retrieves the next batch of wallpapers to download from the queue.
     * 
     * Returns wallpapers that:
     * - Have not been downloaded yet
     * - Are within retry limit (< 3 attempts)
     * - Are sorted by priority (highest first)
     * 
     * **Use Cases:**
     * - Background download worker fetching next batch
     * - Manual retry of failed downloads
     * - Queue status display in UI
     * 
     * @param limit Maximum number of wallpapers to return (default: 10)
     * @return List of queue items ready for download
     */
    suspend fun getNextToDownload(limit: Int = 10): List<DownloadQueueItem>
    
    /**
     * Marks a wallpaper as successfully downloaded.
     * 
     * Updates download queue status and makes wallpaper available for selection.
     * 
     * @param wallpaperId ID of the wallpaper that was downloaded
     */
    suspend fun markAsDownloaded(wallpaperId: String)
    
    /**
     * Records when a wallpaper is applied to the lock/home screen.
     * 
     * Creates history entry with:
     * - Application timestamp
     * - Wallpaper ID reference
     * - Initial feedback status (null)
     * 
     * Returns history ID that can be used to update feedback later.
     * 
     * **Use Cases:**
     * - Tracking wallpaper rotation
     * - Preventing duplicate wallpapers
     * - Learning from implicit feedback (duration)
     * - Displaying history in UI
     * 
     * @param wallpaper The wallpaper that was applied
     * @return History entry ID for later updates
     */
    suspend fun recordWallpaperApplied(wallpaper: WallpaperMetadata): Long
    
    /**
     * Updates history entry with user feedback.
     * 
     * Called when user provides explicit feedback (like/dislike) on a wallpaper.
     * Typically called from the history screen or quick action buttons.
     * 
     * @param historyId ID of the history entry to update
     * @param feedback Type of feedback (LIKE or DISLIKE)
     */
    suspend fun updateHistory(historyId: Long, feedback: FeedbackType)
    
    /**
     * Updates history entry with user feedback and contextual information.
     * 
     * Called when user provides explicit feedback with context tracking.
     * Captures time, battery, brightness for future contextual recommendations.
     * 
     * @param historyId ID of the history entry to update
     * @param feedback Type of feedback (LIKE or DISLIKE)
     * @param context Contextual information (time, battery, brightness)
     */
    suspend fun updateHistoryWithContext(historyId: Long, feedback: FeedbackType, context: me.avinas.vanderwaals.data.entity.FeedbackContext)
    
    /**
     * Retrieves wallpaper application history as a reactive Flow.
     * 
     * Returns Flow that emits history entries ordered by application time
     * (newest first). Limited to last 100 entries.
     * 
     * **Use Cases:**
     * - Displaying history screen in UI
     * - Checking recently applied wallpapers
     * - Preventing duplicate wallpapers in rotation
     * 
     * @return Flow<List<WallpaperHistory>> emitting history entries
     */
    fun getHistory(): Flow<List<WallpaperHistory>>
    
    /**
     * Downloads a wallpaper image file from remote URL.
     * 
     * Uses OkHttp to download wallpaper from GitHub raw URL or jsDelivr CDN.
     * Saves file to app cache directory with proper naming.
     * 
     * **File Management:**
     * - Files saved as: cache/wallpapers/{wallpaperId}.jpg
     * - Automatic retry with exponential backoff on failure
     * - Network checks (WiFi preferred, avoid cellular data)
     * - Battery optimization (skip if battery low)
     * 
     * **Error Handling:**
     * - Network errors: Returns failure, increments retry count
     * - Storage errors: Returns failure with specific error message
     * - HTTP errors: Logs status code and returns failure
     * 
     * @param wallpaper Wallpaper metadata containing download URL
     * @return Result<File> containing downloaded file on success,
     *         or error description on failure
     */
    suspend fun downloadWallpaper(wallpaper: WallpaperMetadata): Result<File>
    
    /**
     * Deletes a wallpaper file from local storage.
     * 
     * Removes wallpaper file from cache directory and updates download status
     * in the queue. Used for cache management and manual deletions.
     * 
     * **Use Cases:**
     * - Cache cleanup (remove old/least-used wallpapers)
     * - Manual deletion from history screen
     * - Making space for new downloads
     * 
     * @param wallpaper Wallpaper to delete
     * @return Result<Unit> indicating success or failure
     */
    suspend fun deleteWallpaper(wallpaper: WallpaperMetadata): Result<Unit>
    
    /**
     * Marks a wallpaper in history as removed at the specified timestamp.
     * 
     * Called when a wallpaper is replaced by a new one. Sets the removedAt
     * field which enables duration calculation for implicit feedback.
     * 
     * @param historyId ID of the history entry to mark as removed
     * @param timestamp Removal timestamp in milliseconds since epoch
     */
    suspend fun markWallpaperRemoved(historyId: Long, timestamp: Long)
    
    /**
     * Retrieves a single history entry by ID.
     * 
     * Used after updating removedAt to get the complete entry with duration
     * for implicit feedback processing.
     * 
     * @param historyId ID of the history entry to retrieve
     * @return History entry or null if not found
     */
    suspend fun getHistoryEntry(historyId: Long): WallpaperHistory?

    /**
     * Gets the File object for the pre-cropped wallpaper.
     * File path: cache/wallpapers/{wallpaperId}_cropped.jpg
     *
     * @param wallpaper Wallpaper metadata
     * @return File object pointing to the cropped wallpaper
     */
    fun getCroppedWallpaperFile(wallpaper: WallpaperMetadata): File
}
