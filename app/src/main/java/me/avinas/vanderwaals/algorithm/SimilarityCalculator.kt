package me.avinas.vanderwaals.algorithm

import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Calculates similarity scores between wallpaper embeddings using cosine similarity and color matching.
 * 
 * This class implements the ranking algorithm that combines:
 * - Embedding similarity (70% weight): Cosine similarity between 576-dimensional vectors
 * - Color similarity (20% weight): Distance between color palettes in RGB space
 * - Category bonus (10% weight): Boost for matching categories and brightness
 * 
 * The final ranking formula:
 * ```
 * final_score = (embedding_score × 0.7) + (color_score × 0.2) + (category_bonus × 0.1)
 * ```
 * 
 * @see EmbeddingExtractor for generating embeddings
 * @see PreferenceUpdater for updating user preferences
 */
class SimilarityCalculator {
    
    companion object {
        // ENHANCED WEIGHTS: Focus more on deep semantic understanding
        private const val EMBEDDING_WEIGHT = 0.75f       // Increased: MobileNetV3 captures aesthetic essence
        private const val COLOR_WEIGHT = 0.10f           // Reduced: Now using perceptual LAB matching
        private const val COMPOSITION_WEIGHT = 0.10f     // New: Visual composition similarity
        private const val CATEGORY_WEIGHT = 0.05f        // Reduced: Less reliable across sources
        
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
        wallpaper: WallpaperMetadata
    ): Float {
        // 1. Embedding similarity (70% weight)
        val embeddingScore = cosineSimilarity(userEmbedding, wallpaper.embedding)
        
        // 2. Color similarity (20% weight)
        val colorScore = calculateColorSimilarity(userColors, wallpaper.colors)
        
        // 3. Category and brightness bonus (10% weight)
        val categoryScore = calculateCategoryBonus(
            userCategory = userCategory,
            userBrightness = userBrightness,
            userContrast = userContrast,
            wallpaper = wallpaper
        )
        
        // Combine weighted scores
        return (embeddingScore * EMBEDDING_WEIGHT) + 
               (colorScore * COLOR_WEIGHT) + 
               (categoryScore * CATEGORY_WEIGHT)
    }
    
    /**
     * ENHANCED: Calculates semantic similarity using deep image analysis.
     * 
     * This method provides superior matching for uploaded wallpapers by analyzing:
     * - Deep aesthetic features (MobileNetV3 embeddings)
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
        wallpaperAnalysis: EnhancedImageAnalyzer.ImageAnalysis?
    ): Float {
        // 1. Embedding similarity (75% weight) - Core aesthetic understanding
        val embeddingScore = cosineSimilarity(userEmbedding, wallpaper.embedding)
        
        // 2. If we have enhanced analysis, use semantic similarity for remaining 25%
        return if (userAnalysis != null && wallpaperAnalysis != null) {
            val semanticScore = EnhancedImageAnalyzer.calculateSemanticSimilarity(
                userAnalysis, 
                wallpaperAnalysis
            )
            
            // Combine: 75% embedding (deep learning) + 25% semantic (composition/mood/color)
            (embeddingScore * EMBEDDING_WEIGHT) + (semanticScore * (1f - EMBEDDING_WEIGHT))
        } else {
            // Fallback to standard composite similarity
            val colorScore = calculateColorSimilarity(userColors, wallpaper.colors)
            val categoryScore = calculateCategoryBonus(
                userCategory = userCategory,
                userBrightness = userBrightness,
                userContrast = userContrast,
                wallpaper = wallpaper
            )
            
            (embeddingScore * EMBEDDING_WEIGHT) + 
            (colorScore * COLOR_WEIGHT) + 
            (categoryScore * CATEGORY_WEIGHT)
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
        
        // Normalize: max possible distance in RGB space is sqrt(3 * 255^2) ≈ 441
        val maxDistance = 441f
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
     * Calculates Euclidean distance between two colors in RGB space.
     * 
     * @param color1 First RGB triple
     * @param color2 Second RGB triple
     * @return Euclidean distance (0 to ~441)
     */
    private fun euclideanColorDistance(
        color1: Triple<Int, Int, Int>,
        color2: Triple<Int, Int, Int>
    ): Float {
        val dr = (color1.first - color2.first).toFloat()
        val dg = (color1.second - color2.second).toFloat()
        val db = (color1.third - color2.third).toFloat()
        
        return sqrt(dr * dr + dg * dg + db * db)
    }
}

