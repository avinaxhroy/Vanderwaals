package me.avinas.vanderwaals.algorithm

import kotlin.math.log2
import kotlin.math.min

/**
 * Offline evaluation harness for the wallpaper recommendation algorithm.
 *
 * Replays a chronological sequence of [FeedbackEvent]s as if the recommender had been run
 * live, then measures how well [rankFn] ranks the eventually-liked wallpaper relative to
 * the full candidate pool at the time of selection.
 *
 * ## Metrics
 * - **Hit Rate @ k**: fraction of liked events where the liked wallpaper appeared in the
 *   top-k ranked candidates at selection time.
 * - **nDCG @ k**: Normalised Discounted Cumulative Gain — rank-aware quality metric that
 *   rewards surfacing the liked wallpaper near the top of the list.
 * - **Precision @ k**: fraction of the top-k candidates that were ultimately liked.
 *
 * ## Design
 * The evaluator is intentionally decoupled from all Android and Hilt dependencies so it can
 * be run in plain JVM unit tests or from an offline analysis script.  The ranking logic is
 * injected as a lambda ([rankFn]) so any version of the algorithm can be evaluated without
 * changing this class.
 *
 * ## Usage
 * ```kotlin
 * val events: List<FeedbackEvent> = buildEventsFromHistory(historyDao, preferencesDao)
 * val result = RecommenderEvaluator().evaluate(
 *     events     = events,
 *     candidates = allWallpapers,
 *     idFn       = { it.id },
 *     rankFn     = { prefVec, pool ->
 *         pool.sortedByDescending { similarityCalculator.calculateSimilarity(prefVec, it.embedding) }
 *     },
 *     k          = 10
 * )
 * Log.d("Eval", result.summary())
 * ```
 */
class RecommenderEvaluator {

