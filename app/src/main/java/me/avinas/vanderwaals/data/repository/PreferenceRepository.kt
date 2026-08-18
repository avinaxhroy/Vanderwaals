package me.avinas.vanderwaals.data.repository

import kotlinx.coroutines.flow.Flow
import me.avinas.vanderwaals.data.entity.UserPreferences

interface PreferenceRepository {
    fun getUserPreferences(): Flow<UserPreferences?>
    
    // Direct database read (no Flow caching) - use for the most recent value.
    suspend fun getUserPreferencesOnce(): UserPreferences?
    
    suspend fun insertUserPreferences(preferences: UserPreferences)
    
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

