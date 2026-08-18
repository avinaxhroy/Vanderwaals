package me.avinas.vanderwaals.algorithm

import me.avinas.vanderwaals.core.ColorSpace
import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The single calibrated ranking path for wallpaper recommendation.
 *
 * Replaces the previous stack of `SimilarityCalculator` dual-anchor
 * scoring + `WallpaperScorer` additive boosts + `YouTubeLikeRecommender`
 * re-scoring, in which one like influenced the final score through five or
 * six independent, uncalibrated terms.  Here every signal enters exactly
 * once, every component is normalised to [0, 1] with 0.5 meaning "no
 * data", and the weights in [RecommenderConfig] sum to 1.0.
 *
 * Score shape:
 * ```
 * base   = Σ w_i · component_i                     (weights sum to 1)
 * score  = base × saturation × dislikeSuppression   (bounded, multiplicative)
 *        + explorationBonus                         (UCB-style, decays with evidence)
 * ```
 *
 * Selection shape: top [RecommenderConfig.MMR_CANDIDATE_POOL] by score →
 * MMR re-ranking for embedding-space diversity → softmax pick from the
 * final pool (temperature-scaled, so the best candidate usually wins but
 * the rotation never becomes deterministic).
 *
 * Pure Kotlin; no Android or database dependencies.
 */
class RankingEngine {

    /**
     * Aggregated per-category feedback used for the category component and
     * the exploration bonus.
     */
    data class CategoryStats(
        val likes: Int = 0,
        val dislikes: Int = 0,
        val views: Int = 0
    )

    /**
     * Everything the engine needs to know about the session.  Built by the
     * caller (use-case layer) from Room + DataStore; lists are ordered
     * newest-first.
     */
    class RankingContext(
        val nowMillis: Long,
        val currentHour: Int,
        val tasteMemory: TasteMemory,
        val categoryStats: Map<String, CategoryStats>,
        /** Wallpaper ids of recent selections, newest first. */
        val recentIds: List<String>,
        /** Categories of recent selections, newest first. */
        val recentCategories: List<String>,
        val moodAffinity: Map<String, Float> = emptyMap(),
        val styleAffinity: Map<String, Float> = emptyMap(),
        /** Hex palette of wallpapers the user liked, for colour matching. */
        val likedColors: List<String> = emptyList()
    )

    /** Candidate with every component score exposed for debugging/tests. */
    data class ScoredWallpaper(
        val wallpaper: WallpaperMetadata,
        val tasteScore: Float,
        val categoryScore: Float,
        val qualityScore: Float,
        val colorScore: Float,
        val semanticScore: Float,
        val timeOfDayScore: Float,
        val explorationBonus: Float,
        val saturationSuppression: Float,
        val dislikeSuppression: Float,
        val finalScore: Float
    )

    /**
     * Context-invariant values computed once per ranking pass.  The catalog
     * is scored as a whole (thousands of candidates per selection), so any
     * constant work — hex parsing, LAB conversion, feedback totals,
     * exposure counting, centroid extraction — must happen here rather than
     * inside [scoreCandidate].
     */
    private class PreparedContext(context: RankingContext) {
        val totalFeedback: Int =
            context.categoryStats.values.sumOf { it.likes + it.dislikes }

        /** Category → exposures within the saturation window. */
        val categoryExposure: Map<String, Int> = context.recentCategories
            .take(RecommenderConfig.SATURATION_WINDOW)
            .groupingBy { it }
            .eachCount()

        /** CIELab values of the liked palette, converted once. */
        val likedLab: List<Triple<Double, Double, Double>> = context.likedColors
            .take(12)
            .asSequence()
            .mapNotNull(::parseHexToRgb)
            .map { ColorSpace.rgbToLab(it[0], it[1], it[2]) }
            .toList()

        val dislikeCentroid: FloatArray? = context.tasteMemory.dislikeCentroid()
    }

    /**
     * Scores and orders candidates (best first).  Deterministic given the
     * same context.
     */
    fun rank(
        candidates: List<WallpaperMetadata>,
        context: RankingContext
    ): List<ScoredWallpaper> {
        val prepared = PreparedContext(context)
        return candidates
            .asSequence()
            .map { scoreCandidate(it, context, prepared) }
            .sortedByDescending { it.finalScore }
            .toList()
    }

