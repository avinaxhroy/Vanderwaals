package me.avinas.vanderwaals.domain.usecase

import android.util.Log
import kotlinx.coroutines.flow.first
import me.avinas.vanderwaals.data.entity.WallpaperHistory
import me.avinas.vanderwaals.data.repository.WallpaperRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for processing implicit feedback from wallpaper duration.
 * 
 * Learns user preferences based on how long a wallpaper stayed active:
 * - **Very Short Duration (< 5 min)**: Interpreted as DISLIKE - user changed quickly
 * - **Very Long Duration (> 24 hours)**: Interpreted as LIKE - user kept it long
 * - **Neutral Duration (5 min - 24 hours)**: No implicit feedback (normal usage)
 * 
 * **CRITICAL CONSTRAINT:**
 * Only processes implicit feedback for MANUAL wallpaper changes (via "Change Now" button).
 * Does NOT process for auto-change events (scheduled changes).
 * 
 * **Rationale:**
 * - Manual change = Active user decision → Strong signal
 * - Auto-change = Scheduled rotation → User may not have noticed → Weak/unreliable signal
 * 
 * **Learning Rate:**
 * Uses 30% of explicit feedback strength (0.3x multiplier) because:
 * - Implicit signals are less certain than explicit like/dislike
 * - Prevents over-fitting to duration patterns
 * - Balances with explicit feedback which has full strength
 * 
 * **Integration Flow:**
 * 1. WallpaperChangeWorker applies new wallpaper
 * 2. Worker marks previous wallpaper as removed (sets removedAt timestamp)
 * 3. Worker calls this use case ONLY if change was manual (not auto)
 * 4. This use case calculates duration and processes implicit feedback
 * 5. UpdatePreferencesUseCase updates preference vector with reduced learning rate
 * 
 * @property wallpaperRepository Repository for accessing wallpaper metadata
 * @property updatePreferencesUseCase Use case for updating preference vector
 * 
 * @see UpdatePreferencesUseCase
 * @see WallpaperHistory
 * @see me.avinas.vanderwaals.worker.WallpaperChangeWorker
 */
@Singleton
class ProcessImplicitFeedbackUseCase @Inject constructor(
    private val wallpaperRepository: WallpaperRepository,
    private val updatePreferencesUseCase: UpdatePreferencesUseCase
) {
    companion object {
        private const val TAG = "ProcessImplicitFeedback"
        
        /**
         * Duration threshold for implicit DISLIKE (5 minutes).
         * If wallpaper removed before this, it's a strong negative signal.
         */
        private const val DISLIKE_THRESHOLD_MS = 5 * 60 * 1000L // 5 minutes
        
        /**
         * Duration threshold for implicit LIKE (24 hours).
         * If wallpaper kept longer than this, it's a strong positive signal.
         */
        private const val LIKE_THRESHOLD_MS = 24 * 60 * 60 * 1000L // 24 hours
        
        /**
         * Learning rate multiplier for implicit feedback (30% of explicit).
         * Applied to UpdatePreferencesUseCase's base learning rate.
         */
        private const val IMPLICIT_LEARNING_RATE_MULTIPLIER = 0.3f
    }
    
    /**
     * Processes implicit feedback from a removed wallpaper's duration.
     * 
     * **ONLY call this for MANUAL wallpaper changes (not auto-change).**
     * 
     * @param history The wallpaper history entry that was just removed
     * @return Result<Unit> indicating success or failure
     * 
     * @throws IllegalArgumentException if history entry is still active (removedAt is null)
     * 
     * Example (in WallpaperChangeWorker):
     * ```kotlin
     * // Mark previous wallpaper as removed
     * val previousHistory = dao.getActiveWallpaper()
     * if (previousHistory != null) {
     *     dao.markRemoved(previousHistory.id, System.currentTimeMillis())
     *     
     *     // ONLY process implicit feedback if this was a MANUAL change
     *     if (isManualChange) {
     *         val updated = dao.getById(previousHistory.id)!!
     *         processImplicitFeedbackUseCase(updated)
     *     }
     * }
     * ```
     */
    suspend operator fun invoke(history: WallpaperHistory): Result<Unit> {
        return try {
            // Validate that the wallpaper has been removed
            if (history.removedAt == null) {
                return Result.failure(
                    IllegalArgumentException("Cannot process implicit feedback for active wallpaper. removedAt must be set.")
                )
            }
            
            // Skip if user already provided explicit feedback
            if (history.userFeedback != null) {
                Log.d(TAG, "Skipping implicit feedback for ${history.wallpaperId} - explicit feedback already provided: ${history.userFeedback}")
                return Result.success(Unit)
            }
            
            // Calculate duration
            val durationMs = history.removedAt - history.appliedAt
            val durationHours = durationMs / (60 * 60 * 1000f)
            
            Log.d(TAG, "Processing implicit feedback for ${history.wallpaperId}, duration: ${String.format("%.2f", durationHours)} hours")
            
            // Determine implicit feedback type based on duration
            val implicitFeedback = when {
                durationMs < DISLIKE_THRESHOLD_MS -> {
                    // Very short duration → DISLIKE
                    Log.d(TAG, "Implicit DISLIKE detected (duration < 5 min)")
                    FeedbackType.DISLIKE
                }
                durationMs > LIKE_THRESHOLD_MS -> {
                    // Very long duration → LIKE
                    Log.d(TAG, "Implicit LIKE detected (duration > 24 hours)")
                    FeedbackType.LIKE
                }
                else -> {
                    // Neutral duration → No feedback
                    Log.d(TAG, "Neutral duration (5 min - 24 hours), no implicit feedback")
                    return Result.success(Unit)
                }
            }
            
            // Get wallpaper metadata
            val allWallpapers = wallpaperRepository.getAllWallpapers().first()
            val wallpaper = allWallpapers.find { it.id == history.wallpaperId }
            
            if (wallpaper == null) {
                Log.w(TAG, "Wallpaper ${history.wallpaperId} not found in repository, skipping implicit feedback")
                return Result.success(Unit)
            }
            
            // Apply implicit feedback with reduced learning rate
            val result = updatePreferencesUseCase.invoke(
                wallpaper = wallpaper,
                feedback = implicitFeedback,
                learningRateMultiplier = IMPLICIT_LEARNING_RATE_MULTIPLIER
            )
            
            result.fold(
                onSuccess = {
                    Log.d(TAG, "Successfully processed implicit ${implicitFeedback.name} for ${history.wallpaperId}")
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to process implicit feedback: ${error.message}")
                }
            )
            
            result
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing implicit feedback", e)
            Result.failure(
                Exception("Failed to process implicit feedback: ${e.message}", e)
            )
        }
    }
}
