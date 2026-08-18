package me.avinas.vanderwaals.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.avinas.vanderwaals.data.entity.UserPreferences

/**
 * DAO for the single-row user_preferences table.
 * Stores the preference vector, feedback count, and personalization mode.
 */
@Dao
interface UserPreferenceDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(preferences: UserPreferences)
    
    @Query("SELECT * FROM user_preferences WHERE id = 1")
    fun get(): Flow<UserPreferences?>
    
    @Query("SELECT * FROM user_preferences WHERE id = 1")
    suspend fun getOnce(): UserPreferences?
    
    @Update
    suspend fun update(preferences: UserPreferences)
    
    @Query("""
        UPDATE user_preferences 
        SET feedbackCount = feedbackCount + 1,
            lastUpdated = :timestamp
        WHERE id = 1
    """)
    suspend fun incrementFeedbackCount(timestamp: Long)
    
    @Query("""
        UPDATE user_preferences 
        SET mode = :mode,
            lastUpdated = :timestamp
        WHERE id = 1
    """)
    suspend fun switchMode(mode: String, timestamp: Long)
    
    @Query("""
        UPDATE user_preferences 
        SET epsilon = :epsilon,
            lastUpdated = :timestamp
        WHERE id = 1
    """)
    suspend fun updateEpsilon(epsilon: Float, timestamp: Long)
    
    @Query("""
        UPDATE user_preferences 
        SET feedbackCount = 0,
            epsilon = 0.1,
            lastUpdated = :timestamp
        WHERE id = 1
    """)
    suspend fun resetFeedback(timestamp: Long)
    
    /** Deletes all user preferences; [insert] must be called to re-initialize. */
    @Query("DELETE FROM user_preferences")
    suspend fun deleteAll()
    
    @Query("SELECT EXISTS(SELECT 1 FROM user_preferences WHERE id = 1)")
    suspend fun exists(): Boolean
}
