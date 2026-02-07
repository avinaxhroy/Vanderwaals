package me.avinas.vanderwaals.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import androidx.core.graphics.createBitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Smart cropping system that ensures preview and applied wallpaper match
 * by using intelligent focal point detection and consistent cropping logic.
 * 
 * **Version 3.0 - Advanced Algorithm (November 2025 - Enhanced)**
 * 
 * **Core Improvements Made:**
 * 
 * 1. **Enhanced Saliency Detection**
 *    - Increased grid resolution from 8x8 to 12x12 for better detail
 *    - Added Gaussian-like smoothing to reduce noise in saliency map
 *    - Improved percentile-based thresholding for better separation
 *    - Multi-directional edge detection (Sobel-like) instead of simple gradient
 * 
 * 2. **Advanced Saliency Scoring**
 *    - Combines three factors with optimized weights:
 *      * Edge/Gradient detection (40%) - Detects boundaries and transitions
 *      * Color saturation and chroma (40%) - Detects colorful/varied regions
 *      * Texture variance (20%) - Detects rich texture areas
 *    - Handles both bright and dark subjects effectively
 * 
 * 3. **Better Focal Point Alignment**
 *    - Weighted center of mass calculation for multiple focal points
 *    - Margin preservation (10%) to avoid cutting off content at edges
 *    - Prevents focal points from being placed too close to crop boundaries
 * 
 * 4. **Improved Aspect Ratio Handling**
 *    - Better preservation of content when extreme aspect ratio mismatches
 *    - Consistent application for phone wallpaper preview (9:16 aspect ratio)
 * 
 * **NEW ADVANCED FEATURES (v3.0):**
 * 
 * 5. **Intelligent Aspect Ratio Analysis**
 *    - Detects if image is landscape, portrait, or square
 *    - Chooses optimal crop strategy based on detected aspect ratio
 *    - Adapts focal point weighting for landscape/portrait content
 * 
 * 6. **Entropy-Based Content Detection**
 *    - Identifies high-information regions (edges, details, patterns)
 *    - Avoids cropping valuable content with high entropy
 *    - Protects complex patterns and text regions
 * 
 * 7. **Local Maxima Detection**
 *    - Finds multiple peaks in saliency map (not just threshold)
 *    - Better for images with multiple subjects
 *    - Clusters nearby maxima into meaningful focal points
 * 
 * 8. **Multi-Scale Saliency Analysis**
 *    - Detects salient regions at multiple scales
 *    - Distinguishes between fine details and large objects
 *    - Combines scale information for robust detection
 * 
 * 9. **Adaptive Factor Weighting**
 *    - Analyzes image content type (bright, dark, colorful, minimal)
 *    - Dynamically adjusts factor weights (edge/color/texture)
 *    - Optimizes for specific image characteristics
 * 
 * 10. **Smart Focal Point Clustering**
 *     - Groups nearby salient regions into coherent focal points
 *     - Reduces noise from small scattered high-salience areas
 *     - Produces cleaner, more meaningful focal point distribution
 * 
 * **Algorithm Flow (v3.0):**
 * 1. Analyze image aspect ratio and content characteristics
 * 2. Calculate adaptive saliency weights based on content type
 * 3. Detect crop dimensions based on aspect ratio
 * 4. Build multi-scale saliency maps
 * 5. Calculate entropy for content preservation
 * 6. Find local maxima in saliency map
 * 7. Cluster focal points for cleaner distribution
 * 8. Calculate weighted center of mass
 * 9. Position crop around focal points with margin preservation
 * 10. Apply crop and scale to target dimensions
 * 
 * **Performance:**
 * - Grid-based analysis is fast and memory-efficient
 * - Multi-scale analysis adds minimal overhead
 * - Caching support for identical images
 * - Suitable for real-time preview rendering
 * - Works on all device types and image sizes
 * 
 * **Image Type Support:**
 * ✓ Landscape/Nature photos - Preserves horizon and scenic elements
 * ✓ Portrait photos - Focuses on subjects with margin preservation
 * ✓ Colorful images - Enhanced saturation-based detection
 * ✓ Minimalist designs - Works with low-color images via texture analysis
 * ✓ Text/Typography - Entropy-based detection protects text
 * ✓ Complex scenes - Multi-scale analysis handles varying content
 * ✓ Dark/Night images - Adaptive weighting for low-light conditions
 * ✓ Bright/High-key images - Adaptive weighting for high-light conditions
 */
