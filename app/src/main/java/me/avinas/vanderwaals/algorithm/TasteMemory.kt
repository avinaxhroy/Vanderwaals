package me.avinas.vanderwaals.algorithm

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Multi-anchor model of the user's taste, replacing the single
 * EMA preference vector.
 *
 * ## Why anchors instead of one vector
 *
 * A single vector averages everything the user ever liked into one
 * direction.  For users with more than one taste (dark minimal AND nature
 * photography) the average matches neither, and because the old learning
 * rate decayed with feedback count, the vector eventually froze and could
 * not follow taste changes.  Anchors fix both problems:
 *
 * - A wallpaper scores well if it is similar to **any** recent liked
 *   anchor (see [tasteSimilarity]), so distinct tastes coexist.
 * - Anchors carry timestamps and decay exponentially, so the model
 *   continuously forgets.  New feedback always matters at full strength;
 *   taste evolution is limited only by how fast the user gives feedback,
 *   not by a shrinking learning rate.
 *
 * Dislikes are **suppression memory**, not steering: they never push the
 * positive direction anywhere (pushing away from a disliked embedding in
 * 1280-d space mostly wanders into unpopulated regions).  Instead they
 * provide a per-item cooldown and a recency-weighted centroid used to
 * down-rank similar candidates.
 *
 * This class is pure Kotlin with no Android dependencies; persistence lives
 * in the `taste_anchors` Room table mapped by the repository layer.
 *
 * ## Configure-once semantics (relative time)
 *
 * Anchor ages are measured **relative to the newest explicit feedback
 * event, not the wall clock**.  A user who configures their taste once at
 * onboarding and never taps like/dislike again keeps full-strength anchors
 * indefinitely — dormant memory is not evidence that taste changed, so it
 * must not evaporate (the previous wall-clock decay compressed the taste
 * signal over a few half-lives until exploration dominated the ranking,
 * which users experienced as "random recommendations after a few days").
 *
 * Evidence is only displaced by newer evidence: when new feedback arrives,
 * everything older ages relative to it, enabling taste evolution.  Implicit
 * (low-strength) events do not advance the reference clock — one casual
 * "Change Now" browse must not instantly age the user's entire onboarding
 * taste.
 *
 * @param positiveAnchors liked wallpapers (embedding may be empty for items
 *   without client-side embeddings; such anchors only serve as cooldown
 *   markers).
 * @param negativeAnchors disliked wallpapers.
 * @param nowMillis wall clock; only a fallback reference when no anchor
 *   carries explicit strength.
 */