    /**
     * Scores, diversifies (MMR) and stochastically picks one wallpaper.
     */
    fun select(
        candidates: List<WallpaperMetadata>,
        context: RankingContext,
        random: Random
    ): WallpaperMetadata {
        if (candidates.isEmpty()) {
            throw IllegalArgumentException("Cannot select from empty candidate list")
        }
        val ranked = rank(candidates, context)
        val mmrPool = selectWithMmr(ranked, RecommenderConfig.SELECTION_POOL_SIZE)
        return softmaxPick(mmrPool, random)
    }

    /**
     * Cold-start score used before any taste exists: objective quality with
     * a per-device jitter so different devices get different rotations.
     * Deliberately carries no source bias — the previous cold-start
     * hard-coded a Bing > Vanderwaals > GitHub preference.
     */
    fun coldStartScore(wallpaper: WallpaperMetadata, deviceSeed: Int): Float {
        val jitter = (Math.floorMod((deviceSeed + wallpaper.id.hashCode()).toLong(), 1000) / 1000f) * 0.05f
        return qualityScore(wallpaper) + jitter
    }

    // Component scoring

    private fun scoreCandidate(
        wallpaper: WallpaperMetadata,
        context: RankingContext,
        prepared: PreparedContext
    ): ScoredWallpaper {
        val cfg = RecommenderConfig

        val taste = context.tasteMemory.tasteSimilarity(wallpaper.embedding)
        val category = categoryAffinity(wallpaper.category, context)
        val quality = qualityScore(wallpaper)
        val color = colorAffinity(wallpaper.colors, prepared.likedLab)
        val semantic = semanticAffinity(wallpaper, context)
        val timeOfDay = timeOfDayFit(wallpaper.brightness, context.currentHour)

        val base =
            taste * cfg.WEIGHT_TASTE +
            category * cfg.WEIGHT_CATEGORY +
            quality * cfg.WEIGHT_QUALITY +
            color * cfg.WEIGHT_COLOR +
            semantic * cfg.WEIGHT_SEMANTIC +
            timeOfDay * cfg.WEIGHT_TIME_OF_DAY

        val saturation = saturationSuppression(wallpaper.category, prepared.categoryExposure)
        val dislike = dislikeSuppression(wallpaper, context, prepared.dislikeCentroid)

        val suppressed = (base * saturation * dislike).coerceAtLeast(0f)
        val exploration = explorationBonus(wallpaper.category, context, prepared.totalFeedback)

        return ScoredWallpaper(
            wallpaper = wallpaper,
            tasteScore = taste,
            categoryScore = category,
            qualityScore = quality,
            colorScore = color,
            semanticScore = semantic,
            timeOfDayScore = timeOfDay,
            explorationBonus = exploration,
            saturationSuppression = saturation,
            dislikeSuppression = dislike,
            finalScore = suppressed + exploration
        )
    }

    /**
     * Bayesian (Beta(1,1)) posterior mean of the like rate for a category:
     * `(likes + 1) / (likes + dislikes + 2)`.  Unknown categories are
     * neutral; the exploration bonus — not the affinity — makes them
     * surface.
     */
    private fun categoryAffinity(category: String, context: RankingContext): Float {
        if (category.isBlank()) return RecommenderConfig.NEUTRAL_SCORE
        val stats = context.categoryStats[category] ?: return RecommenderConfig.NEUTRAL_SCORE
        val posterior = (stats.likes + 1f) / (stats.likes + stats.dislikes + 2f)
        return posterior.coerceIn(0f, 1f)
    }

    /**
     * Objective quality in [0, 1]: resolution, tonal balance, palette size
     * and (where available) the server-side aesthetic score.
     */
    fun qualityScore(wallpaper: WallpaperMetadata): Float {
        var score = 0f

        // Resolution (up to 0.45).  Parsed without split() — this runs for
        // every candidate on every ranking pass.
        val resolution = wallpaper.resolution
        val sep = resolution.indexOf('x')
        if (sep > 0) {
            val width = resolution.substring(0, sep).toIntOrNull() ?: 0
            val height = resolution.substring(sep + 1).toIntOrNull() ?: 0
            val pixels = width.toLong() * height.toLong()
            score += when {
                pixels >= 3840L * 2160L -> 0.45f
                pixels >= 2560L * 1440L -> 0.38f
                pixels >= 1920L * 1080L -> 0.30f
                pixels >= 1280L * 720L -> 0.20f
                pixels > 0 -> 0.10f
                else -> 0f
            }
            // Portrait/lean-landscape fit for phone wallpapers (up to 0.15)
            if (width > 0 && height > 0) {
                val aspect = height.toFloat() / width.toFloat()
                score += when {
                    aspect in 1.5f..2.2f -> 0.15f
                    aspect in 0.9f..1.1f -> 0.08f
                    else -> 0.03f
                }
            }
        }

        // Tonal balance (up to 0.15): reward mid-range brightness/contrast
        val brightnessBalance = 1f - abs(wallpaper.brightness / 100f - 0.5f) * 2f
        val contrastBalance = 1f - abs(wallpaper.contrast / 100f - 0.5f) * 2f
        score += (brightnessBalance + contrastBalance) * 0.075f

        // Palette richness (up to 0.10)
        score += when {
            wallpaper.colors.size >= 5 -> 0.10f
            wallpaper.colors.size >= 3 -> 0.07f
            wallpaper.colors.size >= 2 -> 0.04f
            else -> 0.01f
        }

        // Server-side aesthetic score (up to 0.15), Vanderwaals Collection only
        if (wallpaper.aestheticScore > 0f) {
            score += ((wallpaper.aestheticScore - 5f) / 5f).coerceIn(0f, 1f) * 0.15f
        }

        return score.coerceIn(0f, 1f)
    }

