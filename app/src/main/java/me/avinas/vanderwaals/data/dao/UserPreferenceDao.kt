package me.avinas.vanderwaals.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.avinas.vanderwaals.data.entity.UserPreferences

/**
 * Room DAO for managing user preference vectors.
 * 
 * Provides queries for:
 * - Loading the current user's preference vector
 * - Updating preference vector after feedback
 * - Switching between personalized and auto modes
 * - Resetting preferences when user re-personalizes
 * 
 * **Singleton Pattern:**
 * The user_preferences table always contains exactly one row (id = 1).
 * All operations target this single row.
 * 
 * **Usage:**
 * ```kotlin
 * // Initialize on first launch
 * dao.insert(UserPreferences.createDefault())
 * 
 * // Load preferences reactively
 * dao.get().collect { preferences ->
 *     // Calculate similarities using preferences.preferenceVector
 * }
 * 
 * // Update after feedback
 * val updated = currentPreferences.copy(
 *     preferenceVector = newVector,
 *     feedbackCount = currentPreferences.feedbackCount + 1,
 *     lastUpdated = System.currentTimeMillis()
 * )
 * dao.update(updated)
 * ```
 * 
 * @see me.avinas.vanderwaals.data.entity.UserPreferences
 */
@Dao
interface UserPreferenceDao {
    
    /**
     * Inserts or replaces the user preferences.
     * 
     * Uses REPLACE conflict strategy to ensure only one preferences row exists.
     * Should be called on first app launch to initialize defaults.
     * 
     * @param preferences User preferences to insert (typically id = 1)
     * 
     * Example:
     * ```kotlin
     * // First launch initialization
     * val defaults = UserPreferences.createDefault()
     * dao.insert(defaults)
     * ```
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(preferences: UserPreferences)
    
    /**
     * Retrieves the user preferences as a reactive Flow.
     * 
     * Returns a Flow that emits the current preferences whenever they're updated.
     * Emits null if preferences haven't been initialized yet (first launch).
     * 
     * **Singleton:** Always queries for id = 1 (single user per device).
     * 
     * @return Flow emitting UserPreferences or null if not initialized
     * 
     * Example:
     * ```kotlin
     * dao.get().collect { preferences ->
     *     when {
     *         preferences == null -> initializeDefaults()
     *         preferences.mode == UserPreferences.MODE_AUTO -> useAutoMode()
     *         else -> usePersonalizedMode(preferences.preferenceVector)
     *     }
     * }
     * ```
     */
    @Query("SELECT * FROM user_preferences WHERE id = 1")
    fun get(): Flow<UserPreferences?>
    
    /**
     * Retrieves the user preferences as a one-shot suspend function.
     * 
     * Use this for background processing where reactive updates aren't needed.
     * 
     * @return UserPreferences or null if not initialized
     */
    @Query("SELECT * FROM user_preferences WHERE id = 1")
    suspend fun getOnce(): UserPreferences?
    
    /**
     * Updates the existing user preferences.
     * 
     * Used to update the preference vector after feedback, switch modes,
     * or modify epsilon for exploration.
     * 
     * @param preferences Updated preferences to save
     * 
     * Example:
     * ```kotlin
     * // Update preference vector after like
     * val current = dao.getOnce() ?: return
     * val updatedVector = applyEMA(
     *     current.preferenceVector,
     *     likedWallpaperEmbedding,
     *     learningRate = 0.15f
     * )
     * val updated = current.copy(
     *     preferenceVector = updatedVector,
     *     likedWallpaperIds = current.likedWallpaperIds + wallpaperId,
     *     feedbackCount = current.feedbackCount + 1,
     *     lastUpdated = System.currentTimeMillis()
     * )
     * dao.update(updated)
     * ```
     */
    @Update
    suspend fun update(preferences: UserPreferences)
    
    /**
     * Adds a wallpaper ID to the liked list.
     * 
     * Updates the likedWallpaperIds field and increments feedback count.
     * Also updates the lastUpdated timestamp.
     * 
     * @param wallpaperId ID of the liked wallpaper
     * @param timestamp Current timestamp in milliseconds
     */
    @Query("""
        UPDATE user_preferences 
        SET feedbackCount = feedbackCount + 1,
            lastUpdated = :timestamp
        WHERE id = 1
    """)
    suspend fun incrementFeedbackCount(timestamp: Long)
    
    /**
     * Switches the user's mode between auto and personalized.
     * 
     * @param mode New mode ("auto" or "personalized")
     * @param timestamp Current timestamp in milliseconds
     * 
     * Example:
     * ```kotlin
     * dao.switchMode(UserPreferences.MODE_PERSONALIZED, System.currentTimeMillis())
     * ```
     */
    @Query("""
        UPDATE user_preferences 
        SET mode = :mode,
            lastUpdated = :timestamp
        WHERE id = 1
    """)
    suspend fun switchMode(mode: String, timestamp: Long)
    
    /**
     * Updates the exploration epsilon value.
     * 
     * Used for adaptive exploration based on user satisfaction:
     * - High manual change frequency → increase epsilon (more exploration)
     * - Low manual changes → decrease epsilon (more exploitation)
     * 
     * @param epsilon New epsilon value (0.0 to 1.0)
     * @param timestamp Current timestamp in milliseconds
     * 
     * Example:
     * ```kotlin
     * // User keeps manually changing → increase exploration
     * if (manualChangeFrequency > threshold) {
     *     dao.updateEpsilon(0.2f, System.currentTimeMillis())
     * }
     * ```
     */
    @Query("""
        UPDATE user_preferences 
        SET epsilon = :epsilon,
            lastUpdated = :timestamp
        WHERE id = 1
    """)
    suspend fun updateEpsilon(epsilon: Float, timestamp: Long)
    
    /**
     * Resets preferences to default values.
     * 
     * Used when user wants to re-personalize their aesthetic.
     * Keeps the mode but resets vector, feedback, and IDs.
     * 
     * @param defaultVector Default preference vector (empty or universal)
     * @param timestamp Current timestamp in milliseconds
     */
    @Query("""
        UPDATE user_preferences 
        SET feedbackCount = 0,
            epsilon = 0.1,
            lastUpdated = :timestamp
        WHERE id = 1
    """)
    suspend fun resetFeedback(timestamp: Long)
    
    /**
     * Deletes all user preferences.
     * 
     * Used for testing or complete app data reset.
     * After this, [insert] must be called to re-initialize.
     */
    @Query("DELETE FROM user_preferences")
    suspend fun deleteAll()
    
    /**
     * Checks if preferences have been initialized.
     * 
     * @return true if preferences exist, false otherwise
     */
    @Query("SELECT EXISTS(SELECT 1 FROM user_preferences WHERE id = 1)")
    suspend fun exists(): Boolean
}