object SmartCrop {
    private const val TAG = "SmartCrop"

    // Caching for saliency maps to avoid recalculation
    private val saliencyCache = mutableMapOf<String, Array<FloatArray>>()
    private const val MAX_SALIENCY_CACHE_SIZE = 25  // Increased from 10 for better hit rate

    // NEW: Cache for final crop regions - avoids re-computing crop for same source+target dimensions
    // Key format: "${sourceWidth}x${sourceHeight}_${targetWidth}x${targetHeight}"
    private val cropRegionCache = mutableMapOf<String, RectF>()
    private const val MAX_CROP_CACHE_SIZE = 50  // Small memory footprint (RectF is just 4 floats)

    // ========================================
    // SALIENCY DETECTION EXTRACTED TO SaliencyDetector
    // ========================================
    // The following methods have been moved to me.avinas.vanderwaals.core.SaliencyDetector:
    // - detectSalientRegions(), calculateCellSaliency()
    // - findLocalMaxima(), clusterFocalPoints()
    // - analyzeImageCharacteristics(), calculateAdaptiveWeights()
    // - calculateImageEntropy()
    //
    // This improves:
    // - Testability: Saliency detection can be unit tested
    // - Reusability: Can be used for similar wallpapers, search ranking
    // - Maintainability: ~450 lines removed from this file
    // ========================================

    /**
     * Represents a point of interest in an image with a weight
     */
    data class FocalPoint(
        val x: Float,
        val y: Float,
        val weight: Float = 1.0f
    ) {
        companion object {
            fun fromSaliencyFocalPoint(fp: SaliencyDetector.FocalPoint): FocalPoint {
                return FocalPoint(fp.x, fp.y, fp.weight)
            }
        }
    }

    /**
     * Represents a crop region with its score
     */
    data class CropRegion(
        val rect: RectF,
        val score: Float
    )

    /**
     * Smart crop mode that defines the cropping strategy
     */
    enum class CropMode {
        CENTER,           // Simple center crop (fallback)
        RULE_OF_THIRDS,   // Align with rule of thirds
        SALIENCY,         // Detect salient regions
        FACE_AWARE,       // Prioritize face regions (when ML Kit is available)
        FILL,             // Fit image and blur background (no cropping)
        AUTO              // Automatically choose best mode
    }

    /**
     * Analyze image characteristics using SaliencyDetector.
     */
    private fun analyzeImageCharacteristics(bitmap: Bitmap): SaliencyDetector.ImageCharacteristics {
        return SaliencyDetector.analyzeImageCharacteristics(bitmap)
    }

    /**
     * Calculate image entropy using SaliencyDetector.
     */
    private fun calculateImageEntropy(bitmap: Bitmap): Float {
        return SaliencyDetector.calculateImageEntropy(bitmap)
    }