    /**
     * Perceptual colour match in [0, 1]: 1 − minΔE/100 between the
     * wallpaper palette and the palette of liked wallpapers.  Neutral when
     * either side has no data.  The liked side arrives pre-converted to
     * CIELab (see [PreparedContext]); only the candidate's own colours are
     * converted here.
     */
    private fun colorAffinity(
        wallpaperColors: List<String>,
        likedLab: List<Triple<Double, Double, Double>>
    ): Float {
        if (wallpaperColors.isEmpty() || likedLab.isEmpty()) {
            return RecommenderConfig.NEUTRAL_SCORE
        }
        val candidate = wallpaperColors.take(3).mapNotNull(::parseHexToRgb)
        if (candidate.isEmpty()) {
            return RecommenderConfig.NEUTRAL_SCORE
        }
        var minDeltaE = Double.MAX_VALUE
        for (c in candidate) {
            val lab = ColorSpace.rgbToLab(c[0], c[1], c[2])
            for (l in likedLab) {
                val deltaE = ColorSpace.labDeltaE(lab, l)
                if (deltaE < minDeltaE) minDeltaE = deltaE
            }
        }
        return (1f - (minDeltaE / 100.0).coerceIn(0.0, 1.0).toFloat())
    }

    /**
     * Semantic tag affinity in [0, 1] from learned mood/style maps.
     * Affinity maps live in [-1, 1]; the midpoint maps to neutral.  Only
     * the tags present on both sides contribute — wallpapers without tags
     * (GitHub/Bing) are never penalised.
     */
    private fun semanticAffinity(
        wallpaper: WallpaperMetadata,
        context: RankingContext
    ): Float {
        val affinity = WallpaperScorerLegacy.semanticAffinity(
            wallpaper, context.moodAffinity, context.styleAffinity
        )
        return ((affinity + 1f) / 2f).coerceIn(0f, 1f)
    }

    /**
     * Brightness fit for the wall-clock hour, in [0, 1]: night prefers
     * dark, morning prefers bright, evening prefers moderate.
     */
    fun timeOfDayFit(brightness: Int, hour: Int): Float = when (hour) {
        in 22..23, in 0..5 -> when {
            brightness < 30 -> 1.0f
            brightness < 45 -> 0.7f
            brightness > 65 -> 0.1f
            else -> 0.4f
        }
        in 6..9 -> when {
            brightness > 65 -> 1.0f
            brightness > 50 -> 0.75f
            brightness < 30 -> 0.15f
            else -> 0.45f
        }
        in 10..17 -> RecommenderConfig.NEUTRAL_SCORE
        in 18..21 -> when {
            brightness in 35..65 -> 0.85f
            brightness > 75 -> 0.3f
            brightness < 25 -> 0.25f
            else -> 0.5f
        }
        else -> RecommenderConfig.NEUTRAL_SCORE
    }

    // Post-score adjustments

    /**
     * UCB-style exploration bonus for categories with little *feedback*
     * relative to overall feedback activity.
     *
     * Evidence is measured in feedback events (likes + dislikes), not
     * views: a wallpaper the user saw but never rated is still uncertain,
     * so exploration must persist until the user actually rewards or
     * rejects the category.  This matters for taste evolution — when a
     * user's taste shifts, the new direction has zero feedback and needs
     * sustained (not one-shot) exploration to surface.  The bonus decays
     * naturally as feedback accumulates, and is hard-capped so it can
     * never dominate a strong preference.
     */
    private fun explorationBonus(
        category: String,
        context: RankingContext,
        totalFeedback: Int
    ): Float {
        if (category.isBlank()) return 0f
        if (totalFeedback <= 0) return 0f
        val stats = context.categoryStats[category] ?: RankingEngine.CategoryStats()
        val feedback = stats.likes + stats.dislikes
        val bonus = RecommenderConfig.EXPLORATION_SCALE *
            sqrt(ln(1.0 + totalFeedback) / (1.0 + feedback)).toFloat()
        return bonus.coerceAtMost(RecommenderConfig.EXPLORATION_MAX_BONUS)
    }

