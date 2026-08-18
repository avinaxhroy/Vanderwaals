package me.avinas.vanderwaals.algorithm

import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Unit tests for [RankingEngine] — the single calibrated ranking path.
 *
 * Verifies the invariants the rebuild promised:
 * - components are normalised, weights calibrated (winner beats loser)
 * - every signal enters the score exactly once, bounded
 * - exploration decays with evidence instead of dice rolls
 * - saturation is single and gentle
 * - dislikes suppress without steering the positive direction
 */
class RankingEngineTest {

    private val engine = RankingEngine()
    private val dayMs = RecommenderConfig.MILLIS_PER_DAY.toLong()
    private val now = 100L * dayMs

    private fun unit(v: FloatArray): FloatArray {
        val m = sqrt(v.map { (it * it).toDouble() }.sum()).toFloat()
        return if (m == 0f) v else FloatArray(v.size) { v[it] / m }
    }

    private val dim = 64
    private val dirA = unit(FloatArray(dim) { if (it % 2 == 0) 1f else -0.1f })
    private val dirB = unit(FloatArray(dim) { if (it % 2 == 0) -0.1f else 1f })

    private fun wallpaper(
        id: String,
        embedding: FloatArray = FloatArray(0),
        category: String = "nature",
        brightness: Int = 50,
        colors: List<String> = listOf("#808080")
    ) = WallpaperMetadata(
        id = id,
        url = "https://example.com/$id.jpg",
        thumbnailUrl = "https://example.com/$id.jpg",
        source = "github",
        category = category,
        colors = colors,
        brightness = brightness,
        contrast = 50,
        embedding = embedding,
        resolution = "1920x1080",
        attribution = null
    )

    private fun taste(likes: List<TasteMemory.Anchor>, dislikes: List<TasteMemory.Anchor> = emptyList()) =
        TasteMemory(likes, dislikes, now)

    private fun context(
        memory: TasteMemory,
        categoryStats: Map<String, RankingEngine.CategoryStats> = emptyMap(),
        recentCategories: List<String> = emptyList(),
        hour: Int = 12
    ) = RankingEngine.RankingContext(
        nowMillis = now,
        currentHour = hour,
        tasteMemory = memory,
        categoryStats = categoryStats,
        recentIds = emptyList(),
        recentCategories = recentCategories,
        moodAffinity = emptyMap(),
        styleAffinity = emptyMap(),
        likedColors = emptyList()
    )

    @Test
    fun `wallpaper matching taste outranks unrelated wallpaper`() {
        val memory = taste(
            listOf(TasteMemory.Anchor("liked", dirA, now))
        )
        val match = wallpaper("match", dirA.map { it + 0.05f }.toFloatArray())
        val other = wallpaper("other", dirB.map { it + 0.05f }.toFloatArray())

        val ranked = engine.rank(listOf(other, match), context(memory))

        assertEquals("match", ranked.first().wallpaper.id)
        assertTrue(ranked.first().finalScore - ranked.last().finalScore > 0.1f)
    }

    @Test
    fun `final scores are bounded and components normalised`() {
        val memory = taste(listOf(TasteMemory.Anchor("liked", dirA, now)))
        val candidates = listOf(
            wallpaper("a", dirA), wallpaper("b", dirB),
            wallpaper("noembed"), wallpaper("c", dirA, brightness = 95)
        )
        val ranked = engine.rank(candidates, context(memory))

        ranked.forEach { scored ->
            assertTrue("finalScore ${scored.finalScore} out of range", scored.finalScore in 0f..1.2f)
            listOf(
                scored.tasteScore, scored.categoryScore, scored.qualityScore,
                scored.colorScore, scored.semanticScore, scored.timeOfDayScore
            ).forEach { component ->
                assertTrue("component $component out of [0,1]", component in 0f..1f)
            }
            assertTrue(scored.saturationSuppression in 0f..1f)
            assertTrue(scored.dislikeSuppression in 0f..1f)
            assertTrue(scored.explorationBonus >= 0f)
        }
    }

