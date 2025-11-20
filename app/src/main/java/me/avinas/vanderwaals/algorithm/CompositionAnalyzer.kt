package me.avinas.vanderwaals.algorithm

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Composition analysis utility for learning wallpaper layout preferences.
 * 
 * Analyzes visual composition characteristics:
 * - **Symmetry**: Horizontal/vertical balance
 * - **Rule of Thirds**: Important elements at power points
 * - **Center Weight**: Subject positioning (centered vs off-center)
 * - **Edge Patterns**: Border density and framing
 * - **Complexity**: Visual detail and texture density
 * - **Contrast Distribution**: Light/dark balance across regions
 * 
 * **Use Cases:**
 * - Learning composition preferences from feedback
 * - Matching wallpapers to user's layout style
 * - Detecting patterns in liked compositions
 * - Providing composition-aware recommendations
 * 
 * **Analysis Method:**
 * - Divides image into 3x3 grid (rule of thirds)
 * - Calculates brightness/contrast per region
 * - Compares symmetry across axes
 * - Measures center vs edge weight distribution
 * 
 * @see CompositionPreference
 * @see SelectNextWallpaperUseCase
 */
object CompositionAnalyzer {
    
    /**
     * Analyzes a wallpaper image file for composition characteristics.
     * 
     * @param imageFile Wallpaper image file
     * @return CompositionAnalysis with detailed layout insights
     */
    fun analyzeComposition(imageFile: File): CompositionAnalysis {
        if (!imageFile.exists()) {
            return CompositionAnalysis.empty()
        }
        
        // Load bitmap with downsampling for performance
        val options = BitmapFactory.Options().apply {
            inSampleSize = 4 // 1/4 size for faster analysis
        }
        
        val bitmap = try {
            BitmapFactory.decodeFile(imageFile.absolutePath, options)
        } catch (e: Exception) {
            return CompositionAnalysis.empty()
        }
        
        if (bitmap == null) {
            return CompositionAnalysis.empty()
        }
        
        val result = analyzeBitmap(bitmap)
        bitmap.recycle()
        return result
    }
    
