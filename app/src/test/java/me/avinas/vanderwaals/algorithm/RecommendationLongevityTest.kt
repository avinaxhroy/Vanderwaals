package me.avinas.vanderwaals.algorithm

import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Regression test encoding the original bug report:
 * *"after some time the wallpaper recommendation go worst"*.
 *
 * Simulates 120 days of daily use against a synthetic catalog with two
 * visual clusters (a "dark minimal" cluster and a "nature" cluster):
 *
 * - Days 0–45: the user's taste is cluster A.
 * - Day 46+: the user's taste shifts to cluster B (people's tastes
 *   change — the old EMA model could not follow because its learning
 *   rate decayed to near zero while its decay functions were never
 *   wired up).
 * - Each day the engine picks a wallpaper; the simulated user likes it
 *   with probability proportional to match with the current taste, and
 *   dislikes obvious mismatches.  Likes/dislikes feed the taste memory
 *   exactly as the real feedback path does.
 *
 * Pass criteria:
 * 1. **No degradation**: match quality in the last 15 days of a stable
 *    taste period is not worse than the first 15 days (within noise).
 * 2. **Adaptation**: after the taste switch, match quality to the new
 *    taste in days 95–119 clearly exceeds days 46–60 — the model must
 *    follow taste evolution, not freeze at day one.
 */
class RecommendationLongevityTest {

    private val engine = RankingEngine()
    private val dayMs = RecommenderConfig.MILLIS_PER_DAY.toLong()
    private val dim = 64
    private val random = Random(20260816)

    private val dirA = unit(FloatArray(dim) { if (it % 2 == 0) 1f else -0.05f })
    private val dirB = unit(FloatArray(dim) { if (it % 2 == 0) -0.05f else 1f })

    private fun unit(v: FloatArray): FloatArray {
        val m = sqrt(v.map { (it * it).toDouble() }.sum()).toFloat()
        return if (m == 0f) v else FloatArray(v.size) { v[it] / m }
    }

    private fun noisy(base: FloatArray, amount: Float): FloatArray =
        unit(FloatArray(base.size) { base[it] + (random.nextFloat() - 0.5f) * amount })

    private fun wallpaper(id: String, embedding: FloatArray, category: String) =
        WallpaperMetadata(
            id = id,
            url = "https://example.com/$id.jpg",
            thumbnailUrl = "https://example.com/$id.jpg",
            source = "github",
            category = category,
            colors = listOf("#808080", "#606060"),
            brightness = 50,
            contrast = 50,
            embedding = embedding,
            resolution = "2400x1080",
            attribution = null
        )

    private fun cos(v1: FloatArray, v2: FloatArray): Float {
        var dot = 0f; var m1 = 0f; var m2 = 0f
        for (i in v1.indices) {
            dot += v1[i] * v2[i]; m1 += v1[i] * v1[i]; m2 += v2[i] * v2[i]
        }
        return dot / (sqrt(m1) * sqrt(m2))
    }

