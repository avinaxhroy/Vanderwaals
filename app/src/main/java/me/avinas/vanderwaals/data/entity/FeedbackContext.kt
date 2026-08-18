package me.avinas.vanderwaals.data.entity

/**
 * Context captured when feedback is given. Currently collected but not used for ranking.
 */
data class FeedbackContext(
    val timeOfDay: Int,        // 0-23 hours
    val dayOfWeek: Int,        // 1-7 (1=Monday, 7=Sunday)
    val batteryLevel: Int,     // 0-100 percentage
    val screenBrightness: Int  // 0-255 system brightness
) {
    companion object {
        fun fromCurrentState(context: android.content.Context): FeedbackContext {
            val calendar = java.util.Calendar.getInstance()
            val timeOfDay = calendar.get(java.util.Calendar.HOUR_OF_DAY)
            val dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK)
            
            val batteryManager = context.getSystemService(android.content.Context.BATTERY_SERVICE) 
                as? android.os.BatteryManager
            val batteryLevel = batteryManager?.getIntProperty(
                android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY
            ) ?: 50 // Default to 50% if unavailable
            
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
