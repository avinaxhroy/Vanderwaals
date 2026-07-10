package me.avinas.vanderwaals.core.crop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure [CropEngine] crop mathematics.
 *
 * Pure JVM JUnit4 — no Android, no Robolectric. Synthetic ARGB images are
 * built with [px] and fed directly to the engine.
 */
class CropEngineTest {

    /** Pack an RGB triple into an ARGB pixel (alpha = 255). */
    private fun px(r: Int, g: Int, b: Int): Int =
        (0xff shl 24) or (r shl 16) or (g shl 8) or b

    /** Fill a [w]*[h] image with [value] everywhere. */
    private fun solid(w: Int, h: Int, value: Int): IntArray = IntArray(w * h) { value }

    /** A [w]*[h] dark image with a [sq]*[sq] bright square centred at (cx,cy). */
    private fun brightSquare(w: Int, h: Int, cx: Int, cy: Int, sq: Int, bright: Int, dark: Int): IntArray {
        val img = solid(w, h, px(dark, dark, dark))
        val half = sq / 2
        for (y in (cy - half) until (cy - half + sq)) {
            if (y < 0 || y >= h) continue
            for (x in (cx - half) until (cx - half + sq)) {
                if (x < 0 || x >= w) continue
                img[y * w + x] = px(bright, bright, bright)
            }
        }
        return img
    }

    // =====================================================================================
    // Statistics
    // =====================================================================================

    @Test
    fun `analyze uniform image has zero entropy and contrast`() {
        val stats = CropEngine.analyze(solid(20, 20, px(128, 128, 128)), 20, 20)
        assertEquals(0f, stats.entropy, 1e-3f)
        assertEquals(0f, stats.contrast, 1e-3f)
        assertEquals(128f, stats.averageBrightness, 1f)
    }

    @Test
    fun `analyze two-tone image has high contrast and nonzero entropy`() {
        val img = IntArray(40 * 40)
        for (i in img.indices) img[i] = if (i % 2 == 0) px(0, 0, 0) else px(255, 255, 255)
        val stats = CropEngine.analyze(img, 40, 40)
        assertTrue("contrast=${stats.contrast}", stats.contrast > 0.9f)
        // 50/50 two-tone = exactly 1 bit of entropy; normalised by log2(256)=8 -> ~0.125.
        // The point is that it is clearly nonzero (a uniform image is 0, see other test).
        assertTrue("entropy=${stats.entropy}", stats.entropy in 0.1f..0.15f)
    }

    @Test
    fun `shannonEntropy of even distribution approaches 1`() {
        val total = 256 * 10
        val histogram = IntArray(256) { 10 } // perfectly even
        val e = CropEngine.shannonEntropy(histogram, total)
        assertEquals(1.0, e.toDouble(), 1e-3)
    }

    @Test
    fun `shannonEntropy of single-value histogram is 0`() {
        val histogram = IntArray(256).also { it[100] = 500 }
        val e = CropEngine.shannonEntropy(histogram, 500)
        assertEquals(0f, e, 1e-6f)
    }

    // =====================================================================================
    // Saliency
    // =====================================================================================

    @Test
    fun `saliencyMap output is sized and in 0 to 1`() {
        val sal = CropEngine.saliencyMap(solid(20, 20, px(128, 128, 128)), 20, 20)
        assertEquals(400, sal.size)
        for (v in sal) assertTrue("value $v out of range", v in 0f..1f)
    }

    @Test
    fun `saliencyMap peaks on a bright subject`() {
        val w = 40; val h = 40
        val img = brightSquare(w, h, cx = 20, cy = 20, sq = 12, bright = 240, dark = 10)
        val sal = CropEngine.saliencyMap(img, w, h)
        // Peak must fall inside the bright square (with a little tolerance).
        var maxIdx = 0
        for (i in sal.indices) if (sal[i] > sal[maxIdx]) maxIdx = i
        val peakX = maxIdx % w
        val peakY = maxIdx / w
        assertTrue("peak at ($peakX,$peakY) outside subject", peakX in 13..27 && peakY in 13..27)
        // Mean salience inside the square should exceed the surround.
        var inSum = 0f; var inN = 0; var outSum = 0f; var outN = 0
        for (y in 0 until h) for (x in 0 until w) {
            if (x in 14..26 && y in 14..26) { inSum += sal[y * w + x]; inN++ }
            else { outSum += sal[y * w + x]; outN++ }
        }
        assertTrue("subject not more salient than surround", inSum / inN > outSum / outN)
    }

    // =====================================================================================
    // Sobel
    // =====================================================================================

