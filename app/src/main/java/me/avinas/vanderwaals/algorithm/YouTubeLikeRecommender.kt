package me.avinas.vanderwaals.algorithm

import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * YouTube-like recommendation algorithm for wallpaper selection.
 * 
 * Inspired by YouTube's recommendation system, this algorithm balances:
 * 
 * 1. **Exploitation** - Show wallpapers similar to what user likes
 * 2. **Exploration** - Occasionally show new/different content to discover preferences
 * 3. **Serendipity** - Inject unexpected but potentially interesting content
 * 4. **Freshness** - Boost newer content to prevent staleness
 * 5. **Diversity** - Prevent filter bubbles by varying categories/styles
 * 6. **Diminishing Returns** - Reduce score for over-exposed categories
 * 
 * **Key Differences from Simple Similarity:**
 * - Not just "what's most similar" but "what will user ENGAGE with"
 * - Predicts positive reaction, not just match
 * - Keeps recommendations fresh and surprising
 * - Learns from implicit signals (viewing duration, skip rate)
 * 
 * @see SelectNextWallpaperUseCase
 */
class YouTubeLikeRecommender {
    
    companion object {
        /**
         * Base exploration rate (probability of exploring vs exploiting)
         * YouTube typically uses 5-20% exploration
         */
        private const val BASE_EXPLORATION_RATE = 0.15f
        
        /**
         * Serendipity rate - probability of showing completely unexpected content
         * This is what makes YouTube feel "magical" - occasional surprises
         */
        private const val SERENDIPITY_RATE = 0.05f
        
        /**
         * Maximum diminishing returns penalty for overexposed categories
         * After seeing category N times, score is reduced by this factor
         */
        private const val MAX_SATURATION_PENALTY = 0.4f
        
        /**
         * Number of recent views before category saturation kicks in
         */
        private const val SATURATION_WINDOW = 5
        
        /**
         * Freshness boost for newly added wallpapers (within last week)
         */
        private const val FRESHNESS_BOOST = 0.1f
        
        /**
         * Days to consider a wallpaper "fresh"
         */
        private const val FRESHNESS_WINDOW_DAYS = 7
        
        /**
         * Diversity pool size - take top N candidates then pick randomly
         * This ensures variety even when exploiting
         */
        private const val DIVERSITY_POOL_SIZE = 15
        
        /**
         * Weight for engagement prediction vs raw similarity
         */
        private const val ENGAGEMENT_WEIGHT = 0.3f
        
        /**
         * Weight for novelty score
         */
        private const val NOVELTY_WEIGHT = 0.15f
    }
    
    /**
     * Data class representing a scored wallpaper candidate
     */
    data class ScoredCandidate(
        val wallpaper: WallpaperMetadata,
        val baseSimilarity: Float,
        val engagementScore: Float,
        val noveltyScore: Float,
        val diversityScore: Float,
        val freshnessBoost: Float,
        val saturationPenalty: Float,
        val finalScore: Float
    )
    
    /**
     * Session context for YouTube-like recommendations
     */
    data class SessionContext(
        val recentlyViewedIds: Set<String>,
        val recentCategories: List<String>,
        val sessionLikes: Int,
        val sessionDislikes: Int,
        val totalHistoryLikes: Int,
        val totalHistoryDislikes: Int,
        val likedCategories: Map<String, Int>,
        val dislikedCategories: Map<String, Int>,
        val currentTimeMillis: Long = System.currentTimeMillis()
    )
    
    /**
     * Select next wallpaper using YouTube-like algorithm.
     * 
     * **Algorithm Flow:**
     * 1. Roll dice for serendipity (5% chance of random pick)
     * 2. Roll dice for exploration (15% chance of exploring new category)
     * 3. Calculate engagement scores for all candidates
     * 4. Apply saturation penalties for overexposed categories
     * 5. Apply freshness boosts for new content
     * 6. Take top N candidates and pick randomly (diversity)
     * 
     * @param candidates List of (wallpaper, similarity) pairs
     * @param context Session context with viewing history
     * @param random Random instance for reproducibility
     * @return Selected wallpaper
     */
    fun selectWallpaper(
        candidates: List<Pair<WallpaperMetadata, Float>>,
        context: SessionContext,
        random: Random = Random
    ): WallpaperMetadata {
        if (candidates.isEmpty()) {
            throw IllegalArgumentException("Cannot select from empty candidate list")
        }
        
        // STEP 1: Serendipity roll - occasionally pick something completely random
        if (random.nextFloat() < SERENDIPITY_RATE) {
            android.util.Log.d("YouTubeLikeRecommender", "🎲 Serendipity mode! Picking random wallpaper")
            // Pick from bottom 50% of candidates (less similar = more surprising)
            val surprisePool = candidates.sortedBy { it.second }.take(candidates.size / 2)
            return if (surprisePool.isNotEmpty()) {
                surprisePool.random(random).first
            } else {
                candidates.random(random).first
            }
        }
        
        // STEP 2: Exploration roll - explore underrepresented categories
        val effectiveExplorationRate = calculateAdaptiveExplorationRate(context)
        if (random.nextFloat() < effectiveExplorationRate) {
            android.util.Log.d("YouTubeLikeRecommender", "🔍 Exploration mode! (rate=${String.format("%.1f%%", effectiveExplorationRate * 100)})")
            return selectForExploration(candidates, context, random)
        }
        
        // STEP 3: Calculate comprehensive scores for all candidates
        val scoredCandidates = candidates.map { (wallpaper, similarity) ->
            scoreCandidate(wallpaper, similarity, context)
        }.sortedByDescending { it.finalScore }
        
        // Log top candidates for debugging
        scoredCandidates.take(3).forEachIndexed { i, sc ->
            android.util.Log.d("YouTubeLikeRecommender", 
                "Top ${i+1}: ${sc.wallpaper.id.take(20)}... " +
                "final=${String.format("%.3f", sc.finalScore)} " +
                "(base=${String.format("%.2f", sc.baseSimilarity)}, " +
                "engage=${String.format("%.2f", sc.engagementScore)}, " +
                "novel=${String.format("%.2f", sc.noveltyScore)}, " +
                "sat=${String.format("%.2f", sc.saturationPenalty)})"
            )
        }
        
        // STEP 4: Take top candidates and pick randomly for diversity
        val diversityPool = scoredCandidates.take(DIVERSITY_POOL_SIZE)
        
        // Use weighted random selection based on scores
        return weightedRandomSelect(diversityPool, random)
    }
    
