package me.avinas.vanderwaals.algorithm

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Analyzes visual composition (symmetry, rule of thirds, center weight, edge density, and contrast).
 */
object CompositionAnalyzer {
    
    fun analyzeComposition(imageFile: File): CompositionAnalysis {
        if (!imageFile.exists()) {
            return CompositionAnalysis.empty()
        }
        
        val bitmap = try {
            me.avinas.vanderwaals.core.BitmapManager.loadBitmap(
                file = imageFile,
                maxWidth = 1024,
                maxHeight = 1024
            )
        } catch (e: Exception) {
            return CompositionAnalysis.empty()
        }
        
        if (bitmap == null) {
            return CompositionAnalysis.empty()
        }
        
        val result = analyzeBitmap(bitmap)
        me.avinas.vanderwaals.core.BitmapManager.recycleSafely(bitmap)
        return result
    }
    
    fun analyzeBitmap(bitmap: Bitmap): CompositionAnalysis {
        val width = bitmap.width
        val height = bitmap.height

        // Read pixels once to avoid per-pixel Bitmap.getPixel JNI overhead across all 9 regions.
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val gridWidth = width / 3
        val gridHeight = height / 3

        val regions = Array(3) { row ->
            Array(3) { col ->
                analyzeRegion(
                    pixels,
                    width,
                    height,
                    col * gridWidth,
                    row * gridHeight,
                    gridWidth,
                    gridHeight
                )
            }
        }
        
        return CompositionAnalysis(
            symmetryScore = calculateSymmetry(regions),
            ruleOfThirdsScore = calculateRuleOfThirdsScore(regions),
            centerWeight = calculateCenterWeight(regions),
            edgeDensity = calculateEdgeDensity(regions),
            complexity = calculateComplexity(regions),
            contrastDistribution = calculateContrastDistribution(regions),
            brightnessMap = regions.map { row -> row.map { it.brightness } }
        )
    }
    
    fun calculateCompositionSimilarity(comp1: CompositionAnalysis, comp2: CompositionAnalysis): Float {
        if (comp1.isEmpty() || comp2.isEmpty()) {
            return 0.5f
        }
        
        val symmetrySim = 1f - abs(comp1.symmetryScore - comp2.symmetryScore)
        val ruleOfThirdsSim = 1f - abs(comp1.ruleOfThirdsScore - comp2.ruleOfThirdsScore)
        val centerWeightSim = 1f - abs(comp1.centerWeight - comp2.centerWeight)
        val edgeDensitySim = 1f - abs(comp1.edgeDensity - comp2.edgeDensity)
        val complexitySim = 1f - abs(comp1.complexity - comp2.complexity)
        
        return (symmetrySim * 0.25f +
                ruleOfThirdsSim * 0.20f +
                centerWeightSim * 0.25f +
                edgeDensitySim * 0.15f +
                complexitySim * 0.15f)
            .coerceIn(0f, 1f)
    }
    
    fun extractCompositionPreferences(
        likedCompositions: List<CompositionAnalysis>,
        dislikedCompositions: List<CompositionAnalysis>
    ): CompositionPreferenceProfile {
        if (likedCompositions.isEmpty()) {
            return CompositionPreferenceProfile.neutral()
        }
        
        return CompositionPreferenceProfile(
            preferredSymmetry = likedCompositions.map { it.symmetryScore }.average().toFloat(),
            preferredRuleOfThirds = likedCompositions.map { it.ruleOfThirdsScore }.average().toFloat(),
            preferredCenterWeight = likedCompositions.map { it.centerWeight }.average().toFloat(),
            preferredEdgeDensity = likedCompositions.map { it.edgeDensity }.average().toFloat(),
            preferredComplexity = likedCompositions.map { it.complexity }.average().toFloat(),
            confidence = kotlin.math.min(likedCompositions.size / 10f, 1f)
        )
    }
    
