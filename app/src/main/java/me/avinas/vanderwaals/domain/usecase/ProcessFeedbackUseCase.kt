package me.avinas.vanderwaals.domain.usecase

import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for processing explicit user feedback and updating all relevant preferences.
 *
 * This is the single entry point for like/dislike/download signals. It delegates
 * to [UpdatePreferencesUseCase], which records the taste anchors and updates
 * the category, color, and composition preferences.
 *
 * @see UpdatePreferencesUseCase
 */
@Singleton
class ProcessFeedbackUseCase @Inject constructor(
    private val updatePreferencesUseCase: UpdatePreferencesUseCase
) {

    suspend operator fun invoke(
        wallpaper: WallpaperMetadata,
        feedback: FeedbackType
    ): Result<Unit> {
        return updatePreferencesUseCase(wallpaper, feedback)
    }
}
