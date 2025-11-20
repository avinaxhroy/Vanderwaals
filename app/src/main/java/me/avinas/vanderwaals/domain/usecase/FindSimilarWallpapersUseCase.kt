package me.avinas.vanderwaals.domain.usecase

import kotlinx.coroutines.flow.first
import me.avinas.vanderwaals.algorithm.EnhancedImageAnalyzer
import me.avinas.vanderwaals.algorithm.SimilarityCalculator
import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import me.avinas.vanderwaals.data.repository.WallpaperRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for finding wallpapers similar to a given embedding vector.
 * 
 * This use case implements the core recommendation algorithm:
 * 1. Retrieve all wallpapers from the local database
 * 2. Calculate cosine similarity between user embedding and each wallpaper
 * 3. Apply composite scoring (embedding 70%, color 20%, category 10%)
 * 4. Return top N matches sorted by similarity
 * 
 * **Usage Scenarios:**
 * - Initial onboarding: Find matches for user's uploaded wallpaper
 * - After feedback: Re-rank wallpapers based on updated preferences
 * - Manual refresh: User requests new recommendations
 * 
 * **Performance:**
 * - Calculating 6000 similarities: ~50ms on modern devices
 * - Uses efficient cosine similarity (dot product + normalization)
 * - Pre-computed embeddings stored in database
 * 
 * **Algorithm:**
 * ```
 * For each wallpaper:
 *   embedding_score = cosineSimilarity(userEmbedding, wallpaperEmbedding) [0.7 weight]
 *   color_score = colorSimilarity(userColors, wallpaperColors)           [0.2 weight]
 *   category_bonus = (same_category ? 0.05 : 0)                          [0.1 weight]
 *   
 *   final_score = (embedding_score × 0.7) + (color_score × 0.2) + (category_bonus × 0.1)
 * 
 * Sort by final_score descending
 * Return top N
 * ```
 * 
 * @property wallpaperRepository Repository for accessing wallpaper metadata
 * @property similarityCalculator Utility for computing similarity scores
 * 
 * @see ExtractEmbeddingUseCase
 * @see UpdatePreferencesUseCase
 * @see SelectNextWallpaperUseCase
 */
