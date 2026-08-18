package me.avinas.vanderwaals.domain.usecase

import android.util.Log
import kotlinx.coroutines.flow.first
import me.avinas.vanderwaals.algorithm.RecommenderConfig
import me.avinas.vanderwaals.data.entity.UserPreferences
import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import me.avinas.vanderwaals.data.repository.CategoryPreferenceRepository
import me.avinas.vanderwaals.data.repository.PreferenceRepository
import me.avinas.vanderwaals.data.repository.TasteAnchorRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Records one feedback event into the user's taste memory.
 *
 * Rebuilt around [TasteAnchorRepository]: a like upserts a recency-stamped
 * positive anchor, a dislike upserts a suppression anchor.  There is no
 * learning-rate ladder and no momentum — plasticity comes from the
 * anchor's exponential decay, so the model keeps adapting no matter how
 * much feedback has accumulated (the legacy EMA path froze after ~50
 * events, which is one root cause of recommendations degrading over
 * months of use).
 *
 * Secondary signals (category counts, colour palette entries, mood/style
 * affinities) are still recorded — each is consumed exactly once by the
 * [me.avinas.vanderwaals.algorithm.RankingEngine]'s corresponding score
 * component.
 */
@Singleton
class UpdatePreferencesUseCase @Inject constructor(
    private val preferenceRepository: PreferenceRepository,
    private val tasteAnchorRepository: TasteAnchorRepository,
    private val categoryPreferenceRepository: CategoryPreferenceRepository,
    private val colorPreferenceRepository: me.avinas.vanderwaals.data.repository.ColorPreferenceRepository
) {

    /**
     * @param learningRateMultiplier retained parameter name from the EMA
     * era; it now maps directly to the recorded anchor strength
     * (1.0 explicit, [RecommenderConfig.IMPLICIT_FEEDBACK_STRENGTH] for
     * implicit signals)
     */
    operator suspend fun invoke(
        wallpaper: WallpaperMetadata,
        feedback: FeedbackType,
        learningRateMultiplier: Float = 1.0f
    ): Result<Unit> {
        return try {
            val currentPreferences = preferenceRepository.getUserPreferences().first()
                ?: return Result.failure(
                    IllegalStateException("User preferences not initialized. Call InitializePreferencesUseCase first.")
                )

            val hasValidEmbedding = wallpaper.embedding.size == EXPECTED_EMBEDDING_SIZE
            if (!hasValidEmbedding && wallpaper.embedding.isNotEmpty()) {
                return Result.failure(
                    IllegalArgumentException(
                        "Invalid wallpaper embedding size: expected $EXPECTED_EMBEDDING_SIZE, " +
                            "got ${wallpaper.embedding.size}"
                    )
                )
            }

            val now = System.currentTimeMillis()
            val isPositive = feedback == FeedbackType.LIKE || feedback == FeedbackType.DOWNLOAD
            val strength = learningRateMultiplier.coerceIn(0.2f, 1f)
            // DOWNLOAD is the strongest positive signal — always full strength.
            val effectiveStrength = if (feedback == FeedbackType.DOWNLOAD) 1.0f else strength

            // Taste anchors — the primary learning step.
            // Empty embeddings are allowed (Vanderwaals Collection items have
            // server-side embeddings only); the row then acts purely as a
            // cooldown marker.
            val anchorEmbedding = if (hasValidEmbedding) wallpaper.embedding else FloatArray(0)
            if (isPositive) {
                tasteAnchorRepository.recordLike(
                    wallpaperId = wallpaper.id,
                    embedding = anchorEmbedding,
                    nowMillis = now,
                    strength = effectiveStrength
                )
            } else {
                tasteAnchorRepository.recordDislike(
                    wallpaperId = wallpaper.id,
                    embedding = anchorEmbedding,
                    nowMillis = now,
                    strength = strength
                )
            }

            // Bounded id lists (analytics + colour palette derivation).
            val maxIds = RecommenderConfig.MAX_NEGATIVE_ANCHORS
            val updatedLikedIds = if (isPositive && wallpaper.id !in currentPreferences.likedWallpaperIds) {
                (currentPreferences.likedWallpaperIds + wallpaper.id).takeLast(maxIds)
            } else {
                currentPreferences.likedWallpaperIds
            }
            val updatedDislikedIds = if (feedback == FeedbackType.DISLIKE && wallpaper.id !in currentPreferences.dislikedWallpaperIds) {
                (currentPreferences.dislikedWallpaperIds + wallpaper.id).takeLast(maxIds)
            } else {
                currentPreferences.dislikedWallpaperIds
            }

            // Semantic tag affinities (mood/style).
            val updatedMoodAffinity = updateTagAffinity(
                currentPreferences.moodAffinity, wallpaper.mood, isPositive, strength
            )
            val updatedStyleAffinity = updateTagAffinity(
                currentPreferences.styleAffinity, wallpaper.style, isPositive, strength
            )

            // Legacy vector maintenance. Keep preferenceVector in sync with
            // the recency-weighted centroid of positive anchors so legacy
            // readers (similarity search, queue pre-fill) stay coherent with
            // the new model.
            val centroid = tasteAnchorRepository.getTasteMemory(now).positiveCentroid()
            val updatedPreferences = currentPreferences.copy(
                preferenceVector = centroid,
                momentumVector = FloatArray(0),
                likedWallpaperIds = updatedLikedIds,
                dislikedWallpaperIds = updatedDislikedIds,
                feedbackCount = currentPreferences.feedbackCount + 1,
                lastUpdated = now,
                moodAffinity = updatedMoodAffinity,
                styleAffinity = updatedStyleAffinity
            )
            preferenceRepository.updateUserPreferences(updatedPreferences)

            // Category / colour counts.
            if (wallpaper.category.isNotBlank()) {
                when (feedback) {
                    FeedbackType.LIKE, FeedbackType.DOWNLOAD ->
                        categoryPreferenceRepository.recordLike(wallpaper.category)
                    FeedbackType.DISLIKE ->
                        categoryPreferenceRepository.recordDislike(wallpaper.category)
                }
            } else {
                val topColors = wallpaper.colors.take(3)
                when (feedback) {
                    FeedbackType.LIKE, FeedbackType.DOWNLOAD ->
                        colorPreferenceRepository.recordLikes(topColors)
                    FeedbackType.DISLIKE ->
                        colorPreferenceRepository.recordDislikes(topColors)
                }
            }

            if (me.avinas.vanderwaals.BuildConfig.DEBUG) {
                Log.d(
                    "UpdatePreferences",
                    "Feedback recorded: ${feedback.name} for ${wallpaper.id} " +
                        "(strength=$effectiveStrength, feedbackCount=${updatedPreferences.feedbackCount})"
                )
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to update preferences: ${e.message}", e))
        }
    }

    /**
     * EMA of tag affinity toward +1 (like) or −1 (dislike).
     */
    private fun updateTagAffinity(
        current: Map<String, Float>,
        tags: List<String>,
        isPositive: Boolean,
        strength: Float
    ): Map<String, Float> {
        if (tags.isEmpty()) return current
        val lr = (0.15f * strength).coerceIn(0f, 1f)
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
 * @property LIKE User explicitly liked the wallpaper (positive feedback)
 * @property DISLIKE User explicitly disliked the wallpaper (negative feedback)
 * @property DOWNLOAD User downloaded the wallpaper to gallery (strongest positive feedback)
 */
enum class FeedbackType {
    LIKE,
    DISLIKE,
    DOWNLOAD
}
