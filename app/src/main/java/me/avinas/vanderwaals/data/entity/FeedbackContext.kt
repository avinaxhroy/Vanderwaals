package me.avinas.vanderwaals.data.entity

/**
 * Data class capturing contextual information when feedback is provided.
 * 
 * This enables future contextual recommendations by tracking:
 * - Time patterns (what users like at different times/days)
 * - Device state (battery, brightness preferences)
 * - Environmental conditions for personalization
 * 
 * **Use Cases:**
 * - Time-aware recommendations: Different wallpapers for morning/evening
 * - Day-based patterns: Workday vs weekend preferences
 * - Battery-conscious selection: Lower resolution when battery low
 * - Brightness-adaptive content: Darker wallpapers in low light
 * 
 * **Future Enhancement:**
 * This data is currently collected but not used for ranking.
 * Future versions can:
 * - Boost wallpapers liked in similar contexts
 * - Create time-of-day profiles
 * - Adapt to user's daily routine
 * 
 * **Privacy:**
 * All context data stays on-device and is never transmitted.
 * No location or sensitive information is collected.
 * 
 * @property timeOfDay Hour of day when feedback given (0-23)
 * @property dayOfWeek Day of week when feedback given (1=Monday, 7=Sunday)
 * @property batteryLevel Battery percentage when feedback given (0-100)
 * @property screenBrightness Screen brightness when feedback given (0-255)
 */
data class FeedbackContext(
    val timeOfDay: Int,        // 0-23 hours
    val dayOfWeek: Int,        // 1-7 (1=Monday, 7=Sunday)
    val batteryLevel: Int,     // 0-100 percentage
    val screenBrightness: Int  // 0-255 system brightness
) {
    companion object {
        /**
         * Creates FeedbackContext from current device state.
         * 
         * @param context Android application context
         * @return FeedbackContext with current state
         */
        fun fromCurrentState(context: android.content.Context): FeedbackContext {
            val calendar = java.util.Calendar.getInstance()
            val timeOfDay = calendar.get(java.util.Calendar.HOUR_OF_DAY)
            val dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK)
            
            // Get battery level
            val batteryManager = context.getSystemService(android.content.Context.BATTERY_SERVICE) 
                as? android.os.BatteryManager
            val batteryLevel = batteryManager?.getIntProperty(
                android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY
            ) ?: 50 // Default to 50% if unavailable
            
            // Get screen brightness
            val screenBrightness = try {
                android.provider.Settings.System.getInt(
                    context.contentResolver,
                    android.provider.Settings.System.SCREEN_BRIGHTNESS
                )
            } catch (e: Exception) {
                128 // Default to mid-brightness if unavailable
            }
            
            return FeedbackContext(
                timeOfDay = timeOfDay,
                dayOfWeek = dayOfWeek,
                batteryLevel = batteryLevel,
                screenBrightness = screenBrightness
            )
        }
    }
}
