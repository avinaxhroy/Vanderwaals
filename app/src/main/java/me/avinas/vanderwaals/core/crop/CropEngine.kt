package me.avinas.vanderwaals.core.crop

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Pure, Android-free smart-crop mathematics.
 *
 * Operates on a packed ARGB [IntArray] (0xAARRGGBB, the format produced by
 * [android.graphics.Bitmap.getPixels]) together with its dimensions. Every
 * function here is deterministic and has no platform dependency, so the entire
 * engine is unit-testable on a plain JVM without Robolectric.
 *
 * Pipeline:
 *  1. [analyze]      — single-pass image statistics (brightness, colorfulness,
 *                      contrast, entropy) used to tune adaptive weights.
 *  2. [saliencyMap]  — multi-scale Itti-Koch centre-surround saliency over
 *                      intensity, RG/BY colour opponency and Sobel edge energy,
 *                      with a mild central prior. Normalised to [0,1].
 *  3. [focalPoints]  — non-maximum-suppressed peak picking with greedy radius
 *                      thinning, weighted by peak salience.
 *  4. [optimalCrop]  — candidate crop rectangles at several bounded zoom levels
 *                      around focal / rule-of-thirds / horizon centres, scored
 *                      by a balanced, normalised objective and clamped to safe
 *                      bounds.
 *
 * The Android layer ([me.avinas.vanderwaals.core.SaliencyDetector] and
 * [me.avinas.vanderwaals.core.SmartCrop]) is a thin adapter that supplies
 * pixels via a bulk [android.graphics.Bitmap.getPixels] call and scales the
 * resulting rectangle back up to full resolution.
 */
object CropEngine {

    /** Type of a focal point, used to bias scoring (faces dominate subjects). */
    enum class FocalType { SUBJECT, SALIENCY, FACE, HORIZON, COMPOSITION }

    /** A point of visual interest in image coordinates with an importance weight. */
    data class FocalPoint(
        val x: Float,
        val y: Float,
        val weight: Float = 1.0f,
        val type: FocalType = FocalType.SALIENCY
    )

    /** Single-pass image statistics driving adaptive saliency weights. */
    data class ImageStats(
        val averageBrightness: Float,
        val colorfulness: Float,
        val contrast: Float,
        val entropy: Float,
        val isDark: Boolean,
        val isBright: Boolean,
        val isColorful: Boolean,
        val isMinimal: Boolean
    ) {
        /** Adaptive (edge, colour, texture) weights derived from characteristics. */
        fun adaptiveWeights(): FloatArray {
            var edge = 0.40f
            var color = 0.40f
            var texture = 0.20f
            if (isDark)        { edge = 0.35f; color = 0.35f; texture = 0.30f }
            if (isBright)      { edge = 0.45f; color = 0.35f; texture = 0.20f }
            if (isColorful)    { edge = 0.30f; color = 0.50f; texture = 0.20f }
            if (isMinimal)     { edge = 0.45f; color = 0.25f; texture = 0.30f }
            if (contrast > 0.7f) { edge += 0.05f; color = (color - 0.05f).coerceAtLeast(0.25f) }
            if (contrast < 0.3f) { edge -= 0.05f; color += 0.05f }
            return floatArrayOf(edge.coerceIn(0f, 1f), color.coerceIn(0f, 1f), texture.coerceIn(0f, 1f))
        }
    }

