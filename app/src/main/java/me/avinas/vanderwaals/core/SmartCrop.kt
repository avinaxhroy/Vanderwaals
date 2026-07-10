package me.avinas.vanderwaals.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import me.avinas.vanderwaals.core.crop.CropEngine
import me.avinas.vanderwaals.core.crop.CropEngine.CropOptions
import me.avinas.vanderwaals.core.crop.CropEngine.CropRect
import me.avinas.vanderwaals.core.crop.CropEngine.FocalType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Content-aware wallpaper cropping.
 *
 * This is the Android entry point; all crop *mathematics* (multi-scale saliency,
 * focal extraction, balanced scoring, zoom-bounded rectangle search, bounds
 * clamping) live in the pure, unit-tested [CropEngine]. This class:
 *  - Reads focal points via [SaliencyDetector] (bulk [Bitmap.getPixels], cached
 *    by image content).
 *  - Asks [CropEngine.optimalCrop] for the best rectangle — which can now *zoom*
 *    (tighten) within a quality budget instead of only panning a cover crop.
 *  - Cuts the bitmap out with [applyCrop], which clamps every coordinate to safe
 *    integer bounds so [Bitmap.createBitmap] can never throw (the old code
 *    truncated floats and silently fell back to a centre crop).
 *  - Caches the resulting rectangle per (image content + target + mode) so the
 *    four wallpaper-apply call sites and the Glide preview never recompute.
 *
 * Face awareness is provided by the suspend overload [smartCropBitmapAsync],
 * which merges [FaceCropDetector] face boxes (as FACE-typed focal points that
 * [CropEngine] biases 1000x) into the candidate set. The synchronous
 * [smartCropBitmap] stays saliency-only so the Glide transformation (which must
 * be synchronous) still works; faces are detected on the real apply path.
 */
object SmartCrop {
    private const val TAG = "SmartCrop"

    /** Crop strategy. Preserved for call-site compatibility. */
    enum class CropMode {
        CENTER,
        RULE_OF_THIRDS,
        SALIENCY,
        FACE_AWARE,
        FILL,
        AUTO
    }

