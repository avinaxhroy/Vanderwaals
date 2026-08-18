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
 * Extracts semantic visual features (LAB color distribution, composition, texture, and mood)
 * to complement MobileNetV4 embeddings.
 */
class EnhancedImageAnalyzer {

    data class ImageAnalysis(
        val dominantColors: List<LabColor>,
        val colorWeights: List<Float>,
        val saturation: Float,
        val colorfulness: Float,
        val compositionScore: Float,
        val symmetryScore: Float,
        val visualBalance: Float,
        val complexity: Float,
        val warmth: Float,
        val energy: Float,
        val brightness: Float,
        val contrast: Float
    )

    data class LabColor(
        val l: Float,
        val a: Float,
        val b: Float
    )

    fun analyze(bitmap: Bitmap): ImageAnalysis {
        val scaledBitmap = if (max(bitmap.width, bitmap.height) > 512) {
            val scale = 512f / max(bitmap.width, bitmap.height)
            val newWidth = (bitmap.width * scale).toInt()
            val newHeight = (bitmap.height * scale).toInt()
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } else {
            bitmap
        }

        try {
            // Read pixels once to avoid per-pixel Bitmap.getPixel JNI overhead.
            val width = scaledBitmap.width
            val height = scaledBitmap.height
            val pixels = IntArray(width * height)
            scaledBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val palette = Palette.from(scaledBitmap).generate()
            val dominantColors = extractDominantColors(palette, pixels, width, height)
            val colorWeights = calculateColorWeights(dominantColors)

            val saturation = calculateSaturation(pixels, width, height)
            val colorfulness = calculateColorfulness(dominantColors)

            val compositionScore = analyzeComposition(pixels, width, height)
            val symmetryScore = analyzeSymmetry(pixels, width, height)
            val visualBalance = analyzeBalance(pixels, width, height)
            val complexity = analyzeComplexity(pixels, width, height)

            val warmth = calculateWarmth(dominantColors)
            val energy = calculateEnergy(colorfulness, complexity)
            val brightnessValue = calculateBrightness(pixels, width, height)
            val contrastValue = calculateContrast(pixels, width, height)

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
            if (scaledBitmap !== bitmap) {
                scaledBitmap.recycle()
            }
        }
    }

    /**
     * Extracts dominant colors from palette and converts to LAB color space.
     */
    private fun extractDominantColors(
        palette: Palette,
        pixels: IntArray,
        width: Int,
        height: Int
    ): List<LabColor> {
        val colors = mutableListOf<Int>()

        palette.vibrantSwatch?.let { colors.add(it.rgb) }
        palette.lightVibrantSwatch?.let { colors.add(it.rgb) }
        palette.darkVibrantSwatch?.let { colors.add(it.rgb) }
        palette.mutedSwatch?.let { colors.add(it.rgb) }
        palette.lightMutedSwatch?.let { colors.add(it.rgb) }
        palette.darkMutedSwatch?.let { colors.add(it.rgb) }

        // Backfill with the most common bitmap colors when the palette is sparse.
        if (colors.size < 3) {
            colors.addAll(extractMostCommonColors(pixels, width, height, 5 - colors.size))
        }

        return colors.distinct().take(5).map { rgbToLab(it) }
    }