    /**
     * Single gentle saturation penalty for categories that dominated the
     * recent window.  Logarithmic in exposure count, capped at
     * [RecommenderConfig.SATURATION_MAX_SUPPRESSION] (15%), applied once.
     */
    private fun saturationSuppression(
        category: String,
        categoryExposure: Map<String, Int>
    ): Float {
        if (category.isBlank()) return 1f
        val exposures = categoryExposure[category] ?: 0
        if (exposures == 0) return 1f
        val penalty = (1f - exp(-exposures * 0.5f)) *
            RecommenderConfig.SATURATION_MAX_SUPPRESSION
        return (1f - penalty).coerceIn(0f, 1f)
    }

    /**
     * Combined multiplicative dislike suppression:
     * - item-level: re-showing a wallpaper the user disliked, scaled by the
     *   anchor's recency-relative factor and strength,
     * - centroid-level: embedding similar to the disliked centroid.
     * Applied exactly once, bounded, never stacked with other dislike
     * terms — unlike the legacy path where a dislike hit the score through
     * four separate mechanisms.
     *
     * All factors come from [TasteMemory] in feedback-relative time, so a
     * configure-once user's dislikes keep suppressing until new feedback
     * arrives, while an active user's stale dislikes fade.
     */
    private fun dislikeSuppression(
        wallpaper: WallpaperMetadata,
        context: RankingContext,
        dislikeCentroid: FloatArray?
    ): Float {
        var factor = 1f
        val cfg = RecommenderConfig

        val itemFactor = context.tasteMemory.dislikedItemFactor(wallpaper.id)
        if (itemFactor > 0.05f) {
            factor *= 1f - cfg.DISLIKED_ITEM_SUPPRESSION * itemFactor
        }

        if (dislikeCentroid != null && wallpaper.embedding.isNotEmpty()) {
            val sim = cosine(wallpaper.embedding, dislikeCentroid).coerceAtLeast(0f)
            factor *= 1f - cfg.DISLIKE_CENTROID_SUPPRESSION * sim
        }

        // Liked items inside the re-show cooldown are dampened, not banned.
        val reshowFactor = context.tasteMemory.likedReshowFactor(wallpaper.id)
        if (reshowFactor > 0.05f) {
            factor *= 1f - cfg.LIKED_RESHOW_COOLDOWN_SUPPRESSION * reshowFactor
        }

        return factor.coerceIn(0f, 1f)
    }

    // Selection helpers

    /**
     * Maximal Marginal Relevance over the top-scored candidates, trading
     * relevance against embedding-space similarity to already-selected
     * items.  Kept from the previous architecture — it was the one piece
     * that worked well.
     *
     * Each candidate's maximum similarity to the selected set is computed
     * once per round and reused for both the duplicate hard rule
     * (cos ≥ 0.999 ⟺ maxSim ≥ 0.999) and the MMR score.
     */
    internal fun selectWithMmr(
        ranked: List<ScoredWallpaper>,
        k: Int
    ): List<ScoredWallpaper> {
        if (ranked.size <= k) return ranked

        val pool = ranked.take(RecommenderConfig.MMR_CANDIDATE_POOL).toMutableList()
        val selected = mutableListOf<ScoredWallpaper>()
        selected.add(pool.removeAt(0))

        val lambda = RecommenderConfig.MMR_LAMBDA
        while (selected.size < k && pool.isNotEmpty()) {
            val selectedWithEmbedding = selected.filter {
                it.wallpaper.embedding.isNotEmpty()
            }

            var best: ScoredWallpaper? = null
            var bestMmr = Float.NEGATIVE_INFINITY

            for (candidate in pool) {
                // Hard rule: never fill the pool with visually identical
                // content (cos ≥ 0.999 to something already selected).
                val duplicate: Boolean
                val maxSim: Float
                if (candidate.wallpaper.embedding.isNotEmpty()) {
                    var sim = Float.NaN
                    var dup = false
                    for (sel in selectedWithEmbedding) {
                        val s = cosine(candidate.wallpaper.embedding, sel.wallpaper.embedding)
                        if (sim.isNaN() || s > sim) sim = s
                        if (s >= 0.999f) dup = true
                    }
                    duplicate = dup
                    maxSim = if (sim.isNaN()) 0f else sim
                } else {
                    duplicate = false
                    maxSim = if (selected.isEmpty()) 0f
                    else selected.count {
                        it.wallpaper.category == candidate.wallpaper.category
                    }.toFloat() / selected.size
                }
                if (duplicate) continue

                val mmr = lambda * candidate.finalScore - (1f - lambda) * maxSim
                // Strict '>' keeps the first candidate on ties, matching
                // maxByOrNull's tie-breaking.
                if (mmr > bestMmr) {
                    bestMmr = mmr
                    best = candidate
                }
            }

            if (best == null) break
            selected.add(best)
            pool.remove(best)
        }
        return selected
    }

