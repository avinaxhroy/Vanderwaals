package me.avinas.vanderwaals.algorithm

import android.graphics.Bitmap
import android.graphics.Color
import androidx.palette.graphics.Palette
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.max
import kotlin.math.min

/**
 * Enhanced image analyzer that extracts semantic features beyond embeddings.
 * 
 * Extracts features that capture the "essence" of an image:
 * - Perceptual color analysis (LAB color space, dominant colors with weights)
 * - Visual composition (rule of thirds, symmetry, balance)
 * - Texture and complexity
 * - Mood and atmosphere
 * 
 * These features complement MobileNetV3 embeddings to provide better matching
 * of user-uploaded wallpapers to recommendations.
 */
class EnhancedImageAnalyzer {
    
    /**
     * Complete analysis result containing all extracted features.
     */
    data class ImageAnalysis(
        // Color features (LAB color space for perceptual similarity)
        val dominantColors: List<LabColor>,
        val colorWeights: List<Float>,
        val saturation: Float,           // 0.0 to 1.0
        val colorfulness: Float,          // 0.0 to 1.0
        
        // Composition features
        val compositionScore: Float,      // 0.0 to 1.0 (rule of thirds alignment)
        val symmetryScore: Float,         // 0.0 to 1.0
        val visualBalance: Float,         // 0.0 to 1.0
        val complexity: Float,            // 0.0 to 1.0 (edge density)
        
        // Mood/atmosphere
        val warmth: Float,                // -1.0 (cool) to 1.0 (warm)
        val energy: Float,                // 0.0 (calm) to 1.0 (energetic)
        val brightness: Float,            // 0.0 (dark) to 1.0 (bright)
        val contrast: Float               // 0.0 (low) to 1.0 (high)
    )
    
    /**
     * LAB color representation for perceptual color matching.
     * LAB color space is perceptually uniform - distances match human perception.
     */
    data class LabColor(
        val l: Float,  // Lightness: 0 (black) to 100 (white)
        val a: Float,  // Green-red: -128 (green) to 127 (red)
        val b: Float   // Blue-yellow: -128 (blue) to 127 (yellow)
    )
    
    /**
     * Analyzes an image and extracts comprehensive features.
     * 
     * @param bitmap Input image (will be scaled down for performance if needed)
     * @return ImageAnalysis containing all extracted features
     */
    fun analyze(bitmap: Bitmap): ImageAnalysis {
        // Scale down for performance (max 512px on longest side)
        val scaledBitmap = if (max(bitmap.width, bitmap.height) > 512) {
            val scale = 512f / max(bitmap.width, bitmap.height)
            val newWidth = (bitmap.width * scale).toInt()
            val newHeight = (bitmap.height * scale).toInt()
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } else {
            bitmap
        }
        
        try {
            // Extract color palette using Android Palette library
            val palette = Palette.from(scaledBitmap).generate()
            
            // Extract and convert dominant colors to LAB
            val dominantColors = extractDominantColors(palette, scaledBitmap)
            val colorWeights = calculateColorWeights(dominantColors)
            
            // Color features
            val saturation = calculateSaturation(scaledBitmap)
            val colorfulness = calculateColorfulness(dominantColors)
            
            // Composition features
            val compositionScore = analyzeComposition(scaledBitmap)
            val symmetryScore = analyzeSymmetry(scaledBitmap)
            val visualBalance = analyzeBalance(scaledBitmap)
            val complexity = analyzeComplexity(scaledBitmap)
            
            // Mood features
            val warmth = calculateWarmth(dominantColors)
            val energy = calculateEnergy(colorfulness, complexity)
            val brightnessValue = calculateBrightness(scaledBitmap)
            val contrastValue = calculateContrast(scaledBitmap)
            
            return ImageAnalysis(
                dominantColors = dominantColors,
                colorWeights = colorWeights,
                saturation = saturation,
                colorfulness = colorfulness,
                compositionScore = compositionScore,
                symmetryScore = symmetryScore,
                visualBalance = visualBalance,
                complexity = complexity,
                warmth = warmth,
                energy = energy,
                brightness = brightnessValue,
                contrast = contrastValue
            )
        } finally {
            // Clean up scaled bitmap if we created a new one
            if (scaledBitmap !== bitmap) {
                scaledBitmap.recycle()
            }
        }
    }
    
