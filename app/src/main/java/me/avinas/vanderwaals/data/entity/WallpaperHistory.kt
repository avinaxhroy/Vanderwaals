package me.avinas.vanderwaals.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tracks when each wallpaper was applied/removed, user feedback ("like"/"dislike"/null),
 * and implicit duration signal. Auto-cleaned to keep last 100 entries.
 */
@Entity(
    tableName = "wallpaper_history",
    indices = [
        Index(value = ["wallpaperId"]),
        Index(value = ["appliedAt"]),
        Index(value = ["userFeedback"]),
        Index(value = ["removedAt"])
    ]
)
data class WallpaperHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val wallpaperId: String,
    val appliedAt: Long,
    val removedAt: Long?,
    val userFeedback: String?,
    val downloadedToStorage: Boolean,
    val feedbackContext: FeedbackContext? = null
) {
    fun getDurationSeconds(): Long? {
        return removedAt?.let { (it - appliedAt) / 1000 }
    }

    fun isActive(): Boolean {
        return removedAt == null
    }

    fun hasFeedback(): Boolean {
        return userFeedback != null
    }

    companion object {
        const val FEEDBACK_LIKE = "like"

        const val FEEDBACK_DISLIKE = "dislike"

        /**
         * Maximum number of history entries to keep per user.
         * Older entries are automatically deleted.
         */
        const val MAX_HISTORY_ENTRIES = 100

        /**
         * Duration threshold in milliseconds for implicit dislike (5 minutes).
         * If wallpaper is removed before this, it's considered a strong dislike.
         */
        const val IMPLICIT_DISLIKE_THRESHOLD_MS = 5 * 60 * 1000L // 5 minutes

        /**
         * Duration threshold in milliseconds for implicit like (24 hours).
         * If wallpaper is kept longer than this, it's considered a strong like.
         */
        const val IMPLICIT_LIKE_THRESHOLD_MS = 24 * 60 * 60 * 1000L // 24 hours
    }
}
