package me.avinas.vanderwaals.domain.usecase

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

private val Context.engagementDataStore by preferencesDataStore("user_engagement")

/**
 * Tracks user engagement and determines optimal sync intervals.
 * 
 * Engagement is measured by:
 * - App launches (frequency and recency)
 * - Wallpaper changes (manual interactions)
 * - Time spent in app (estimated from session starts)
 * - Feedback given (likes/dislikes indicate active usage)
 * 
 * **Sync Intervals**:
 * - HIGH engagement: Daily (24 hours)
 * - MEDIUM engagement: Every 3 days (72 hours)
 * - LOW engagement: Weekly (168 hours)
 * - MINIMAL engagement: Every 2 weeks (336 hours)
 * 
 * **Usage**:
 * ```kotlin
 * val engagement = userEngagementTracker.calculateEngagement()
 * val syncInterval = userEngagementTracker.getSyncIntervalHours(engagement)
 * 
 * // Schedule sync with optimal interval
 * scheduleSyncWork(intervalHours = syncInterval)
 * ```
 */
@Singleton
class UserEngagementTracker @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    
    private val dataStore = context.engagementDataStore
    
    companion object {
        private const val TAG = "UserEngagementTracker"
        
        // DataStore keys
        private val KEY_APP_LAUNCHES = intPreferencesKey("engagement_app_launches")
        private val KEY_LAST_LAUNCH = longPreferencesKey("engagement_last_launch")
        private val KEY_WALLPAPER_CHANGES = intPreferencesKey("engagement_wallpaper_changes")
        private val KEY_FEEDBACK_COUNT = intPreferencesKey("engagement_feedback_count")
        private val KEY_TOTAL_SESSIONS = intPreferencesKey("engagement_total_sessions")
        private val KEY_LAST_ENGAGEMENT_CALC = longPreferencesKey("engagement_last_calculation")
        
        // Engagement thresholds (launches per week)
        private const val HIGH_ENGAGEMENT_LAUNCHES = 14  // 2+ times per day
        private const val MEDIUM_ENGAGEMENT_LAUNCHES = 7  // Once per day
        private const val LOW_ENGAGEMENT_LAUNCHES = 3    // Few times per week
        
        // Sync intervals (hours)
        const val SYNC_INTERVAL_HIGH = 24      // Daily
        const val SYNC_INTERVAL_MEDIUM = 72    // Every 3 days
        const val SYNC_INTERVAL_LOW = 168      // Weekly
        const val SYNC_INTERVAL_MINIMAL = 336  // Every 2 weeks
        
        // Time windows for engagement calculation
        private const val RECENT_WINDOW_DAYS = 7  // Look at last 7 days
        private const val DECAY_FACTOR = 0.9      // Decay older data
    }
    
    /**
     * Engagement levels based on user activity.
     */
    enum class EngagementLevel {
        HIGH,      // Very active user: daily app usage
        MEDIUM,    // Regular user: several times per week
        LOW,       // Occasional user: few times per week
        MINIMAL    // Rare user: once per week or less
    }
    
    /**
     * Records an app launch event.
     * Call this from MainActivity.onCreate() or Application.onCreate().
     */
    suspend fun recordAppLaunch() {
        try {
            val currentTime = System.currentTimeMillis()
            
            dataStore.edit { prefs ->
                // Increment launch count
                val launches = (prefs[KEY_APP_LAUNCHES] ?: 0) + 1
                prefs[KEY_APP_LAUNCHES] = launches
                
                // Update last launch time
                prefs[KEY_LAST_LAUNCH] = currentTime
                
                // Increment session count
                val sessions = (prefs[KEY_TOTAL_SESSIONS] ?: 0) + 1
                prefs[KEY_TOTAL_SESSIONS] = sessions
                
                Log.d(TAG, "App launch recorded: $launches total launches, $sessions sessions")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record app launch", e)
        }
    }
    
    /**
     * Records a wallpaper change event.
     * Call this when user manually changes wallpaper or applies a new one.
     */
    suspend fun recordWallpaperChange() {
        try {
            dataStore.edit { prefs ->
                val changes = (prefs[KEY_WALLPAPER_CHANGES] ?: 0) + 1
                prefs[KEY_WALLPAPER_CHANGES] = changes
                Log.d(TAG, "Wallpaper change recorded: $changes total")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record wallpaper change", e)
        }
    }
    
    /**
     * Records user feedback (like/dislike).
     * Call this when user provides feedback on wallpapers.
     */
    suspend fun recordFeedback() {
        try {
            dataStore.edit { prefs ->
                val feedback = (prefs[KEY_FEEDBACK_COUNT] ?: 0) + 1
                prefs[KEY_FEEDBACK_COUNT] = feedback
                Log.d(TAG, "Feedback recorded: $feedback total")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record feedback", e)
        }
    }
    
    /**
     * Calculates current user engagement level.
     * 
     * Algorithm:
     * 1. Get app launches in recent window (last 7 days)
     * 2. Weight recent activity more heavily (decay factor)
     * 3. Factor in wallpaper changes and feedback
     * 4. Classify into engagement levels
     * 
     * @return Current engagement level
     */
    suspend fun calculateEngagement(): EngagementLevel {
        try {
            val currentTime = System.currentTimeMillis()
            
            // Get metrics
            val prefs = dataStore.data.first()
            val totalLaunches = prefs[KEY_APP_LAUNCHES] ?: 0
            val lastLaunch = prefs[KEY_LAST_LAUNCH]
            val wallpaperChanges = prefs[KEY_WALLPAPER_CHANGES] ?: 0
            val feedbackCount = prefs[KEY_FEEDBACK_COUNT] ?: 0
            val lastCalc = prefs[KEY_LAST_ENGAGEMENT_CALC]
            
            // Calculate days since last launch
            val lastLaunchTime = lastLaunch ?: currentTime
            val daysSinceLastLaunch = (currentTime - lastLaunchTime) / (1000 * 60 * 60 * 24)
            
            // If first launch or no data, default to MEDIUM
            if (totalLaunches == 0) {
                Log.d(TAG, "First launch, defaulting to MEDIUM engagement")
                return EngagementLevel.MEDIUM
            }
            
            // Calculate launches per week (estimate)
            val lastCalcTime = lastCalc ?: (currentTime - (7 * 24 * 60 * 60 * 1000))
            val daysSinceCalc = ((currentTime - lastCalcTime) / (1000 * 60 * 60 * 24)).toInt()
            val weeksTracking = min(daysSinceCalc, RECENT_WINDOW_DAYS) / 7.0
            val launchesPerWeek = if (weeksTracking > 0) totalLaunches / weeksTracking else totalLaunches.toDouble()
            
            // Factor in other engagement signals
            val engagementScore = launchesPerWeek + 
                (wallpaperChanges * 0.5) +  // Wallpaper changes show active use
                (feedbackCount * 0.3)        // Feedback shows engagement
            
            // Apply recency penalty (if user hasn't launched in a while, reduce engagement)
            val recencyFactor = when {
                daysSinceLastLaunch <= 1 -> 1.0
                daysSinceLastLaunch <= 3 -> 0.8
                daysSinceLastLaunch <= 7 -> 0.5
                else -> 0.3
            }
            
            val adjustedScore = engagementScore * recencyFactor
            
            // Classify engagement level
            val engagement = when {
                adjustedScore >= HIGH_ENGAGEMENT_LAUNCHES -> EngagementLevel.HIGH
                adjustedScore >= MEDIUM_ENGAGEMENT_LAUNCHES -> EngagementLevel.MEDIUM
                adjustedScore >= LOW_ENGAGEMENT_LAUNCHES -> EngagementLevel.LOW
                else -> EngagementLevel.MINIMAL
            }
            
            // Update last calculation time
            dataStore.edit { it[KEY_LAST_ENGAGEMENT_CALC] = currentTime }
            
            Log.d(TAG, "Engagement calculated: $engagement " +
                    "(score: ${"%.2f".format(adjustedScore)}, " +
                    "launches/week: ${"%.1f".format(launchesPerWeek)}, " +
                    "recency: ${daysSinceLastLaunch}d)")
            
            return engagement
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to calculate engagement, defaulting to MEDIUM", e)
            return EngagementLevel.MEDIUM
        }
    }
    
    /**
     * Gets optimal sync interval based on engagement level.
     * 
     * @param engagement User's engagement level
     * @return Sync interval in hours
     */
    fun getSyncIntervalHours(engagement: EngagementLevel): Int {
        return when (engagement) {
            EngagementLevel.HIGH -> SYNC_INTERVAL_HIGH
            EngagementLevel.MEDIUM -> SYNC_INTERVAL_MEDIUM
            EngagementLevel.LOW -> SYNC_INTERVAL_LOW
            EngagementLevel.MINIMAL -> SYNC_INTERVAL_MINIMAL
        }
    }
    
    /**
     * Gets a human-readable description of the engagement level.
     */
    fun getEngagementDescription(engagement: EngagementLevel): String {
        return when (engagement) {
            EngagementLevel.HIGH -> "Very Active (syncs daily)"
            EngagementLevel.MEDIUM -> "Regular User (syncs every 3 days)"
            EngagementLevel.LOW -> "Occasional User (syncs weekly)"
            EngagementLevel.MINIMAL -> "Rare User (syncs bi-weekly)"
        }
    }
    
    /**
     * Resets all engagement tracking data.
     * Useful for testing or user-requested data reset.
     */
    suspend fun resetEngagementData() {
        try {
            dataStore.edit { prefs ->
                prefs.remove(KEY_APP_LAUNCHES)
                prefs.remove(KEY_LAST_LAUNCH)
                prefs.remove(KEY_WALLPAPER_CHANGES)
                prefs.remove(KEY_FEEDBACK_COUNT)
                prefs.remove(KEY_TOTAL_SESSIONS)
                prefs.remove(KEY_LAST_ENGAGEMENT_CALC)
            }
            Log.d(TAG, "Engagement data reset")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reset engagement data", e)
        }
    }
    
    /**
     * Gets current engagement statistics for debugging/display.
     */
    suspend fun getEngagementStats(): EngagementStats {
        return try {
            val prefs = dataStore.data.first()
            val launches = prefs[KEY_APP_LAUNCHES] ?: 0
            val lastLaunch = prefs[KEY_LAST_LAUNCH]
            val changes = prefs[KEY_WALLPAPER_CHANGES] ?: 0
            val feedback = prefs[KEY_FEEDBACK_COUNT] ?: 0
            val sessions = prefs[KEY_TOTAL_SESSIONS] ?: 0
            
            EngagementStats(
                totalLaunches = launches,
                lastLaunchTimestamp = lastLaunch,
                wallpaperChanges = changes,
                feedbackCount = feedback,
                totalSessions = sessions
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get engagement stats", e)
            EngagementStats()
        }
    }
    
    /**
     * Data class for engagement statistics.
     */
    data class EngagementStats(
        val totalLaunches: Int = 0,
        val lastLaunchTimestamp: Long? = null,
        val wallpaperChanges: Int = 0,
        val feedbackCount: Int = 0,
        val totalSessions: Int = 0
    ) {
        fun getLastLaunchDaysAgo(): Int? {
            return lastLaunchTimestamp?.let { timestamp ->
                ((System.currentTimeMillis() - timestamp) / (1000 * 60 * 60 * 24)).toInt()
            }
        }
    }
}
