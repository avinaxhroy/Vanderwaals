package me.avinas.vanderwaals.data.entity

/**
 * Room entity representing user feedback history for wallpapers.
 * 
 * Tracks all user interactions with wallpapers including:
 * - Explicit feedback (likes, dislikes)
 * - Implicit feedback (wallpaper duration)
 * - Application and removal timestamps
 * 
 * This data is used for:
 * - Displaying history in the UI
 * - Learning user preferences
 * - Preventing duplicate wallpapers in rotation
 * - Category preference tracking
 * 
 * Keeps last 100 entries, auto-deletes older records.
 * 
 * @property id Unique identifier for the feedback event
 * @property wallpaperId Reference to WallpaperMetadata.id
 * @property appliedAt Timestamp when wallpaper was applied
 * @property removedAt Timestamp when wallpaper was removed (null if current)
 * @property feedbackType Type of feedback ("like", "dislike", "neutral", "implicit")
 * @property durationSeconds Duration wallpaper was displayed (for implicit feedback)
 */
data class FeedbackHistory(
    val id: String,
    val wallpaperId: String,
    val appliedAt: Long,
    val removedAt: Long?,
    val feedbackType: String,
    val durationSeconds: Long?
)
