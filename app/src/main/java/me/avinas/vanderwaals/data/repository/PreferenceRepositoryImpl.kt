package me.avinas.vanderwaals.data.repository

import kotlinx.coroutines.flow.Flow
import me.avinas.vanderwaals.data.VanderwaalsDatabase
import me.avinas.vanderwaals.data.dao.UserPreferenceDao
import me.avinas.vanderwaals.data.entity.UserPreferences

/**
 * Implementation of PreferenceRepository for user preference management.
 */
class PreferenceRepositoryImpl(
    private val userPreferenceDao: UserPreferenceDao,
    private val database: VanderwaalsDatabase
) : PreferenceRepository {
    override fun getUserPreferences(): Flow<UserPreferences?> {
        return userPreferenceDao.get()
    }
    
    override suspend fun getUserPreferencesOnce(): UserPreferences? {
        // Force WAL checkpoint to sync database changes from other processes (Workers)
        try {
            database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(PASSIVE)").close()
        } catch (e: Exception) {
            android.util.Log.w("PreferenceRepository", "WAL checkpoint failed: ${e.message}")
        }
        return userPreferenceDao.getOnce()
    }

    override suspend fun insertUserPreferences(preferences: UserPreferences) {
        // Use retryable transaction for insert to handle potential conflicts
        me.avinas.vanderwaals.data.TransactionHelper.withRetryableTransaction(database) {
            userPreferenceDao.insert(preferences)
        }
    }

    override suspend fun updateUserPreferences(preferences: UserPreferences) {
        // Use retryable transaction for update to handle potential conflicts
        me.avinas.vanderwaals.data.TransactionHelper.withRetryableTransaction(database) {
            userPreferenceDao.update(preferences)
        }
    }
    
    override suspend fun resetForEmbeddingMigration(keepMode: Boolean) {
        val current = getUserPreferencesOnce()
        
        val migratedPreferences = if (current != null) {
            // Preserve liked/disliked IDs but clear embedding vectors
            UserPreferences(
                id = 1,
                mode = if (keepMode) current.mode else UserPreferences.MODE_AUTO,
                preferenceVector = floatArrayOf(),  // Clear - incompatible dimension
                originalEmbedding = floatArrayOf(), // Clear - incompatible dimension
                momentumVector = floatArrayOf(),    // Clear - incompatible dimension
                likedWallpaperIds = current.likedWallpaperIds,    // PRESERVE
                dislikedWallpaperIds = current.dislikedWallpaperIds, // PRESERVE
                feedbackCount = 0,  // Reset - will rebuild from new embeddings
                epsilon = UserPreferences.DEFAULT_EPSILON,
                lastUpdated = System.currentTimeMillis()
            )
        } else {
            // No existing preferences - create default
            UserPreferences.createDefault()
        }
        
        android.util.Log.i("PreferenceRepository", 
            "Embedding migration reset: preserved ${migratedPreferences.likedWallpaperIds.size} likes, " +
            "${migratedPreferences.dislikedWallpaperIds.size} dislikes, mode=${migratedPreferences.mode}"
        )
        
        // Use insert to replace existing row
        insertUserPreferences(migratedPreferences)
    }
}

