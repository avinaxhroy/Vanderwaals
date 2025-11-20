package me.avinas.vanderwaals.data.repository

import kotlinx.coroutines.flow.Flow
import me.avinas.vanderwaals.data.dao.CompositionPreferenceDao
import me.avinas.vanderwaals.data.entity.CompositionPreference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing composition preferences.
 * 
 * Provides access to user's learned composition/layout preferences:
 * - Load current preferences
 * - Update preferences after feedback
 * - Calculate composition similarity scores
 * 
 * @property compositionPreferenceDao DAO for database access
 */
@Singleton
class CompositionPreferenceRepository @Inject constructor(
    private val compositionPreferenceDao: CompositionPreferenceDao
) {
    
    /**
     * Gets composition preferences as Flow for reactive updates.
     * 
     * @return Flow of composition preferences (null if not initialized)
     */
    fun getCompositionPreferences(): Flow<CompositionPreference?> {
        return compositionPreferenceDao.getCompositionPreferencesFlow()
    }
    
    /**
     * Gets composition preferences synchronously (single read).
     * 
     * @return Composition preferences or null if not initialized
     */
    suspend fun getCompositionPreferencesOnce(): CompositionPreference? {
        return compositionPreferenceDao.getCompositionPreferences()
    }
    
    /**
     * Inserts or replaces composition preferences.
     * 
     * @param preferences Composition preferences to save
     */
    suspend fun insertCompositionPreferences(preferences: CompositionPreference) {
        compositionPreferenceDao.insert(preferences)
    }
    
    /**
     * Updates composition preferences with new feedback.
     * 
     * Uses exponential moving average (EMA) to smoothly incorporate new data:
     * newValue = oldValue × (1 - learningRate) + newSample × learningRate
     * 
     * @param newComposition Composition analysis from latest feedback
     * @param learningRate How much weight to give new sample (0.0 to 1.0)
     */
    suspend fun updatePreferences(
        newComposition: me.avinas.vanderwaals.algorithm.CompositionAnalysis,
        learningRate: Float = 0.2f
    ) {
        val current = getCompositionPreferencesOnce() ?: CompositionPreference.createDefault()
        
        // Apply EMA to each metric
        val updated = current.copy(
            averageSymmetry = lerp(current.averageSymmetry, newComposition.symmetryScore, learningRate),
            averageRuleOfThirds = lerp(current.averageRuleOfThirds, newComposition.ruleOfThirdsScore, learningRate),
            averageCenterWeight = lerp(current.averageCenterWeight, newComposition.centerWeight, learningRate),
            averageEdgeDensity = lerp(current.averageEdgeDensity, newComposition.edgeDensity, learningRate),
            averageComplexity = lerp(current.averageComplexity, newComposition.complexity, learningRate),
            
            // Update tendency preferences
            prefersHorizontalSymmetry = lerp(
                current.prefersHorizontalSymmetry, 
                if (newComposition.symmetryScore > 0.6f) 1f else 0f,
                learningRate
            ),
            prefersVerticalSymmetry = lerp(
                current.prefersVerticalSymmetry,
                if (newComposition.symmetryScore > 0.6f) 1f else 0f,
                learningRate
            ),
            prefersCenteredComposition = lerp(
                current.prefersCenteredComposition,
                if (newComposition.centerWeight > 0.6f) 1f else 0f,
                learningRate
            ),
            prefersEdgeDetail = lerp(
                current.prefersEdgeDetail,
                if (newComposition.edgeDensity > 0.6f) 1f else 0f,
                learningRate
            ),
            
            // Update metadata
            sampleCount = current.sampleCount + 1,
            lastUpdated = System.currentTimeMillis()
        )
        
        compositionPreferenceDao.update(updated)
    }
    
    /**
     * Deletes all composition preferences (reset).
     */
    suspend fun deleteAll() {
        compositionPreferenceDao.deleteAll()
    }
    
    /**
     * Linear interpolation helper.
     * 
     * @param a Start value
     * @param b End value
     * @param t Interpolation factor (0.0 to 1.0)
     * @return Interpolated value
     */
    private fun lerp(a: Float, b: Float, t: Float): Float {
        return a * (1f - t) + b * t
    }
}
