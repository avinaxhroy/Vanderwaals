package me.avinas.vanderwaals.algorithm

import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import org.junit.Test
import org.junit.Assert.*
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Unit tests for [SimilarityCalculator].
 *
 * Verifies:
 * - Cosine similarity normalisation ([0, 1] range)
 * - Composite similarity scoring with correct weights (renormalised to 1.0)
 * - Colour similarity via CIE76 ΔE (LAB space)
 * - Category bonus (brightness / contrast / category match)
 * - Perfect-match score equals 1.0 (H1 fix)
 * - Dislike penalty reduces score
 * - Empty / mismatched embedding handling
 */
class SimilarityCalculatorTest {

    private val calculator = SimilarityCalculator()

    private fun magnitude(v: FloatArray): Float =
        sqrt(v.sumOf { (it * it).toDouble() }).toFloat()

    private fun normalize(v: FloatArray): FloatArray {
        val m = magnitude(v)
        return if (m == 0f) v else FloatArray(v.size) { v[it] / m }
    }

    private fun wallpaper(
        id: String = "w1",
        embedding: FloatArray = FloatArray(1280) { 0f },
        colors: List<String> = listOf("#FF0000"),
        category: String = "nature",
        brightness: Int = 50,
        contrast: Int = 50
    ): WallpaperMetadata = WallpaperMetadata(
        id = id,
        url = "https://example.com/$id.jpg",
        thumbnailUrl = "https://example.com/$id.jpg",
        source = "github",
        category = category,
        colors = colors,
        brightness = brightness,
        contrast = contrast,
        embedding = embedding,
        resolution = "1920x1080",
        attribution = null
    )

    // ── Cosine similarity ─────────────────────────────────────────────────────

    @Test
    fun cosineSimilarityIdenticalVectorsIsOne() {
        val v = normalize(FloatArray(1280) { it.toFloat() })
        val sim = calculator.calculateSimilarity(v, v)
        assertEquals(1.0f, sim, 0.001f)
    }

    @Test
    fun cosineSimilarityOrthogonalVectorsIsZeroFive() {
        // cosine [-1,1] → [0,1], so orthogonal (cos=0) → 0.5
        val v1 = floatArrayOf(1f, 0f, 0f)
        val v2 = floatArrayOf(0f, 1f, 0f)
        val sim = calculator.calculateSimilarity(v1, v2)
        assertEquals(0.5f, sim, 0.001f)
    }

    @Test
    fun cosineSimilarityOppositeVectorsIsZero() {
        val v1 = floatArrayOf(1f, 0f, 0f)
        val v2 = floatArrayOf(-1f, 0f, 0f)
        val sim = calculator.calculateSimilarity(v1, v2)
        assertEquals(0.0f, sim, 0.001f)
    }

    @Test
    fun cosineSimilarityEmptyEmbeddingReturnsZero() {
        val sim = calculator.calculateSimilarity(FloatArray(0), FloatArray(1280))
        assertEquals(0f, sim, 0.0001f)
    }

    @Test
    fun cosineSimilaritySizeMismatchReturnsZero() {
        val sim = calculator.calculateSimilarity(floatArrayOf(1f, 0f), floatArrayOf(1f, 0f, 0f))
        assertEquals(0f, sim, 0.0001f)
    }

    // ── Composite similarity ──────────────────────────────────────────────────

    @Test
    fun perfectMatchScoresOne() {
        // H1 fix: composite score should be 1.0 for a perfect match (not 0.89)
        val emb = normalize(FloatArray(1280) { 1f })
        val w = wallpaper(embedding = emb, colors = listOf("#FF0000"), category = "nature", brightness = 50, contrast = 50)
        val score = calculator.calculateCompositeSimilarity(
            userEmbedding = emb,
            userColors = listOf("#FF0000"),
            userCategory = "nature",
            userBrightness = 50,
            userContrast = 50,
            wallpaper = w
        )
        assertEquals(1.0f, score, 0.01f)
    }

    @Test
    fun compositeScoreInRange() {
        val emb = normalize(FloatArray(1280) { it.toFloat() })
        val w = wallpaper(embedding = normalize(FloatArray(1280) { (it + 100).toFloat() }))
        val score = calculator.calculateCompositeSimilarity(
            userEmbedding = emb,
            userColors = listOf("#00FF00"),
            userCategory = "abstract",
            userBrightness = 30,
            userContrast = 70,
            wallpaper = w
        )
        assertTrue("Score should be in [0, 1]", score in 0f..1f)
    }

    @Test
    fun dislikePenaltyReducesScore() {
        val emb = normalize(FloatArray(1280) { 1f })
        val w = wallpaper(embedding = emb, colors = listOf("#FF0000"), category = "nature", brightness = 50, contrast = 50)
        val disliked = emb // Same as wallpaper → maximum penalty

        val scoreNoPenalty = calculator.calculateCompositeSimilarity(
            userEmbedding = emb, userColors = listOf("#FF0000"), userCategory = "nature",
            userBrightness = 50, userContrast = 50, wallpaper = w
        )
        val scoreWithPenalty = calculator.calculateCompositeSimilarity(
            userEmbedding = emb, userColors = listOf("#FF0000"), userCategory = "nature",
            userBrightness = 50, userContrast = 50, wallpaper = w, dislikedEmbedding = disliked
        )

        assertTrue("Penalty should reduce score", scoreWithPenalty < scoreNoPenalty)
    }

    // ── Colour similarity (LAB ΔE) ────────────────────────────────────────────

    @Test
    fun identicalColorsHaveMaxSimilarity() {
        val emb = normalize(FloatArray(1280) { 1f })
        val w = wallpaper(embedding = emb, colors = listOf("#AABBCC"))
        val score = calculator.calculateCompositeSimilarity(
            userEmbedding = emb,
            userColors = listOf("#AABBCC"),
            userCategory = "nature",
            userBrightness = 50,
            userContrast = 50,
            wallpaper = w
        )
        // Perfect embedding + perfect color + perfect category → 1.0
        assertEquals(1.0f, score, 0.01f)
    }

    @Test
    fun emptyColorsGiveNeutralScore() {
        val emb = normalize(FloatArray(1280) { 1f })
        val w = wallpaper(embedding = emb, colors = emptyList())
        val score = calculator.calculateCompositeSimilarity(
            userEmbedding = emb,
            userColors = emptyList(),
            userCategory = null,
            userBrightness = 50,
            userContrast = 50,
            wallpaper = w
        )
        // Neutral color (0.5) + neutral category → embedding only / renormalised
        assertTrue("Score should be in [0, 1]", score in 0f..1f)
    }

    // ── Category bonus ────────────────────────────────────────────────────────

    @Test
    fun matchingCategoryScoresHigherThanMismatch() {
        val emb = normalize(FloatArray(1280) { 1f })
        val wMatch = wallpaper(embedding = emb, category = "nature", brightness = 50, contrast = 50)
        val wMismatch = wallpaper(embedding = emb, category = "abstract", brightness = 50, contrast = 50)

        val scoreMatch = calculator.calculateCompositeSimilarity(
            userEmbedding = emb, userColors = listOf("#FF0000"), userCategory = "nature",
            userBrightness = 50, userContrast = 50, wallpaper = wMatch
        )
        val scoreMismatch = calculator.calculateCompositeSimilarity(
            userEmbedding = emb, userColors = listOf("#FF0000"), userCategory = "nature",
            userBrightness = 50, userContrast = 50, wallpaper = wMismatch
        )
        assertTrue("Matching category should score higher", scoreMatch >= scoreMismatch)
    }
}