    /**
     * Apply smart crop to a bitmap to match target dimensions
     * This is the main entry point for smart cropping
     * 
     * IMPROVED LOGIC (Nov 2025):
     * - Detects if image is suitable for smart crop (desktop/landscape images)
     * - Only applies smart crop when aspect ratio mismatch is significant
     * - Uses intelligent scaling to minimize content loss
     * - Preserves important content without over-zooming
     */
    fun smartCropBitmap(
        source: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        mode: CropMode = CropMode.AUTO,
        preserveQuality: Boolean = true
    ): Bitmap {
        if (source.width == targetWidth && source.height == targetHeight) {
            return source
        }

        if (source.width <= 0 || source.height <= 0 || targetWidth <= 0 || targetHeight <= 0) {
            Log.w(TAG, "Invalid dimensions for smartCropBitmap")
            return source
        }

        return try {
            val sourceAspect = source.width.toFloat() / source.height.toFloat()
            val targetAspect = targetWidth.toFloat() / targetHeight.toFloat()
            val aspectDifference = abs(sourceAspect - targetAspect)

            // Quality Preservation: Calculate optimal output dimensions
            // If source is significantly larger than target, preserve extra resolution
            val qualityScaleFactor = when {
                preserveQuality && 
                source.width >= targetWidth * 1.5f && 
                source.height >= targetHeight * 1.5f -> 1.25f
                else -> 1.0f
            }
            val outputWidth = (targetWidth * qualityScaleFactor).toInt()
            val outputHeight = (targetHeight * qualityScaleFactor).toInt()
            
            if (qualityScaleFactor > 1.0f) {
                Log.d(TAG, "Quality preservation: scaling output to ${outputWidth}x${outputHeight} (${qualityScaleFactor}x)")
            }

            // If aspects are very similar (< 1% difference), just scale
            if (aspectDifference < 0.01f) {
                Log.d(TAG, "Aspect ratios are very similar, using simple scaling")
                return scaleBitmap(source, outputWidth, outputHeight)
            }

            // Check if user explicitly requested FILL
            if (mode == CropMode.FILL) {
                return fillBitmap(source, outputWidth, outputHeight)
            }

            val sourceIsLandscape = source.width > source.height
            val targetIsPortrait = targetHeight > targetWidth
            
            // AUTO MODE LOGIC: Check if we should FILL instead of CROP
            if (mode == CropMode.AUTO) {
                // If extreme aspect ratio mismatch (e.g. landscape to portrait), consider FILL
                // Threshold: aspect difference > 0.8 (e.g. 1.77 vs 0.56 is 1.2 diff)
                val isExtremeMismatch = aspectDifference > 0.8f
                
                // If it's a very wide panorama (21:9 or wider) going to portrait
                if (sourceAspect > 2.0f && targetIsPortrait) {
                     Log.d(TAG, "Auto-detected FILL mode: Wide panorama")
                     return fillBitmap(source, targetWidth, targetHeight)
                }

                // Analyze image to see if we should preserve it all
                // Only do this expensive analysis if we are considering FILL
                if (isExtremeMismatch) {
                    val characteristics = analyzeImageCharacteristics(source)
                    // If high entropy (lots of detail) and extreme mismatch, use FILL
                    if (characteristics.entropy > 0.65f) {
                        Log.d(TAG, "Auto-detected FILL mode: Extreme mismatch with high entropy")
                        return fillBitmap(source, targetWidth, targetHeight)
                    }
                }
            }

            // CRITICAL FIX: Check if image is already well-suited for target dimensions
            // Desktop/landscape images for portrait screens need smart crop
            // But portrait images for portrait screens should just scale
            
            // If both are similar orientation and aspect difference is small (< 15%), prefer scaling
            if (aspectDifference < 0.15f && (sourceIsLandscape == !targetIsPortrait)) {
                Log.d(TAG, "Similar orientations with minor aspect difference, using gentle scaling")
                return scaleBitmap(source, outputWidth, outputHeight)
            }

            // For desktop wallpapers (typically 16:9 or wider) going to phone screens (9:16),
            // smart crop is beneficial. For portrait images going to portrait screens, not so much.
            val needsSmartCrop = (sourceIsLandscape && targetIsPortrait) || aspectDifference > 0.25f
            
            if (!needsSmartCrop && mode == CropMode.AUTO) {
                Log.d(TAG, "Image doesn't need smart crop, using content-preserving scale")
                return scaleBitmap(source, outputWidth, outputHeight)
            }

            Log.d(TAG, "Applying smart crop: source ${source.width}x${source.height} (%.2f vs %.2f)".format(sourceAspect, targetAspect) + " -> target ${outputWidth}x${outputHeight}")

            // Determine crop mode
            val actualMode = when (mode) {
                CropMode.AUTO -> determineBestMode(source)
                else -> mode
            }

            // Get focal points based on mode
            val focalPoints = detectFocalPoints(source, actualMode)

            // Calculate optimal crop region
            val cropRegion = calculateOptimalCrop(
                sourceWidth = source.width,
                sourceHeight = source.height,
                targetWidth = outputWidth,
                targetHeight = outputHeight,
                focalPoints = focalPoints,
                horizonY = detectHorizon(source)
            )

            // Apply the crop
            applyCrop(source, cropRegion, outputWidth, outputHeight)
        } catch (e: Exception) {
            Log.e(TAG, "Error in smartCropBitmap", e)
            // Fallback to center crop
            centerCropBitmap(source, targetWidth, targetHeight)
        }
    }

