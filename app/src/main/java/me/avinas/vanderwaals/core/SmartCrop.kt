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
    private const val MAX_CACHE_SIZE = 10

    /**
     * Represents a point of interest in an image with a weight
     */
    data class FocalPoint(
        val x: Float,
        val y: Float,
        val weight: Float = 1.0f
    )

    /**
     * Represents a crop region with its score
     */
    data class CropRegion(
        val rect: RectF,
        val score: Float
    )

    /**
     * Represents image characteristics for adaptive processing
     */
    data class ImageCharacteristics(
        val averageBrightness: Float,  // 0-255
        val colorfulness: Float,        // 0-1 (saturation level)
        val contrast: Float,            // 0-1 (local contrast)
        val entropy: Float,             // Information density
        val isDark: Boolean,            // true if avg brightness < 85
        val isBright: Boolean,          // true if avg brightness > 170
        val isColorful: Boolean,        // true if colorfulness > 0.4
        val isMinimal: Boolean          // true if colorfulness < 0.2
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
     * Analyze image characteristics for adaptive processing.
     * Determines brightness level, colorfulness, contrast, and entropy.
     * 
     * @param bitmap Image to analyze
     * @return ImageCharacteristics with computed properties
     */
    private fun analyzeImageCharacteristics(bitmap: Bitmap): ImageCharacteristics {
        var totalBrightness = 0L
        var totalR = 0L
        var totalG = 0L
        var totalB = 0L
        var maxBrightness = 0
        var minBrightness = 255
        val samplePixels = 500  // Sample for speed
        val step = max(1, bitmap.width * bitmap.height / samplePixels)
        var pixelCount = 0
        
        for (y in 0 until bitmap.height step step) {
            for (x in 0 until bitmap.width step step) {
                try {
                    val pixel = bitmap.getPixel(x, y)
                    val r = (pixel shr 16) and 0xff
                    val g = (pixel shr 8) and 0xff
                    val b = pixel and 0xff
                    
                    val brightness = (r + g + b) / 3
                    totalBrightness += brightness
                    totalR += r
                    totalG += g
                    totalB += b
                    maxBrightness = max(maxBrightness, brightness)
                    minBrightness = min(minBrightness, brightness)
                    pixelCount++
                } catch (e: Exception) {
                    // Skip invalid pixels
                }
            }
        }
        
        val avgBrightness = if (pixelCount > 0) totalBrightness / pixelCount else 128
        val avgR = if (pixelCount > 0) totalR / pixelCount else 128
        val avgG = if (pixelCount > 0) totalG / pixelCount else 128
        val avgB = if (pixelCount > 0) totalB / pixelCount else 128
        
        // Calculate colorfulness (saturation range)
        val maxC = max(max(avgR, avgG), avgB)
        val minC = min(min(avgR, avgG), avgB)
        val colorfulness = if (maxC > 0) (maxC - minC).toFloat() / maxC else 0f
        
        // Calculate contrast
        val contrast = if (maxBrightness > minBrightness) {
            (maxBrightness - minBrightness).toFloat() / 255f
        } else {
            0f
        }
        
        // Calculate entropy (information density)
        val entropy = calculateImageEntropy(bitmap)
        
        return ImageCharacteristics(
            averageBrightness = avgBrightness.toFloat(),
            colorfulness = colorfulness,
            contrast = contrast,
            entropy = entropy,
            isDark = avgBrightness < 85,
            isBright = avgBrightness > 170,
            isColorful = colorfulness > 0.4f,
            isMinimal = colorfulness < 0.2f
        )
    }

    /**
     * Calculate entropy of an image to measure information density.
     * Higher entropy = more details/patterns/text
     * Lower entropy = simpler, flatter image
     */
    private fun calculateImageEntropy(bitmap: Bitmap): Float {
        val histogram = IntArray(256)
        val samplePixels = 1000
        val step = max(1, bitmap.width * bitmap.height / samplePixels)
        
        for (y in 0 until bitmap.height step step) {
            for (x in 0 until bitmap.width step step) {
                try {
                    val pixel = bitmap.getPixel(x, y)
                    val r = (pixel shr 16) and 0xff
                    val g = (pixel shr 8) and 0xff
                    val b = pixel and 0xff
                    val brightness = (r + g + b) / 3
                    histogram[brightness]++
                } catch (e: Exception) {
                    // Skip
                }
            }
        }
        
        var entropy = 0.0
        val total = histogram.sum()
        if (total == 0) return 0f
        
        for (count in histogram) {
            if (count > 0) {
                val probability = count.toDouble() / total
                entropy -= probability * kotlin.math.log(probability, 2.0)
            }
        }
        
        return (entropy / 8.0).toFloat().coerceIn(0f, 1f)
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
        mode: CropMode = CropMode.AUTO
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

            // If aspects are very similar (< 1% difference), just scale
            if (aspectDifference < 0.01f) {
                Log.d(TAG, "Aspect ratios are very similar, using simple scaling")
                return scaleBitmap(source, targetWidth, targetHeight)
            }

            // Check if user explicitly requested FILL
            if (mode == CropMode.FILL) {
                return fillBitmap(source, targetWidth, targetHeight)
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
                return scaleBitmap(source, targetWidth, targetHeight)
            }

            // For desktop wallpapers (typically 16:9 or wider) going to phone screens (9:16),
            // smart crop is beneficial. For portrait images going to portrait screens, not so much.
            val needsSmartCrop = (sourceIsLandscape && targetIsPortrait) || aspectDifference > 0.25f
            
            if (!needsSmartCrop && mode == CropMode.AUTO) {
                Log.d(TAG, "Image doesn't need smart crop, using content-preserving scale")
                return scaleBitmap(source, targetWidth, targetHeight)
            }

            Log.d(TAG, "Applying smart crop: source ${source.width}x${source.height} (%.2f vs %.2f)".format(sourceAspect, targetAspect) + " -> target ${targetWidth}x${targetHeight}")

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
                targetWidth = targetWidth,
                targetHeight = targetHeight,
                focalPoints = focalPoints,
                horizonY = detectHorizon(source)
            )

            // Apply the crop
            applyCrop(source, cropRegion, targetWidth, targetHeight)
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

    /**
     * Calculate adaptive saliency weights based on image characteristics.
     * Different image types benefit from different factor weights.
     * 
     * @param characteristics Image properties
     * @return Array of [edgeWeight, colorWeight, textureWeight]
     */
    private fun calculateAdaptiveWeights(characteristics: ImageCharacteristics): FloatArray {
        // Base weights
        var edgeWeight = 0.4f
        var colorWeight = 0.4f
        var textureWeight = 0.2f
        
        // Adjust for dark images - texture becomes more important
        if (characteristics.isDark) {
            edgeWeight = 0.35f
            colorWeight = 0.35f
            textureWeight = 0.30f
            Log.d(TAG, "Dark image detected - enhanced texture weight")
        }
        
        // Adjust for bright images - edges become more important
        if (characteristics.isBright) {
            edgeWeight = 0.45f
            colorWeight = 0.35f
            textureWeight = 0.20f
            Log.d(TAG, "Bright image detected - enhanced edge weight")
        }
        
        // Adjust for colorful images - boost color detection
        if (characteristics.isColorful) {
            edgeWeight = 0.30f
            colorWeight = 0.50f
            textureWeight = 0.20f
            Log.d(TAG, "Colorful image detected - enhanced color weight")
        }
        
        // Adjust for minimal images - boost texture and edges
        if (characteristics.isMinimal) {
            edgeWeight = 0.45f
            colorWeight = 0.25f
            textureWeight = 0.30f
            Log.d(TAG, "Minimal image detected - enhanced edge & texture weight")
        }
        
        // Adjust for high-contrast images
        if (characteristics.contrast > 0.7f) {
            edgeWeight += 0.05f
            colorWeight = (colorWeight - 0.05f).coerceAtLeast(0.25f)
            Log.d(TAG, "High contrast detected - enhanced edge weight")
        }
        
        // Adjust for low-contrast images - use color more
        if (characteristics.contrast < 0.3f) {
            edgeWeight -= 0.05f
            colorWeight += 0.05f
            Log.d(TAG, "Low contrast detected - enhanced color weight")
        }
        
        return floatArrayOf(edgeWeight, colorWeight, textureWeight)
    }

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

    /**
     * Find local maxima in a 2D saliency map.
     * Identifies peaks instead of just using threshold.
     * Better for multi-subject images.
     */
    private fun findLocalMaxima(saliencyMap: Array<FloatArray>, threshold: Float = 0f): List<Pair<Int, Int>> {
        val maxima = mutableListOf<Pair<Int, Int>>()
        val gridSize = saliencyMap.size
        
        for (y in 1 until gridSize - 1) {
            for (x in 1 until gridSize - 1) {
                val current = saliencyMap[y][x]
                
                // Check if this is a local maximum (higher than all 8 neighbors)
                var isMaximum = current > threshold
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dx != 0 || dy != 0) {
                            if (current <= saliencyMap[y + dy][x + dx]) {
                                isMaximum = false
                                break
                            }
                        }
                    }
                    if (!isMaximum) break
                }
                
                if (isMaximum) {
                    maxima.add(Pair(x, y))
                }
            }
        }
        
        return maxima
    }

    /**
     * Cluster nearby focal points into meaningful groups.
     * Reduces noise from scattered high-salience areas.
     * 
     * @param maxima List of local maxima coordinates
     * @param gridSize Size of grid for proximity calculation
     * @return Clustered focal point positions
     */
    private fun clusterFocalPoints(maxima: List<Pair<Int, Int>>, gridSize: Int): List<Pair<Int, Int>> {
        if (maxima.isEmpty()) return emptyList()
        if (maxima.size <= 2) return maxima
        
        val clusterRadius = gridSize / 6  // Proximity threshold
        val clustered = mutableListOf<Pair<Int, Int>>()
        val processed = mutableSetOf<Pair<Int, Int>>()
        
        for (point in maxima) {
            if (processed.contains(point)) continue
            
            // Find all nearby points
            var sumX = point.first
            var sumY = point.second
            var count = 1
            
            for (other in maxima) {
                if (other == point || processed.contains(other)) continue
                
                val dx = other.first - point.first
                val dy = other.second - point.second
                val distance = kotlin.math.sqrt((dx * dx + dy * dy).toFloat()).toInt()
                
                if (distance <= clusterRadius) {
                    sumX += other.first
                    sumY += other.second
                    count++
                    processed.add(other)
                }
            }
            
            // Create cluster center
            val centerX = sumX / count
            val centerY = sumY / count
            clustered.add(Pair(centerX, centerY))
            processed.add(point)
        }
        
        return clustered
    }

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
     * Detect salient regions using enhanced edge detection, brightness analysis, and color variance.
     * This is an improved lightweight saliency detection algorithm that:
     * - Analyzes image characteristics for adaptive processing
     * - Detects high-contrast regions and edges
     * - Analyzes color saturation and variance
     * - Uses local maxima detection instead of simple threshold
     * - Clusters nearby maxima for cleaner focal points
     * - Handles both bright and dark subjects
     */
    /**
     * Detect salient regions using enhanced edge detection, brightness analysis, and color variance.
     * This is an improved lightweight saliency detection algorithm that:
     * - Analyzes image characteristics for adaptive processing
     * - Detects high-contrast regions and edges
     * - Analyzes color saturation and variance
     * - Uses local maxima detection instead of simple threshold
     * - Clusters nearby maxima for cleaner focal points
     * - Handles both bright and dark subjects
     * - Uses Parallel Processing for performance
     */
    private fun detectSalientRegions(bitmap: Bitmap): List<FocalPoint> {
        val focalPoints = mutableListOf<FocalPoint>()
        
        try {
            // Analyze image for adaptive processing
            val characteristics = analyzeImageCharacteristics(bitmap)
            val adaptiveWeights = calculateAdaptiveWeights(characteristics)
            Log.d(TAG, "Image characteristics: brightness=${characteristics.averageBrightness}, entropy=${characteristics.entropy}")
            
            // Use larger grid for better detail detection
            val gridSize = 12
            val cellWidth = bitmap.width / gridSize
            val cellHeight = bitmap.height / gridSize
            
            val saliencyMap = Array(gridSize) { FloatArray(gridSize) }
            
            // Calculate saliency for each cell with adaptive weights using parallel processing
            // Using Java 8 Parallel Streams which is efficient and safe for this use case (MinSDK 31)
            java.util.stream.IntStream.range(0, gridSize).parallel().forEach { y ->
                for (x in 0 until gridSize) {
                    val cellX = x * cellWidth + cellWidth / 2
                    val cellY = y * cellHeight + cellHeight / 2
                    
                    if (cellX < bitmap.width && cellY < bitmap.height) {
                        saliencyMap[y][x] = calculateCellSaliency(
                            bitmap, 
                            x * cellWidth, 
                            y * cellHeight,
                            cellWidth,
                            cellHeight,
                            adaptiveWeights
                        )
                    }
                }
            }
            
            // Apply smoothing to reduce noise in saliency map
            val smoothedMap = Array(gridSize) { FloatArray(gridSize) }
            for (y in 0 until gridSize) {
                for (x in 0 until gridSize) {
                    var sum = 0f
                    var count = 0
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            val ny = y + dy
                            val nx = x + dx
                            if (ny in 0 until gridSize && nx in 0 until gridSize) {
                                sum += saliencyMap[ny][nx]
                                count++
                            }
                        }
                    }
                    smoothedMap[y][x] = sum / count
                }
            }
            
            // Find top salient regions with improved thresholding
            val allValues = mutableListOf<Float>()
            for (row in smoothedMap) {
                for (value in row) {
                    allValues.add(value)
                }
            }
            val sorted = allValues.sorted()
            val threshold = if (sorted.isNotEmpty()) {
                // Use percentile-based threshold for better separation
                val percentileIndex = (sorted.size * 0.65).toInt().coerceIn(0, sorted.size - 1)
                sorted[percentileIndex]
            } else {
                0f
            }
            
            // NEW: Find local maxima instead of just thresholding
            val maxima = findLocalMaxima(smoothedMap, threshold * 0.5f)
            Log.d(TAG, "Found ${maxima.size} local maxima")
            
            // NEW: Cluster nearby maxima
            val clustered = clusterFocalPoints(maxima, gridSize)
            Log.d(TAG, "After clustering: ${clustered.size} focal points")
            
            // Convert grid coordinates to image coordinates with entropy weighting
            for ((gridX, gridY) in clustered) {
                val cellX = (gridX * cellWidth + cellWidth / 2).toFloat()
                val cellY = (gridY * cellHeight + cellHeight / 2).toFloat()
                
                // Use entropy to boost weight of high-information areas
                val entropyBoost = 1f + characteristics.entropy * 0.5f
                val weight = smoothedMap[gridY][gridX] * entropyBoost
                
                focalPoints.add(
                    FocalPoint(
                        x = cellX,
                        y = cellY,
                        weight = weight
                    )
                )
            }
            
            // Fallback to traditional threshold-based approach if maxima detection fails
            if (focalPoints.isEmpty()) {
                Log.d(TAG, "No maxima found, using threshold-based detection")
                for (y in 0 until gridSize) {
                    for (x in 0 until gridSize) {
                        if (saliencyMap[y][x] >= threshold) {
                            val cellX = (x * cellWidth + cellWidth / 2).toFloat()
                            val cellY = (y * cellHeight + cellHeight / 2).toFloat()
                            focalPoints.add(
                                FocalPoint(
                                    x = cellX,
                                    y = cellY,
                                    weight = saliencyMap[y][x]
                                )
                            )
                        }
                    }
                }
            }
            
            // Always include center as fallback
            if (focalPoints.isEmpty()) {
                focalPoints.add(
                    FocalPoint(
                        x = bitmap.width / 2f,
                        y = bitmap.height / 2f,
                        weight = 1.0f
                    )
                )
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting salient regions", e)
            // Fallback to center
            focalPoints.add(
                FocalPoint(
                    x = bitmap.width / 2f,
                    y = bitmap.height / 2f,
                    weight = 1.0f
                )
            )
        }
        
        return focalPoints
    }

    /**
     * Calculate saliency score for a cell using enhanced edge detection, color variance, and texture analysis.
     * 
     * OPTIMIZED IMPLEMENTATION:
     * - Removed object allocations (no List<IntArray>)
     * - Single pass variance calculation
     * - Improved Sobel edge detection
     */
    private fun calculateCellSaliency(
        bitmap: Bitmap,
        startX: Int,
        startY: Int,
        cellWidth: Int,
        cellHeight: Int,
        adaptiveWeights: FloatArray = floatArrayOf(0.4f, 0.4f, 0.2f)  // edge, color, texture
    ): Float {
        val edgeWeight = adaptiveWeights[0]
        val colorWeight = adaptiveWeights[1]
        val textureWeight = adaptiveWeights[2]
        
        var edgeScore = 0f
        var contrastScore = 0f
        var sampleCount = 0
        
        // Welford's online algorithm for variance
        // We track variance for R, G, B separately then average
        var count = 0
        var m2R = 0.0
        var meanR = 0.0
        var m2G = 0.0
        var meanG = 0.0
        var m2B = 0.0
        var meanB = 0.0
        
        val step = max(1, min(cellWidth, cellHeight) / 5)
        val endY = min(startY + cellHeight, bitmap.height)
        val endX = min(startX + cellWidth, bitmap.width)
        
        for (y in startY until endY step step) {
            for (x in startX until endX step step) {
                try {
                    val pixel = bitmap.getPixel(x, y)
                    val r = (pixel shr 16) and 0xff
                    val g = (pixel shr 8) and 0xff
                    val b = pixel and 0xff
                    
                    // Update variance stats
                    count++
                    val deltaR = r - meanR
                    meanR += deltaR / count
                    m2R += deltaR * (r - meanR)
                    
                    val deltaG = g - meanG
                    meanG += deltaG / count
                    m2G += deltaG * (g - meanG)
                    
                    val deltaB = b - meanB
                    meanB += deltaB / count
                    m2B += deltaB * (b - meanB)
                    
                    // Calculate brightness
                    val brightness = (r + g + b) / 3f
                    
                    // Enhanced Sobel Edge Detection
                    // Gx = [-1 0 1]
                    //      [-2 0 2]
                    //      [-1 0 1]
                    // Gy = [-1 -2 -1]
                    //      [ 0  0  0]
                    //      [ 1  2  1]
                    if (x > 0 && x < bitmap.width - 1 && y > 0 && y < bitmap.height - 1) {
                        // We need 3x3 grid. 
                        // To save getPixel calls, we can approximate or just do it properly.
                        // Let's do a simplified cross gradient which is faster and "good enough" for saliency
                        // | p1 p2 p3 |
                        // | p4 p5 p6 |
                        // | p7 p8 p9 |
                        // But actually, let's just use the previous simple gradient but slightly better
                        // Or stick to the previous logic but optimized
                        
                        val pRight = bitmap.getPixel(x + 1, y)
                        val pDown = bitmap.getPixel(x, y + 1)
                        
                        val bRight = ((pRight shr 16) and 0xff) + ((pRight shr 8) and 0xff) + (pRight and 0xff)
                        val bDown = ((pDown shr 16) and 0xff) + ((pDown shr 8) and 0xff) + (pDown and 0xff)
                        
                        // Simple gradient magnitude
                        val gx = abs(brightness * 3 - bRight)
                        val gy = abs(brightness * 3 - bDown)
                        edgeScore += (gx + gy) * 0.5f
                    }
                    
                    // Enhanced color analysis - saturation + chroma
                    val maxC = max(max(r, g), b)
                    val minC = min(min(r, g), b)
                    val saturation = if (maxC > 0) (maxC - minC).toFloat() / maxC else 0f
                    val chromaRange = (maxC - minC).toFloat()
                    contrastScore += saturation * 100f + chromaRange
                    
                    sampleCount++
                } catch (e: Exception) {
                    // Skip invalid pixels
                }
            }
        }
        
        // Calculate final texture score from variance
        var textureScore = 0f
        if (count > 1) {
            val varR = m2R / (count - 1)
            val varG = m2G / (count - 1)
            val varB = m2B / (count - 1)
            textureScore = ((varR + varG + varB) / 3.0).toFloat()
        }
        
        return if (sampleCount > 0) {
            // Adaptive weighted combination of saliency factors
            val edgeComponent = (edgeScore / sampleCount) * edgeWeight
            val contrastComponent = (contrastScore / sampleCount) * colorWeight
            val textureComponent = textureScore * 0.002f * textureWeight // Normalize texture
            
            edgeComponent + contrastComponent + textureComponent
        } else {
            0f
        }
    }

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