    fun calculateCompositionPreferenceScore(
        composition: CompositionAnalysis,
        preferences: CompositionPreferenceProfile
    ): Float {
        if (composition.isEmpty() || preferences.confidence < 0.1f) {
            return 0f
        }
        
        val symmetryDiff = abs(composition.symmetryScore - preferences.preferredSymmetry)
        val symmetryScore = 1f - symmetryDiff
        
        val ruleOfThirdsDiff = abs(composition.ruleOfThirdsScore - preferences.preferredRuleOfThirds)
        val ruleOfThirdsScore = 1f - ruleOfThirdsDiff
        
        val centerWeightDiff = abs(composition.centerWeight - preferences.preferredCenterWeight)
        val centerWeightScore = 1f - centerWeightDiff
        
        val edgeDensityDiff = abs(composition.edgeDensity - preferences.preferredEdgeDensity)
        val edgeDensityScore = 1f - edgeDensityDiff
        
        val complexityDiff = abs(composition.complexity - preferences.preferredComplexity)
        val complexityScore = 1f - complexityDiff
        
        val rawScore = (symmetryScore * 0.25f +
                        ruleOfThirdsScore * 0.20f +
                        centerWeightScore * 0.25f +
                        edgeDensityScore * 0.15f +
                        complexityScore * 0.15f)
        
        return (rawScore * 2f - 1f) * preferences.confidence
    }
    
    private data class RegionStats(
        val brightness: Float,
        val contrast: Float,
        val edgeCount: Int
    )
    
    private fun analyzeRegion(
        pixels: IntArray,
        bitmapWidth: Int,
        bitmapHeight: Int,
        startX: Int,
        startY: Int,
        width: Int,
        height: Int
    ): RegionStats {
        var totalBrightness = 0f
        var pixelCount = 0
        val brightnesses = mutableListOf<Float>()
        var edgeCount = 0
        
        val endX = (startX + width).coerceAtMost(bitmapWidth)
        val endY = (startY + height).coerceAtMost(bitmapHeight)
        
        for (y in startY until endY step 4) {
            for (x in startX until endX step 4) {
                val pixel = pixels[y * bitmapWidth + x]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                
                val brightness = (r + g + b) / (3f * 255f)
                totalBrightness += brightness
                brightnesses.add(brightness)
                pixelCount++
                
                if (x < endX - 4 && y < endY - 4) {
                    val nextPixel = pixels[y * bitmapWidth + (x + 4)]
                    val nextR = (nextPixel shr 16) and 0xFF
                    val nextBrightness = (nextR + ((nextPixel shr 8) and 0xFF) + (nextPixel and 0xFF)) / (3f * 255f)
                    
                    if (abs(brightness - nextBrightness) > 0.2f) {
                        edgeCount++
                    }
                }
            }
        }
        
        val avgBrightness = if (pixelCount > 0) totalBrightness / pixelCount else 0.5f
        val variance = if (brightnesses.isNotEmpty()) {
            brightnesses.map { (it - avgBrightness).pow(2) }.average().toFloat()
        } else {
            0f
        }
        val contrast = sqrt(variance)
        
        return RegionStats(avgBrightness, contrast, edgeCount)
    }
    
    private fun calculateSymmetry(regions: Array<Array<RegionStats>>): Float {
        val topBrightness = (regions[0][0].brightness + regions[0][1].brightness + regions[0][2].brightness) / 3f
        val bottomBrightness = (regions[2][0].brightness + regions[2][1].brightness + regions[2][2].brightness) / 3f
        val horizontalSymmetry = 1f - abs(topBrightness - bottomBrightness)
        
        val leftBrightness = (regions[0][0].brightness + regions[1][0].brightness + regions[2][0].brightness) / 3f
        val rightBrightness = (regions[0][2].brightness + regions[1][2].brightness + regions[2][2].brightness) / 3f
        val verticalSymmetry = 1f - abs(leftBrightness - rightBrightness)
        
        return (horizontalSymmetry + verticalSymmetry) / 2f
    }
    
