package me.avinas.vanderwaals.domain.usecase

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import me.avinas.vanderwaals.algorithm.RankingEngine
import me.avinas.vanderwaals.algorithm.TasteMemory
import me.avinas.vanderwaals.data.dao.WallpaperHistoryDao
import me.avinas.vanderwaals.data.entity.UserPreferences
import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import me.avinas.vanderwaals.data.repository.CategoryPreferenceRepository
import me.avinas.vanderwaals.data.repository.PreferenceRepository
import me.avinas.vanderwaals.data.repository.TasteAnchorRepository
import me.avinas.vanderwaals.data.repository.WallpaperRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Selects the next wallpaper to display.
 *
 * Thin orchestrator: gathers context (settings, taste memory, category
 * stats, recent history) and delegates all scoring and selection to
 * [RankingEngine] — the single calibrated ranking path.  Both Auto and
 * Personalize modes use the same flow; they differ only in whether the
 * taste memory already contains anchors.
 *
 * Selection flow:
 * 1. Every-unlock mode is served from the daily playlist when available.
 * 2. Filter catalog by enabled sources; exclude current + recent window.
 * 3. With taste anchors → [RankingEngine.select].
 *    Without → cold start: objective quality with category-stratified
 *    sampling so early feedback has varied data to learn from.
 */
