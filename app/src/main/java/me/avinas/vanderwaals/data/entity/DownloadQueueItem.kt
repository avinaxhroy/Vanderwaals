package me.avinas.vanderwaals.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Priority-ranked download queue entry. Top 50 wallpapers are queued
 * by similarity/appeal score and downloaded via WorkManager.
 *
 * @property wallpaperId Reference to WallpaperMetadata.id (primary key)
 * @property priority Score 0.0–1.0 (higher = download first)
 * @property downloaded Whether the file has been fetched
 * @property retryCount Failed download attempts (for backoff)
 */
@Entity(
    tableName = "download_queue",
    indices = [
        Index(value = ["priority"]),
        Index(value = ["downloaded"]),
        Index(value = ["downloaded", "priority"])
    ]
)
data class DownloadQueueItem(
    @PrimaryKey
    val wallpaperId: String,
    val priority: Float,
    val downloaded: Boolean,
    val retryCount: Int
) {
    /**
     * Checks if this item should be retried based on retry count.
     * 
     * Max retries is 3 attempts. After that, item is considered failed
     * and should be removed from queue or marked for manual retry.
     * 
     * @return true if should retry, false if max retries exceeded
     */
    fun shouldRetry(): Boolean {
        return retryCount < MAX_RETRY_COUNT
    }

    /**
     * Calculates exponential backoff delay for retry attempts.
     * 
     * Delay formula: BASE_DELAY * (2 ^ retryCount)
     * - Attempt 0: 5 seconds
     * - Attempt 1: 10 seconds
     * - Attempt 2: 20 seconds
     * - Attempt 3: 40 seconds
     * 
     * @return Delay in milliseconds before next retry
     */
    fun getRetryDelayMs(): Long {
        return RETRY_BASE_DELAY_MS * (1 shl retryCount) // Bit shift for 2^retryCount
    }

    /**
     * Checks if this item is ready for download (not downloaded and within retry limit).
     * 
     * @return true if item can be downloaded, false otherwise
     */
    fun isReadyForDownload(): Boolean {
        return !downloaded && shouldRetry()
    }

    companion object {
        /**
         * Maximum number of download retry attempts before marking as failed.
         */
        const val MAX_RETRY_COUNT = 3

        /**
         * Base delay for exponential backoff in milliseconds (5 seconds).
         */
        const val RETRY_BASE_DELAY_MS = 5000L

        /**
         * Maximum number of items to keep in download queue.
         * Top 50 wallpapers are queued based on priority.
         */
        const val MAX_QUEUE_SIZE = 50

        /**
         * Minimum priority score to be added to queue (0.0 to 1.0).
         * Prevents downloading wallpapers with very low similarity.
         */
        const val MIN_PRIORITY_THRESHOLD = 0.3f

        /**
         * Creates a new DownloadQueueItem for a wallpaper.
         * 
         * @param wallpaperId ID of the wallpaper to queue
         * @param priority Similarity/appeal score
         * @return New queue item ready for download
         */
        fun create(wallpaperId: String, priority: Float): DownloadQueueItem {
            return DownloadQueueItem(
                wallpaperId = wallpaperId,
                priority = priority,
                downloaded = false,
                retryCount = 0
            )
        }
    }
}
