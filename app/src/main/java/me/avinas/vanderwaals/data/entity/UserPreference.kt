package me.avinas.vanderwaals.data.entity

/**
 * Room entity representing user's learned aesthetic preferences.
 * 
 * Stores the preference vector that evolves through user feedback using Exponential
 * Moving Average (EMA). This vector is used to rank and filter wallpapers for
 * personalized recommendations.
 * 
 * The preference vector is initialized from the user's uploaded favorite wallpaper
 * and continuously updated based on likes, dislikes, and implicit feedback
 * (wallpaper duration).
 * 
 * @property userId User identifier (typically a single user per device)
 * @property preferenceVector 576-dimensional vector representing aesthetic preferences
 * @property feedbackCount Total number of explicit feedback events (likes + dislikes)
 * @property lastUpdated Timestamp of last preference update
 * @property mode Personalization mode ("personalized" or "auto")
 */
data class UserPreference(
    val userId: String,
    val preferenceVector: FloatArray,
    val feedbackCount: Int,
    val lastUpdated: Long,
    val mode: String
)
