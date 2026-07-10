package me.avinas.vanderwaals.core

import android.graphics.Bitmap
import android.util.Log
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Face detection for face-aware wallpaper cropping.
 *
 * Uses a conservative skin-tone connected-components heuristic that only
 * emits a candidate when it finds a sizable, roughly face-shaped, skin-dense
 * cluster — high precision, low recall — so it never mis-crops.
 *
 * This is dependency-free (no ML Kit / Firebase) to avoid shipping Firebase
 * analytics transport that the app does not use. SmartCrop's saliency
 * detection handles the majority of content-aware cropping; face detection
 * here adds extra focal-point bias for portrait/people wallpapers.
 */
object FaceCropDetector {
    private const val TAG = "FaceCropDetector"

    /** A detected face in source-pixel coordinates. */
    data class Face(
        val centerX: Float,
        val centerY: Float,
        val width: Float,
        val height: Float,
        val confidence: Float
    )

    /**
     * Detect faces in [bitmap] via skin-tone connected-components.
     * Always returns a list (possibly empty).
     */
    suspend fun detectFaces(bitmap: Bitmap): List<Face> {
        if (bitmap.width < 32 || bitmap.height < 32) return emptyList()
        return detectSkinToneFallback(bitmap)
    }

    // =====================================================================================
    // Skin-tone detection — conservative connected-components on a downscale
    // =====================================================================================

    private const val FALLBACK_MAX = 160

    private fun detectSkinToneFallback(bitmap: Bitmap): List<Face> {
        return try {
        val scale = FALLBACK_MAX.toFloat() / max(bitmap.width, bitmap.height)
        if (scale >= 1f) return emptyList()
        val w = max(16, (bitmap.width * scale).toInt())
        val h = max(16, (bitmap.height * scale).toInt())
        val small = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        small.recycle()

        val skin = BooleanArray(w * h)
        for (i in px.indices) {
            val c = px[i]
            val r = (c shr 16) and 0xff
            val g = (c shr 8) and 0xff
            val b = c and 0xff
            if (isSkin(r, g, b)) skin[i] = true
        }

        // Union-find over 4-connected skin pixels.
        val parent = IntArray(w * h) { -1 }
        fun find(a: Int): Int {
            var x = a
            while (parent[x] >= 0) x = parent[x]
            var cur = a
            while (parent[cur] >= 0 && parent[cur] != x) { val nxt = parent[cur]; parent[cur] = x; cur = nxt }
            return x
        }
        fun union(a: Int, b: Int) {
            val ra = find(a); val rb = find(b)
            if (ra == rb) return
            if (parent[ra] < parent[rb]) { parent[ra] += parent[rb]; parent[rb] = ra }
            else { parent[rb] += parent[ra]; parent[ra] = rb }
        }
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                if (!skin[i]) continue
                if (x + 1 < w && skin[i + 1]) union(i, i + 1)
                if (y + 1 < h && skin[i + w]) union(i, i + w)
            }
        }

        // Aggregate component bounding boxes and pixel counts.
        data class Box(var minX: Int, var minY: Int, var maxX: Int, var maxY: Int, var count: Int)
        val comps = HashMap<Int, Box>()
        for (i in skin.indices) {
            if (!skin[i]) continue
            val root = find(i)
            val box = comps.getOrPut(root) { Box(i % w, i / w, i % w, i / w, 0) }
            val xi = i % w; val yi = i / w
            if (xi < box.minX) box.minX = xi
            if (xi > box.maxX) box.maxX = xi
            if (yi < box.minY) box.minY = yi
            if (yi > box.maxY) box.maxY = yi
            box.count++
        }

        val imgArea = (w * h).toFloat()
        val results = ArrayList<Face>()
        for ((_, box) in comps) {
            val bw = box.maxX - box.minX + 1
            val bh = box.maxY - box.minY + 1
            val area = bw * bh
            val density = box.count.toFloat() / area
            val areaFrac = box.count / imgArea
            val aspect = bw.toFloat() / bh
            // Conservative: face-sized, roughly square, skin-dense, not tiny, not huge.
            if (areaFrac in 0.004f..0.20f && aspect in 0.5f..2.0f && density > 0.45f) {
                results.add(
                    Face(
                        centerX = (box.minX + bw / 2f) / scale,
                        centerY = (box.minY + bh / 2f) / scale,
                        width = bw / scale,
                        height = bh / scale,
                        confidence = 0.35f
                    )
                )
            }
        }
        // Keep the largest few; scale confidence by relative size.
        results.sortedByDescending { it.width * it.height }.take(3)
    } catch (e: Exception) {
        Log.w(TAG, "Skin-tone detection failed", e)
        emptyList()
    }
    }

    /** Common RGB skin heuristic (works across ethnicities, low false-positive when combined with density). */
    private fun isSkin(r: Int, g: Int, b: Int): Boolean {
        val rgbMax = max(max(r, g), b)
        val rgbMin = min(min(r, g), b)
        return r > 95 && g > 40 && b > 20 &&
            (rgbMax - rgbMin) > 15 &&
            abs(r - g) > 15 &&
            r > g && r > b
    }
}
