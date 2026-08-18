package me.avinas.vanderwaals.data.repository

import kotlinx.coroutines.flow.Flow
import me.avinas.vanderwaals.data.dao.CompositionPreferenceDao
import me.avinas.vanderwaals.data.entity.CompositionPreference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CompositionPreferenceRepository @Inject constructor(
    private val compositionPreferenceDao: CompositionPreferenceDao
) {
    
    fun getCompositionPreferences(): Flow<CompositionPreference?> {
        return compositionPreferenceDao.getCompositionPreferencesFlow()
    }
    
    suspend fun getCompositionPreferencesOnce(): CompositionPreference? {
        return compositionPreferenceDao.getCompositionPreferences()
    }
    
    suspend fun insertCompositionPreferences(preferences: CompositionPreference) {
        compositionPreferenceDao.insert(preferences)
    }
    
    /**
     * Uses exponential moving average (EMA) to incorporate new data:
     * newValue = oldValue × (1 - learningRate) + newSample × learningRate
     */
    suspend fun updatePreferences(
        newComposition: me.avinas.vanderwaals.algorithm.CompositionAnalysis,
        learningRate: Float = 0.2f
    ) {
        val current = getCompositionPreferencesOnce() ?: CompositionPreference.createDefault()
        
        val updated = current.copy(
            averageSymmetry = lerp(current.averageSymmetry, newComposition.symmetryScore, learningRate),
            averageRuleOfThirds = lerp(current.averageRuleOfThirds, newComposition.ruleOfThirdsScore, learningRate),
            averageCenterWeight = lerp(current.averageCenterWeight, newComposition.centerWeight, learningRate),
            averageEdgeDensity = lerp(current.averageEdgeDensity, newComposition.edgeDensity, learningRate),
            averageComplexity = lerp(current.averageComplexity, newComposition.complexity, learningRate),
            
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
            
            sampleCount = current.sampleCount + 1,
            lastUpdated = System.currentTimeMillis()
        )
        
        compositionPreferenceDao.update(updated)
    }
    
    suspend fun deleteAll() {
        compositionPreferenceDao.deleteAll()
    }
    
    private fun lerp(a: Float, b: Float, t: Float): Float {
        return a * (1f - t) + b * t
    }
}