    /**
     * Extracts dominant colors from palette and converts to LAB color space.
     */
    private fun extractDominantColors(palette: Palette, bitmap: Bitmap): List<LabColor> {
        val colors = mutableListOf<Int>()
        
        // Get palette swatches in order of prominence
        palette.vibrantSwatch?.let { colors.add(it.rgb) }
        palette.lightVibrantSwatch?.let { colors.add(it.rgb) }
        palette.darkVibrantSwatch?.let { colors.add(it.rgb) }
        palette.mutedSwatch?.let { colors.add(it.rgb) }
        palette.lightMutedSwatch?.let { colors.add(it.rgb) }
        palette.darkMutedSwatch?.let { colors.add(it.rgb) }
        
        // If we have fewer than 3 colors, add most common colors from bitmap
        if (colors.size < 3) {
            colors.addAll(extractMostCommonColors(bitmap, 5 - colors.size))
        }
        
        // Convert RGB to LAB
        return colors.distinct().take(5).map { rgbToLab(it) }
    }
    
    /**
     * Extracts most common colors from bitmap using color quantization.
     */
    private fun extractMostCommonColors(bitmap: Bitmap, count: Int): List<Int> {
        val colorMap = mutableMapOf<Int, Int>()
        
        // Sample pixels (every 10th pixel for performance)
        val step = 10
        for (y in 0 until bitmap.height step step) {
            for (x in 0 until bitmap.width step step) {
                val pixel = bitmap.getPixel(x, y)
                // Quantize to reduce similar colors (reduce precision to 32 levels per channel)
                val quantized = quantizeColor(pixel, 8)
                colorMap[quantized] = (colorMap[quantized] ?: 0) + 1
            }
        }
        
        return colorMap.entries
            .sortedByDescending { it.value }
            .take(count)
            .map { it.key }
    }
    
    /**
     * Quantizes a color by reducing precision.
     */
    private fun quantizeColor(color: Int, levels: Int): Int {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        
        val qr = (r * levels / 256) * (256 / levels)
        val qg = (g * levels / 256) * (256 / levels)
        val qb = (b * levels / 256) * (256 / levels)
        
        return Color.rgb(qr, qg, qb)
    }
    
    /**
     * Converts RGB color to LAB color space.
     * LAB is perceptually uniform - Euclidean distance matches human perception.
     */
    private fun rgbToLab(rgb: Int): LabColor {
        // Extract RGB components
        val r = Color.red(rgb) / 255f
        val g = Color.green(rgb) / 255f
        val b = Color.blue(rgb) / 255f
        
        // Convert RGB to XYZ
        val x = r * 0.4124f + g * 0.3576f + b * 0.1805f
        val y = r * 0.2126f + g * 0.7152f + b * 0.0722f
        val z = r * 0.0193f + g * 0.1192f + b * 0.9505f
        
        // Normalize for D65 illuminant
        val xn = x / 0.95047f
        val yn = y / 1.00000f
        val zn = z / 1.08883f
        
        // Apply LAB transformation
        fun f(t: Float): Float = if (t > 0.008856f) t.pow(1f/3f) else (7.787f * t + 16f/116f)
        
        val fx = f(xn)
        val fy = f(yn)
        val fz = f(zn)
        
        val l = (116f * fy) - 16f
        val a = 500f * (fx - fy)
        val bValue = 200f * (fy - fz)
        
        return LabColor(l, a, bValue)
    }
    
    /**
     * Calculates color weights based on visual importance.
     */
    private fun calculateColorWeights(colors: List<LabColor>): List<Float> {
        if (colors.isEmpty()) return emptyList()
        
        // Simple weight distribution: exponential decay
        val weights = colors.indices.map { i ->
            exp(-i * 0.5f)
        }
        
        // Normalize to sum to 1.0
        val sum = weights.sum()
        return weights.map { it / sum }
    }
    
