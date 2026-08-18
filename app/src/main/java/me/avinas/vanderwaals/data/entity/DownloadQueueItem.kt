package me.avinas.vanderwaals.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Priority-ranked queue entry; top 50 wallpapers are downloaded via WorkManager. */
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
    /** True while retryCount < MAX_RETRY_COUNT; beyond that the item is failed. */
    fun shouldRetry(): Boolean {
        return retryCount < MAX_RETRY_COUNT
    }

    fun getRetryDelayMs(): Long {
        return RETRY_BASE_DELAY_MS * (1 shl retryCount) // Bit shift for 2^retryCount
    }

    fun isReadyForDownload(): Boolean {
        return !downloaded && shouldRetry()
    }

    companion object {
        /** Maximum download retry attempts before marking as failed. */
        const val MAX_RETRY_COUNT = 3

        /** Base delay for exponential backoff in milliseconds (5 seconds). */
        const val RETRY_BASE_DELAY_MS = 5000L

        /** Maximum items kept in the download queue (top 50 by priority). */
        const val MAX_QUEUE_SIZE = 50

        /** Minimum priority to enqueue; skips wallpapers with very low similarity. */
        const val MIN_PRIORITY_THRESHOLD = 0.3f

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