    @Test
    fun `sobel detects a vertical edge and is flat elsewhere`() {
        val w = 20; val h = 20
        val img = IntArray(w * h) { i -> if (i % w < w / 2) px(0, 0, 0) else px(255, 255, 255) }
        val edge = CropEngine.sobel(FloatArray(w * h) { i ->
            val c = img[i]; 0.299f * ((c shr 16) and 0xff) + 0.587f * ((c shr 8) and 0xff) + 0.114f * (c and 0xff)
        }, w, h)
        // Strong response near the edge column, ~0 in the flat interiors.
        val edgeCol = w / 2 - 1
        var edgeMax = 0f
        var flatMax = 0f
        for (y in 1 until h - 1) {
            if (edge[y * w + edgeCol] > edgeMax) edgeMax = edge[y * w + edgeCol]
            if (edge[y * w + 2] > flatMax) flatMax = edge[y * w + 2]
            if (edge[y * w + (w - 3)] > flatMax) flatMax = edge[y * w + (w - 3)]
        }
        assertTrue("edge response $edgeMax should be strong", edgeMax > 200f)
        assertTrue("flat region $flatMax should be ~0", flatMax < 5f)
    }

    // =====================================================================================
    // Integral / box mean
    // =====================================================================================

    @Test
    fun `integral and boxMean compute correct averages`() {
        val src = floatArrayOf(1f, 2f, 3f, 4f) // 2x2
        val ii = CropEngine.integral(src, 2, 2)
        assertEquals(2.5f, CropEngine.boxMean(ii, 2, 2, 0, 0, 1, 1), 1e-4f) // whole image
        assertEquals(1.0f, CropEngine.boxMean(ii, 2, 2, 0, 0, 0, 0), 1e-4f) // top-left
        assertEquals(4.0f, CropEngine.boxMean(ii, 2, 2, 1, 1, 1, 1), 1e-4f) // bottom-right
    }

    @Test
    fun `normalise maps to 0 to 1 and flat input yields zeros`() {
        val flat = FloatArray(10) { 5f }
        val nf = CropEngine.normalise(flat)
        for (v in nf) assertEquals(0f, v, 1e-6f)
        val n = CropEngine.normalise(floatArrayOf(0f, 5f, 10f))
        assertEquals(0f, n[0], 1e-4f)
        assertEquals(0.5f, n[1], 1e-4f)
        assertEquals(1f, n[2], 1e-4f)
    }

    // =====================================================================================
    // Focal points / NMS
    // =====================================================================================

    @Test
    fun `focalPoints finds the subject and NMS suppresses near-duplicates`() {
        val w = 40; val h = 40
        val sal = CropEngine.saliencyMap(brightSquare(w, h, 20, 20, 12, 240, 10), w, h)
        val focals = CropEngine.focalPoints(sal, w, h)
        assertTrue("expected at least one focal", focals.isNotEmpty())
        // NMS radius (~min/12≈3px) should keep at most a couple from one compact subject.
        assertTrue("too many focals kept (${focals.size})", focals.size <= 4)
        // The strongest focal should be near the subject centre.
        val top = focals.maxByOrNull { it.weight }!!
        assertTrue("strongest focal at (${top.x},${top.y}) off subject", top.x in 13f..27f && top.y in 13f..27f)
    }

    @Test
    fun `greedyNms keeps distant points and drops close ones`() {
        val pts = listOf(
            CropEngine.FocalPoint(10f, 10f, 1f),
            CropEngine.FocalPoint(11f, 11f, 0.5f), // within radius of (10,10) -> suppressed
            CropEngine.FocalPoint(80f, 80f, 0.8f)  // far -> kept
        )
        val kept = CropEngine.greedyNms(pts, 100, 100, radius = 5, maxPoints = 8)
        assertEquals(2, kept.size)
        // Strongest first: weight 1.0 at (10,10) beats 0.8 at (80,80).
        assertEquals(10f, kept[0].x, 1e-3f)
    }

    @Test
    fun `greedyNms promotes FACE points above stronger salience`() {
        val pts = listOf(
            CropEngine.FocalPoint(50f, 50f, 5f, CropEngine.FocalType.SALIENCY),
            CropEngine.FocalPoint(20f, 20f, 1f, CropEngine.FocalType.FACE)
        )
        val kept = CropEngine.greedyNms(pts, 100, 100, radius = 100, maxPoints = 1)
        assertEquals(1, kept.size)
        assertEquals(CropEngine.FocalType.FACE, kept[0].type)
    }

    // =====================================================================================
    // clampCropRect
    // =====================================================================================

