package me.avinas.vanderwaals.domain.usecase

import kotlinx.coroutines.flow.first
import me.avinas.vanderwaals.algorithm.EnhancedImageAnalyzer
import me.avinas.vanderwaals.algorithm.SimilarityCalculator
import me.avinas.vanderwaals.data.datastore.SettingsDataStore
import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import me.avinas.vanderwaals.data.repository.WallpaperRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FindSimilarWallpapersUseCase @Inject constructor(
    private val wallpaperRepository: WallpaperRepository,
    private val similarityCalculator: SimilarityCalculator,
    private val settingsDataStore: SettingsDataStore
) {
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
            
            val allWallpapers = wallpaperRepository.getAllWallpapers().first()
            val settings = settingsDataStore.settings.first()
            val enabledSources = mutableListOf<String>()
            if (settings.githubEnabled) enabledSources.add("github")
            if (settings.bingEnabled) enabledSources.add("bing")
            if (settings.vanderwaalsCollectionEnabled) enabledSources.add("vanderwaals")
            
            val filteredWallpapers = if (enabledSources.isEmpty()) {
                allWallpapers
            } else {
                allWallpapers.filter { it.source.lowercase() in enabledSources }
            }
            
            if (filteredWallpapers.isEmpty()) {
                return Result.success(emptyList())
            }
            
            val rankedWallpapers = filteredWallpapers
                .map { wallpaper ->
                    val similarity = when {
                        userAnalysis != null -> {
                            similarityCalculator.calculateEnhancedSimilarity(
                                userEmbedding = userEmbedding,
                                userAnalysis = userAnalysis,
                                userColors = userColors ?: emptyList(),
                                userCategory = userCategory,
                                userBrightness = userBrightness,
                                userContrast = userContrast,
                                wallpaper = wallpaper,
                                wallpaperAnalysis = null
                            )
                        }
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
                .sortedByDescending { it.score }
            
            val finalWallpapers = rankedWallpapers
                .take(limit)
                .map { it.wallpaper }
            
            Result.success(finalWallpapers)
            
        } catch (e: Exception) {
            Result.failure(
                Exception("Failed to find similar wallpapers: ${e.message}", e)
            )
        }
    }
    
    private data class ScoredWallpaper(
        val wallpaper: WallpaperMetadata,
        val score: Float
    )
    
    companion object {
        private const val EXPECTED_EMBEDDING_SIZE = 1280
        private const val DEFAULT_LIMIT = 50
    }
}
