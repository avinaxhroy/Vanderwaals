package me.avinas.vanderwaals.domain.usecase

import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for processing explicit user feedback and updating all relevant preferences.
 *
 * This is the single entry point for like/dislike/download signals. It delegates
 * fully to [UpdatePreferencesUseCase], which updates the embedding preference
 * vector, category preferences, color preferences, and composition preferences
 * using EMA with adaptive learning rates.
 *
 * @see UpdatePreferencesUseCase
 */
@Singleton
class ProcessFeedbackUseCase @Inject constructor(
    private val updatePreferencesUseCase: UpdatePreferencesUseCase
) {

    /**
     * Processes explicit feedback for a wallpaper.
     *
     * @param wallpaper  The wallpaper that received feedback.
     * @param feedback   The type of feedback ([FeedbackType.LIKE], [FeedbackType.DISLIKE],
     *                   or [FeedbackType.DOWNLOAD]).
     * @return [Result.success] on success, [Result.failure] with the underlying error otherwise.
     */
    suspend operator fun invoke(
        wallpaper: WallpaperMetadata,
        feedback: FeedbackType
    ): Result<Unit> {
        return updatePreferencesUseCase(wallpaper, feedback)
    }
}