    /** Cache of final crop rectangles keyed by image content + target + mode. */
    private val cropRectCache = object : LinkedHashMap<String, CropRect>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CropRect>): Boolean = size > 64
    }

    // =====================================================================================
    // Public entry points
    // =====================================================================================

    /**
     * Synchronous smart crop (saliency-only). Used by the Glide preview
     * transformation and as the fallback for the async face-aware path.
     */
    fun smartCropBitmap(
        source: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        mode: CropMode = CropMode.AUTO,
        preserveQuality: Boolean = true
    ): Bitmap = smartCropInternal(source, targetWidth, targetHeight, mode, preserveQuality, faces = null)

    /**
     * Suspending smart crop with face awareness. The four wallpaper-apply
     * call sites (all `suspend`) should use this so FACE_AWARE/AUTO actually
     * prioritise faces via skin-tone detection instead of the old stub.
     */
    suspend fun smartCropBitmapAsync(
        source: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        mode: CropMode = CropMode.AUTO,
        preserveQuality: Boolean = true
    ): Bitmap = withContext(Dispatchers.Default) {
        val faces = if (mode == CropMode.FACE_AWARE || mode == CropMode.AUTO) {
            try { FaceCropDetector.detectFaces(source) } catch (e: Exception) { null }
        } else null
        smartCropInternal(source, targetWidth, targetHeight, mode, preserveQuality, faces)
    }

    // =====================================================================================
    // Core
    // =====================================================================================

    private fun smartCropInternal(
        source: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        mode: CropMode,
        preserveQuality: Boolean,
        faces: List<FaceCropDetector.Face>?
    ): Bitmap {
        if (source.width == targetWidth && source.height == targetHeight) return source
        if (source.width <= 0 || source.height <= 0 || targetWidth <= 0 || targetHeight <= 0) {
            Log.w(TAG, "Invalid dimensions for smartCropBitmap")
            return source
        }
        return try {
            val sourceAspect = source.width.toFloat() / source.height.toFloat()
            val targetAspect = targetWidth.toFloat() / targetHeight.toFloat()
            val aspectDifference = abs(sourceAspect - targetAspect)

            // Optional quality headroom when the source has plenty of resolution.
            val qualityScale = if (preserveQuality &&
                source.width >= targetWidth * 1.5f && source.height >= targetHeight * 1.5f
            ) 1.25f else 1.0f
            val outputWidth = (targetWidth * qualityScale).roundToInt()
            val outputHeight = (targetHeight * qualityScale).roundToInt()

            // Nearly identical aspect → just scale.
            if (aspectDifference < 0.01f) return scaleBitmap(source, outputWidth, outputHeight)

            // Explicit FILL.
            if (mode == CropMode.FILL) return fillBitmap(source, outputWidth, outputHeight)

            // AUTO: fill for extreme mismatches where content would be destroyed.
            if (mode == CropMode.AUTO && shouldFill(sourceAspect, targetAspect)) {
                return fillBitmap(source, outputWidth, outputHeight)
            }

            // CENTER is a plain centre crop.
            if (mode == CropMode.CENTER) return centerCropBitmap(source, outputWidth, outputHeight)

            // Content-aware path: build focal points, choose rectangle, cut.
            val focals = buildFocalPoints(source, mode, faces)
            val cacheKey = cropCacheKey(source, outputWidth, outputHeight, mode, faces)
            val rect = cropRectCache.getOrPut(cacheKey) {
                val horizonY = detectHorizon(source)
                val engineFocals = focals.map {
                    CropEngine.FocalPoint(it.x, it.y, it.weight, it.type)
                }
                CropEngine.optimalCrop(
                    sourceWidth = source.width,
                    sourceHeight = source.height,
                    targetWidth = outputWidth,
                    targetHeight = outputHeight,
                    focalPoints = engineFocals,
                    horizonY = horizonY,
                    options = CropOptions()
                )
            }
            applyCrop(source, rect, outputWidth, outputHeight)
        } catch (e: Exception) {
            Log.e(TAG, "Error in smartCropBitmap", e)
            centerCropBitmap(source, targetWidth, targetHeight)
        }
    }

    /**
     * AUTO heuristic for switching to FILL (no content loss) on brutal mismatches.
     *
     * Only genuine ultra-wide panoramas (source aspect > 2.0) aimed at a portrait
     * screen trigger FILL — there, a cover crop would discard almost the entire
     * image. Regular landscape wallpapers (16:9, 16:10, 4:3 ...) must be cropped
     * to the target aspect like every other wallpaper; the previous
     * `aspectDifference > 0.8 && entropy > 0.65` branch incorrectly routed busy
     * 16:9 desktop wallpapers through FILL, leaving large blurred gaps above and
     * below the fitted image.
     */
    private fun shouldFill(
        sourceAspect: Float,
        targetAspect: Float
    ): Boolean {
        val targetIsPortrait = targetAspect < 1f
        // Very wide panorama → portrait: cropping would discard almost everything.
        if (sourceAspect > 2.0f && targetIsPortrait) return true
        return false
    }

    /** Build focal points in source coordinates for [mode], merging [faces] when present. */
    private fun buildFocalPoints(
        source: Bitmap,
        mode: CropMode,
        faces: List<FaceCropDetector.Face>?
    ): List<Focal> {
        if (mode == CropMode.CENTER) return listOf(Focal(source.width / 2f, source.height / 2f, 1f, FocalType.COMPOSITION))

        val points = ArrayList<Focal>()

        // Faces dominate when available; CropEngine scores them 1000x so a face
        // always becomes the "strongest" point driving thirds/centroid terms.
        if (faces != null && faces.isNotEmpty()) {
            for (f in faces) {
                val cx = f.centerX
                val cy = f.centerY
                // Weight larger, central, upright faces higher.
                val area = f.width * f.height
                val sizeBoost = (area / (source.width * source.height * 0.05f)).coerceIn(0.5f, 2f)
                val centerness = 1f - abs(cx - source.width / 2f) / (source.width / 2f)
                points.add(Focal(cx, cy, 2.5f * sizeBoost * (0.6f + 0.4f * centerness), FocalType.FACE))
            }
        }

        // Always include saliency so non-face subjects (landmarks, text, objects)
        // still factor in, unless the caller explicitly asked for FACE_AWARE only.
        if (mode != CropMode.FACE_AWARE || faces.isNullOrEmpty()) {
            for (p in SaliencyDetector.detectSalientRegions(source)) {
                points.add(Focal(p.x, p.y, p.weight, FocalType.SALIENCY))
            }
        }
        if (points.isEmpty()) points.add(Focal(source.width / 2f, source.height / 2f, 1f, FocalType.COMPOSITION))
        return points
    }

    /** Internal focal point carrying the engine [FocalType]. */
    private data class Focal(val x: Float, val y: Float, val weight: Float, val type: FocalType)

    // =====================================================================================
    // Bitmap operations
    // =====================================================================================

    /**
     * Cut [rect] out of [source] and scale to [targetWidth]*[targetHeight].
     *
     * Every coordinate is rounded and clamped so the crop lies fully within the
     * source and is at least 1px on each side — the guard the old code lacked,
     * which made [Bitmap.createBitmap] throw and silently drop to centre crop.
     */
    private fun applyCrop(source: Bitmap, rect: CropRect, targetWidth: Int, targetHeight: Int): Bitmap {
        val sw = source.width
        val sh = source.height
        var w = rect.width.roundToInt()
        var h = rect.height.roundToInt()
        if (w < 1) w = 1
        if (h < 1) h = 1
        if (w > sw) w = sw
        if (h > sh) h = sh
        var x = rect.left.roundToInt()
        var y = rect.top.roundToInt()
        if (x < 0) x = 0
        if (y < 0) y = 0
        if (x > sw - w) x = sw - w
        if (y > sh - h) y = sh - h
        if (x < 0 || y < 0 || w <= 0 || h <= 0 || x + w > sw || y + h > sh) {
            Log.w(TAG, "applyCrop bounds still invalid after clamp; centre-cropping")
            return centerCropBitmap(source, targetWidth, targetHeight)
        }
        val cropped = try {
            Bitmap.createBitmap(source, x, y, w, h)
        } catch (e: Exception) {
            Log.e(TAG, "createBitmap failed; centre-cropping", e)
            return centerCropBitmap(source, targetWidth, targetHeight)
        }
        return if (cropped.width != targetWidth || cropped.height != targetHeight) {
            val scaled = scaleBitmap(cropped, targetWidth, targetHeight)
            if (scaled != cropped) cropped.recycle()
            scaled
        } else cropped
    }

    /** Simple bilinear scale. */
    private fun scaleBitmap(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap =
        try {
            Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
        } catch (e: Exception) {
            Log.e(TAG, "Error scaling bitmap", e); source
        }

    /** Centre crop fallback matching the target aspect. */
    private fun centerCropBitmap(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap = try {
        val sourceAspect = source.width.toFloat() / source.height.toFloat()
        val targetAspect = targetWidth.toFloat() / targetHeight.toFloat()
        val cropW: Float; val cropH: Float
        if (sourceAspect > targetAspect) {
            cropH = source.height.toFloat(); cropW = cropH * targetAspect
        } else {
            cropW = source.width.toFloat(); cropH = cropW / targetAspect
        }
        val x = ((source.width - cropW) / 2f).roundToInt().coerceAtLeast(0)
        val y = ((source.height - cropH) / 2f).roundToInt().coerceAtLeast(0)
        val w = cropW.roundToInt().coerceAtMost(source.width - x)
        val h = cropH.roundToInt().coerceAtMost(source.height - y)
        val cropped = Bitmap.createBitmap(source, x, y, w, h)
        if (cropped.width != targetWidth || cropped.height != targetHeight) {
            val scaled = scaleBitmap(cropped, targetWidth, targetHeight)
            if (scaled != cropped) cropped.recycle()
            scaled
        } else cropped
    } catch (e: Exception) {
        Log.e(TAG, "Error in centerCropBitmap", e); source
    }

    /**
     * Fill the target by drawing a genuinely blurred version of the source
     * covering the canvas, then compositing the sharp source fitted inside.
     *
     * Replaces the old 1/40th blocky upscale + 67% black crush with an
     * integral-image box blur on a 1/16th downscale (a real low-pass), a light
     * scrim for separation, and a soft shadow rect behind the fitted image.
     */
    private fun fillBitmap(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap = try {
        val out = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        // --- Blurred background: downscale, box-blur, upscale-to-cover ---
        val bgLongMax = 128
        val bgScale = bgLongMax.toFloat() / max(source.width, source.height)
        val bgW = max(1, (source.width * bgScale).roundToInt())
        val bgH = max(1, (source.height * bgScale).roundToInt())
        val down = Bitmap.createScaledBitmap(source, bgW, bgH, true)
        val blurred = blurBitmap(down, radius = max(3, min(bgW, bgH) / 6))
        if (down !== blurred) down.recycle()

        val coverScale = max(targetWidth.toFloat() / bgW, targetHeight.toFloat() / bgH)
        val drawW = bgW * coverScale
        val drawH = bgH * coverScale
        val drawLeft = (targetWidth - drawW) / 2f
        val drawTop = (targetHeight - drawH) / 2f
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(blurred, null, RectF(drawLeft, drawTop, drawLeft + drawW, drawTop + drawH), bgPaint)
        blurred.recycle()

        // Light scrim for subject separation (was 67% black — far too heavy).
        val scrim = Paint().apply { color = 0x33000000 }
        canvas.drawRect(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat(), scrim)

        // --- Sharp foreground: fit inside the target, centred ---
        val fitScale = min(targetWidth.toFloat() / source.width, targetHeight.toFloat() / source.height)
        val fitW = source.width * fitScale
        val fitH = source.height * fitScale
        val fitLeft = (targetWidth - fitW) / 2f
        val fitTop = (targetHeight - fitH) / 2f

        // Soft shadow rect behind the fitted image.
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x55000000
            setShadowLayer(24f, 0f, 0f, 0x99000000.toInt())
        }
        canvas.drawRect(RectF(fitLeft, fitTop, fitLeft + fitW, fitTop + fitH), shadowPaint)
        canvas.drawRect(RectF(fitLeft, fitTop, fitLeft + fitW, fitTop + fitH),
            Paint().apply { color = 0x00000000 })

        val fitMatrix = Matrix().apply {
            postScale(fitScale, fitScale)
            postTranslate(fitLeft, fitTop)
        }
        canvas.drawBitmap(source, fitMatrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        out
    } catch (e: Exception) {
        Log.e(TAG, "Error in fillBitmap", e); scaleBitmap(source, targetWidth, targetHeight)
    }

    /**
     * Integral-image box blur over ARGB channels. One pass with a square kernel
     * of half-extent [radius]; a genuine low-pass, unlike the old nearest-1/40th.
     */
    private fun blurBitmap(src: Bitmap, radius: Int): Bitmap {
        if (radius < 1 || src.width < 3 || src.height < 3) return src
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val out = boxBlurArgb(px, w, h, radius)
        return Bitmap.createBitmap(out, 0, w, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun boxBlurArgb(pixels: IntArray, w: Int, h: Int, radius: Int): IntArray {
        val n = w * h
        // Per-channel summed-area tables.
        val iiR = LongArray((w + 1) * (h + 1))
        val iiG = LongArray((w + 1) * (h + 1))
        val iiB = LongArray((w + 1) * (h + 1))
        for (y in 1..h) {
            var sr = 0L; var sg = 0L; var sb = 0L
            val row = (y - 1) * w
            for (x in 1..w) {
                val c = pixels[row + x - 1]
                sr += (c shr 16) and 0xff
                sg += (c shr 8) and 0xff
                sb += c and 0xff
                val idx = y * (w + 1) + x
                val prev = (y - 1) * (w + 1) + x
                iiR[idx] = iiR[prev] + sr
                iiG[idx] = iiG[prev] + sg
                iiB[idx] = iiB[prev] + sb
            }
        }
        val out = IntArray(n)
        val r = radius
        for (y in 0 until h) {
            val y0 = max(0, y - r)
            val y1 = min(h - 1, y + r)
            val rows = (y1 - y0 + 1)
            for (x in 0 until w) {
                val x0 = max(0, x - r)
                val x1 = min(w - 1, x + r)
                val cols = (x1 - x0 + 1)
                val area = rows * cols
                val a = y0 * (w + 1) + x0
                val b = y0 * (w + 1) + x1 + 1
                val c = (y1 + 1) * (w + 1) + x0
                val d = (y1 + 1) * (w + 1) + x1 + 1
                val rr = ((iiR[d] - iiR[b] - iiR[c] + iiR[a]) / area).toInt().coerceIn(0, 255)
                val gg = ((iiG[d] - iiG[b] - iiG[c] + iiG[a]) / area).toInt().coerceIn(0, 255)
                val bb = ((iiB[d] - iiB[b] - iiB[c] + iiB[a]) / area).toInt().coerceIn(0, 255)
                out[y * w + x] = (0xff shl 24) or (rr shl 16) or (gg shl 8) or bb
            }
        }
        return out
    }

    // =====================================================================================
    // Horizon detection — strongest horizontal luminance edge, cheap on a downscale
    // =====================================================================================

    /** Return the source-space y of a dominant horizon, or null if none is found. */
    private fun detectHorizon(source: Bitmap): Float? {
        if (source.width < 8 || source.height < 8) return null
        return try {
            val targetW = 64
            val scale = targetW.toFloat() / source.width
            val th = max(4, (source.height * scale).toInt())
            val small = Bitmap.createScaledBitmap(source, targetW, th, true)
            val px = IntArray(targetW * th)
            small.getPixels(px, 0, targetW, 0, 0, targetW, th)
            small.recycle()
            // Row luminance means.
            val rowLum = FloatArray(th) { 0f }
            for (y in 0 until th) {
                var s = 0f
                val row = y * targetW
                for (x in 0 until targetW) {
                    val c = px[row + x]
                    s += 0.299f * ((c shr 16) and 0xff) + 0.587f * ((c shr 8) and 0xff) + 0.114f * (c and 0xff)
                }
                rowLum[y] = s / targetW
            }
            // Find the row with the largest neighbour gradient (strong horizontal edge).
            var bestY = -1
            var bestGrad = 0f
            for (y in 1 until th) {
                val g = abs(rowLum[y] - rowLum[y - 1])
                if (g > bestGrad) { bestGrad = g; bestY = y }
            }
            // Only accept a clear horizon: gradient must be a meaningful fraction of range.
            val range = (rowLum.maxOrNull() ?: 0f) - (rowLum.minOrNull() ?: 0f)
            if (bestY < 0 || bestGrad < range * 0.25f) null else bestY / scale
        } catch (e: Exception) {
            null
        }
    }

    // =====================================================================================
    // Cache key
    // =====================================================================================

    private fun cropCacheKey(
        source: Bitmap,
        outputWidth: Int,
        outputHeight: Int,
        mode: CropMode,
        faces: List<FaceCropDetector.Face>?
    ): String {
        val sig = SaliencyDetector.contentSignature(source)
        val faceSig = if (faces.isNullOrEmpty()) "none" else faces.joinToString(",") {
            "${it.centerX.toInt()},${it.centerY.toInt()},${it.width.toInt()}"
        }
        return "$sig|${outputWidth}x${outputHeight}|${mode.name}|$faceSig"
    }
}
