package me.avinas.vanderwaals.domain.usecase

import kotlinx.coroutines.flow.first
import me.avinas.vanderwaals.algorithm.SimilarityCalculator
import me.avinas.vanderwaals.algorithm.WallpaperScorer
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
 * 3. Calculate color similarity (CIE76 ΔE in LAB space) for all wallpapers
 * 4. Apply category bonus based on feedback history
 * 5. Combine scores using centralized weights (see RecommendationWeights)
 * 6. Apply dislike penalty and brightness variation bonus
 * 7. Filter out recently shown wallpapers
 * 8. Return top N ranked wallpapers
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
    private val similarityCalculator: SimilarityCalculator,
    private val wallpaperScorer: WallpaperScorer
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
     * Uses perceptual CIE76 ΔE in LAB colour space (via shared ColorSpace).
     */
    private fun calculateColorSimilarity(
        wallpaperColors: List<Int>,
        preferredColors: List<Int>
    ): Float {
        if (wallpaperColors.isEmpty() || preferredColors.isEmpty()) {
            return 0.5f // Neutral score if no color data
        }

        // Calculate minimum colour distance between any pair
        var minDeltaE = Double.MAX_VALUE

        wallpaperColors.take(3).forEach { wColor ->
            preferredColors.take(3).forEach { pColor ->
                val wr = (wColor shr 16) and 0xFF
                val wg = (wColor shr 8) and 0xFF
                val wb = wColor and 0xFF
                val pr = (pColor shr 16) and 0xFF
                val pg = (pColor shr 8) and 0xFF
                val pb = pColor and 0xFF
                val deltaE = me.avinas.vanderwaals.core.ColorSpace.rgbDeltaE(wr, wg, wb, pr, pg, pb)
                if (deltaE < minDeltaE) {
                    minDeltaE = deltaE
                }
            }
        }

        // Normalize ΔE to similarity score (0-1). Max ΔE ≈ 100 (black ↔ white).
        return (1f - (minDeltaE / 100.0).coerceIn(0.0, 1.0)).toFloat()
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
            
            // Pre-compute liked/disliked wallpaper data once to avoid O(n²)
            // lookups inside the ranking loop (was: allWallpapers.find{} per
            // liked/disliked ID per candidate wallpaper).
            val wallpaperById = allWallpapers.associateBy { it.id }
            val preferredColors = preferences.likedWallpaperIds
                .mapNotNull { wallpaperById[it] }
                .flatMap { it.colors }
                .mapNotNull { hexToRgb(it) }
                .distinct()
            val topCategories = preferences.likedWallpaperIds
                .mapNotNull { wallpaperById[it]?.category }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(3)
                .map { it.key }
            val dislikedCategories = preferences.dislikedWallpaperIds
                .mapNotNull { wallpaperById[it]?.category }
                .toSet()

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
                
                val colorSimilarity = if (wallpaperColors.isNotEmpty() && preferredColors.isNotEmpty()) {
                    calculateColorSimilarity(wallpaperColors, preferredColors)
                } else {
                    0.5f  // Neutral score if no color data
                }
                
                // 3. Category bonus (10% weight) - enhanced with feedback decay
                val categoryBonus = when {
                    wallpaper.category in topCategories.take(1) -> 0.3f  // Top category
                    wallpaper.category in topCategories.take(2) -> 0.2f  // 2nd category
                    wallpaper.category in topCategories -> 0.1f          // 3rd category
                    else -> 0.0f
                }
                
                // 4. Semantic boost — mood/style tag affinity (Vanderwaals Collection)
                val semanticBoost = wallpaperScorer.getSemanticBoost(
                    wallpaper = wallpaper,
                    moodAffinity = preferences.moodAffinity,
                    styleAffinity = preferences.styleAffinity
                )

                // 5. Dislike penalty - reduce score for disliked wallpapers' categories
                val dislikePenalty = if (wallpaper.category in dislikedCategories) {
                    -0.2f
                } else {
                    0.0f
                }

                // 6. Brightness variation bonus - prefer variety
                // This helps avoid showing too many similar brightness levels
                val brightnessVariationBonus = 0.02f * (1f - kotlin.math.abs(wallpaper.brightness - 50) / 50f)
                
                // Combined score using centralized weights (see RecommendationWeights).
                // Renormalised by STANDARD_WEIGHTS_SUM so a perfect match scores 1.0.
                val finalScore = ((embeddingSimilarity * me.avinas.vanderwaals.algorithm.RecommendationWeights.EMBEDDING_WEIGHT) +
                               (colorSimilarity * me.avinas.vanderwaals.algorithm.RecommendationWeights.COLOR_WEIGHT) +
                               (categoryBonus * me.avinas.vanderwaals.algorithm.RecommendationWeights.CATEGORY_WEIGHT)) /
                               me.avinas.vanderwaals.algorithm.RecommendationWeights.STANDARD_WEIGHTS_SUM +
                               dislikePenalty +
                               brightnessVariationBonus +
                               semanticBoost
                
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