    @Test
    fun `wallpaper without embedding stays neutral not buried`() {
        val memory = taste(listOf(TasteMemory.Anchor("liked", dirA, now)))
        val noEmbed = wallpaper("novector", FloatArray(0))
        val scored = engine.rank(listOf(noEmbed), context(memory)).first()
        assertEquals(RecommenderConfig.NEUTRAL_SCORE, scored.tasteScore, 0.0001f)
    }

    @Test
    fun `category affinity is bayesian and liked beats hated`() {
        val memory = taste(listOf(TasteMemory.Anchor("liked", dirA, now)))
        val ctx = context(
            memory,
            categoryStats = mapOf(
                "nature" to RankingEngine.CategoryStats(likes = 9, dislikes = 1, views = 10),
                "city" to RankingEngine.CategoryStats(likes = 0, dislikes = 10, views = 10)
            )
        )
        val ranked = engine.rank(
            listOf(
                wallpaper("u", dirA, category = "city"),
                wallpaper("n", dirA, category = "nature")
            ),
            ctx
        )
        assertEquals("n", ranked[0].wallpaper.id)
        assertTrue(ranked[0].finalScore - ranked[1].finalScore > 0.05f)
    }

    @Test
    fun `unknown category exploration is bounded and decays with feedback`() {
        val memory = taste(listOf(TasteMemory.Anchor("liked", dirA, now)))

        val noFeedback = context(
            memory, categoryStats = mapOf(
                "nature" to RankingEngine.CategoryStats(likes = 9, dislikes = 1)
            )
        )
        val matureFeedback = context(
            memory, categoryStats = mapOf(
                "nature" to RankingEngine.CategoryStats(likes = 9, dislikes = 1),
                "rare" to RankingEngine.CategoryStats(likes = 4, dislikes = 2)
            )
        )

        val rare = wallpaper("r", dirA, category = "rare")
        val freshBonus = engine.rank(listOf(rare), noFeedback).first().explorationBonus
        val matureBonus = engine.rank(listOf(rare), matureFeedback).first().explorationBonus

        // Bonus exists for the unproven category, is hard-capped…
        assertTrue(freshBonus > 0.05f)
        assertTrue("bonus exceeds cap: $freshBonus", freshBonus <= RecommenderConfig.EXPLORATION_MAX_BONUS + 0.001f)
        // …and decays once feedback accumulates.
        assertTrue(freshBonus > matureBonus)
    }

    @Test
    fun `saturation is single and gently bounded`() {
        val memory = taste(listOf(TasteMemory.Anchor("liked", dirA, now)))
        val saturated = context(
            memory,
            recentCategories = List(RecommenderConfig.SATURATION_WINDOW) { "nature" }
        )
        val clean = context(memory)

        val w = wallpaper("w", dirA, category = "nature")
        val suppressed = engine.rank(listOf(w), saturated).first()
        val normal = engine.rank(listOf(w), clean).first()

        // Max suppression is 15% — the legacy stack removed up to 40%+.
        assertTrue(suppressed.saturationSuppression >= 1f - RecommenderConfig.SATURATION_MAX_SUPPRESSION - 0.001f)
        assertEquals(1f, normal.saturationSuppression, 0.0001f)
        assertTrue("penalty too harsh", normal.finalScore - suppressed.finalScore <= RecommenderConfig.SATURATION_MAX_SUPPRESSION + 0.01f)
    }

    @Test
    fun `disliked item is suppressed but not excluded`() {
        val memory = taste(
            likes = listOf(TasteMemory.Anchor("liked", dirA, now)),
            dislikes = listOf(TasteMemory.Anchor("bad", dirB, now))
        )
        val bad = wallpaper("bad", dirB)
        val similarToBad = wallpaper("similar", dirB.map { it + 0.02f }.toFloatArray())

        val ranked = engine.rank(listOf(bad, similarToBad), context(memory))

        assertTrue(ranked.first().dislikeSuppression < 1f)
        assertTrue("suppression must be bounded, got ${ranked.first().dislikeSuppression}",
            ranked.first().dislikeSuppression >= 1f - RecommenderConfig.DISLIKE_CENTROID_SUPPRESSION - 0.01f)
    }