class TasteMemory(
    private val positiveAnchors: List<Anchor>,
    private val negativeAnchors: List<Anchor>,
    private val nowMillis: Long
) {

    data class Anchor(
        val wallpaperId: String,
        val embedding: FloatArray,
        val updatedAt: Long,
        /** 1.0 for explicit feedback, lower for implicit signals. */
        val strength: Float = 1.0f
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Anchor) return false
            return wallpaperId == other.wallpaperId &&
                updatedAt == other.updatedAt &&
                strength == other.strength &&
                embedding.contentEquals(other.embedding)
        }

        override fun hashCode(): Int {
            var result = wallpaperId.hashCode()
            result = 31 * result + updatedAt.hashCode()
            result = 31 * result + strength.hashCode()
            result = 31 * result + embedding.contentHashCode()
            return result
        }
    }

    /** True when at least one positive anchor carries a usable embedding. */
    val hasTaste: Boolean
        get() = positiveScoringAnchors.isNotEmpty()

    private val positiveById: Map<String, Anchor> =
        positiveAnchors.associateBy { it.wallpaperId }

    private val negativeById: Map<String, Anchor> =
        negativeAnchors.associateBy { it.wallpaperId }

    /**
     * Reference time all anchor ages are measured against: the newest
     * explicit-strength event across both kinds.  Falls back to the newest
     * event of any strength, then to the wall clock for an empty memory.
     */
    private val referenceTime: Long = run {
        var explicitMax = Long.MIN_VALUE
        var anyMax = Long.MIN_VALUE
        for (anchors in listOf(positiveAnchors, negativeAnchors)) {
            for (anchor in anchors) {
                if (anchor.updatedAt > anyMax) anyMax = anchor.updatedAt
                if (anchor.strength >= RecommenderConfig.EXPLICIT_STRENGTH_THRESHOLD &&
                    anchor.updatedAt > explicitMax
                ) {
                    explicitMax = anchor.updatedAt
                }
            }
        }
        when {
            explicitMax != Long.MIN_VALUE -> explicitMax
            anyMax != Long.MIN_VALUE -> anyMax
            else -> nowMillis
        }
    }

    /**
     * Anchors with usable embeddings paired with their per-snapshot-constant
     * scoring data, so [tasteSimilarity]'s per-candidate hot loop is a bare
     * dot product: no `pow` recency computation and no anchor magnitude
     * recomputation per (candidate × anchor) pair.
     */
    private class ScoringAnchor(
        val embedding: FloatArray,
        /** recency weight × strength — constant for the snapshot's clock. */
        val weight: Float,
        /** sqrt(Σ embedding²) — constant per anchor. */
        val magnitude: Float
    )

    private val positiveScoringAnchors: List<ScoringAnchor> = positiveAnchors
        .asSequence()
        .filter { it.embedding.isNotEmpty() }
        .map { anchor ->
            var magSq = 0f
            for (v in anchor.embedding) magSq += v * v
            ScoringAnchor(
                embedding = anchor.embedding,
                weight = recencyWeight(
                    anchor.updatedAt,
                    RecommenderConfig.POSITIVE_HALF_LIFE_DAYS
                ) * anchor.strength,
                magnitude = sqrt(magSq)
            )
        }
        .toList()

    /**
     * Taste match of a candidate embedding, in [0, 1].
     *
     * ```
     * r_j = strength_j * 0.5 ^ (ageDays_j / POSITIVE_HALF_LIFE_DAYS)
     * s_j = cosine(candidate, anchor_j)
     * score = BEST_SHARE * max_j(r_j * s_j) + (1 - BEST_SHARE) * weightedMean_j(s_j)
     * ```
     *
     * The max term lets any single recent strong taste carry a candidate;
     * the mean term rewards broad fit.  With no usable anchors this returns
     * [RecommenderConfig.NEUTRAL_SCORE] and the caller is expected to use
     * its cold-start path.
     */
    fun tasteSimilarity(embedding: FloatArray): Float {
        if (embedding.isEmpty()) return RecommenderConfig.NEUTRAL_SCORE

        // Candidate magnitude is identical across anchors — compute once.
        var candidateMagSq = 0f
        for (v in embedding) candidateMagSq += v * v
        val candidateMag = sqrt(candidateMagSq)

        var best = Float.NEGATIVE_INFINITY
        var weightedSum = 0f
        var weightTotal = 0f

        for (data in positiveScoringAnchors) {
            val sim = if (data.magnitude == 0f || candidateMag == 0f ||
                data.embedding.size != embedding.size
            ) {
                0f
            } else {
                var dot = 0f
                for (i in embedding.indices) {
                    dot += embedding[i] * data.embedding[i]
                }
                (dot / (candidateMag * data.magnitude)).coerceIn(-1f, 1f)
            }
            val scaled = data.weight * sim
            if (scaled > best) best = scaled
            weightedSum += data.weight * sim
            weightTotal += data.weight
        }

        if (weightTotal <= 0f) return RecommenderConfig.NEUTRAL_SCORE

        val mean = weightedSum / weightTotal
        val bestShare = RecommenderConfig.TASTE_BEST_ANCHOR_SHARE
        val score = bestShare * best.coerceIn(0f, 1f) +
            (1f - bestShare) * mean.coerceIn(0f, 1f)
        return score.coerceIn(0f, 1f)
    }

    /**
     * Timestamp of the most recent like for [wallpaperId], or null if the
     * user never liked it.  Used for the liked-item re-show cooldown.
     */
    fun lastLikedAt(wallpaperId: String): Long? =
        positiveById[wallpaperId]?.updatedAt

    /**
     * Timestamp of the most recent dislike for [wallpaperId], or null.
     * Used for the disliked-item suppression cooldown.
     */
    fun lastDislikedAt(wallpaperId: String): Long? =
        negativeById[wallpaperId]?.updatedAt

    /** Strength recorded when [wallpaperId] was disliked (0 if never). */
    fun dislikeStrength(wallpaperId: String): Float =
        negativeById[wallpaperId]?.strength ?: 0f

    /**
     * Recency-weighted centroid of disliked embeddings, or null when no
     * disliked anchor has a usable embedding.  Cosine similarity to this
     * centroid drives bounded dislike suppression in the ranking engine.
     *
     * The snapshot is immutable, so the centroid is computed at most once
     * per instance — ranking previously recomputed it for every candidate.
     */
    private val dislikeCentroidCache: FloatArray? by lazy(::computeDislikeCentroid)

    fun dislikeCentroid(): FloatArray? = dislikeCentroidCache

    private fun computeDislikeCentroid(): FloatArray? {
        var dim = -1
        for (anchor in negativeAnchors) {
            if (anchor.embedding.isNotEmpty()) {
                dim = anchor.embedding.size
                break
            }
        }
        if (dim <= 0) return null

        val sum = FloatArray(dim)
        var weightTotal = 0f
        for (anchor in negativeAnchors) {
            if (anchor.embedding.size != dim) continue
            val recency = recencyWeight(
                anchor.updatedAt,
                RecommenderConfig.NEGATIVE_HALF_LIFE_DAYS
            ) * anchor.strength
            if (recency <= 0f) continue
            for (i in 0 until dim) sum[i] += recency * anchor.embedding[i]
            weightTotal += recency
        }
        if (weightTotal <= 0f) return null

        val centroid = FloatArray(dim) { sum[it] / weightTotal }
        var magnitude = 0f
        for (v in centroid) magnitude += v * v
        magnitude = sqrt(magnitude)
        if (magnitude == 0f) return null
        return FloatArray(dim) { centroid[it] / magnitude }
    }

    /**
     * Recency-weighted centroid of positive anchors — the modern
     * equivalent of the legacy `preferenceVector`, kept so legacy readers
     * (e.g. similarity searches) keep working.  Returns an empty array
     * when no positive anchor has an embedding.
     */
    private val positiveCentroidCache: FloatArray by lazy(::computePositiveCentroid)

    fun positiveCentroid(): FloatArray = positiveCentroidCache

    private fun computePositiveCentroid(): FloatArray {
        var dim = -1
        for (anchor in positiveAnchors) {
            if (anchor.embedding.isNotEmpty()) {
                dim = anchor.embedding.size
                break
            }
        }
        if (dim <= 0) return FloatArray(0)

        val sum = FloatArray(dim)
        var weightTotal = 0f
        for (anchor in positiveAnchors) {
            if (anchor.embedding.size != dim) continue
            val recency = recencyWeight(
                anchor.updatedAt,
                RecommenderConfig.POSITIVE_HALF_LIFE_DAYS
            ) * anchor.strength
            for (i in 0 until dim) sum[i] += recency * anchor.embedding[i]
            weightTotal += recency
        }
        if (weightTotal <= 0f) return FloatArray(dim)

        val centroid = FloatArray(dim) { sum[it] / weightTotal }
        var magnitude = 0f
        for (v in centroid) magnitude += v * v
        magnitude = sqrt(magnitude)
        if (magnitude == 0f) return FloatArray(dim)
        return FloatArray(dim) { centroid[it] / magnitude }
    }

    /**
     * Exponential decay factor in (0, 1] for an event, measured **relative
     * to [referenceTime]** (the newest explicit feedback), per
     * [recencyWeight]'s configure-once contract.
     */
    private fun recencyWeight(eventAt: Long, halfLifeDays: Double): Float {
        val ageDays = ((referenceTime - eventAt).coerceAtLeast(0L)) /
            RecommenderConfig.MILLIS_PER_DAY
        return 0.5.pow(ageDays / halfLifeDays).toFloat()
    }

    /**
     * Suppression factor in [0, 1] for re-showing a previously disliked
     * wallpaper: recency (relative to the newest feedback) × recorded
     * strength.  Dormant dislikes persist at full factor; a stream of new
     * feedback fades them.
     */
    fun dislikedItemFactor(wallpaperId: String): Float {
        val anchor = negativeById[wallpaperId] ?: return 0f
        return recencyWeight(
            anchor.updatedAt,
            RecommenderConfig.NEGATIVE_HALF_LIFE_DAYS
        ) * anchor.strength
    }

    /**
     * Re-show cooldown factor in [0, 1] for a previously liked wallpaper:
     * 1 right after the like (dampen re-shows), decaying to 0 after the
     * cooldown.  Ages are relative to the newest feedback, so liked
     * wallpapers stay rotated out until new feedback arrives.
     */
    fun likedReshowFactor(wallpaperId: String): Float {
        val anchor = positiveById[wallpaperId] ?: return 0f
        val ageDays = ((referenceTime - anchor.updatedAt).coerceAtLeast(0L)) /
            RecommenderConfig.MILLIS_PER_DAY
        return 0.5.pow(ageDays / RecommenderConfig.LIKED_RESHOW_COOLDOWN_DAYS).toFloat()
    }
}
