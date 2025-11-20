package me.avinas.vanderwaals.domain.usecase

import kotlinx.coroutines.flow.first
import me.avinas.vanderwaals.algorithm.PreferenceUpdater
import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import me.avinas.vanderwaals.data.repository.CategoryPreferenceRepository
import me.avinas.vanderwaals.data.repository.PreferenceRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for updating user preference vector based on feedback.
 * 
 * **CRITICAL: Works identically for BOTH Auto and Personalize modes!**
 * This use case doesn't check the mode - it simply updates the preference vector
 * based on feedback. Both modes use the exact same learning algorithm.
 * 
 * **How It Works for Each Mode:**
 * 
 * PERSONALIZE MODE:
 * - Starts with preference vector from uploaded image (feedbackCount > 0)
 * - Each like/dislike updates this vector using EMA
 * - Vector continuously evolves with feedback
 * 
 * **AUTO MODE:**
 * - Starts with EMPTY preference vector (size = 0, feedbackCount = 0)
 * - First like: Creates preference vector directly from that wallpaper's embedding
 * - First dislike: Rejected (need at least one like to establish baseline)
 * - Subsequent likes/dislikes: Updates vector exactly like Personalize Mode using EMA
 * - After 10-15 likes: Vector is just as refined as Personalize Mode
 * 
 * **Learning Algorithm (EMA):**
 * ```
 * For LIKE feedback:
 *   preference_vector[i] += learning_rate × (wallpaper_embedding[i] - preference_vector[i])
 * 
 * For DISLIKE feedback:
 *   preference_vector[i] -= learning_rate × (wallpaper_embedding[i] - preference_vector[i])
 * 
 * Normalize preference_vector to unit length
 * ```
 * 
 * **Adaptive Learning Rates (same for both modes):**
 * - 0-10 feedback events: Fast learning (rate = 0.15 like, 0.20 dislike)
 * - 10-50 feedback events: Moderate learning (rate = 0.10 like, 0.15 dislike)
 * - 50+ feedback events: Stable maintenance (rate = 0.05 like, 0.10 dislike)
 * 
 * **Side Effects:**
 * After updating preferences, this use case triggers:
 * - Preference vector saved to database
 * - Wallpaper IDs added to liked/disliked lists
 * - Feedback count incremented
 * - Download queue re-ranking (via repository)
 * 
 * @property preferenceRepository Repository for accessing and updating user preferences
 * @property preferenceUpdater Algorithm implementation for EMA updates
 * 
 * @see FindSimilarWallpapersUseCase
 * @see SelectNextWallpaperUseCase
 */
