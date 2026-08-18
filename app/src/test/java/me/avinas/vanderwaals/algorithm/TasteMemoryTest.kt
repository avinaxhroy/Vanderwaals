package me.avinas.vanderwaals.algorithm

import me.avinas.vanderwaals.data.entity.TasteAnchor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * Unit tests for [TasteMemory] — the multi-anchor replacement for the
 * single EMA preference vector.
 */
class TasteMemoryTest {

    private val dayMs = RecommenderConfig.MILLIS_PER_DAY.toLong()
    private val now = 100L * dayMs

    private fun unit(v: FloatArray): FloatArray {
        val m = sqrt(v.map { (it * it).toDouble() }.sum()).toFloat()
        return if (m == 0f) v else FloatArray(v.size) { v[it] / m }
    }

    private fun dim(): Int = 64

    /** Two near-orthogonal random-ish directions (deterministic). */
    private fun directionA(): FloatArray = unit(FloatArray(dim()) { if (it % 2 == 0) 1f else -0.1f })

    private fun directionB(): FloatArray = unit(FloatArray(dim()) { if (it % 2 == 0) -0.1f else 1f })

    private fun like(
        id: String,
        embedding: FloatArray,
        ageDays: Double = 0.0,
        strength: Float = 1.0f
    ) = TasteMemory.Anchor(
        wallpaperId = id,
        embedding = embedding,
        updatedAt = now - (ageDays * dayMs).toLong(),
        strength = strength
    )

    @Test
    fun `empty memory has no taste and scores neutral`() {
        val memory = TasteMemory(emptyList(), emptyList(), now)
        assertFalse(memory.hasTaste)
        assertEquals(RecommenderConfig.NEUTRAL_SCORE, memory.tasteSimilarity(directionA()), 0.0001f)
        assertNull(memory.dislikeCentroid())
        assertNull(memory.lastLikedAt("anything"))
    }

    @Test
    fun `candidate near one anchor scores high regardless of other taste`() {
        val a = directionA()
        val b = directionB()
        val memory = TasteMemory(
            positiveAnchors = listOf(like("likedA", a), like("likedB", b)),
            negativeAnchors = emptyList(),
            nowMillis = now
        )
        assertTrue(memory.hasTaste)

        val nearA = unit(a.map { it + 0.05f }.toFloatArray())
        val unrelated = unit(FloatArray(dim()) { 0f })

        val simA = memory.tasteSimilarity(nearA)
        val simNone = memory.tasteSimilarity(unrelated)

        // Multi-taste: matching EITHER anchor is enough for a strong score.
        assertTrue("expected simA=$simA to be well above unrelated=$simNone", simA > 0.5f)
        assertTrue("expected a clear gap (simA=$simA, none=$simNone)", simA - simNone > 0.2f)
    }

    @Test
    fun `dormant memory keeps full strength regardless of wall clock`() {
        // Configure-once contract: no new feedback → anchors do not decay.
        val a = directionA()
        val onboarding = TasteMemory(listOf(like("seed", a)), emptyList(), now)
        val oneYearLater = TasteMemory(listOf(like("seed", a)), emptyList(), now + 365L * dayMs)

        val candidate = unit(a.map { it + 0.05f }.toFloatArray())
        val freshScore = onboarding.tasteSimilarity(candidate)
        val yearLaterScore = oneYearLater.tasteSimilarity(candidate)

        assertEquals("dormant memory must not decay by wall clock", freshScore, yearLaterScore, 0.0001f)
        assertTrue("on-taste candidate must stay strong ($yearLaterScore)", yearLaterScore > 0.6f)
    }

    @Test
    fun `newer like ages older anchors relative to it`() {
        val a = directionA()
        val b = directionB()
        val withNewEvidence = TasteMemory(
            listOf(
                like("old", a, ageDays = RecommenderConfig.POSITIVE_HALF_LIFE_DAYS),
                like("new", b, ageDays = 0.0)
            ),
            emptyList(),
            now
        )
        val allFresh = TasteMemory(
            listOf(like("old", a, ageDays = 0.0), like("new", b, ageDays = 0.0)),
            emptyList(),
            now
        )

        val candidateNearOld = unit(a.map { it + 0.05f }.toFloatArray())
        val agedScore = withNewEvidence.tasteSimilarity(candidateNearOld)
        val freshScore = allFresh.tasteSimilarity(candidateNearOld)

        assertTrue("newer evidence must age the old anchor ($agedScore vs $freshScore)", agedScore < freshScore)
        assertTrue(agedScore < 0.75f)
    }

    @Test
    fun `implicit event does not age explicit evidence`() {
        val a = directionA()
        val onboardingDay = 10L * dayMs
        val anchors = listOf(
            TasteMemory.Anchor("seed", a, onboardingDay, strength = 1.0f)
        )
        val implicitDislike = TasteMemory.Anchor(
            "casual", directionB(), onboardingDay + 30L * dayMs, strength = RecommenderConfig.IMPLICIT_FEEDBACK_STRENGTH
        )

        val withoutImplicit = TasteMemory(anchors, emptyList(), onboardingDay + 30L * dayMs)
        val withImplicit = TasteMemory(anchors, listOf(implicitDislike), onboardingDay + 30L * dayMs)

        val candidate = unit(a.map { it + 0.05f }.toFloatArray())
        assertEquals(
            "a casual implicit dislike must not age onboarding taste",
            withoutImplicit.tasteSimilarity(candidate),
            withImplicit.tasteSimilarity(candidate),
            0.0001f
        )
    }

