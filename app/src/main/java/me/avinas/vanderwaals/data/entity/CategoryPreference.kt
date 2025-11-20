package me.avinas.vanderwaals.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity tracking user preferences for wallpaper categories.
 * 
 * This table stores aggregate feedback statistics for each category,
 * enabling category-aware personalization and diversity enforcement.
 * 
 * **Usage:**
 * - Track which categories the user tends to like/dislike
 * - Boost wallpapers from preferred categories during selection
 * - Enforce diversity by occasionally showing underexplored categories
 * - Prevent filter bubbles by limiting overrepresented categories
 * 
 * **Category Score Calculation:**
 * ```
 * category_score = (likes - 2 × dislikes) / (likes + dislikes + 1)
 * 
 * Ranges from:
 * - +1.0: All likes, no dislikes (strong preference)
 * -  0.0: Neutral or no data
 * - -1.0: All dislikes, no likes (strong aversion)
 * ```
 * 
 * **Update Strategy:**
 * - Increment likes/dislikes when user gives explicit feedback
 * - Increment views when wallpaper from category is shown
 * - Update lastShown timestamp for temporal diversity tracking
 * 
 * @property category Category name (e.g., "nature", "minimal", "anime")
 * @property likes Number of times user liked wallpapers from this category
 * @property dislikes Number of times user disliked wallpapers from this category
 * @property views Number of times wallpapers from this category were shown
 * @property lastShown Timestamp when category was last shown (milliseconds since epoch)
 */
@Entity(tableName = "category_preferences")
data class CategoryPreference(
    @PrimaryKey
    val category: String,
    val likes: Int = 0,
    val dislikes: Int = 0,
    val views: Int = 0,
    val lastShown: Long = 0L
) {
    /**
     * Calculates preference score for this category.
     * 
     * Formula: (likes - 2 × dislikes) / (likes + dislikes + 1)
     * 
     * - Dislikes weighted more heavily to avoid showing disliked content
     * - +1 in denominator prevents division by zero and reduces impact of single feedback
     * 
     * @return Score from -1.0 (strong aversion) to +1.0 (strong preference)
     */
    fun calculateScore(): Float {
        val totalFeedback = likes + dislikes
        if (totalFeedback == 0) return 0f
        
        val weightedScore = likes - (2 * dislikes)
        return weightedScore.toFloat() / (totalFeedback + 1)
    }
    
    /**
     * Checks if this category is underexplored.
     * A category is underexplored if it has few views relative to feedback potential.
     * 
     * @return True if should be shown more often for exploration
     */
    fun isUnderexplored(): Boolean {
        return views < 3 // Show at least 3 times before making judgment
    }
    
    /**
     * Checks if category was shown recently.
     * Used for temporal diversity enforcement.
     * 
     * @param withinMillis Time window to check (default: 24 hours)
     * @return True if shown within the specified time window
     */
    fun wasShownRecently(withinMillis: Long = 24 * 60 * 60 * 1000L): Boolean {
        return (System.currentTimeMillis() - lastShown) < withinMillis
    }
}
