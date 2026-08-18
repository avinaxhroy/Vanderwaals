package me.avinas.vanderwaals.data.model

/**
 * Per-category preference stats, used as a tiebreaker when embedding
 * similarities are close. score = (likes - 2×dislikes) / (likes + dislikes + 1).
 */
data class CategoryStats(
    val category: String,
    val likeCount: Int,
    val dislikeCount: Int,
    val categoryWeight: Float
)