    /**
     * Calculate adaptive exploration rate based on session context.
     * 
     * - More exploration when user is unhappy (high dislike rate)
     * - Less exploration when user is satisfied (high like rate)
     * - More exploration early in session, less as session progresses
     */
    private fun calculateAdaptiveExplorationRate(context: SessionContext): Float {
        var rate = BASE_EXPLORATION_RATE
        
        // Adjust based on session satisfaction
        val sessionTotal = context.sessionLikes + context.sessionDislikes
        if (sessionTotal > 0) {
            val sessionSatisfaction = context.sessionLikes.toFloat() / sessionTotal
            // If satisfaction < 50%, increase exploration
            // If satisfaction > 50%, decrease exploration
            rate += (0.5f - sessionSatisfaction) * 0.2f
        }
        
        // Adjust based on historical satisfaction
        val historyTotal = context.totalHistoryLikes + context.totalHistoryDislikes
        if (historyTotal > 10) {
            val historySatisfaction = context.totalHistoryLikes.toFloat() / historyTotal
            if (historySatisfaction < 0.4f) {
                // User is frequently unhappy - explore more!
                rate += 0.15f
            }
        }
        
        return rate.coerceIn(0.05f, 0.4f)
    }
    
    /**
     * Select wallpaper for exploration - prioritize underrepresented categories.
     */
    private fun selectForExploration(
        candidates: List<Pair<WallpaperMetadata, Float>>,
        context: SessionContext,
        random: Random
    ): WallpaperMetadata {
        // Find categories not recently shown
        val recentCategorySet = context.recentCategories.toSet()
        
        // Filter to candidates from different categories
        val exploreCandidates = candidates.filter { (wp, _) ->
            wp.category !in recentCategorySet
        }
        
        return if (exploreCandidates.isNotEmpty()) {
            // Weight by inverse of category exposure
            val scored = exploreCandidates.map { (wp, similarity) ->
                val categoryExposure = context.recentCategories.count { it == wp.category }
                val explorationBonus = 1f / (1f + categoryExposure)
                Pair(wp, similarity + explorationBonus * 0.3f)
            }
            scored.maxByOrNull { it.second }?.first ?: candidates.random(random).first
        } else {
            // All categories explored recently, just pick randomly from top half
            candidates.take(candidates.size / 2).random(random).first
        }
    }
    
    /**
     * Calculate comprehensive score for a wallpaper candidate.
     */
    private fun scoreCandidate(
        wallpaper: WallpaperMetadata,
        baseSimilarity: Float,
        context: SessionContext
    ): ScoredCandidate {
        // 1. Engagement prediction - will user like this?
        val engagementScore = predictEngagement(wallpaper, context)
        
        // 2. Novelty score - how new/different is this?
        val noveltyScore = calculateNoveltyScore(wallpaper, context)
        
        // 3. Diversity score - how different from recent content?
        val diversityScore = calculateDiversityScore(wallpaper, context)
        
        // 4. Freshness boost - is this newly added content?
        val freshnessBoost = calculateFreshnessBoost(wallpaper, context.currentTimeMillis)
        
        // 5. Saturation penalty - has user seen this category too much?
        val saturationPenalty = calculateSaturationPenalty(wallpaper.category, context)
        
        // Combine scores with weights
        // Base similarity: 55% (still important to match preferences)
        // Engagement prediction: 20% (predict positive reaction)
        // Novelty: 10% (favor new/different)
        // Diversity: 10% (variety in categories)
        // Freshness: 5% (boost new content)
        // Saturation: penalty applied after
        val rawScore = (baseSimilarity * 0.55f) +
                       (engagementScore * 0.20f) +
                       (noveltyScore * 0.10f) +
                       (diversityScore * 0.10f) +
                       (freshnessBoost * 0.05f)
        
        // Apply saturation penalty (diminishing returns)
        val finalScore = rawScore * (1f - saturationPenalty)
        
        return ScoredCandidate(
            wallpaper = wallpaper,
            baseSimilarity = baseSimilarity,
            engagementScore = engagementScore,
            noveltyScore = noveltyScore,
            diversityScore = diversityScore,
            freshnessBoost = freshnessBoost,
            saturationPenalty = saturationPenalty,
            finalScore = finalScore
        )
    }
    
