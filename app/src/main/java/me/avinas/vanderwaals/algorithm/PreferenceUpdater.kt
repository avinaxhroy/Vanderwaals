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
            // Return a uniform unit vector so the preference is not stuck
            // at zero (which would trap the user in cold-start forever).
            val n = vector.size
            return if (n == 0) vector else FloatArray(n) { 1f / sqrt(n.toFloat()) }
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
     * @param currentVector Current preference vector
     * @param decayRate Decay rate (0.0 = no decay, 1.0 = complete reset)
     * @return Decayed preference vector
     */
    fun applyPreferenceDecay(
        currentVector: FloatArray,
        decayRate: Float
    ): FloatArray {
        if (decayRate <= 0f || currentVector.isEmpty()) return currentVector

        val n = currentVector.size
        val uniform = 1f / sqrt(n.toFloat())
        val blend = decayRate.coerceIn(0f, 1f)

        val decayed = FloatArray(n) { i ->
            (1f - blend) * currentVector[i] + blend * uniform
        }

        return normalizeVector(decayed)
    }

    /**
     * Applies time-aware exponential decay to the preference vector.
     *
     * When a user has not given feedback for a prolonged period their stored preference
     * vector may no longer reflect their current taste. This method softens the vector
     * toward a uniform (less opinionated) state using an exponential half-life so that
     * staleness is penalised proportionally to elapsed time.
     *
     * ```
     * decay_factor = exp(-ln(2) × daysSinceLastFeedback / halfLifeDays)
     * ```
     *
     * Examples with halfLifeDays = 30:
     *  - 0 days   → factor = 1.00 (no change)
     *  - 15 days  → factor ≈ 0.71 (mild softening)
     *  - 30 days  → factor = 0.50 (half strength)
     *  - 60 days  → factor ≈ 0.25 (strong softening toward neutral)
     *
     * @param currentVector         Current preference vector to decay
     * @param daysSinceLastFeedback Days elapsed since the last explicit feedback event
     * @param halfLifeDays          Half-life for the decay curve (default: 30 days)
     * @return Time-decayed and re-normalised preference vector
     */
    fun applyTemporalDecay(
        currentVector: FloatArray,
        daysSinceLastFeedback: Double,
        halfLifeDays: Double = 30.0
    ): FloatArray {
        if (daysSinceLastFeedback <= 0.0 || currentVector.isEmpty()) return currentVector
        val decayFactor = kotlin.math.exp(
            -kotlin.math.ln(2.0) * daysSinceLastFeedback / halfLifeDays
        ).toFloat()
        // decayFactor ∈ (0, 1]: 1 = no change, → 0 = full neutralisation.
        // Blend toward a uniform vector so the preference softens toward a
        // less opinionated state rather than just scaling (which normalises
        // back to the same unit vector).
        val n = currentVector.size
        val uniform = 1f / sqrt(n.toFloat())
        val blend = 1f - decayFactor

        val decayed = FloatArray(n) { i ->
            decayFactor * currentVector[i] + blend * uniform
        }

        return normalizeVector(decayed)
    }

    /**
     * Updates the preference vector with a confidence-weighted learning rate.
     *
     * When the user already has strong, confident preferences for a category (high
     * [categoryConfidence]), the effective learning rate is reduced so that a single
     * new data point does not over-correct well-established taste. When preferences are
     * still uncertain (low confidence), the full [baseLearningRate] is applied for
     * faster convergence.
     *
     * ```
     * effectiveLearningRate = baseLearningRate × (1 − ATTENUATION × categoryConfidence)
     * ```
     *
     * With ATTENUATION = 0.60:
     *  - confidence 0.0 → effective rate = baseLearningRate × 1.00 (full rate)
     *  - confidence 0.5 → effective rate = baseLearningRate × 0.70 (30% reduction)
     *  - confidence 1.0 → effective rate = baseLearningRate × 0.40 (60% reduction)
     *
     * @param currentVector       Current preference vector
     * @param targetEmbedding     Embedding of the wallpaper that received feedback
     * @param baseLearningRate    Nominal learning rate before confidence scaling (0.0–1.0)
     * @param categoryConfidence  Confidence in current preferences for this category (0.0–1.0)
     * @param momentum            Previous velocity vector for momentum smoothing (null = none)
     * @param isPositive          True for a like, false for a dislike
     * @return Pair of (updated preference vector, new velocity vector)
     */
    fun updateWithConfidenceWeighting(
        currentVector: FloatArray,
        targetEmbedding: FloatArray,
        baseLearningRate: Float,
        categoryConfidence: Float,
        momentum: FloatArray? = null,
        isPositive: Boolean
    ): Pair<FloatArray, FloatArray> {
        // Attenuate the learning rate proportionally to preference confidence.
        // A fully-confident category (1.0) uses 40% of the base rate;
        // an unknown category (0.0) uses the full base rate.
        val confidenceAttenuation = 0.60f
        val effectiveRate = baseLearningRate *
            (1f - confidenceAttenuation * categoryConfidence.coerceIn(0f, 1f))
        return if (isPositive) {
            updateWithPositiveFeedback(currentVector, targetEmbedding, effectiveRate, momentum)
        } else {
            updateWithNegativeFeedback(currentVector, targetEmbedding, effectiveRate, momentum)
        }
    }
}

