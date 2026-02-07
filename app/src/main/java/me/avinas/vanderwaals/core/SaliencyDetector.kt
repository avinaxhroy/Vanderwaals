package me.avinas.vanderwaals.core

import android.graphics.Bitmap
import android.util.Log
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Saliency detection for smart image cropping.
 * 
 * Extracted from SmartCrop to improve:
 * - **Single Responsibility**: Saliency detection is isolated
 * - **Testability**: Detection algorithms can be unit tested
 * - **Reusability**: Can be used for similar wallpapers, search ranking, etc.
 * 
 * **Algorithm Overview:**
 * 1. Build saliency map using edge, color, and texture analysis
 * 2. Find local maxima in saliency map (peaks)
 * 3. Cluster nearby maxima into focal points
 * 4. Weight focal points by image entropy
 * 
 * @see SmartCrop
 */
object SaliencyDetector {
    private const val TAG = "SaliencyDetector"
    
    /**
     * Represents a point of interest in an image with a weight.
     */
    data class FocalPoint(
        val x: Float,
        val y: Float,
        val weight: Float = 1.0f
    )
    
    /**
     * Image characteristics for adaptive processing.
     */
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
    
    /**
     * Detects salient (visually important) regions in an image.
     * 
     * Uses multi-factor analysis:
     * - Edge detection (Sobel-like gradient)
     * - Color saturation and chroma
     * - Texture variance
     * 
     * @param bitmap Image to analyze
     * @return List of focal points with weights
     */
    fun detectSalientRegions(bitmap: Bitmap): List<FocalPoint> {
        val focalPoints = mutableListOf<FocalPoint>()
        
        // Guard: If bitmap is too small, just return center
        if (bitmap.width < 12 || bitmap.height < 12) {
            Log.d(TAG, "Bitmap too small (${bitmap.width}x${bitmap.height}), returning center")
            return listOf(FocalPoint(bitmap.width / 2f, bitmap.height / 2f, 1.0f))
        }
        
        try {
            val characteristics = analyzeImageCharacteristics(bitmap)
            val adaptiveWeights = calculateAdaptiveWeights(characteristics)
            Log.d(TAG, "Image: brightness=${characteristics.averageBrightness.toInt()}, entropy=${String.format("%.2f", characteristics.entropy)}")
            
            val gridSize = 12
            val cellWidth = bitmap.width / gridSize
            val cellHeight = bitmap.height / gridSize
            
            val saliencyMap = Array(gridSize) { FloatArray(gridSize) }
            
            // Calculate saliency using parallel processing
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
            
            // Apply Gaussian-like smoothing
            val smoothedMap = applySmoothingFilter(saliencyMap, gridSize)
            
            // Calculate threshold using percentile
            val threshold = calculatePercentileThreshold(smoothedMap, 0.65f)
            
            // Find local maxima
            val maxima = findLocalMaxima(smoothedMap, threshold * 0.5f)
            Log.d(TAG, "Found ${maxima.size} local maxima")
            
            // Cluster nearby maxima
            val clustered = clusterFocalPoints(maxima, gridSize)
            Log.d(TAG, "After clustering: ${clustered.size} focal points")
            
            // Convert grid coords to image coords
            for ((gridX, gridY) in clustered) {
                val cellX = (gridX * cellWidth + cellWidth / 2).toFloat()
                val cellY = (gridY * cellHeight + cellHeight / 2).toFloat()
                
                val entropyBoost = 1f + characteristics.entropy * 0.5f
                val weight = smoothedMap[gridY][gridX] * entropyBoost
                
                focalPoints.add(FocalPoint(x = cellX, y = cellY, weight = weight))
            }
            
            // Fallback to threshold-based if no maxima found
            if (focalPoints.isEmpty()) {
                Log.d(TAG, "No maxima found, using threshold-based detection")
                addThresholdBasedFocalPoints(saliencyMap, gridSize, cellWidth, cellHeight, threshold, focalPoints)
            }
            
            // Always include center as fallback
            if (focalPoints.isEmpty()) {
                focalPoints.add(FocalPoint(bitmap.width / 2f, bitmap.height / 2f, 1.0f))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting salient regions", e)
            focalPoints.add(FocalPoint(bitmap.width / 2f, bitmap.height / 2f, 1.0f))
        }
        
        return focalPoints
    }
    
    /**
     * Analyzes image characteristics for adaptive processing.
     */
    fun analyzeImageCharacteristics(bitmap: Bitmap): ImageCharacteristics {
        var totalBrightness = 0L
        var totalR = 0L
        var totalG = 0L
        var totalB = 0L
        var maxBrightness = 0
        var minBrightness = 255
        val samplePixels = 500
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
        
        val maxC = max(max(avgR, avgG), avgB)
        val minC = min(min(avgR, avgG), avgB)
        val colorfulness = if (maxC > 0) (maxC - minC).toFloat() / maxC else 0f
        
        val contrast = if (maxBrightness > minBrightness) {
            (maxBrightness - minBrightness).toFloat() / 255f
        } else 0f
        
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
     * Calculates image entropy (information density).
     * Higher entropy = more details/patterns/text.
     */
    fun calculateImageEntropy(bitmap: Bitmap): Float {
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
                } catch (e: Exception) { }
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
     * Calculates adaptive saliency weights based on image characteristics.
     */
    fun calculateAdaptiveWeights(characteristics: ImageCharacteristics): FloatArray {
        var edgeWeight = 0.4f
        var colorWeight = 0.4f
        var textureWeight = 0.2f
        
        if (characteristics.isDark) {
            edgeWeight = 0.35f
            colorWeight = 0.35f
            textureWeight = 0.30f
        }
        
        if (characteristics.isBright) {
            edgeWeight = 0.45f
            colorWeight = 0.35f
            textureWeight = 0.20f
        }
        
        if (characteristics.isColorful) {
            edgeWeight = 0.30f
            colorWeight = 0.50f
            textureWeight = 0.20f
        }
        
        if (characteristics.isMinimal) {
            edgeWeight = 0.45f
            colorWeight = 0.25f
            textureWeight = 0.30f
        }
        
        if (characteristics.contrast > 0.7f) {
            edgeWeight += 0.05f
            colorWeight = (colorWeight - 0.05f).coerceAtLeast(0.25f)
        }
        
        if (characteristics.contrast < 0.3f) {
            edgeWeight -= 0.05f
            colorWeight += 0.05f
        }
        
        return floatArrayOf(edgeWeight, colorWeight, textureWeight)
    }
    
    /**
     * Calculates saliency score for a single cell.
     * Uses edge detection, color analysis, and texture variance.
     */
    private fun calculateCellSaliency(
        bitmap: Bitmap,
        startX: Int,
        startY: Int,
        cellWidth: Int,
        cellHeight: Int,
        adaptiveWeights: FloatArray
    ): Float {
        val edgeWeight = adaptiveWeights[0]
        val colorWeight = adaptiveWeights[1]
        val textureWeight = adaptiveWeights[2]
        
        var edgeScore = 0f
        var contrastScore = 0f
        var sampleCount = 0
        
        // Welford's online algorithm for variance
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
                    
                    val brightness = (r + g + b) / 3f
                    
                    // Edge detection
                    if (x > 0 && x < bitmap.width - 1 && y > 0 && y < bitmap.height - 1) {
                        val pRight = bitmap.getPixel(x + 1, y)
                        val pDown = bitmap.getPixel(x, y + 1)
                        
                        val bRight = ((pRight shr 16) and 0xff) + ((pRight shr 8) and 0xff) + (pRight and 0xff)
                        val bDown = ((pDown shr 16) and 0xff) + ((pDown shr 8) and 0xff) + (pDown and 0xff)
                        
                        val gx = abs(brightness * 3 - bRight)
                        val gy = abs(brightness * 3 - bDown)
                        edgeScore += (gx + gy) * 0.5f
                    }
                    
                    // Color analysis
                    val maxC = max(max(r, g), b)
                    val minC = min(min(r, g), b)
                    val saturation = if (maxC > 0) (maxC - minC).toFloat() / maxC else 0f
                    val chromaRange = (maxC - minC).toFloat()
                    contrastScore += saturation * 100f + chromaRange
                    
                    sampleCount++
                } catch (e: Exception) { }
            }
        }
        
