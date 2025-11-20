package me.avinas.vanderwaals.domain.usecase

/**
 * Use case for processing user feedback and updating preferences.
 * 
 * Handles both explicit feedback (likes/dislikes) and implicit feedback (wallpaper duration):
 * 
 * Explicit feedback flow:
 * 1. Record feedback in history database
 * 2. Extract embedding from liked/disliked wallpaper
 * 3. Update preference vector using EMA algorithm with adaptive learning rate
 * 4. Normalize preference vector to unit length
 * 5. Persist updated preferences
 * 6. Trigger reranking of wallpaper queue
 * 
 * Implicit feedback flow:
 * 1. Calculate wallpaper duration (time_removed - time_applied)
 * 2. Convert duration to feedback signal:
 *    - < 5 minutes: Strong dislike (weight = -0.2)
 *    - > 24 hours: Strong like (weight = +0.2)
 *    - Otherwise: Neutral (no update)
 * 3. Apply same EMA update process
 * 
 * @see me.avinas.vanderwaals.algorithm.PreferenceUpdater
 * @see me.avinas.vanderwaals.data.repository.PreferenceRepository
 * @see me.avinas.vanderwaals.data.repository.FeedbackRepository
 */
class ProcessFeedbackUseCase {
    
}
