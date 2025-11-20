package me.avinas.vanderwaals.ui.analytics

import me.avinas.vanderwaals.data.model.CategoryStats

/**
 * UI state for the Analytics screen
 * 
 * Comprehensive dashboard showing personalization effectiveness,
 * learning progress, and recommendation quality metrics.
 */
data class AnalyticsState(
    val isLoading: Boolean = true,
    val error: String? = null,
    
    // === Personalization Status ===
    val mode: String = "auto", // "auto" or "personalized"
    val isPersonalizationActive: Boolean = false, // True when learning is active (feedbackCount > 0)
    val isPersonalizationWorking: Boolean = false, // Same as isPersonalizationActive
    val personalizationQuality: PersonalizationQuality = PersonalizationQuality.NOT_INITIALIZED,
    
    // === Learning Progress ===
    val totalFeedbackCount: Int = 0,
    val likeCount: Int = 0,
    val dislikeCount: Int = 0,
    val feedbackRatio: Float = 0f, // likes / (likes + dislikes)
    
    // === Recommendation Impact ===
    val averageSimilarityScore: Float = 0f, // 0-100 scale
    val similarityTrend: SimilarityTrend = SimilarityTrend.STABLE,
    val originalAnchorInfluence: Float = 40f, // Always 40%
    val learnedAnchorInfluence: Float = 60f, // Always 60%
    
    // === History Stats ===
    val totalWallpapersViewed: Int = 0,
    val averageWallpaperDuration: Long = 0, // In seconds
    val mostLikedCategory: String? = null,
    val mostDislikedCategory: String? = null,
    
    // === Category Breakdown ===
    val categoryStats: List<CategoryStats> = emptyList(),
    val topCategories: List<CategoryInsight> = emptyList(),
    
    // === Recent Activity ===
    val recentLikes: Int = 0, // Last 7 days
    val recentDislikes: Int = 0, // Last 7 days
    val activityTrend: ActivityTrend = ActivityTrend.STABLE,
    
    // === Smart Insights ===
    val insights: List<SmartInsight> = emptyList(),
    val recommendations: List<String> = emptyList(),
    
    // === Advanced Metrics ===
    val explorationRate: Float = 0f, // Epsilon value
    val preferenceVectorMagnitude: Float = 0f,
    val learningRate: Float = 0f,
    val preferenceDrift: Float = 0f // Distance from original embedding
)

/**
 * Personalization quality levels based on feedback count and consistency
 */
enum class PersonalizationQuality {
    NOT_INITIALIZED,    // No preferences set
    LEARNING,          // < 5 feedback items
    DEVELOPING,        // 5-15 feedback items
    ESTABLISHED,       // 15-30 feedback items
    REFINED,           // 30+ feedback items with consistent patterns
    EXCELLENT          // 50+ feedback items with high consistency
}

/**
 * Similarity score trend over recent wallpapers
 */
enum class SimilarityTrend {
    IMPROVING,  // Getting more similar to preferences
    STABLE,     // Consistent similarity
    DECLINING   // Getting less similar (needs more feedback)
}

/**
 * Recent activity trend (last 7 days vs previous 7 days)
 */
enum class ActivityTrend {
    INCREASING,  // More active recently
    STABLE,      // Similar activity
    DECREASING   // Less active recently
}

/**
 * Category insight with preference strength
 */
data class CategoryInsight(
    val category: String,
    val likeCount: Int,
    val dislikeCount: Int,
    val preferenceStrength: Float, // -1.0 to 1.0
    val displayName: String,
    val emoji: String
)

/**
 * Smart insights based on user behavior and personalization state
 */
data class SmartInsight(
    val type: InsightType,
    val title: String,
    val description: String,
    val actionable: Boolean = false,
    val actionText: String? = null
)

/**
 * Types of insights shown to users
 */
enum class InsightType {
    SUCCESS,        // Personalization is working well
    LEARNING,       // System is learning from feedback
    NEED_FEEDBACK,  // More feedback needed
    DISCOVERY,      // Interesting pattern found
    TIP,            // Helpful tip for better experience
    WARNING         // Something needs attention
}
