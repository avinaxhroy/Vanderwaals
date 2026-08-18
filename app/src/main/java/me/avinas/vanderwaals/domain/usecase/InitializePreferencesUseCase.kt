package me.avinas.vanderwaals.domain.usecase

import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import me.avinas.vanderwaals.data.entity.UserPreferences
import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import me.avinas.vanderwaals.data.repository.PreferenceRepository
import me.avinas.vanderwaals.data.repository.TasteAnchorRepository
import javax.inject.Inject

/**
 * Initializes the user's taste memory from the onboarding confirmation
 * gallery — the "configure once" entry point.
 *
 * Everything the user gives here becomes durable taste evidence:
 * - the uploaded image (Personalize mode) is seeded as a persistent anchor,
 * - each liked gallery wallpaper becomes a positive anchor,
 * each disliked one a suppression anchor,
 * - category likes/dislikes are recorded so the category component and
 * exploration calibration are live from day one.
 *
 * Thanks to the taste memory's relative-time decay, this one-time input
 * keeps working indefinitely: anchors only lose influence when newer
 * feedback displaces them, never by wall-clock staleness.
 */
class InitializePreferencesUseCase @Inject constructor(
    private val preferenceRepository: PreferenceRepository,
    private val tasteAnchorRepository: TasteAnchorRepository,
    private val categoryPreferenceRepository: me.avinas.vanderwaals.data.repository.CategoryPreferenceRepository
) {

    companion object {
        private const val TAG = "InitializePreferences"
        private const val EMBEDDING_SIZE = 1280

        /** Synthetic anchor id for the onboarding upload's embedding. */
        const val UPLOAD_ANCHOR_ID = "__onboarding_upload__"
    }

    suspend operator fun invoke(
        originalEmbedding: FloatArray,
        likedWallpapers: List<WallpaperMetadata>,
        dislikedWallpapers: List<WallpaperMetadata>
    ): Result<Unit> {
        return try {
            Log.d(TAG, "Initializing with ${likedWallpapers.size} liked and ${dislikedWallpapers.size} disliked wallpapers")

            if (likedWallpapers.isEmpty()) {
                return Result.failure(Exception("At least one liked wallpaper is required"))
            }

            val now = System.currentTimeMillis()

            // Seed the taste memory: the uploaded image (prime reference)
            // plus one anchor per onboarding choice.
            if (originalEmbedding.isNotEmpty()) {
                tasteAnchorRepository.recordLike(
                    wallpaperId = UPLOAD_ANCHOR_ID,
                    embedding = originalEmbedding,
                    nowMillis = now
                )
            }
            likedWallpapers.forEach { wallpaper ->
                tasteAnchorRepository.recordLike(
                    wallpaperId = wallpaper.id,
                    embedding = wallpaper.embedding,
                    nowMillis = now
                )
            }
            dislikedWallpapers.forEach { wallpaper ->
                tasteAnchorRepository.recordDislike(
                    wallpaperId = wallpaper.id,
                    embedding = wallpaper.embedding,
                    nowMillis = now
                )
            }

            // Category evidence so the category component and exploration
            // calibration are live from the first recommendation.
            likedWallpapers.map { it.category }.filter { it.isNotBlank() }.forEach {
                categoryPreferenceRepository.recordLike(it)
            }
            dislikedWallpapers.map { it.category }.filter { it.isNotBlank() }.forEach {
                categoryPreferenceRepository.recordDislike(it)
            }

            val userPreferences = UserPreferences(
                id = 1,
                mode = "personalized",
                preferenceVector = averageEmbeddings(likedWallpapers.map { it.embedding }),
                originalEmbedding = originalEmbedding,
                likedWallpaperIds = likedWallpapers.map { it.id },
                dislikedWallpaperIds = dislikedWallpapers.map { it.id },
                feedbackCount = likedWallpapers.size + dislikedWallpapers.size,
                epsilon = UserPreferences.DEFAULT_EPSILON,
                lastUpdated = now
            )

            preferenceRepository.insertUserPreferences(userPreferences)

            var savedPreferences: UserPreferences? = null
            var attempts = 0
            while (savedPreferences == null && attempts < 5) {
                delay(100L)
                savedPreferences = preferenceRepository.getUserPreferencesOnce()
                attempts++
            }
            if (savedPreferences == null || savedPreferences.feedbackCount == 0) {
                Log.e(TAG, "Preferences were not properly saved")
                return Result.failure(Exception("Preferences not persisted to database"))
            }

            Log.d(TAG, "Successfully initialized with ${likedWallpapers.size} taste anchors")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize preferences", e)
            Result.failure(e)
        }
    }

    /** Mean of the given embeddings, normalised to unit length. */
    private fun averageEmbeddings(embeddings: List<FloatArray>): FloatArray {
        val usable = embeddings.filter { it.isNotEmpty() }
        if (usable.isEmpty()) return FloatArray(0)

        val dim = usable.first().size
        val averaged = FloatArray(dim)
        usable.forEach { embedding ->
            for (i in 0 until dim) averaged[i] += embedding[i]
        }
        val count = usable.size.toFloat()
        for (i in 0 until dim) averaged[i] /= count

        var magnitude = 0f
        for (v in averaged) magnitude += v * v
        if (magnitude == 0f) return averaged
        magnitude = kotlin.math.sqrt(magnitude)
        return FloatArray(dim) { averaged[it] / magnitude }
    }
}
