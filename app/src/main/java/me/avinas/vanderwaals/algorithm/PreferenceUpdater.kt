package me.avinas.vanderwaals.algorithm

import kotlin.math.sqrt

/**
 * Updates user preference vector using Enhanced Exponential Moving Average (EMA) with momentum.
 * 
 * This class implements an advanced learning algorithm that adapts the user's preference vector
 * in response to likes, dislikes, and implicit feedback (wallpaper duration).
 * 
 * **Enhanced Learning Features:**
 * - Momentum tracking to smooth out learning and prevent oscillation
 * - Adaptive learning rates based on feedback history
 * - Preference stability for well-established preferences
 * - Protection against overfitting to recent feedback
 * 
 * **Standard EMA Algorithm:**
 * ```
 * For likes:   preference_vector[i] += learning_rate × (liked_embedding[i] - preference_vector[i])
 * For dislikes: preference_vector[i] -= learning_rate × (disliked_embedding[i] - preference_vector[i])
 * ```
 * 
 * **With Momentum (Enhanced):**
 * ```
 * velocity[i] = momentum × velocity[i] + learning_rate × gradient[i]
 * preference_vector[i] += velocity[i]
 * ```
 * 
 * Adaptive learning rates:
 * - feedback_count < 10: rate_positive = 0.15, rate_negative = 0.20 (fast initial learning)
 * - feedback_count < 50: rate_positive = 0.10, rate_negative = 0.15 (moderate learning)
 * - feedback_count >= 50: rate_positive = 0.05, rate_negative = 0.10 (stable maintenance)
 * 
 * The preference vector is normalized to unit length after each update.
 * 
 * @see EmbeddingExtractor for generating embeddings
 * @see SimilarityCalculator for ranking wallpapers using updated preferences
 */
class PreferenceUpdater {
    
    companion object {
        /**
         * Momentum coefficient for smoothing updates.
         * Higher values = more momentum, slower adaptation.
         * Range: 0.0 (no momentum) to 1.0 (full momentum)
         */
        private const val MOMENTUM_COEFFICIENT = 0.3f
        
        /**
         * Maximum magnitude for velocity vector.
         * Prevents runaway updates from extreme feedback.
         */
        private const val MAX_VELOCITY_MAGNITUDE = 0.5f
    }
    
    /**
     * Updates preference vector with positive feedback (user liked wallpaper).
     * 
     * @param currentVector Current preference vector
     * @param targetEmbedding Embedding of the liked wallpaper
     * @param learningRate Learning rate for this update (0.0 - 1.0)
     * @param momentum Optional previous velocity vector for momentum (null = no momentum)
     * @return Pair of (updated preference vector, new velocity vector)
     */
    fun updateWithPositiveFeedback(
        currentVector: FloatArray,
        targetEmbedding: FloatArray,
        learningRate: Float,
        momentum: FloatArray? = null
    ): Pair<FloatArray, FloatArray> {
        if (currentVector.size != targetEmbedding.size) {
            return Pair(currentVector, FloatArray(currentVector.size))
        }
        
        return updateWithMomentum(
            currentVector = currentVector,
            targetEmbedding = targetEmbedding,
            learningRate = learningRate,
            momentum = momentum,
            isPositive = true
        )
    }
    
    /**
     * Updates preference vector with negative feedback (user disliked wallpaper).
     * 
     * @param currentVector Current preference vector
     * @param targetEmbedding Embedding of the disliked wallpaper
     * @param learningRate Learning rate for this update (0.0 - 1.0)
     * @param momentum Optional previous velocity vector for momentum (null = no momentum)
     * @return Pair of (updated preference vector, new velocity vector)
     */
    fun updateWithNegativeFeedback(
        currentVector: FloatArray,
        targetEmbedding: FloatArray,
        learningRate: Float,
        momentum: FloatArray? = null
    ): Pair<FloatArray, FloatArray> {
        if (currentVector.size != targetEmbedding.size) {
            return Pair(currentVector, FloatArray(currentVector.size))
        }
        
        return updateWithMomentum(
            currentVector = currentVector,
            targetEmbedding = targetEmbedding,
            learningRate = learningRate,
            momentum = momentum,
            isPositive = false
        )
    }
    