    @Test
    fun `stale dislike fades once newer evidence arrives`() {
        val w = wallpaper("bad", dirB)
        val newerLike = TasteMemory.Anchor("liked", dirA, now)

        val freshDislike = TasteMemory.Anchor("bad", dirB, now)
        val oldDislike = TasteMemory.Anchor(
            "bad", dirB, now - (4 * RecommenderConfig.NEGATIVE_HALF_LIFE_DAYS * dayMs).toLong()
        )

        val freshMemory = taste(listOf(newerLike), listOf(freshDislike))
        val oldMemory = taste(listOf(newerLike), listOf(oldDislike))

        val freshScore = engine.rank(listOf(w), context(freshMemory)).first().finalScore
        val oldScore = engine.rank(listOf(w), context(oldMemory)).first().finalScore

        // The newer like advances the reference clock, so the 4-half-life-old
        // dislike suppresses far less than a same-day one.
        assertTrue("stale dislike should suppress less ($freshScore vs $oldScore)", oldScore > freshScore)
    }

    @Test
    fun `mmr avoids embedding clones among equally relevant candidates`() {
        val memory = taste(listOf(TasteMemory.Anchor("liked", dirA, now)))
        // Two exact clones (highest relevance, identical embeddings) plus
        // four slightly noisier variants that are mutually distinct.
        val clone1 = wallpaper("clone1", dirA.copyOf())
        val clone2 = wallpaper("clone2", dirA.copyOf())
        val variants = (0 until 4).map { i ->
            wallpaper("v$i", dirA.map { it + (i + 1) * 0.05f + 0.03f }.toFloatArray())
        }
        val all = listOf(clone1, clone2) + variants

        val ranked = engine.rank(all, context(memory))
        val pool = engine.selectWithMmr(ranked, 5)

        val cloneCount = pool.count { it.wallpaper.id.startsWith("clone") }
        assertTrue(
            "MMR selected both clones (pool=${pool.map { it.wallpaper.id }}); expected at most one",
            cloneCount <= 1
        )
    }

    @Test
    fun `selection is deterministic for identical seed`() {
        val memory = taste((0 until 5).map { TasteMemory.Anchor("l$it", if (it % 2 == 0) dirA else dirB, now) })
        val candidates = (0 until 20).map { i ->
            wallpaper("w$i", (if (i % 2 == 0) dirA else dirB).map { v -> v + i * 0.01f }.toFloatArray())
        }
        val ctx = context(memory)

        val first = engine.select(candidates, ctx, Random(42))
        val second = engine.select(candidates, ctx, Random(42))
        assertEquals(first.id, second.id)
    }

    @Test
    fun `empty candidate list throws`() {
        try {
            engine.select(emptyList(), context(taste(emptyList())), Random(1))
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `quality score has no source bias`() {
        val bing = wallpaper("b", dirA).copy(source = "bing", resolution = "1920x1080")
        val github = wallpaper("g", dirA).copy(source = "github", resolution = "1920x1080")
        val delta = kotlin.math.abs(engine.qualityScore(bing) - engine.qualityScore(github))
        assertEquals(0f, delta, 0.0001f)
    }

    @Test
    fun `time of day prefers dark at night and bright in morning`() {
        val dark = engine.timeOfDayFit(20, 2)
        val brightAtNight = engine.timeOfDayFit(90, 2)
        val brightMorning = engine.timeOfDayFit(90, 7)
        val darkMorning = engine.timeOfDayFit(20, 7)
        assertTrue(dark > brightAtNight)
        assertTrue(brightMorning > darkMorning)
    }

    @Test
    fun `cold start score bounded and varies per device`() {
        val w = wallpaper("w", dirA)
        val s1 = engine.coldStartScore(w, 1)
        val s2 = engine.coldStartScore(w, 2)
        assertTrue(s1 in 0f..1.1f)
        assertTrue(kotlin.math.abs(s1 - s2) <= 0.051f)
    }
}
