package me.avinas.vanderwaals.domain.usecase

import kotlinx.coroutines.flow.first
import me.avinas.vanderwaals.algorithm.EnhancedImageAnalyzer
import me.avinas.vanderwaals.algorithm.SimilarityCalculator
import me.avinas.vanderwaals.data.datastore.SettingsDataStore
import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import me.avinas.vanderwaals.data.repository.WallpaperRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finds wallpapers similar to a given embedding vector.
 *
 * Uses composite scoring: embedding similarity (70%), color matching (20%),
 * category affinity (10%). Returns top N matches sorted by score.
 */
@Singleton
class FindSimilarWallpapersUseCase @Inject constructor(
    private val wallpaperRepository: WallpaperRepository,
    private val similarityCalculator: SimilarityCalculator,
    private val settingsDataStore: SettingsDataStore
) {
    /**
     * Finds the most similar wallpapers to a given embedding vector.
     * Call from a background coroutine (IO dispatcher).
     *
     * @param userEmbedding 1280-dimensional embedding vector
     * @param limit Maximum matches to return (default: 50)
     * @param userAnalysis Optional enhanced image analysis for better matching
     * @param userColors Optional color palette for composite scoring
     * @param userCategory Optional category preference
     * @param useCompositeSimilarity Use composite scoring (default: true)
     * @return Result with top matches or error
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
            
            // Step 1b: Filter by enabled sources (github/bing user settings)
            val settings = settingsDataStore.settings.first()
            val enabledSources = mutableListOf<String>()
            if (settings.githubEnabled) enabledSources.add("github")
            if (settings.bingEnabled) enabledSources.add("bing")
            if (settings.vanderwaalsCollectionEnabled) enabledSources.add("vanderwaals")
            
            val filteredWallpapers = if (enabledSources.isEmpty()) {
                // If no sources enabled, fall back to all wallpapers
                allWallpapers
            } else {
                allWallpapers.filter { it.source.lowercase() in enabledSources }
            }
            
            // Step 2: Handle empty database
            if (filteredWallpapers.isEmpty()) {
                return Result.success(emptyList())
            }
            
            // Step 3: Calculate similarity scores for all wallpapers
            android.util.Log.d("FindSimilarWallpapers", "Comparing against ${filteredWallpapers.size} wallpapers (filtered from ${allWallpapers.size} total, sources: $enabledSources)")
            
            // LOG: CRITICAL - Check if database embeddings are diverse or all similar
            if (filteredWallpapers.isNotEmpty()) {
                val sample = filteredWallpapers.take(10)
                android.util.Log.d("FindSimilarWallpapers", "=== DATABASE EMBEDDING DIVERSITY CHECK ===")
                sample.forEachIndexed { index, wallpaper ->
                    val embPreview = wallpaper.embedding.take(5).joinToString(", ", "[", ", ...]")
                    val magnitude = kotlin.math.sqrt(wallpaper.embedding.map { it * it }.sum())
                    val stats = "min=${wallpaper.embedding.minOrNull()}, max=${wallpaper.embedding.maxOrNull()}, avg=${wallpaper.embedding.average()}"
                    android.util.Log.d("FindSimilarWallpapers", "DB[$index] ID:${wallpaper.id.take(20)} cat:${wallpaper.category} src:${wallpaper.source}")
                    android.util.Log.d("FindSimilarWallpapers", "  Embedding: $embPreview, mag=${"%.2f".format(magnitude)}, $stats")
                }
                
                // Check if embeddings are too similar (sign of corruption)
                val firstEmb = filteredWallpapers[0].embedding
                val secondEmb = filteredWallpapers[1].embedding
                val similarity = similarityCalculator.calculateSimilarity(firstEmb, secondEmb)
                android.util.Log.d("FindSimilarWallpapers", "Similarity between first 2 wallpapers: $similarity")
                android.util.Log.d("FindSimilarWallpapers", "=== END DIVERSITY CHECK ===")
            }
            
            val rankedWallpapers = filteredWallpapers
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
         * Expected embedding dimension for MobileNetV4-Conv-Small model.
         */
        private const val EXPECTED_EMBEDDING_SIZE = 1280
        
        /**
         * Default number of similar wallpapers to return.
         * Optimized for download queue management (top 50).
         */
        private const val DEFAULT_LIMIT = 50
    }
}