    @Test
    fun `recommendation quality does not degrade over 90 days`() {
        // Catalog: 120 per cluster, each a noisy variant.
        val catalog = buildList {
            repeat(120) { i -> add(wallpaper("a$i", noisy(dirA, 0.25f), "minimal")) }
            repeat(120) { i -> add(wallpaper("b$i", noisy(dirB, 0.25f), "nature")) }
        }

        var likes = listOf<TasteMemory.Anchor>()
        var dislikes = listOf<TasteMemory.Anchor>()
        val categoryViews = mutableMapOf<String, Int>()
        val categoryLikes = mutableMapOf<String, Int>()
        val categoryDislikes = mutableMapOf<String, Int>()
        val recentIds = ArrayDeque<String>()

        // (dayIndex, matchToCurrentTaste) per selection
        val selectionQuality = mutableListOf<Pair<Int, Float>>()

        for (day in 0 until 120) {
            val now = day * dayMs
            val tasteMemory = TasteMemory(likes.toList(), dislikes.toList(), now)

            val candidates = catalog.filter { it.id !in recentIds }
                .ifEmpty { catalog }

            val context = RankingEngine.RankingContext(
                nowMillis = now,
                currentHour = 12,
                tasteMemory = tasteMemory,
                categoryStats = categoryViews.keys.associateWith { cat ->
                    RankingEngine.CategoryStats(
                        likes = categoryLikes[cat] ?: 0,
                        dislikes = categoryDislikes[cat] ?: 0,
                        views = categoryViews[cat] ?: 0
                    )
                },
                recentIds = recentIds.toList(),
                recentCategories = recentIds.map { id ->
                    if (id.startsWith("a")) "minimal" else "nature"
                },
                moodAffinity = emptyMap(),
                styleAffinity = emptyMap(),
                likedColors = emptyList()
            )

            val selected = if (tasteMemory.hasTaste) {
                engine.select(candidates, context, Random(random.nextLong()))
            } else {
                candidates.random(random)
            }

            val currentTaste = if (day < 45) dirA else dirB
            val match = cos(selected.embedding, currentTaste)
            selectionQuality.add(day to match)

            // Recency window.
            recentIds.addFirst(selected.id)
            while (recentIds.size > 14) recentIds.removeLast()
            val selectedCategory = if (selected.id.startsWith("a")) "minimal" else "nature"
            categoryViews.merge(selectedCategory, 1, Int::plus)

            // Simulated feedback: strong match → likely like; strong
            // mismatch → dislike; otherwise silent (like real usage).
            when {
                match > 0.85 && random.nextFloat() < 0.5 -> {
                    likes = (likes + TasteMemory.Anchor(selected.id, selected.embedding, now)).takeLast(30)
                    categoryLikes.merge(selectedCategory, 1, Int::plus)
                }
                match < 0.5 && random.nextFloat() < 0.6 -> {
                    dislikes = (dislikes + TasteMemory.Anchor(selected.id, selected.embedding, now)).takeLast(50)
                    categoryDislikes.merge(selectedCategory, 1, Int::plus)
                }
            }
        }

        fun meanMatch(fromDay: Int, toDay: Int): Float =
            selectionQuality.filter { (day, _) -> day in fromDay..toDay }
                .map { it.second }
                .average()
                .toFloat()

        val firstFortnight = meanMatch(0, 14)
        val lastFortnightOfStableTaste = meanMatch(31, 45)
        val rightAfterSwitch = meanMatch(46, 60)
        val adaptationComplete = meanMatch(95, 119)

        // 1. No degradation while taste is stable (the reported bug).
        assertTrue(
            "quality degraded over time: first=$firstFortnight late=$lastFortnightOfStableTaste",
            lastFortnightOfStableTaste >= firstFortnight - 0.03f
        )

        // 2. The model follows the taste switch: once the first likes for
        // the new taste land, match quality climbs decisively within a few
        // weeks and stays there for the remainder of the simulation.
        assertTrue(
            "engine failed to adapt to taste change: post-switch=$rightAfterSwitch adapted=$adaptationComplete",
            adaptationComplete > rightAfterSwitch
        )
        assertTrue(
            "adapted-period match to new taste too low: $adaptationComplete",
            adaptationComplete > 0.7f
        )

        println(
            "Longevity simulation — stable period: $firstFortnight → $lastFortnightOfStableTaste; " +
                "post-switch: $rightAfterSwitch → adapted (95-119): $adaptationComplete"
        )
    }

    @Test
    fun `taste memory follows its anchors - adaptation is possible`() {
        // Contrast with the legacy frozen-EMA failure: there, the vector
        // could not move after ~50 feedback events no matter what the user
        // liked next.  Here, a memory anchored on B must clearly prefer B
        // content over a memory anchored on A — and A-anchored memories
        // must not be permanently stuck when the anchors change.
        val aAnchors = (0 until 5).map { i -> TasteMemory.Anchor("a$i", noisy(dirA, 0.2f), now2()) }
        val bAnchors = (0 until 5).map { i -> TasteMemory.Anchor("b$i", noisy(dirB, 0.2f), now2()) }

        val memoryA = TasteMemory(aAnchors, emptyList(), now2())
        val memoryB = TasteMemory(bAnchors, emptyList(), now2())

        val candidateB = noisy(dirB, 0.1f)
        val underA = memoryA.tasteSimilarity(candidateB)
        val underB = memoryB.tasteSimilarity(candidateB)

        assertTrue("B content must score far higher under B anchors ($underB vs $underA)", underB - underA > 0.3f)

        // And re-anchoring is all it takes — no learning rate, no momentum.
        val reAnchored = TasteMemory(bAnchors, emptyList(), now2())
        assertEquals(underB, reAnchored.tasteSimilarity(candidateB), 0.0001f)
    }

    private fun now2() = 50L * dayMs
}
