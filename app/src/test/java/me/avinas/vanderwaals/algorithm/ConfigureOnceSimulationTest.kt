package me.avinas.vanderwaals.algorithm

import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Regression test for the second reported failure mode:
 * *"let the user configure the app only once (with small taste input) and
 * let it run — after a few days recommendations go worse and recommend
 * random things."*
 *
 * Simulates a configure-once user: five likes at onboarding (small taste
 * input around one visual cluster), then **zero feedback for 60 days**
 * while the app rotates wallpapers daily.
 *
 * Root cause this guards against: wall-clock anchor decay evaporated the
 * onboarding taste after a few half-lives, compressing the taste signal
 * until exploration and neutral components dominated the ranking —
 * experienced as random recommendations.  The fix ages anchors relative to
 * the newest feedback event, so dormant memory keeps full strength.
 *
 * Pass criteria:
 * 1. Match quality in days 46–60 is not worse than days 1–15.
 * 2. Late-period selections remain clearly on-taste (far above the
 *    catalog's random-selection baseline) — not random.
 */
class ConfigureOnceSimulationTest {

    private val engine = RankingEngine()
    private val dayMs = RecommenderConfig.MILLIS_PER_DAY.toLong()
    private val dim = 64
    private val random = Random(20260817)

    private val dirTaste = unit(FloatArray(dim) { if (it % 2 == 0) 1f else -0.05f })
    private val dirOther = unit(FloatArray(dim) { if (it % 2 == 0) -0.05f else 1f })

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
    fun `configure once then zero feedback keeps recommendations on taste`() {
        val catalog = buildList {
            repeat(120) { i -> add(wallpaper("t$i", noisy(dirTaste, 0.25f), "tasteful")) }
            repeat(120) { i -> add(wallpaper("o$i", noisy(dirOther, 0.25f), "other")) }
        }

        // Onboarding: five likes around the taste cluster, then silence.
        val onboarding = catalog.filter { it.id.startsWith("t") }.take(5)
        var anchors = onboarding.map { TasteMemory.Anchor(it.id, it.embedding, 0L) }
        val categoryStats = mapOf(
            "tasteful" to RankingEngine.CategoryStats(likes = 5, views = 5)
        )

        val selectionQuality = mutableListOf<Pair<Int, Float>>()
        val recentIds = ArrayDeque<String>()

        for (day in 1..60) {
            val now = day * dayMs
            val memory = TasteMemory(anchors, emptyList(), now)
            val candidates = catalog.filter { it.id !in recentIds }.ifEmpty { catalog }

            val context = RankingEngine.RankingContext(
                nowMillis = now,
                currentHour = 12,
                tasteMemory = memory,
                categoryStats = categoryStats,
                recentIds = recentIds.toList(),
                recentCategories = recentIds.map { if (it.startsWith("t")) "tasteful" else "other" },
                moodAffinity = emptyMap(),
                styleAffinity = emptyMap(),
                likedColors = emptyList()
            )

            val selected = engine.select(candidates, context, Random(random.nextLong()))
            val match = cos(selected.embedding, dirTaste)
            selectionQuality.add(day to match)

            recentIds.addFirst(selected.id)
            while (recentIds.size > 14) recentIds.removeLast()

            // Configure-once: NO feedback ever recorded after onboarding.
        }

        fun meanMatch(from: Int, to: Int): Float =
            selectionQuality.filter { (day, _) -> day in from..to }
                .map { it.second }.average().toFloat()

        val early = meanMatch(1, 15)
        val late = meanMatch(46, 60)
        val randomBaseline = catalog.map { cos(it.embedding, dirTaste) }.average().toFloat()

        assertTrue(
            "configure-once quality degraded: early=$early late=$late",
            late >= early - 0.03f
        )
        assertTrue(
            "late recommendations near random baseline (late=$late, baseline=$randomBaseline)",
            late > randomBaseline + 0.3f
        )
        assertTrue("late recommendations must stay on-taste (late=$late)", late > 0.7f)

        println("Configure-once simulation — early=$early, late=$late, randomBaseline=$randomBaseline")
    }

    @Test
    fun `dormant engine still suppresses onboarding dislikes`() {
        // Configure-once also covers dislikes: the user rejected a few
        // wallpapers at onboarding and those rejections must persist.
        val onboardingDislike = TasteMemory.Anchor(
            "o1", noisy(dirOther, 0.1f), 0L, 1.0f
        )
        val likes = (0 until 3).map { i ->
            TasteMemory.Anchor("t$i", noisy(dirTaste, 0.1f), 0L, 1.0f)
        }
        val memory = TasteMemory(likes, listOf(onboardingDislike), 90L * dayMs)

        val dislikedCandidate = noisy(dirOther, 0.1f)
        val likedCandidate = noisy(dirTaste, 0.1f)

        assertTrue(memory.tasteSimilarity(likedCandidate) > memory.tasteSimilarity(dislikedCandidate))
        // 90 days later, with zero new feedback, the dislike still bites.
        assertTrue(memory.dislikedItemFactor("o1") > 0.9f)
    }
}
