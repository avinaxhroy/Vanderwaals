package me.avinas.vanderwaals.domain.usecase

import android.util.Log
import kotlinx.coroutines.flow.first
import me.avinas.vanderwaals.algorithm.RecommenderConfig
import me.avinas.vanderwaals.data.datastore.SettingsDataStore
import me.avinas.vanderwaals.data.entity.WallpaperHistory
import me.avinas.vanderwaals.data.repository.WallpaperRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Processes implicit feedback from how long a manually-applied wallpaper
 * stayed active.
 *
 * Thresholds are **relative to the user's change interval**, fixing the
 * legacy absolute thresholds (<5 min = dislike, >24 h = like) which made it
 * impossible for fast-rotation users (15 min / hourly / unlock) to ever
 * earn an implicit like while a few quick "Change Now" taps while browsing
 * injected dislike signals — a systematic bias that poisoned the model
 * harder the longer the app was used.
 *
 * Rules (manual changes only — scheduled rotation never reaches this use
 * case):
 * - removed before 25% of the expected interval → implicit dislike
 * - kept for at least 2× the expected interval → implicit like
 * - anything in between → no signal
 *
 * Implicit signals record at [RecommenderConfig.IMPLICIT_FEEDBACK_STRENGTH]
 * strength and, when negative, never steer the positive taste direction —
 * they only add bounded suppression.
 */
@Singleton
class ProcessImplicitFeedbackUseCase @Inject constructor(
    private val wallpaperRepository: WallpaperRepository,
    private val updatePreferencesUseCase: UpdatePreferencesUseCase,
    private val settingsDataStore: SettingsDataStore
) {
    companion object {
        private const val TAG = "ProcessImplicitFeedback"

        /** Removed faster than this fraction of the interval → dislike. */
        private const val DISLIKE_FRACTION = 0.25

        /** Kept longer than this multiple of the interval → like. */
        private const val LIKE_MULTIPLE = 2.0

        /**
         * Expected minutes between unlock-triggered changes; unlock
         * cadence varies per user, so a mid-range estimate is used.
         */
        private const val UNLOCK_EXPECTED_MINUTES = 45L

        /**
         * Nominal duration a wallpaper is expected to stay active for the
         * configured change interval.  Pure so unit tests can call it
         * without repository dependencies.
         */
        fun expectedIntervalMs(changeInterval: String): Long = when (changeInterval) {
            "15min" -> 15 * 60_000L
            "unlock" -> UNLOCK_EXPECTED_MINUTES * 60_000L
            "hourly" -> 60 * 60_000L
            "3hours" -> 3 * 60 * 60_000L
            "6hours" -> 6 * 60 * 60_000L
            "12hours" -> 12 * 60 * 60_000L
            "daily" -> 24 * 60 * 60_000L
            "3days" -> 3 * 24 * 60 * 60_000L
            "7days" -> 7 * 24 * 60 * 60_000L
            else -> 24 * 60 * 60_000L
        }
    }

    suspend operator fun invoke(history: WallpaperHistory): Result<Unit> {
        return try {
            if (history.removedAt == null) {
                return Result.failure(
                    IllegalArgumentException(
                        "Cannot process implicit feedback for active wallpaper. removedAt must be set."
                    )
                )
            }

            // Explicit feedback always wins.
            if (history.userFeedback != null) {
                Log.d(TAG, "Skipping implicit feedback for ${history.wallpaperId} - explicit feedback: ${history.userFeedback}")
                return Result.success(Unit)
            }

            val settings = settingsDataStore.settings.first()
            val expectedMs = expectedIntervalMs(settings.changeInterval)
            val durationMs = history.removedAt - history.appliedAt
            val implicitFeedback = when {
                durationMs < (expectedMs * DISLIKE_FRACTION) -> FeedbackType.DISLIKE
                durationMs >= (expectedMs * LIKE_MULTIPLE) -> FeedbackType.LIKE
                else -> {
                    Log.d(
                        TAG,
                        "Neutral duration for ${history.wallpaperId}: " +
                            "${durationMs / 60000} min vs expected ${expectedMs / 60000} min"
                    )
                    return Result.success(Unit)
                }
            }

            // By-id lookup — the full catalog (with embeddings) is never
            // needed just to attribute one feedback event.
            val wallpaper = wallpaperRepository.getWallpaperById(history.wallpaperId)
            if (wallpaper == null) {
                Log.w(TAG, "Wallpaper ${history.wallpaperId} not found, skipping implicit feedback")
                return Result.success(Unit)
            }

            Log.d(
                TAG,
                "Implicit ${implicitFeedback.name} for ${history.wallpaperId} " +
                    "(${durationMs / 60000} min vs expected ${expectedMs / 60000} min)"
            )

            updatePreferencesUseCase.invoke(
                wallpaper = wallpaper,
                feedback = implicitFeedback,
                learningRateMultiplier = RecommenderConfig.IMPLICIT_FEEDBACK_STRENGTH
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error processing implicit feedback", e)
            Result.failure(Exception("Failed to process implicit feedback: ${e.message}", e))
        }
    }

}
