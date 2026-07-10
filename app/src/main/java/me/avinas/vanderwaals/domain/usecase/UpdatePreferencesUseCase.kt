package me.avinas.vanderwaals.domain.usecase

import kotlinx.coroutines.flow.first
import me.avinas.vanderwaals.algorithm.PreferenceUpdater
import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import me.avinas.vanderwaals.data.repository.CategoryPreferenceRepository
import me.avinas.vanderwaals.data.repository.PreferenceRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Updates user preference vector based on like/dislike feedback using EMA.
 *
 * Works identically for both Auto and Personalize modes.
 * In Auto mode, the first like creates the preference vector from scratch.
 *
 * Learning rates adapt based on feedback count:
 * - 0-10: fast (0.15/0.20), 10-50: moderate (0.10/0.15), 50+: stable (0.05/0.10)
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
            
            // Step 2: Check if embedding-based vector learning is possible.
            // Vanderwaals API wallpapers have no client-side embedding (server-side only),
            // so we skip the vector math but still run all other learning steps so that
            // category, color, feedbackCount, and liked/disliked ID tracking stay accurate.
            val hasValidEmbedding = wallpaper.embedding.size == EXPECTED_EMBEDDING_SIZE
            if (!hasValidEmbedding && wallpaper.embedding.isNotEmpty()) {
                // Non-empty but wrong size — genuine data corruption, reject early.
                return Result.failure(
                    IllegalArgumentException(
                        "Invalid wallpaper embedding size: expected $EXPECTED_EMBEDDING_SIZE, got ${wallpaper.embedding.size}"
                    )
                )
            }

            // Auto Mode: Initialize preference vector from first positive feedback
            // When Auto Mode starts, preference vector is EMPTY (size = 0)
            // First LIKE or DOWNLOAD creates the vector from that wallpaper's embedding
            val isVectorEmpty = currentPreferences.preferenceVector.isEmpty()
            val isPositive = feedback == FeedbackType.LIKE || feedback == FeedbackType.DOWNLOAD

            // Step 3-5: Update preference vector using EMA with momentum.
            // Skipped entirely for wallpapers without a local embedding (e.g. VDW API wallpapers).
            val (updatedVector, newMomentum) = if (!hasValidEmbedding) {
                // No local embedding — keep the existing vector unchanged; other signals still learn.
                android.util.Log.d("UpdatePreferences",
                    "Skipping vector update for ${wallpaper.id} (no local embedding); category/color/feedback still updated")
                Pair(currentPreferences.preferenceVector, currentPreferences.momentumVector)
            } else {
                // Step 3: Initialize or get current vector
                val currentVector = if (isVectorEmpty) {
                    if (isPositive) {
                        android.util.Log.d("UpdatePreferences",
                            "Auto Mode FIRST ${feedback.name} - initializing preference vector from wallpaper ${wallpaper.id}")
                        wallpaper.embedding.clone()
                    } else {
                        android.util.Log.d("UpdatePreferences",
                            "Auto Mode FIRST DISLIKE - initializing with zero vector to enable negative learning")
                        FloatArray(EXPECTED_EMBEDDING_SIZE)
                    }
                } else {
                    currentPreferences.preferenceVector
                }

                // Step 4: Calculate adaptive learning rate based on feedback count
                val baseLearningRate = calculateLearningRate(
                    feedbackCount = currentPreferences.feedbackCount,
                    feedbackType = feedback
                )
                val learningRate = baseLearningRate * learningRateMultiplier

                // Step 5: Update preference vector using EMA with momentum
                if (isVectorEmpty && isPositive) {
                    android.util.Log.d("UpdatePreferences",
                        "First positive feedback (${feedback.name}) - using wallpaper embedding directly (no EMA update)")
                    Pair(currentVector, FloatArray(EXPECTED_EMBEDDING_SIZE))
                } else {
                    when (feedback) {
                        FeedbackType.LIKE, FeedbackType.DOWNLOAD -> {
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

            // Step 6: Update mood/style affinity maps (Vanderwaals Collection semantic tags)
            val updatedMoodAffinity = updateTagAffinity(
                currentPreferences.moodAffinity, wallpaper.mood, isPositive, learningRateMultiplier
            )
            val updatedStyleAffinity = updateTagAffinity(
                currentPreferences.styleAffinity, wallpaper.style, isPositive, learningRateMultiplier
            )

            // Step 6: Create updated preferences object with momentum
            val updatedPreferences = currentPreferences.copy(
                preferenceVector = updatedVector,
                momentumVector = newMomentum,
                likedWallpaperIds = updatedLikedIds,
                dislikedWallpaperIds = updatedDislikedIds,
                feedbackCount = currentPreferences.feedbackCount + 1,
                lastUpdated = System.currentTimeMillis(),
                moodAffinity = updatedMoodAffinity,
                styleAffinity = updatedStyleAffinity
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
                    val wallpaperFile = me.avinas.vanderwaals.core.resolveWallpaperFile(context, wallpaper.id)
                    if (wallpaperFile != null) {
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
                ├── Old Feedback Count: ${currentPreferences.feedbackCount}
                ├── New Feedback Count: ${updatedPreferences.feedbackCount}
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
    
    /**
     * Updates mood/style tag affinity using exponential moving average.
     *
     * For each tag on the wallpaper, the affinity value is nudged toward
     * +1 (like) or -1 (dislike) at the given learning rate. Tags absent
     * from the current map start from 0 (neutral).
     *
     * @param current Current affinity map (tag → [-1,+1])
     * @param tags Tags present on the wallpaper
     * @param isPositive True for like/download, false for dislike
     * @param learningRateMultiplier Multiplier applied to the base tag learning rate
     * @return Updated affinity map
     */
    private fun updateTagAffinity(
        current: Map<String, Float>,
        tags: List<String>,
        isPositive: Boolean,
        learningRateMultiplier: Float
    ): Map<String, Float> {
        if (tags.isEmpty()) return current
        val lr = 0.15f * learningRateMultiplier
        val signal = if (isPositive) 1f else -1f
        val result = current.toMutableMap()
        for (tag in tags) {
            val currentVal = result.getOrDefault(tag, 0f)
            result[tag] = (currentVal * (1f - lr) + signal * lr).coerceIn(-1f, 1f)
        }
        return result
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