    /** Temperature-scaled softmax pick over the MMR pool. */
    private fun softmaxPick(
        pool: List<ScoredWallpaper>,
        random: Random
    ): WallpaperMetadata {
        if (pool.size == 1) return pool.first().wallpaper

        val temperature = RecommenderConfig.SELECTION_TEMPERATURE
        val minScore = pool.minOf { it.finalScore }
        val shifted = pool.map { (it.finalScore - minScore).coerceAtLeast(1e-4f) }
        val maxScore = shifted.max()
        val weights = shifted.map { exp((it - maxScore) / temperature) }
        val total = weights.sum()

        var pick = random.nextFloat() * total
        for (i in pool.indices) {
            pick -= weights[i]
            if (pick <= 0f) return pool[i].wallpaper
        }
        return pool.first().wallpaper
    }

    // Small utilities

    private fun cosine(v1: FloatArray, v2: FloatArray): Float {
        if (v1.isEmpty() || v2.isEmpty() || v1.size != v2.size) return 0f
        var dot = 0f
        var m1 = 0f
        var m2 = 0f
        for (i in v1.indices) {
            dot += v1[i] * v2[i]
            m1 += v1[i] * v1[i]
            m2 += v2[i] * v2[i]
        }
        if (m1 == 0f || m2 == 0f) return 0f
        return (dot / (sqrt(m1) * sqrt(m2))).coerceIn(-1f, 1f)
    }
}

/** Parses a `#RRGGBB` string to an RGB triple, or null when malformed. */
private fun parseHexToRgb(hex: String): IntArray? = try {
    val clean = hex.removePrefix("#")
    if (clean.length != 6) null
    else intArrayOf(
        clean.substring(0, 2).toInt(16),
        clean.substring(2, 4).toInt(16),
        clean.substring(4, 6).toInt(16)
    )
} catch (e: Exception) {
    null
}

/**
 * Minimal extraction of the legacy semantic-affinity computation, kept only
 * so [RankingEngine] can reuse the exact mood/style matching logic without
 * depending on the deleted legacy scorer class.
 */
internal object WallpaperScorerLegacy {
    /**
     * Mean affinity in [-1, 1] across the wallpaper's mood and style tags
     * that also appear in the learned affinity maps; 0 when no overlap.
     */
    fun semanticAffinity(
        wallpaper: WallpaperMetadata,
        moodAffinity: Map<String, Float>,
        styleAffinity: Map<String, Float>
    ): Float {
        val hasWallpaperTags = wallpaper.mood.isNotEmpty() || wallpaper.style.isNotEmpty()
        val hasUserAffinity = moodAffinity.isNotEmpty() || styleAffinity.isNotEmpty()
        if (!hasWallpaperTags || !hasUserAffinity) return 0f

        val moodScore = if (wallpaper.mood.isNotEmpty() && moodAffinity.isNotEmpty()) {
            wallpaper.mood.map { moodAffinity[it] ?: 0f }.average().toFloat()
        } else 0f

        val styleScore = if (wallpaper.style.isNotEmpty() && styleAffinity.isNotEmpty()) {
            wallpaper.style.map { styleAffinity[it] ?: 0f }.average().toFloat()
        } else 0f

        val hasMood = wallpaper.mood.isNotEmpty() && moodAffinity.isNotEmpty()
        val hasStyle = wallpaper.style.isNotEmpty() && styleAffinity.isNotEmpty()
        return when {
            hasMood && hasStyle -> (moodScore + styleScore) / 2f
            hasMood -> moodScore
            hasStyle -> styleScore
            else -> 0f
        }
    }
}
