package me.avinas.vanderwaals.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity for storing user's composition and layout preferences learned from feedback.
 * 
 * Tracks preferred composition patterns:
 * - Symmetry preferences (horizontal, vertical)
 * - Rule of thirds usage (power point positioning)
 * - Center vs edge weight preferences
 * - Complexity preferences (busy vs simple)
 * - Edge density preferences (borders vs open)
 * 
 * Built from analyzing wallpapers that received positive feedback.
 * Used to rank future wallpapers based on composition similarity.
 * 
 * @property id Primary key (always 1, single row for global preferences)
 * @property averageSymmetry Preferred symmetry level (0-1, higher = more symmetric)
 * @property averageRuleOfThirds Preferred rule of thirds score (0-1)
 * @property averageCenterWeight Preferred center weight (0-1, higher = centered subject)
 * @property averageEdgeDensity Preferred edge density (0-1, higher = detail at edges)
 * @property averageComplexity Preferred complexity (0-1, higher = more detailed)
 * @property prefersHorizontalSymmetry Tendency toward horizontal symmetry
 * @property prefersVerticalSymmetry Tendency toward vertical symmetry
 * @property prefersCenteredComposition Tendency toward centered subjects
 * @property prefersEdgeDetail Tendency toward detail at image borders
 * @property sampleCount Number of wallpapers analyzed (for confidence weighting)
 * @property lastUpdated Timestamp of last update
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
    /**
     * Calculates confidence level based on sample count.
     * 
     * 0 samples: 0% confidence
     * 5 samples: 50% confidence
     * 10+ samples: 100% confidence
     * 
     * @return Confidence (0.0 to 1.0)
     */
    fun calculateConfidence(): Float {
        return (sampleCount / 10f).coerceIn(0f, 1f)
    }
    
    /**
     * Checks if user has strong symmetry preference.
     * 
     * @return True if user clearly prefers or avoids symmetry
     */
    fun hasStrongSymmetryPreference(): Boolean {
        val horizontalStrong = prefersHorizontalSymmetry < 0.3f || prefersHorizontalSymmetry > 0.7f
        val verticalStrong = prefersVerticalSymmetry < 0.3f || prefersVerticalSymmetry > 0.7f
        return horizontalStrong || verticalStrong
    }
    
    /**
     * Checks if user prefers centered compositions.
     * 
     * @return True if center weight preference > 0.6
     */
    fun prefersCentered(): Boolean {
        return prefersCenteredComposition > 0.6f
    }
    
    /**
     * Checks if user prefers edge detail.
     * 
     * @return True if edge detail preference > 0.6
     */
    fun prefersEdges(): Boolean {
        return prefersEdgeDetail > 0.6f
    }
    
    /**
     * Checks if user prefers complex/busy compositions.
     * 
     * @return True if complexity preference > 0.6
     */
    fun prefersComplex(): Boolean {
        return averageComplexity > 0.6f
    }
    
    /**
     * Checks if user prefers simple/minimal compositions.
     * 
     * @return True if complexity preference < 0.4
     */
    fun prefersMinimal(): Boolean {
        return averageComplexity < 0.4f
    }
    
    companion object {
        /**
         * Creates default composition preference with neutral values.
         * Used before any feedback is collected.
         */
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