    @Test
    fun `clampCropRect contains overflow within bounds`() {
        val r = CropEngine.clampCropRect(
            CropEngine.CropRect(-10f, -10f, 120f, 120f), 100, 100, 1f, 1f
        )
        assertTrue("left ${r.left} < 0", r.left >= 0f)
        assertTrue("top ${r.top} < 0", r.top >= 0f)
        assertTrue("right ${r.right} > 100", r.right <= 100f)
        assertTrue("bottom ${r.bottom} > 100", r.bottom <= 100f)
    }

    @Test
    fun `clampCropRect shrinks oversized rect to source`() {
        val r = CropEngine.clampCropRect(
            CropEngine.CropRect(0f, 0f, 200f, 200f), 100, 100, 1f, 1f
        )
        assertEquals(100f, r.width, 1e-3f)
        assertEquals(100f, r.height, 1e-3f)
    }

    @Test
    fun `clampCropRect enforces a minimum size`() {
        val r = CropEngine.clampCropRect(
            CropEngine.CropRect(40f, 40f, 41f, 41f), 100, 100, minW = 10f, minH = 10f
        )
        assertTrue("width ${r.width} < 10", r.width >= 10f)
        assertTrue("height ${r.height} < 10", r.height >= 10f)
    }

    // =====================================================================================
    // optimalCrop
    // =====================================================================================

    @Test
    fun `optimalCrop stays in bounds and matches target aspect`() {
        val rect = CropEngine.optimalCrop(
            sourceWidth = 400, sourceHeight = 300,
            targetWidth = 108, targetHeight = 240, // phone portrait
            focalPoints = listOf(CropEngine.FocalPoint(300f, 150f, 3f))
        )
        assertTrue("left ${rect.left} < 0", rect.left >= 0f)
        assertTrue("top ${rect.top} < 0", rect.top >= 0f)
        assertTrue("right ${rect.right} > 400", rect.right <= 400f)
        assertTrue("bottom ${rect.bottom} > 300", rect.bottom <= 300f)
        assertTrue("width ${rect.width} <= 0", rect.width > 0f)
        assertTrue("height ${rect.height} <= 0", rect.height > 0f)
        val aspect = rect.width / rect.height
        val target = 108f / 240f
        assertTrue("aspect $aspect != target $target", kotlin.math.abs(aspect - target) < 0.02f)
    }

    @Test
    fun `optimalCrop pans toward an off-centre subject`() {
        // Source is wide; target is portrait. Subject is far right.
        val rect = CropEngine.optimalCrop(
            sourceWidth = 400, sourceHeight = 200,
            targetWidth = 100, targetHeight = 200,
            focalPoints = listOf(CropEngine.FocalPoint(360f, 100f, 3f))
        )
        // Cover crop width = 100 (height = 200, full). Centre should shift right.
        assertTrue("centreX ${rect.centerX} not panned toward subject", rect.centerX > 200f)
        assertTrue("right ${rect.right} > 400", rect.right <= 400f)
        assertTrue("left ${rect.left} < 0", rect.left >= 0f)
    }

    @Test
    fun `optimalCrop never produces out-of-bounds for extreme target aspect`() {
        // Very tall target from a square source.
        val rect = CropEngine.optimalCrop(
            sourceWidth = 100, sourceHeight = 100,
            targetWidth = 50, targetHeight = 400,
            focalPoints = listOf(CropEngine.FocalPoint(50f, 50f, 1f))
        )
        assertTrue("left ${rect.left} < 0", rect.left >= 0f)
        assertTrue("top ${rect.top} < 0", rect.top >= 0f)
        assertTrue("right ${rect.right} > 100", rect.right <= 100f)
        assertTrue("bottom ${rect.bottom} > 100", rect.bottom <= 100f)
        assertTrue("width<=0", rect.width > 0f)
        assertTrue("height<=0", rect.height > 0f)
    }

    // =====================================================================================
    // scoreCropRect
    // =====================================================================================

    @Test
    fun `scoreCropRect is normalised to 0 to 1`() {
        val pts = listOf(CropEngine.FocalPoint(50f, 50f, 2f))
        val s = CropEngine.scoreCropRect(
            CropEngine.CropRect(0f, 0f, 100f, 100f), pts, 100, 100
        )
        assertTrue("score $s out of [0,1]", s in 0f..1f)
    }

    @Test
    fun `scoreCropRect rewards containing focal points`() {
        val pts = listOf(
            CropEngine.FocalPoint(50f, 50f, 3f),
            CropEngine.FocalPoint(60f, 55f, 2f)
        )
        val containing = CropEngine.scoreCropRect(
            CropEngine.CropRect(0f, 0f, 100f, 100f), pts, 100, 100
        )
        val excluding = CropEngine.scoreCropRect(
            CropEngine.CropRect(0f, 0f, 10f, 10f), pts, 100, 100
        )
        assertTrue("containing=$containing should beat excluding=$excluding", containing > excluding)
    }
}