    /**
     * Updates preference vector with momentum for smoother learning.
     * 
     * Momentum helps:
     * - Smooth out noisy feedback
     * - Prevent oscillation between conflicting preferences
     * - Maintain stable long-term trends
     * - Avoid overfitting to most recent feedback
     * 
     * @param currentVector Current preference vector
     * @param targetEmbedding Target embedding to move toward/away from
     * @param learningRate Learning rate
     * @param momentum Previous velocity (null if first update)
     * @param isPositive True for like, false for dislike
     * @return Pair of (updated vector, new velocity)
     */
    private fun updateWithMomentum(
        currentVector: FloatArray,
        targetEmbedding: FloatArray,
        learningRate: Float,
        momentum: FloatArray?,
        isPositive: Boolean
    ): Pair<FloatArray, FloatArray> {
        val size = currentVector.size
        val newVelocity = FloatArray(size)
        val updated = FloatArray(size)
        
        // Calculate gradient (direction to move)
        val direction = if (isPositive) 1f else -1f
        
        for (i in 0 until size) {
            // Calculate gradient
            val gradient = direction * (targetEmbedding[i] - currentVector[i])
            
            // Apply momentum
            val previousVelocity = momentum?.getOrNull(i) ?: 0f
            newVelocity[i] = MOMENTUM_COEFFICIENT * previousVelocity + learningRate * gradient
            
            // Apply velocity to update preference
            updated[i] = currentVector[i] + newVelocity[i]
        }
        
        // Clip velocity to prevent runaway updates
        val clippedVelocity = clipVelocity(newVelocity)
        
        // Normalize preference vector to unit length
        val normalizedPreference = normalizeVector(updated)
        
        return Pair(normalizedPreference, clippedVelocity)
    }
    
    /**
     * Clips velocity vector to prevent extreme updates.
     * Maintains direction but limits magnitude.
     * 
     * @param velocity Velocity vector
     * @return Clipped velocity with magnitude <= MAX_VELOCITY_MAGNITUDE
     */
    private fun clipVelocity(velocity: FloatArray): FloatArray {
        var magnitude = 0f
        for (v in velocity) {
            magnitude += v * v
        }
        magnitude = sqrt(magnitude)
        
        if (magnitude <= MAX_VELOCITY_MAGNITUDE) {
            return velocity
        }
        
        // Scale down to max magnitude
        val scale = MAX_VELOCITY_MAGNITUDE / magnitude
        return FloatArray(velocity.size) { i ->
            velocity[i] * scale
        }
    }
    
    /**
     * Normalizes a vector to unit length.
     * 
     * @param vector Vector to normalize
     * @return Normalized vector with magnitude 1.0
     */
    private fun normalizeVector(vector: FloatArray): FloatArray {
        var magnitude = 0f
        for (value in vector) {
            magnitude += value * value
        }
        
        if (magnitude == 0f) {
            return vector
        }
        
        magnitude = sqrt(magnitude)
        
        val normalized = FloatArray(vector.size)
        for (i in vector.indices) {
            normalized[i] = vector[i] / magnitude
        }
        
        return normalized
    }
    
    /**
     * Applies preference decay to prevent overfitting to old feedback.
     * Gradually reduces the influence of preferences over time.
     * 
     * This is useful when user's tastes change over time.
     * 
     * @param currentVector Current preference vector
     * @param decayRate Decay rate (0.0 = no decay, 1.0 = complete reset)
     * @return Decayed preference vector
     */
    fun applyPreferenceDecay(
        currentVector: FloatArray,
        decayRate: Float
    ): FloatArray {
        if (decayRate <= 0f) return currentVector
        
        val decayed = FloatArray(currentVector.size) { i ->
            currentVector[i] * (1f - decayRate)
        }
        
        return normalizeVector(decayed)
    }
}