@Singleton
class FindSimilarWallpapersUseCase @Inject constructor(
    private val wallpaperRepository: WallpaperRepository,
    private val similarityCalculator: SimilarityCalculator
) {
    /**
     * Finds the most similar wallpapers to a given embedding vector.
     * 
     * **Thread Safety:**
     * This operation performs database queries and CPU-intensive similarity
     * calculations. Should be called from a background coroutine (IO dispatcher).
     * 
     * **Limit Parameter:**
     * Default is 50 wallpapers (optimized for download queue size).
     * Can be adjusted for different use cases:
     * - Onboarding preview: 8-12 wallpapers
     * - Download queue: 50 wallpapers
     * - Full re-ranking: 100+ wallpapers
     * 
     * **Empty Results:**
     * If no wallpapers are found (empty database), returns empty list.
     * Caller should handle this case (e.g., trigger manifest sync).
     * 
     * **ENHANCED MATCHING:**
     * Pass userAnalysis for superior semantic matching that captures image essence.
     * 
     * @param userEmbedding 576-dimensional embedding vector representing user preference
     * @param limit Maximum number of matches to return (default: 50)
     * @param userAnalysis Enhanced image analysis of uploaded image (optional, recommended)
     * @param userColors Optional user color palette for composite scoring
     * @param userCategory Optional user category preference
     * @param userBrightness Optional user brightness preference (0-100)
     * @param userContrast Optional user contrast preference (0-100)
     * @param useCompositeSimilarity If true, uses composite scoring (embedding + color + category)
     * @return Result<List<WallpaperMetadata>> containing top matches on success,
     *         or error description on failure
     * 
     * @throws None - All exceptions are caught and returned as Result.failure
     * 
     * Example:
     * ```kotlin
     * viewModelScope.launch {
     *     // After user uploads wallpaper
     *     val embedding = extractEmbeddingUseCase(uri).getOrNull() ?: return@launch
     *     
     *     // RECOMMENDED: Enhanced matching with image analysis
     *     val analysis = enhancedImageAnalyzer.analyze(bitmap)
     *     val result = findSimilarWallpapersUseCase(
     *         userEmbedding = embedding,
     *         userAnalysis = analysis,  // Captures essence of image
     *         limit = 12
     *     )
     *     
     *     result.fold(
     *         onSuccess = { wallpapers ->
     *             // Show preview gallery with better matches
     *             displayMatches(wallpapers)
     *         },
     *         onFailure = { error ->
     *             showError("Failed to find matches: ${error.message}")
     *         }
     *     )
     * }
     * ```
     */
    suspend operator fun invoke(
        userEmbedding: FloatArray,
        limit: Int = DEFAULT_LIMIT,
        userAnalysis: EnhancedImageAnalyzer.ImageAnalysis? = null,
        userColors: List<String>? = null,
        userCategory: String? = null,
        userBrightness: Int = 50,
        userContrast: Int = 50,
        useCompositeSimilarity: Boolean = false
    ): Result<List<WallpaperMetadata>> {
        return try {
            // Validate input
            if (userEmbedding.size != EXPECTED_EMBEDDING_SIZE) {
                return Result.failure(
                    IllegalArgumentException(
                        "Invalid embedding size: expected $EXPECTED_EMBEDDING_SIZE, got ${userEmbedding.size}"
                    )
                )
            }
            
            if (limit < 1) {
                return Result.failure(
                    IllegalArgumentException("Limit must be positive, got: $limit")
                )
            }
            
            // Step 1: Get all wallpapers from database
            // Note: This is a suspend function that needs to be called from a coroutine
            // The repository returns Flow, so we use .first() to get current value
            val allWallpapers = wallpaperRepository.getAllWallpapers().first()
            
            // Step 2: Handle empty database
            if (allWallpapers.isEmpty()) {
                return Result.success(emptyList())
            }
            
            // Step 3: Calculate similarity scores for all wallpapers
            android.util.Log.d("FindSimilarWallpapers", "Comparing against ${allWallpapers.size} wallpapers in database")
            
            // LOG: CRITICAL - Check if database embeddings are diverse or all similar
            if (allWallpapers.isNotEmpty()) {
                val sample = allWallpapers.take(10)
                android.util.Log.d("FindSimilarWallpapers", "=== DATABASE EMBEDDING DIVERSITY CHECK ===")
                sample.forEachIndexed { index, wallpaper ->
                    val embPreview = wallpaper.embedding.take(5).joinToString(", ", "[", ", ...]")
                    val magnitude = kotlin.math.sqrt(wallpaper.embedding.map { it * it }.sum())
                    val stats = "min=${wallpaper.embedding.minOrNull()}, max=${wallpaper.embedding.maxOrNull()}, avg=${wallpaper.embedding.average()}"
                    android.util.Log.d("FindSimilarWallpapers", "DB[$index] ID:${wallpaper.id.take(20)} cat:${wallpaper.category}")
                    android.util.Log.d("FindSimilarWallpapers", "  Embedding: $embPreview, mag=${"%.2f".format(magnitude)}, $stats")
                }
                
                // Check if embeddings are too similar (sign of corruption)
                val firstEmb = allWallpapers[0].embedding
                val secondEmb = allWallpapers[1].embedding
                val similarity = similarityCalculator.calculateSimilarity(firstEmb, secondEmb)
                android.util.Log.d("FindSimilarWallpapers", "Similarity between first 2 wallpapers: $similarity")
                android.util.Log.d("FindSimilarWallpapers", "=== END DIVERSITY CHECK ===")
            }
            
            val rankedWallpapers = allWallpapers
                .map { wallpaper ->
                    val similarity = when {
                        // BEST: Enhanced similarity with image analysis (captures essence)
                        userAnalysis != null -> {
                            // Note: wallpaperAnalysis would need to be pre-computed and stored
                            // For now, we use enhanced similarity without wallpaper analysis
                            similarityCalculator.calculateEnhancedSimilarity(
                                userEmbedding = userEmbedding,
                                userAnalysis = userAnalysis,
                                userColors = userColors ?: emptyList(),
                                userCategory = userCategory,
                                userBrightness = userBrightness,
                                userContrast = userContrast,
                                wallpaper = wallpaper,
                                wallpaperAnalysis = null // TODO: Pre-compute and cache
                            )
                        }
                        // GOOD: Composite scoring with color and category
                        useCompositeSimilarity && userColors != null -> {
                            similarityCalculator.calculateCompositeSimilarity(
                                userEmbedding = userEmbedding,
                                userColors = userColors,
                                userCategory = userCategory,
                                userBrightness = userBrightness,
                                userContrast = userContrast,
                                wallpaper = wallpaper
                            )
                        }
                        // BASIC: Simple embedding similarity
                        else -> {
                            similarityCalculator.calculateSimilarity(
                                userEmbedding,
                                wallpaper.embedding
                            )
                        }
                    }
                    
                    ScoredWallpaper(
                        wallpaper = wallpaper,
                        score = similarity
                    )
                }
                // Step 4: Sort by similarity (descending)
                .sortedByDescending { it.score }
            
            // LOG: Debug similarity scores before taking top N
            if (rankedWallpapers.isNotEmpty()) {
                val top5 = rankedWallpapers.take(5)
                android.util.Log.d("FindSimilarWallpapers", "Top 5 matches before limit:")
                top5.forEachIndexed { index, scored ->
                    android.util.Log.d("FindSimilarWallpapers", "  ${index + 1}. ID:${scored.wallpaper.id.take(20)}... (score: ${scored.score})")
                }
                
                val allScores = rankedWallpapers.map { it.score }
                android.util.Log.d("FindSimilarWallpapers", "Score range: ${allScores.minOrNull()} to ${allScores.maxOrNull()}")
                android.util.Log.d("FindSimilarWallpapers", "Score average: ${allScores.average()}")
            }
            
            val finalWallpapers = rankedWallpapers
                // Step 5: Take top N matches
                .take(limit)
                // Step 6: Extract wallpaper metadata
                .map { it.wallpaper }
            
            Result.success(finalWallpapers)
            
        } catch (e: Exception) {
            Result.failure(
                Exception("Failed to find similar wallpapers: ${e.message}", e)
            )
        }
    }
    
    /**
     * Internal data class for pairing wallpapers with their similarity scores.
     * Used during sorting before returning final results.
     */
    private data class ScoredWallpaper(
        val wallpaper: WallpaperMetadata,
        val score: Float
    )
    
    companion object {
        /**
         * Expected embedding dimension for MobileNetV3-Small model.
         */
        private const val EXPECTED_EMBEDDING_SIZE = 576
        
        /**
         * Default number of similar wallpapers to return.
         * Optimized for download queue management (top 50).
         */
        private const val DEFAULT_LIMIT = 50
    }
}
