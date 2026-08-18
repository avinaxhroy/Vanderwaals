package me.avinas.vanderwaals.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Aggregate like/dislike/view counts per wallpaper category.
 * Score: (likes - 2*dislikes) / (likes + dislikes + 1), range [-1, +1].
 */
@Entity(
    tableName = "category_preferences",
    indices = [Index(value = ["lastShown"])]
)
data class CategoryPreference(
    @PrimaryKey
    val category: String,
    val likes: Int = 0,
    val dislikes: Int = 0,
    val views: Int = 0,
    val lastShown: Long = 0L
) {
    /** Dislikes weighted 2× to avoid surfacing disliked categories. */
    fun calculateScore(): Float {
        val totalFeedback = likes + dislikes
        if (totalFeedback == 0) return 0f
        
        val weightedScore = likes - (2 * dislikes)
        return weightedScore.toFloat() / (totalFeedback + 1)
    }
    
    fun isUnderexplored(): Boolean {
        return views < 3 // Show at least 3 times before making judgment
    }
    
    /** Enforces temporal diversity in selection. */
    fun wasShownRecently(withinMillis: Long = 24 * 60 * 60 * 1000L): Boolean {
        return (System.currentTimeMillis() - lastShown) < withinMillis
    }
}
