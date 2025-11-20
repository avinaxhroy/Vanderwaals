package me.avinas.vanderwaals.data.model

/**
 * Domain model representing user preference statistics for a wallpaper category.
 * 
 * Tracks likes and dislikes per category to provide category-based filtering
 * and boosting in the ranking algorithm. Categories are derived from GitHub
 * folder structures (e.g., "gruvbox", "nord", "nature", "minimal", "anime").
 * 
 * Category weight formula:
 * ```
 * category_weight = (likes - 2×dislikes) / (likes + dislikes + 1)
 * ```
 * 
 * Used as a tiebreaker when embedding similarities are close.
 * 
 * @property category Category name
 * @property likeCount Number of likes in this category
 * @property dislikeCount Number of dislikes in this category
 * @property categoryWeight Computed preference weight (-1.0 to 1.0)
 */
data class CategoryStats(
    val category: String,
    val likeCount: Int,
    val dislikeCount: Int,
    val categoryWeight: Float
)
