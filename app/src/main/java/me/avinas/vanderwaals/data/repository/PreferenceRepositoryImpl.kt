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
        userPreferenceDao.insert(preferences)
    }

    override suspend fun updateUserPreferences(preferences: UserPreferences) {
        userPreferenceDao.update(preferences)
    }
}