@Singleton
class SelectNextWallpaperUseCase @Inject constructor(
    private val wallpaperRepository: WallpaperRepository,
    private val preferenceRepository: PreferenceRepository,
    private val categoryPreferenceRepository: CategoryPreferenceRepository,
    private val tasteAnchorRepository: TasteAnchorRepository,
    private val rankingEngine: RankingEngine,
    private val settingsDataStore: me.avinas.vanderwaals.data.datastore.SettingsDataStore,
    private val dailyPlaylistManager: me.avinas.vanderwaals.data.repository.DailyPlaylistManager,
    private val wallpaperHistoryDao: WallpaperHistoryDao,
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) {

    /**
     * Device seed resolved once — [android.provider.Settings.Secure] is a
     * content-resolver query and this use case runs on every rotation.
     */
    private val deviceSeed: Int by lazy { readDeviceSpecificSeed() }

    /**
     * Seeded random combining device identity and system entropy so each
     * invocation yields a different rotation sequence per device.
     */
    private fun createSeededRandom(): Random {
        val timeSeed = System.currentTimeMillis()
        val uptimeSeed = android.os.SystemClock.uptimeMillis()
        val noiseSeed = (Math.random() * Int.MAX_VALUE).toLong()
        return Random((deviceSeed.toLong() xor timeSeed xor uptimeSeed xor noiseSeed).toInt())
    }

    /**
     * Selects the next wallpaper to display.
     *
     * Called by the change worker on schedule, by the "Change Now"
     * button, and after onboarding.
     */
    suspend operator fun invoke(excludeWallpaperId: String? = null): Result<WallpaperMetadata> {
        return try {
            val currentActiveWallpaper = wallpaperHistoryDao.getActiveWallpaper()
            val effectiveExcludeId = excludeWallpaperId ?: currentActiveWallpaper?.wallpaperId

            // Preferences row must exist (onboarding race guard, unchanged).
            var preferences = preferenceRepository.getUserPreferencesOnce()
            var retryCount = 0
            while ((preferences == null || preferences.feedbackCount == 0) && retryCount < 5) {
                delay(300L)
                preferences = preferenceRepository.getUserPreferencesOnce()
                retryCount++
            }
            if (preferences == null) {
                val defaultPreferences = UserPreferences.createDefault()
                preferenceRepository.insertUserPreferences(defaultPreferences)
                var savedPreferences: UserPreferences? = null
                var retries = 0
                while (savedPreferences == null && retries < 5) {
                    delay(500L)
                    savedPreferences = preferenceRepository.getUserPreferencesOnce()
                    retries++
                }
                if (savedPreferences == null) {
                    return Result.failure(
                        IllegalStateException("User preferences not initialized and could not be created")
                    )
                }
                preferences = savedPreferences
            }

            val settings = settingsDataStore.settings.first()

            // Every-unlock mode: serve from the pre-computed daily playlist.
            if (settings.changeInterval == "unlock") {
                var nextId = dailyPlaylistManager.getNextWallpaperId()
                if (nextId != null && nextId == effectiveExcludeId) {
                    nextId = dailyPlaylistManager.getNextWallpaperId()
                }
                if (nextId != null) {
                    // By-id lookup — loading the full catalog here would
                    // materialise every embedding (~18 MB) to find one row.
                    val match = wallpaperRepository.getWallpaperById(nextId)
                    if (match != null) {
                        return Result.success(match)
                    }
                    android.util.Log.w("SelectNextWallpaper", "Playlist item $nextId not found in database")
                }
            }

            val allWallpapers = wallpaperRepository.getAllWallpapers().first()
            if (allWallpapers.isEmpty()) {
                return Result.failure(
                    IllegalStateException("No wallpapers in catalog. Please sync the wallpaper catalog first.")
                )
            }

            // Source filtering. Case-insensitive compare against the small
            // enabled set — avoids lowercasing every source string in the
            // catalog on every rotation.
            val enabledSources = mutableSetOf<String>()
            if (settings.githubEnabled) enabledSources.add("github")
            if (settings.bingEnabled) enabledSources.add("bing")
            if (settings.vanderwaalsCollectionEnabled) enabledSources.add("vanderwaals")
            if (enabledSources.isEmpty()) enabledSources.add("github")

            val sourceFiltered = allWallpapers.filter { wallpaper ->
                wallpaper.id != effectiveExcludeId &&
                    enabledSources.any { it.equals(wallpaper.source, ignoreCase = true) }
            }
            if (sourceFiltered.isEmpty()) {
                return Result.failure(
                    IllegalStateException("No wallpapers available for selected sources (${enabledSources.joinToString()})")
                )
            }

            // Recent-history window (dynamic per change frequency).
            val dynamicHistorySize = getHistorySizeForInterval(settings.changeInterval)
            val recentHistoryList = wallpaperRepository.getHistory().first()
            val recentIds = recentHistoryList
                .take(dynamicHistorySize)
                .map { it.wallpaperId }
                .toSet()

            // Lookup map only for ids actually needed downstream (recent
            // window + liked palette) — a full catalog associateBy builds a
            // 3.7k-entry map per selection for a few dozen lookups.
            val neededIds = HashSet<String>(recentIds.size + preferences.likedWallpaperIds.size)
            neededIds.addAll(recentIds)
            neededIds.addAll(preferences.likedWallpaperIds)
            val wallpaperById = HashMap<String, WallpaperMetadata>(neededIds.size)
            for (wallpaper in allWallpapers) {
                if (wallpaper.id in neededIds) wallpaperById[wallpaper.id] = wallpaper
            }
            val recentCategories = recentHistoryList
                .take(dynamicHistorySize)
                .mapNotNull { wallpaperById[it.wallpaperId]?.category }

            val candidates = sourceFiltered
                .filter { it.id !in recentIds }
                .ifEmpty { sourceFiltered }
            if (candidates.isEmpty()) {
                return Result.failure(IllegalStateException("No candidate wallpapers available"))
            }

            val now = System.currentTimeMillis()
            val tasteMemory = tasteAnchorRepository.getTasteMemory(now)

            // Warm path whenever the user has given ANY feedback — including
            // anchors without client-side embeddings (Vanderwaals
            // Collection), where category/semantic/colour components carry
            // the personalisation instead.  Pure cold start is only for
            // brand-new users with zero feedback; routing feedback users
            // there made recommendations effectively random.
            val selectedWallpaper = if (tasteMemory.hasTaste || preferences.feedbackCount > 0) {
                selectWithTaste(candidates, tasteMemory, preferences, recentIds, recentCategories, wallpaperById, now)
            } else {
                selectColdStart(candidates)
            }

            // Record the category view for exploration statistics.
            if (selectedWallpaper.category.isNotBlank()) {
                categoryPreferenceRepository.recordView(selectedWallpaper.category)
            }

            Result.success(selectedWallpaper)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to select next wallpaper: ${e.message}", e))
        }
    }

    /**
     * Warm path: build the [RankingEngine.RankingContext] and let the
     * engine score, diversify and pick.
     */
    private suspend fun selectWithTaste(
        candidates: List<WallpaperMetadata>,
        tasteMemory: TasteMemory,
        preferences: UserPreferences,
        recentIds: Set<String>,
        recentCategories: List<String>,
        wallpaperById: Map<String, WallpaperMetadata>,
        now: Long
    ): WallpaperMetadata {
        val categoryRows = categoryPreferenceRepository.getAllCategoryPreferences().first()
        val categoryStats = categoryRows.associate { row ->
            row.category to RankingEngine.CategoryStats(
                likes = row.likes,
                dislikes = row.dislikes,
                views = row.views
            )
        }

        val likedColors = preferences.likedWallpaperIds
            .mapNotNull { wallpaperById[it]?.colors }
            .flatten()
            .take(24)

        val context = RankingEngine.RankingContext(
            nowMillis = now,
            currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY),
            tasteMemory = tasteMemory,
            categoryStats = categoryStats,
            recentIds = recentIds.toList(),
            recentCategories = recentCategories,
            moodAffinity = preferences.moodAffinity,
            styleAffinity = preferences.styleAffinity,
            likedColors = likedColors
        )

        return try {
            rankingEngine.select(candidates, context, createSeededRandom())
        } catch (e: Exception) {
            android.util.Log.w("SelectNextWallpaper", "Engine selection failed, using top match", e)
            rankingEngine.rank(candidates, context).first().wallpaper
        }
    }

    /**
     * Cold start: no taste anchors yet.  Score by objective quality (no
     * source bias) and sample from a category-stratified pool so the user
     * sees variety while the model collects its first feedback.
     */
    private fun selectColdStart(candidates: List<WallpaperMetadata>): WallpaperMetadata {
        val deviceSeed = this.deviceSeed
        val scored = candidates
            .map { it to rankingEngine.coldStartScore(it, deviceSeed) }
            .sortedByDescending { it.second }

        // Stratify: at most two wallpapers per category within the top 30,
        // then a score-weighted pick from the resulting pool.
        val perCategoryCount = mutableMapOf<String, Int>()
        val pool = mutableListOf<Pair<WallpaperMetadata, Float>>()
        for ((wallpaper, score) in scored) {
            if (pool.size >= 10) break
            val count = perCategoryCount.getOrDefault(wallpaper.category, 0)
            if (count >= 2) continue
            perCategoryCount[wallpaper.category] = count + 1
            pool.add(wallpaper to score)
        }
        if (pool.isEmpty()) return candidates.first()

        val random = createSeededRandom()
        val weights = pool.map { (_, score) -> kotlin.math.exp(score / 0.1f) }
        val total = weights.sum()
        var pick = random.nextFloat() * total
        for (i in pool.indices) {
            pick -= weights[i]
            if (pick <= 0f) return pool[i].first
        }
        return pool.first().first
    }

    /**
     * Selects the next wallpaper after the user just disliked one.
     *
     * The dislike is persisted into the taste memory first, then selection
     * goes through the exact same engine path — the suppression memory and
     * the raised exploration from the fresh dislike anchor handle the rest.
     * There is no separate post-dislike ranking.
     */
    suspend fun selectAfterDislike(
        dislikedWallpaperId: String,
        dislikedCategory: String,
        dislikedEmbedding: FloatArray
    ): Result<WallpaperMetadata> {
        return try {
            tasteAnchorRepository.recordDislike(
                wallpaperId = dislikedWallpaperId,
                embedding = dislikedEmbedding,
                nowMillis = System.currentTimeMillis(),
                strength = 1.0f
            )
            if (dislikedCategory.isNotBlank()) {
                categoryPreferenceRepository.recordDislike(dislikedCategory)
            }
            invoke(excludeWallpaperId = dislikedWallpaperId)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to select wallpaper after dislike: ${e.message}", e))
        }
    }

    /**
     * Device-specific seed so different devices get different sequences.
     */
    private fun readDeviceSpecificSeed(): Int {
        val androidId = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "default_device"
        return androidId.hashCode()
    }

    companion object {
        private const val BASE_RECENT_HISTORY_SIZE = 10

        /**
         * Recent-history window sized so repeats are protected for at least
         * 12–24 h at the configured change frequency.
         */
        fun getHistorySizeForInterval(changeInterval: String): Int {
            return when (changeInterval) {
                "15min" -> 48
                "unlock" -> 40
                "hourly" -> 24
                "3hours" -> 16
                "6hours" -> 12
                "12hours" -> 10
                "daily" -> 14
                "3days" -> 7
                "7days" -> 4
                else -> BASE_RECENT_HISTORY_SIZE
            }
        }
    }
}