    /**
     * Predict engagement probability - will user like this wallpaper?
     * 
     * Uses category preference history as a proxy for engagement prediction.
     * YouTube uses complex ML models; we use simpler heuristics.
     */
    private fun predictEngagement(wallpaper: WallpaperMetadata, context: SessionContext): Float {
        val category = wallpaper.category
        
        val categoryLikes = context.likedCategories[category] ?: 0
        val categoryDislikes = context.dislikedCategories[category] ?: 0
        val total = categoryLikes + categoryDislikes
        
        return if (total > 0) {
            // Category has feedback history
            val likeRatio = categoryLikes.toFloat() / total
            // Use Bayesian average with prior of 0.5
            val priorWeight = 3
            (categoryLikes + priorWeight * 0.5f) / (total + priorWeight)
        } else {
            // Unknown category - neutral with slight exploration bonus
            0.55f
        }
    }
    
    /**
     * Calculate novelty score - how new/different is this content?
     */
    private fun calculateNoveltyScore(wallpaper: WallpaperMetadata, context: SessionContext): Float {
        var novelty = 0f
        
        // Category novelty - never/rarely seen category gets boost
        val categoryCount = context.recentCategories.count { it == wallpaper.category }
        if (categoryCount == 0) {
            novelty += 0.5f // Completely new category
        } else {
            novelty += 0.3f / categoryCount // Diminishing novelty
        }
        
        // ID novelty - never seen this specific wallpaper
        if (wallpaper.id !in context.recentlyViewedIds) {
            novelty += 0.5f
        }
        
        return novelty.coerceIn(0f, 1f)
    }
    
    /**
     * Calculate diversity score - how different from recent selections?
     */
    private fun calculateDiversityScore(wallpaper: WallpaperMetadata, context: SessionContext): Float {
        if (context.recentCategories.isEmpty()) return 1f
        
        // Category diversity
        val recentCategorySet = context.recentCategories.takeLast(5).toSet()
        val categoryDiversity = if (wallpaper.category !in recentCategorySet) 1f else 0.3f
        
        // Source diversity (if available)
        val sourceDiversity = 0.5f // Neutral for now
        
        return (categoryDiversity + sourceDiversity) / 2f
    }
    
    /**
     * Calculate freshness boost for newly added wallpapers.
     */
    private fun calculateFreshnessBoost(wallpaper: WallpaperMetadata, currentTimeMillis: Long): Float {
        // If wallpaper has timestamp metadata, use it
        // Otherwise return neutral
        // For now, we don't have addedAt timestamp, so return slight boost for variety
        return 0.05f
    }
    
    /**
     * Calculate saturation penalty for overexposed categories.
     * 
     * Like YouTube's "seen similar" signal - if user has seen many wallpapers
     * from a category recently, reduce score to prevent filter bubble.
     */
    private fun calculateSaturationPenalty(category: String, context: SessionContext): Float {
        // Count recent exposures to this category
        val recentExposures = context.recentCategories.takeLast(SATURATION_WINDOW)
            .count { it == category }
        
        if (recentExposures == 0) return 0f
        
        // Logarithmic penalty - first exposure is free, then diminishing returns
        // 1 exposure: 5% penalty
        // 2 exposures: 15% penalty
        // 3+ exposures: 30%+ penalty
        val penalty = (1 - exp(-recentExposures * 0.5)).toFloat() * MAX_SATURATION_PENALTY
        
        return penalty.coerceIn(0f, MAX_SATURATION_PENALTY)
    }
    
    /**
     * Weighted random selection from scored candidates.
     * 
     * Higher scored candidates have higher probability of selection,
     * but lower scored ones still have a chance (more YouTube-like).
     */
    private fun weightedRandomSelect(
        candidates: List<ScoredCandidate>,
        random: Random
    ): WallpaperMetadata {
        if (candidates.isEmpty()) throw IllegalArgumentException("Empty candidate list")
        if (candidates.size == 1) return candidates.first().wallpaper
        
        // Convert scores to probabilities using softmax-like distribution
        val minScore = candidates.minOf { it.finalScore }
        val scores = candidates.map { (it.finalScore - minScore).coerceAtLeast(0.01f) }
        
        // Use score^2 for sharper distribution (favor higher scores more)
        val weights = scores.map { it.pow(2) }
        val totalWeight = weights.sum()
        
        // Weighted random selection
        var pick = random.nextFloat() * totalWeight
        for (i in candidates.indices) {
            pick -= weights[i]
            if (pick <= 0) {
                return candidates[i].wallpaper
            }
        }
        
        // Fallback to best candidate
        return candidates.first().wallpaper
    }
}
