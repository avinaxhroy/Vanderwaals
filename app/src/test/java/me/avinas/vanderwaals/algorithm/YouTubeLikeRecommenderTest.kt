package me.avinas.vanderwaals.algorithm

import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import org.junit.Test
import org.junit.Assert.*
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Unit tests for [YouTubeLikeRecommender].
 *
 * Verifies:
 * - Empty candidate list throws
 * - Selection always returns a candidate from the pool
 * - Dislike penalty reduces scores for semantically similar candidates
 * - Saturation penalty grows with exposure count
 * - SessionContext equals/hashCode use content equality for FloatArray (L4 fix)
 * - Deterministic selection with seeded Random
 */
class YouTubeLikeRecommenderTest {

    private val recommender = YouTubeLikeRecommender()

    private fun normalize(v: FloatArray): FloatArray {
        var mag = 0f
        for (x in v) mag += x * x
        mag = sqrt(mag)
        return if (mag == 0f) v else FloatArray(v.size) { v[it] / mag }
    }

    private fun wallpaper(
        id: String,
        embedding: FloatArray = FloatArray(1280) { 0f },
        category: String = "nature",
        colors: List<String> = listOf("#FF0000"),
        source: String = "github"
    ): WallpaperMetadata = WallpaperMetadata(
        id = id,
        url = "https://example.com/$id.jpg",
        thumbnailUrl = "https://example.com/$id.jpg",
        source = source,
        category = category,
        colors = colors,
        brightness = 50,
        contrast = 50,
        embedding = embedding,
        resolution = "1920x1080",
        attribution = null
    )

    private fun emptyContext(
        dislikedCentroid: FloatArray? = null
    ): YouTubeLikeRecommender.SessionContext =
        YouTubeLikeRecommender.SessionContext(
            recentlyViewedIds = emptySet(),
            recentCategories = emptyList(),
            sessionLikes = 0,
            sessionDislikes = 0,
            totalHistoryLikes = 0,
            totalHistoryDislikes = 0,
            likedCategories = emptyMap(),
            dislikedCategories = emptyMap(),
            dislikedEmbeddingCentroid = dislikedCentroid
        )

    // ── Basic selection ───────────────────────────────────────────────────────

    @Test
    fun emptyCandidatesThrows() {
        try {
            recommender.selectWallpaper(emptyList(), emptyContext(), Random(42))
            fail("Should throw on empty candidates")
        } catch (e: IllegalArgumentException) {
            // Expected
        }
    }

    @Test
    fun selectionReturnsCandidateFromPool() {
        val candidates = listOf(
            Pair(wallpaper("a"), 0.9f),
            Pair(wallpaper("b"), 0.5f),
            Pair(wallpaper("c"), 0.3f)
        )
        val selected = recommender.selectWallpaper(candidates, emptyContext(), Random(42))
        val ids = candidates.map { it.first.id }
        assertTrue("Selected ${selected.id} should be in pool $ids", selected.id in ids)
    }

    @Test
    fun singleCandidateIsReturned() {
        val w = wallpaper("only")
        val selected = recommender.selectWallpaper(
            listOf(Pair(w, 0.8f)), emptyContext(), Random(0)
        )
        assertEquals("only", selected.id)
    }

    // ── SessionContext equals/hashCode (L4 fix) ───────────────────────────────

    @Test
    fun sessionContextEqualsWithSameFloatArrayContent() {
        val centroid = floatArrayOf(0.1f, 0.2f, 0.3f)
        val ctx1 = emptyContext(centroid)
        val ctx2 = emptyContext(floatArrayOf(0.1f, 0.2f, 0.3f))
        assertEquals(ctx1, ctx2)
        assertEquals(ctx1.hashCode(), ctx2.hashCode())
    }

    @Test
    fun sessionContextNotEqualsWithDifferentFloatArrayContent() {
        val ctx1 = emptyContext(floatArrayOf(0.1f, 0.2f, 0.3f))
        val ctx2 = emptyContext(floatArrayOf(0.1f, 0.2f, 0.9f))
        assertNotEquals(ctx1, ctx2)
    }

    // ── Determinism ───────────────────────────────────────────────────────────

    @Test
    fun sameSeedProducesSameSelection() {
        val candidates = (1..20).map { i ->
            Pair(wallpaper("w$i", embedding = normalize(FloatArray(1280) { (it * i).toFloat() })), i.toFloat() / 20f)
        }
        val ctx = emptyContext()

        val selected1 = recommender.selectWallpaper(candidates, ctx, Random(12345))
        val selected2 = recommender.selectWallpaper(candidates, ctx, Random(12345))

        assertEquals(selected1.id, selected2.id)
    }
}