    private fun calculateRuleOfThirdsScore(regions: Array<Array<RegionStats>>): Float {
        val powerPoints = listOf(
            regions[0][0], regions[0][2],
            regions[2][0], regions[2][2]
        )
        
        val avgContrast = regions.flatten().map { it.contrast }.average().toFloat()
        val powerPointContrast = powerPoints.map { it.contrast }.average().toFloat()
        
        return if (avgContrast > 0) {
            (powerPointContrast / avgContrast).coerceIn(0f, 1f)
        } else {
            0.5f
        }
    }
    
    private fun calculateCenterWeight(regions: Array<Array<RegionStats>>): Float {
        val centerBrightness = regions[1][1].brightness
        val cornerBrightness = (regions[0][0].brightness + regions[0][2].brightness +
                                 regions[2][0].brightness + regions[2][2].brightness) / 4f
        
        return if (centerBrightness > cornerBrightness) {
            0.5f + (centerBrightness - cornerBrightness) / 2f
        } else {
            0.5f - (cornerBrightness - centerBrightness) / 2f
        }.coerceIn(0f, 1f)
    }
    
    private fun calculateEdgeDensity(regions: Array<Array<RegionStats>>): Float {
        val edgeRegions = listOf(
            regions[0][0], regions[0][1], regions[0][2],
            regions[1][0], regions[1][2],
            regions[2][0], regions[2][1], regions[2][2]
        )
        
        val centerEdges = regions[1][1].edgeCount.toFloat()
        val edgeEdges = edgeRegions.map { it.edgeCount }.average().toFloat()
        
        return if (edgeEdges + centerEdges > 0) {
            (edgeEdges / (edgeEdges + centerEdges)).coerceIn(0f, 1f)
        } else {
            0.5f
        }
    }
    
    private fun calculateComplexity(regions: Array<Array<RegionStats>>): Float {
        val totalEdges = regions.flatten().sumOf { it.edgeCount }.toFloat()
        val avgContrast = regions.flatten().map { it.contrast }.average().toFloat()
        
        val edgeScore = (totalEdges / 1000f).coerceIn(0f, 1f)
        val contrastScore = avgContrast.coerceIn(0f, 1f)
        
        return (edgeScore + contrastScore) / 2f
    }
    
    private fun calculateContrastDistribution(regions: Array<Array<RegionStats>>): Float {
        val contrasts = regions.flatten().map { it.contrast }
        val avgContrast = contrasts.average().toFloat()
        val variance = contrasts.map { (it - avgContrast).pow(2) }.average().toFloat()
        
        return 1f - sqrt(variance).coerceIn(0f, 1f)
    }
}

data class CompositionAnalysis(
    val symmetryScore: Float,
    val ruleOfThirdsScore: Float,
    val centerWeight: Float,
    val edgeDensity: Float,
    val complexity: Float,
    val contrastDistribution: Float,
    val brightnessMap: List<List<Float>>
) {
    fun isEmpty(): Boolean = brightnessMap.isEmpty()
    
    companion object {
        fun empty() = CompositionAnalysis(
            symmetryScore = 0.5f,
            ruleOfThirdsScore = 0.5f,
            centerWeight = 0.5f,
            edgeDensity = 0.5f,
            complexity = 0.5f,
            contrastDistribution = 0.5f,
            brightnessMap = emptyList()
        )
    }
}

data class CompositionPreferenceProfile(
    val preferredSymmetry: Float,
    val preferredRuleOfThirds: Float,
    val preferredCenterWeight: Float,
    val preferredEdgeDensity: Float,
    val preferredComplexity: Float,
    val confidence: Float
) {
    companion object {
        fun neutral() = CompositionPreferenceProfile(
            preferredSymmetry = 0.5f,
            preferredRuleOfThirds = 0.5f,
            preferredCenterWeight = 0.5f,
            preferredEdgeDensity = 0.5f,
            preferredComplexity = 0.5f,
            confidence = 0f
        )
    }
}
