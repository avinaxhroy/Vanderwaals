package me.avinas.vanderwaals.domain.usecase

import kotlinx.coroutines.flow.first
import me.avinas.vanderwaals.algorithm.SimilarityCalculator
import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import me.avinas.vanderwaals.data.repository.PreferenceRepository
import me.avinas.vanderwaals.data.repository.WallpaperRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for getting ranked wallpaper recommendations.
 * 
 * Implements the complete ranking algorithm:
 * 
 * 1. Load user's current preference vector
 * 2. Calculate embedding similarity for all wallpapers
 * 3. Calculate color similarity for all wallpapers
 * 4. Apply category bonus based on feedback history
 * 5. Combine scores: (embedding × 0.7) + (color × 0.2) + (category × 0.1)
 * 6. Apply epsilon-greedy exploration (10% random from top 100)
 * 7. Filter out recently shown wallpapers
 * 8. Return top 50 ranked wallpapers
 * 
 * Used for:
 * - Populating wallpaper rotation queue
 * - Manual wallpaper change requests
 * - Reranking after feedback updates
 * 
 * @see me.avinas.vanderwaals.algorithm.SimilarityCalculator
 * @see me.avinas.vanderwaals.data.repository.WallpaperRepository
 * @see me.avinas.vanderwaals.data.repository.PreferenceRepository
 */
@Singleton
class GetRankedWallpapersUseCase @Inject constructor(
    private val wallpaperRepository: WallpaperRepository,
    private val preferenceRepository: PreferenceRepository,
    private val similarityCalculator: SimilarityCalculator
) {
    
    /**
     * Convert hex color string to RGB integer.
     * @param hex Color string like "#FF5733" or "FF5733"
     * @return RGB color as integer, or null if invalid
     */
    private fun hexToRgb(hex: String): Int? {
        return try {
            val cleanHex = hex.removePrefix("#")
            if (cleanHex.length != 6) return null
            android.graphics.Color.parseColor("#$cleanHex")
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Calculate color similarity between wallpaper and user preferences.
     * Uses RGB distance in color space.
     */
    private fun calculateColorSimilarity(
        wallpaperColors: List<Int>,
        preferredColors: List<Int>
    ): Float {
        if (wallpaperColors.isEmpty() || preferredColors.isEmpty()) {
            return 0.5f // Neutral score if no color data
        }
        
        // Calculate minimum color distance between any pair
        var minDistance = Float.MAX_VALUE
        
        wallpaperColors.take(3).forEach { wColor ->
            preferredColors.take(3).forEach { pColor ->
                val distance = colorDistance(wColor, pColor)
                if (distance < minDistance) {
                    minDistance = distance
                }
            }
        }
        
        // Normalize distance to similarity score (0-1)
        // Max distance in RGB space is sqrt(3 * 255^2) ≈ 441
        val maxDistance = 441f
        return 1f - (minDistance / maxDistance).coerceIn(0f, 1f)
    }
    
    /**
     * Calculate Euclidean distance between two RGB colors.
     */
    private fun colorDistance(color1: Int, color2: Int): Float {
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
     * Gets ranked list of wallpapers (no source filtering - used for catalog browsing).
     * 
     * NOTE: Source filtering is handled in SelectNextWallpaperUseCase.
     * This use case is for browsing all available wallpapers.
     * 
     * @param limit Maximum number of wallpapers to return (default: 50)
     * @return Result containing ranked wallpapers or error
     */
    suspend operator fun invoke(limit: Int = 50): Result<List<WallpaperMetadata>> {
        return try {
            // Get user preferences
            val preferences = preferenceRepository.getUserPreferences().first()
                ?: return Result.failure(Exception("User preferences not initialized"))
            
            // Get all wallpapers from database
            val allWallpapers = wallpaperRepository.getAllWallpapers().first()
            
            if (allWallpapers.isEmpty()) {
                return Result.failure(Exception("No wallpapers available in catalog"))
            }
            
            // Get recently shown wallpapers to filter out
            val recentHistory = wallpaperRepository.getHistory().first().take(10)
            val recentIds = recentHistory.map { it.wallpaperId }.toSet()
            
            // Filter out recently shown
            val candidateWallpapers = allWallpapers.filterNot { it.id in recentIds }
            
            // If all wallpapers were recently shown, use all available wallpapers
            val wallpapersToRank = if (candidateWallpapers.isEmpty()) {
                allWallpapers
            } else {
                candidateWallpapers
            }
            
            // Calculate similarity scores with improved algorithm
            val rankedWallpapers = wallpapersToRank.map { wallpaper ->
                // 1. Embedding similarity (70% weight) - semantic understanding
                val embeddingSimilarity = similarityCalculator.calculateSimilarity(
                    preferences.preferenceVector,
                    wallpaper.embedding
                )
                
                // 2. Color similarity (20% weight) - improved calculation
                // Use color palette from wallpaper metadata
                val wallpaperColors = wallpaper.colors.mapNotNull { hexToRgb(it) }
                
                // Extract colors from user's liked wallpapers
                val preferredColors = preferences.likedWallpaperIds
                    .mapNotNull { id -> allWallpapers.find { it.id == id } }
                    .flatMap { it.colors }
                    .mapNotNull { hexToRgb(it) }
                    .distinct()
                
                val colorSimilarity = if (wallpaperColors.isNotEmpty() && preferredColors.isNotEmpty()) {
                    calculateColorSimilarity(wallpaperColors, preferredColors)
                } else {
                    0.5f  // Neutral score if no color data
                }
                
                // 3. Category bonus (10% weight) - enhanced with feedback decay
                val likedCategories = preferences.likedWallpaperIds
                    .mapNotNull { id -> allWallpapers.find { it.id == id }?.category }
                    .groupingBy { it }
                    .eachCount()
                
                // Get top 3 favorite categories instead of just 1
                val topCategories = likedCategories.entries
                    .sortedByDescending { it.value }
                    .take(3)
                    .map { it.key }
                
                val categoryBonus = when {
                    wallpaper.category in topCategories.take(1) -> 0.3f  // Top category
                    wallpaper.category in topCategories.take(2) -> 0.2f  // 2nd category
                    wallpaper.category in topCategories -> 0.1f          // 3rd category
                    else -> 0.0f
                }
                
                // 4. Dislike penalty - reduce score for disliked wallpapers' categories
                val dislikedCategories = preferences.dislikedWallpaperIds
                    .mapNotNull { id -> allWallpapers.find { it.id == id }?.category }
                    .toSet()
                
                val dislikePenalty = if (wallpaper.category in dislikedCategories) {
                    -0.2f
                } else {
                    0.0f
                }
                
                // 5. Brightness variation bonus - prefer variety
                // This helps avoid showing too many similar brightness levels
                val brightnessVariationBonus = 0.02f * (1f - kotlin.math.abs(wallpaper.brightness - 50) / 50f)
                
                // Combined score with improved weights
                val finalScore = (embeddingSimilarity * 0.7f) + 
                               (colorSimilarity * 0.2f) + 
                               (categoryBonus * 0.1f) +
                               dislikePenalty +
                               brightnessVariationBonus
                
                wallpaper to finalScore
            }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
            
            Result.success(rankedWallpapers)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
