package me.avinas.vanderwaals.data.repository

import kotlinx.coroutines.flow.Flow
import me.avinas.vanderwaals.data.entity.ColorPreference

/**
 * Repository for color-level preference tracking.
 * 
 * Responsibilities:
 * - Managing color preference data (likes, dislikes, views)
 * - Calculating color preference scores
 * - Tracking temporal usage patterns (when colors were last shown)
 * - Providing fallback personalization when categories are missing
 * 
 * Color preferences work as a fallback to category preferences:
 * - If wallpaper has category: use category boost (15% weight)
 * - If wallpaper has no category: use color similarity boost (10% weight)
 * - Uses RGB Euclidean distance for color matching
 * - Compares against user's liked color palette
 * 
 * Preference score formula:
 * ```
 * score = (likes - 2×dislikes) / (likes + dislikes + 1)
 * ```
 * 
 * @see me.avinas.vanderwaals.data.dao.ColorPreferenceDao
 * @see me.avinas.vanderwaals.algorithm.SimilarityCalculator
 */
interface ColorPreferenceRepository {
    /**
     * Get all color preferences as a Flow.
     * Emits updates whenever any color preference changes.
     */
    fun getAllColorPreferences(): Flow<List<ColorPreference>>
    
    /**
     * Get preference for a specific color as a Flow.
     * Emits null if color hasn't been encountered yet.
     * 
     * @param colorHex Hex color code (e.g., "#FF5733")
     */
    fun getColorPreference(colorHex: String): Flow<ColorPreference?>
    
    /**
     * Get colors sorted by preference score.
     * Higher scores indicate user preference.
     * 
     * @return List of color hex codes ordered by preference score (descending)
     */
    suspend fun getColorsByPreference(): List<String>
    
    /**
     * Get liked colors (likes > dislikes).
     * Used to build user's preferred color palette.
     * 
     * @return List of color hex codes the user tends to like
     */
    suspend fun getLikedColors(): List<String>
    
    /**
     * Get disliked colors (dislikes > likes).
     * Used to avoid colors user tends to dislike.
     * 
     * @return List of color hex codes the user tends to dislike
     */
    suspend fun getDislikedColors(): List<String>
    
    /**
     * Get colors that haven't been shown recently.
     * Uses temporal diversity threshold to avoid repetition.
     * 
     * @param minTimeSinceShown Minimum time in milliseconds since last shown
     * @return List of colors not shown within the time window
     */
    suspend fun getUnderutilizedColors(minTimeSinceShown: Long): List<String>
    
    /**
     * Record that a wallpaper with this color was viewed.
     * Increments view count and updates last shown timestamp.
     * 
     * @param colorHex Hex color code being viewed
     */
    suspend fun recordView(colorHex: String)
    
    /**
     * Record that wallpapers with these colors were viewed.
     * Batch operation for multiple colors.
     * 
     * @param colors List of hex color codes being viewed
     */
    suspend fun recordViews(colors: List<String>)
    
    /**
     * Record that a wallpaper with this color was liked.
     * Increments both view count and like count.
     * 
     * @param colorHex Hex color code being liked
     */
    suspend fun recordLike(colorHex: String)
    
    /**
     * Record that wallpapers with these colors were liked.
     * Batch operation for multiple colors (typically top 3 from palette).
     * 
     * @param colors List of hex color codes being liked
     */
    suspend fun recordLikes(colors: List<String>)
    
    /**
     * Record that a wallpaper with this color was disliked.
     * Increments both view count and dislike count.
     * 
     * @param colorHex Hex color code being disliked
     */
    suspend fun recordDislike(colorHex: String)
    
    /**
     * Record that wallpapers with these colors were disliked.
     * Batch operation for multiple colors (typically top 3 from palette).
     * 
     * @param colors List of hex color codes being disliked
     */
    suspend fun recordDislikes(colors: List<String>)
    
    /**
     * Get preference score for a color.
     * Returns 0.0 if color hasn't been encountered.
     * 
     * Score formula: (likes - 2×dislikes) / (likes + dislikes + 1)
     * - Positive: User likes this color
     * - Negative: User dislikes this color
     * - Near zero: Neutral or unexplored
     * 
     * @param colorHex Hex color code to compute score for
     * @return Preference score in range [-2.0, 1.0] typically
     */
    suspend fun getColorScore(colorHex: String): Double
    
    /**
     * Calculate average score for a list of colors.
     * Useful for wallpapers with multiple dominant colors.
     * 
     * @param colors List of hex color codes
     * @return Average preference score across all colors
     */
    suspend fun getAverageColorScore(colors: List<String>): Double
    
    /**
     * Reset all color preferences.
     * Useful for starting fresh or debugging.
     */
    suspend fun resetAllColorPreferences()
    
    /**
     * Get total count of tracked colors.
     * 
     * @return Number of colors with recorded preferences
     */
    suspend fun getColorCount(): Int
}
