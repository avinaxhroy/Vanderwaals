package me.avinas.vanderwaals.data.repository

/**
 * Repository for feedback history and category preference tracking.
 * 
 * Responsibilities:
 * - Recording explicit feedback (likes, dislikes)
 * - Tracking implicit feedback (wallpaper duration)
 * - Providing chronological history for UI
 * - Calculating category preferences from feedback patterns
 * - Managing history size (auto-delete after 100 entries)
 * 
 * Coordinates between:
 * - Local database (Room)
 * - Algorithm layer (preference updates)
 * - UI layer (history display)
 * 
 * @see me.avinas.vanderwaals.data.dao.FeedbackHistoryDao
 * @see me.avinas.vanderwaals.algorithm.PreferenceUpdater
 */
interface FeedbackRepository {
    
}
