package me.avinas.vanderwaals.algorithm

import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Advanced exploration strategies for wallpaper selection.
 * 
 * Implements multiple exploration algorithms beyond simple epsilon-greedy:
 * - Upper Confidence Bound (UCB): Balances exploitation and exploration mathematically
 * - Thompson Sampling: Probabilistic approach to exploration
 * - Diversity-aware exploration: Ensures variety in categories and styles
 * 
 * **Why Better Than Epsilon-Greedy:**
 * - UCB systematically explores less-tried options
 * - Reduces wasted exploration on poor matches
 * - Converges faster to optimal selection
 * - Provides confidence bounds on wallpaper quality
 * 
 * @see SelectNextWallpaperUseCase
 */
class ExplorationStrategies {
    
    companion object {
        /**
         * Exploration constant for UCB algorithm.
         * Higher values = more exploration, lower = more exploitation.
         * Typical range: 0.5 to 2.0
         */
        private const val UCB_EXPLORATION_CONSTANT = 1.4f
        
        /**
         * Minimum views before UCB confidence bound is meaningful.
         * Wallpapers with fewer views get bonus to ensure initial exploration.
         */
        private const val MIN_VIEWS_FOR_UCB = 2
    }
    
    /**
     * Data class tracking wallpaper selection statistics.
     * Used for UCB and Thompson Sampling algorithms.
     * 
     * @property wallpaperId Unique wallpaper identifier
     * @property timesShown Number of times this wallpaper was shown
     * @property positiveReactions Number of likes/positive implicit feedback
     * @property negativeReactions Number of dislikes/negative implicit feedback
     * @property avgSimilarityScore Average similarity score from personalization algorithm
     */
    data class WallpaperStats(
        val wallpaperId: String,
        val timesShown: Int = 0,
        val positiveReactions: Int = 0,
        val negativeReactions: Int = 0,
        val avgSimilarityScore: Float = 0f
    ) {
        /**
         * Calculates empirical success rate (positive feedback ratio).
         * Used as exploitation component in UCB.
         */
        fun successRate(): Float {
            val totalReactions = positiveReactions + negativeReactions
            if (totalReactions == 0) return 0.5f // Neutral prior
            return positiveReactions.toFloat() / totalReactions
        }
        
        /**
         * Checks if wallpaper has enough data for reliable statistics.
         */
        fun hasEnoughData(): Boolean = timesShown >= MIN_VIEWS_FOR_UCB
    }
    
    /**
     * Selects wallpaper using Upper Confidence Bound (UCB1) algorithm.
     * 
     * **UCB Formula:**
     * ```
     * UCB_score = exploitation_score + exploration_bonus
     * 
     * exploitation_score = success_rate (or similarity_score)
     * exploration_bonus = c × sqrt(ln(total_selections) / times_shown)
     * 
     * where c = exploration constant (typically 1.4)
     * ```
     * 
     * **How It Works:**
     * - Wallpapers shown less often get higher exploration bonus
     * - Wallpapers with better feedback get higher exploitation score
     * - Automatically balances trying new content vs showing known good content
     * - Mathematically proven to converge to optimal selection
     * 
     * **Advantages Over Epsilon-Greedy:**
     * - No random chance waste (always picks best UCB score)
     * - Systematically explores undersampled options
     * - Adapts exploration rate automatically based on uncertainty
     * - Better long-term performance
     * 
     * @param candidates List of wallpapers with similarity scores
     * @param stats Map of wallpaper statistics (shows, feedback)
     * @param totalSelections Total number of wallpapers shown to user
     * @return Selected wallpaper with highest UCB score
     */
    fun selectWithUCB(
        candidates: List<Pair<WallpaperMetadata, Float>>, // (wallpaper, similarity_score)
        stats: Map<String, WallpaperStats>,
        totalSelections: Int
    ): WallpaperMetadata {
        if (candidates.isEmpty()) {
            throw IllegalArgumentException("Cannot select from empty candidate list")
        }
        
        // Calculate UCB score for each candidate
        val scoredCandidates = candidates.map { (wallpaper, similarityScore) ->
            val wallpaperStats = stats[wallpaper.id]
            val ucbScore = calculateUCBScore(
                similarityScore = similarityScore,
                stats = wallpaperStats,
                totalSelections = totalSelections
            )
            
            Triple(wallpaper, similarityScore, ucbScore)
        }
        
        // Select wallpaper with highest UCB score
        return scoredCandidates.maxByOrNull { it.third }?.first
            ?: candidates.first().first
    }
    
    /**
     * Calculates UCB score for a single wallpaper.
     * 
     * @param similarityScore Personalization similarity score (0.0-1.0)
     * @param stats Wallpaper selection statistics (nullable for new wallpapers)
     * @param totalSelections Total selections across all wallpapers
     * @return UCB score (unbounded, higher = better)
     */
    private fun calculateUCBScore(
        similarityScore: Float,
        stats: WallpaperStats?,
        totalSelections: Int
    ): Float {
        // Exploitation component: use similarity score as base quality
        val exploitationScore = similarityScore
        
        // If no stats, give maximum exploration bonus (new wallpaper)
        if (stats == null || stats.timesShown == 0) {
            return exploitationScore + 10f // Large bonus for untried wallpapers
        }
        
        // Exploration component: confidence bound based on uncertainty
        val explorationBonus = UCB_EXPLORATION_CONSTANT * sqrt(
            ln(totalSelections.toFloat()) / stats.timesShown.toFloat()
        )
        
        // Optional: Adjust exploitation based on feedback if available
        val feedbackAdjustment = if (stats.hasEnoughData()) {
            // Blend similarity score with empirical success rate (70%-30%)
            0.7f * similarityScore + 0.3f * stats.successRate()
        } else {
            similarityScore
        }
        
        return feedbackAdjustment + explorationBonus
    }
    
