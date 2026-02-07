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
            
            // CRITICAL FIX FOR AUTO MODE: Initialize preference vector from first positive feedback
            // When Auto Mode starts, preference vector is EMPTY (size = 0)
            // First LIKE or DOWNLOAD should create the vector from that wallpaper's embedding
            // First DISLIKE initializes with zero vector to enable negative learning
            val isVectorEmpty = currentPreferences.preferenceVector.isEmpty()
            val isPositive = feedback == FeedbackType.LIKE || feedback == FeedbackType.DOWNLOAD
            
            // Step 3: Initialize or get current vector
            val currentVector = if (isVectorEmpty) {
                if (isPositive) {
                    // First positive feedback in Auto Mode: Initialize preference vector from this wallpaper
                    android.util.Log.d("UpdatePreferences", 
                        "Auto Mode FIRST ${feedback.name} - initializing preference vector from wallpaper ${wallpaper.id}"
                    )
                    wallpaper.embedding.clone()
                } else {
                    // First dislike in Auto Mode: Initialize with zero vector
                    // This allows the negative update to work: 0 - lr * embedding = -lr * embedding
                    // Resulting in a vector pointing AWAY from the disliked wallpaper
                    android.util.Log.d("UpdatePreferences", 
                        "Auto Mode FIRST DISLIKE - initializing with zero vector to enable negative learning"
                    )
                    FloatArray(EXPECTED_EMBEDDING_SIZE) // Zero-filled array
                }
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
            // Skip EMA update on first positive feedback (already initialized above), otherwise update normally
            val (updatedVector, newMomentum) = if (isVectorEmpty && isPositive) {
                // First positive feedback: Use initialized vector as-is, no momentum yet
                android.util.Log.d("UpdatePreferences", "First positive feedback (${feedback.name}) - using wallpaper embedding directly (no EMA update)")
                Pair(currentVector, FloatArray(EXPECTED_EMBEDDING_SIZE))
            } else {
                // Subsequent feedback: Update using EMA
                when (feedback) {
                    FeedbackType.LIKE, FeedbackType.DOWNLOAD -> {
                        // Both like and download use positive feedback update
                        // Download just has higher learning rate (calculated above)
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
            
            // Step 6: Update liked/disliked wallpaper lists
            // DOWNLOAD counts as a "super like" - add to liked list
            val updatedLikedIds = if (isPositive) {
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
                    FeedbackType.LIKE, FeedbackType.DOWNLOAD -> categoryPreferenceRepository.recordLike(wallpaper.category)
                    FeedbackType.DISLIKE -> categoryPreferenceRepository.recordDislike(wallpaper.category)
                }
            } else {
                // Update color preferences for uncategorized wallpapers
                // Extract top 3 colors from palette
                val topColors = wallpaper.colors.take(3)
                when (feedback) {
                    FeedbackType.LIKE, FeedbackType.DOWNLOAD -> colorPreferenceRepository.recordLikes(topColors)
                    FeedbackType.DISLIKE -> colorPreferenceRepository.recordDislikes(topColors)
                }
            }
            
            // Step 9: Update composition preferences for positive feedback
            // Learn preferred visual styles from both LIKE and DOWNLOAD (with higher weight for download)
            if (isPositive) {
                try {
                    val wallpaperFile = java.io.File(context.filesDir, "wallpapers/${wallpaper.id}.jpg")
                    if (wallpaperFile.exists()) {
                        val composition = compositionAnalyzer.analyzeComposition(wallpaperFile)
                        if (!composition.isEmpty()) {
                            // DOWNLOAD has higher learning rate (1.5x) for composition too
                            val compositionLearningRate = if (feedback == FeedbackType.DOWNLOAD) 0.225f else 0.15f
                            // Update composition preferences
                            compositionPreferenceRepository.updatePreferences(
                                newComposition = composition,
                                learningRate = compositionLearningRate * learningRateMultiplier
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
            
            // SUCCESS LOG: Explicit confirmation for debugging
            android.util.Log.i("UpdatePreferences", """
                ✅ PREFERENCE UPDATED SUCCESSFULLY
                ├── Wallpaper: ${wallpaper.id}
                ├── Category: ${wallpaper.category.ifBlank { "uncategorized" }}
                ├── Feedback: ${feedback.name}
                ├── Learning Rate: ${String.format("%.4f", learningRate)} (base) × ${String.format("%.2f", learningRateMultiplier)} (multiplier)
                ├── Old Feedback Count: ${currentPreferences.feedbackCount}
                ├── New Feedback Count: ${updatedPreferences.feedbackCount}
                ├── Vector Changed: ${!currentVector.contentEquals(updatedVector)}
                └── Liked/Disliked Counts: ${updatedLikedIds.size}/${updatedDislikedIds.size}
            """.trimIndent())
            
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
        // Base rates by feedback type:
        // - DOWNLOAD: Highest weight (1.5x) - user wants to keep it for future
        // - DISLIKE: High weight - strong signal to avoid similar content
        // - LIKE: Standard weight - positive but not as strong as download
        val baseMultiplier = when (feedbackType) {
            FeedbackType.DOWNLOAD -> 1.5f  // 50% stronger than like
            FeedbackType.DISLIKE -> 1.0f   // Standard negative
            FeedbackType.LIKE -> 1.0f      // Standard positive
        }
        
        // TUNED FOR MobileNetV4: Slightly faster early/mid learning to capture richer signals
        val baseRate = when {
            feedbackCount < 10 -> {
                // Fast initial learning (tuned +20% for MobileNetV4)
                when (feedbackType) {
                    FeedbackType.DOWNLOAD -> 0.27f   // 0.18 * 1.5
                    FeedbackType.DISLIKE -> 0.22f    // Faster avoidance
                    FeedbackType.LIKE -> 0.18f       // Faster capture
                }
            }
            feedbackCount < 50 -> {
                // Moderate learning (tuned +20% for MobileNetV4)
                when (feedbackType) {
                    FeedbackType.DOWNLOAD -> 0.18f   // 0.12 * 1.5
                    FeedbackType.DISLIKE -> 0.16f    // Faster mid-phase
                    FeedbackType.LIKE -> 0.12f       // Faster mid-phase
                }
            }
            else -> {
                // Stable maintenance (unchanged - prevent overfitting)
                when (feedbackType) {
                    FeedbackType.DOWNLOAD -> 0.075f  // 0.05 * 1.5
                    FeedbackType.DISLIKE -> 0.10f
                    FeedbackType.LIKE -> 0.05f
                }
            }
        }
        return baseRate
    }
    
    companion object {
        /**
         * Expected embedding dimension for MobileNetV4-Conv-Small model.
         */
        private const val EXPECTED_EMBEDDING_SIZE = 1280
    }
}

/**
 * Enum representing types of user feedback on wallpapers.
 * 
 * Learning rate weights (from highest to lowest):
 * - DOWNLOAD: 1.5x base rate - Strongest positive signal (user wants to keep it)
 * - DISLIKE: 1.0x base rate - Strong negative signal (user definitely doesn't want it)
 * - LIKE: 1.0x base rate - Standard positive signal
 * 
 * @property LIKE User explicitly liked the wallpaper (positive feedback)
 * @property DISLIKE User explicitly disliked the wallpaper (negative feedback)
 * @property DOWNLOAD User downloaded the wallpaper to gallery (strongest positive feedback)
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
    DISLIKE,
    
    /**
     * Strongest positive feedback: User downloaded the wallpaper to gallery.
     * This indicates the user values the wallpaper enough to save it for future use.
     * Has the highest learning rate to strongly pull preference vector toward it.
     */
    DOWNLOAD
}
