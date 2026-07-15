package me.avinas.vanderwaals.algorithm

import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Calculates similarity scores between wallpaper embeddings using cosine similarity and color matching.
 *
 * This class implements the ranking algorithm that combines:
 * - Embedding similarity (75% weight): Cosine similarity between 1280-dimensional vectors
 * - Color similarity (12% weight): CIE76 ΔE distance in LAB colour space
 * - Category bonus (2% weight): Boost for matching categories and brightness
 *
 * Weights are centralised in [RecommendationWeights].  Non-enhanced paths
 * renormalise by [RecommendationWeights.STANDARD_WEIGHTS_SUM] (excludes
 * composition) so a perfect match scores 1.0.
 * 
 * @see EmbeddingExtractor for generating embeddings
 * @see PreferenceUpdater for updating user preferences
 */
class SimilarityCalculator {
    
    companion object {
        // Canonical weights — see RecommendationWeights for documentation.
        private val EMBEDDING_WEIGHT = RecommendationWeights.EMBEDDING_WEIGHT
        private val COLOR_WEIGHT = RecommendationWeights.COLOR_WEIGHT
        private val COMPOSITION_WEIGHT = RecommendationWeights.COMPOSITION_WEIGHT
        private val CATEGORY_WEIGHT = RecommendationWeights.CATEGORY_WEIGHT
        private val STANDARD_WEIGHTS_SUM = RecommendationWeights.STANDARD_WEIGHTS_SUM

        // Brightness tolerance for matching (±20 on 0-100 scale)
        private const val BRIGHTNESS_TOLERANCE = 20
        
        // Contrast tolerance for matching (±15 on 0-100 scale)
        private const val CONTRAST_TOLERANCE = 15
    }
    
    /**
     * Calculates cosine similarity between two embedding vectors.
     * 
     * @param embedding1 First embedding vector
     * @param embedding2 Second embedding vector
     * @return Similarity score between 0 and 1 (normalized)
     */
    fun calculateSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        if (embedding1.isEmpty() || embedding2.isEmpty()) {
            return 0f
        }
        
        if (embedding1.size != embedding2.size) {
            return 0f
        }
        
