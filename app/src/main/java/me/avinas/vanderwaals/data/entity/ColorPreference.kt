package me.avinas.vanderwaals.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity tracking user preferences for specific colors.
 * 
 * This table stores aggregate feedback statistics for color hex codes,
 * enabling color-aware personalization when category information is missing.
 * 
 * **Usage:**
 * - Fallback personalization when wallpaper has blank/missing category
 * - Track which colors the user tends to like/dislike
 * - Boost wallpapers with similar color palettes during selection
 * - Enable personalization for uncategorized content
 * 
 * **Color Matching:**
 * - Uses RGB Euclidean distance for similarity calculation
 * - Compares against user's liked color palette
 * - Applied at ~10% weight in final ranking (lower than category boost)
 * 
 * **Color Score Calculation:**
 * ```
 * color_score = (likes - 2 × dislikes) / (likes + dislikes + 1)
 * 
 * Ranges from:
 * - +1.0: All likes, no dislikes (strong preference)
 * -  0.0: Neutral or no data
 * - -1.0: All dislikes, no likes (strong aversion)
 * ```
 * 
 * **Update Strategy:**
 * - Extract top 3 dominant colors from liked/disliked wallpapers
 * - Increment likes/dislikes for each color in the palette
 * - Increment views when wallpaper with this color is shown
 * - Update lastShown timestamp for temporal diversity
 * 
 * @property colorHex Hex color code (e.g., "#FF5733", "#3498DB") - Primary Key
 * @property likes Number of times user liked wallpapers containing this color
 * @property dislikes Number of times user disliked wallpapers containing this color
 * @property views Number of times wallpapers with this color were shown
 * @property lastShown Timestamp when color was last shown (milliseconds since epoch)
 */
@Entity(tableName = "color_preferences")
data class ColorPreference(
    @PrimaryKey
    val colorHex: String,
    val likes: Int = 0,
    val dislikes: Int = 0,
    val views: Int = 0,
    val lastShown: Long = 0L
) {
    /**
     * Calculates preference score for this color.
     * 
     * Formula: (likes - 2 × dislikes) / (likes + dislikes + 1)
     * 
     * - Dislikes weighted more heavily to avoid showing disliked colors
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
     * Checks if this color is underexplored.
     * A color is underexplored if it has few views relative to feedback potential.
     * 
     * @return True if should be shown more often for exploration
     */
    fun isUnderexplored(): Boolean {
        return views < 3 // Show at least 3 times before making judgment
    }
    
    /**
     * Checks if color was shown recently.
     * Used for color diversity enforcement.
     * 
     * @param withinMillis Time window to check (default: 24 hours)
     * @return True if shown within the specified time window
     */
    fun wasShownRecently(withinMillis: Long = 24 * 60 * 60 * 1000L): Boolean {
        return (System.currentTimeMillis() - lastShown) < withinMillis
    }
    
    /**
     * Parses hex color to RGB components.
     * 
     * @return Triple of (red, green, blue) values in range [0, 255]
     * @throws IllegalArgumentException if colorHex format is invalid
     */
    fun toRgb(): Triple<Int, Int, Int> {
        val hex = colorHex.removePrefix("#")
        require(hex.length == 6) { "Invalid hex color format: $colorHex" }
        
        val r = hex.substring(0, 2).toInt(16)
        val g = hex.substring(2, 4).toInt(16)
        val b = hex.substring(4, 6).toInt(16)
        
        return Triple(r, g, b)
    }
}