    /** A floating-point rectangle in source-pixel coordinates. */
    data class CropRect(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    ) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
        val centerX: Float get() = (left + right) * 0.5f
        val centerY: Float get() = (top + bottom) * 0.5f
        fun contains(x: Float, y: Float): Boolean =
            x >= left && x <= right && y >= top && y <= bottom
    }

    /** Tunable crop behaviour. Defaults are tuned for phone wallpaper targets. */
    data class CropOptions(
        /** Maximum upscale tolerated when zooming the crop up to the target size. */
        val maxUpscale: Float = 1.3f,
        /** Zoom levels evaluated between 1.0 (cover) and the upscale-limited max. */
        val zoomSteps: Int = 4,
        /** Weight of focal-point coverage in the objective (all terms normalised 0..1). */
        val coverageWeight: Float = 0.45f,
        val thirdsWeight: Float = 0.20f,
        val edgeSafetyWeight: Float = 0.20f,
        val centroidWeight: Float = 0.15f,
        /** Mild penalty per unit of zoom to prefer wider framing on ties. */
        val zoomPenalty: Float = 0.04f,
        /** Edge margin as a fraction of the shorter crop side. */
        val edgeMarginFraction: Float = 0.06f
    )

    // =====================================================================================
    // Pixel extraction (packed ARGB, Android layout: alpha in bits 24..31)
    // =====================================================================================

    private inline fun red(c: Int) = (c ushr 16) and 0xff
    private inline fun green(c: Int) = (c ushr 8) and 0xff
    private inline fun blue(c: Int) = c and 0xff

    /** Rec. 601 luminance. Cheap and good enough for saliency. */
    @Suppress("unused") private inline fun luminance(c: Int): Float =
        0.299f * red(c) + 0.587f * green(c) + 0.114f * blue(c)

    // =====================================================================================
    // 1. Statistics — one pass, memoised by the adapter.
    // =====================================================================================

    /**
     * Compute [ImageStats] in a single sampling pass over [pixels].
     * [width]*[height] must equal [pixels].size.
     */
    fun analyze(pixels: IntArray, width: Int, height: Int): ImageStats {
        require(pixels.size == width * height) { "pixels size ${pixels.size} != ${width}x${height}" }
        val n = pixels.size
        if (n == 0) return ImageStats(128f, 0f, 0f, 0f, false, false, false, true)

        var sumBright = 0L
        var sumR = 0L; var sumG = 0L; var sumB = 0L
        var minB = 255; var maxB = 0
        val histogram = IntArray(256)

        // Sample at most ~4000 pixels for speed on large working images; stride evenly.
        val targetSamples = 4000
        val stride = max(1, n / targetSamples)
        var count = 0
        var i = 0
        while (i < n) {
            val c = pixels[i]
            val r = red(c); val g = green(c); val b = blue(c)
            val bright = (r + g + b) / 3
            sumBright += bright
            sumR += r; sumG += g; sumB += b
            if (bright < minB) minB = bright
            if (bright > maxB) maxB = bright
            histogram[bright]++
            count++
            i += stride
        }
        if (count == 0) return ImageStats(128f, 0f, 0f, 0f, false, false, false, true)

        val avgBright = sumBright.toFloat() / count
        val avgR = sumR.toFloat() / count
        val avgG = sumG.toFloat() / count
        val avgB = sumB.toFloat() / count
        val maxC = max(max(avgR, avgG), avgB)
        val minC = min(min(avgR, avgG), avgB)
        // Hasler & Süsstrunk-style colour spread on the averaged channels: a cheap
        // but far better "colourfulness" signal than saturation-of-the-mean.
        val rg = abs(avgR - avgG)
        val yb = (avgR + avgG) * 0.5f - avgB
        val colorfulness = sqrt(rg * rg + yb * yb) / 255f
        val contrast = if (maxB > minB) (maxB - minB).toFloat() / 255f else 0f
        val entropy = shannonEntropy(histogram, count)

        return ImageStats(
            averageBrightness = avgBright,
            colorfulness = colorfulness,
            contrast = contrast,
            entropy = entropy,
            isDark = avgBright < 85f,
            isBright = avgBright > 170f,
            isColorful = colorfulness > 0.18f,
            isMinimal = colorfulness < 0.08f
        )
    }

    /** Shannon entropy of a brightness histogram, normalised to [0,1] (max 8 bits). */
    fun shannonEntropy(histogram: IntArray, total: Int): Float {
        if (total <= 0) return 0f
        var entropy = 0.0
        for (count in histogram) {
            if (count > 0) {
                val p = count.toDouble() / total
                entropy -= p * kotlin.math.log(p, 2.0)
            }
        }
        return (entropy / 8.0).toFloat().coerceIn(0f, 1f)
    }

    // =====================================================================================
    // 2. Saliency — multi-scale centre-surround (Itti-Koch) + Sobel + colour opponency
    // =====================================================================================

    private val CENTRE_RADII = intArrayOf(1, 2)          // fine "centre" box radii
    private val SURROUND_RADII = intArrayOf(5, 11, 23)   // coarse "surround" box radii
    private const val CENTRAL_PRIOR_SIGMA_FRAC = 0.4f    // central prior sigma as fraction of half-extent

    /**
     * Build a normalised saliency map of size [width]*[height] from [pixels].
     *
     * Combines centre-surround responses over intensity, RG/BY colour opponency
     * and Sobel edge energy, each normalised per scale and fused with adaptive
     * weights from [stats] (computed via [analyze] if absent). A mild Gaussian
     * central prior is added — enough to bias toward the photographic centre
     * without overwhelming off-centre subjects.
     */
    fun saliencyMap(
        pixels: IntArray,
        width: Int,
        height: Int,
        stats: ImageStats? = null
    ): FloatArray {
        require(pixels.size == width * height) { "pixels size ${pixels.size} != ${width}x${height}" }
        val n = width * height
        if (n == 0) return FloatArray(0)

        val st = stats ?: analyze(pixels, width, height)
        val w = st.adaptiveWeights()
        val wI = w[0]; val wC = w[1]; val wE = w[2]

        // --- Channel maps ---
        val lum = FloatArray(n)
        val rg = FloatArray(n)   // R - G  colour opponent
        val by = FloatArray(n)   // (R+G)/2 - B  colour opponent
        for (i in 0 until n) {
            val c = pixels[i]
            val r = red(c); val g = green(c); val b = blue(c)
            lum[i] = 0.299f * r + 0.587f * g + 0.114f * b
            // Standard Itti colour opponency; values kept signed for centre-surround.
            rg[i] = (r - g) * 0.5f
            by[i] = (r + g) * 0.25f - b * 0.5f
        }

        // --- Sobel edge energy on luminance ---
        val edge = sobel(lum, width, height)

        // --- Integral images for O(1) box means ---
        val iiLum = integral(lum, width, height)
        val iiRG = integral(rg, width, height)
        val iiBY = integral(by, width, height)
        val iiEdge = integral(edge, width, height)

        val cs = FloatArray(n)   // accumulated centre-surround response
        // One scratch buffer reused across all per-scale/per-channel calls below
        // (12 calls); avoids 12 transient FloatArray allocations per frame.
        val csScratch = FloatArray(n)

        for (si in SURROUND_RADII.indices) {
            val sr = SURROUND_RADII[si]
            val cr = CENTRE_RADII[si % CENTRE_RADII.size]
            // Per-scale, per-channel responses, normalised then summed.
            addCentreSurround(cs, iiLum, width, height, cr, sr, wI, csScratch)
            addCentreSurround(cs, iiRG, width, height, cr, sr, wC * 0.5f, csScratch)
            addCentreSurround(cs, iiBY, width, height, cr, sr, wC * 0.5f, csScratch)
            addCentreSurround(cs, iiEdge, width, height, cr, sr, wE * 0.5f, csScratch)
        }

        // Add raw (non-CS) edge energy, normalised — edges are a strong subject cue.
        val edgeNorm = normalise(edge)
        for (i in 0 until n) cs[i] += edgeNorm[i] * wE

        // --- Mild central prior (Gaussian centred on image) ---
        val cx = width * 0.5f
        val cy = height * 0.5f
        val sigma = min(width, height) * 0.5f * CENTRAL_PRIOR_SIGMA_FRAC
        val twoSigmaSq = 2f * sigma * sigma
        if (twoSigmaSq > 0f) {
            var idx = 0
            for (y in 0 until height) {
                val dy = y - cy
                for (x in 0 until width) {
                    val dx = x - cx
                    cs[idx] += 0.12f * kotlin.math.exp(-(dx * dx + dy * dy) / twoSigmaSq)
                    idx++
                }
            }
        }

        return normalise(cs)
    }

    /** 3x3 Sobel magnitude on a single-channel [src] map. */
    fun sobel(src: FloatArray, width: Int, height: Int): FloatArray {
        val n = width * height
        val out = FloatArray(n)
        if (width < 3 || height < 3) return out
        for (y in 1 until height - 1) {
            val row = y * width
            for (x in 1 until width - 1) {
                val i = row + x
                val tl = src[i - width - 1]; val tc = src[i - width]; val tr = src[i - width + 1]
                val bl = src[i + width - 1]; val bc = src[i + width]; val br = src[i + width + 1]
                val gx = (tr + 2f * src[i + 1] + br) - (tl + 2f * src[i - 1] + bl)
                val gy = (bl + 2f * bc + br) - (tl + 2f * tc + tr)
                out[i] = sqrt(gx * gx + gy * gy)
            }
        }
        return out
    }

    /**
     * Integral image (summed-area table) with a zero-padded border so that any
     * box query is O(1) without bounds checks. Size (width+1)*(height+1).
     */
    fun integral(src: FloatArray, width: Int, height: Int): FloatArray {
        val w1 = width + 1
        val h1 = height + 1
        val ii = FloatArray(w1 * h1)
        for (y in 1..height) {
            var rowSum = 0f
            val srcRow = (y - 1) * width
            val iiRow = y * w1
            val iiPrev = (y - 1) * w1
            for (x in 1..width) {
                rowSum += src[srcRow + x - 1]
                ii[iiRow + x] = ii[iiPrev + x] + rowSum
            }
        }
        return ii
    }

    /** Mean over the axis-aligned box [x0,x1] x [y0,y1] (inclusive) via the integral image. */
    fun boxMean(ii: FloatArray, width: Int, height: Int, x0: Int, y0: Int, x1: Int, y1: Int): Float {
        val w1 = width + 1
        val xa = x0.coerceIn(0, width - 1)
        val xb = x1.coerceIn(0, width - 1)
        val ya = y0.coerceIn(0, height - 1)
        val yb = y1.coerceIn(0, height - 1)
        val area = ((xb - xa + 1).coerceAtLeast(0)) * ((yb - ya + 1).coerceAtLeast(0))
        if (area <= 0) return 0f
        val a = ii[ya * w1 + xa]
        val b = ii[ya * w1 + xb + 1]
        val c = ii[(yb + 1) * w1 + xa]
        val d = ii[(yb + 1) * w1 + xb + 1]
        return (d - b - c + a) / area
    }

    /**
     * Adds |centre_mean - surround_mean| (per-pixel) into [out], normalised by the
     * current map's max so each scale contributes comparably regardless of gain.
     *
     * [response] is a caller-owned scratch buffer of size [out].size that is fully
     * overwritten on every call; reusing it across the per-scale/per-channel calls
     * in [saliencyMap] avoids a dozen transient ~n-float allocations per frame.
     */
    private fun addCentreSurround(
        out: FloatArray,
        ii: FloatArray,
        width: Int,
        height: Int,
        centreRadius: Int,
        surroundRadius: Int,
        weight: Float,
        response: FloatArray
    ) {
        var maxResp = 1f
        for (y in 0 until height) {
            for (x in 0 until width) {
                val centre = boxMean(ii, width, height, x - centreRadius, y - centreRadius, x + centreRadius, y + centreRadius)
                val surround = boxMean(ii, width, height, x - surroundRadius, y - surroundRadius, x + surroundRadius, y + surroundRadius)
                val r = abs(centre - surround)
                response[y * width + x] = r
                if (r > maxResp) maxResp = r
            }
        }
        val inv = weight / maxResp
        for (i in out.indices) out[i] += response[i] * inv
    }

    /** Linear normalisation to [0,1]; returns a zero map if the input is flat. */
    fun normalise(src: FloatArray): FloatArray {
        var lo = Float.POSITIVE_INFINITY
        var hi = Float.NEGATIVE_INFINITY
        for (v in src) { if (v < lo) lo = v; if (v > hi) hi = v }
        val range = hi - lo
        if (range <= 1e-6f) return FloatArray(src.size)
        val inv = 1f / range
        return FloatArray(src.size) { (src[it] - lo) * inv }
    }

    // =====================================================================================
    // 3. Focal-point extraction — NMS peak picking with greedy radius thinning
    // =====================================================================================

    /**
     * Extract weighted focal points from a [saliency] map of size [width]*[height].
     *
     * Picks peaks above [thresholdFraction]*globalMax, then greedily keeps the
     * strongest and suppresses everything within [nmsRadius] (in pixels) until
     * none remain. Peak weight combines salience and a local-density boost so a
     * broad salient region outweighs a single noisy pixel.
     */
    fun focalPoints(
        saliency: FloatArray,
        width: Int,
        height: Int,
        stats: ImageStats? = null,
        thresholdFraction: Float = 0.35f,
        nmsRadius: Int = -1,
        maxPoints: Int = 8
    ): List<FocalPoint> {
        require(saliency.size == width * height) { "saliency size ${saliency.size} != ${width}x${height}" }
        val n = width * height
        if (n == 0) return emptyList()

        var globalMax = 0f
        for (v in saliency) if (v > globalMax) globalMax = v
        if (globalMax <= 1e-6f) {
            return listOf(FocalPoint(width / 2f, height / 2f, 1f, FocalType.COMPOSITION))
        }
        val threshold = globalMax * thresholdFraction

        // Local maxima: strictly greater than 8-neighbours and above threshold.
        val peaks = ArrayList<Int>()
        for (y in 1 until height - 1) {
            val row = y * width
            for (x in 1 until width - 1) {
                val i = row + x
                val v = saliency[i]
                if (v < threshold) continue
                if (v > saliency[i - 1] && v > saliency[i + 1] &&
                    v > saliency[i - width] && v > saliency[i + width] &&
                    v > saliency[i - width - 1] && v > saliency[i - width + 1] &&
                    v > saliency[i + width - 1] && v > saliency[i + width + 1]
                ) {
                    peaks.add(i)
                }
            }
        }

        if (peaks.isEmpty()) {
            // Fall back to thresholded cells; still better than a bare centre.
            val pts = ArrayList<FocalPoint>()
            for (i in 0 until n) {
                if (saliency[i] >= threshold) {
                    pts.add(FocalPoint((i % width).toFloat(), (i / width).toFloat(), saliency[i]))
                }
            }
            if (pts.isEmpty()) return listOf(FocalPoint(width / 2f, height / 2f, 1f, FocalType.COMPOSITION))
            return greedyNms(pts, width, height, nmsRadius, maxPoints, stats)
        }

        val radius = if (nmsRadius > 0) nmsRadius else max(2, min(width, height) / 12)
        val candidates = peaks.map { i ->
            val px = (i % width).toFloat()
            val py = (i / width).toFloat()
            // Local support: mean salience in a small box around the peak rewards regions.
            val support = localSupport(saliency, width, height, px, py, radius)
            val weight = saliency[i] * (0.6f + 0.4f * support)
            FocalPoint(px, py, weight, FocalType.SALIENCY)
        }
        return greedyNms(candidates, width, height, radius, maxPoints, stats)
    }

    /** Mean salience in a box of half-extent [r] around (px,py); 0 if out of range. */
    private fun localSupport(saliency: FloatArray, width: Int, height: Int, px: Float, py: Float, r: Int): Float {
        val x0 = max(0, (px - r).toInt()); val x1 = min(width - 1, (px + r).toInt())
        val y0 = max(0, (py - r).toInt()); val y1 = min(height - 1, (py + r).toInt())
        var sum = 0f; var count = 0
        for (y in y0..y1) {
            val row = y * width
            for (x in x0..x1) { sum += saliency[row + x]; count++ }
        }
        return if (count > 0) sum / count else 0f
    }

    /**
     * Greedy non-maximum suppression: sort by weight desc, keep the strongest,
     * drop everything within [radius], repeat. Promotes [points] with a FACE type
     * to the front so faces are never thinned out by nearby salience.
     */
    fun greedyNms(
        points: List<FocalPoint>,
        width: Int,
        height: Int,
        radius: Int,
        maxPoints: Int,
        stats: ImageStats? = null
    ): List<FocalPoint> {
        if (points.isEmpty()) return emptyList()
        val entropyBoost = 1f + (stats?.entropy ?: 0f) * 0.4f
        val sorted = points.sortedByDescending { it.weight * (if (it.type == FocalType.FACE) 1_000f else 1f) }
        val kept = ArrayList<FocalPoint>()
        val suppressed = BooleanArray(sorted.size)
        for (i in sorted.indices) {
            if (suppressed[i]) continue
            val p = sorted[i]
            kept.add(p.copy(weight = p.weight * entropyBoost))
            if (kept.size >= maxPoints) break
            for (j in i + 1 until sorted.size) {
                if (suppressed[j]) continue
                val q = sorted[j]
                val dx = q.x - p.x; val dy = q.y - p.y
                if (dx * dx + dy * dy <= radius * radius) suppressed[j] = true
            }
        }
        if (kept.isEmpty()) kept.add(FocalPoint(width / 2f, height / 2f, 1f, FocalType.COMPOSITION))
        return kept
    }

    // =====================================================================================
    // 4. Optimal crop — multi-zoom candidate search with balanced scoring
    // =====================================================================================

    /**
     * Choose the best [CropRect] of aspect [targetWidth]/[targetHeight] within an
     * image of [sourceWidth]*[sourceHeight], given [focalPoints] (in source
     * coordinates) and an optional [horizonY] (source-space y of a detected
     * horizon, to align it on a thirds line).
     *
     * Unlike a plain cover crop, this evaluates tighter (zoomed) framings bounded
     * by [CropOptions.maxUpscale] so a subject can be emphasised without ever
     * upscaling the final bitmap beyond the quality budget.
     */
    fun optimalCrop(
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
        focalPoints: List<FocalPoint>,
        horizonY: Float? = null,
        options: CropOptions = CropOptions()
    ): CropRect {
        if (sourceWidth <= 0 || sourceHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) {
            return CropRect(0f, 0f, sourceWidth.toFloat(), sourceHeight.toFloat())
        }
        val targetAspect = targetWidth.toFloat() / targetHeight.toFloat()

        // Cover crop: largest rect of target aspect fitting inside the source.
        val coverW: Float; val coverH: Float
        val sourceAspect = sourceWidth.toFloat() / sourceHeight.toFloat()
        if (sourceAspect > targetAspect) {
            coverH = sourceHeight.toFloat()
            coverW = coverH * targetAspect
        } else {
            coverW = sourceWidth.toFloat()
            coverH = coverW / targetAspect
        }

        // Zoom levels: 1.0 (cover) up to where the crop would need > maxUpscale to
        // reach the target. cropW(z) = coverW / z; require cropW >= targetW / maxUpscale
        // (and symmetrically for height) so we never blow the quality budget.
        val minCropW = targetWidth.toFloat() / options.maxUpscale
        val minCropH = targetHeight.toFloat() / options.maxUpscale
        val maxZoomW = if (coverW > minCropW) coverW / minCropW else 1f
        val maxZoomH = if (coverH > minCropH) coverH / minCropH else 1f
        val maxZoom = min(maxZoomW, maxZoomH).coerceIn(1f, 2.5f)

        val zooms = ArrayList<Float>()
        zooms.add(1.0f)
        if (options.zoomSteps > 1 && maxZoom > 1.01f) {
            for (s in 1..options.zoomSteps) {
                val z = 1f + (maxZoom - 1f) * (s.toFloat() / options.zoomSteps)
                if (z > 1.001f && z <= maxZoom) zooms.add(z)
            }
        }

        // Candidate centres (in source coords): COM, each focal point, thirds grid,
        // and horizon-aligned thirds positions.
        val centres = ArrayList<Pair<Float, Float>>()
        val totalWeight = focalPoints.sumOf { it.weight.toDouble() }.toFloat().coerceAtLeast(1e-4f)
        val comX = focalPoints.sumOf { (it.x * it.weight).toDouble() }.toFloat() / totalWeight
        val comY = focalPoints.sumOf { (it.y * it.weight).toDouble() }.toFloat() / totalWeight
        centres.add(Pair(comX, comY))
        for (p in focalPoints) centres.add(Pair(p.x, p.y))
        val tX1 = sourceWidth / 3f; val tX2 = sourceWidth * 2f / 3f
        val tY1 = sourceHeight / 3f; val tY2 = sourceHeight * 2f / 3f
        centres.add(Pair(tX1, tY1)); centres.add(Pair(tX2, tY1))
        centres.add(Pair(tX1, tY2)); centres.add(Pair(tX2, tY2))
        centres.add(Pair(sourceWidth / 2f, sourceHeight / 2f))
        if (horizonY != null) {
            // Place the horizon on the upper or lower thirds line of the crop.
            for (z in zooms) {
                val ch = coverH / z
                val cyUpper = horizonY - ch / 6f      // horizon at 1/3 from top
                val cyLower = horizonY + ch / 6f      // horizon at 1/3 from bottom
                centres.add(Pair(comX, cyUpper))
                centres.add(Pair(comX, cyLower))
            }
        }

        var bestRect = clampCropRect(
            CropRect(comX - coverW / 2f, comY - coverH / 2f, comX + coverW / 2f, comY + coverH / 2f),
            sourceWidth, sourceHeight, minCropW, minCropH
        )
        var bestScore = scoreCropRect(bestRect, focalPoints, sourceWidth, sourceHeight, options) -
            options.zoomPenalty * 0f

        for (z in zooms) {
            val cropW = coverW / z
            val cropH = coverH / z
            if (cropW < minCropW || cropH < minCropH) continue
            if (cropW > sourceWidth + 0.5f || cropH > sourceHeight + 0.5f) continue
            for (c in centres) {
                val rect = clampCropRect(
                    CropRect(c.first - cropW / 2f, c.second - cropH / 2f,
                             c.first + cropW / 2f, c.second + cropH / 2f),
                    sourceWidth, sourceHeight, minCropW, minCropH
                )
                val score = scoreCropRect(rect, focalPoints, sourceWidth, sourceHeight, options) -
                    options.zoomPenalty * (z - 1f)
                if (score > bestScore) {
                    bestScore = score
                    bestRect = rect
                }
            }
        }
        return bestRect
    }

    /**
     * Clamp [rect] to lie within [0,sourceWidth] x [0,sourceHeight] and to be at
     * least [minW]*[minH] in size. If the requested size exceeds the source it is
     * shrunk to the source extent (aspect is preserved as closely as possible).
     * This is the guard that makes [android.graphics.Bitmap.createBitmap] safe.
     */
    fun clampCropRect(
        rect: CropRect,
        sourceWidth: Int,
        sourceHeight: Int,
        minW: Float = 1f,
        minH: Float = 1f
    ): CropRect {
        var w = rect.width
        var h = rect.height
        if (w > sourceWidth) w = sourceWidth.toFloat()
        if (h > sourceHeight) h = sourceHeight.toFloat()
        if (w < minW) w = minW.coerceAtMost(sourceWidth.toFloat())
        if (h < minH) h = minH.coerceAtMost(sourceHeight.toFloat())

        var left = rect.left
        var top = rect.top
        val maxLeft = (sourceWidth - w).coerceAtLeast(0f)
        val maxTop = (sourceHeight - h).coerceAtLeast(0f)
        if (left < 0f) left = 0f
        if (top < 0f) top = 0f
        if (left > maxLeft) left = maxLeft
        if (top > maxTop) top = maxTop
        return CropRect(left, top, left + w, top + h)
    }

    /**
     * Balanced, fully-normalised crop objective in roughly [0,1].
     *
     *  - coverage    : fraction of total focal weight captured (faces count fully).
     *  - thirds      : closeness of the strongest captured point to a thirds intersection.
     *  - edgeSafety  : 1 minus the average edge-nearness penalty of captured points.
     *  - centroid    : closeness of the rect centre to the focal centre of mass.
     *
     * Every term is normalised 0..1 so no single term can dominate (the bug that
     * made the old `coverage * 0.8` term swallow thirds and horizon candidates).
     */
    fun scoreCropRect(
        rect: CropRect,
        focalPoints: List<FocalPoint>,
        sourceWidth: Int,
        sourceHeight: Int,
        options: CropOptions = CropOptions()
    ): Float {
        if (focalPoints.isEmpty()) return 0f
        val w = rect.width.coerceAtLeast(1f)
        val h = rect.height.coerceAtLeast(1f)
        val margin = min(w, h) * options.edgeMarginFraction

        var totalWeight = 0f
        var coveredWeight = 0f
        var edgePenaltySum = 0f
        var capturedForEdge = 0
        var strongest: FocalPoint? = null
        var strongestW = Float.NEGATIVE_INFINITY

        for (p in focalPoints) {
            totalWeight += p.weight
            if (rect.contains(p.x, p.y)) {
                coveredWeight += p.weight
                capturedForEdge++
                val dx = min(p.x - rect.left, rect.right - p.x)
                val dy = min(p.y - rect.top, rect.bottom - p.y)
                val d = min(dx, dy)
                if (d < margin) edgePenaltySum += (margin - d) / margin
                val effW = if (p.type == FocalType.FACE) p.weight * 1_000f else p.weight
                if (effW > strongestW) { strongestW = effW; strongest = p }
            } else if (strongest == null) {
                val effW = if (p.type == FocalType.FACE) p.weight * 1_000f else p.weight
                if (effW > strongestW) { strongestW = effW; strongest = p }
            }
        }

        val coverage = if (totalWeight > 0f) (coveredWeight / totalWeight).coerceIn(0f, 1f) else 0f
        val edgeSafety = if (capturedForEdge > 0) (1f - (edgePenaltySum / capturedForEdge)).coerceIn(0f, 1f) else 1f

        var thirds = 0f
        if (strongest != null) {
            val rx = strongest!!.x - rect.left
            val ry = strongest!!.y - rect.top
            val x1 = w / 3f; val x2 = 2f * w / 3f
            val y1 = h / 3f; val y2 = 2f * h / 3f
            val d1 = abs(rx - x1) + abs(ry - y1)
            val d2 = abs(rx - x2) + abs(ry - y1)
            val d3 = abs(rx - x1) + abs(ry - y2)
            val d4 = abs(rx - x2) + abs(ry - y2)
            val bestD = min(min(d1, d2), min(d3, d4))
            thirds = 1f / (1f + bestD / ((w + h) * 0.1f))
        }

        val comX = focalPoints.sumOf { (it.x * it.weight).toDouble() }.toFloat() / totalWeight.coerceAtLeast(1e-4f)
        val comY = focalPoints.sumOf { (it.y * it.weight).toDouble() }.toFloat() / totalWeight.coerceAtLeast(1e-4f)
        val cdx = rect.centerX - comX
        val cdy = rect.centerY - comY
        val centroidDist = sqrt(cdx * cdx + cdy * cdy)
        val centroid = 1f / (1f + centroidDist / ((w + h) * 0.15f))

        return options.coverageWeight * coverage +
            options.thirdsWeight * thirds +
            options.edgeSafetyWeight * edgeSafety +
            options.centroidWeight * centroid
    }

}
