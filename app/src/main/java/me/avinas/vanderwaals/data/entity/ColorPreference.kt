package me.avinas.vanderwaals.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Aggregate like/dislike/view counts per hex color.
 * Fallback personalization signal when category data is missing.
 * Score: (likes - 2*dislikes) / (likes + dislikes + 1), range [-1, +1].
 */
@Entity(
    tableName = "color_preferences",
    indices = [Index(value = ["lastShown"])]
)
data class ColorPreference(
    @PrimaryKey
    val colorHex: String,
    val likes: Int = 0,
    val dislikes: Int = 0,
    val views: Int = 0,
    val lastShown: Long = 0L
) {
    /** Dislikes weighted 2× to avoid surfacing disliked colors. */
    fun calculateScore(): Float {
        val totalFeedback = likes + dislikes
        if (totalFeedback == 0) return 0f
        
        val weightedScore = likes - (2 * dislikes)
        return weightedScore.toFloat() / (totalFeedback + 1)
    }
    
    fun isUnderexplored(): Boolean {
        return views < 3 // Show at least 3 times before making judgment
    }
    
    /** Enforces color diversity in selection. */
    fun wasShownRecently(withinMillis: Long = 24 * 60 * 60 * 1000L): Boolean {
        return (System.currentTimeMillis() - lastShown) < withinMillis
    }
    
    fun toRgb(): Triple<Int, Int, Int> {
        val hex = colorHex.removePrefix("#")
        require(hex.length == 6) { "Invalid hex color format: $colorHex" }
        
        val r = hex.substring(0, 2).toInt(16)
        val g = hex.substring(2, 4).toInt(16)
        val b = hex.substring(4, 6).toInt(16)
        
        return Triple(r, g, b)
    }
}