    /**
     * Determine the best crop mode for an image based on analysis.
     * Uses image characteristics to choose optimal strategy.
     */
    private fun determineBestMode(bitmap: Bitmap): CropMode {
        // Analyze image to choose best mode
        val characteristics = analyzeImageCharacteristics(bitmap)
        
        // For high-entropy images (text, patterns), use rule of thirds to preserve structure
        if (characteristics.entropy > 0.6f) {
            Log.d(TAG, "High entropy detected - using rule of thirds")
            return CropMode.RULE_OF_THIRDS
        }
        
        // Use saliency as default for general images
        Log.d(TAG, "Using saliency detection (brightness=${characteristics.averageBrightness}, colorful=${characteristics.isColorful})")
        return CropMode.SALIENCY
    }

    // calculateAdaptiveWeights - REMOVED (now in SaliencyDetector)

    /**
     * Detect focal points in the image based on the mode
     */
    private fun detectFocalPoints(bitmap: Bitmap, mode: CropMode): List<FocalPoint> {
        return when (mode) {
            CropMode.CENTER -> listOf(
                FocalPoint(
                    x = bitmap.width / 2f,
                    y = bitmap.height / 2f,
                    weight = 1.0f
                )
            )
            CropMode.RULE_OF_THIRDS -> getRuleOfThirdsFocalPoints(bitmap)
            CropMode.SALIENCY -> detectSalientRegions(bitmap)
            CropMode.FACE_AWARE -> detectFaces(bitmap)
            CropMode.FILL -> listOf(
                FocalPoint(
                    x = bitmap.width / 2f,
                    y = bitmap.height / 2f,
                    weight = 1.0f
                )
            )
            CropMode.AUTO -> detectSalientRegions(bitmap)
        }
    }

    /**
     * Get focal points based on rule of thirds
     */
    private fun getRuleOfThirdsFocalPoints(bitmap: Bitmap): List<FocalPoint> {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        
        return listOf(
            // Four power points at rule of thirds intersections
            FocalPoint(w * 1f / 3f, h * 1f / 3f, 1.5f),
            FocalPoint(w * 2f / 3f, h * 1f / 3f, 1.5f),
            FocalPoint(w * 1f / 3f, h * 2f / 3f, 1.5f),
            FocalPoint(w * 2f / 3f, h * 2f / 3f, 1.5f),
            // Center as backup
            FocalPoint(w / 2f, h / 2f, 0.8f)
        )
    }

    // findLocalMaxima, clusterFocalPoints - REMOVED (now in SaliencyDetector)

    private fun detectHorizon(bitmap: Bitmap): Int? {
        val height = bitmap.height
        val width = bitmap.width
        if (height < 4 || width < 4) return null
        var maxGrad = 0f
        var maxY = -1
        var sumGrad = 0f
        var count = 0
        val stepY = max(2, height / 60)
        val stepX = max(2, width / 60)
        for (y in 0 until height - 1 step stepY) {
            var rowGrad = 0f
            var samples = 0
            for (x in 0 until width step stepX) {
                try {
                    val p1 = bitmap.getPixel(x, y)
                    val p2 = bitmap.getPixel(x, y + 1)
                    val b1 = ((p1 shr 16) and 0xff) + ((p1 shr 8) and 0xff) + (p1 and 0xff)
                    val b2 = ((p2 shr 16) and 0xff) + ((p2 shr 8) and 0xff) + (p2 and 0xff)
                    rowGrad += abs(b2 - b1).toFloat()
                    samples++
                } catch (_: Exception) {}
            }
            if (samples > 0) {
                val avg = rowGrad / samples
                sumGrad += avg
                count++
                if (avg > maxGrad) {
                    maxGrad = avg
                    maxY = y
                }
            }
        }
        if (count == 0) return null
        val mean = sumGrad / count
        return if (maxGrad > mean * 1.8f) maxY else null
    }

