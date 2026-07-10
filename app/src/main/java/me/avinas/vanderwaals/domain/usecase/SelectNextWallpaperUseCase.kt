package me.avinas.vanderwaals.domain.usecase

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import me.avinas.vanderwaals.algorithm.SimilarityCalculator
import me.avinas.vanderwaals.data.dao.WallpaperHistoryDao
import me.avinas.vanderwaals.data.entity.UserPreferences
import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import me.avinas.vanderwaals.data.repository.CategoryPreferenceRepository
import me.avinas.vanderwaals.data.repository.PreferenceRepository
import me.avinas.vanderwaals.data.repository.WallpaperRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Selects the next wallpaper to display based on learned preferences.
 *
 * Both Auto and Personalize modes use the same learning algorithm.
 * The only difference is initialization:
 * - Personalize: starts with a preference vector from an uploaded image
 * - Auto: starts with no vector; first like creates it
 *
 * Selection flow:
 * 1. If preference vector exists → score by similarity
 *    If not → show diverse wallpapers (cold start)
 * 2. Filter out recently shown wallpapers
 * 3. Apply YouTube-like selection (exploration + exploitation)
 */
@Singleton
class SelectNextWallpaperUseCase @Inject constructor(
    private val wallpaperRepository: WallpaperRepository,
    private val preferenceRepository: PreferenceRepository,
    private val categoryPreferenceRepository: CategoryPreferenceRepository,
    private val colorPreferenceRepository: me.avinas.vanderwaals.data.repository.ColorPreferenceRepository,
    private val compositionPreferenceRepository: me.avinas.vanderwaals.data.repository.CompositionPreferenceRepository,
    private val similarityCalculator: SimilarityCalculator,
    private val wallpaperScorer: me.avinas.vanderwaals.algorithm.WallpaperScorer,
    private val settingsDataStore: me.avinas.vanderwaals.data.datastore.SettingsDataStore,
    private val dailyPlaylistManager: me.avinas.vanderwaals.data.repository.DailyPlaylistManager,
    private val wallpaperHistoryDao: WallpaperHistoryDao,
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) {
    
    // YouTube-like recommender for more engaging, diverse recommendations
    private val youtubeLikeRecommender = me.avinas.vanderwaals.algorithm.YouTubeLikeRecommender()
    
    /**
     * Seeded random for true randomness per invocation.
     * Combines device ID, timestamp, process uptime, and system noise.
     */
    private fun createSeededRandom(): Random {
        val deviceSeed = getDeviceSpecificSeed()
        val timeSeed = System.currentTimeMillis()
        val uptimeSeed = android.os.SystemClock.uptimeMillis()
        val noiseSeed = (Math.random() * Int.MAX_VALUE).toLong()
        
        // Combine all entropy sources with different bit operations for maximum randomness
        val combinedSeed = (deviceSeed.toLong() xor timeSeed xor uptimeSeed xor noiseSeed).toInt()
        if (me.avinas.vanderwaals.BuildConfig.DEBUG) android.util.Log.d("SelectNextWallpaper","Created seeded Random with combined seed: $combinedSeed (device=$deviceSeed, time=$timeSeed, uptime=$uptimeSeed, noise=$noiseSeed)")
        return Random(combinedSeed)
    }
    
    /**
     * Selects the next wallpaper to display using epsilon-greedy strategy.
     * 
     * **Thread Safety:**
     * This operation performs database queries and calculations. Should be called
     * from a background coroutine (IO dispatcher).
     * 
     * **Return Value:**
     * - Success: Returns the selected wallpaper
     * - Failure: Returns error if no wallpapers available or preferences not initialized
     * 
     * **Typical Flow:**
     * 1. Called by WallpaperChangeWorker on schedule (hourly/daily)
     * 2. Called by "Change Now" button in UI
     * 3. Called after initial onboarding
     * 
     * @return Result<WallpaperMetadata> containing selected wallpaper on success,
     *         or error description on failure
     * 
     * @throws None - All exceptions are caught and returned as Result.failure
     * 
     * Example:
     * ```kotlin
     * class WallpaperChangeWorker : CoroutineWorker() {
     *     override suspend fun doWork(): Result {
     *         val result = selectNextWallpaperUseCase()
     *         result.fold(
     *             onSuccess = { wallpaper ->
     *                 applyWallpaper(wallpaper)
     *                 recordHistory(wallpaper)
     *                 Result.success()
     *             },
     *             onFailure = { error ->
     *                 Log.e(TAG, "Failed to select wallpaper: ${error.message}")
     *                 Result.retry()
     *             }
     *         )
     *     }
     * }
     * ```
     */
    suspend operator fun invoke(excludeWallpaperId: String? = null): Result<WallpaperMetadata> {
        return try {
            // Always exclude currently active wallpaper to prevent re-selection
            val currentActiveWallpaper = wallpaperHistoryDao.getActiveWallpaper()
            val effectiveExcludeId = excludeWallpaperId ?: currentActiveWallpaper?.wallpaperId
            
            if (effectiveExcludeId != null) {
                if (me.avinas.vanderwaals.BuildConfig.DEBUG) android.util.Log.d("SelectNextWallpaper","Excluding currently active wallpaper: $effectiveExcludeId")
            }
            
            // Get user preferences, retry a few times if stale
            var preferences = preferenceRepository.getUserPreferencesOnce()
            var retryCount = 0
            
            // Retry if preferences are null OR if they have default/stale values
            while ((preferences == null || preferences.feedbackCount == 0) && retryCount < 5) {
                if (me.avinas.vanderwaals.BuildConfig.DEBUG) android.util.Log.d("SelectNextWallpaper","Preferences stale/null (feedbackCount=${preferences?.feedbackCount}) on attempt ${retryCount + 1}, retrying after delay...")
                delay(300L)  // Longer delay for database sync
                preferences = preferenceRepository.getUserPreferencesOnce()
                retryCount++
                if (me.avinas.vanderwaals.BuildConfig.DEBUG) android.util.Log.d("SelectNextWallpaper","Retry $retryCount result: feedbackCount=${preferences?.feedbackCount}")
            }
            
            if (preferences == null) {
                // Auto-create default preferences if not initialized
                // This handles race conditions in onboarding flow
                val defaultPreferences = UserPreferences.createDefault()
                preferenceRepository.insertUserPreferences(defaultPreferences)
                
                // Verify the insert actually worked by querying the database multiple times
                // Use separate variable to track DB state (not the local defaultPreferences object)
                var savedPreferences: UserPreferences? = null
                var retries = 0
                while (savedPreferences == null && retries < 5) {
                    delay(500L)  // Wait before each retry
                    savedPreferences = preferenceRepository.getUserPreferencesOnce()
                    retries++
                }
                
                if (savedPreferences == null) {
                    return Result.failure(
                        IllegalStateException("User preferences not initialized and could not be created after $retries retries - data not persisted to database")
                    )
                }
                
                preferences = savedPreferences
            }
            
            // Step 2: Get settings to check enabled sources
            val settings = settingsDataStore.settings.first()
            
            // CRITICAL: Check for Daily Playlist (Every Unlock mode)
            if (settings.changeInterval == "unlock") {
                var nextId = dailyPlaylistManager.getNextWallpaperId()
                
                // If the selected ID matches the excluded one, try getting the next one
                if (nextId != null && nextId == effectiveExcludeId) {
                    if (me.avinas.vanderwaals.BuildConfig.DEBUG) android.util.Log.d("SelectNextWallpaper","Selected ID $nextId matches excluded ID, skipping to next...")
                    nextId = dailyPlaylistManager.getNextWallpaperId()
                }
                
                if (nextId != null) {
                    // Get the wallpaper metadata for this ID from database
                    val allWallpapersForPlaylist = wallpaperRepository.getAllWallpapers().first()
                    val match = allWallpapersForPlaylist.find { it.id == nextId }
                    
                    if (match != null) {
                        if (me.avinas.vanderwaals.BuildConfig.DEBUG) android.util.Log.d("SelectNextWallpaper","Selected from Daily Playlist: ${match.id}")
                        return Result.success(match)
                    } else {
                         android.util.Log.w("SelectNextWallpaper", "Playlist item $nextId not found in database")
                         // Fallback to normal selection if not found
                    }
                } else {
                    if (me.avinas.vanderwaals.BuildConfig.DEBUG) android.util.Log.d("SelectNextWallpaper","Daily Playlist empty or not initialized")
                }
            }
            
            // Step 3: Get ALL wallpapers from database (not just downloaded ones)
            // NEW ARCHITECTURE: Select best wallpaper from entire catalog, download on-demand
            // This eliminates the "no downloaded wallpapers" issue completely
            val allWallpapers = wallpaperRepository.getAllWallpapers().first()
            
            if (me.avinas.vanderwaals.BuildConfig.DEBUG) android.util.Log.d("SelectNextWallpaper","Total wallpapers in catalog: ${allWallpapers.size}")

            if (allWallpapers.isEmpty()) {
                android.util.Log.e("SelectNextWallpaper", "Database is empty! Please sync wallpaper catalog.")
                return Result.failure(
                    IllegalStateException("No wallpapers in catalog. Please sync the wallpaper catalog first.")
                )
            }

            // Filter by enabled sources from settings
            val enabledSources = mutableSetOf<String>()
            if (settings.githubEnabled) enabledSources.add("github")
            if (settings.bingEnabled) enabledSources.add("bing")
            if (settings.vanderwaalsCollectionEnabled) enabledSources.add("vanderwaals")
            
            // Default to GitHub if no sources enabled
            if (enabledSources.isEmpty()) {
                enabledSources.add("github")
            }
            
            if (me.avinas.vanderwaals.BuildConfig.DEBUG) android.util.Log.d("SelectNextWallpaper","Enabled sources: $enabledSources")
            
            // Filter wallpapers by source and exclude current wallpaper
            val filteredWallpapers = allWallpapers.filter { wallpaper ->
                wallpaper.source.lowercase() in enabledSources && wallpaper.id != effectiveExcludeId
            }
            
            if (me.avinas.vanderwaals.BuildConfig.DEBUG) android.util.Log.d("SelectNextWallpaper","Filtered wallpapers (by source, excluding current): ${filteredWallpapers.size}")

            if (filteredWallpapers.isEmpty()) {
                val sourcesInDb = allWallpapers.map { it.source.lowercase() }.distinct()
                android.util.Log.e("SelectNextWallpaper", "No wallpapers after filtering. Sources in DB: $sourcesInDb, enabled: $enabledSources")
                return Result.failure(
                    IllegalStateException("No wallpapers available for selected sources (${enabledSources.joinToString()})")
                )
            }

            // Use filtered Room wallpapers as the local candidate base
            val downloadedWallpapers = filteredWallpapers
            
            // Step 3: Get recent wallpaper history to avoid repeats
            // Use dynamic history size based on change frequency
            val dynamicHistorySize = getHistorySizeForInterval(settings.changeInterval)
            if (me.avinas.vanderwaals.BuildConfig.DEBUG) android.util.Log.d("SelectNextWallpaper","Using dynamic history size: $dynamicHistorySize for interval: ${settings.changeInterval}")
            
            val recentHistoryList = wallpaperRepository.getHistory().first()
            val recentHistory = recentHistoryList
                .take(dynamicHistorySize)
                .map { it.wallpaperId }
                .toSet()
            
            // Calculate consecutive dislikes to boost exploration
            // If user keeps disliking recommendations, we need to explore more aggressively
            var consecutiveDislikes = 0
            for (historyItem in recentHistoryList) {
                if (historyItem.userFeedback == "dislike") {
                    consecutiveDislikes++
                } else if (historyItem.userFeedback == "like") {
                    // Explicit like breaks the dislike chain immediately
                    break
                }
                // Note: We ignore null feedback (passive view) - it doesn't break the chain
                // but doesn't count as dislike either. This allows "dislike -> skip -> dislike"
                // to still count as a negative trend.
            }

            val explorationBoost = when {
                consecutiveDislikes >= 3 -> 0.6f  // High exploration if really unhappy
                consecutiveDislikes == 2 -> 0.3f  // Moderate boost
                consecutiveDislikes == 1 -> 0.1f  // Slight boost
                else -> 0.0f
            }
            
            if (consecutiveDislikes > 0) {
                 if (me.avinas.vanderwaals.BuildConfig.DEBUG) android.util.Log.d("SelectNextWallpaper","User is unhappy (consecutive dislikes: $consecutiveDislikes). Boosting exploration by $explorationBoost")
            }

            // Step 4: Filter out recently shown wallpapers
            val availableWallpapers = downloadedWallpapers.filter { wallpaper ->
                wallpaper.id !in recentHistory
            }

            // Step 5: If all Room wallpapers were shown recently, reset and use all
            val candidateWallpapers = if (availableWallpapers.isEmpty()) downloadedWallpapers else availableWallpapers

            if (candidateWallpapers.isEmpty()) {
                return Result.failure(
                    IllegalStateException("No wallpapers available for enabled sources (${enabledSources.joinToString()})")
                )
            }
            
            // Step 6: Calculate scores for all candidates
            // Check if preference vector exists (either from upload or from first like)
            // IMPORTANT: Both Auto and Personalize modes use this same check
            // - Personalize: Has vector from day 1 (from upload)
            // - Auto: Gets vector after first like, then works identically
            val hasPreferenceVector = preferences.feedbackCount > 0 || 
                                       preferences.preferenceVector.any { it != 0f }
            
            // Log current state for debugging
            val state = if (hasPreferenceVector) "LEARNED (similarity-based)" else "COLD START (diverse selection)"
            if (me.avinas.vanderwaals.BuildConfig.DEBUG) android.util.Log.d("SelectNextWallpaper","Selection state: $state, " +
                    "mode=${preferences.mode}, " +
                    "feedbackCount=${preferences.feedbackCount}, " +
                    "preferenceVector non-zero=${preferences.preferenceVector.any { it != 0f }}, " +
                    "candidates=${candidateWallpapers.size}")
            
            val deviceSeed = getDeviceSpecificSeed()
            
            // Get recent categories for diversity enforcement (before using in map)
            val recentCategories = getRecentCategories(
                recentHistory = recentHistory.toList(),
                allWallpapers = downloadedWallpapers
            )
            
            val rankedWallpapers = if (hasPreferenceVector) {
                // Preference vector exists: Use DUAL-ANCHOR similarity scoring
                // USED BY BOTH MODES once preferences exist:
                // - Personalize Mode: From day 1 (initialized from upload)
                // - Auto Mode: After first like (created from feedback)
                // Combines: originalEmbedding + preferenceVector + category + color + composition
                if (me.avinas.vanderwaals.BuildConfig.DEBUG) android.util.Log.d("SelectNextWallpaper","Using LEARNED PREFERENCES (dual-anchor + category scoring)")
                
                val hasOriginalEmbedding = preferences.originalEmbedding.isNotEmpty()
                if (me.avinas.vanderwaals.BuildConfig.DEBUG) android.util.Log.d("SelectNextWallpaper","Has original embedding: $hasOriginalEmbedding")
                
                // ADAPTIVE LEARNING WEIGHTS: Calculate once outside loop
                // Start: 40% original + 60% learned
                // After 50 feedback: ~20% original + ~80% learned (trust user's taste more)
                // Renormalised so the two weights always sum to exactly 1.0.
                val learningProgress = kotlin.math.min(preferences.feedbackCount / 50f, 1f)
                val rawOriginalWeight = 0.4f * (1f - learningProgress * 0.5f)  // 40% → 20%
                val rawLearnedWeight = 0.6f * (1f + learningProgress * 0.3f)   // 60% → 78%
                val weightSum = rawOriginalWeight + rawLearnedWeight
                val originalWeight = rawOriginalWeight / weightSum
                val learnedWeight = rawLearnedWeight / weightSum
                
                if (me.avinas.vanderwaals.BuildConfig.DEBUG) android.util.Log.d("SelectNextWallpaper",
                    "Adaptive weights: original=${String.format("%.1f%%", originalWeight * 100)}, " +
                    "learned=${String.format("%.1f%%", learnedWeight * 100)} " +
                    "(progress=${String.format("%.0f%%", learningProgress * 100)}, " +
                    "feedbackCount=${preferences.feedbackCount})"
                )
                
                // MEMORY-EFFICIENT CHUNKED PROCESSING
                // Process wallpapers in batches of 1000 to reduce peak memory from ~190MB to ~40MB
                // After each batch, keep only top 200 candidates to avoid holding all scores in memory
                // IMPORTANT: Same exact algorithm, just processed in memory-friendly batches
                val BATCH_SIZE = 1000
                val TOP_K_KEEP = 200  // Keep top 200 after each batch to ensure best surface
                
                val topCandidates = java.util.ArrayList<RankedWallpaper>(TOP_K_KEEP * 2)
                val recentCategoriesList = recentCategories.toList()
                
                candidateWallpapers.chunked(BATCH_SIZE).forEachIndexed { batchIndex, batch ->
                    // Score this batch - exact same logic as before
                    val batchScored = batch.map { wallpaper ->
                        // Calculate similarity to preference vector (learned from likes/dislikes)
                        val preferenceSimilarity = similarityCalculator.calculateSimilarity(
                            preferences.preferenceVector,
                            wallpaper.embedding
                        )
                        
                        // Calculate similarity to original embedding (prime reference from upload/category)
                        val originalSimilarity = if (hasOriginalEmbedding) {
                            similarityCalculator.calculateSimilarity(
                                preferences.originalEmbedding,
                                wallpaper.embedding
                            )
                        } else {
                            0f // Fallback if original embedding missing (legacy data)
                        }
                        
                        // DUAL-ANCHOR scoring with adaptive weights.
                        val baseSimilarity = if (wallpaper.embedding.isNotEmpty()) {
                            // Embedding available: use dual-anchor scoring
                            if (hasOriginalEmbedding) {
                                (originalSimilarity * originalWeight) + (preferenceSimilarity * learnedWeight)
                            } else {
                                preferenceSimilarity
                            }
                        } else {
                            // No embedding: use moderate neutral score to stay competitive
                            0.4f
                        }
                        
                        // CONTENT BOOST: Category boost OR color boost as fallback
                        val categoryScore = wallpaperScorer.getContentBoost(wallpaper)
                        
                        // COMPOSITION BOOST: Advanced layout/composition preference matching
                        val compositionScore = wallpaperScorer.getCompositionBoost(wallpaper)
                        
                        // TEMPORAL DIVERSITY BOOST: Prevent repetition, explore new categories
                        val diversityBoost = wallpaperScorer.getTemporalDiversityBoost(
                            category = wallpaper.category,
                            recentCategories = recentCategoriesList
                        )

                        // TIME-OF-DAY BOOST: nudges brightness preference based on the current
                        // wall-clock hour. Night → prefer dark; morning → prefer bright; neutral
                        // during the day. See WallpaperScorer.getTimeOfDayBoost for details.
                        val timeOfDayBoost = wallpaperScorer.getTimeOfDayBoost(wallpaper)

                        // SEMANTIC BOOST: mood/style tag affinity (Vanderwaals Collection).
                        // Returns 0 for sources without mood/style tags — graceful degradation.
                        val semanticBoost = wallpaperScorer.getSemanticBoost(
                            wallpaper = wallpaper,
                            moodAffinity = preferences.moodAffinity,
                            styleAffinity = preferences.styleAffinity
                        )

                        // FINAL SCORE: similarity + content + composition + diversity + time-of-day + semantic
                        val adjustedSimilarity = baseSimilarity + categoryScore + compositionScore + diversityBoost + timeOfDayBoost + semanticBoost
                        
                        RankedWallpaper(
                            wallpaper = wallpaper,
                            similarity = adjustedSimilarity
                        )
                    }
                    
                    // Add batch results to candidates
                    topCandidates.addAll(batchScored)
                    
                    // After each batch, prune to top K to limit memory growth
                    if (topCandidates.size > TOP_K_KEEP) {
                        topCandidates.sortByDescending { it.similarity }
                        // Remove lower-scoring candidates to free memory
                        while (topCandidates.size > TOP_K_KEEP) {
                            topCandidates.removeAt(topCandidates.size - 1)
                        }
                    }
                }
                
                // Final sort of top candidates
                topCandidates.sortByDescending { it.similarity }
                
                // Log top 5 for debugging
                topCandidates.take(5).forEachIndexed { index, wallpaper ->
                    if (me.avinas.vanderwaals.BuildConfig.DEBUG) android.util.Log.d("SelectNextWallpaper","Top ${index + 1}: ${wallpaper.wallpaper.id} (similarity=${String.format("%.4f", wallpaper.similarity)}, category=${wallpaper.wallpaper.category})")
                }
                
                if (me.avinas.vanderwaals.BuildConfig.DEBUG) android.util.Log.d("SelectNextWallpaper","Chunked processing complete: ${candidateWallpapers.size} candidates → ${topCandidates.size} top matches")
                
                topCandidates
            } else {
                // Cold start state (no preference vector yet):
                // - Personalize Mode: Never reaches here (has vector from upload)
                // - Auto Mode: Starts here, then moves to similarity-based after first like
                // 
                // Show diverse, high-quality wallpapers to help user discover preferences
                // This gives the algorithm varied data to learn from when feedback starts
                // 
                // Strategy: Sample best wallpapers from each repo proportionally
                // Prevents one repo from dominating while prioritizing quality
                if (me.avinas.vanderwaals.BuildConfig.DEBUG) android.util.Log.d("SelectNextWallpaper","Using COLD START (diverse selection, no preferences yet)")
                selectDiverseWallpapers(candidateWallpapers, deviceSeed)
            }
            
            // Create seeded random for each invocation
            val seededRandom = createSeededRandom()
            
            // Use YouTube-like selection for diverse, engaging recommendations
            // Replaced epsilon-greedy in Dec 2025 for better:
            // serendipity, adaptive exploration, category diversity, engagement prediction
            
            // Build session context for YouTube-like recommender
            val likedCategories = mutableMapOf<String, Int>()
            val dislikedCategories = mutableMapOf<String, Int>()

            // Build category preference maps from history.
            val wallpaperById = (candidateWallpapers + downloadedWallpapers).associateBy { it.id }
            recentHistoryList.forEach { historyItem ->
                val wallpaper = wallpaperById[historyItem.wallpaperId]
                if (wallpaper != null) {
                    val category = wallpaper.category
                    when (historyItem.userFeedback) {
                        "like" -> {
                            if (category.isNotBlank()) likedCategories[category] =
                                likedCategories.getOrDefault(category, 0) + 1
                        }
                        "dislike" -> {
                            if (category.isNotBlank()) dislikedCategories[category] =
                                dislikedCategories.getOrDefault(category, 0) + 1
                        }
                    }
                }
            }
            
            // ── New signals for advanced scoring ──────────────────────────────────────────

            // Disliked embedding centroid: L2-normalised mean of disliked wallpaper embeddings.
            // Fed into YouTubeLikeRecommender as a semantic dislike-penalty signal so that
            // candidates similar to previously disliked content are downranked.
            val dislikedCentroid: FloatArray? = run {
                val dislikedIds = preferences.dislikedWallpaperIds.toSet()
                if (dislikedIds.isEmpty()) return@run null
                val embeddings = candidateWallpapers
                    .filter { it.id in dislikedIds && it.embedding.isNotEmpty() }
                    .map { it.embedding }
                if (embeddings.isEmpty()) return@run null
                val dim = embeddings.first().size
                val sum = FloatArray(dim)
                embeddings.forEach { e -> e.forEachIndexed { i, v -> sum[i] += v } }
                val n = embeddings.size.toFloat()
                val raw = FloatArray(dim) { i -> sum[i] / n }
                val mag = kotlin.math.sqrt(raw.sumOf { (it * it).toDouble() }).toFloat()
                if (mag == 0f) null else FloatArray(dim) { i -> raw[i] / mag }
            }

            // Category set-duration map: average minutes each category was displayed per history
            // entry. Used as implicit engagement signal in YouTubeLikeRecommender.predictEngagement.
            val categorySetDurations: Map<String, Long> = run {
                val durationsByCat = mutableMapOf<String, MutableList<Long>>()
                recentHistoryList.forEach { item ->
                    val durationMins = (item.getDurationSeconds() ?: return@forEach) / 60L
                    val category = wallpaperById[item.wallpaperId]?.category
                        ?.takeIf { it.isNotBlank() } ?: return@forEach
                    durationsByCat.getOrPut(category) { mutableListOf() }.add(durationMins)
                }
                durationsByCat.mapValues { (_, list) -> list.average().toLong() }
            }

            val sessionContext = me.avinas.vanderwaals.algorithm.YouTubeLikeRecommender.SessionContext(
                recentlyViewedIds = recentHistory,
                recentCategories = recentCategories.toList(),
                sessionLikes = recentHistoryList.take(10).count { it.userFeedback == "like" },
                sessionDislikes = recentHistoryList.take(10).count { it.userFeedback == "dislike" },
                totalHistoryLikes = preferences.likedWallpaperIds.size,
                totalHistoryDislikes = preferences.dislikedWallpaperIds.size,
                likedCategories = likedCategories,
                dislikedCategories = dislikedCategories,
                categorySetDurations = categorySetDurations,
                dislikedEmbeddingCentroid = dislikedCentroid
            )
            
            // Convert ranked wallpapers to format expected by YouTube-like recommender
            val candidatesWithScores = rankedWallpapers.map { 
                Pair(it.wallpaper, it.similarity) 
            }
            
            val selectedWallpaper = try {
                youtubeLikeRecommender.selectWallpaper(
                    candidates = candidatesWithScores,
                    context = sessionContext,
                    random = seededRandom
                )
            } catch (e: Exception) {
                android.util.Log.w("SelectNextWallpaper", "YouTube-like selection failed, falling back to top match", e)
                // Fallback to top ranked wallpaper
                rankedWallpapers.first().wallpaper
            }
            
            if (me.avinas.vanderwaals.BuildConfig.DEBUG) android.util.Log.d("SelectNextWallpaper","Selected via YouTube-like algorithm: ${selectedWallpaper.id} (category=${selectedWallpaper.category})")
            
            // Step 9: Record category/color views
            if (selectedWallpaper.category.isNotBlank()) {
                categoryPreferenceRepository.recordView(selectedWallpaper.category)
            } else {
                // Record color views for uncategorized wallpapers
                colorPreferenceRepository.recordViews(selectedWallpaper.colors.take(3))
            }
            
            Result.success(selectedWallpaper)
            
        } catch (e: Exception) {
            Result.failure(
                Exception("Failed to select next wallpaper: ${e.message}", e)
            )
        }
    }
    
    /**
     * Gets categories from recent wallpaper history.
     */
    private fun getRecentCategories(
        recentHistory: List<String>,
        allWallpapers: List<WallpaperMetadata>
    ): Set<String> {
        val wallpaperMap = allWallpapers.associateBy { it.id }
        return recentHistory
            .take(3)
            .mapNotNull { wallpaperMap[it]?.category }
            .toSet()
    }
    
    /**
     * Select diverse wallpapers for cold start using WallpaperScorer.
     */
    private fun selectDiverseWallpapers(
        wallpapers: List<WallpaperMetadata>,
        deviceSeed: Int
    ): List<RankedWallpaper> {
        return wallpapers.map { wallpaper ->
            val score = wallpaperScorer.calculatePopularityScore(
                wallpaper = wallpaper,
                deviceSeed = deviceSeed
            )
            RankedWallpaper(wallpaper, score)
        }.sortedByDescending { it.similarity }
    }
    
    /**
     * Internal data class for pairing wallpapers with similarity scores.
     */
    private data class RankedWallpaper(
        val wallpaper: WallpaperMetadata,
        val similarity: Float
    )
    
    companion object {
        /**
         * Base number of recent wallpapers to remember (to avoid repeats).
         * This is dynamically adjusted based on change frequency.
         */
        private const val BASE_RECENT_HISTORY_SIZE = 10

        /**
         * Calculates dynamic history size based on change interval.
         * 
         * For high-frequency changes (15 min, unlock), we need a larger history window
         * to prevent repeats over a reasonable time period (at least 12-24 hours).
         * 
         * - 15 minutes: 48 changes/day → need at least 48 history entries for 12hr protection
         * - Unlock: ~20-50 unlocks/day → need at least 30 history entries
         * - Hourly: 24 changes/day → need at least 24 history entries for 12hr protection
         * - 3 hours: 8 changes/day → need at least 16 history entries for 2-day protection
         * - 6 hours: 4 changes/day → need at least 12 history entries for 3-day protection
         * - 12 hours: 2 changes/day → need at least 10 history entries for 5-day protection
         * - Daily: 1 change/day → need at least 7 history entries for a week's protection
         * 
         * @param changeInterval The current change interval setting
         * @return Dynamic history size
         */
        fun getHistorySizeForInterval(changeInterval: String): Int {
            return when (changeInterval) {
                "15min" -> 48    // 12 hours of protection at 15-min intervals
                "unlock" -> 40   // Full day protection for typical usage
                "hourly" -> 24   // 24 hours of protection
                "3hours" -> 16   // 2 days of protection at 3-hour intervals
                "6hours" -> 12   // 3 days of protection at 6-hour intervals
                "12hours" -> 10  // 5 days of protection at 12-hour intervals
                "daily" -> 14    // 2 weeks of protection
                "3days" -> 7     // 3 weeks of protection at 3-day intervals
                "7days" -> 4     // 4 weeks of protection at 7-day intervals
                else -> BASE_RECENT_HISTORY_SIZE
            }
        }
    }
    
    /**
     * Get device-specific seed for randomization.
     * Uses Android ID to ensure different devices get different wallpaper sequences.
     */
    private fun getDeviceSpecificSeed(): Int {
        val androidId = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "default_device"
        return androidId.hashCode()
    }
    
    /**
     * Selects the next wallpaper after a user dislike.
     *
     * Routes through the same [YouTubeLikeRecommender] used by normal selection
     * so post-dislike recommendations follow the same algorithm (MMR diversity,
     * temperature-softmax exploration, saturation penalties) rather than a
     * bespoke stale path.
     *
     * **Post-dislike adjustments** (applied via [SessionContext]):
     * 1. The just-disliked wallpaper's category is added to `dislikedCategories`
     *    so [YouTubeLikeRecommender.predictEngagement] downranks it.
     * 2. The just-disliked embedding is merged into `dislikedEmbeddingCentroid`
     *    so [YouTubeLikeRecommender.calculateDislikedPenalty] penalises
     *    semantically similar candidates.
     * 3. `sessionDislikes` is incremented so
     *    [YouTubeLikeRecommender.calculateAdaptiveExplorationRate] raises the
     *    exploration rate (user is unhappy → explore more broadly).
     * 4. The disliked wallpaper id is added to `recentlyViewedIds` to prevent
     *    immediate re-selection.
     *
     * @param dislikedWallpaperId ID of the wallpaper the user just disliked
     * @param dislikedCategory Category of the disliked wallpaper
     * @param dislikedEmbedding Embedding vector of the disliked wallpaper
     * @return Result<WallpaperMetadata> with a diverse wallpaper selection
     */
    suspend fun selectAfterDislike(
        dislikedWallpaperId: String,
        dislikedCategory: String,
        dislikedEmbedding: FloatArray
    ): Result<WallpaperMetadata> {
        return try {
            if (me.avinas.vanderwaals.BuildConfig.DEBUG) android.util.Log.d("SelectNextWallpaper","=== POST-DISLIKE SELECTION ===")
            if (me.avinas.vanderwaals.BuildConfig.DEBUG) android.util.Log.d("SelectNextWallpaper","Disliked wallpaper: $dislikedWallpaperId (category: $dislikedCategory)")

            // Step 1: Get settings and preferences
            val settings = settingsDataStore.settings.first()
            val preferences = preferenceRepository.getUserPreferencesOnce()

            // Step 2: Get ALL wallpapers from database
            val allWallpapers = wallpaperRepository.getAllWallpapers().first()
            if (allWallpapers.isEmpty()) {
                return Result.failure(IllegalStateException("No wallpapers in catalog"))
            }

            // Step 3: Filter by enabled sources
            val enabledSources = mutableSetOf<String>()
            if (settings.githubEnabled) enabledSources.add("github")
            if (settings.bingEnabled) enabledSources.add("bing")
            if (settings.vanderwaalsCollectionEnabled) enabledSources.add("vanderwaals")
            if (enabledSources.isEmpty()) enabledSources.add("github")

            val candidateWallpapers = allWallpapers.filter { wallpaper ->
                wallpaper.source.lowercase() in enabledSources && wallpaper.id != dislikedWallpaperId
            }
            if (candidateWallpapers.isEmpty()) {
                return Result.failure(IllegalStateException("No alternative wallpapers available"))
            }

            // Step 4: Get recent history to avoid immediate repeats
            val recentHistoryList = wallpaperRepository.getHistory().first()
            val recentHistory = recentHistoryList.take(10).map { it.wallpaperId }.toSet() + dislikedWallpaperId
            val recentCategoriesList = recentHistoryList.take(10).mapNotNull { item ->
                candidateWallpapers.find { it.id == item.wallpaperId }?.category
            }

            // Step 5: Build category preference maps from history
            val wallpaperById = candidateWallpapers.associateBy { it.id }
            val likedCategories = mutableMapOf<String, Int>()
            val dislikedCategories = mutableMapOf<String, Int>()
            recentHistoryList.forEach { historyItem ->
                val wallpaper = wallpaperById[historyItem.wallpaperId] ?: return@forEach
                val category = wallpaper.category
                if (category.isBlank()) return@forEach
                when (historyItem.userFeedback) {
                    "like" -> likedCategories[category] = likedCategories.getOrDefault(category, 0) + 1
                    "dislike" -> dislikedCategories[category] = dislikedCategories.getOrDefault(category, 0) + 1
                }
            }
            // Fold in the just-disliked category
            if (dislikedCategory.isNotBlank()) {
                dislikedCategories[dislikedCategory] = dislikedCategories.getOrDefault(dislikedCategory, 0) + 1
            }

            // Step 6: Build disliked embedding centroid (existing dislikes + just-disliked)
            val dislikedCentroid: FloatArray? = run {
                val dislikedIds = (preferences?.dislikedWallpaperIds ?: emptyList()).toSet() + dislikedWallpaperId
                val embeddings = candidateWallpapers
                    .filter { it.id in dislikedIds && it.embedding.isNotEmpty() }
                    .map { it.embedding }
                    .toMutableList()
                // Include the just-disliked embedding even if not yet in the catalog filter
                if (dislikedEmbedding.isNotEmpty()) embeddings.add(dislikedEmbedding)
                if (embeddings.isEmpty()) return@run null
                val dim = embeddings.first().size
                val sum = FloatArray(dim)
                embeddings.forEach { e -> e.forEachIndexed { i, v -> sum[i] += v } }
                val n = embeddings.size.toFloat()
                val raw = FloatArray(dim) { i -> sum[i] / n }
                val mag = kotlin.math.sqrt(raw.sumOf { (it * it).toDouble() }).toFloat()
                if (mag == 0f) null else FloatArray(dim) { i -> raw[i] / mag }
            }

            // Step 7: Score candidates (base similarity + content boosts)
            val hasPreferenceVector = preferences?.preferenceVector?.isNotEmpty() == true
            val scoredWallpapers = candidateWallpapers.map { wallpaper ->
                val baseSimilarity = if (hasPreferenceVector && wallpaper.embedding.isNotEmpty()) {
                    similarityCalculator.calculateSimilarity(preferences!!.preferenceVector, wallpaper.embedding)
                } else {
                    0.4f // Neutral for cold start
                }
                val categoryScore = wallpaperScorer.getContentBoost(wallpaper)
                val compositionScore = wallpaperScorer.getCompositionBoost(wallpaper)
                val diversityBoost = wallpaperScorer.getTemporalDiversityBoost(
                    category = wallpaper.category,
                    recentCategories = recentCategoriesList
                )
                val timeOfDayBoost = wallpaperScorer.getTimeOfDayBoost(wallpaper)
                val semanticBoost = wallpaperScorer.getSemanticBoost(
                    wallpaper = wallpaper,
                    moodAffinity = preferences?.moodAffinity ?: emptyMap(),
                    styleAffinity = preferences?.styleAffinity ?: emptyMap()
                )
                RankedWallpaper(
                    wallpaper = wallpaper,
                    similarity = baseSimilarity + categoryScore + compositionScore + diversityBoost + timeOfDayBoost + semanticBoost
                )
            }.sortedByDescending { it.similarity }

            if (me.avinas.vanderwaals.BuildConfig.DEBUG) {
                scoredWallpapers.take(5).forEachIndexed { index, ranked ->
                    android.util.Log.d("SelectNextWallpaper",
                        "Post-dislike candidate ${index + 1}: ${ranked.wallpaper.id} " +
                        "(category: ${ranked.wallpaper.category}, score: ${String.format("%.3f", ranked.similarity)})")
                }
            }

            // Step 8: Build session context with post-dislike signals folded in
            val sessionContext = me.avinas.vanderwaals.algorithm.YouTubeLikeRecommender.SessionContext(
                recentlyViewedIds = recentHistory,
                recentCategories = recentCategoriesList,
                sessionLikes = recentHistoryList.take(10).count { it.userFeedback == "like" },
                // +1 for the just-registered dislike → raises exploration rate
                sessionDislikes = recentHistoryList.take(10).count { it.userFeedback == "dislike" } + 1,
                totalHistoryLikes = preferences?.likedWallpaperIds?.size ?: 0,
                totalHistoryDislikes = (preferences?.dislikedWallpaperIds?.size ?: 0) + 1,
                likedCategories = likedCategories,
                dislikedCategories = dislikedCategories,
                dislikedEmbeddingCentroid = dislikedCentroid
            )

            val candidatesWithScores = scoredWallpapers.map { Pair(it.wallpaper, it.similarity) }
            val seededRandom = createSeededRandom()

            val selectedWallpaper = try {
                youtubeLikeRecommender.selectWallpaper(
                    candidates = candidatesWithScores,
                    context = sessionContext,
                    random = seededRandom
                )
            } catch (e: Exception) {
                android.util.Log.w("SelectNextWallpaper", "Post-dislike selection failed, falling back to top match", e)
                scoredWallpapers.first().wallpaper
            }

            if (me.avinas.vanderwaals.BuildConfig.DEBUG) android.util.Log.d("SelectNextWallpaper",
                "Post-dislike selected: ${selectedWallpaper.id} (category: ${selectedWallpaper.category})")
            if (me.avinas.vanderwaals.BuildConfig.DEBUG) android.util.Log.d("SelectNextWallpaper","=== END POST-DISLIKE SELECTION ===")

            // Record category view
            if (selectedWallpaper.category.isNotBlank()) {
                categoryPreferenceRepository.recordView(selectedWallpaper.category)
            }

            Result.success(selectedWallpaper)

        } catch (e: Exception) {
            android.util.Log.e("SelectNextWallpaper", "Post-dislike selection failed", e)
            Result.failure(Exception("Failed to select wallpaper after dislike: ${e.message}", e))
        }
    }
}
