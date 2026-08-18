package me.avinas.vanderwaals.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

/**
 * Single-row entity holding learned preference state. Both modes share the
 * table; `mode` only records how the vector was initialized (empty vs. uploaded).
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
    val lastUpdated: Long,
    val moodAffinity: Map<String, Float> = emptyMap(),
    val styleAffinity: Map<String, Float> = emptyMap()
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
        if (moodAffinity != other.moodAffinity) return false
        if (styleAffinity != other.styleAffinity) return false

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
    result = 31 * result + moodAffinity.hashCode()
    result = 31 * result + styleAffinity.hashCode()
        return result
    }

    companion object {
        /** Default epsilon: 10% exploration, 90% exploitation. */
        const val DEFAULT_EPSILON = 0.1f

        /** Auto Mode: skipped upload, learns from scratch. */
        const val MODE_AUTO = "auto"

        /** Personalize Mode: seeded from an uploaded favorite wallpaper. */
        const val MODE_PERSONALIZED = "personalized"

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
