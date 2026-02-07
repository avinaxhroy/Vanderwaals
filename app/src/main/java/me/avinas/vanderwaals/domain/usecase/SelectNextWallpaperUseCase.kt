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
    private val wallpaperScorer: me.avinas.vanderwaals.algorithm.WallpaperScorer,
    private val settingsDataStore: me.avinas.vanderwaals.data.datastore.SettingsDataStore,
    private val dailyPlaylistManager: me.avinas.vanderwaals.data.repository.DailyPlaylistManager,
    private val wallpaperHistoryDao: WallpaperHistoryDao,
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) {
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
                    // Get the wallpaper metadata for this ID from database
                    val allWallpapersForPlaylist = wallpaperRepository.getAllWallpapers().first()
                    val match = allWallpapersForPlaylist.find { it.id == nextId }
                    
                    if (match != null) {
                        android.util.Log.d("SelectNextWallpaper", "Selected from Daily Playlist: ${match.id}")
                        return Result.success(match)
                    } else {
                         android.util.Log.w("SelectNextWallpaper", "Playlist item $nextId not found in database")
                         // Fallback to normal selection if not found
                    }
                } else {
                    android.util.Log.d("SelectNextWallpaper", "Daily Playlist empty or not initialized")
                }
            }
            
            // Step 3: Get ALL wallpapers from database (not just downloaded ones)
            // NEW ARCHITECTURE: Select best wallpaper from entire catalog, download on-demand
            // This eliminates the "no downloaded wallpapers" issue completely
            val allWallpapers = wallpaperRepository.getAllWallpapers().first()
            
            android.util.Log.d("SelectNextWallpaper", "Total wallpapers in catalog: ${allWallpapers.size}")
            
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
            
            // Default to GitHub if no sources enabled
            if (enabledSources.isEmpty()) {
                enabledSources.add("github")
            }
            
            android.util.Log.d("SelectNextWallpaper", "Enabled sources: $enabledSources")
            
            // Filter wallpapers by source and exclude current wallpaper
            val filteredWallpapers = allWallpapers.filter { wallpaper ->
                wallpaper.source.lowercase() in enabledSources && wallpaper.id != effectiveExcludeId
            }
            
            android.util.Log.d("SelectNextWallpaper", "Filtered wallpapers (by source, excluding current): ${filteredWallpapers.size}")
            
            if (filteredWallpapers.isEmpty()) {
                // Edge case: Only 1 wallpaper in DB and it's excluded, or source mismatch
                val sourcesInDb = allWallpapers.map { it.source.lowercase() }.distinct()
                android.util.Log.e("SelectNextWallpaper", "No wallpapers after filtering. Sources in DB: $sourcesInDb, enabled: $enabledSources")
                return Result.failure(
                    IllegalStateException("No wallpapers available for selected sources (${enabledSources.joinToString()})")
                )
            }
            
            // Use filtered wallpapers as candidates
            val downloadedWallpapers = filteredWallpapers
            
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
                        val categoryScore = wallpaperScorer.getContentBoost(wallpaper)
                        
                        // COMPOSITION BOOST: Advanced layout/composition preference matching
                        val compositionScore = wallpaperScorer.getCompositionBoost(wallpaper.id)
                        
                        // TEMPORAL DIVERSITY BOOST: Prevent repetition, explore new categories
                        val diversityBoost = wallpaperScorer.getTemporalDiversityBoost(
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
    
    // ========================================
    // SCORING METHODS EXTRACTED TO WallpaperScorer
    // ========================================
    // The following methods have been moved to me.avinas.vanderwaals.algorithm.WallpaperScorer:
    // - getContentBoost(), getCategoryBoost(), getColorBoost(), getCompositionBoost()
    // - getTemporalDiversityBoost()
    // - calculatePopularityScore(), calculateQualityScore()
    // - colorDistance(), parseHexToColor(), calculateColorSimilarity()
    //
    // This improves:
    // - Testability: Scoring logic can be unit tested independently
    // - Reusability: Other components (search, similar wallpapers) can reuse scoring
    // - Maintainability: ~500 lines removed from this already large file
    // ========================================
    
    /**
     * Selects wallpaper for exploitation phase (best match).
     * Checks category diversity to avoid repetition.
     */
    private fun selectForExploitation(
        rankedWallpapers: List<RankedWallpaper>,
        recentCategories: Set<String>,
        random: Random
    ): WallpaperMetadata {
        if (recentCategories.isNotEmpty()) {
            val topCandidates = rankedWallpapers.take(10)
            val differentCategory = topCandidates
                .firstOrNull { it.wallpaper.category !in recentCategories }
            
            if (differentCategory != null) {
                return differentCategory.wallpaper
            }
        }
        return rankedWallpapers.first().wallpaper
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
            
            // Step 2: Get ALL wallpapers from database (download on-demand)
            val allWallpapers = wallpaperRepository.getAllWallpapers().first()
            
            if (allWallpapers.isEmpty()) {
                return Result.failure(IllegalStateException("No wallpapers in catalog"))
            }
            
            // Step 3: Filter by enabled sources
            val enabledSources = mutableSetOf<String>()
            if (settings.githubEnabled) enabledSources.add("github")
            if (settings.bingEnabled) enabledSources.add("bing")
            if (enabledSources.isEmpty()) enabledSources.add("github")
            
            val downloadedWallpapers = allWallpapers.filter { wallpaper ->
                wallpaper.source.lowercase() in enabledSources && wallpaper.id != dislikedWallpaperId
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