    /**
     * Analyzes a bitmap for composition characteristics.
     * 
     * Internal method - exposed for testing.
     * 
     * @param bitmap Image bitmap
     * @return CompositionAnalysis with detailed insights
     */
    fun analyzeBitmap(bitmap: Bitmap): CompositionAnalysis {
        val width = bitmap.width
        val height = bitmap.height
        
        // Divide into 3x3 grid (rule of thirds)
        val gridWidth = width / 3
        val gridHeight = height / 3
        
        val regions = Array(3) { row ->
            Array(3) { col ->
                analyzeRegion(
                    bitmap,
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
    
    /**
     * Calculates similarity between two compositions.
     * 
     * Considers:
     * - Symmetry similarity
     * - Rule of thirds similarity
     * - Center weight similarity
     * - Edge density similarity
     * - Complexity similarity
     * 
     * @param comp1 First composition
     * @param comp2 Second composition
     * @return Similarity score 0.0 (completely different) to 1.0 (identical)
     */
    fun calculateCompositionSimilarity(comp1: CompositionAnalysis, comp2: CompositionAnalysis): Float {
        if (comp1.isEmpty() || comp2.isEmpty()) {
            return 0.5f // Neutral when data unavailable
        }
        
        val symmetrySim = 1f - abs(comp1.symmetryScore - comp2.symmetryScore)
        val ruleOfThirdsSim = 1f - abs(comp1.ruleOfThirdsScore - comp2.ruleOfThirdsScore)
        val centerWeightSim = 1f - abs(comp1.centerWeight - comp2.centerWeight)
        val edgeDensitySim = 1f - abs(comp1.edgeDensity - comp2.edgeDensity)
        val complexitySim = 1f - abs(comp1.complexity - comp2.complexity)
        
        // Weighted combination
        return (symmetrySim * 0.25f +           // 25% symmetry
                ruleOfThirdsSim * 0.20f +       // 20% rule of thirds
                centerWeightSim * 0.25f +       // 25% center weight
                edgeDensitySim * 0.15f +        // 15% edge density
                complexitySim * 0.15f)          // 15% complexity
            .coerceIn(0f, 1f)
    }
    
    /**
     * Extracts composition preferences from liked/disliked wallpapers.
     * 
     * Analyzes patterns:
     * - Preferred symmetry level
     * - Rule of thirds usage
     * - Center vs edge preference
     * - Complexity preference
     * - Contrast style
     * 
     * @param likedCompositions Compositions from liked wallpapers
     * @param dislikedCompositions Compositions from disliked wallpapers
     * @return CompositionPreferenceProfile with learned preferences
     */
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
    
    /**
     * Calculates composition preference score based on learned preferences.
     * 
     * @param composition Wallpaper composition to score
     * @param preferences Learned composition preferences
     * @return Score from -1.0 (strongly dislike) to +1.0 (strongly like)
     */
    fun calculateCompositionPreferenceScore(
        composition: CompositionAnalysis,
        preferences: CompositionPreferenceProfile
    ): Float {
        if (composition.isEmpty() || preferences.confidence < 0.1f) {
            return 0f // Neutral when insufficient data
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
        
        // Weighted combination
        val rawScore = (symmetryScore * 0.25f +
                        ruleOfThirdsScore * 0.20f +
                        centerWeightScore * 0.25f +
                        edgeDensityScore * 0.15f +
                        complexityScore * 0.15f)
        
        // Apply confidence factor
        return (rawScore * 2f - 1f) * preferences.confidence // Map 0-1 to -1 to +1
    }
    
    // ========== Private Helper Methods ==========
    
    private data class RegionStats(
        val brightness: Float,
        val contrast: Float,
        val edgeCount: Int
    )
    
    private fun analyzeRegion(bitmap: Bitmap, startX: Int, startY: Int, width: Int, height: Int): RegionStats {
        var totalBrightness = 0f
        var pixelCount = 0
        val brightnesses = mutableListOf<Float>()
        var edgeCount = 0
        
        val endX = (startX + width).coerceAtMost(bitmap.width)
        val endY = (startY + height).coerceAtMost(bitmap.height)
        
        // Sample every 4th pixel for performance
        for (y in startY until endY step 4) {
            for (x in startX until endX step 4) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                
                val brightness = (r + g + b) / (3f * 255f)
                totalBrightness += brightness
                brightnesses.add(brightness)
                pixelCount++
                
                // Simple edge detection (brightness gradient)
                if (x < endX - 4 && y < endY - 4) {
                    val nextPixel = bitmap.getPixel(x + 4, y)
                    val nextR = (nextPixel shr 16) and 0xFF
                    val nextBrightness = (nextR + ((nextPixel shr 8) and 0xFF) + (nextPixel and 0xFF)) / (3f * 255f)
                    
                    if (abs(brightness - nextBrightness) > 0.2f) {
                        edgeCount++
                    }
                }
            }
        }
        
        val avgBrightness = if (pixelCount > 0) totalBrightness / pixelCount else 0.5f
        
        // Calculate contrast (standard deviation of brightness)
        val variance = if (brightnesses.isNotEmpty()) {
            brightnesses.map { (it - avgBrightness).pow(2) }.average().toFloat()
        } else {
            0f
        }
        val contrast = sqrt(variance)
        
        return RegionStats(avgBrightness, contrast, edgeCount)
    }
    
    private fun calculateSymmetry(regions: Array<Array<RegionStats>>): Float {
        // Horizontal symmetry (top vs bottom)
        val topBrightness = (regions[0][0].brightness + regions[0][1].brightness + regions[0][2].brightness) / 3f
        val bottomBrightness = (regions[2][0].brightness + regions[2][1].brightness + regions[2][2].brightness) / 3f
        val horizontalSymmetry = 1f - abs(topBrightness - bottomBrightness)
        
        // Vertical symmetry (left vs right)
        val leftBrightness = (regions[0][0].brightness + regions[1][0].brightness + regions[2][0].brightness) / 3f
        val rightBrightness = (regions[0][2].brightness + regions[1][2].brightness + regions[2][2].brightness) / 3f
        val verticalSymmetry = 1f - abs(leftBrightness - rightBrightness)
        
        return (horizontalSymmetry + verticalSymmetry) / 2f
    }
    
    private fun calculateRuleOfThirdsScore(regions: Array<Array<RegionStats>>): Float {
        // Power points: intersections of thirds lines
        // Check if interesting content (high contrast) at power points
        val powerPoints = listOf(
            regions[0][0], regions[0][2], // Top power points
            regions[2][0], regions[2][2]  // Bottom power points
        )
        
        val avgContrast = regions.flatten().map { it.contrast }.average().toFloat()
        val powerPointContrast = powerPoints.map { it.contrast }.average().toFloat()
        
        // High score if power points have more contrast than average
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
        
        // High score if center is brighter (subject in center)
        return if (centerBrightness > cornerBrightness) {
            0.5f + (centerBrightness - cornerBrightness) / 2f
        } else {
            0.5f - (cornerBrightness - centerBrightness) / 2f
        }.coerceIn(0f, 1f)
    }
    
    private fun calculateEdgeDensity(regions: Array<Array<RegionStats>>): Float {
        val edgeRegions = listOf(
            regions[0][0], regions[0][1], regions[0][2], // Top
            regions[1][0], regions[1][2],                // Sides
            regions[2][0], regions[2][1], regions[2][2]  // Bottom
        )
        
        val centerEdges = regions[1][1].edgeCount.toFloat()
        val edgeEdges = edgeRegions.map { it.edgeCount }.average().toFloat()
        
        // High score if more edges at borders (framed composition)
        return if (edgeEdges + centerEdges > 0) {
            (edgeEdges / (edgeEdges + centerEdges)).coerceIn(0f, 1f)
        } else {
            0.5f
        }
    }
    
    private fun calculateComplexity(regions: Array<Array<RegionStats>>): Float {
        val totalEdges = regions.flatten().sumOf { it.edgeCount }.toFloat()
        val avgContrast = regions.flatten().map { it.contrast }.average().toFloat()
        
        // Normalize to 0-1 range (arbitrary scaling based on typical values)
        val edgeScore = (totalEdges / 1000f).coerceIn(0f, 1f)
        val contrastScore = avgContrast.coerceIn(0f, 1f)
        
        return (edgeScore + contrastScore) / 2f
    }
    
    private fun calculateContrastDistribution(regions: Array<Array<RegionStats>>): Float {
        val contrasts = regions.flatten().map { it.contrast }
        val avgContrast = contrasts.average().toFloat()
        val variance = contrasts.map { (it - avgContrast).pow(2) }.average().toFloat()
        
        // High score for uniform contrast distribution
        return 1f - sqrt(variance).coerceIn(0f, 1f)
    }
}

/**
 * Detailed composition analysis of a wallpaper.
 */
data class CompositionAnalysis(
    val symmetryScore: Float,         // 0-1, how balanced is the image
    val ruleOfThirdsScore: Float,     // 0-1, important elements at power points
    val centerWeight: Float,           // 0-1, subject centered (1) or edge (0)
    val edgeDensity: Float,            // 0-1, detail at borders vs center
    val complexity: Float,             // 0-1, visual detail level
    val contrastDistribution: Float,  // 0-1, uniform contrast
    val brightnessMap: List<List<Float>> // 3x3 grid of brightness values
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

/**
 * Learned composition preferences from user feedback.
 */
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