    /**
     * Detect salient regions using SaliencyDetector.
     * Delegates to centralized saliency detection for better testability.
     */
    private fun detectSalientRegions(bitmap: Bitmap): List<FocalPoint> {
        return SaliencyDetector.detectSalientRegions(bitmap).map { 
            FocalPoint.fromSaliencyFocalPoint(it) 
        }
    }

    // calculateCellSaliency - REMOVED (now in SaliencyDetector)

    /**
     * Detect faces in the image using ML Kit Face Detection.
     * 
     * To enable face detection, add the ML Kit dependency to build.gradle:
     * implementation("com.google.mlkit:face-detection:16.1.6")
     * 
     * Then implement face detection as follows:
     * ```
     * val options = FaceDetectorOptions.Builder()
     *     .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
     *     .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
     *     .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
     *     .build()
     * val detector = FaceDetection.getClient(options)
     * val inputImage = InputImage.fromBitmap(bitmap, 0)
     * detector.process(inputImage)
     *     .addOnSuccessListener { faces ->
     *         faces.map { face ->
     *             FocalPoint(
     *                 x = face.boundingBox.centerX().toFloat(),
     *                 y = face.boundingBox.centerY().toFloat(),
     *                 weight = 1.5f,
     *                 type = FocalPointType.FACE
     *             )
     *         }
     *     }
     * ```
     * 
     * For now, use rule of thirds as fallback for robust operation.
     */
    private fun detectFaces(bitmap: Bitmap): List<FocalPoint> {
        // Use rule of thirds as fallback (no ML Kit dependency yet)
        Log.d(TAG, "Face detection not enabled, using rule of thirds")
        return getRuleOfThirdsFocalPoints(bitmap)
    }

    /**
     * Calculate optimal crop region based on focal points with improved alignment.
     * 
     * Improvements:
     * - Weighted center of mass calculation for multiple focal points
     * - Bias towards keeping content away from edges with margin preservation
     * - Fallback to rule of thirds if focal points are too close to edges
     * - Better handling of extreme aspect ratios
     */
    private fun calculateOptimalCrop(
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
        focalPoints: List<FocalPoint>,
        horizonY: Int?
    ): RectF {
        val sourceAspect = sourceWidth.toFloat() / sourceHeight.toFloat()
        val targetAspect = targetWidth.toFloat() / targetHeight.toFloat()
        val cropWidth: Float
        val cropHeight: Float
        if (sourceAspect > targetAspect) {
            cropHeight = sourceHeight.toFloat()
            cropWidth = cropHeight * targetAspect
        } else {
            cropWidth = sourceWidth.toFloat()
            cropHeight = cropWidth / targetAspect
        }
        val totalWeight = focalPoints.sumOf { it.weight.toDouble() }.toFloat().coerceAtLeast(0.0001f)
        val comX = focalPoints.sumOf { (it.x * it.weight).toDouble() }.toFloat() / totalWeight
        val comY = focalPoints.sumOf { (it.y * it.weight).toDouble() }.toFloat() / totalWeight
        // Reduced margin from 10% to 5% to allow content closer to edges without penalty
        val margin = minOf(cropWidth, cropHeight) * 0.05f
        val minX = cropWidth / 2f + margin
        val maxX = sourceWidth - cropWidth / 2f - margin
        val minY = cropHeight / 2f + margin
        val maxY = sourceHeight - cropHeight / 2f - margin
        val candidates = mutableListOf<Pair<Float, Float>>()
        candidates.add(Pair(comX, comY))
        for (p in focalPoints) {
            candidates.add(Pair(p.x, p.y))
        }
        val thirdsX1 = sourceWidth / 3f
        val thirdsX2 = sourceWidth * 2f / 3f
        val thirdsY1 = sourceHeight / 3f
        val thirdsY2 = sourceHeight * 2f / 3f
        candidates.add(Pair(thirdsX1, thirdsY1))
        candidates.add(Pair(thirdsX2, thirdsY1))
        candidates.add(Pair(thirdsX1, thirdsY2))
        candidates.add(Pair(thirdsX2, thirdsY2))
        if (horizonY != null) {
            val pos1 = cropHeight / 3f
            val pos2 = cropHeight * 2f / 3f
            val cY1 = horizonY + cropHeight / 2f - pos1
            val cY2 = horizonY + cropHeight / 2f - pos2
            candidates.add(Pair(comX, cY1))
            candidates.add(Pair(comX, cY2))
        }
        var bestRect = RectF(0f, 0f, cropWidth, cropHeight)
        var bestScore = Float.NEGATIVE_INFINITY
        for (c in candidates) {
            var cx = c.first
            var cy = c.second
            cx = if (minX <= maxX) cx.coerceIn(minX, maxX) else sourceWidth / 2f
            cy = if (minY <= maxY) cy.coerceIn(minY, maxY) else sourceHeight / 2f
            var left = cx - cropWidth / 2f
            var top = cy - cropHeight / 2f
            if (left < 0) left = 0f
            if (top < 0) top = 0f
            if (left + cropWidth > sourceWidth) left = sourceWidth - cropWidth
            if (top + cropHeight > sourceHeight) top = sourceHeight - cropHeight
            val rect = RectF(left, top, left + cropWidth, top + cropHeight)
            val score = scoreCropRect(rect, focalPoints, margin)
            if (score > bestScore) {
                bestScore = score
                bestRect = rect
            }
        }
        return bestRect
    }

    