        return cosineSimilarity(embedding1, embedding2)
    }
    
    /**
     * Calculates composite similarity score between user preference and a wallpaper.
     * Combines embedding similarity, color matching, and category bonus as per strategy.
     * 
     * @param userEmbedding User's preference embedding vector
     * @param userColors User's preferred color palette (hex strings)
     * @param userCategory User's preferred category (optional)
     * @param userBrightness User's preferred brightness level (0-100)
     * @param userContrast User's preferred contrast level (0-100)
     * @param wallpaper Target wallpaper to compare
     * @return Composite similarity score (0.0 to 1.0)
     */
    fun calculateCompositeSimilarity(
        userEmbedding: FloatArray,
        userColors: List<String>,
        userCategory: String?,
        userBrightness: Int,
        userContrast: Int,
        wallpaper: WallpaperMetadata,
        dislikedEmbedding: FloatArray? = null
    ): Float {
        // 1. Embedding similarity (primary aesthetic signal)
        val embeddingScore = cosineSimilarity(userEmbedding, wallpaper.embedding)

        // 2. Dislike penalty: reduce score for wallpapers whose embedding is positively
        //    similar to the disliked content centroid.  This doubles down on the negative
        //    EMA signal already encoded in userEmbedding, catching residual similarity.
        val dislikePenalty = if (dislikedEmbedding != null && wallpaper.embedding.isNotEmpty()) {
            // cosineSimilarity returns [0, 1]; clamp at 0 so anti-correlated content gets no penalty
            cosineSimilarity(dislikedEmbedding, wallpaper.embedding).coerceAtLeast(0f) * 0.30f
        } else 0f

        // 3. Color similarity using perceptual CIE76 ΔE distance
        val colorScore = calculateColorSimilarity(userColors, wallpaper.colors)

        // 4. Category and brightness bonus
        val categoryScore = calculateCategoryBonus(
            userCategory = userCategory,
            userBrightness = userBrightness,
            userContrast = userContrast,
            wallpaper = wallpaper
        )

        // Combine weighted scores; dislike penalty applied inside the embedding term.
        // Renormalise by STANDARD_WEIGHTS_SUM so a perfect match scores 1.0
        // (composition weight is excluded here because no composition data is
        // available in this code path).
        return (((embeddingScore - dislikePenalty).coerceAtLeast(0f) * EMBEDDING_WEIGHT) +
               (colorScore * COLOR_WEIGHT) +
               (categoryScore * CATEGORY_WEIGHT)) / STANDARD_WEIGHTS_SUM
    }
    
    /**
     * ENHANCED: Calculates semantic similarity using deep image analysis.
     * 
     * This method provides superior matching for uploaded wallpapers by analyzing:
     * - Deep aesthetic features (MobileNetV4 embeddings)
     * - Perceptual color matching (LAB color space)
     * - Visual composition (rule of thirds, symmetry, balance)
     * - Mood and atmosphere (warmth, energy, contrast)
     * 
     * Use this for initial wallpaper matching from uploaded images.
     * 
     * @param userEmbedding User's preference embedding vector
     * @param userAnalysis Enhanced analysis of user's uploaded image (optional)
     * @param userColors User's preferred color palette (hex strings)
     * @param userCategory User's preferred category (optional)
     * @param userBrightness User's preferred brightness level (0-100)
     * @param userContrast User's preferred contrast level (0-100)
     * @param wallpaper Target wallpaper to compare
     * @param wallpaperAnalysis Enhanced analysis of target wallpaper (optional)
     * @return Enhanced similarity score (0.0 to 1.0)
     */
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
        // 1. Embedding similarity (75% weight) - Core aesthetic understanding
        val embeddingScore = cosineSimilarity(userEmbedding, wallpaper.embedding)

        // 2. Dislike penalty — same logic as calculateCompositeSimilarity
        val dislikePenalty = if (dislikedEmbedding != null && wallpaper.embedding.isNotEmpty()) {
            cosineSimilarity(dislikedEmbedding, wallpaper.embedding).coerceAtLeast(0f) * 0.30f
        } else 0f
        val adjustedEmbeddingScore = (embeddingScore - dislikePenalty).coerceAtLeast(0f)

        // 3. If we have enhanced analysis, use semantic similarity for remaining 25%
        return if (userAnalysis != null && wallpaperAnalysis != null) {
            val semanticScore = EnhancedImageAnalyzer.calculateSemanticSimilarity(
                userAnalysis,
                wallpaperAnalysis
            )
            // Combine: 75% adjusted embedding + 25% semantic (composition/mood/colour)
            (adjustedEmbeddingScore * EMBEDDING_WEIGHT) + (semanticScore * (1f - EMBEDDING_WEIGHT))
        } else {
            // Fallback to standard composite similarity (renormalised, same as
            // calculateCompositeSimilarity, so a perfect match scores 1.0).
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
    
    /**
     * Calculates cosine similarity between two vectors.
     * Uses efficient dot product and normalization.
     * 
     * @param vector1 First vector
     * @param vector2 Second vector
     * @return Normalized similarity score (0.0 to 1.0)
     */
    private fun cosineSimilarity(vector1: FloatArray, vector2: FloatArray): Float {
        if (vector1.isEmpty() || vector2.isEmpty() || vector1.size != vector2.size) {
            return 0f
        }

        // Calculate dot product and magnitudes
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
        
        // Calculate cosine similarity
        val cosineSimilarity = dotProduct / (magnitude1 * magnitude2)
        
        // Normalize from [-1, 1] to [0, 1] for consistency
        return (cosineSimilarity + 1f) / 2f
    }
    
    /**
     * Calculates color similarity between two color palettes.
     * Uses average Euclidean distance in RGB space.
     * 
     * Algorithm:
     * 1. Parse hex colors to RGB values
     * 2. Find best matching pair for each color
     * 3. Calculate average distance in RGB space
     * 4. Normalize to 0-1 range (max distance = sqrt(3*255^2))
     * 
     * @param colors1 First color palette (hex strings like "#FF5733")
     * @param colors2 Second color palette (hex strings)
     * @return Color similarity score (0.0 to 1.0, higher = more similar)
     */
    private fun calculateColorSimilarity(colors1: List<String>, colors2: List<String>): Float {
        if (colors1.isEmpty() || colors2.isEmpty()) {
            return 0.5f // Neutral score if no color data
        }
        
        // Parse hex colors to RGB triples
        val rgb1 = colors1.mapNotNull { parseHexColor(it) }
        val rgb2 = colors2.mapNotNull { parseHexColor(it) }
        
        if (rgb1.isEmpty() || rgb2.isEmpty()) {
            return 0.5f // Neutral score if parsing failed
        }
        
        // Calculate best matching distance for each color in palette 1
        var totalDistance = 0f
        for (color1 in rgb1) {
            // Find closest matching color in palette 2
            val minDistance = rgb2.minOf { color2 ->
                euclideanColorDistance(color1, color2)
            }
            totalDistance += minDistance
        }
        
        // Average distance per color
        val avgDistance = totalDistance / rgb1.size
        
        // Normalize: max CIE76 ΔE ≈ 100 (black ↔ white in CIELab space)
        val maxDistance = 100f
        val normalizedDistance = (avgDistance / maxDistance).coerceIn(0f, 1f)
        
        // Convert distance to similarity (inverse)
        return 1f - normalizedDistance
    }
    
    /**
     * Calculates category and brightness bonus.
     * Rewards matching categories and similar brightness/contrast levels.
     * 
     * @param userCategory User's preferred category (nullable)
     * @param userBrightness User's preferred brightness (0-100)
     * @param userContrast User's preferred contrast (0-100)
     * @param wallpaper Target wallpaper
     * @return Bonus score (0.0 to 1.0)
     */
    private fun calculateCategoryBonus(
        userCategory: String?,
        userBrightness: Int,
        userContrast: Int,
        wallpaper: WallpaperMetadata
    ): Float {
        var bonus = 0.5f // Start neutral
        
        // Category match bonus (50% of category weight)
        if (userCategory != null && userCategory.isNotBlank()) {
            if (userCategory.equals(wallpaper.category, ignoreCase = true)) {
                bonus += 0.3f
            }
        }
        
        // Brightness proximity bonus (30% of category weight)
        val brightnessDiff = abs(userBrightness - wallpaper.brightness)
        if (brightnessDiff <= BRIGHTNESS_TOLERANCE) {
            val brightnessBonus = 0.2f * (1f - (brightnessDiff.toFloat() / BRIGHTNESS_TOLERANCE))
            bonus += brightnessBonus
        }
        
        // Contrast proximity bonus (20% of category weight)
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

