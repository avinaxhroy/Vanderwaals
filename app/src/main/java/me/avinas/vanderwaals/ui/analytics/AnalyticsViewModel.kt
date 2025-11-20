package me.avinas.vanderwaals.ui.analytics

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import me.avinas.vanderwaals.data.entity.WallpaperHistory
import me.avinas.vanderwaals.data.repository.PreferenceRepository
import me.avinas.vanderwaals.data.repository.WallpaperRepository
import me.avinas.vanderwaals.data.model.CategoryStats
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * ViewModel for Analytics screen
 * 
 * Computes comprehensive personalization metrics from:
 * - UserPreferences (preference vector, feedback counts, learning state)
 * - WallpaperHistory (likes, dislikes, duration, timestamps)
 * - WallpaperMetadata (categories, embeddings)
 */
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val preferenceRepository: PreferenceRepository,
    private val wallpaperRepository: WallpaperRepository
) : ViewModel() {

    private val TAG = "AnalyticsViewModel"

    private val _state = MutableStateFlow(AnalyticsState())
    val state: StateFlow<AnalyticsState> = _state.asStateFlow()

    init {
        loadAnalytics()
    }

    /**
     * Load and compute all analytics metrics
     */
    private fun loadAnalytics() {
        // CRITICAL: Run heavy math calculations on Default dispatcher (CPU intensive)
        viewModelScope.launch(Dispatchers.Default) {
            try {
                _state.update { it.copy(isLoading = true, error = null) }

                // Combine all data sources
                combine(
                    preferenceRepository.getUserPreferences(),
                    wallpaperRepository.getHistory(),
                    wallpaperRepository.getAllWallpapers()
                ) { preferences, history, allWallpapers ->
                    Triple(preferences, history, allWallpapers)
                }.collectLatest { (preferences, history, allWallpapers) ->
                    
                    if (preferences == null) {
                        _state.update { 
                            it.copy(
                                isLoading = false,
                                isPersonalizationActive = false,
                                personalizationQuality = PersonalizationQuality.NOT_INITIALIZED,
                                insights = listOf(
                                    SmartInsight(
                                        type = InsightType.TIP,
                                        title = "Ready to personalize?",
                                        description = "Start the onboarding process to teach Vanderwaals your style. Upload a favorite wallpaper or browse categories!",
                                        actionable = true,
                                        actionText = "Start Personalization"
                                    )
                                )
                            )
                        }
                        return@collectLatest
                    }

                    // Compute all metrics
                    val feedbackCount = preferences.feedbackCount
                    val mode = preferences.mode
                    
                    // IMPORTANT: Both Auto and Personalize modes learn!
                    // isLearning = true when preference vector exists (feedbackCount > 0)
                    val isLearning = feedbackCount > 0 || preferences.preferenceVector.isNotEmpty()
                    
                    // Calculate like/dislike counts
                    val likeCount = preferences.likedWallpaperIds.size
                    val dislikeCount = preferences.dislikedWallpaperIds.size
                    val feedbackRatio = if (feedbackCount > 0) {
                        likeCount.toFloat() / feedbackCount.toFloat()
                    } else 0f

                    // Determine personalization quality
                    val quality = determineQuality(feedbackCount, feedbackRatio)
                    
                    // Calculate history stats
                    val (avgDuration, recentLikes, recentDislikes) = calculateHistoryStats(history)
                    
                    // Calculate category stats
                    val categoryInsights = calculateCategoryInsights(history, allWallpapers)
                    val mostLiked = categoryInsights.maxByOrNull { it.likeCount }
                    val mostDisliked = categoryInsights.maxByOrNull { it.dislikeCount }
                    
                    // Calculate similarity metrics
                    val avgSimilarity = calculateAverageSimilarity(preferences, history, allWallpapers)
                    val trend = calculateSimilarityTrend(history, preferences, allWallpapers)
                    
                    // Calculate activity trend
                    val activityTrend = calculateActivityTrend(history)
                    
                    // Calculate preference drift
                    val drift = calculatePreferenceDrift(preferences)
                    
                    // Calculate learning rate
                    val learningRate = calculateLearningRate(feedbackCount)
                    
                    // Calculate preference vector magnitude
                    val vectorMagnitude = sqrt(preferences.preferenceVector.sumOf { (it * it).toDouble() }.toFloat())
                    
                    // Generate smart insights
                    val insights = generateInsights(
                        isLearning,  // Both modes learn!
                        quality,
                        feedbackCount,
                        feedbackRatio,
                        trend,
                        activityTrend,
                        drift,
                        categoryInsights
                    )

                    // Generate recommendations
                    val recommendations = generateRecommendations(
                        quality,
                        feedbackCount,
                        categoryInsights,
                        avgDuration
                    )

                    _state.update {
                        it.copy(
                            isLoading = false,
                            mode = mode,
                            isPersonalizationActive = isLearning,
                            isPersonalizationWorking = isLearning,
                            personalizationQuality = quality,
                            totalFeedbackCount = feedbackCount,
                            likeCount = likeCount,
                            dislikeCount = dislikeCount,
                            feedbackRatio = feedbackRatio,
                            averageSimilarityScore = avgSimilarity,
                            similarityTrend = trend,
                            totalWallpapersViewed = history.size,
                            averageWallpaperDuration = avgDuration,
                            mostLikedCategory = mostLiked?.category,
                            mostDislikedCategory = mostDisliked?.category,
                            topCategories = categoryInsights.take(5),
                            recentLikes = recentLikes,
                            recentDislikes = recentDislikes,
                            activityTrend = activityTrend,
                            insights = insights,
                            recommendations = recommendations,
                            explorationRate = preferences.epsilon,
                            preferenceVectorMagnitude = vectorMagnitude,
                            learningRate = learningRate,
                            preferenceDrift = drift
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading analytics", e)
                _state.update { 
                    it.copy(
                        isLoading = false, 
                        error = "Failed to load analytics: ${e.message}"
                    ) 
                }
            }
        }
    }

    /**
     * Determine personalization quality based on feedback count and consistency
     */
    private fun determineQuality(feedbackCount: Int, feedbackRatio: Float): PersonalizationQuality {
        if (feedbackCount == 0) return PersonalizationQuality.NOT_INITIALIZED
        
        val isConsistent = feedbackRatio in 0.3f..0.7f || feedbackRatio > 0.7f
        
        return when {
            feedbackCount >= 50 && isConsistent -> PersonalizationQuality.EXCELLENT
            feedbackCount >= 30 && isConsistent -> PersonalizationQuality.REFINED
            feedbackCount >= 15 -> PersonalizationQuality.ESTABLISHED
            feedbackCount >= 5 -> PersonalizationQuality.DEVELOPING
            else -> PersonalizationQuality.LEARNING
        }
    }

    /**
     * Calculate history-based statistics
     */
    private fun calculateHistoryStats(history: List<WallpaperHistory>): Triple<Long, Int, Int> {
        val avgDuration = if (history.isNotEmpty()) {
            history.mapNotNull { it.getDurationSeconds() }.average().toLong()
        } else 0L

        val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
        val recentHistory = history.filter { it.appliedAt >= sevenDaysAgo }
        
        val recentLikes = recentHistory.count { it.userFeedback == "like" }
        val recentDislikes = recentHistory.count { it.userFeedback == "dislike" }

        return Triple(avgDuration, recentLikes, recentDislikes)
    }

    /**
     * Calculate category insights from history
     */
    private fun calculateCategoryInsights(
        history: List<WallpaperHistory>,
        allWallpapers: List<me.avinas.vanderwaals.data.entity.WallpaperMetadata>
    ): List<CategoryInsight> {
        val wallpaperMap = allWallpapers.associateBy { it.id }
        val categoryMap = mutableMapOf<String, Pair<Int, Int>>() // category -> (likes, dislikes)

        history.forEach { h ->
            val wallpaper = wallpaperMap[h.wallpaperId]
            if (wallpaper != null && h.userFeedback != null) {
                val category = wallpaper.category
                val current = categoryMap.getOrDefault(category, Pair(0, 0))
                
                when (h.userFeedback) {
                    "like" -> categoryMap[category] = Pair(current.first + 1, current.second)
                    "dislike" -> categoryMap[category] = Pair(current.first, current.second + 1)
                }
            }
        }

        return categoryMap.map { (category, counts) ->
            val (likes, dislikes) = counts
            val total = likes + dislikes
            val strength = if (total > 0) {
                (likes - dislikes).toFloat() / total.toFloat()
            } else 0f

            CategoryInsight(
                category = category,
                likeCount = likes,
                dislikeCount = dislikes,
                preferenceStrength = strength,
                displayName = category.replaceFirstChar { it.uppercase() },
                emoji = getCategoryEmoji(category)
            )
        }.sortedByDescending { abs(it.preferenceStrength) }
    }

    /**
     * Get emoji for category
     */
    private fun getCategoryEmoji(category: String): String {
        return when (category.lowercase()) {
            "nature", "landscape" -> "🌲"
            "minimal", "minimalist" -> "⬜"
            "abstract" -> "🎨"
            "anime", "manga" -> "🎭"
            "dark", "gruvbox", "nord" -> "🌙"
            "space", "galaxy" -> "🌌"
            "city", "urban" -> "🏙️"
            "architecture" -> "🏛️"
            "art", "artistic" -> "🖼️"
            else -> "🖼️"
        }
    }

    /**
     * Calculate average similarity score for recent wallpapers
     */
    private fun calculateAverageSimilarity(
        preferences: me.avinas.vanderwaals.data.entity.UserPreferences,
        history: List<WallpaperHistory>,
        allWallpapers: List<me.avinas.vanderwaals.data.entity.WallpaperMetadata>
    ): Float {
        val wallpaperMap = allWallpapers.associateBy { it.id }
        val recent = history.takeLast(10)
        
        // If no history, return neutral score
        if (recent.isEmpty()) return 50f
        
        // If preference vector is empty but we have history, return a neutral score
        // The card will show "gathering data" state
        if (preferences.preferenceVector.isEmpty()) return 50f

        val similarities = recent.mapNotNull { h ->
            wallpaperMap[h.wallpaperId]?.let { wallpaper ->
                cosineSimilarity(preferences.preferenceVector, wallpaper.embedding)
            }
        }

        return if (similarities.isNotEmpty()) {
            (similarities.average().toFloat() * 100f).coerceIn(0f, 100f)
        } else 50f // Return neutral if no similarities could be calculated
    }

    /**
     * Calculate cosine similarity between two vectors
     */
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        
        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0
        
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        
        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator > 0) (dotProduct / denominator).toFloat() else 0f
    }

    /**
     * Calculate similarity trend over recent wallpapers
     */
    private fun calculateSimilarityTrend(
        history: List<WallpaperHistory>,
        preferences: me.avinas.vanderwaals.data.entity.UserPreferences,
        allWallpapers: List<me.avinas.vanderwaals.data.entity.WallpaperMetadata>
    ): SimilarityTrend {
        // Return STABLE if we have insufficient data to calculate a trend
        if (history.isEmpty()) return SimilarityTrend.STABLE
        
        // If preference vector is empty, we can't calculate cosine similarity
        // But we still have history, so return STABLE instead of early exit
        if (preferences.preferenceVector.isEmpty()) return SimilarityTrend.STABLE

        val wallpaperMap = allWallpapers.associateBy { it.id }
        val recent = history.takeLast(10)
        
        // Need at least 2 items to calculate a trend
        if (recent.size < 2) return SimilarityTrend.STABLE
        
        // Split into two halves for comparison
        val firstHalf = recent.take(recent.size / 2).mapNotNull { h ->
            wallpaperMap[h.wallpaperId]?.let { 
                cosineSimilarity(preferences.preferenceVector, it.embedding)
            }
        }

        val secondHalf = recent.takeLast(recent.size / 2).mapNotNull { h ->
            wallpaperMap[h.wallpaperId]?.let { 
                cosineSimilarity(preferences.preferenceVector, it.embedding)
            }
        }
        
        // If we couldn't calculate similarities for either half, return STABLE
        if (firstHalf.isEmpty() || secondHalf.isEmpty()) return SimilarityTrend.STABLE

        val firstAvg = firstHalf.average()
        val secondAvg = secondHalf.average()
        val diff = secondAvg - firstAvg

        return when {
            diff > 0.05 -> SimilarityTrend.IMPROVING
            diff < -0.05 -> SimilarityTrend.DECLINING
            else -> SimilarityTrend.STABLE
        }
    }

    /**
     * Calculate activity trend
     */
    private fun calculateActivityTrend(history: List<WallpaperHistory>): ActivityTrend {
        val fourteenDaysAgo = System.currentTimeMillis() - (14 * 24 * 60 * 60 * 1000L)
        val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)

        val recentWeek = history.count { it.appliedAt >= sevenDaysAgo }
        val previousWeek = history.count { it.appliedAt in fourteenDaysAgo until sevenDaysAgo }

        if (previousWeek == 0) return ActivityTrend.STABLE

        val ratio = recentWeek.toFloat() / previousWeek.toFloat()
        return when {
            ratio > 1.2f -> ActivityTrend.INCREASING
            ratio < 0.8f -> ActivityTrend.DECREASING
            else -> ActivityTrend.STABLE
        }
    }

    /**
     * Calculate preference drift from original embedding
     */
    private fun calculatePreferenceDrift(preferences: me.avinas.vanderwaals.data.entity.UserPreferences): Float {
        if (preferences.originalEmbedding.isEmpty() || preferences.preferenceVector.isEmpty()) {
            return 0f
        }

        val similarity = cosineSimilarity(preferences.originalEmbedding, preferences.preferenceVector)
        return (1f - similarity) * 100f // Convert to 0-100 scale
    }

    /**
     * Calculate adaptive learning rate
     */
    private fun calculateLearningRate(feedbackCount: Int): Float {
        val baseRate = 0.3f
        val decayFactor = 0.995f
        return baseRate * Math.pow(decayFactor.toDouble(), feedbackCount.toDouble()).toFloat()
    }

    /**
     * Generate smart insights based on analytics
     */
    private fun generateInsights(
        isPersonalized: Boolean,  // Keep for compatibility but unused
        quality: PersonalizationQuality,
        feedbackCount: Int,
        feedbackRatio: Float,
        trend: SimilarityTrend,
        activityTrend: ActivityTrend,
        drift: Float,
        categories: List<CategoryInsight>
    ): List<SmartInsight> {
        val insights = mutableListOf<SmartInsight>()

        // No "personalization is off" check anymore!
        // Both Auto and Personalize modes learn from feedback
        // The quality check below handles all states correctly

        // Quality-based insights
        when (quality) {
            PersonalizationQuality.NOT_INITIALIZED -> {
                insights.add(SmartInsight(
                    type = InsightType.TIP,
                    title = "Ready to learn your style!",
                    description = "Like or dislike wallpapers to teach Vanderwaals your preferences. After your first like, the app will start showing similar wallpapers.",
                    actionable = false
                ))
            }
            PersonalizationQuality.LEARNING -> {
                insights.add(SmartInsight(
                    type = InsightType.LEARNING,
                    title = "Learning your style...",
                    description = "Great start! The algorithm is beginning to understand your preferences. Keep providing feedback for better recommendations.",
                    actionable = false
                ))
            }
            PersonalizationQuality.DEVELOPING -> {
                insights.add(SmartInsight(
                    type = InsightType.SUCCESS,
                    title = "Personalization is developing!",
                    description = "Your feedback is helping! Wallpapers are being ranked based on your $feedbackCount interactions. The recommendations will keep improving.",
                    actionable = false
                ))
            }
            PersonalizationQuality.ESTABLISHED -> {
                insights.add(SmartInsight(
                    type = InsightType.SUCCESS,
                    title = "Personalization is working great!",
                    description = "With $feedbackCount feedback items, the algorithm has a solid understanding of your taste. Recommendations are tuned to your preferences.",
                    actionable = false
                ))
            }
            PersonalizationQuality.REFINED -> {
                insights.add(SmartInsight(
                    type = InsightType.SUCCESS,
                    title = "Excellent personalization!",
                    description = "Your $feedbackCount feedback items have created a refined preference profile. The algorithm is delivering highly personalized recommendations.",
                    actionable = false
                ))
            }
            PersonalizationQuality.EXCELLENT -> {
                insights.add(SmartInsight(
                    type = InsightType.SUCCESS,
                    title = "Masterful personalization!",
                    description = "With $feedbackCount feedback items, you've built an exceptional preference profile. The algorithm knows exactly what you love!",
                    actionable = false
                ))
            }
        }

        // Trend-based insights
        when (trend) {
            SimilarityTrend.IMPROVING -> {
                insights.add(SmartInsight(
                    type = InsightType.DISCOVERY,
                    title = "Recommendations improving!",
                    description = "Recent wallpapers are matching your preferences better. Your feedback is making a real difference!",
                    actionable = false
                ))
            }
            SimilarityTrend.DECLINING -> {
                insights.add(SmartInsight(
                    type = InsightType.NEED_FEEDBACK,
                    title = "Need more feedback",
                    description = "Recent recommendations seem less aligned with your taste. Like or dislike wallpapers to realign the algorithm.",
                    actionable = false
                ))
            }
            SimilarityTrend.STABLE -> {
                if (feedbackCount > 10) {
                    insights.add(SmartInsight(
                        type = InsightType.SUCCESS,
                        title = "Consistent recommendations",
                        description = "The algorithm is consistently matching your preferences. Your feedback has created stable, reliable recommendations.",
                        actionable = false
                    ))
                }
            }
        }

        // Feedback ratio insights
        if (feedbackCount > 5) {
            when {
                feedbackRatio > 0.8f -> {
                    insights.add(SmartInsight(
                        type = InsightType.TIP,
                        title = "You love most wallpapers!",
                        description = "You're liking ${(feedbackRatio * 100).toInt()}% of wallpapers. The algorithm can learn better from both likes and dislikes.",
                        actionable = false
                    ))
                }
                feedbackRatio < 0.3f -> {
                    insights.add(SmartInsight(
                        type = InsightType.TIP,
                        title = "Being selective?",
                        description = "You're only liking ${(feedbackRatio * 100).toInt()}% of wallpapers. More likes help the algorithm understand what you enjoy.",
                        actionable = false
                    ))
                }
                else -> {
                    insights.add(SmartInsight(
                        type = InsightType.SUCCESS,
                        title = "Balanced feedback",
                        description = "Your ${(feedbackRatio * 100).toInt()}% like rate shows you're thoughtfully curating. This helps the algorithm learn faster!",
                        actionable = false
                    ))
                }
            }
        }

        // Category insights
        val strongPreferences = categories.filter { abs(it.preferenceStrength) > 0.6f }
        if (strongPreferences.isNotEmpty()) {
            val favorite = strongPreferences.first()
            insights.add(SmartInsight(
                type = InsightType.DISCOVERY,
                title = "Category preference detected",
                description = "You ${if (favorite.preferenceStrength > 0) "love" else "dislike"} ${favorite.displayName} wallpapers! The algorithm is boosting similar styles.",
                actionable = false
            ))
        }

        // Drift insight
        if (drift > 30f && feedbackCount > 20) {
            insights.add(SmartInsight(
                type = InsightType.DISCOVERY,
                title = "Your taste is evolving",
                description = "Your preferences have shifted ${drift.toInt()}% from your original style. The algorithm adapts while keeping your core aesthetic!",
                actionable = false
            ))
        } else if (drift < 10f && feedbackCount > 20) {
            insights.add(SmartInsight(
                type = InsightType.SUCCESS,
                title = "Consistent taste",
                description = "Your preferences remain close to your original style. The dual-anchor system is maintaining your aesthetic identity!",
                actionable = false
            ))
        }

        return insights
    }

    /**
     * Generate actionable recommendations
     */
    private fun generateRecommendations(
        quality: PersonalizationQuality,
        feedbackCount: Int,
        categories: List<CategoryInsight>,
        avgDuration: Long
    ): List<String> {
        val recommendations = mutableListOf<String>()

        when (quality) {
            PersonalizationQuality.NOT_INITIALIZED, PersonalizationQuality.LEARNING -> {
                recommendations.add("Provide more feedback: Like and dislike wallpapers to help the algorithm learn faster")
                recommendations.add("Explore categories: Browse different styles to discover what you enjoy")
            }
            PersonalizationQuality.DEVELOPING -> {
                recommendations.add("Keep the momentum: A few more interactions will significantly improve recommendations")
                if (categories.isEmpty()) {
                    recommendations.add("Try different categories: Diverse feedback helps the algorithm understand your range")
                }
            }
            else -> {
                if (avgDuration < 3600) { // Less than 1 hour average
                    recommendations.add("Changing wallpapers frequently? Consider increasing the auto-change interval")
                }
                if (categories.size < 3) {
                    recommendations.add("Explore new categories: Broaden your aesthetic range for more variety")
                }
            }
        }

        if (feedbackCount > 50) {
            recommendations.add("Expert mode: Your refined preferences enable maximum personalization quality")
        }

        return recommendations
    }

    /**
     * Refresh analytics data
     */
    fun refresh() {
        loadAnalytics()
    }
}
