package me.avinas.vanderwaals.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

/**
 * Room entity representing user's learned aesthetic preferences.
 * 
 * Stores the preference vector that evolves through user feedback using Enhanced Exponential
 * Moving Average (EMA) with momentum. This vector is used to rank and filter wallpapers for
 * personalized recommendations.
 * 
 * **IMPORTANT: Both Auto and Personalize modes use this same table!**
 * 
 * **Personalize Mode:**
 * - Preference vector initialized from uploaded wallpaper (feedbackCount > 0 from day 1)
 * - User sees personalized recommendations immediately
 * - Vector continuously updated with likes/dislikes
 * 
 * **Auto Mode:**
 * - Starts with EMPTY preference vector (feedbackCount = 0)
 * - First like creates the preference vector
 * - Then updates exactly like Personalize Mode
 * - After 10-15 likes, becomes just as personalized
 * 
 * The preference vector is updated based on:
 * - Explicit feedback (likes and dislikes)
 * - Implicit feedback (wallpaper duration)
 * - Adaptive learning rates based on feedback count
 * - Momentum tracking for stable learning
 * 
 * **Single Row Table:**
 * This table always contains exactly one row (id = 1) per user/device.
 * Updates replace the existing preference state.
 * 
 * **Enhanced Learning Algorithm:**
 * - Uses adaptive learning rates that decrease with more feedback
 * - Momentum tracking to smooth updates and prevent oscillation
 * - Positive feedback: pulls preference vector toward liked wallpaper
 * - Negative feedback: pushes preference vector away from disliked wallpaper
 * - See VanderwaalsStrategy.md for complete algorithm details
 * 
 * **Type Converters:**
 * - Uses [Converters] to serialize `preferenceVector` (FloatArray)
 * - Uses [Converters] to serialize `momentumVector` (FloatArray)
 * - Uses [Converters] to serialize `likedWallpaperIds` and `dislikedWallpaperIds` (List<String>)
 * 
 * @property id Primary key, always 1 (single user preferences per device)
 * @property mode User's chosen mode: "auto" (learn from scratch) or "personalized" (started with upload)
 *                Note: Both modes use identical learning! Mode only indicates initialization method.
 * @property preferenceVector 576-dimensional vector representing aesthetic preferences
 *                Empty at start for Auto Mode, populated from upload for Personalize Mode
 * @property originalEmbedding Prime reference from upload/category (Personalize) or empty (Auto)
 * @property momentumVector 576-dimensional velocity vector for momentum-based learning
 * @property likedWallpaperIds List of wallpaper IDs the user explicitly liked
 * @property dislikedWallpaperIds List of wallpaper IDs the user explicitly disliked
 * @property feedbackCount Total number of explicit feedback events (likes + dislikes)
 *                0 at start for Auto Mode, > 0 from day 1 for Personalize Mode
 * @property epsilon Exploration rate for epsilon-greedy algorithm (0.0 to 1.0)
 * @property lastUpdated Timestamp of last preference update (milliseconds since epoch)
 */
@Entity(tableName = "user_preferences")
@TypeConverters(Converters::class)
data class UserPreferences(
    @PrimaryKey
    val id: Int = 1,
    val mode: String,
    val preferenceVector: FloatArray,
    val originalEmbedding: FloatArray = floatArrayOf(), // Prime reference from upload/category
    val momentumVector: FloatArray = floatArrayOf(),
    val likedWallpaperIds: List<String>,
    val dislikedWallpaperIds: List<String>,
    val feedbackCount: Int,
    val epsilon: Float,
    val lastUpdated: Long
) {
    /**
     * Override equals to properly compare FloatArray and Lists.
     * Auto-generated equals from data class doesn't handle arrays correctly.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UserPreferences

        if (id != other.id) return false
        if (mode != other.mode) return false
        if (!preferenceVector.contentEquals(other.preferenceVector)) return false
        if (!originalEmbedding.contentEquals(other.originalEmbedding)) return false
        if (!momentumVector.contentEquals(other.momentumVector)) return false
        if (likedWallpaperIds != other.likedWallpaperIds) return false
        if (dislikedWallpaperIds != other.dislikedWallpaperIds) return false
        if (feedbackCount != other.feedbackCount) return false
        if (epsilon != other.epsilon) return false
        if (lastUpdated != other.lastUpdated) return false

        return true
    }

    /**
     * Override hashCode to properly hash FloatArray.
     * Auto-generated hashCode from data class doesn't handle arrays correctly.
     */
    override fun hashCode(): Int {
        var result = id
        result = 31 * result + mode.hashCode()
        result = 31 * result + preferenceVector.contentHashCode()
        result = 31 * result + originalEmbedding.contentHashCode()
        result = 31 * result + momentumVector.contentHashCode()
        result = 31 * result + likedWallpaperIds.hashCode()
        result = 31 * result + dislikedWallpaperIds.hashCode()
        result = 31 * result + feedbackCount
        result = 31 * result + epsilon.hashCode()
        result = 31 * result + lastUpdated.hashCode()
        return result
    }

    companion object {
        /**
         * Default epsilon value for exploration (10% exploration, 90% exploitation).
         */
        const val DEFAULT_EPSILON = 0.1f

        /**
         * Mode constant: User chose Auto Mode (skipped upload, learns from scratch).
         * Algorithm starts with diverse wallpapers, then learns from likes/dislikes.
         * After 10-15 likes, becomes just as personalized as MODE_PERSONALIZED.
         */
        const val MODE_AUTO = "auto"

        /**
         * Mode constant: User chose Personalize Mode (uploaded favorite wallpaper).
         * Algorithm starts with preferences from upload, continues learning from feedback.
         * Uses same learning mechanism as MODE_AUTO, just different starting point.
         */
        const val MODE_PERSONALIZED = "personalized"

        /**
         * Creates a new UserPreferences instance with default values for a new user.
         * 
         * @param initialVector Optional initial preference vector (e.g., from uploaded wallpaper)
         * @return New UserPreferences instance in auto mode with empty feedback
         */
        fun createDefault(initialVector: FloatArray = floatArrayOf()): UserPreferences {
            return UserPreferences(
                id = 1,
                mode = MODE_AUTO,
                preferenceVector = initialVector,
                likedWallpaperIds = emptyList(),
                dislikedWallpaperIds = emptyList(),
                feedbackCount = 0,
                epsilon = DEFAULT_EPSILON,
                lastUpdated = System.currentTimeMillis()
            )
        }
    }
}
