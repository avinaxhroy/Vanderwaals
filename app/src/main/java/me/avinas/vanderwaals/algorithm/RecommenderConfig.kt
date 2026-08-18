package me.avinas.vanderwaals.algorithm

/**
 * Single source of truth for every tunable constant in the recommendation
 * engine.  All ranking and selection logic must read its parameters from
 * here — no component may declare private magic numbers that influence
 * ranking.
 *
 * ## Score composition
 *
 * Every component is normalised to [0, 1] (0.5 = neutral / no data) before
 * weighting, and the weights below sum to exactly 1.0, so a wallpaper that
 * wins every component scores 1.0 and one that loses every component scores
 * 0.0.  Suppressions (dislikes, saturation) are applied multiplicatively
 * *after* the weighted sum so they can never flip the sign of a score or
 * stack additively without bound.
 *
 * | Component | Weight | Signal                                            |
 * |-----------|--------|---------------------------------------------------|
 * | Taste     | 0.60   | Multi-anchor embedding similarity (dominant)      |
 * | Category  | 0.12   | Bayesian like-rate per category                   |
 * | Quality   | 0.10   | Resolution, aesthetic score, tonal balance        |
 * | Color     | 0.06   | Perceptual palette match to liked wallpapers      |
 * | Semantic  | 0.06   | Mood/style tag affinity (Vanderwaals Collection)  |
 * | Time      | 0.06   | Brightness fit for the current wall-clock hour    |
 */
object RecommenderConfig {

    // Core score weights (must sum to 1.0)
    const val WEIGHT_TASTE = 0.60f
    const val WEIGHT_CATEGORY = 0.12f
    const val WEIGHT_QUALITY = 0.10f
    const val WEIGHT_COLOR = 0.06f
    const val WEIGHT_SEMANTIC = 0.06f
    const val WEIGHT_TIME_OF_DAY = 0.06f

    // Taste memory
    /**
     * Maximum number of positive (liked) anchors retained.  Older anchors
     * beyond this cap are pruned; recency weighting handles the rest.
     */
    const val MAX_POSITIVE_ANCHORS = 30

    /**
     * Maximum number of negative (disliked) anchors retained for cooldown
     * and centroid suppression.
     */
    const val MAX_NEGATIVE_ANCHORS = 200

    /** Half-life of a liked anchor's influence, in days. */
    const val POSITIVE_HALF_LIFE_DAYS = 14.0

    /** Half-life of a disliked anchor's influence, in days. */
    const val NEGATIVE_HALF_LIFE_DAYS = 7.0

    /**
     * Blend between best-matching anchor and the recency-weighted mean of
     * all anchors when scoring taste similarity.  A high value supports
     * users with several distinct tastes (dark minimal AND nature); the mean
     * component rewards wallpapers that broadly fit everything the user
     * liked.
     */
    const val TASTE_BEST_ANCHOR_SHARE = 0.75f

    /**
     * Strength recorded for implicit feedback anchors relative to explicit
     * feedback (implicit signals are noisier).
     */
    const val IMPLICIT_FEEDBACK_STRENGTH = 0.4f

    /**
     * Anchors at or above this strength count as explicit feedback and may
     * advance the taste memory's reference clock.  Implicit events below
     * this threshold age relative to the last explicit event but never
     * displace explicit evidence by themselves.
     */
    const val EXPLICIT_STRENGTH_THRESHOLD = 0.8f

    // Dislike suppression
    /**
     * Multiplicative score reduction for wallpapers whose embedding is
     * positively correlated with the recency-weighted centroid of recently
     * disliked embeddings.  Applied once, bounded, never additive.
     */
    const val DISLIKE_CENTROID_SUPPRESSION = 0.25f

    /**
     * Multiplicative score reduction for re-showing a wallpaper the user
     * explicitly disliked while its anchor is still alive (within
     * [NEGATIVE_HALF_LIFE_DAYS]-scaled decay).  Not a hard exclusion:
     * tastes change and catalog churn can re-introduce remastered assets.
     */
    const val DISLIKED_ITEM_SUPPRESSION = 0.5f

    /**
     * Multiplicative score reduction applied while a *liked* wallpaper is
     * inside its re-show cooldown, so favourites can return eventually
     * without dominating the rotation.
     */
    const val LIKED_RESHOW_COOLDOWN_SUPPRESSION = 0.5f

    /** Days after liking before a wallpaper may be re-shown at full score. */
    const val LIKED_RESHOW_COOLDOWN_DAYS = 21.0

    // Exploration & diversity
    /**
     * Scale of the UCB-style exploration bonus:
     * `EXPLORATION_SCALE * sqrt(ln(1 + totalFeedback) / (1 + categoryFeedback))`.
     * Categories with little feedback relative to overall activity earn a
     * bonus that decays naturally as evidence accumulates — no dice rolls.
     */
    const val EXPLORATION_SCALE = 0.10f

    /** Hard ceiling for the exploration bonus so it can never dominate taste. */
    const val EXPLORATION_MAX_BONUS = 0.15f

    /**
     * Single gentle saturation penalty for categories shown repeatedly in
     * the recent window.  Replaces the three stacked anti-repetition
     * mechanisms of the previous architecture.
     */
    const val SATURATION_MAX_SUPPRESSION = 0.15f

    /** How many recent selections form the saturation window. */
    const val SATURATION_WINDOW = 8

    /** MMR relevance/diversity trade-off (higher = more relevance). */
    const val MMR_LAMBDA = 0.75f

    /** Number of top-scored candidates fed into MMR re-ranking. */
    const val MMR_CANDIDATE_POOL = 50

    /** Final pool size MMR reduces to before stochastic pick. */
    const val SELECTION_POOL_SIZE = 8

    /** Softmax temperature for the final stochastic pick. */
    const val SELECTION_TEMPERATURE = 0.35f

    // Neutral component value
    /**
     * Value used for any score component with insufficient data.  Keeping
     * this identical across components keeps the weighted sum calibrated
     * when signals are missing (e.g. Vanderwaals Collection items have no
     * client-side embedding).
     */
    const val NEUTRAL_SCORE = 0.5f

    /** Millis per day, shared by recency computations. */
    const val MILLIS_PER_DAY = 24.0 * 60.0 * 60.0 * 1000.0
}
