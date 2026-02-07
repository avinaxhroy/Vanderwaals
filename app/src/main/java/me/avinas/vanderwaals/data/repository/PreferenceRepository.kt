package me.avinas.vanderwaals.data.repository

import kotlinx.coroutines.flow.Flow
import me.avinas.vanderwaals.data.entity.UserPreferences

/**
 * Repository for user preference management and learning.
 * 
 * Responsibilities:
 * - Initializing preference vector from user's uploaded wallpaper
 * - Updating preference vector based on feedback (EMA algorithm)
 * - Calculating adaptive learning rates based on feedback count
 * - Managing mode switches (personalized/auto)
 * - Persisting preference state to database
 * 
 * Coordinates between:
 * - Algorithm layer (EmbeddingExtractor, PreferenceUpdater)
 * - Local database (Room)
 * - UI layer (feedback events)
 * 
 * @see me.avinas.vanderwaals.data.dao.UserPreferenceDao
 * @see me.avinas.vanderwaals.algorithm.PreferenceUpdater
 */
interface PreferenceRepository {
    /**
     * Get user preferences as a Flow
     */
    fun getUserPreferences(): Flow<UserPreferences?>
    
    /**
     * Get user preferences once (direct database read, no Flow caching)
     * Use this when you need the most recent value from database
     */
    suspend fun getUserPreferencesOnce(): UserPreferences?
    
    /**
     * Insert or replace user preferences (for initialization)
     */
    suspend fun insertUserPreferences(preferences: UserPreferences)
    
    /**
     * Update user preferences
     */
    suspend fun updateUserPreferences(preferences: UserPreferences)
    
    /**
     * Resets preference vectors for embedding dimension migration.
     * 
     * Preserves:
     * - Liked/disliked wallpaper IDs
     * - Mode setting
     * 
     * Clears:
     * - preferenceVector (set to empty)
     * - originalEmbedding (set to empty)
     * - momentumVector (set to empty)
     * - feedbackCount (reset to 0)
     * 
     * This is called when migrating from MobileNetV3 (576D) to MobileNetV4 (1280D)
     * since the embedding dimensions are incompatible.
     * 
     * @param keepMode If true, preserves current mode (auto/personalized). If false, resets to auto.
     */
    suspend fun resetForEmbeddingMigration(keepMode: Boolean = false)
}

