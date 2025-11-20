package me.avinas.vanderwaals.algorithm

import kotlin.math.*
import kotlin.random.Random
import javax.inject.Inject

/**
 * Smart exploration strategy for wallpaper recommendations.
 * 
 * Balances exploitation (show known good wallpapers) vs exploration (discover new preferences):
 * 
 * **Strategies Implemented:**
 * 1. **Epsilon-Greedy with Decay**
 *    - Early: High exploration (30%)
 *    - Later: Low exploration (5%)
 *    - Decays with feedback count
 * 
 * 2. **Thompson Sampling**
 *    - Bayesian approach to exploration
 *    - Maintains Beta distributions for each category
 *    - Samples from distributions to balance uncertainty
 * 
 * 3. **Upper Confidence Bound (UCB)**
 *    - Prefers less-explored options with potential
 *    - Bonus for uncertainty: sqrt(log(t) / n)
 *    - Guarantees eventual exploration of all options
 * 
 * **Adaptive Behavior:**
 * - New users: More exploration (discover preferences)
 * - Experienced users: More exploitation (refined taste)
 * - Stuck in local optimum: Temporary exploration boost
 * 
 * @see SelectNextWallpaperUseCase
 */
class ExplorationStrategy @Inject constructor(
    private val random: Random = Random.Default
) {
    
    companion object {
        // Epsilon decay parameters
        private const val INITIAL_EPSILON = 0.30f      // 30% exploration for new users
        private const val MIN_EPSILON = 0.05f          // 5% minimum exploration
        private const val DECAY_RATE = 0.95f           // Exponential decay rate
        private const val DECAY_STEPS = 50             // Decay over 50 feedback events
        
        // UCB parameters
        private const val UCB_EXPLORATION_FACTOR = 2.0f // Controls exploration intensity
        
        // Thompson Sampling parameters
        private const val PRIOR_ALPHA = 1.0            // Beta distribution prior
        private const val PRIOR_BETA = 1.0
        
        // Diversity boost
        private const val MIN_CATEGORY_SAMPLES = 3     // Minimum views before settling
        private const val DIVERSITY_BOOST = 0.10f      // 10% boost for underexplored
    }
    
    /**
     * Calculates current epsilon value based on feedback count.
     * 
     * Uses exponential decay: epsilon = max(MIN, INITIAL × decay^(count/steps))
     * 
     * @param feedbackCount Total feedback events so far
     * @return Current epsilon value (0.05 to 0.30)
     */
    fun calculateEpsilon(feedbackCount: Int): Float {
        val decayFactor = DECAY_RATE.pow(feedbackCount.toFloat() / DECAY_STEPS)
        return (INITIAL_EPSILON * decayFactor).coerceAtLeast(MIN_EPSILON)
    }
    
    /**
     * Selects wallpaper using epsilon-greedy strategy.
     * 
     * With probability epsilon: Select random wallpaper (exploration)
     * With probability 1-epsilon: Select best wallpaper (exploitation)
     * 
     * @param rankedWallpapers Wallpapers sorted by score (best first)
     * @param epsilon Current exploration rate
     * @return Selected wallpaper with exploration reason
     */
    fun <T> selectWithEpsilonGreedy(
        rankedWallpapers: List<T>,
        epsilon: Float
    ): ExplorationResult<T> {
        if (rankedWallpapers.isEmpty()) {
            throw IllegalArgumentException("Cannot select from empty list")
        }
        
        return if (random.nextFloat() < epsilon) {
            // Exploration: Random selection
            val index = random.nextInt(rankedWallpapers.size)
            ExplorationResult(
                selected = rankedWallpapers[index],
                reason = ExplorationReason.EPSILON_RANDOM,
                explorationWeight = epsilon
            )
        } else {
            // Exploitation: Best wallpaper
            ExplorationResult(
                selected = rankedWallpapers.first(),
                reason = ExplorationReason.BEST_MATCH,
                explorationWeight = 0f
            )
        }
    }
    
    /**
     * Calculates UCB (Upper Confidence Bound) score for a category.
     * 
     * UCB = averageScore + explorationBonus
     * explorationBonus = c × sqrt(ln(totalViews) / categoryViews)
     * 
     * Higher bonus for less-explored categories with potential.
     * 
     * @param categoryScore Average score for this category
     * @param categoryViews Number of times category was shown
     * @param totalViews Total wallpapers shown across all categories
     * @return UCB score (higher = prefer this category)
     */
    fun calculateUcbScore(
        categoryScore: Float,
        categoryViews: Int,
        totalViews: Int
    ): Float {
        if (categoryViews == 0) {
            // Infinite exploration bonus for never-seen categories
            return Float.MAX_VALUE
        }
        
        if (totalViews == 0) {
            return categoryScore
        }
        
        val explorationBonus = UCB_EXPLORATION_FACTOR * sqrt(ln(totalViews.toDouble()) / categoryViews).toFloat()
        return categoryScore + explorationBonus
    }
    
    /**
     * Samples from Thompson Sampling distribution for a category.
     * 
     * Maintains Beta(α, β) distribution for each category:
     * - α increments with likes
     * - β increments with dislikes
     * - Sample from Beta(α, β) to get exploration-aware score
     * 
     * Categories with high uncertainty get higher variance in samples,
     * allowing occasional exploration even if current mean is low.
     * 
     * @param likes Number of likes for category
     * @param dislikes Number of dislikes for category
     * @return Sampled score (0-1) representing exploration-aware preference
     */
    fun sampleThompson(likes: Int, dislikes: Int): Float {
        val alpha = PRIOR_ALPHA + likes
        val beta = PRIOR_BETA + dislikes
        
        // Sample from Beta(α, β) distribution
        // Using Gamma distribution properties: Beta(α,β) = Gamma(α,1) / (Gamma(α,1) + Gamma(β,1))
        val x = sampleGamma(alpha, 1.0)
        val y = sampleGamma(beta, 1.0)
        
        return (x / (x + y)).toFloat()
    }
    
    /**
     * Calculates diversity boost for underexplored categories.
     * 
     * Gives bonus to categories with fewer than MIN_CATEGORY_SAMPLES views
     * to ensure balanced exploration across all content types.
     * 
     * @param categoryViews Number of times category was shown
     * @return Diversity boost (0.0 to DIVERSITY_BOOST)
     */
    fun calculateDiversityBoost(categoryViews: Int): Float {
        return if (categoryViews < MIN_CATEGORY_SAMPLES) {
            DIVERSITY_BOOST * (1f - categoryViews.toFloat() / MIN_CATEGORY_SAMPLES)
        } else {
            0f
        }
    }
    
    /**
     * Detects if user is stuck in local optimum (filter bubble).
     * 
     * Indicators:
     * - Low variety in recent selections (same categories)
     * - High confidence but declining satisfaction
     * - Long time since trying new categories
     * 
     * @param recentCategories Categories from last N wallpapers
     * @param feedbackCount Total feedback events
     * @return True if should boost exploration temporarily
     */
    fun shouldBoostExploration(
        recentCategories: List<String>,
        feedbackCount: Int
    ): Boolean {
        if (recentCategories.size < 10 || feedbackCount < 20) {
            return false // Need more data
        }
        
        // Check category diversity in recent history
        val uniqueCategories = recentCategories.takeLast(10).toSet().size
        val isLowDiversity = uniqueCategories < 3
        
        // Check if stuck (high feedback but low recent diversity)
        val isStuck = feedbackCount > 50 && isLowDiversity
        
        return isStuck
    }
    
    /**
     * Selects exploration strategy based on user's experience level.
     * 
     * - New users (0-10 feedback): High exploration (Thompson Sampling)
     * - Learning users (10-50 feedback): Balanced (UCB)
     * - Experienced users (50+ feedback): Low exploration (Epsilon-Greedy with decay)
     * 
     * @param feedbackCount Total feedback events
     * @return Recommended exploration strategy
     */
    fun selectStrategy(feedbackCount: Int): Strategy {
        return when {
            feedbackCount < 10 -> Strategy.THOMPSON_SAMPLING
            feedbackCount < 50 -> Strategy.UCB
            else -> Strategy.EPSILON_GREEDY
        }
    }
    
    /**
     * Applies exploration strategy to wallpaper ranking.
     * 
     * Modifies scores based on exploration needs:
     * - Boosts underexplored categories
     * - Adds exploration noise to prevent getting stuck
     * - Ensures minimum diversity
     * 
     * @param wallpapers Wallpapers with initial scores
     * @param categoryViews View counts per category
     * @param totalViews Total views across all categories
     * @param feedbackCount Total feedback events
     * @return Wallpapers with exploration-adjusted scores
     */
    fun <T : ScoredItem> applyExplorationBonus(
        wallpapers: List<T>,
        categoryViews: Map<String, Int>,
        totalViews: Int,
        feedbackCount: Int
    ): List<T> {
        val strategy = selectStrategy(feedbackCount)
        
        return wallpapers.map { wallpaper ->
            val baseScore = wallpaper.score
            val category = wallpaper.category
            val views = categoryViews[category] ?: 0
            
            val explorationBonus = when (strategy) {
                Strategy.EPSILON_GREEDY -> {
                    // Epsilon-greedy adds diversity boost only
                    calculateDiversityBoost(views)
                }
                Strategy.UCB -> {
                    // UCB adds confidence-based bonus
                    val ucbScore = calculateUcbScore(baseScore, views, totalViews)
                    ucbScore - baseScore // Just the bonus part
                }
                Strategy.THOMPSON_SAMPLING -> {
                    // Thompson sampling: resample entire score
                    // (This would require likes/dislikes per wallpaper, simplified here)
                    calculateDiversityBoost(views)
                }
            }
            
            @Suppress("UNCHECKED_CAST")
            wallpaper.withAdjustedScore(baseScore + explorationBonus) as T
        }
    }
    
    // ========== Private Helper Methods ==========
    
    /**
     * Samples from Gamma distribution using Marsaglia and Tsang's method.
     * 
     * Used internally for Thompson Sampling (Beta distribution).
     */
    private fun sampleGamma(alpha: Double, beta: Double): Double {
        if (alpha < 1.0) {
            // Use Gamma(α+1) and scale
            return sampleGamma(alpha + 1.0, beta) * random.nextDouble().pow(1.0 / alpha)
        }
        
        // Marsaglia and Tsang's method for α >= 1
        val d = alpha - 1.0 / 3.0
        val c = 1.0 / sqrt(9.0 * d)
        
        while (true) {
            var x: Double
            var v: Double
            
            do {
                x = sampleStandardNormal()
                v = 1.0 + c * x
            } while (v <= 0.0)
            
            v = v * v * v
            val u = random.nextDouble()
            val x2 = x * x
            
            if (u < 1.0 - 0.0331 * x2 * x2) {
                return d * v / beta
            }
            
            if (ln(u) < 0.5 * x2 + d * (1.0 - v + ln(v))) {
                return d * v / beta
            }
        }
    }
    
    /**
     * Samples from standard normal distribution using Box-Muller transform.
     */
    private fun sampleStandardNormal(): Double {
        val u1 = random.nextDouble()
        val u2 = random.nextDouble()
        return sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
    }
    
    // ========== Data Classes and Enums ==========
    
    enum class Strategy {
        EPSILON_GREEDY,      // Simple random exploration
        UCB,                 // Upper Confidence Bound
        THOMPSON_SAMPLING    // Bayesian sampling
    }
    
    enum class ExplorationReason {
        BEST_MATCH,          // Exploitation: Highest score
        EPSILON_RANDOM,      // Exploration: Random selection
        UCB_BONUS,           // Exploration: UCB strategy
        THOMPSON_SAMPLE,     // Exploration: Thompson sampling
        DIVERSITY_BOOST      // Exploration: Underexplored category
    }
    
    data class ExplorationResult<T>(
        val selected: T,
        val reason: ExplorationReason,
        val explorationWeight: Float  // How much exploration influenced this choice
    )
    
    /**
     * Interface for items that can be scored and have categories.
     */
    interface ScoredItem {
        val score: Float
        val category: String
        fun withAdjustedScore(newScore: Float): ScoredItem
    }
}