        // Calculate texture score from variance
        var textureScore = 0f
        if (count > 1) {
            val varR = m2R / (count - 1)
            val varG = m2G / (count - 1)
            val varB = m2B / (count - 1)
            textureScore = ((varR + varG + varB) / 3.0).toFloat()
        }
        
        return if (sampleCount > 0) {
            val edgeComponent = (edgeScore / sampleCount) * edgeWeight
            val contrastComponent = (contrastScore / sampleCount) * colorWeight
            val textureComponent = textureScore * 0.002f * textureWeight
            edgeComponent + contrastComponent + textureComponent
        } else 0f
    }
    
    /**
     * Applies Gaussian-like smoothing to reduce noise.
     */
    private fun applySmoothingFilter(saliencyMap: Array<FloatArray>, gridSize: Int): Array<FloatArray> {
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
        return smoothedMap
    }
    
    /**
     * Calculates percentile-based threshold for saliency.
     */
    private fun calculatePercentileThreshold(saliencyMap: Array<FloatArray>, percentile: Float): Float {
        val allValues = mutableListOf<Float>()
        for (row in saliencyMap) {
            for (value in row) {
                allValues.add(value)
            }
        }
        val sorted = allValues.sorted()
        return if (sorted.isNotEmpty()) {
            val percentileIndex = (sorted.size * percentile).toInt().coerceIn(0, sorted.size - 1)
            sorted[percentileIndex]
        } else 0f
    }
    
    /**
     * Finds local maxima (peaks) in saliency map.
     */
    fun findLocalMaxima(saliencyMap: Array<FloatArray>, threshold: Float): List<Pair<Int, Int>> {
        val maxima = mutableListOf<Pair<Int, Int>>()
        val gridSize = saliencyMap.size
        
        for (y in 1 until gridSize - 1) {
            for (x in 1 until gridSize - 1) {
                val current = saliencyMap[y][x]
                
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
     * Clusters nearby focal points into meaningful groups.
     */
    fun clusterFocalPoints(maxima: List<Pair<Int, Int>>, gridSize: Int): List<Pair<Int, Int>> {
        if (maxima.isEmpty()) return emptyList()
        if (maxima.size <= 2) return maxima
        
        val clusterRadius = gridSize / 6
        val clustered = mutableListOf<Pair<Int, Int>>()
        val processed = mutableSetOf<Pair<Int, Int>>()
        
        for (point in maxima) {
            if (processed.contains(point)) continue
            
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
            
            val centerX = sumX / count
            val centerY = sumY / count
            clustered.add(Pair(centerX, centerY))
            processed.add(point)
        }
        
        return clustered
    }
    
    /**
     * Adds focal points using threshold-based detection (fallback).
     */
    private fun addThresholdBasedFocalPoints(
        saliencyMap: Array<FloatArray>,
        gridSize: Int,
        cellWidth: Int,
        cellHeight: Int,
        threshold: Float,
        focalPoints: MutableList<FocalPoint>
    ) {
        for (y in 0 until gridSize) {
            for (x in 0 until gridSize) {
                if (saliencyMap[y][x] >= threshold) {
                    val cellX = (x * cellWidth + cellWidth / 2).toFloat()
                    val cellY = (y * cellHeight + cellHeight / 2).toFloat()
                    focalPoints.add(FocalPoint(cellX, cellY, saliencyMap[y][x]))
                }
            }
        }
    }
}
