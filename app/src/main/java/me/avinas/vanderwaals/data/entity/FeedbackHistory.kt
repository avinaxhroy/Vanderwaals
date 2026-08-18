package me.avinas.vanderwaals.data.entity

/** User feedback history for wallpapers; keeps the last 100 entries, auto-deleting older records. */
data class FeedbackHistory(
    val id: String,
    val wallpaperId: String,
    val appliedAt: Long,
    val removedAt: Long?,
    val feedbackType: String,
    val durationSeconds: Long?
)
