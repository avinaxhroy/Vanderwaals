package me.avinas.vanderwaals.algorithm

import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Recommendation algorithm that balances exploitation (show what user likes),
 * exploration (discover new preferences), serendipity, freshness, diversity,
 * and diminishing returns per category.
 */
class YouTubeLikeRecommender {
    
    companion object {
        /**
         * Base exploration rate (probability of exploring vs exploiting).
         * Kept low so exploitation (similarity) dominates; the adaptive rate
         * adds more exploration only when the user is actively dissatisfied.
         */
        private const val BASE_EXPLORATION_RATE = 0.08f
        
        /**
         * Serendipity rate - probability of showing something unexpected.
         * Values above 3% make the feed feel random to users.
         */
        private const val SERENDIPITY_RATE = 0.02f
        
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
         * Temperature for softmax-weighted random selection from the diversity pool.
         * Lower value → more deterministic (top score dominates); higher → more uniform.
         * Range: typically 0.2 (greedy) to 1.5 (near-random).
         */
        private const val SOFTMAX_TEMPERATURE = 0.4f

        /**
         * Lambda (relevance trade-off) for Maximal Marginal Relevance.
         * Higher → lean toward relevance; lower → lean toward diversity.
         */
        private const val MMR_LAMBDA = 0.7f

        /**
         * Size of the candidate pool fed into MMR selection.
         * Must be >= DIVERSITY_POOL_SIZE.
         */
        private const val MMR_CANDIDATE_POOL = 15

        /**
         * Diversity pool size - take top N candidates then pick randomly.
         * Smaller pool = stronger exploitation. 5 is a good balance: enough
         * variety to avoid repetition without feeling random.
         */
        private const val DIVERSITY_POOL_SIZE = 5
        
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
        val timeOfDayBoost: Float,
        val dislikedPenalty: Float,
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
        /**
         * Average display duration in minutes per category, derived from wallpaper history.
         * A long average duration implies implicit long-term preference even without explicit
         * likes. Used to boost engagement prediction in [predictEngagement].
         */
        val categorySetDurations: Map<String, Long> = emptyMap(),
        /**
         * L2-normalised centroid of disliked wallpaper embeddings, or null if no dislikes.
         * Used to penalise candidates semantically close to disliked content, double-down on
         * the negative EMA signal already encoded in the preference vector.
         */
        val dislikedEmbeddingCentroid: FloatArray? = null,
        /**
         * Current hour (0–23) for time-of-day personalisation.
         * Defaults to system clock; can be overridden in tests.
         */
        val currentHour: Int = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY),
        val currentTimeMillis: Long = System.currentTimeMillis()
    ) {
        // FloatArray uses reference equality by default; override so array
        // content is compared (and hashed) correctly.
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SessionContext) return false
            return recentlyViewedIds == other.recentlyViewedIds &&
                recentCategories == other.recentCategories &&
                sessionLikes == other.sessionLikes &&
                sessionDislikes == other.sessionDislikes &&
                totalHistoryLikes == other.totalHistoryLikes &&
                totalHistoryDislikes == other.totalHistoryDislikes &&
                likedCategories == other.likedCategories &&
                dislikedCategories == other.dislikedCategories &&
                categorySetDurations == other.categorySetDurations &&
                dislikedEmbeddingCentroid.contentEquals(other.dislikedEmbeddingCentroid) &&
                currentHour == other.currentHour &&
                currentTimeMillis == other.currentTimeMillis
        }

        override fun hashCode(): Int {
            var result = recentlyViewedIds.hashCode()
            result = 31 * result + recentCategories.hashCode()
            result = 31 * result + sessionLikes
            result = 31 * result + sessionDislikes
            result = 31 * result + totalHistoryLikes
            result = 31 * result + totalHistoryDislikes
            result = 31 * result + likedCategories.hashCode()
            result = 31 * result + dislikedCategories.hashCode()
            result = 31 * result + categorySetDurations.hashCode()
            result = 31 * result + (dislikedEmbeddingCentroid?.contentHashCode() ?: 0)
            result = 31 * result + currentHour
            result = 31 * result + currentTimeMillis.hashCode()
            return result
        }
    }
    
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
            if (me.avinas.vanderwaals.BuildConfig.DEBUG) android.util.Log.d("YouTubeLikeRecommender", "🎲 Serendipity mode! Picking random wallpaper")
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
            if (me.avinas.vanderwaals.BuildConfig.DEBUG) android.util.Log.d("YouTubeLikeRecommender", "🔍 Exploration mode! (rate=${String.format("%.1f%%", effectiveExplorationRate * 100)})")
            return selectForExploration(candidates, context, random)
        }
        
        // STEP 3: Calculate comprehensive scores for all candidates
        val scoredCandidates = candidates.map { (wallpaper, similarity) ->
            scoreCandidate(wallpaper, similarity, context)
        }.sortedByDescending { it.finalScore }
        
        // Log top candidates for debugging (gated to avoid per-selection string
        // formatting in release builds)
        if (me.avinas.vanderwaals.BuildConfig.DEBUG) {
            scoredCandidates.take(3).forEachIndexed { i, sc ->
                android.util.Log.d("YouTubeLikeRecommender",
                    "Top ${i+1}: ${sc.wallpaper.id.take(20)}... " +
                    "final=${String.format("%.3f", sc.finalScore)} " +
                    "(base=${String.format("%.2f", sc.baseSimilarity)}, " +
                    "engage=${String.format("%.2f", sc.engagementScore)}, " +
                    "tod=${String.format("%.2f", sc.timeOfDayBoost)}, " +
                    "dis=-${String.format("%.2f", sc.dislikedPenalty)}, " +
                    "novel=${String.format("%.2f", sc.noveltyScore)}, " +
                    "sat=${String.format("%.2f", sc.saturationPenalty)})"
                )
            }
        }
        
        // STEP 4: Apply Maximal Marginal Relevance (MMR) for embedding-aware diversity,
        //         then stochastically pick from the resulting pool via softmax weights.
        val mmrPool = selectWithMmr(scoredCandidates, DIVERSITY_POOL_SIZE)

        // Temperature-scaled softmax selection
        return weightedRandomSelect(mmrPool, random)
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

        // Scale down exploration as the preference model matures.
        // With 50+ feedback events the model is well-calibrated; exploration
        // should yield to exploitation.
        val historyTotal = context.totalHistoryLikes + context.totalHistoryDislikes
        val maturityScale = when {
            historyTotal >= 50 -> 0.4f   // 60% reduction — trust the model
            historyTotal >= 20 -> 0.7f   // 30% reduction — mostly trust it
            else               -> 1.0f   // No reduction — still learning
        }
        rate *= maturityScale

        // Adjust based on consecutive session dislikes (user is unhappy right now)
        val sessionTotal = context.sessionLikes + context.sessionDislikes
        if (sessionTotal > 0) {
            val sessionSatisfaction = context.sessionLikes.toFloat() / sessionTotal
            // Only increase exploration if satisfaction is genuinely low
            if (sessionSatisfaction < 0.3f) {
                rate += 0.10f
            }
        }

        // Historically unhappy user — explore more broadly
        if (historyTotal > 10) {
            val historySatisfaction = context.totalHistoryLikes.toFloat() / historyTotal
            if (historySatisfaction < 0.4f) {
                rate += 0.10f
            }
        }
        
        return rate.coerceIn(0.02f, 0.25f)
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
            // Stochastic selection via temperature-softmax (not deterministic max)
            selectByTemperatureSoftmax(scored, SOFTMAX_TEMPERATURE, random)
        } else {
            // All categories explored recently, just pick randomly from top half
            candidates.take(candidates.size / 2).random(random).first
        }
    }
    
    /**
     * Calculate comprehensive score for a wallpaper candidate.
     *
     * Score weights (sum = 1.0):
     * - Base similarity:    55% — embedding match to user preferences
     * - Engagement:         18% — category feedback + implicit set-duration signal
     * - Novelty:             7% — how new/different this content is
     * - Diversity:           7% — how varied vs recent selections
     * - Freshness:           7% — newly added content
     * - Time-of-day:         6% — brightness preference matched to wall-clock hour
     *
     * Post-weighting adjustments:
     * - Disliked centroid penalty subtracted (up to −0.25 × dislike similarity)
     * - Saturation penalty applied multiplicatively
     */
    private fun scoreCandidate(
        wallpaper: WallpaperMetadata,
        baseSimilarity: Float,
        context: SessionContext
    ): ScoredCandidate {
        // 1. Engagement prediction - will user like this? (category-based)
        val engagementScore = predictEngagement(wallpaper, context)

        // 2. Novelty score - how new/different is this?
        val noveltyScore = calculateNoveltyScore(wallpaper, context)

        // 3. Diversity score - how different from recent content?
        val diversityScore = calculateDiversityScore(wallpaper, context)
        
        // 4. Freshness boost - is this newly added content?
        val freshnessBoost = calculateFreshnessBoost(wallpaper, context.currentTimeMillis)
        
        // 5. Saturation penalty - has user seen this category too much?
        val saturationPenalty = calculateSaturationPenalty(wallpaper.category, context)

        // 6. Time-of-day boost — brightness matched to the current hour
        val timeOfDayBoost = calculateTimeOfDayBoost(wallpaper, context.currentHour)

        // 7. Disliked centroid penalty — penalise candidates close to disliked content
        val dislikedPenalty = calculateDislikedPenalty(wallpaper, context.dislikedEmbeddingCentroid)

        val rawScore = (baseSimilarity  * 0.55f) +
                       (engagementScore * 0.18f) +
                       (noveltyScore    * 0.07f) +
                       (diversityScore  * 0.07f) +
                       (freshnessBoost  * 0.07f) +
                       (timeOfDayBoost  * 0.06f)

        // Apply dislike penalty then saturation (both reduce the final score)
        val finalScore = (rawScore - dislikedPenalty).coerceAtLeast(0f) * (1f - saturationPenalty)

        return ScoredCandidate(
            wallpaper = wallpaper,
            baseSimilarity = baseSimilarity,
            engagementScore = engagementScore,
            noveltyScore = noveltyScore,
            diversityScore = diversityScore,
            freshnessBoost = freshnessBoost,
            saturationPenalty = saturationPenalty,
            timeOfDayBoost = timeOfDayBoost,
            dislikedPenalty = dislikedPenalty,
            finalScore = finalScore
        )
    }
    
    /**
     * Predict engagement probability - will user like this wallpaper?
     *
     * Uses category preference history as a proxy for engagement prediction.
     * Returns a Bayesian-smoothed like probability (0–1).
     */
    private fun predictEngagement(wallpaper: WallpaperMetadata, context: SessionContext): Float {
        val category = wallpaper.category
        val categoryLikes = context.likedCategories[category] ?: 0
        val categoryDislikes = context.dislikedCategories[category] ?: 0
        val total = categoryLikes + categoryDislikes
        val priorWeight = 3
        val explicitScore = if (total > 0) {
            (categoryLikes + priorWeight * 0.5f) / (total + priorWeight)
        } else {
            0.55f // Unknown category — neutral with slight exploration bonus
        }

        // Implicit engagement: boost categories where wallpapers were kept for a long time.
        // A long display duration is a strong implicit signal of genuine preference even
        // without an explicit like tap.  Derived from WallpaperHistory.getDurationSeconds().
        val avgDurationMinutes = context.categorySetDurations[category] ?: 0L
        val implicitBonus = when {
            avgDurationMinutes >= 720 -> 0.08f  // 12+ hours — strong preference
            avgDurationMinutes >= 240 -> 0.05f  // 4+ hours  — clear preference
            avgDurationMinutes >= 60  -> 0.03f  // 1+ hour   — mild preference
            avgDurationMinutes >= 15  -> 0.01f  // 15+ min   — slight preference
            else                      -> 0f
        }
        return (explicitScore + implicitBonus).coerceIn(0f, 1f)
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
     *
     * Currently based on category diversity only.  Source diversity would
     * require a `recentSources` list in [SessionContext]; until that is
     * added, category diversity is the sole signal.
     */
    private fun calculateDiversityScore(wallpaper: WallpaperMetadata, context: SessionContext): Float {
        if (context.recentCategories.isEmpty()) return 1f

        // Category diversity
        val recentCategorySet = context.recentCategories.takeLast(5).toSet()
        return if (wallpaper.category !in recentCategorySet) 1f else 0.3f
    }
    
    /**
     * Calculate freshness boost for newly added wallpapers.
     *
     * WallpaperMetadata does not currently carry an `addedAt` timestamp, so
     * there is no signal to distinguish new content from old.  Returns 0
     * (neutral) so the freshness weight in [scoreCandidate] is a no-op until
     * a timestamp field is added to the entity and manifest.
     */
    private fun calculateFreshnessBoost(wallpaper: WallpaperMetadata, currentTimeMillis: Long): Float {
        return 0f
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
        // 1 exposure: ~16% penalty
        // 2 exposures: ~25% penalty
        // 3+ exposures: ~31%+ penalty
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
        
        // Convert scores to probabilities using temperature-scaled softmax.
        // Subtract max score before exp() for numerical stability (avoids Float overflow).
        val minScore = candidates.minOf { it.finalScore }
        val scores = candidates.map { (it.finalScore - minScore).coerceAtLeast(0.01f) }
        val maxScore = scores.max()
        val weights = scores.map { exp((it - maxScore) / SOFTMAX_TEMPERATURE) }
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

    /**
     * Temperature-softmax weighted random selection from a list of (wallpaper, score) pairs.
     * Stochastic — higher-scoring candidates are more likely but not guaranteed.
     */
    private fun selectByTemperatureSoftmax(
        candidates: List<Pair<WallpaperMetadata, Float>>,
        temperature: Float,
        random: Random
    ): WallpaperMetadata {
        if (candidates.isEmpty()) throw IllegalArgumentException("Empty candidate list")
        if (candidates.size == 1) return candidates.first().first

        val scores = candidates.map { it.second }
        val maxScore = scores.max()
        val weights = scores.map { exp((it - maxScore) / temperature) }
        val totalWeight = weights.sum()

        var pick = random.nextFloat() * totalWeight
        for (i in candidates.indices) {
            pick -= weights[i]
            if (pick <= 0) {
                return candidates[i].first
            }
        }
        return candidates.first().first
    }

    // ========== Private helpers added for advanced improvements ==========

    /**
     * Returns a brightness-aware boost based on the current wall-clock hour.
     *
     * Night (22:00\u201305:59): dark wallpapers preferred.
     * Morning (06:00\u201309:59): bright wallpapers preferred.
     * Evening (18:00\u201321:59): moderate brightness preferred.
     * Daytime (10:00\u201317:59): neutral.
     *
     * Signal is normalised to [0, 1] for use as a score component.
     */
    private fun calculateTimeOfDayBoost(wallpaper: WallpaperMetadata, hour: Int): Float {
        val brightness = wallpaper.brightness  // 0\u2013100
        return when (hour) {
            in 22..23, in 0..5 -> when {
                brightness < 30  -> 1.0f
                brightness < 45  -> 0.6f
                brightness > 65  -> 0.0f
                else             -> 0.3f
            }
            in 6..9 -> when {
                brightness > 65  -> 1.0f
                brightness > 50  -> 0.7f
                brightness < 30  -> 0.1f
                else             -> 0.4f
            }
            in 10..17 -> 0.5f  // Daytime: neutral mid-point
            in 18..21 -> when {
                brightness in 35..65 -> 0.8f
                brightness > 75      -> 0.3f
                brightness < 25      -> 0.2f
                else                 -> 0.5f
            }
            else -> 0.5f
        }
    }

    /**
     * Penalises a candidate whose embedding is positively similar to the centroid of
     * disliked wallpaper embeddings.
     *
     * Only wallpapers with a positive cosine similarity to the disliked centroid are
     * penalised; orthogonal or anti-correlated wallpapers receive no penalty.
     *
     * Max penalty = 0.25 (applied when cosine similarity = 1.0).
     */
    private fun calculateDislikedPenalty(
        wallpaper: WallpaperMetadata,
        dislikedCentroid: FloatArray?
    ): Float {
        if (dislikedCentroid == null || wallpaper.embedding.isEmpty()) return 0f
        // Raw cosine in [-1, 1]; clamp at 0 so anti-similar items get no penalty
        val sim = cosineSimilarityLocal(wallpaper.embedding, dislikedCentroid).coerceAtLeast(0f)
        return sim * 0.25f
    }

    /**
     * Cosine similarity between two float vectors, returned in [-1, 1].
     * Returns 0 for zero-magnitude vectors.
     */
    private fun cosineSimilarityLocal(v1: FloatArray, v2: FloatArray): Float {
        if (v1.size != v2.size) return 0f
        var dot = 0f; var m1 = 0f; var m2 = 0f
        for (i in v1.indices) {
            dot += v1[i] * v2[i]
            m1  += v1[i] * v1[i]
            m2  += v2[i] * v2[i]
        }
        if (m1 == 0f || m2 == 0f) return 0f
        return (dot / (sqrt(m1) * sqrt(m2))).coerceIn(-1f, 1f)
    }

    /**
     * Selects [k] candidates from the top of [candidates] using Maximal Marginal
     * Relevance (MMR), balancing relevance (final score) against embedding-space
     * diversity.
     *
     * MMR objective per iteration:
     * ```
     * argmax_d [ \u03bb \u00d7 score(d) \u2212 (1\u2212\u03bb) \u00d7 max_{d'\u2208S} sim(d, d') ]
     * ```
     * where S is the set of already-selected candidates.
     *
     * Wallpapers without embeddings fall back to category diversity as a proxy
     * for embedding similarity.
     *
     * @param candidates Scored candidates sorted by finalScore descending
     * @param k          Number of candidates to select
     * @return MMR-selected list of up to [k] candidates
     */
    private fun selectWithMmr(
        candidates: List<ScoredCandidate>,
        k: Int
    ): List<ScoredCandidate> {
        if (candidates.size <= k) return candidates

        // Consider only the top MMR_CANDIDATE_POOL entries as the source pool
        val pool = candidates.take(minOf(MMR_CANDIDATE_POOL, candidates.size)).toMutableList()
        val selected = mutableListOf<ScoredCandidate>()

        // Always include the highest-scoring candidate first
        selected.add(pool.removeAt(0))

        while (selected.size < k && pool.isNotEmpty()) {
            val best = pool.maxByOrNull { candidate ->
                val relevance = candidate.finalScore
                val maxSim: Float = if (candidate.wallpaper.embedding.isNotEmpty()) {
                    // Embedding-based similarity to already-selected items
                    selected
                        .filter { it.wallpaper.embedding.isNotEmpty() }
                        .maxOfOrNull { sel ->
                            cosineSimilarityLocal(
                                candidate.wallpaper.embedding,
                                sel.wallpaper.embedding
                            )
                        } ?: 0f
                } else {
                    // Fallback for candidates without client-side embeddings:
                    val sameCategory = selected.count {
                        it.wallpaper.category == candidate.wallpaper.category
                    }
                    if (selected.isEmpty()) 0f else sameCategory.toFloat() / selected.size
                }
                MMR_LAMBDA * relevance - (1f - MMR_LAMBDA) * maxSim
            } ?: break

            selected.add(best)
            pool.remove(best)
        }

        return selected
    }
}
