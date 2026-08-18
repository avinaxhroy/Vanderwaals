package me.avinas.vanderwaals.data.repository

import me.avinas.vanderwaals.algorithm.TasteMemory
import me.avinas.vanderwaals.data.entity.TasteAnchor

/**
 * Persistence boundary for the user's [TasteMemory].
 *
 * The repository owns two invariants the algorithm relies on:
 * 1. **Bounded state** — after every write the per-kind anchor rows are
 *    pruned to the configured cap, newest first.
 * 2. **Legacy continuity** — when no like anchors exist yet but the legacy
 *    single preference vector does (users upgrading from the EMA
 *    architecture), the vector is surfaced as one synthetic in-memory
 *    anchor so learned taste survives the upgrade and then fades
 *    naturally as real feedback arrives.
 */
interface TasteAnchorRepository {

    /**
     * Builds the immutable [TasteMemory] snapshot for [nowMillis].
     * Cheap to call per selection; rows are small and capped.
     */
    suspend fun getTasteMemory(nowMillis: Long = System.currentTimeMillis()): TasteMemory

    /**
     * Records (or refreshes) a liked anchor and prunes the like set.
     *
     * @param embedding the wallpaper's client-side embedding; empty when
     *   the source provides none (row then acts as cooldown marker only)
     * @param strength 1.0 explicit, lower for implicit signals
     */
    suspend fun recordLike(
        wallpaperId: String,
        embedding: FloatArray,
        nowMillis: Long = System.currentTimeMillis(),
        strength: Float = 1.0f
    )

    /**
     * Records (or refreshes) a disliked anchor and prunes the dislike set.
     */
    suspend fun recordDislike(
        wallpaperId: String,
        embedding: FloatArray,
        nowMillis: Long = System.currentTimeMillis(),
        strength: Float = 1.0f
    )

    /** Drops a like anchor (e.g. the user un-likes a wallpaper). */
    suspend fun removeLike(wallpaperId: String)

    /** Wipes all anchors (debug/reset flows). */
    suspend fun clearAll()
}

/**
 * Room-backed implementation.  The legacy-vector seeding in
 * [getTasteMemory] is deliberately in-memory only: the first real like
 * persists a true anchor and the synthetic one disappears on the next
 * snapshot, so stale EMA state can never outrank fresh feedback.
 */
class TasteAnchorRepositoryImpl(
    private val tasteAnchorDao: me.avinas.vanderwaals.data.dao.TasteAnchorDao,
    private val preferenceDao: me.avinas.vanderwaals.data.dao.UserPreferenceDao
) : TasteAnchorRepository {

    override suspend fun getTasteMemory(nowMillis: Long): TasteMemory {
        val likes = tasteAnchorDao.getByKind(TasteAnchor.KIND_LIKE)
        val dislikes = tasteAnchorDao.getByKind(TasteAnchor.KIND_DISLIKE)

        val likeAnchors = likes.map { it.toMemoryAnchor() }.toMutableList()

        // Legacy continuity: surface the old EMA vector as one synthetic
        // anchor when upgrading users have no persisted anchors yet.
        if (likeAnchors.none { it.embedding.isNotEmpty() }) {
            val prefs = preferenceDao.getOnce()
            if (prefs != null) {
                if (prefs.preferenceVector.isNotEmpty()) {
                    likeAnchors.add(
                        TasteMemory.Anchor(
                            wallpaperId = TasteAnchor.LEGACY_SEED_ID,
                            embedding = prefs.preferenceVector,
                            updatedAt = prefs.lastUpdated,
                            strength = 1.0f
                        )
                    )
                }
                if (prefs.originalEmbedding.isNotEmpty()) {
                    likeAnchors.add(
                        TasteMemory.Anchor(
                            wallpaperId = TasteAnchor.LEGACY_SEED_ID + "_original",
                            embedding = prefs.originalEmbedding,
                            updatedAt = prefs.lastUpdated,
                            strength = 0.8f
                        )
                    )
                }
            }
        }

        return TasteMemory(
            positiveAnchors = likeAnchors,
            negativeAnchors = dislikes.map { it.toMemoryAnchor() },
            nowMillis = nowMillis
        )
    }

    override suspend fun recordLike(
        wallpaperId: String,
        embedding: FloatArray,
        nowMillis: Long,
        strength: Float
    ) {
        tasteAnchorDao.upsert(
            TasteAnchor(
                wallpaperId = wallpaperId,
                kind = TasteAnchor.KIND_LIKE,
                embedding = embedding,
                updatedAt = nowMillis,
                strength = strength.coerceIn(0f, 1f)
            )
        )
        tasteAnchorDao.pruneTo(
            TasteAnchor.KIND_LIKE,
            me.avinas.vanderwaals.algorithm.RecommenderConfig.MAX_POSITIVE_ANCHORS
        )
    }

    override suspend fun recordDislike(
        wallpaperId: String,
        embedding: FloatArray,
        nowMillis: Long,
        strength: Float
    ) {
        tasteAnchorDao.upsert(
            TasteAnchor(
                wallpaperId = wallpaperId,
                kind = TasteAnchor.KIND_DISLIKE,
                embedding = embedding,
                updatedAt = nowMillis,
                strength = strength.coerceIn(0f, 1f)
            )
        )
        tasteAnchorDao.pruneTo(
            TasteAnchor.KIND_DISLIKE,
            me.avinas.vanderwaals.algorithm.RecommenderConfig.MAX_NEGATIVE_ANCHORS
        )
    }

    override suspend fun removeLike(wallpaperId: String) {
        tasteAnchorDao.deleteByWallpaperId(wallpaperId)
    }

    override suspend fun clearAll() {
        tasteAnchorDao.deleteAll()
    }

    private fun TasteAnchor.toMemoryAnchor(): TasteMemory.Anchor =
        TasteMemory.Anchor(
            wallpaperId = wallpaperId,
            embedding = embedding,
            updatedAt = updatedAt,
            strength = strength
        )
}
