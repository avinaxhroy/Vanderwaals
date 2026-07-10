package me.avinas.vanderwaals.core

import android.graphics.Bitmap
import android.util.Log
import me.avinas.vanderwaals.core.crop.CropEngine
import kotlin.math.max

/**
 * Android adapter for [CropEngine] saliency analysis.
 *
 * Responsibilities:
 *  - Bulk-read a bitmap's pixels once via [Bitmap.getPixels] (instead of the
 *    per-pixel JNI `getPixel` calls that dominated the old implementation).
 *  - Downscale to a small working resolution so saliency is computed in
 *    microseconds rather than scanning a multi-megapixel image in Kotlin.
 *  - Translate [CropEngine]'s pure results back into full-resolution image
 *    coordinates.
 *  - Memoise both [ImageCharacteristics] and focal points per *image content*
 *    (a cheap 16x16 signature), so the four wallpaper-apply call sites and the
 *    Glide preview transformation that re-crop the same image do not recompute.
 *
 * The actual mathematics live in [CropEngine] and are fully unit-testable on a
 * plain JVM; this class only bridges Android `Bitmap` to that pure core.
 */
object SaliencyDetector {
    private const val TAG = "SaliencyDetector"

    /** Maximum long-side resolution at which saliency is computed. */
    private const val WORKING_MAX = 256
    /** Long side of the content signature used as the cache key. */
    private const val SIGNATURE_SIZE = 16
    /** Number of analysed images kept in the cache. */
    private const val CACHE_LIMIT = 24

    /** A point of interest in full-resolution image coordinates with a weight. */
    data class FocalPoint(
        val x: Float,
        val y: Float,
        val weight: Float = 1.0f
    )

    /** Image characteristics for adaptive processing. */
    data class ImageCharacteristics(
        val averageBrightness: Float,
        val colorfulness: Float,
        val contrast: Float,
        val entropy: Float,
        val isDark: Boolean,
        val isBright: Boolean,
        val isColorful: Boolean,
        val isMinimal: Boolean
    )

    /** Cached analysis for one image, keyed by a content signature. */
    private data class CachedAnalysis(
        val sourceWidth: Int,
        val sourceHeight: Int,
        val stats: ImageCharacteristics,
        val focalPoints: List<FocalPoint>
    )

    private val cache = object : LinkedHashMap<String, CachedAnalysis>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedAnalysis>): Boolean =
            size > CACHE_LIMIT
    }

    /**
     * Detects salient (visually important) regions in [bitmap] and returns them
     * as weighted focal points in full-resolution coordinates.
     */
    fun detectSalientRegions(bitmap: Bitmap): List<FocalPoint> {
        if (bitmap.width < 2 || bitmap.height < 2) {
            return listOf(FocalPoint(bitmap.width / 2f, bitmap.height / 2f, 1.0f))
        }
        return try {
            analyze(bitmap).focalPoints
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting salient regions", e)
            listOf(FocalPoint(bitmap.width / 2f, bitmap.height / 2f, 1.0f))
        }
    }

    /** Analyses image characteristics for adaptive processing (memoised). */
    fun analyzeImageCharacteristics(bitmap: Bitmap): ImageCharacteristics {
        if (bitmap.width < 2 || bitmap.height < 2) {
            return ImageCharacteristics(128f, 0f, 0f, 0f, false, false, false, true)
        }
        return try {
            analyze(bitmap).stats
        } catch (e: Exception) {
            Log.e(TAG, "Error analysing image characteristics", e)
            ImageCharacteristics(128f, 0f, 0f, 0f, false, false, false, true)
        }
    }

    /**
     * Image entropy in [0,1]. Kept for callers that query it directly; the value
     * is computed as part of [analyzeImageCharacteristics] and cached with it.
     */
    fun calculateImageEntropy(bitmap: Bitmap): Float = analyzeImageCharacteristics(bitmap).entropy

    /**
     * Public for testability: compute a stable content signature for [bitmap].
     * Downsamples to [SIGNATURE_SIZE]x[SIGNATURE_SIZE] and folds the packed
     * pixels into a hex string. Two different images of identical dimensions get
     * different signatures (the bug the old dimension-only cache would have had).
     */
    fun contentSignature(bitmap: Bitmap): String {
        val small = downscale(bitmap, SIGNATURE_SIZE, SIGNATURE_SIZE) ?: bitmap
        val px = IntArray(SIGNATURE_SIZE * SIGNATURE_SIZE)
        small.getPixels(px, 0, SIGNATURE_SIZE, 0, 0, SIGNATURE_SIZE, SIGNATURE_SIZE)
        if (small !== bitmap) small.recycle()
        val sb = StringBuilder(SIGNATURE_SIZE * SIGNATURE_SIZE * 2 + 16)
        sb.append(bitmap.width).append('x').append(bitmap.height).append(':')
        for (p in px) {
            val v = (p xor (p ushr 16)) and 0xffff
            sb.append(v.toString(16).padStart(4, '0'))
        }
        return sb.toString()
    }

    // -----------------------------------------------------------------------------------

    /** Core path: downscale, bulk-read pixels, run [CropEngine], scale back up. */
    private fun analyze(bitmap: Bitmap): CachedAnalysis {
        val key = contentSignature(bitmap)
        cache[key]?.let { existing ->
            if (existing.sourceWidth == bitmap.width && existing.sourceHeight == bitmap.height) {
                return existing
            }
        }

        val working = downscaleMax(bitmap, WORKING_MAX) ?: bitmap
        val ww = working.width
        val wh = working.height
        val pixels = IntArray(ww * wh)
        working.getPixels(pixels, 0, ww, 0, 0, ww, wh)

        val stats = CropEngine.analyze(pixels, ww, wh)
        val sal = CropEngine.saliencyMap(pixels, ww, wh, stats)
        val rawFocals = CropEngine.focalPoints(sal, ww, wh, stats)

        // Scale focal-point coordinates from working space back to full resolution.
        val sx = bitmap.width.toFloat() / ww
        val sy = bitmap.height.toFloat() / wh
        val focals = rawFocals.map {
            FocalPoint(it.x * sx, it.y * sy, it.weight)
        }

        if (working !== bitmap) working.recycle()

        val result = CachedAnalysis(bitmap.width, bitmap.height, stats.toApp(), focals)
        cache[key] = result
        return result
    }

    /** Downscale [src] so its longer side is at most [maxDim], preserving aspect. */
    private fun downscaleMax(src: Bitmap, maxDim: Int): Bitmap? {
        val longest = max(src.width, src.height)
        if (longest <= maxDim) return null
        val scale = maxDim.toFloat() / longest
        val w = max(1, (src.width * scale).toInt())
        val h = max(1, (src.height * scale).toInt())
        return try {
            Bitmap.createScaledBitmap(src, w, h, true)
        } catch (e: Exception) {
            Log.w(TAG, "Downscale failed, using full resolution", e)
            null
        }
    }

    /** Downscale [src] to exact [w]*[h] (used for the content signature). */
    private fun downscale(src: Bitmap, w: Int, h: Int): Bitmap? {
        return try {
            Bitmap.createScaledBitmap(src, w, h, true)
        } catch (e: Exception) {
            null
        }
    }

    private fun CropEngine.ImageStats.toApp() = ImageCharacteristics(
        averageBrightness, colorfulness, contrast, entropy,
        isDark, isBright, isColorful, isMinimal
    )
}
