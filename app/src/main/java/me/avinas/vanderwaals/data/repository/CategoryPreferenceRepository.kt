package me.avinas.vanderwaals.data.repository

import kotlinx.coroutines.flow.Flow
import me.avinas.vanderwaals.data.entity.CategoryPreference

/**
 * Repository for category-level preference tracking.
 * 
 * Responsibilities:
 * - Managing category preference data (likes, dislikes, views)
 * - Calculating category preference scores
 * - Tracking temporal usage patterns (when categories were last shown)
 * - Providing category-based exploration strategies
 * 
 * Category preferences work alongside embedding-based preferences to:
 * - Add interpretable category bonuses to ranking
 * - Enable category-aware diversity in wallpaper selection
 * - Prevent over-exploitation of single categories
 * 
 * Preference score formula:
 * ```
 * score = (likes - 2×dislikes) / (views + 1)
 * ```
 * 
 * @see me.avinas.vanderwaals.data.dao.CategoryPreferenceDao
 * @see me.avinas.vanderwaals.algorithm.SimilarityCalculator
 */
interface CategoryPreferenceRepository {
    /**
     * Get all category preferences as a Flow.
     * Emits updates whenever any category preference changes.
     */
    fun getAllCategoryPreferences(): Flow<List<CategoryPreference>>
    
    /**
     * Get preference for a specific category as a Flow.
     * Emits null if category hasn't been encountered yet.
     * 
     * @param category The category name to query
     */
    fun getCategoryPreference(category: String): Flow<CategoryPreference?>
    
    /**
     * Get preference for a specific category synchronously.
     * 
     * @param category The category name to query
     * @return CategoryPreference if exists, null otherwise
     */
    suspend fun getByCategory(category: String): CategoryPreference?
    
    /**
     * Get categories sorted by preference score.
     * Higher scores indicate user preference.
     * 
     * @return List of categories ordered by computed preference score (descending)
     */
    suspend fun getCategoriesByPreference(): List<String>
    
    /**
     * Get categories that haven't been shown recently.
     * Uses temporal diversity threshold to avoid repetition.
     * 
     * @param minTimeSinceShown Minimum time in milliseconds since last shown
     * @return List of categories not shown within the time window
     */
    suspend fun getUnderutilizedCategories(minTimeSinceShown: Long): List<String>
    
    /**
     * Record that a wallpaper from this category was viewed.
     * Increments view count and updates last shown timestamp.
     * 
     * @param category The category being viewed
     */
    suspend fun recordView(category: String)
    
    /**
     * Record that a wallpaper from this category was liked.
     * Increments both view count and like count.
     * 
     * @param category The category being liked
     */
    suspend fun recordLike(category: String)
    
    /**
     * Record that a wallpaper from this category was disliked.
     * Increments both view count and dislike count.
     * 
     * @param category The category being disliked
     */
    suspend fun recordDislike(category: String)
    
    /**
     * Get preference score for a category.
     * Returns 0.0 if category hasn't been encountered.
     * 
     * Score formula: (likes - 2×dislikes) / (views + 1)
     * - Positive: User likes this category
     * - Negative: User dislikes this category
     * - Near zero: Neutral or unexplored
     * 
     * @param category The category to compute score for
     * @return Preference score in range [-2.0, 1.0] typically
     */
    suspend fun getCategoryScore(category: String): Double
    
    /**
     * Reset all category preferences.
     * Useful for starting fresh or debugging.
     */
    suspend fun clearAllPreferences()
}
