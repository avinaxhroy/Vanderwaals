package me.avinas.vanderwaals.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing user interaction history with wallpapers.
 * 
 * Tracks all user interactions with wallpapers including:
 * - Explicit feedback (likes, dislikes)
 * - Implicit feedback (wallpaper duration)
 * - Application and removal timestamps
 * - Download status
 * - Contextual information (time, battery, brightness)
 * 
 * This data is used for:
 * - Displaying history in the UI (chronological list)
 * - Learning user preferences through feedback
 * - Preventing duplicate wallpapers in rotation
 * - Category preference tracking
 * - Contextual recommendations (future enhancement)
 * - Analytics and usage patterns
 * 
 * **Auto-cleanup:**
 * Keeps last 100 entries per user, auto-deletes older records to prevent bloat.
 * 
 * **Database Indexes:**
 * - `wallpaperId`: Fast lookup for checking if wallpaper was previously applied
 * - `appliedAt`: Enables efficient chronological sorting and time-based queries
 * 
 * **User Feedback Values:**
 * - "like": User explicitly liked the wallpaper (heart icon)
 * - "dislike": User explicitly disliked the wallpaper (thumbs down icon)
 * - null: No explicit feedback provided
 * 
 * @property id Auto-generated unique identifier for the history entry
 * @property wallpaperId Reference to WallpaperMetadata.id
 * @property appliedAt Timestamp when wallpaper was applied (milliseconds since epoch)
 * @property removedAt Timestamp when wallpaper was removed (null if currently active)
 * @property userFeedback User's explicit feedback: "like", "dislike", or null
 * @property downloadedToStorage Whether the wallpaper was downloaded to device storage
 * @property feedbackContext Contextual information when feedback was provided (null if no feedback or legacy data)
 */
@Entity(
    tableName = "wallpaper_history",
    indices = [
        Index(value = ["wallpaperId"]),
        Index(value = ["appliedAt"])
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
    /**
     * Calculates the duration the wallpaper was displayed in seconds.
     * 
     * Used for implicit feedback learning:
     * - Duration < 5 minutes: Strong dislike (user changed quickly)
     * - Duration > 24 hours: Strong like (user kept it for a long time)
     * - Duration in between: Neutral (no learning applied)
     * 
     * @return Duration in seconds, or null if wallpaper is still active (removedAt is null)
     */
    fun getDurationSeconds(): Long? {
        return removedAt?.let { (it - appliedAt) / 1000 }
    }

    /**
     * Checks if the wallpaper is currently active (not yet removed).
     * 
     * @return true if this is the current wallpaper, false if it was removed
     */
    fun isActive(): Boolean {
        return removedAt == null
    }

    /**
     * Checks if the user provided explicit feedback (like or dislike).
     * 
     * @return true if user liked or disliked, false if no feedback
     */
    fun hasFeedback(): Boolean {
        return userFeedback != null
    }

    companion object {
        /**
         * Feedback constant for liked wallpapers.
         */
        const val FEEDBACK_LIKE = "like"

        /**
         * Feedback constant for disliked wallpapers.
         */
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
