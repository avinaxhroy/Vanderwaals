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
 * Use case for selecting the next wallpaper to display with intelligent learning algorithm.
 * 
 * **IMPORTANT: Both Auto and Personalize modes use the SAME learning algorithm!**
 * The ONLY difference is how preferences are initialized:
 * 
 * **PERSONALIZE MODE (Netflix: Tell us your favorites upfront):**
 * - During onboarding: User uploads favorite wallpaper OR selects category
 * - App finds similar wallpapers, user picks 3+ they like
 * - Creates initial preference vector immediately (feedbackCount > 0)
 * - Shows personalized wallpapers from day 1
 * - Continues learning from every like/dislike
 * 
 * **AUTO MODE (Netflix: Start watching, we'll learn as you go):**
 * - During onboarding: User skips upload step
 * - Starts with NO preference vector (feedbackCount = 0, empty vector)
 * - Shows diverse, high-quality wallpapers initially
 * - When user likes FIRST wallpaper: Creates preference vector
 * - After that: Uses EXACT SAME algorithm as Personalize Mode
 * - After 10-15 likes: Just as personalized as Personalize Mode
 * 
 * **Key Insight:**
 * Auto Mode IS personalized - it just learns from scratch instead of starting
 * with user's upload. Both modes end up equally personalized over time.
 * 
 * **Selection Algorithm:**
 * ```
 * 1. Check: Does preference vector exist? (feedbackCount > 0)
 * 2. If NO:  Show diverse wallpapers (Auto Mode cold start)
 *    If YES: Use similarity scoring (learned preferences)
 * 3. Filter out recently shown wallpapers (last 10)
 * 4. Apply epsilon-greedy selection (90% best, 10% explore)
 * 5. Return chosen wallpaper
 * ```
 * 
 * **Learning Mechanism (IDENTICAL for both modes):**
 * - Like: Pull preference vector toward wallpaper embedding
 * - Dislike: Push preference vector away from wallpaper embedding
 * - Adaptive learning rate based on feedback count
 * 
 * @property wallpaperRepository Repository for accessing downloaded wallpapers
 * @property preferenceRepository Repository for accessing user preferences
 * @property similarityCalculator Utility for computing similarity scores
 * 
 * @see UpdatePreferencesUseCase
 * @see FindSimilarWallpapersUseCase
 */
@Singleton
class SelectNextWallpaperUseCase @Inject constructor(
    private val wallpaperRepository: WallpaperRepository,
    private val preferenceRepository: PreferenceRepository,
    private val categoryPreferenceRepository: CategoryPreferenceRepository,
    private val colorPreferenceRepository: me.avinas.vanderwaals.data.repository.ColorPreferenceRepository,
    private val compositionPreferenceRepository: me.avinas.vanderwaals.data.repository.CompositionPreferenceRepository,
    private val similarityCalculator: SimilarityCalculator,
    private val settingsDataStore: me.avinas.vanderwaals.data.datastore.SettingsDataStore,
    private val dailyPlaylistManager: me.avinas.vanderwaals.data.repository.DailyPlaylistManager,
    private val wallpaperHistoryDao: WallpaperHistoryDao,
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) {
    private val colorAnalyzer = me.avinas.vanderwaals.algorithm.ColorAnalyzer
    private val compositionAnalyzer = me.avinas.vanderwaals.algorithm.CompositionAnalyzer
    private val explorationStrategy = me.avinas.vanderwaals.algorithm.ExplorationStrategy()
    
    // YouTube-like recommender for more engaging, diverse recommendations
    private val youtubeLikeRecommender = me.avinas.vanderwaals.algorithm.YouTubeLikeRecommender()
    
    /**
     * Seeded random instance for true randomness.
     * CRITICAL FIX (Nov 2025): Creates new seed on each invocation combining multiple entropy sources.
     * This prevents repeating patterns after app restart or data clear.
     * 
     * IMPROVED: Now uses multiple entropy sources including:
     * - Device ID (consistent per device)
     * - Current timestamp (changes every millisecond) 
     * - Process uptime (different each app launch)
     * - Random system noise
     * 
     * This ensures fresh installs don't see same sequence.
     */
    private fun createSeededRandom(): Random {
        val deviceSeed = getDeviceSpecificSeed()
        val timeSeed = System.currentTimeMillis()
        val uptimeSeed = android.os.SystemClock.uptimeMillis()
        val noiseSeed = (Math.random() * Int.MAX_VALUE).toLong()
        
        // Combine all entropy sources with different bit operations for maximum randomness
        val combinedSeed = (deviceSeed.toLong() xor timeSeed xor uptimeSeed xor noiseSeed).toInt()
        android.util.Log.d("SelectNextWallpaper", "Created seeded Random with combined seed: $combinedSeed (device=$deviceSeed, time=$timeSeed, uptime=$uptimeSeed, noise=$noiseSeed)")
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
            // CRITICAL FIX (Dec 2025): Always get the currently active wallpaper and exclude it
            // This prevents the algorithm from selecting the same wallpaper that's currently displayed.
            // Previously, history was only recorded AFTER applying the wallpaper, causing a race condition
            // where the same wallpaper could be selected again immediately.
            val currentActiveWallpaper = wallpaperHistoryDao.getActiveWallpaper()
            val effectiveExcludeId = excludeWallpaperId ?: currentActiveWallpaper?.wallpaperId
            
            if (effectiveExcludeId != null) {
                android.util.Log.d("SelectNextWallpaper", "Excluding currently active wallpaper: $effectiveExcludeId")
            }
            
            // Step 1: Get user preferences, or create defaults if not initialized
            // Use direct database read (not Flow) to avoid cached values
            // CRITICAL FIX: Retry multiple times to handle multi-instance sync delay
            // Check for both null AND stale default values (feedbackCount=0)
            var preferences = preferenceRepository.getUserPreferencesOnce()
            var retryCount = 0
            
            // Retry if preferences are null OR if they have default/stale values
            while ((preferences == null || preferences.feedbackCount == 0) && retryCount < 5) {
                android.util.Log.d("SelectNextWallpaper", "Preferences stale/null (feedbackCount=${preferences?.feedbackCount}) on attempt ${retryCount + 1}, retrying after delay...")
                delay(300L)  // Longer delay for database sync
                preferences = preferenceRepository.getUserPreferencesOnce()
                retryCount++
                android.util.Log.d("SelectNextWallpaper", "Retry $retryCount result: feedbackCount=${preferences?.feedbackCount}")
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
                    android.util.Log.d("SelectNextWallpaper", "Selected ID $nextId matches excluded ID, skipping to next...")
                    nextId = dailyPlaylistManager.getNextWallpaperId()
                }
                
                if (nextId != null) {
                    // Get the wallpaper metadata for this ID
                    // We need a way to get a single wallpaper. 
                    // Since we don't have getWallpaperById, we can find it in downloaded list or all list.
                    // Ideally we should have getWallpaperById in repository.
                    // For now, let's try to find it in downloaded wallpapers first.
                    val downloaded = wallpaperRepository.getDownloadedWallpapers().first()
                    val match = downloaded.find { it.id == nextId }
                    
                    if (match != null) {
                        android.util.Log.d("SelectNextWallpaper", "Selected from Daily Playlist: ${match.id}")
                        return Result.success(match)
                    } else {
                         android.util.Log.w("SelectNextWallpaper", "Playlist item $nextId not found in downloaded wallpapers")
                         // Fallback to normal selection if file missing
                    }
                } else {
                    android.util.Log.d("SelectNextWallpaper", "Daily Playlist empty or not initialized")
                }
            }
            
            // Step 3: Get downloaded wallpapers (only those ready for display)
            val allDownloadedWallpapers = wallpaperRepository.getDownloadedWallpapers().first()
            
            if (allDownloadedWallpapers.isEmpty()) {
                return Result.failure(
                    IllegalStateException("No wallpapers available. Please download wallpapers first.")
                )
            }
            
            // CRITICAL FIX: Filter by enabled sources from settings
            val enabledSources = mutableSetOf<String>()
            if (settings.githubEnabled) enabledSources.add("github")
            if (settings.bingEnabled) enabledSources.add("bing")
            
            // Default to GitHub if no sources enabled
            if (enabledSources.isEmpty()) {
                enabledSources.add("github")
            }
            
            val downloadedWallpapers = allDownloadedWallpapers.filter { wallpaper ->
                wallpaper.source in enabledSources && wallpaper.id != effectiveExcludeId
            }
            
            if (downloadedWallpapers.isEmpty()) {
                return Result.failure(
                    IllegalStateException("No wallpapers available for selected sources (${enabledSources.joinToString()})")
                )
            }
            
            // Step 3: Get recent wallpaper history to avoid repeats
            // CRITICAL FIX (Dec 2025): Use dynamic history size based on change frequency
            // For 15-minute changes, we need a much larger window to prevent repeats
            val dynamicHistorySize = getHistorySizeForInterval(settings.changeInterval)
            android.util.Log.d("SelectNextWallpaper", "Using dynamic history size: $dynamicHistorySize for interval: ${settings.changeInterval}")
            
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
                 android.util.Log.d("SelectNextWallpaper", "User is unhappy (consecutive dislikes: $consecutiveDislikes). Boosting exploration by $explorationBoost")
            }

            // Step 4: Filter out recently shown wallpapers
            val availableWallpapers = downloadedWallpapers.filter { wallpaper ->
                wallpaper.id !in recentHistory
            }
            
            // Step 5: If all wallpapers were shown recently, reset and use all
            val candidateWallpapers = if (availableWallpapers.isEmpty()) {
                downloadedWallpapers
            } else {
                availableWallpapers
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
            android.util.Log.d("SelectNextWallpaper", "Selection state: $state, " +
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
                android.util.Log.d("SelectNextWallpaper", "Using LEARNED PREFERENCES (dual-anchor + category scoring)")
                
                val hasOriginalEmbedding = preferences.originalEmbedding.isNotEmpty()
                android.util.Log.d("SelectNextWallpaper", "Has original embedding: $hasOriginalEmbedding")
                
                // ADAPTIVE LEARNING WEIGHTS: Calculate once outside loop
                // Start: 40% original + 60% learned
                // After 50 feedback: 20% original + 78% learned (trust user's taste more)
                val learningProgress = kotlin.math.min(preferences.feedbackCount / 50f, 1f)
                val originalWeight = 0.4f * (1f - learningProgress * 0.5f)  // 40% → 20%
                val learnedWeight = 0.6f * (1f + learningProgress * 0.3f)   // 60% → 78%
                
                android.util.Log.d("SelectNextWallpaper", 
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
                        
                        // DUAL-ANCHOR scoring with adaptive weights
                        val baseSimilarity = if (hasOriginalEmbedding) {
                            (originalSimilarity * originalWeight) + (preferenceSimilarity * learnedWeight)
                        } else {
                            preferenceSimilarity // Fallback for legacy data
                        }
                        
                        // CONTENT BOOST: Category boost OR color boost as fallback
                        val categoryScore = getContentBoost(wallpaper)
                        
                        // COMPOSITION BOOST: Advanced layout/composition preference matching
                        val compositionScore = getCompositionBoost(wallpaper.id)
                        
                        // TEMPORAL DIVERSITY BOOST: Prevent repetition, explore new categories
                        val diversityBoost = getTemporalDiversityBoost(
                            category = wallpaper.category,
                            recentCategories = recentCategoriesList
                        )
                        
                        // Add device-specific variation
                        val deviceVariation = ((deviceSeed + wallpaper.id.hashCode()).toLong() % 100) / 1000f
                        
                        // FINAL SCORE: similarity + content boost + composition + diversity + device variation
                        val adjustedSimilarity = baseSimilarity + categoryScore + compositionScore + diversityBoost + deviceVariation
                        
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
                    android.util.Log.d("SelectNextWallpaper", "Top ${index + 1}: ${wallpaper.wallpaper.id} (similarity=${String.format("%.4f", wallpaper.similarity)}, category=${wallpaper.wallpaper.category})")
                }
                
                android.util.Log.d("SelectNextWallpaper", "Chunked processing complete: ${candidateWallpapers.size} candidates → ${topCandidates.size} top matches")
                
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
                android.util.Log.d("SelectNextWallpaper", "Using COLD START (diverse selection, no preferences yet)")
                selectDiverseWallpapers(candidateWallpapers, deviceSeed)
            }
            
            // CRITICAL FIX: Create seeded random for true randomness on each invocation
            val seededRandom = createSeededRandom()
            
            // Step 8: Use YouTube-like selection algorithm for more engaging recommendations
            // IMPROVED (Dec 2025): Replaced epsilon-greedy with YouTube-like algorithm that provides:
            // - Serendipity (5% chance of surprise picks)
            // - Adaptive exploration (more when user is unhappy)
            // - Diminishing returns for overexposed categories
            // - Diversity in final selection
            // - Engagement prediction based on category history
            
            // Build session context for YouTube-like recommender
            val likedCategories = mutableMapOf<String, Int>()
            val dislikedCategories = mutableMapOf<String, Int>()
            
            // Build category preference maps from history
            recentHistoryList.forEach { historyItem ->
                val wallpaper = downloadedWallpapers.find { it.id == historyItem.wallpaperId }
                if (wallpaper != null && wallpaper.category.isNotBlank()) {
                    when (historyItem.userFeedback) {
                        "like" -> likedCategories[wallpaper.category] = 
                            likedCategories.getOrDefault(wallpaper.category, 0) + 1
                        "dislike" -> dislikedCategories[wallpaper.category] = 
                            dislikedCategories.getOrDefault(wallpaper.category, 0) + 1
                    }
                }
            }
            
            val sessionContext = me.avinas.vanderwaals.algorithm.YouTubeLikeRecommender.SessionContext(
                recentlyViewedIds = recentHistory,
                recentCategories = recentCategories.toList(),
                sessionLikes = recentHistoryList.take(10).count { it.userFeedback == "like" },
                sessionDislikes = recentHistoryList.take(10).count { it.userFeedback == "dislike" },
                totalHistoryLikes = preferences.likedWallpaperIds.size,
                totalHistoryDislikes = preferences.dislikedWallpaperIds.size,
                likedCategories = likedCategories,
                dislikedCategories = dislikedCategories
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
            
            android.util.Log.d("SelectNextWallpaper", "Selected via YouTube-like algorithm: ${selectedWallpaper.id} (category=${selectedWallpaper.category})")
            
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
     * Selects a wallpaper using enhanced epsilon-greedy algorithm with diversity awareness.
     * 
     * **Selection Strategy:**
     * - With probability epsilon: Explore (diversity-aware random selection)
     * - With probability (1-epsilon): Exploit (best match with category diversity check)
     * 
     * **Diversity Enforcement:**
     * - Prevents showing same category back-to-back
     * - Occasionally shows underexplored categories
     * - Balances between quality and variety
     * 
     * **Exploration Pool:**
     * - If < 100 wallpapers available: Use all
     * - If >= 100 wallpapers: Use top 100 by similarity
     * 
     * This ensures exploration still favors reasonably good matches,
     * not completely random wallpapers.
     * 
     * @param rankedWallpapers All candidate wallpapers sorted by similarity
     * @param epsilon Exploration probability (0.0 to 1.0)
     * @param recentCategories Categories of recently shown wallpapers (for diversity)
     * @return Selected wallpaper
     */
    private fun selectWithEpsilonGreedy(
        rankedWallpapers: List<RankedWallpaper>,
        epsilon: Float,
        recentCategories: Set<String> = emptySet(),
        random: Random
    ): WallpaperMetadata {
        // Determine if we should explore or exploit using seeded random
        val shouldExplore = random.nextFloat() < epsilon
        
        return if (shouldExplore) {
            // EXPLORATION: Diversity-aware random selection
            selectForExploration(rankedWallpapers, recentCategories, random)
        } else {
            // EXPLOITATION: Best match with category diversity check
            selectForExploitation(rankedWallpapers, recentCategories, random)
        }
    }
    
    /**
     * Selects wallpaper for exploration phase.
     * Prioritizes diverse categories and underexplored content.
     * 
     * @param rankedWallpapers All candidates sorted by similarity
     * @param recentCategories Recently shown categories
     * @return Selected wallpaper
     */
    private fun selectForExploration(
        rankedWallpapers: List<RankedWallpaper>,
        recentCategories: Set<String>,
        random: Random
    ): WallpaperMetadata {
        val explorationPoolSize = minOf(MAX_EXPLORATION_POOL, rankedWallpapers.size)
        val explorationPool = rankedWallpapers.take(explorationPoolSize)
        
        // Try to find wallpaper from different category first (70% of time)
        if (random.nextFloat() < 0.7f && recentCategories.isNotEmpty()) {
            val differentCategory = explorationPool
                .filter { it.wallpaper.category !in recentCategories }
            
            if (differentCategory.isNotEmpty()) {
                return differentCategory.random(random).wallpaper
            }
        }
        
        // Fallback: Random from full exploration pool
        return explorationPool.random(random).wallpaper
    }
    
    /**
     * Selects wallpaper for exploitation phase (best match).
     * Checks category diversity to avoid repetition.
     * 
     * @param rankedWallpapers All candidates sorted by similarity
     * @param recentCategories Recently shown categories
     * @return Selected wallpaper
     */
    private fun selectForExploitation(
        rankedWallpapers: List<RankedWallpaper>,
        recentCategories: Set<String>,
        random: Random
    ): WallpaperMetadata {
        // Try to find best match from different category
        if (recentCategories.isNotEmpty()) {
            // Look through top 10 matches for different category
            val topCandidates = rankedWallpapers.take(10)
            val differentCategory = topCandidates
                .firstOrNull { it.wallpaper.category !in recentCategories }
            
            if (differentCategory != null) {
                return differentCategory.wallpaper
            }
        }
        
        // Fallback: Just use best match (may repeat category)
        return rankedWallpapers.first().wallpaper
    }
    
    /**
     * Calculates temporal diversity boost to prevent category repetition and explore new categories.
     * 
     * **Recency Penalty:**
     * Penalizes categories that were recently shown to avoid monotony:
     * - Each occurrence in recent history (last 3 wallpapers): -5% penalty
     * - Example: Category shown 2 times recently = -10% penalty
     * 
     * **Exploration Boost:**
     * Boosts categories that are underexplored to increase variety:
     * - New categories (never seen): +5% boost
     * - Rarely seen categories (< 3 views): +5% boost
     * - Well-explored categories (≥ 3 views): No boost
     * 
     * **Result Range:**
     * From -0.15 (shown 3 times recently) to +0.05 (new category)
     * 
     * **Benefits:**
     * - Prevents showing same category back-to-back
     * - Encourages variety in recommendations
     * - Discovers user preferences for new categories
     * - Balances exploitation (known preferences) with exploration (new content)
     * 
     * @param category Category name to evaluate
     * @param recentCategories List of categories from last 3 wallpapers
     * @return Boost value from -0.15 to +0.05
     */
    private suspend fun getTemporalDiversityBoost(
        category: String,
        recentCategories: List<String>
    ): Float {
        return try {
            // RECENCY PENALTY: Penalize if category shown recently
            // Count how many times this category appears in recent history
            val recentCount = recentCategories.count { it == category }
            val recencyPenalty = recentCount * -0.05f  // -5% per recent occurrence
            
            // EXPLORATION BOOST: Boost if category underexplored
            val categoryPref = categoryPreferenceRepository.getByCategory(category)
            val exploreBoost = if (categoryPref == null || categoryPref.views < 3) {
                0.05f  // +5% for new/underexplored categories
            } else {
                0f  // No boost for well-explored categories
            }
            
            val totalBoost = recencyPenalty + exploreBoost
            
            // NOTE: Logging removed - was causing log spam for every wallpaper
            
            return totalBoost
        } catch (e: Exception) {
            android.util.Log.e("SelectNextWallpaper", "Error calculating temporal diversity boost", e)
            return 0f
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
     * Formula: categoryScore × 0.15
     * Where categoryScore = (likes - 2×dislikes) / (likes + dislikes + 1)
     * 
     * This ensures the algorithm respects BOTH:
     * - Visual similarity (85% weight via embeddings)
     * - Category preferences (15% weight via explicit feedback)
     * 
     * @param category Category name (e.g., "nature", "minimal")
     * @return Boost value from -0.15 (strongly disliked) to +0.15 (strongly liked)
     */
    private suspend fun getCategoryBoost(category: String): Float {
        return try {
            val categoryPref = categoryPreferenceRepository.getByCategory(category)
            if (categoryPref == null) {
                // No data yet - neutral score
                return 0f
            }
            
            // Calculate category score (-1.0 to +1.0)
            val score = categoryPref.calculateScore()
            
            // Apply 15% weight to category preference
            // This balances embedding similarity (85%) with category feedback (15%)
            val boost = score * 0.15f
            
            // NOTE: Logging removed - was causing log spam for every wallpaper
            
            return boost
        } catch (e: Exception) {
            android.util.Log.e("SelectNextWallpaper", "Error calculating category boost", e)
            return 0f
        }
    }

    /**
     * Calculates content-based boost with category fallback to color similarity.
     * 
     * **Strategy:**
     * - If wallpaper has category: use category feedback boost (15% weight)
     * - If wallpaper has no category: use color similarity boost (10% weight)
     * 
     * This ensures personalization works even for uncategorized wallpapers by
     * using color palette matching as a fallback mechanism.
     * 
     * **Color Similarity:**
     * - Extract top 3 colors from wallpaper palette
     * - Compare with user's liked colors using RGB Euclidean distance
     * - Apply 10% weight (lower than category to reflect lower confidence)
     * 
     * @param wallpaper Wallpaper to calculate boost for
     * @return Boost value from -0.15 to +0.15 (category) or -0.10 to +0.10 (color)
     */
    private suspend fun getContentBoost(wallpaper: WallpaperMetadata): Float {
        // STRATEGY 1: Use category boost if category exists
        if (wallpaper.category.isNotBlank()) {
            return getCategoryBoost(wallpaper.category)
        }
        
        // STRATEGY 2: Fallback to color similarity boost
        return getColorBoost(wallpaper.colors)
    }
    
    /**
     * Calculates advanced color preference boost using ColorAnalyzer.
     * 
     * ENHANCED STRATEGY (using ColorAnalyzer):
     * - Analyzes wallpaper colors in HSV color space (perceptual matching)
     * - Detects color harmony (monochromatic, analogous, complementary, triadic)
     * - Classifies warm/cool tones and vibrant/muted characteristics
     * - Compares with learned color preferences from liked wallpapers
     * 
     * Uses user's color preferences built from liked/disliked wallpapers:
     * - Liked colors get positive boost (up to +0.12)
     * - Disliked colors get negative penalty (down to -0.12)
     * - New/neutral colors get no adjustment (0.0)
     * 
     * Formula: colorPreferenceScore × 0.12
     * Where colorPreferenceScore comes from ColorAnalyzer's similarity calculation
     * 
     * This is weighted slightly higher (12%) than basic color matching (10%) to reflect
     * the improved accuracy of perceptual color space analysis.
     * 
     * @param colors List of hex color codes from wallpaper palette
     * @return Boost value from -0.12 (disliked colors) to +0.12 (liked colors)
     */
    private suspend fun getColorBoost(colors: List<String>): Float {
        return try {
            if (colors.isEmpty()) {
                return 0f
            }
            
            // Get user's liked colors for learning color preferences
            val likedColors = colorPreferenceRepository.getLikedColors()
            if (likedColors.isEmpty()) {
                // No color preference data yet
                return 0f
            }
            
            // Analyze the liked palette
            val likedPalette = colorAnalyzer.analyzePalette(likedColors)
            
            // Extract color preferences from liked wallpapers using advanced analysis
            val colorPreferences = colorAnalyzer.extractColorPreferences(
                likedPalettes = listOf(likedPalette),
                dislikedPalettes = emptyList()
            )
            
            // Analyze current wallpaper colors
            val wallpaperPalette = colorAnalyzer.analyzePalette(colors)
            
            // Calculate preference score for this wallpaper's colors
            val preferenceScore = colorAnalyzer.calculateColorPreferenceScore(
                palette = wallpaperPalette,
                preferences = colorPreferences
            )
            
            // Apply 12% weight to advanced color analysis
            // Higher weight than basic RGB matching (10%) due to better accuracy
            val boost = preferenceScore * 0.12f
            
            // NOTE: Logging removed - was causing log spam for every wallpaper
            
            return boost
        } catch (e: Exception) {
            android.util.Log.e("SelectNextWallpaper", "Error calculating advanced color boost", e)
            return 0f
        }
    }
    
    /**
     * Calculates composition preference boost using CompositionAnalyzer.
     * 
     * ADVANCED COMPOSITION ANALYSIS:
     * - Analyzes wallpaper layout using 3x3 grid (rule of thirds)
     * - Calculates symmetry (horizontal, vertical)
     * - Measures center weight vs edge density
     * - Evaluates complexity (busy vs simple)
     * - Compares with learned composition preferences
     * 
     * Uses user's composition preferences built from liked wallpapers:
     * - Preferred compositions get positive boost (up to +0.08)
     * - Disliked compositions get negative penalty (down to -0.08)
     * - Neutral compositions get no adjustment (0.0)
     * 
     * Formula: compositionSimilarity × 0.08
     * Where compositionSimilarity compares wallpaper with learned preferences
     * 
     * Weight is 8% to balance with color (12%) and category (15%) boosts.
     * Total personalization signal: 35% (category/color + composition + embedding)
     * 
     * @param wallpaperId Wallpaper ID to analyze
     * @return Boost value from -0.08 (disliked composition) to +0.08 (liked composition)
     */
    private suspend fun getCompositionBoost(wallpaperId: String): Float {
        return try {
            // Get user's composition preferences
            val preferences = compositionPreferenceRepository.getCompositionPreferencesOnce()
            if (preferences == null || preferences.sampleCount == 0) {
                // No composition preference data yet
                return 0f
            }
            
            // Get wallpaper file path
            val wallpaperFile = java.io.File(context.filesDir, "wallpapers/$wallpaperId.jpg")
            if (!wallpaperFile.exists()) {
                android.util.Log.w("SelectNextWallpaper", "Wallpaper file not found for composition analysis: $wallpaperId")
                return 0f
            }
            
            // Analyze composition
            val composition = compositionAnalyzer.analyzeComposition(wallpaperFile)
                ?: return 0f
            
            // Build a preference composition for comparison
            val preferenceComposition = me.avinas.vanderwaals.algorithm.CompositionAnalysis(
                symmetryScore = preferences.averageSymmetry,
                ruleOfThirdsScore = preferences.averageRuleOfThirds,
                centerWeight = preferences.averageCenterWeight,
                edgeDensity = preferences.averageEdgeDensity,
                complexity = preferences.averageComplexity,
                contrastDistribution = 0.5f,
                brightnessMap = emptyList()
            )
            
            // Calculate similarity between this composition and learned preferences
            val similarity = compositionAnalyzer.calculateCompositionSimilarity(
                comp1 = composition,
                comp2 = preferenceComposition
            )
            
            // Convert similarity (0-1) to preference score (-1 to +1)
            // similarity=1.0 (perfect match) → score=+1.0
            // similarity=0.5 (neutral) → score=0.0
            // similarity=0.0 (opposite) → score=-1.0
            val preferenceScore = (similarity - 0.5f) * 2f
            
            // Apply confidence weighting based on sample count
            val confidence = preferences.calculateConfidence()
            val weightedScore = preferenceScore * confidence
            
            // Apply 8% weight to composition similarity
            val boost = weightedScore * 0.08f
            
            // NOTE: Logging removed - was causing log spam for every wallpaper
            
            return boost
        } catch (e: Exception) {
            android.util.Log.e("SelectNextWallpaper", "Error calculating composition boost for $wallpaperId", e)
            return 0f
        }
    }
    
    /**
     * Parses hex color string to RGB integer.
     * 
     * @param hex Hex color string (e.g., "#FF5733" or "FF5733")
     * @return RGB integer or null if parsing fails
     */
    private fun parseHexToColor(hex: String): Int? {
        return try {
            val cleanHex = hex.removePrefix("#")
            android.graphics.Color.parseColor("#$cleanHex")
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Calculates color similarity using RGB Euclidean distance.
     * 
     * @param wallpaperColors List of RGB integers for wallpaper
     * @param preferredColors List of RGB integers for user preferences
     * @return Similarity score from 0.0 (completely different) to 1.0 (identical)
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
        
        wallpaperColors.forEach { wColor ->
            preferredColors.forEach { pColor ->
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
     * Calculates Euclidean distance between two RGB colors.
     * 
     * @param color1 First RGB color as integer
     * @param color2 Second RGB color as integer
     * @return Distance value (0 = identical, higher = more different)
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
     * Gets categories from recent wallpaper history.
     * Used for diversity enforcement.
     * 
     * @param recentHistory Recent wallpaper IDs
     * @param allWallpapers All available wallpapers
     * @return Set of categories from recent history
     */
    private fun getRecentCategories(
        recentHistory: List<String>,
        allWallpapers: List<WallpaperMetadata>
    ): Set<String> {
        val wallpaperMap = allWallpapers.associateBy { it.id }
        return recentHistory
            .take(3) // Only look at last 3 wallpapers
            .mapNotNull { wallpaperMap[it]?.category }
            .toSet()
    }
    
    /**
     * Select diverse wallpapers for cold start (before user provides first like).
     * 
     * COLD START STRATEGY - Used by Auto Mode initially:
     * - Shows diverse, high-quality wallpapers to help user discover preferences
     * - Once user likes first wallpaper, preference vector is created
     * - Then switches to similarity-based scoring (same as Personalize Mode)
     * 
     * IMPORTANT: This is TEMPORARY state for Auto Mode only!
     * After first like, Auto Mode uses exact same algorithm as Personalize Mode.
     * 
     * Scoring based on universal quality signals:
     * - Resolution (higher = better)
     * - Source (Bing curated > GitHub community)
     * - Contrast/brightness balance (moderate = better)
     * - Category hints when available (nature/aesthetic > anime/gaming)
     */
    private fun selectDiverseWallpapers(
        wallpapers: List<WallpaperMetadata>,
        deviceSeed: Int
    ): List<RankedWallpaper> {
        // Score all wallpapers using universal quality metrics
        return wallpapers.map { wallpaper ->
            val score = calculatePopularityScore(
                wallpaper = wallpaper,
                deviceSeed = deviceSeed,
                position = 0  // Position not used
            )
            RankedWallpaper(wallpaper, score)
        }.sortedByDescending { it.similarity }
    }

    /**
     * Calculates universal quality score for cold start (before first like).
     * 
     * **UNIVERSAL APPROACH - Works with any wallpaper collection:**
     * Problem: Can't rely on specific repo information or perfect categorization
     * Solution: Use ONLY universal quality signals available in every wallpaper
     * 
     * **Available fields in WallpaperMetadata:**
     * - resolution: String (e.g., "1920x1080")
     * - brightness: Int (0-100 scale, stored as percentage)
     * - contrast: Int (0-100 scale, stored as percentage)
     * - category: String (may be "other" or unreliable across repos)
     * - source: String ("github" or "bing")
     * 
     * **Scoring Components:**
     * 1. Source Base (0.4-0.75): Bing (curated professional) significantly higher than GitHub
     * 2. Resolution (0.0-0.15): Higher resolution = better quality
     * 3. Balance (0.0-0.08): Moderate contrast/brightness = better
     * 4. Category Hint (0.0-0.07): Use as hint if available, neutral if "other"
     * 5. Device Variation (0.0-0.1): Unique ordering per device
     * 
     * Total range: 0.4-1.05
     * 
     * **VOLUME BALANCING:**
     * GitHub has 6000+ wallpapers vs Bing's ~1500. To ensure quality balance:
     * - Bing base score: 0.75 (professional curation, UHD quality, daily featured)
     * - GitHub base score: 0.40 (community collections, variable quality)
     * This 0.35 difference ensures Bing wallpapers appear prominently despite lower volume.
     * 
     * This ensures cold start shows quality wallpapers regardless of:
     * - Which repos are included in manifest
     * - How categories are assigned
     * - Position in manifest
     * 
     * @param wallpaper The wallpaper to score
     * @param deviceSeed Device-specific random seed
     * @param position Not used (kept for interface compatibility)
     * @return Popularity score (0.4 to 1.05)
     */
    private fun calculatePopularityScore(
        wallpaper: WallpaperMetadata,
        deviceSeed: Int,
        position: Int
    ): Float {
        // 1. SOURCE BASE (0.4-0.75): Source quality with volume balancing
        // CRITICAL FIX: Bing gets much higher base to overcome GitHub's 4x volume advantage
        val sourceBase = when (wallpaper.source.lowercase()) {
            "bing" -> 0.75f  // Professionally curated, UHD quality, daily featured photography
            else -> 0.40f     // GitHub collections (community curated, variable quality)
        }

        // 2. QUALITY SIGNALS (0.0-0.3): Wallpaper-specific quality
        val qualityScore = calculateQualityScore(wallpaper)

        // 3. DEVICE VARIATION (0.0-0.1): Deterministic randomness
        val deviceVariation = ((deviceSeed + wallpaper.id.hashCode()).toLong() % 100) / 1000f

        return sourceBase + qualityScore + deviceVariation
    }

    /**
     * Calculate quality score based on wallpaper-specific attributes.
     * 
     * ENHANCED QUALITY METRICS (0.0-0.3 range):
     * - Resolution: Higher is better
     * - Aspect ratio: Prefer portrait/square for mobile
     * - Balance: Moderate brightness/contrast is better
     * - Color diversity: Rich color palettes score higher
     * - Category: Use as hint when meaningful
     * - Aesthetic composition: Rule of thirds, symmetry hints
     * 
     * Works regardless of source or categorization scheme.
     */
    private fun calculateQualityScore(wallpaper: WallpaperMetadata): Float {
        var score = 0f

        // 1. Resolution bonus (0.0-0.10): Higher resolution = better quality
        val resolutionParts = wallpaper.resolution.split("x")
        if (resolutionParts.size == 2) {
            val width = resolutionParts[0].toIntOrNull() ?: 0
            val height = resolutionParts[1].toIntOrNull() ?: 0
            val pixels = width * height
            
            score += when {
                pixels >= 3840 * 2160 -> 0.10f  // 4K or higher
                pixels >= 2560 * 1440 -> 0.08f  // QHD
                pixels >= 1920 * 1080 -> 0.06f  // Full HD
                pixels >= 1280 * 720 -> 0.04f   // HD
                else -> 0.02f                    // Lower resolution
            }
            
            // 2. Aspect ratio bonus (0.0-0.03): Prefer portrait/square for mobile
            if (width > 0 && height > 0) {
                val aspectRatio = height.toFloat() / width.toFloat()
                score += when {
                    aspectRatio >= 1.5f && aspectRatio <= 2.2f -> 0.03f  // Good portrait (9:16 to 10:16)
                    aspectRatio >= 0.9f && aspectRatio <= 1.1f -> 0.02f  // Square-ish
                    else -> 0.01f                                          // Landscape or unusual
                }
            }
        }

        // 3. Contrast/Brightness balance (0.0-0.06): Well-balanced = better
        // Values stored as Int 0-100, convert to 0.0-1.0 range
        // Prefer moderate values (30-70) - not too dark, not too bright
        val contrastNormalized = wallpaper.contrast / 100f
        val brightnessNormalized = wallpaper.brightness / 100f
        
        val contrastBalance = 1f - kotlin.math.abs(contrastNormalized - 0.5f) * 2f
        val brightnessBalance = 1f - kotlin.math.abs(brightnessNormalized - 0.5f) * 2f
        score += (contrastBalance + brightnessBalance) * 0.03f

        // 4. Color diversity bonus (0.0-0.04): Rich color palettes = better
        val colorCount = wallpaper.colors.size
        score += when {
            colorCount >= 5 -> 0.04f  // Rich palette (5+ colors)
            colorCount >= 3 -> 0.03f  // Moderate palette
            colorCount >= 2 -> 0.02f  // Basic palette
            else -> 0.01f              // Monochrome
        }

        // 5. Category hint bonus (0.0-0.07): Use when available, ignore when "other"
        // Universal appeal categories get bonus, niche get penalty
        score += when (wallpaper.category.lowercase()) {
            // Universal appeal - most people like these
            "nature", "aesthetic", "minimal", "space", "landscape" -> 0.07f
            
            // Broad appeal - many people like these
            "abstract", "dark", "city", "gradient", "architecture" -> 0.05f
            
            // Moderate appeal
            "art", "design", "pattern", "texture" -> 0.03f
            
            // Niche appeal - specific audiences only
            "anime", "gaming", "nord", "gruvbox", "cartoon" -> -0.02f
            
            // Unknown/other - neutral (don't penalize repos with poor categorization)
            "other", "" -> 0.0f
            else -> 0.0f
        }

        return score.coerceIn(0f, 0.3f)
    }
    
    /**
     * Internal data class for pairing wallpapers with similarity scores.
     * Implements ScoredItem for ExplorationStrategy integration.
     */
    private data class RankedWallpaper(
        val wallpaper: WallpaperMetadata,
        val similarity: Float
    ) : me.avinas.vanderwaals.algorithm.ExplorationStrategy.ScoredItem {
        override val score: Float get() = similarity
        override val category: String get() = wallpaper.category
        
        override fun withAdjustedScore(newScore: Float): me.avinas.vanderwaals.algorithm.ExplorationStrategy.ScoredItem {
            return copy(similarity = newScore)
        }
    }
    
    companion object {
        /**
         * Base number of recent wallpapers to remember (to avoid repeats).
         * This is dynamically adjusted based on change frequency.
         */
        private const val BASE_RECENT_HISTORY_SIZE = 10
        
        /**
         * Maximum candidates to consider for epsilon-greedy selection.
         */
        private const val MAX_EXPLORATION_POOL = 100
        
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
     * Selects the next wallpaper after a user dislike with enhanced diversity.
     * 
     * **Key Differences from Regular Selection:**
     * 1. **Category Exclusion**: Strongly avoids wallpapers from the disliked category
     * 2. **High Exploration**: Uses 70% exploration rate (vs. 10% normal)
     * 3. **Dissimilarity Boost**: Prefers wallpapers that are DIFFERENT from the disliked one
     * 
     * **Why This Matters:**
     * When a user dislikes a wallpaper, they're signaling "show me something different."
     * Regular selection just updates preferences slightly and picks the next best match,
     * which often feels too similar. This method ensures a noticeable change.
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
            android.util.Log.d("SelectNextWallpaper", "=== POST-DISLIKE SELECTION ===")
            android.util.Log.d("SelectNextWallpaper", "Disliked wallpaper: $dislikedWallpaperId (category: $dislikedCategory)")
            
            // Step 1: Get settings and preferences
            val settings = settingsDataStore.settings.first()
            val preferences = preferenceRepository.getUserPreferencesOnce()
            
            // Step 2: Get downloaded wallpapers
            val allDownloadedWallpapers = wallpaperRepository.getDownloadedWallpapers().first()
            
            if (allDownloadedWallpapers.isEmpty()) {
                return Result.failure(IllegalStateException("No wallpapers available"))
            }
            
            // Step 3: Filter by enabled sources
            val enabledSources = mutableSetOf<String>()
            if (settings.githubEnabled) enabledSources.add("github")
            if (settings.bingEnabled) enabledSources.add("bing")
            if (enabledSources.isEmpty()) enabledSources.add("github")
            
            val downloadedWallpapers = allDownloadedWallpapers.filter { wallpaper ->
                wallpaper.source in enabledSources && wallpaper.id != dislikedWallpaperId
            }
            
            if (downloadedWallpapers.isEmpty()) {
                return Result.failure(IllegalStateException("No alternative wallpapers available"))
            }
            
            // Step 4: Get recent history to avoid immediate repeats
            val recentHistoryList = wallpaperRepository.getHistory().first()
            val recentHistory = recentHistoryList.take(10).map { it.wallpaperId }.toSet()
            
            // Step 5: Filter out recently shown wallpapers and the disliked one
            val availableWallpapers = downloadedWallpapers.filter { wallpaper ->
                wallpaper.id !in recentHistory && wallpaper.id != dislikedWallpaperId
            }.ifEmpty { downloadedWallpapers.filter { it.id != dislikedWallpaperId } }
            
            // CRITICAL: Separate wallpapers into different-category and same-category groups
            val differentCategoryWallpapers = availableWallpapers.filter { 
                it.category.isNotBlank() && it.category != dislikedCategory 
            }
            val sameCategoryWallpapers = availableWallpapers.filter { 
                it.category == dislikedCategory || it.category.isBlank()
            }
            
            android.util.Log.d("SelectNextWallpaper", "Available: ${availableWallpapers.size} total, " +
                "${differentCategoryWallpapers.size} different-category, " +
                "${sameCategoryWallpapers.size} same-category")
            
            // Step 6: Score wallpapers with DISSIMILARITY boost
            // For post-dislike, we want wallpapers that are DIFFERENT from the disliked one
            val scoredWallpapers = availableWallpapers.map { wallpaper ->
                // Calculate similarity to disliked wallpaper (we want LOW similarity)
                val similarityToDisliked = if (dislikedEmbedding.isNotEmpty() && wallpaper.embedding.isNotEmpty()) {
                    similarityCalculator.calculateSimilarity(dislikedEmbedding, wallpaper.embedding)
                } else {
                    0.5f // Neutral if embeddings unavailable
                }
                
                // DISSIMILARITY SCORE: Invert similarity (1.0 - similarity) 
                // Low similarity to disliked = high dissimilarity score
                val dissimilarityScore = 1.0f - similarityToDisliked
                
                // CATEGORY DIVERSITY BONUS: Strong bonus for different category
                val categoryBonus = if (wallpaper.category.isNotBlank() && 
                                        wallpaper.category != dislikedCategory) {
                    0.3f  // 30% bonus for different category
                } else {
                    0f
                }
                
                // PREFERENCE ALIGNMENT: Still respect user's overall preferences (but reduced weight)
                val preferenceScore = if (preferences?.preferenceVector?.isNotEmpty() == true && 
                                          wallpaper.embedding.isNotEmpty()) {
                    similarityCalculator.calculateSimilarity(
                        preferences.preferenceVector, 
                        wallpaper.embedding
                    ) * 0.3f  // Only 30% weight to preferences (normally 70%)
                } else {
                    0f
                }
                
                // COMPOSITE SCORE: Dissimilarity (50%) + Category Bonus (30%) + Preferences (20%)
                val compositeScore = (dissimilarityScore * 0.5f) + categoryBonus + preferenceScore
                
                RankedWallpaper(wallpaper, compositeScore)
            }.sortedByDescending { it.similarity }
            
            // Log top candidates for debugging
            scoredWallpapers.take(5).forEachIndexed { index, ranked ->
                android.util.Log.d("SelectNextWallpaper", 
                    "Post-dislike candidate ${index + 1}: ${ranked.wallpaper.id} " +
                    "(category: ${ranked.wallpaper.category}, score: ${String.format("%.3f", ranked.similarity)})")
            }
            
            // Step 7: Select with HIGH exploration rate (70% vs normal 10%)
            // This ensures we often pick something unexpected
            val seededRandom = createSeededRandom()
            val explorationRate = 0.7f  // 70% exploration for post-dislike
            
            val selectedWallpaper = if (seededRandom.nextFloat() < explorationRate) {
                // EXPLORATION: Pick from different-category wallpapers if available
                if (differentCategoryWallpapers.isNotEmpty()) {
                    val differentCategoryScored = scoredWallpapers.filter { ranked ->
                        ranked.wallpaper.category.isNotBlank() && 
                        ranked.wallpaper.category != dislikedCategory
                    }
                    if (differentCategoryScored.isNotEmpty()) {
                        // Pick from top 20 different-category options
                        val pool = differentCategoryScored.take(20)
                        pool.random(seededRandom).wallpaper
                    } else {
                        scoredWallpapers.take(20).random(seededRandom).wallpaper
                    }
                } else {
                    // No different-category wallpapers, pick from top scored
                    scoredWallpapers.take(20).random(seededRandom).wallpaper
                }
            } else {
                // EXPLOITATION: Best dissimilarity score (preferring different category)
                val bestDifferentCategory = scoredWallpapers.firstOrNull { ranked ->
                    ranked.wallpaper.category.isNotBlank() && 
                    ranked.wallpaper.category != dislikedCategory
                }
                bestDifferentCategory?.wallpaper ?: scoredWallpapers.first().wallpaper
            }
            
            android.util.Log.d("SelectNextWallpaper", 
                "Post-dislike selected: ${selectedWallpaper.id} (category: ${selectedWallpaper.category})")
            android.util.Log.d("SelectNextWallpaper", "=== END POST-DISLIKE SELECTION ===")
            
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