    // ─────────────────────────────────────────────────────────────────────────
    // Data types
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * A single feedback event replayed during evaluation.
     *
     * @property wallpaperId           ID of the wallpaper that was shown.
     * @property liked                 True if the user explicitly liked this wallpaper.
     * @property timestampMs           Wall-clock time when the wallpaper was applied (epoch ms).
     * @property preferenceVectorAtTime Snapshot of the preference vector active at the time of
     *                                 selection.  Supply an empty array for cold-start events.
     * @property candidatePool         IDs available at selection time.  If empty the evaluator
     *                                 uses the full [candidates] list passed to [evaluate].
     */
    data class FeedbackEvent(
        val wallpaperId: String,
        val liked: Boolean,
        val timestampMs: Long,
        val preferenceVectorAtTime: FloatArray = floatArrayOf(),
        val candidatePool: Set<String> = emptySet()
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is FeedbackEvent) return false
            return wallpaperId == other.wallpaperId &&
                liked == other.liked &&
                timestampMs == other.timestampMs &&
                preferenceVectorAtTime.contentEquals(other.preferenceVectorAtTime) &&
                candidatePool == other.candidatePool
        }

        override fun hashCode(): Int {
            var result = wallpaperId.hashCode()
            result = 31 * result + liked.hashCode()
            result = 31 * result + timestampMs.hashCode()
            result = 31 * result + preferenceVectorAtTime.contentHashCode()
            result = 31 * result + candidatePool.hashCode()
            return result
        }
    }

    /**
     * Aggregated result of an offline evaluation run.
     *
     * @property hitRateAtK      Fraction of liked events where the item was in the top-k.
     * @property ndcgAtK         Mean nDCG@k across all liked events (higher = better ranking).
     * @property precisionAtK    Mean Precision@k across all liked events.
     * @property k               Rank cut-off used for all three metrics.
     * @property likedEventCount Number of liked events that contributed to the metrics.
     * @property totalEventCount Total events in the replay sequence (liked + non-liked).
     */
    data class EvaluationResult(
        val hitRateAtK: Float,
        val ndcgAtK: Float,
        val precisionAtK: Float,
        val k: Int,
        val likedEventCount: Int,
        val totalEventCount: Int
    ) {
        /**
         * Human-readable summary suitable for logcat or a console.
         */
        fun summary(): String = buildString {
            appendLine("=== RecommenderEvaluator Results ===")
            appendLine("Events  : $likedEventCount liked / $totalEventCount total")
            appendLine("k       : $k")
            appendLine("HitRate@$k  : ${String.format("%.4f", hitRateAtK)}  " +
                       "(${String.format("%.1f", hitRateAtK * 100f)}%)")
            appendLine("nDCG@$k     : ${String.format("%.4f", ndcgAtK)}")
            append  ("Precision@$k: ${String.format("%.4f", precisionAtK)}")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Core evaluation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Evaluates the recommendation algorithm by replaying [events] and measuring how well
     * [rankFn] ranks the liked wallpaper within the candidate pool.
     *
     * Only **liked** events contribute to the three metrics — other events are still
     * processed to allow future extensions (e.g. supplying an updated preference vector for
     * subsequent events), but are excluded from metric aggregation.
     *
     * @param events     Chronological list of feedback events (oldest first).
     * @param candidates All wallpapers available in the catalog.
     * @param idFn       Function that extracts a unique string ID from a wallpaper object.
     * @param rankFn     Pure ranking function: (preferenceVector, candidatePool) → ranked list.
     *                   Must be deterministic.  The list may be shorter than [candidates] if
     *                   [FeedbackEvent.candidatePool] is non-empty.
     * @param k          Rank cut-off for hit-rate, nDCG, and precision (default: 10).
     * @return [EvaluationResult] with aggregated metrics.
     */
    fun <W : Any> evaluate(
        events: List<FeedbackEvent>,
        candidates: List<W>,
        idFn: (W) -> String,
        rankFn: (preferenceVector: FloatArray, pool: List<W>) -> List<W>,
        k: Int = 10
    ): EvaluationResult {
        if (events.isEmpty()) {
            return EvaluationResult(0f, 0f, 0f, k, 0, 0)
        }

        val candidateById: Map<String, W> = candidates.associateBy(idFn)

        var hitCount     = 0
        var ndcgSum      = 0.0
        var precisionSum = 0.0
        var likedEvents  = 0

        events.forEach { event ->
            // Only liked events contribute to metrics
            if (!event.liked) return@forEach

            // Determine the candidate pool for this event
            val pool: List<W> = if (event.candidatePool.isNotEmpty()) {
                event.candidatePool.mapNotNull { candidateById[it] }
            } else {
                candidates
            }
            if (pool.isEmpty()) return@forEach

            // Rank the pool as the algorithm would have at the time of selection
            val ranked   = rankFn(event.preferenceVectorAtTime, pool)
            val topK     = ranked.take(k)
            val topKIds  = topK.map(idFn).toSet()

            // ── Hit Rate @ k ────────────────────────────────────────────────
            if (event.wallpaperId in topKIds) hitCount++

            // ── nDCG @ k ────────────────────────────────────────────────────
            // Single-relevant-item nDCG: DCG / IDCG where IDCG = 1/log2(2) = 1.0 (item at pos 1)
            val rank = ranked.indexOfFirst { idFn(it) == event.wallpaperId }
            if (rank in 0 until k) {
                // Position is 1-based: rank 0 → position 1 → discount = 1/log2(2) = 1.0
                val dcg  = 1.0 / log2(rank + 2.0)   // +2: rank is 0-based, position starts at 1
                val idcg = 1.0 / log2(2.0)           // Best possible: item at position 1
                ndcgSum += dcg / idcg
            }

            // ── Precision @ k ───────────────────────────────────────────────
            val likedInTopK = topKIds.count { it == event.wallpaperId }
            precisionSum += likedInTopK.toDouble() / min(k, pool.size)

            likedEvents++
        }

        if (likedEvents == 0) {
            return EvaluationResult(0f, 0f, 0f, k, 0, events.size)
        }

        return EvaluationResult(
            hitRateAtK      = hitCount.toFloat() / likedEvents,
            ndcgAtK         = (ndcgSum / likedEvents).toFloat(),
            precisionAtK    = (precisionSum / likedEvents).toFloat(),
            k               = k,
            likedEventCount = likedEvents,
            totalEventCount = events.size
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Convenience factory — builds FeedbackEvents from parallel lists
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds a list of [FeedbackEvent]s from parallel history arrays.
     *
     * Useful when loading events from a Room DAO without custom mapping logic.
     *
     * @param wallpaperIds            IDs in chronological order (oldest first).
     * @param feedbackFlags           Nullable feedback strings; "like" → liked = true.
     * @param timestampsMs            Epoch-ms timestamps aligned with [wallpaperIds].
     * @param preferenceVectorsByTime Snapshot preference vectors aligned with [wallpaperIds].
     *                                Supply an empty list to use zero vectors for all events.
     * @param candidatePools          Per-event candidate pools (empty = use full catalog).
     */
    fun buildEvents(
        wallpaperIds: List<String>,
        feedbackFlags: List<String?>,
        timestampsMs: List<Long>,
        preferenceVectorsByTime: List<FloatArray> = emptyList(),
        candidatePools: List<Set<String>> = emptyList()
    ): List<FeedbackEvent> {
        require(wallpaperIds.size == feedbackFlags.size) {
            "wallpaperIds and feedbackFlags must have the same size"
        }
        require(wallpaperIds.size == timestampsMs.size) {
            "wallpaperIds and timestampsMs must have the same size"
        }
        return wallpaperIds.indices.map { i ->
            FeedbackEvent(
                wallpaperId            = wallpaperIds[i],
                liked                  = feedbackFlags[i] == "like",
                timestampMs            = timestampsMs[i],
                preferenceVectorAtTime = preferenceVectorsByTime.getOrElse(i) { floatArrayOf() },
                candidatePool          = candidatePools.getOrElse(i) { emptySet() }
            )
        }
    }
}