    /**
     * Selects wallpaper using diversity-aware strategy.
     * Ensures variety in categories, colors, and styles over time.
     * 
     * **Diversity Dimensions:**
     * - Category: Avoid repeating same category
     * - Color palette: Try different color schemes
     * - Brightness: Vary between light and dark
     * - Source: Balance between GitHub and Bing
     * - Embedding: Aesthetic diversity in 1280D space (MobileNetV4)
     * 
     * @param candidates List of wallpapers with scores
     * @param recentWallpapers Recently shown wallpapers (for diversity check)
     * @param diversityWeight How much to weight diversity vs quality (0.0-1.0)
     * @return Selected wallpaper balancing quality and diversity
     */
    fun selectWithDiversity(
        candidates: List<Pair<WallpaperMetadata, Float>>,
        recentWallpapers: List<WallpaperMetadata>,
        diversityWeight: Float = 0.3f
    ): WallpaperMetadata {
        if (candidates.isEmpty()) {
            throw IllegalArgumentException("Cannot select from empty candidate list")
        }
        
        // Extract diversity features from recent wallpapers
        val recentCategories = recentWallpapers.map { it.category }.toSet()
        val recentSources = recentWallpapers.map { it.source }.toSet()
        val avgRecentBrightness = recentWallpapers
            .map { it.brightness }
            .average()
            .toFloat()
        val recentEmbeddings = recentWallpapers.map { it.embedding }
        
        // Score each candidate for diversity
        val diversityScored = candidates.map { (wallpaper, qualityScore) ->
            val diversityScore = calculateDiversityScore(
                wallpaper = wallpaper,
                recentCategories = recentCategories,
                recentSources = recentSources,
                avgRecentBrightness = avgRecentBrightness,
                recentEmbeddings = recentEmbeddings
            )
            
            // Combine quality and diversity
            val finalScore = (1f - diversityWeight) * qualityScore + 
                            diversityWeight * diversityScore
            
            Pair(wallpaper, finalScore)
        }
        
        // Select highest combined score
        return diversityScored.maxByOrNull { it.second }?.first
            ?: candidates.first().first
    }
    
    /**
     * Calculates diversity score for a wallpaper.
     * Higher score = more different from recent selections.
     * 
     * ENHANCED (MobileNetV4): Now includes embedding-space diversity
     * to detect aesthetic differences beyond just category/brightness.
     * 
     * @param wallpaper Candidate wallpaper
     * @param recentCategories Categories shown recently
     * @param recentSources Sources used recently
     * @param avgRecentBrightness Average brightness of recent wallpapers
     * @param recentEmbeddings Embeddings of recent wallpapers (for aesthetic diversity)
     * @return Diversity score (0.0-1.0)
     */
    private fun calculateDiversityScore(
        wallpaper: WallpaperMetadata,
        recentCategories: Set<String>,
        recentSources: Set<String>,
        avgRecentBrightness: Float,
        recentEmbeddings: List<FloatArray> = emptyList()
    ): Float {
        var score = 0f
        
        // Category diversity (30% weight) - reduced from 40%
        if (wallpaper.category !in recentCategories) {
            score += 0.30f
        }
        
        // Source diversity (15% weight) - reduced from 20%
        if (wallpaper.source !in recentSources) {
            score += 0.15f
        }
        
        // Brightness diversity (35% weight) - reduced from 40%
        val brightnessDiff = kotlin.math.abs(wallpaper.brightness - avgRecentBrightness)
        val brightnessDiversity = (brightnessDiff / 100f).coerceIn(0f, 1f)
        score += 0.35f * brightnessDiversity
        
        // Embedding diversity (20% weight) - NEW for MobileNetV4
        // Higher when wallpaper is aesthetically different from recent ones
        if (recentEmbeddings.isNotEmpty() && wallpaper.embedding.isNotEmpty()) {
            val embeddingDiversity = calculateEmbeddingDiversity(
                candidate = wallpaper.embedding,
                recentEmbeddings = recentEmbeddings
            )
            score += 0.20f * embeddingDiversity
        } else {
            // If no embedding data, give neutral score for this component
            score += 0.10f
        }
        
        return score.coerceIn(0f, 1f)
    }
    
    /**
     * Calculates embedding-space diversity for MobileNetV4 1280D vectors.
     * 
     * Measures how aesthetically different the candidate is from recent wallpapers
     * by computing average cosine distance in the embedding space.
     * 
     * @param candidate Candidate embedding vector (1280D)
     * @param recentEmbeddings Recent wallpaper embeddings
     * @return Diversity score (0.0-1.0), higher = more different
     */
    private fun calculateEmbeddingDiversity(
        candidate: FloatArray,
        recentEmbeddings: List<FloatArray>
    ): Float {
        if (recentEmbeddings.isEmpty() || candidate.isEmpty()) return 0.5f
        
        // Calculate average cosine distance from recent embeddings
        // Distance = 1 - similarity, so higher distance = more diverse
        var totalDistance = 0f
        var validCount = 0
        
        for (recent in recentEmbeddings) {
            if (recent.isEmpty()) continue
            
            val similarity = cosineSimilarity(candidate, recent)
            val distance = 1f - similarity
            totalDistance += distance
            validCount++
        }
        
        if (validCount == 0) return 0.5f
        
        // Average distance, normalized to 0-1
        // Most embeddings have similarity 0.5-0.9, so distance 0.1-0.5
        // Scale to make it more discriminative
        val avgDistance = totalDistance / validCount
        return (avgDistance * 2f).coerceIn(0f, 1f)
    }
    
    /**
     * Computes cosine similarity between two embedding vectors.
     */
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        
        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator > 0f) dotProduct / denominator else 0f
    }
}
