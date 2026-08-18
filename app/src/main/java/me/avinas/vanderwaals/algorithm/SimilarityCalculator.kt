package me.avinas.vanderwaals.algorithm

import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Computes cosine embedding similarity, CIELAB color matching, and category alignment scores.
 */
class SimilarityCalculator {
    
    companion object {
        private val EMBEDDING_WEIGHT = RecommendationWeights.EMBEDDING_WEIGHT
        private val COLOR_WEIGHT = RecommendationWeights.COLOR_WEIGHT
        private val COMPOSITION_WEIGHT = RecommendationWeights.COMPOSITION_WEIGHT
        private val CATEGORY_WEIGHT = RecommendationWeights.CATEGORY_WEIGHT
        private val STANDARD_WEIGHTS_SUM = RecommendationWeights.STANDARD_WEIGHTS_SUM

        private const val BRIGHTNESS_TOLERANCE = 20
        private const val CONTRAST_TOLERANCE = 15
    }
    
    fun calculateSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        if (embedding1.isEmpty() || embedding2.isEmpty()) {
            return 0f
        }
        
        if (embedding1.size != embedding2.size) {
            return 0f
        }
        
        return cosineSimilarity(embedding1, embedding2)
    }
    
    fun calculateCompositeSimilarity(
        userEmbedding: FloatArray,
        userColors: List<String>,
        userCategory: String?,
        userBrightness: Int,
        userContrast: Int,
        wallpaper: WallpaperMetadata,
        dislikedEmbedding: FloatArray? = null
    ): Float {
        val embeddingScore = cosineSimilarity(userEmbedding, wallpaper.embedding)

        val dislikePenalty = if (dislikedEmbedding != null && wallpaper.embedding.isNotEmpty()) {
            cosineSimilarity(dislikedEmbedding, wallpaper.embedding).coerceAtLeast(0f) * 0.30f
        } else 0f

        val colorScore = calculateColorSimilarity(userColors, wallpaper.colors)

        val categoryScore = calculateCategoryBonus(
            userCategory = userCategory,
            userBrightness = userBrightness,
            userContrast = userContrast,
            wallpaper = wallpaper
        )

        return (((embeddingScore - dislikePenalty).coerceAtLeast(0f) * EMBEDDING_WEIGHT) +
               (colorScore * COLOR_WEIGHT) +
               (categoryScore * CATEGORY_WEIGHT)) / STANDARD_WEIGHTS_SUM
    }
    
    fun calculateEnhancedSimilarity(
        userEmbedding: FloatArray,
        userAnalysis: EnhancedImageAnalyzer.ImageAnalysis?,
        userColors: List<String>,
        userCategory: String?,
        userBrightness: Int,
        userContrast: Int,
        wallpaper: WallpaperMetadata,
        wallpaperAnalysis: EnhancedImageAnalyzer.ImageAnalysis?,
        dislikedEmbedding: FloatArray? = null
    ): Float {
        val embeddingScore = cosineSimilarity(userEmbedding, wallpaper.embedding)

        val dislikePenalty = if (dislikedEmbedding != null && wallpaper.embedding.isNotEmpty()) {
            cosineSimilarity(dislikedEmbedding, wallpaper.embedding).coerceAtLeast(0f) * 0.30f
        } else 0f
        val adjustedEmbeddingScore = (embeddingScore - dislikePenalty).coerceAtLeast(0f)

        return if (userAnalysis != null && wallpaperAnalysis != null) {
            val semanticScore = EnhancedImageAnalyzer.calculateSemanticSimilarity(
                userAnalysis,
                wallpaperAnalysis
            )
            (adjustedEmbeddingScore * EMBEDDING_WEIGHT) + (semanticScore * (1f - EMBEDDING_WEIGHT))
        } else {
            val colorScore = calculateColorSimilarity(userColors, wallpaper.colors)
            val categoryScore = calculateCategoryBonus(
                userCategory = userCategory,
                userBrightness = userBrightness,
                userContrast = userContrast,
                wallpaper = wallpaper
            )
            ((adjustedEmbeddingScore * EMBEDDING_WEIGHT) +
            (colorScore * COLOR_WEIGHT) +
            (categoryScore * CATEGORY_WEIGHT)) / STANDARD_WEIGHTS_SUM
        }
    }
    
    private fun cosineSimilarity(vector1: FloatArray, vector2: FloatArray): Float {
        if (vector1.isEmpty() || vector2.isEmpty() || vector1.size != vector2.size) {
            return 0f
        }

        var dotProduct = 0f
        var magnitude1 = 0f
        var magnitude2 = 0f
        
        for (i in vector1.indices) {
            dotProduct += vector1[i] * vector2[i]
            magnitude1 += vector1[i] * vector1[i]
            magnitude2 += vector2[i] * vector2[i]
        }
        
        // Handle zero magnitude (should not happen with normalized embeddings)
        if (magnitude1 == 0f || magnitude2 == 0f) {
            return 0f
        }
        
        magnitude1 = sqrt(magnitude1)
        magnitude2 = sqrt(magnitude2)
        
        val cosineSimilarity = dotProduct / (magnitude1 * magnitude2)
        
        // Normalize from [-1, 1] to [0, 1] for consistency
        return (cosineSimilarity + 1f) / 2f
    }
    
    private fun calculateColorSimilarity(colors1: List<String>, colors2: List<String>): Float {
        if (colors1.isEmpty() || colors2.isEmpty()) {
            return 0.5f
        }
        
        val rgb1 = colors1.mapNotNull { parseHexColor(it) }
        val rgb2 = colors2.mapNotNull { parseHexColor(it) }
        
        if (rgb1.isEmpty() || rgb2.isEmpty()) {
            return 0.5f
        }
        
        var totalDistance = 0f
        for (color1 in rgb1) {
            val minDistance = rgb2.minOf { color2 ->
                euclideanColorDistance(color1, color2)
            }
            totalDistance += minDistance
        }
        
        val avgDistance = totalDistance / rgb1.size
        val maxDistance = 100f
        val normalizedDistance = (avgDistance / maxDistance).coerceIn(0f, 1f)
        
        return 1f - normalizedDistance
    }
    
    private fun calculateCategoryBonus(
        userCategory: String?,
        userBrightness: Int,
        userContrast: Int,
        wallpaper: WallpaperMetadata
    ): Float {
        var bonus = 0.5f
        
        if (userCategory != null && userCategory.isNotBlank()) {
            if (userCategory.equals(wallpaper.category, ignoreCase = true)) {
                bonus += 0.3f
            }
        }
        
        val brightnessDiff = abs(userBrightness - wallpaper.brightness)
        if (brightnessDiff <= BRIGHTNESS_TOLERANCE) {
            val brightnessBonus = 0.2f * (1f - (brightnessDiff.toFloat() / BRIGHTNESS_TOLERANCE))
            bonus += brightnessBonus
        }
        
        val contrastDiff = abs(userContrast - wallpaper.contrast)
        if (contrastDiff <= CONTRAST_TOLERANCE) {
            val contrastBonus = 0.15f * (1f - (contrastDiff.toFloat() / CONTRAST_TOLERANCE))
            bonus += contrastBonus
        }
        
        return bonus.coerceIn(0f, 1f)
    }
    
    /**
     * Parses hex color string to RGB triple.
     * 
     * @param hexColor Hex color string (e.g., "#FF5733" or "FF5733")
     * @return RGB triple [r, g, b] where each component is 0-255, or null if invalid
     */
    private fun parseHexColor(hexColor: String): Triple<Int, Int, Int>? {
        return try {
            val color = hexColor.removePrefix("#")
            if (color.length != 6) return null
            
            val r = color.substring(0, 2).toInt(16)
            val g = color.substring(2, 4).toInt(16)
            val b = color.substring(4, 6).toInt(16)
            
            Triple(r, g, b)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Perceptual colour distance (CIE76 ΔE) between two colours given as RGB triples.
     *
     * Operates in the perceptually-uniform CIELab colour space so distances closely match
     * how humans perceive colour differences (unlike Euclidean RGB distance).
     * Max ΔE ≈ 100 (black ↔ white).
     *
     * @param color1 First RGB triple
     * @param color2 Second RGB triple
     * @return CIE76 ΔE (0 to ~100)
     */
    private fun euclideanColorDistance(
        color1: Triple<Int, Int, Int>,
        color2: Triple<Int, Int, Int>
    ): Float = labDeltaE(color1, color2).toFloat()

    /**
     * Converts an RGB triple to CIELab (D65 illuminant).
     * Delegates to the shared [me.avinas.vanderwaals.core.ColorSpace] implementation.
     */
    private fun rgbTripleToLab(c: Triple<Int, Int, Int>): Triple<Double, Double, Double> =
        me.avinas.vanderwaals.core.ColorSpace.rgbToLab(c.first, c.second, c.third)

    /** CIE76 ΔE between two RGB triples. */
    private fun labDeltaE(c1: Triple<Int, Int, Int>, c2: Triple<Int, Int, Int>): Double {
        val lab1 = rgbTripleToLab(c1)
        val lab2 = rgbTripleToLab(c2)
        return me.avinas.vanderwaals.core.ColorSpace.labDeltaE(lab1, lab2)
    }
}