    /**
     * Calculates average saturation of the image.
     */
    private fun calculateSaturation(bitmap: Bitmap): Float {
        var totalSaturation = 0f
        var pixelCount = 0
        
        val step = 10
        val hsv = FloatArray(3)
        
        for (y in 0 until bitmap.height step step) {
            for (x in 0 until bitmap.width step step) {
                val pixel = bitmap.getPixel(x, y)
                Color.colorToHSV(pixel, hsv)
                totalSaturation += hsv[1]
                pixelCount++
            }
        }
        
        return if (pixelCount > 0) totalSaturation / pixelCount else 0f
    }
    
    /**
     * Calculates colorfulness based on color variety and saturation.
     */
    private fun calculateColorfulness(colors: List<LabColor>): Float {
        if (colors.size < 2) return 0f
        
        // Measure color variety by calculating average distance between colors
        var totalDistance = 0f
        var pairCount = 0
        
        for (i in colors.indices) {
            for (j in i + 1 until colors.size) {
                totalDistance += labDistance(colors[i], colors[j])
                pairCount++
            }
        }
        
        val avgDistance = if (pairCount > 0) totalDistance / pairCount else 0f
        
        // Normalize to 0-1 range (max LAB distance is ~200)
        return (avgDistance / 200f).coerceIn(0f, 1f)
    }
    
    /**
     * Calculates Euclidean distance in LAB color space.
     */
    private fun labDistance(c1: LabColor, c2: LabColor): Float {
        val dl = c1.l - c2.l
        val da = c1.a - c2.a
        val db = c1.b - c2.b
        return sqrt(dl * dl + da * da + db * db)
    }
    
    /**
     * Analyzes image composition (rule of thirds alignment).
     */
    private fun analyzeComposition(bitmap: Bitmap): Float {
        // Analyze edge density at rule of thirds intersections
        val width = bitmap.width
        val height = bitmap.height
        
        val intersections = listOf(
            Pair(width / 3, height / 3),
            Pair(2 * width / 3, height / 3),
            Pair(width / 3, 2 * height / 3),
            Pair(2 * width / 3, 2 * height / 3)
        )
        
        var totalEdgeDensity = 0f
        
        for ((x, y) in intersections) {
            // Sample region around intersection point
            val regionSize = minOf(width, height) / 10
            totalEdgeDensity += getEdgeDensity(bitmap, x, y, regionSize)
        }
        
        return (totalEdgeDensity / 4f).coerceIn(0f, 1f)
    }
    
    /**
     * Analyzes horizontal and vertical symmetry.
     */
    private fun analyzeSymmetry(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        
        var symmetryScore = 0f
        var comparisons = 0
        
        // Sample points for horizontal symmetry
        val step = 20
        for (y in 0 until height step step) {
            for (x in 0 until width / 2 step step) {
                val leftPixel = bitmap.getPixel(x, y)
                val rightPixel = bitmap.getPixel(width - 1 - x, y)
                
                val similarity = colorSimilarity(leftPixel, rightPixel)
                symmetryScore += similarity
                comparisons++
            }
        }
        
        return if (comparisons > 0) symmetryScore / comparisons else 0f
    }
    
    /**
     * Analyzes visual balance (weight distribution).
     */
    private fun analyzeBalance(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        
        var leftWeight = 0f
        var rightWeight = 0f
        
        val step = 10
        for (y in 0 until height step step) {
            for (x in 0 until width step step) {
                val pixel = bitmap.getPixel(x, y)
                val weight = getVisualWeight(pixel)
                
                if (x < width / 2) {
                    leftWeight += weight
                } else {
                    rightWeight += weight
                }
            }
        }
        
        val totalWeight = leftWeight + rightWeight
        if (totalWeight == 0f) return 0.5f
        
        val balance = minOf(leftWeight, rightWeight) / totalWeight
        return balance * 2f // Scale to 0-1 range
    }
    