    /**
     * Extracts most common colors from the pixel buffer using color quantization.
     */
    private fun extractMostCommonColors(pixels: IntArray, width: Int, height: Int, count: Int): List<Int> {
        val colorMap = mutableMapOf<Int, Int>()

        // Sample pixels (every 10th pixel for performance)
        val step = 10
        for (y in 0 until height step step) {
            for (x in 0 until width step step) {
                val pixel = pixels[y * width + x]
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
     * Delegates to the shared [me.avinas.vanderwaals.core.ColorSpace] implementation
     * (correct sRGB linearisation; was previously skipped, producing inaccurate Lab values).
     */
    private fun rgbToLab(rgb: Int): LabColor {
        val (l, a, b) = me.avinas.vanderwaals.core.ColorSpace.rgbToLab(
            android.graphics.Color.red(rgb),
            android.graphics.Color.green(rgb),
            android.graphics.Color.blue(rgb)
        )
        return LabColor(l.toFloat(), a.toFloat(), b.toFloat())
    }

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

    private fun calculateSaturation(pixels: IntArray, width: Int, height: Int): Float {
        var totalSaturation = 0f
        var pixelCount = 0

        val step = 10
        val hsv = FloatArray(3)

        for (y in 0 until height step step) {
            for (x in 0 until width step step) {
                val pixel = pixels[y * width + x]
                Color.colorToHSV(pixel, hsv)
                totalSaturation += hsv[1]
                pixelCount++
            }
        }

        return if (pixelCount > 0) totalSaturation / pixelCount else 0f
    }

    private fun calculateColorfulness(colors: List<LabColor>): Float {
        if (colors.size < 2) return 0f

        // Average pairwise distance between colors.
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

    private fun labDistance(c1: LabColor, c2: LabColor): Float {
        val dl = c1.l - c2.l
        val da = c1.a - c2.a
        val db = c1.b - c2.b
        return sqrt(dl * dl + da * da + db * db)
    }

    /**
     * Analyzes image composition (rule of thirds alignment).
     */
    private fun analyzeComposition(pixels: IntArray, width: Int, height: Int): Float {
        // Edge density at the rule-of-thirds intersections.
        val intersections = listOf(
            Pair(width / 3, height / 3),
            Pair(2 * width / 3, height / 3),
            Pair(width / 3, 2 * height / 3),
            Pair(2 * width / 3, 2 * height / 3)
        )

        var totalEdgeDensity = 0f

        for ((x, y) in intersections) {
            val regionSize = minOf(width, height) / 10
            totalEdgeDensity += getEdgeDensity(pixels, width, height, x, y, regionSize)
        }

        return (totalEdgeDensity / 4f).coerceIn(0f, 1f)
    }

    private fun analyzeSymmetry(pixels: IntArray, width: Int, height: Int): Float {
        var symmetryScore = 0f
        var comparisons = 0

        val step = 20
        for (y in 0 until height step step) {
            for (x in 0 until width / 2 step step) {
                val leftPixel = pixels[y * width + x]
                val rightPixel = pixels[y * width + (width - 1 - x)]

                val similarity = colorSimilarity(leftPixel, rightPixel)
                symmetryScore += similarity
                comparisons++
            }
        }

        return if (comparisons > 0) symmetryScore / comparisons else 0f
    }

    private fun analyzeBalance(pixels: IntArray, width: Int, height: Int): Float {
        var leftWeight = 0f
        var rightWeight = 0f

        val step = 10
        for (y in 0 until height step step) {
            for (x in 0 until width step step) {
                val pixel = pixels[y * width + x]
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

    private fun analyzeComplexity(pixels: IntArray, width: Int, height: Int): Float {
        var edgeCount = 0
        var totalPixels = 0

        val step = 5
        for (y in step until height - step step step) {
            for (x in step until width - step step step) {
                val center = pixels[y * width + x]
                val right = pixels[y * width + (x + step)]
                val down = pixels[(y + step) * width + x]

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

    private fun calculateWarmth(colors: List<LabColor>): Float {
        if (colors.isEmpty()) return 0f

        // In LAB space, 'a' component indicates green-red
        // Positive 'a' = warm (red), negative 'a' = cool (green)
        val avgA = colors.map { it.a }.average().toFloat()

        // Normalize to -1 to 1 range
        return (avgA / 127f).coerceIn(-1f, 1f)
    }

    private fun calculateEnergy(colorfulness: Float, complexity: Float): Float {
        return (colorfulness * 0.6f + complexity * 0.4f).coerceIn(0f, 1f)
    }

    private fun calculateBrightness(pixels: IntArray, width: Int, height: Int): Float {
        var totalBrightness = 0f
        var pixelCount = 0

        val step = 10
        for (y in 0 until height step step) {
            for (x in 0 until width step step) {
                val pixel = pixels[y * width + x]
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
    private fun calculateContrast(pixels: IntArray, width: Int, height: Int): Float {
        val brightnesses = mutableListOf<Float>()

        val step = 10
        for (y in 0 until height step step) {
            for (x in 0 until width step step) {
                val pixel = pixels[y * width + x]
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

    private fun getEdgeDensity(
        pixels: IntArray,
        width: Int,
        height: Int,
        centerX: Int,
        centerY: Int,
        regionSize: Int
    ): Float {
        var edgeCount = 0
        var totalPixels = 0

        val halfSize = regionSize / 2
        val startX = max(1, centerX - halfSize)
        val endX = min(width - 1, centerX + halfSize)
        val startY = max(1, centerY - halfSize)
        val endY = minOf(height - 2, centerY + halfSize)

        for (y in startY until endY) {
            for (x in startX until endX) {
                val center = pixels[y * width + x]
                val right = pixels[y * width + (x + 1)]
                val down = pixels[(y + 1) * width + x]

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
        fun calculateSemanticSimilarity(analysis1: ImageAnalysis, analysis2: ImageAnalysis): Float {
            val colorSim = calculateColorSimilarity(analysis1, analysis2)
            val compSim = calculateCompositionSimilarity(analysis1, analysis2)
            val moodSim = calculateMoodSimilarity(analysis1, analysis2)
            val energySim = 1f - abs(analysis1.energy - analysis2.energy)

            val totalSimilarity = (colorSim * 0.40f) +
                (compSim * 0.25f) +
                (moodSim * 0.20f) +
                (energySim * 0.15f)

            return totalSimilarity.coerceIn(0f, 1f)
        }

        private fun calculateColorSimilarity(analysis1: ImageAnalysis, analysis2: ImageAnalysis): Float {
            if (analysis1.dominantColors.isEmpty() || analysis2.dominantColors.isEmpty()) {
                return 0.5f
            }

            var totalSimilarity = 0f
            var weightSum = 0f

            for (i in analysis1.dominantColors.indices) {
                val color1 = analysis1.dominantColors[i]
                val weight1 = analysis1.colorWeights.getOrElse(i) { 0f }

                val bestMatch = analysis2.dominantColors.minOfOrNull { color2 ->
                    val dl = color1.l - color2.l
                    val da = color1.a - color2.a
                    val db = color1.b - color2.b
                    sqrt(dl * dl + da * da + db * db)
                } ?: 200f

                // LAB Euclidean distance ranges up to ~200 in standard color spaces.
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

            return 1f - ((compDiff + symDiff + balanceDiff + complexityDiff) / 4f)
        }

        private fun calculateMoodSimilarity(analysis1: ImageAnalysis, analysis2: ImageAnalysis): Float {
            val warmthDiff = abs(analysis1.warmth - analysis2.warmth) / 2f
            val brightnessDiff = abs(analysis1.brightness - analysis2.brightness)
            val contrastDiff = abs(analysis1.contrast - analysis2.contrast)

            return 1f - ((warmthDiff + brightnessDiff + contrastDiff) / 3f)
        }
    }
}
