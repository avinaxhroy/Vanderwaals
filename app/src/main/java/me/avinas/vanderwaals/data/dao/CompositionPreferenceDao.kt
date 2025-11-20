package me.avinas.vanderwaals.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.avinas.vanderwaals.data.entity.CompositionPreference

/**
 * Data Access Object for composition preferences.
 * 
 * Stores and retrieves user's learned composition/layout preferences.
 * Single-row table (id=1) containing global composition preferences.
 * 
 * @see CompositionPreference
 */
@Dao
interface CompositionPreferenceDao {
    
    /**
     * Gets composition preferences as Flow for reactive updates.
     * 
     * @return Flow of composition preferences (null if not initialized)
     */
    @Query("SELECT * FROM composition_preferences WHERE id = 1")
    fun getCompositionPreferencesFlow(): Flow<CompositionPreference?>
    
    /**
     * Gets composition preferences synchronously (single read).
     * 
     * @return Composition preferences or null if not initialized
     */
    @Query("SELECT * FROM composition_preferences WHERE id = 1")
    suspend fun getCompositionPreferences(): CompositionPreference?
    
    /**
     * Inserts composition preferences.
     * Uses REPLACE strategy to overwrite existing preferences.
     * 
     * @param preferences Composition preferences to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(preferences: CompositionPreference)
    
    /**
     * Updates existing composition preferences.
     * 
     * @param preferences Updated composition preferences
     */
    @Update
    suspend fun update(preferences: CompositionPreference)
    
    /**
     * Deletes all composition preferences (reset).
     * Used for clearing user data.
     */
    @Query("DELETE FROM composition_preferences")
    suspend fun deleteAll()
}
