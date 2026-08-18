package me.avinas.vanderwaals.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.avinas.vanderwaals.data.entity.CompositionPreference

/** DAO for composition preferences. Single-row table (id=1). */
@Dao
interface CompositionPreferenceDao {
    
    @Query("SELECT * FROM composition_preferences WHERE id = 1")
    fun getCompositionPreferencesFlow(): Flow<CompositionPreference?>
    
    @Query("SELECT * FROM composition_preferences WHERE id = 1")
    suspend fun getCompositionPreferences(): CompositionPreference?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(preferences: CompositionPreference)
    
    @Update
    suspend fun update(preferences: CompositionPreference)
    
    @Query("DELETE FROM composition_preferences")
    suspend fun deleteAll()
}
