package me.avinas.vanderwaals.algorithm

/**
 * Canonical recommendation scoring weights.
 *
 * Single source of truth for all ranking paths that combine embedding,
 * colour, category, and composition signals.  Components that compute
 * composite similarity scores should reference these constants instead
 * of declaring their own private weight values.
 *
 * | Signal      | Weight | Notes                                            |
 * |-------------|--------|--------------------------------------------------|
 * | Embedding   | 0.75   | MobileNetV4 cosine similarity (primary signal)   |
 * | Color       | 0.12   | Perceptual CIE76 ΔE in LAB space                 |
 * | Composition | 0.11   | Layout / rule-of-thirds (enhanced path only)     |
 * | Category    | 0.02   | Minimal — labels unreliable across sources       |
 *
 * **Additive boosts** (applied on top of the composite score, not part of
 * the weight normalisation above):
 * - Semantic (mood/style): ±8% — learned tag affinity, Vanderwaals Collection only
 * - Aesthetic score: 0–10% — server-side quality signal, Vanderwaals Collection only
 * - Temporal diversity: ±15% — recency penalty + exploration boost
 * - Time-of-day: [-4%,+5%] — brightness context by wall-clock hour
 *
 * Non-enhanced paths (no composition data) should renormalise by
 * [STANDARD_WEIGHTS_SUM] so a perfect match scores 1.0.
 */
object RecommendationWeights {
    const val EMBEDDING_WEIGHT = 0.75f
    const val COLOR_WEIGHT = 0.12f
    const val COMPOSITION_WEIGHT = 0.11f
    const val CATEGORY_WEIGHT = 0.02f

    /** Sum of weights used in non-enhanced paths (excludes composition). */
    val STANDARD_WEIGHTS_SUM = EMBEDDING_WEIGHT + COLOR_WEIGHT + CATEGORY_WEIGHT
}