@Singleton
class UpdatePreferencesUseCase @Inject constructor(
    private val preferenceRepository: PreferenceRepository,
    private val categoryPreferenceRepository: CategoryPreferenceRepository,
    private val colorPreferenceRepository: me.avinas.vanderwaals.data.repository.ColorPreferenceRepository,
    private val compositionPreferenceRepository: me.avinas.vanderwaals.data.repository.CompositionPreferenceRepository,
    private val preferenceUpdater: PreferenceUpdater,
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) {
    private val compositionAnalyzer = me.avinas.vanderwaals.algorithm.CompositionAnalyzer
    /**
     * Updates user preferences based on wallpaper feedback.
     * 
     * **Thread Safety:**
     * This operation performs database reads/writes. Should be called from
     * a background coroutine (IO dispatcher).
     * 
     * **Feedback Types:**
     * - LIKE: User explicitly liked the wallpaper (pulls preference vector toward it)
     * - DISLIKE: User explicitly disliked the wallpaper (pushes preference vector away)
     * 
     * **Learning Rate Multiplier:**
     * - Default 1.0 for explicit feedback (full strength)
     * - Can be reduced (e.g., 0.3) for implicit feedback (lower confidence)
     * - Applied to base learning rate: finalRate = baseRate × multiplier
     * 
     * **Error Handling:**
     * - If preferences don't exist, returns failure (user should initialize first)
     * - If embedding is invalid, returns failure without updating
     * - All exceptions caught and returned as Result.failure
     * 
     * @param wallpaper The wallpaper that received feedback
     * @param feedback Type of feedback (LIKE or DISLIKE)
     * @param learningRateMultiplier Multiplier for base learning rate (default 1.0 for explicit feedback)
     * @return Result<Unit> indicating success or failure with error description
     * 
     * @throws None - All exceptions are caught and returned as Result.failure
     * 
     * Example:
     * ```kotlin
     * viewModelScope.launch {
     *     // Explicit feedback - full strength
     *     val result = updatePreferencesUseCase(wallpaper, FeedbackType.LIKE)
     *     
     *     // Implicit feedback - reduced strength
     *     val implicitResult = updatePreferencesUseCase(
     *         wallpaper = wallpaper,
     *         feedback = FeedbackType.LIKE,
     *         learningRateMultiplier = 0.3f
     *     )
     * }
     * ```
     */
    operator suspend fun invoke(
        wallpaper: WallpaperMetadata,
        feedback: FeedbackType,
        learningRateMultiplier: Float = 1.0f
    ): Result<Unit> {
        return try {
            // Step 1: Get current user preferences
            val currentPreferences = preferenceRepository.getUserPreferences().first()
                ?: return Result.failure(
                    IllegalStateException("User preferences not initialized. Call InitializePreferencesUseCase first.")
                )
            
            // Step 2: Validate wallpaper embedding
            if (wallpaper.embedding.size != EXPECTED_EMBEDDING_SIZE) {
                return Result.failure(
                    IllegalArgumentException(
                        "Invalid wallpaper embedding size: expected $EXPECTED_EMBEDDING_SIZE, got ${wallpaper.embedding.size}"
                    )
                )
            }
            
            // CRITICAL FIX FOR AUTO MODE: Initialize preference vector from first liked wallpaper
            // When Auto Mode starts, preference vector is EMPTY (size = 0)
            // First LIKE should create the vector from that wallpaper's embedding
            // First DISLIKE should be ignored (can't learn what to avoid without knowing what they like)
            val isVectorEmpty = currentPreferences.preferenceVector.isEmpty()
            
            if (isVectorEmpty && feedback == FeedbackType.DISLIKE) {
                // Can't process dislike without a preference vector
                // User needs to like something first to establish their preferences
                android.util.Log.d("UpdatePreferences", 
                    "Skipping dislike for Auto Mode (no preference vector yet). User must like something first."
                )
                return Result.failure(
                    IllegalStateException("Cannot process dislike in Auto Mode without any likes. Like a wallpaper first to establish preferences.")
                )
            }
            
            // Step 3: Initialize or get current vector
            val currentVector = if (isVectorEmpty && feedback == FeedbackType.LIKE) {
                // First like in Auto Mode: Initialize preference vector from this wallpaper
                android.util.Log.d("UpdatePreferences", 
                    "Auto Mode FIRST LIKE - initializing preference vector from wallpaper ${wallpaper.id}"
                )
                wallpaper.embedding.clone()
            } else {
                currentPreferences.preferenceVector
            }
            
            // Step 4: Calculate adaptive learning rate based on feedback count
            val baseLearningRate = calculateLearningRate(
                feedbackCount = currentPreferences.feedbackCount,
                feedbackType = feedback
            )
            
            // Apply multiplier for implicit vs explicit feedback
            val learningRate = baseLearningRate * learningRateMultiplier
            
            // Step 5: Update preference vector using EMA with momentum
            // Skip EMA update on first like (already initialized above), otherwise update normally
            val (updatedVector, newMomentum) = if (isVectorEmpty && feedback == FeedbackType.LIKE) {
                // First like: Use initialized vector as-is, no momentum yet
                android.util.Log.d("UpdatePreferences", "First like - using wallpaper embedding directly (no EMA update)")
                Pair(currentVector, FloatArray(EXPECTED_EMBEDDING_SIZE))
            } else {
                // Subsequent feedback: Update using EMA
                when (feedback) {
                    FeedbackType.LIKE -> {
                        preferenceUpdater.updateWithPositiveFeedback(
                            currentVector = currentVector,
                            targetEmbedding = wallpaper.embedding,
                            learningRate = learningRate,
                            momentum = currentPreferences.momentumVector.takeIf { it.isNotEmpty() }
                        )
                    }
                    FeedbackType.DISLIKE -> {
                        preferenceUpdater.updateWithNegativeFeedback(
                            currentVector = currentVector,
                            targetEmbedding = wallpaper.embedding,
                            learningRate = learningRate,
                            momentum = currentPreferences.momentumVector.takeIf { it.isNotEmpty() }
                        )
                    }
                }
            }
            
            // Step 5: Update liked/disliked wallpaper lists
            val updatedLikedIds = if (feedback == FeedbackType.LIKE) {
                currentPreferences.likedWallpaperIds + wallpaper.id
            } else {
                currentPreferences.likedWallpaperIds
            }
            
            val updatedDislikedIds = if (feedback == FeedbackType.DISLIKE) {
                currentPreferences.dislikedWallpaperIds + wallpaper.id
            } else {
                currentPreferences.dislikedWallpaperIds
            }
            
            // Step 6: Create updated preferences object with momentum
            val updatedPreferences = currentPreferences.copy(
                preferenceVector = updatedVector,
                momentumVector = newMomentum,
                likedWallpaperIds = updatedLikedIds,
                dislikedWallpaperIds = updatedDislikedIds,
                feedbackCount = currentPreferences.feedbackCount + 1,
                lastUpdated = System.currentTimeMillis()
            )
            
            // Step 7: Save updated preferences to database
            preferenceRepository.updateUserPreferences(updatedPreferences)
            
            // Step 8: Update category/color preferences
            if (wallpaper.category.isNotBlank()) {
                // Update category preferences for categorized wallpapers
                when (feedback) {
                    FeedbackType.LIKE -> categoryPreferenceRepository.recordLike(wallpaper.category)
                    FeedbackType.DISLIKE -> categoryPreferenceRepository.recordDislike(wallpaper.category)
                }
            } else {
                // Update color preferences for uncategorized wallpapers
                // Extract top 3 colors from palette
                val topColors = wallpaper.colors.take(3)
                when (feedback) {
                    FeedbackType.LIKE -> colorPreferenceRepository.recordLikes(topColors)
                    FeedbackType.DISLIKE -> colorPreferenceRepository.recordDislikes(topColors)
                }
            }
            
            // Step 9: Update composition preferences for LIKE feedback
            // Only update on likes to learn preferred visual styles
            if (feedback == FeedbackType.LIKE) {
                try {
                    val wallpaperFile = java.io.File(context.filesDir, "wallpapers/${wallpaper.id}.jpg")
                    if (wallpaperFile.exists()) {
                        val composition = compositionAnalyzer.analyzeComposition(wallpaperFile)
                        if (!composition.isEmpty()) {
                            // Update composition preferences with reduced learning rate for implicit style learning
                            compositionPreferenceRepository.updatePreferences(
                                newComposition = composition,
                                learningRate = 0.15f * learningRateMultiplier
                            )
                            android.util.Log.d("UpdatePreferences", 
                                "Updated composition preferences: symmetry=${String.format("%.2f", composition.symmetryScore)}, " +
                                "centerWeight=${String.format("%.2f", composition.centerWeight)}, " +
                                "complexity=${String.format("%.2f", composition.complexity)}"
                            )
                        }
                    }
                } catch (e: Exception) {
                    // Non-fatal: Composition learning is optional enhancement
                    android.util.Log.w("UpdatePreferences", "Failed to update composition preferences: ${e.message}")
                }
            }
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            Result.failure(
                Exception("Failed to update preferences: ${e.message}", e)
            )
        }
    }
    
    /**
     * Calculates adaptive learning rate based on feedback history.
     * 
     * Implements the adaptive learning rate strategy:
     * - Early learning (< 10 feedback): Fast adaptation
     * - Moderate learning (10-50 feedback): Balanced updates
     * - Stable learning (> 50 feedback): Fine-tuning
     * 
     * Negative feedback always has slightly higher learning rate to prevent
     * showing disliked content more aggressively.
     * 
     * @param feedbackCount Total number of previous feedback events
     * @param feedbackType Type of current feedback (LIKE or DISLIKE)
     * @return Calculated learning rate (0.0 to 1.0)
     */
    private fun calculateLearningRate(
        feedbackCount: Int,
        feedbackType: FeedbackType
    ): Float {
        return when {
            feedbackCount < 10 -> {
                // Fast initial learning
                if (feedbackType == FeedbackType.LIKE) 0.15f else 0.20f
            }
            feedbackCount < 50 -> {
                // Moderate learning
                if (feedbackType == FeedbackType.LIKE) 0.10f else 0.15f
            }
            else -> {
                // Stable maintenance
                if (feedbackType == FeedbackType.LIKE) 0.05f else 0.10f
            }
        }
    }
    
    companion object {
        /**
         * Expected embedding dimension for MobileNetV3-Small model.
         */
        private const val EXPECTED_EMBEDDING_SIZE = 576
    }
}

/**
 * Enum representing types of user feedback on wallpapers.
 * 
 * @property LIKE User explicitly liked the wallpaper (positive feedback)
 * @property DISLIKE User explicitly disliked the wallpaper (negative feedback)
 */
enum class FeedbackType {
    /**
     * Positive feedback: User likes the wallpaper.
     * Pulls preference vector toward the wallpaper's embedding.
     */
    LIKE,
    
    /**
     * Negative feedback: User dislikes the wallpaper.
     * Pushes preference vector away from the wallpaper's embedding.
     */
    DISLIKE
}