    /**
     * Analyzes image complexity (edge density).
     */
    private fun analyzeComplexity(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        
        var edgeCount = 0
        var totalPixels = 0
        
        val step = 5
        for (y in step until height - step step step) {
            for (x in step until width - step step step) {
                val center = bitmap.getPixel(x, y)
                val right = bitmap.getPixel(x + step, y)
                val down = bitmap.getPixel(x, y + step)
                
                val horizontalDiff = colorDifference(center, right)
                val verticalDiff = colorDifference(center, down)
                
                if (horizontalDiff > 30 || verticalDiff > 30) {
                    edgeCount++
                }
                totalPixels++
            }
        }
        
        return if (totalPixels > 0) edgeCount.toFloat() / totalPixels else 0f
    }
    
    /**
     * Calculates warmth of the image (cool to warm colors).
     */
    private fun calculateWarmth(colors: List<LabColor>): Float {
        if (colors.isEmpty()) return 0f
        
        // In LAB space, 'a' component indicates green-red
        // Positive 'a' = warm (red), negative 'a' = cool (green)
        val avgA = colors.map { it.a }.average().toFloat()
        
        // Normalize to -1 to 1 range
        return (avgA / 127f).coerceIn(-1f, 1f)
    }
    
    /**
     * Calculates energy level (calm to energetic).
     */
    private fun calculateEnergy(colorfulness: Float, complexity: Float): Float {
        // Energy is combination of colorfulness and complexity
        return (colorfulness * 0.6f + complexity * 0.4f).coerceIn(0f, 1f)
    }
    
    /**
     * Calculates average brightness.
     */
    private fun calculateBrightness(bitmap: Bitmap): Float {
        var totalBrightness = 0f
        var pixelCount = 0
        
        val step = 10
        for (y in 0 until bitmap.height step step) {
            for (x in 0 until bitmap.width step step) {
                val pixel = bitmap.getPixel(x, y)
                val brightness = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / (3f * 255f)
                totalBrightness += brightness
                pixelCount++
            }
        }
        
        return if (pixelCount > 0) totalBrightness / pixelCount else 0f
    }
    
    /**
     * Calculates contrast (standard deviation of brightness).
     */
    private fun calculateContrast(bitmap: Bitmap): Float {
        val brightnesses = mutableListOf<Float>()
        
        val step = 10
        for (y in 0 until bitmap.height step step) {
            for (x in 0 until bitmap.width step step) {
                val pixel = bitmap.getPixel(x, y)
                val brightness = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / (3f * 255f)
                brightnesses.add(brightness)
            }
        }
        
        if (brightnesses.isEmpty()) return 0f
        
        val mean = brightnesses.average().toFloat()
        val variance = brightnesses.map { (it - mean).pow(2) }.average().toFloat()
        val stdDev = sqrt(variance)
        
        // Normalize (typical std dev ranges from 0 to ~0.3)
        return (stdDev / 0.3f).coerceIn(0f, 1f)
    }
    
    // Helper functions
    
    private fun getEdgeDensity(bitmap: Bitmap, centerX: Int, centerY: Int, regionSize: Int): Float {
        var edgeCount = 0
        var totalPixels = 0
        
        val halfSize = regionSize / 2
        val startX = max(1, centerX - halfSize)
        val endX = min(bitmap.width - 1, centerX + halfSize)
        val startY = max(1, centerY - halfSize)
        val endY = minOf(bitmap.height - 2, centerY + halfSize)
        
        for (y in startY until endY) {
            for (x in startX until endX) {
                val center = bitmap.getPixel(x, y)
                val right = bitmap.getPixel(x + 1, y)
                val down = bitmap.getPixel(x, y + 1)
                
                val horizontalDiff = colorDifference(center, right)
                val verticalDiff = colorDifference(center, down)
                
                if (horizontalDiff > 30 || verticalDiff > 30) {
                    edgeCount++
                }
                totalPixels++
            }
        }
        
        return if (totalPixels > 0) edgeCount.toFloat() / totalPixels else 0f
    }
    
    private fun colorSimilarity(color1: Int, color2: Int): Float {
        val diff = colorDifference(color1, color2)
        return 1f - (diff / 441f).coerceIn(0f, 1f)
    }
    
    private fun colorDifference(color1: Int, color2: Int): Float {
        val dr = Color.red(color1) - Color.red(color2)
        val dg = Color.green(color1) - Color.green(color2)
        val db = Color.blue(color1) - Color.blue(color2)
        return sqrt((dr * dr + dg * dg + db * db).toFloat())
    }
    
