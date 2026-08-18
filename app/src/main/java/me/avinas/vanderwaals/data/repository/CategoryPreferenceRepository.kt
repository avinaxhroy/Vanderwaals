package me.avinas.vanderwaals.data.repository

import kotlinx.coroutines.flow.Flow
import me.avinas.vanderwaals.data.entity.CategoryPreference

interface CategoryPreferenceRepository {
    /**
     * Emits updates whenever any category preference changes.
     */
    fun getAllCategoryPreferences(): Flow<List<CategoryPreference>>
    
    /**
     * Emits null if category hasn't been encountered yet.
     */
    fun getCategoryPreference(category: String): Flow<CategoryPreference?>
    
    suspend fun getByCategory(category: String): CategoryPreference?
    
    suspend fun getCategoriesByPreference(): List<String>
    
    /**
     * Uses temporal diversity threshold to avoid repetition.
     */
    suspend fun getUnderutilizedCategories(minTimeSinceShown: Long): List<String>
    
    suspend fun recordView(category: String)
    
    suspend fun recordLike(category: String)
    
    suspend fun recordDislike(category: String)
    
    /**
     * Score formula: (likes - 2×dislikes) / (views + 1), range [-2.0, 1.0].
     */
    suspend fun getCategoryScore(category: String): Double
    
    // Useful for starting fresh or debugging.
    suspend fun clearAllPreferences()
}
