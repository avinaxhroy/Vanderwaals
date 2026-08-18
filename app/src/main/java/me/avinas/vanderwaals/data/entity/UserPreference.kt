package me.avinas.vanderwaals.data.entity

/**
 * User's learned aesthetic preferences (EMA preference vector), initialized from
 * an uploaded favorite wallpaper and updated by feedback.
 */
data class UserPreference(
    val userId: String,
    val preferenceVector: FloatArray,
    val feedbackCount: Int,
    val lastUpdated: Long,
    val mode: String
)
