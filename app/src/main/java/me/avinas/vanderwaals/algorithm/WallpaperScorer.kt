package me.avinas.vanderwaals.algorithm

import android.content.Context
import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import me.avinas.vanderwaals.data.repository.CategoryPreferenceRepository
import me.avinas.vanderwaals.data.repository.ColorPreferenceRepository
import me.avinas.vanderwaals.data.repository.CompositionPreferenceRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Centralized scoring logic for wallpaper ranking and selection.
 * 
 * Extracted from SelectNextWallpaperUseCase to improve:
 * - **Single Responsibility**: Scoring logic is now isolated and testable
 * - **Reusability**: Scoring can be used by other components (e.g., search, similar wallpapers)
 * - **Maintainability**: Changes to scoring algorithms are localized here
 * 
 * **Scoring Components:**
 * - Category boost: +/- 15% based on user's category feedback
 * - Color boost: +/- 12% based on color palette preferences
 * - Composition boost: +/- 8% based on layout/symmetry preferences
 * - Temporal diversity: +/- 15% to prevent repetition and encourage variety
 * - Quality score: 0-30% for cold-start based on universal quality signals
 * 
 * @see SelectNextWallpaperUseCase
 * @see SimilarityCalculator
 */
@Singleton
class WallpaperScorer @Inject constructor(
    private val categoryPreferenceRepository: CategoryPreferenceRepository,
    private val colorPreferenceRepository: ColorPreferenceRepository,
    private val compositionPreferenceRepository: CompositionPreferenceRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) {
    private val colorAnalyzer = ColorAnalyzer
    private val compositionAnalyzer = CompositionAnalyzer
    
    /**
     * Calculates content-based boost with category fallback to color similarity.
     * 
     * **Strategy:**
     * - If wallpaper has category: use category feedback boost (15% weight)
     * - If wallpaper has no category: use color similarity boost (12% weight)
     * 
     * @param wallpaper Wallpaper to calculate boost for
     * @return Boost value from -0.15 to +0.15 (category) or -0.12 to +0.12 (color)
     */
    suspend fun getContentBoost(wallpaper: WallpaperMetadata): Float {
        return if (wallpaper.category.isNotBlank()) {
            getCategoryBoost(wallpaper.category)
        } else {
            getColorBoost(wallpaper.colors)
        }
    }
    
    /**
     * Calculates category-based boost for wallpaper ranking.
     * 
     * Uses user's feedback history for each category to boost/penalize wallpapers:
     * - Liked categories get positive boost (up to +0.15)
     * - Disliked categories get negative penalty (down to -0.15)
     * - New/neutral categories get no adjustment (0.0)
     * 
     * @param category Category name (e.g., "nature", "minimal")
     * @return Boost value from -0.15 (strongly disliked) to +0.15 (strongly liked)
     */
    suspend fun getCategoryBoost(category: String): Float {
        return try {
            val categoryPref = categoryPreferenceRepository.getByCategory(category)
            if (categoryPref == null) {
                return 0f
            }
            
            val score = categoryPref.calculateScore()
            score * 0.15f
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error calculating category boost", e)
            0f
        }
    }
    
    /**
     * Calculates advanced color preference boost using ColorAnalyzer.
     * 
     * Uses HSV color space for perceptual matching:
     * - Analyzes color harmony (monochromatic, analogous, complementary)
     * - Classifies warm/cool tones and vibrant/muted characteristics
     * - Compares with learned color preferences from liked wallpapers
     * 
     * @param colors List of hex color codes from wallpaper palette
     * @return Boost value from -0.12 (disliked colors) to +0.12 (liked colors)
     */
    suspend fun getColorBoost(colors: List<String>): Float {
        return try {
            if (colors.isEmpty()) {
                return 0f
            }
            
            val likedColors = colorPreferenceRepository.getLikedColors()
            if (likedColors.isEmpty()) {
                return 0f
            }
            
            val likedPalette = colorAnalyzer.analyzePalette(likedColors)
            val colorPreferences = colorAnalyzer.extractColorPreferences(
                likedPalettes = listOf(likedPalette),
                dislikedPalettes = emptyList()
            )
            
            val wallpaperPalette = colorAnalyzer.analyzePalette(colors)
            val preferenceScore = colorAnalyzer.calculateColorPreferenceScore(
                palette = wallpaperPalette,
                preferences = colorPreferences
            )
            
            preferenceScore * 0.12f
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error calculating color boost", e)
            0f
        }
    }
    
    /**
     * Calculates composition preference boost using CompositionAnalyzer.
     * 
     * Analyzes wallpaper layout using:
     * - 3x3 grid (rule of thirds)
     * - Symmetry (horizontal, vertical)
     * - Center weight vs edge density
     * - Complexity (busy vs simple)
     * 
     * @param wallpaperId Wallpaper ID to analyze
     * @return Boost value from -0.08 (disliked composition) to +0.08 (liked composition)
     */
    suspend fun getCompositionBoost(wallpaperId: String): Float {
        return try {
            val preferences = compositionPreferenceRepository.getCompositionPreferencesOnce()
            if (preferences == null || preferences.sampleCount == 0) {
                return 0f
            }
            
            val wallpaperFile = File(context.filesDir, "wallpapers/$wallpaperId.jpg")
            if (!wallpaperFile.exists()) {
                return 0f
            }
            
            val composition = compositionAnalyzer.analyzeComposition(wallpaperFile)
                ?: return 0f
            
            val preferenceComposition = CompositionAnalysis(
                symmetryScore = preferences.averageSymmetry,
                ruleOfThirdsScore = preferences.averageRuleOfThirds,
                centerWeight = preferences.averageCenterWeight,
                edgeDensity = preferences.averageEdgeDensity,
                complexity = preferences.averageComplexity,
                contrastDistribution = 0.5f,
                brightnessMap = emptyList()
            )
            
            val similarity = compositionAnalyzer.calculateCompositionSimilarity(
                comp1 = composition,
                comp2 = preferenceComposition
            )
            
            val preferenceScore = (similarity - 0.5f) * 2f
            val confidence = preferences.calculateConfidence()
            val weightedScore = preferenceScore * confidence
            
            weightedScore * 0.08f
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error calculating composition boost for $wallpaperId", e)
            0f
        }
    }
    
    /**
     * Calculates temporal diversity boost to prevent category repetition.
     * 
     * **Recency Penalty:**
     * - Each occurrence in recent history (last 3 wallpapers): -5% penalty
     * 
     * **Exploration Boost:**
     * - New categories (never seen): +5% boost
     * - Rarely seen categories (<3 views): +5% boost
     * 
     * @param category Category name to evaluate
     * @param recentCategories List of categories from last 3 wallpapers
     * @return Boost value from -0.15 to +0.05
     */
    suspend fun getTemporalDiversityBoost(
        category: String,
        recentCategories: List<String>
    ): Float {
        return try {
            val recentCount = recentCategories.count { it == category }
            val recencyPenalty = recentCount * -0.05f
            
            val categoryPref = categoryPreferenceRepository.getByCategory(category)
            val exploreBoost = if (categoryPref == null || categoryPref.views < 3) {
                0.05f
            } else {
                0f
            }
            
            recencyPenalty + exploreBoost
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error calculating temporal diversity boost", e)
            0f
        }
    }
    
    /**
     * Calculates universal quality score for cold start (before first like).
     * 
     * **Scoring Components:**
     * 1. Source Base (0.4-0.75): Bing significantly higher than GitHub
     * 2. Quality Score (0.0-0.3): Resolution, aspect ratio, balance, colors, category
     * 3. Device Variation (0.0-0.1): Unique ordering per device
     * 
     * @param wallpaper The wallpaper to score
     * @param deviceSeed Device-specific random seed
     * @return Popularity score (0.4 to 1.05)
     */
    fun calculatePopularityScore(wallpaper: WallpaperMetadata, deviceSeed: Int): Float {
        val sourceBase = when (wallpaper.source.lowercase()) {
            "bing" -> 0.75f
            else -> 0.40f
        }
        
        val qualityScore = calculateQualityScore(wallpaper)
        val deviceVariation = ((deviceSeed + wallpaper.id.hashCode()).toLong() % 100) / 1000f
        
        return sourceBase + qualityScore + deviceVariation
    }
    
    /**
     * Calculate quality score based on wallpaper-specific attributes.
     * 
     * **Components (0.0-0.3 range):**
     * - Resolution: Higher is better (0-0.10)
     * - Aspect ratio: Portrait/square preferred (0-0.03)
     * - Balance: Moderate brightness/contrast (0-0.06)
     * - Color diversity: Rich palettes (0-0.04)
     * - Category hint: Universal appeal categories (0-0.07)
     */
    fun calculateQualityScore(wallpaper: WallpaperMetadata): Float {
        var score = 0f
        
        // Resolution bonus
        val resolutionParts = wallpaper.resolution.split("x")
        if (resolutionParts.size == 2) {
            val width = resolutionParts[0].toIntOrNull() ?: 0
            val height = resolutionParts[1].toIntOrNull() ?: 0
            val pixels = width * height
            
            score += when {
                pixels >= 3840 * 2160 -> 0.10f
                pixels >= 2560 * 1440 -> 0.08f
                pixels >= 1920 * 1080 -> 0.06f
                pixels >= 1280 * 720 -> 0.04f
                else -> 0.02f
            }
            
            // Aspect ratio bonus
            if (width > 0 && height > 0) {
                val aspectRatio = height.toFloat() / width.toFloat()
                score += when {
                    aspectRatio >= 1.5f && aspectRatio <= 2.2f -> 0.03f
                    aspectRatio >= 0.9f && aspectRatio <= 1.1f -> 0.02f
                    else -> 0.01f
                }
            }
        }
        
        // Contrast/Brightness balance bonus
        val contrastNormalized = wallpaper.contrast / 100f
        val brightnessNormalized = wallpaper.brightness / 100f
        val contrastBalance = 1f - abs(contrastNormalized - 0.5f) * 2f
        val brightnessBalance = 1f - abs(brightnessNormalized - 0.5f) * 2f
        score += (contrastBalance + brightnessBalance) * 0.03f
        
        // Color diversity bonus
        val colorCount = wallpaper.colors.size
        score += when {
            colorCount >= 5 -> 0.04f
            colorCount >= 3 -> 0.03f
            colorCount >= 2 -> 0.02f
            else -> 0.01f
        }
        
        // Category hint bonus
        score += when (wallpaper.category.lowercase()) {
            "nature", "aesthetic", "minimal", "space", "landscape" -> 0.07f
            "abstract", "dark", "city", "gradient", "architecture" -> 0.05f
            "art", "design", "pattern", "texture" -> 0.03f
            "anime", "gaming", "nord", "gruvbox", "cartoon" -> -0.02f
            else -> 0.0f
        }
        
        return score.coerceIn(0f, 0.3f)
    }
    
    /**
     * Calculates Euclidean distance between two RGB colors.
     */
    fun colorDistance(color1: Int, color2: Int): Float {
        val r1 = (color1 shr 16) and 0xFF
        val g1 = (color1 shr 8) and 0xFF
        val b1 = color1 and 0xFF
        
        val r2 = (color2 shr 16) and 0xFF
        val g2 = (color2 shr 8) and 0xFF
        val b2 = color2 and 0xFF
        
        val dr = r1 - r2
        val dg = g1 - g2
        val db = b1 - b2
        
        return kotlin.math.sqrt((dr * dr + dg * dg + db * db).toFloat())
    }
    
    /**
     * Parses hex color string to RGB integer.
     */
    fun parseHexToColor(hex: String): Int? {
        return try {
            val cleanHex = hex.removePrefix("#")
            android.graphics.Color.parseColor("#$cleanHex")
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Calculates color similarity using RGB Euclidean distance.
     */
    fun calculateColorSimilarity(
        wallpaperColors: List<Int>,
        preferredColors: List<Int>
    ): Float {
        if (wallpaperColors.isEmpty() || preferredColors.isEmpty()) {
            return 0.5f
        }
        
        var minDistance = Float.MAX_VALUE
        
        wallpaperColors.forEach { wColor ->
            preferredColors.forEach { pColor ->
                val distance = colorDistance(wColor, pColor)
                if (distance < minDistance) {
                    minDistance = distance
                }
            }
        }
        
        val maxDistance = 441f
        return 1f - (minDistance / maxDistance).coerceIn(0f, 1f)
    }
    
    companion object {
        private const val TAG = "WallpaperScorer"
    }
}