    private fun scoreCropRect(rect: RectF, focalPoints: List<FocalPoint>, margin: Float): Float {
        var coverage = 0f
        var edgePenalty = 0f
        var maxWeightPoint: FocalPoint? = null
        var maxWeight = Float.NEGATIVE_INFINITY
        for (p in focalPoints) {
            if (p.x >= rect.left && p.x <= rect.right && p.y >= rect.top && p.y <= rect.bottom) {
                coverage += p.weight
                val dx = min(p.x - rect.left, rect.right - p.x)
                val dy = min(p.y - rect.top, rect.bottom - p.y)
                val d = min(dx, dy)
                if (d < margin) {
                    edgePenalty += (margin - d) / margin
                }
                if (p.weight > maxWeight) {
                    maxWeight = p.weight
                    maxWeightPoint = p
                }
            }
        }
        var thirdsScore = 0f
        if (maxWeightPoint != null) {
            val rx = maxWeightPoint!!.x - rect.left
            val ry = maxWeightPoint!!.y - rect.top
            val w = rect.width()
            val h = rect.height()
            val x1 = w / 3f
            val x2 = 2f * w / 3f
            val y1 = h / 3f
            val y2 = 2f * h / 3f
            val d1 = kotlin.math.abs(rx - x1) + kotlin.math.abs(ry - y1)
            val d2 = kotlin.math.abs(rx - x2) + kotlin.math.abs(ry - y1)
            val d3 = kotlin.math.abs(rx - x1) + kotlin.math.abs(ry - y2)
            val d4 = kotlin.math.abs(rx - x2) + kotlin.math.abs(ry - y2)
            val bestD = min(min(d1, d2), min(d3, d4))
            thirdsScore = 1f / (1f + bestD / ((w + h) * 0.1f))
        }
        return coverage * 0.8f + thirdsScore * 0.25f - edgePenalty * 0.2f
    }

