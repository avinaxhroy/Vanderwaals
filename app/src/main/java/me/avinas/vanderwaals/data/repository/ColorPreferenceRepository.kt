package me.avinas.vanderwaals.data.repository

import kotlinx.coroutines.flow.Flow
import me.avinas.vanderwaals.data.entity.ColorPreference

interface ColorPreferenceRepository {
    /**
     * Emits updates whenever any color preference changes.
     */
    fun getAllColorPreferences(): Flow<List<ColorPreference>>
    
    /**
     * Emits null if color hasn't been encountered yet.
     */
    fun getColorPreference(colorHex: String): Flow<ColorPreference?>
    
    suspend fun getColorsByPreference(): List<String>
    
    // Used to build user's preferred color palette.
    suspend fun getLikedColors(): List<String>
    
    // Used to avoid colors the user tends to dislike.
    suspend fun getDislikedColors(): List<String>
    
    /**
     * Uses temporal diversity threshold to avoid repetition.
     */
    suspend fun getUnderutilizedColors(minTimeSinceShown: Long): List<String>
    
    suspend fun recordView(colorHex: String)
    
    suspend fun recordViews(colors: List<String>)
    
    suspend fun recordLike(colorHex: String)
    
    suspend fun recordLikes(colors: List<String>)
    
    suspend fun recordDislike(colorHex: String)
    
    suspend fun recordDislikes(colors: List<String>)
    
    /**
     * Score formula: (likes - 2×dislikes) / (likes + dislikes + 1), range [-2.0, 1.0].
     */
    suspend fun getColorScore(colorHex: String): Double
    
    // Useful for wallpapers with multiple dominant colors.
    suspend fun getAverageColorScore(colors: List<String>): Double
    
    // Useful for starting fresh or debugging.
    suspend fun resetAllColorPreferences()
    
    suspend fun getColorCount(): Int
}