    @Test
    fun `disliked item factor persists while dormant and fades with new evidence`() {
        val a = directionA()
        val b = directionB()
        val dormant = TasteMemory(
            emptyList(),
            listOf(TasteMemory.Anchor("bad", a, now - 60L * dayMs, 1.0f)),
            now
        )
        // No newer events → the 60-day-old dislike still suppresses fully.
        assertEquals(1.0f, dormant.dislikedItemFactor("bad"), 0.0001f)

        val withNewerEvidence = TasteMemory(
            listOf(TasteMemory.Anchor("recentLike", b, now, 1.0f)),
            listOf(TasteMemory.Anchor("bad", a, now - 28L * dayMs, 1.0f)),
            now
        )
        // 28 days ≈ 4 negative half-lives → factor ≈ 0.0625.
        assertEquals(0.0625f, withNewerEvidence.dislikedItemFactor("bad"), 0.01f)
        assertEquals(0f, withNewerEvidence.dislikedItemFactor("unknown"), 0.0001f)
    }

    @Test
    fun `liked reshow factor is strongest right after the like`() {
        val a = directionA()
        val memory = TasteMemory(listOf(like("fav", a)), emptyList(), now)
        // Only event is the like itself → relative age 0 → full cooldown.
        assertEquals(1.0f, memory.likedReshowFactor("fav"), 0.0001f)
        assertEquals(0f, memory.likedReshowFactor("other"), 0.0001f)
    }

    @Test
    fun `anchor with empty embedding is only a cooldown marker`() {
        val memory = TasteMemory(
            positiveAnchors = listOf(like("novector", FloatArray(0))),
            negativeAnchors = emptyList(),
            nowMillis = now
        )
        assertFalse(memory.hasTaste)
        assertNotNull(memory.lastLikedAt("novector"))
    }

    @Test
    fun `dislike centroid points at disliked direction and decays`() {
        val dislikedDir = directionA()
        val recentDislike = TasteMemory.Anchor("d1", dislikedDir, now, 1.0f)
        val oldDislike = TasteMemory.Anchor(
            "d2", directionB(), now - (RecommenderConfig.NEGATIVE_HALF_LIFE_DAYS * dayMs).toLong(), 1.0f
        )
        val memory = TasteMemory(emptyList(), listOf(recentDislike, oldDislike), now)

        val centroid = memory.dislikeCentroid()
        assertNotNull(centroid)

        // Centroid must be much closer to the fresh dislike than the aged one.
        fun cos(v1: FloatArray, v2: FloatArray): Float {
            var dot = 0f; var m1 = 0f; var m2 = 0f
            for (i in v1.indices) {
                dot += v1[i] * v2[i]; m1 += v1[i] * v1[i]; m2 += v2[i] * v2[i]
            }
            return dot / (sqrt(m1) * sqrt(m2))
        }
        assertTrue(cos(centroid!!, dislikedDir) > 0.75f)
        assertEquals(now, memory.lastDislikedAt("d1"))
        assertEquals(1.0f, memory.dislikeStrength("d1"), 0.0001f)
        assertNull(memory.lastDislikedAt("unknown"))
    }

    @Test
    fun `positive centroid is unit length`() {
        val memory = TasteMemory(
            positiveAnchors = listOf(like("a", directionA()), like("b", directionB(), ageDays = 3.0)),
            negativeAnchors = emptyList(),
            nowMillis = now
        )
        val centroid = memory.positiveCentroid()
        assertEquals(directionA().size, centroid.size)
        val magnitude = sqrt(centroid.map { (it * it).toDouble() }.sum())
        assertEquals(1.0, magnitude, 0.001)
    }

    @Test
    fun `implicit strength scales taste similarity`() {
        val a = directionA()
        val explicit = TasteMemory(listOf(like("e", a, strength = 1.0f)), emptyList(), now)
        val implicit = TasteMemory(listOf(like("i", a, strength = RecommenderConfig.IMPLICIT_FEEDBACK_STRENGTH)), emptyList(), now)

        val candidate = unit(a.map { it + 0.05f }.toFloatArray())
        assertTrue(explicit.tasteSimilarity(candidate) > implicit.tasteSimilarity(candidate))
    }

    @Test
    fun `taste similarity is bounded to zero one`() {
        val memory = TasteMemory(
            positiveAnchors = listOf(like("a", directionA()), like("b", directionB())),
            negativeAnchors = emptyList(),
            nowMillis = now
        )
        val scores = listOf(directionA(), directionB(), FloatArray(dim()) { 0.2f }, FloatArray(dim()) { -1f })
            .map { memory.tasteSimilarity(it) }
        scores.forEach {
            assertTrue(it in 0f..1f)
        }
    }

    @Test
    fun `entity anchor round trips to memory anchor fields`() {
        val entity = TasteAnchor(
            wallpaperId = "w",
            kind = TasteAnchor.KIND_LIKE,
            embedding = directionA(),
            updatedAt = 42L,
            strength = 0.4f
        )
        assertEquals(TasteAnchor.KIND_LIKE, entity.kind)
        assertEquals(42L, entity.updatedAt)
        assertEquals(0.4f, entity.strength, 0.0001f)
        assertEquals(entity, entity.copy())
    }
}