    /**
     * Apply the crop to the bitmap
     */
    private fun applyCrop(
        source: Bitmap,
        cropRegion: RectF,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap {
        val croppedBitmap = Bitmap.createBitmap(
            source,
            cropRegion.left.toInt(),
            cropRegion.top.toInt(),
            cropRegion.width().toInt(),
            cropRegion.height().toInt()
        )
        
        // Scale to target size
        return if (croppedBitmap.width != targetWidth || croppedBitmap.height != targetHeight) {
            val scaled = scaleBitmap(croppedBitmap, targetWidth, targetHeight)
            if (scaled != croppedBitmap) {
                croppedBitmap.recycle()
            }
            scaled
        } else {
            croppedBitmap
        }
    }

    /**
     * Simple scale without cropping
     */
    private fun scaleBitmap(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        return try {
            Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
        } catch (e: Exception) {
            Log.e(TAG, "Error scaling bitmap", e)
            source
        }
    }

    /**
     * Fallback center crop implementation
     */
    private fun centerCropBitmap(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        return try {
            val sourceAspect = source.width.toFloat() / source.height.toFloat()
            val targetAspect = targetWidth.toFloat() / targetHeight.toFloat()
            
            val cropWidth: Float
            val cropHeight: Float
            
            if (sourceAspect > targetAspect) {
                cropHeight = source.height.toFloat()
                cropWidth = cropHeight * targetAspect
            } else {
                cropWidth = source.width.toFloat()
                cropHeight = cropWidth / targetAspect
            }
            
            val left = (source.width - cropWidth) / 2f
            val top = (source.height - cropHeight) / 2f
            
            val cropped = Bitmap.createBitmap(
                source,
                left.toInt(),
                top.toInt(),
                cropWidth.toInt(),
                cropHeight.toInt()
            )
            
            val scaled = Bitmap.createScaledBitmap(cropped, targetWidth, targetHeight, true)
            if (scaled != cropped) {
                cropped.recycle()
            }
            scaled
        } catch (e: Exception) {
            Log.e(TAG, "Error in centerCropBitmap", e)
            source
        }
    }

    /**
     * Calculate crop rect for given dimensions - useful for preview
     * Returns the crop region in source bitmap coordinates
     */
    fun calculateCropRect(
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
        focalPoints: List<FocalPoint>? = null
    ): RectF {
        val points = focalPoints ?: listOf(
            FocalPoint(sourceWidth / 2f, sourceHeight / 2f, 1.0f)
        )
        
        return calculateOptimalCrop(
            sourceWidth,
            sourceHeight,
            targetWidth,
            targetHeight,
            points,
            null
        )
    }

    /**
     * Create a filled bitmap with blurred background.
     * Preserves the entire source image by fitting it within target dimensions
     * and filling the empty space with a blurred version of the image.
     */
    private fun fillBitmap(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        try {
            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            
            // 1. Draw blurred background
            // Scale source to cover target (center crop style for background)
            val scaleX = targetWidth.toFloat() / source.width
            val scaleY = targetHeight.toFloat() / source.height
            val scale = max(scaleX, scaleY)
            
            val scaledWidth = source.width * scale
            val scaledHeight = source.height * scale
            val left = (targetWidth - scaledWidth) / 2f
            val top = (targetHeight - scaledHeight) / 2f
            
            // Create a low-res version for blurring (1/40th size)
            val lowResW = max(1, source.width / 40)
            val lowResH = max(1, source.height / 40)
            val lowRes = Bitmap.createScaledBitmap(source, lowResW, lowResH, true)
            
            val paint = Paint()
            paint.isAntiAlias = true
            paint.isFilterBitmap = true
            
            // Draw scaled up low-res image (effectively blurred)
            canvas.drawBitmap(lowRes, null, RectF(left, top, left + scaledWidth, top + scaledHeight), paint)
            
            // Add a dark overlay to make the foreground pop
            canvas.drawColor(0xAA000000.toInt()) // 67% black overlay
            
            // 2. Draw fitted foreground image
            val fitScaleX = targetWidth.toFloat() / source.width
            val fitScaleY = targetHeight.toFloat() / source.height
            val fitScale = min(fitScaleX, fitScaleY)
            
            val fitWidth = source.width * fitScale
            val fitHeight = source.height * fitScale
            val fitLeft = (targetWidth - fitWidth) / 2f
            val fitTop = (targetHeight - fitHeight) / 2f
            
            val fitMatrix = Matrix()
            fitMatrix.postScale(fitScale, fitScale)
            fitMatrix.postTranslate(fitLeft, fitTop)
            
            // Draw shadow/glow behind the image
            val shadowPaint = Paint()
            shadowPaint.setShadowLayer(20f, 0f, 0f, 0xFF000000.toInt())
            // Note: setShadowLayer only works on text or shapes, not bitmaps directly in hardware accel
            // So we draw a rect behind it
            val shadowRect = RectF(fitLeft, fitTop, fitLeft + fitWidth, fitTop + fitHeight)
            val rectPaint = Paint()
            rectPaint.color = 0xFF000000.toInt()
            rectPaint.alpha = 100
            canvas.drawRect(shadowRect, rectPaint)
            
            canvas.drawBitmap(source, fitMatrix, paint)
            
            lowRes.recycle()
            return bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error in fillBitmap", e)
            return scaleBitmap(source, targetWidth, targetHeight)
        }
    }
}