    private fun getVisualWeight(pixel: Int): Float {
        // Darker and more saturated colors have more visual weight
        val brightness = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / (3f * 255f)
        val hsv = FloatArray(3)
        Color.colorToHSV(pixel, hsv)
        val saturation = hsv[1]
        
        return (1f - brightness) * 0.7f + saturation * 0.3f
    }
    
    companion object {
        /**
         * Calculates semantic similarity between two image analyses.
         * 
         * This goes beyond embedding similarity to match the "essence" of images:
         * - Color harmony (LAB color space)
         * - Composition style
         * - Mood and atmosphere
         * 
         * @param analysis1 First image analysis
         * @param analysis2 Second image analysis
         * @return Similarity score 0.0 to 1.0
         */
        fun calculateSemanticSimilarity(analysis1: ImageAnalysis, analysis2: ImageAnalysis): Float {
            var totalSimilarity = 0f
            
            // 1. Color similarity (40% weight) - perceptual LAB distance
            val colorSim = calculateColorSimilarity(analysis1, analysis2)
            totalSimilarity += colorSim * 0.40f
            
            // 2. Composition similarity (25% weight)
            val compSim = calculateCompositionSimilarity(analysis1, analysis2)
            totalSimilarity += compSim * 0.25f
            
            // 3. Mood similarity (20% weight)
            val moodSim = calculateMoodSimilarity(analysis1, analysis2)
            totalSimilarity += moodSim * 0.20f
            
            // 4. Energy/complexity similarity (15% weight)
            val energySim = 1f - abs(analysis1.energy - analysis2.energy)
            totalSimilarity += energySim * 0.15f
            
            return totalSimilarity.coerceIn(0f, 1f)
        }
        
        private fun calculateColorSimilarity(analysis1: ImageAnalysis, analysis2: ImageAnalysis): Float {
            if (analysis1.dominantColors.isEmpty() || analysis2.dominantColors.isEmpty()) {
                return 0.5f
            }
            
            // Match dominant colors using LAB distance
            var totalSimilarity = 0f
            var weightSum = 0f
            
            for (i in analysis1.dominantColors.indices) {
                val color1 = analysis1.dominantColors[i]
                val weight1 = analysis1.colorWeights.getOrElse(i) { 0f }
                
                // Find best matching color in analysis2
                val bestMatch = analysis2.dominantColors.minOfOrNull { color2 ->
                    val dl = color1.l - color2.l
                    val da = color1.a - color2.a
                    val db = color1.b - color2.b
                    sqrt(dl * dl + da * da + db * db)
                } ?: 200f
                
                // Convert distance to similarity (max LAB distance ~200)
                val similarity = 1f - (bestMatch / 200f).coerceIn(0f, 1f)
                totalSimilarity += similarity * weight1
                weightSum += weight1
            }
            
            return if (weightSum > 0) totalSimilarity / weightSum else 0.5f
        }
        
        private fun calculateCompositionSimilarity(analysis1: ImageAnalysis, analysis2: ImageAnalysis): Float {
            val compDiff = abs(analysis1.compositionScore - analysis2.compositionScore)
            val symDiff = abs(analysis1.symmetryScore - analysis2.symmetryScore)
            val balanceDiff = abs(analysis1.visualBalance - analysis2.visualBalance)
            val complexityDiff = abs(analysis1.complexity - analysis2.complexity)
            
            // Average similarity
            return 1f - ((compDiff + symDiff + balanceDiff + complexityDiff) / 4f)
        }
        
        private fun calculateMoodSimilarity(analysis1: ImageAnalysis, analysis2: ImageAnalysis): Float {
            val warmthDiff = abs(analysis1.warmth - analysis2.warmth) / 2f // Scale from 0-2 to 0-1
            val brightnessDiff = abs(analysis1.brightness - analysis2.brightness)
            val contrastDiff = abs(analysis1.contrast - analysis2.contrast)
            
            return 1f - ((warmthDiff + brightnessDiff + contrastDiff) / 3f)
        }
    }
}
