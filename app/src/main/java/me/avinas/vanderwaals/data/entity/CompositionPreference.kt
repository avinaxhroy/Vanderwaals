package me.avinas.vanderwaals.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Learned composition/layout preferences (single row, id = 1), built from
 * wallpapers that received positive feedback.
 */
@Entity(tableName = "composition_preferences")
data class CompositionPreference(
    @PrimaryKey
    val id: Int = 1,  // Single row for global preferences
    
    // Average values from liked wallpapers
    val averageSymmetry: Float = 0.5f,
    val averageRuleOfThirds: Float = 0.5f,
    val averageCenterWeight: Float = 0.5f,
    val averageEdgeDensity: Float = 0.5f,
    val averageComplexity: Float = 0.5f,
    
    // Preference tendencies (boolean-like, 0-1 range)
    val prefersHorizontalSymmetry: Float = 0.5f,  // >0.6 = prefers, <0.4 = avoids
    val prefersVerticalSymmetry: Float = 0.5f,
    val prefersCenteredComposition: Float = 0.5f,
    val prefersEdgeDetail: Float = 0.5f,
    
    // Metadata
    val sampleCount: Int = 0,  // Number of wallpapers analyzed
    val lastUpdated: Long = System.currentTimeMillis()
) {
    /** Confidence ramps 0→100% over 0→10 samples. */
    fun calculateConfidence(): Float {
        return (sampleCount / 10f).coerceIn(0f, 1f)
    }
    
    fun hasStrongSymmetryPreference(): Boolean {
        val horizontalStrong = prefersHorizontalSymmetry < 0.3f || prefersHorizontalSymmetry > 0.7f
        val verticalStrong = prefersVerticalSymmetry < 0.3f || prefersVerticalSymmetry > 0.7f
        return horizontalStrong || verticalStrong
    }
    
    fun prefersCentered(): Boolean {
        return prefersCenteredComposition > 0.6f
    }
    
    fun prefersEdges(): Boolean {
        return prefersEdgeDetail > 0.6f
    }
    
    fun prefersComplex(): Boolean {
        return averageComplexity > 0.6f
    }
    
    fun prefersMinimal(): Boolean {
        return averageComplexity < 0.4f
    }
    
    companion object {
        fun createDefault(): CompositionPreference {
            return CompositionPreference(
                id = 1,
                averageSymmetry = 0.5f,
                averageRuleOfThirds = 0.5f,
                averageCenterWeight = 0.5f,
                averageEdgeDensity = 0.5f,
                averageComplexity = 0.5f,
                prefersHorizontalSymmetry = 0.5f,
                prefersVerticalSymmetry = 0.5f,
                prefersCenteredComposition = 0.5f,
                prefersEdgeDetail = 0.5f,
                sampleCount = 0,
                lastUpdated = System.currentTimeMillis()
            )
        }
    }
}
