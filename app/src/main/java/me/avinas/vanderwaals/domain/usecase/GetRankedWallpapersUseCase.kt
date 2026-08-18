package me.avinas.vanderwaals.domain.usecase

import kotlinx.coroutines.flow.first
import me.avinas.vanderwaals.algorithm.RankingEngine
import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import me.avinas.vanderwaals.data.repository.CategoryPreferenceRepository
import me.avinas.vanderwaals.data.repository.PreferenceRepository
import me.avinas.vanderwaals.data.repository.TasteAnchorRepository
import me.avinas.vanderwaals.data.repository.WallpaperRepository
import javax.inject.Inject
import javax.inject.Singleton
import java.util.Calendar

/**
 * Returns the top-N ranked wallpapers for queue pre-fill and manual
 * refresh, using the same [RankingEngine] path as automatic selection so
 * every surface shows one consistent ordering.
 */
@Singleton
class GetRankedWallpapersUseCase @Inject constructor(
    private val wallpaperRepository: WallpaperRepository,
    private val preferenceRepository: PreferenceRepository,
    private val categoryPreferenceRepository: CategoryPreferenceRepository,
    private val tasteAnchorRepository: TasteAnchorRepository,
    private val rankingEngine: RankingEngine
) {

    suspend operator fun invoke(limit: Int = 50): Result<List<WallpaperMetadata>> {
        return try {
            val preferences = preferenceRepository.getUserPreferences().first()
                ?: return Result.failure(Exception("User preferences not initialized"))

            val allWallpapers = wallpaperRepository.getAllWallpapers().first()
            if (allWallpapers.isEmpty()) {
                return Result.failure(Exception("No wallpapers available in catalog"))
            }

            val recentIds = wallpaperRepository.getHistory().first()
                .take(10)
                .map { it.wallpaperId }
                .toSet()

            val candidates = allWallpapers.filterNot { it.id in recentIds }.ifEmpty { allWallpapers }

            val now = System.currentTimeMillis()
            val tasteMemory = tasteAnchorRepository.getTasteMemory(now)

            if (!tasteMemory.hasTaste && preferences.feedbackCount == 0) {
                // Cold start (brand-new user): objective quality ordering.
                // Users with feedback — even without client-side embeddings —
                // go through the engine so their category/semantic signals
                // still personalise the list.
                val deviceSeed = "ranked".hashCode()
                val ranked = candidates
                    .map { it to rankingEngine.coldStartScore(it, deviceSeed) }
                    .sortedByDescending { it.second }
                    .take(limit)
                    .map { it.first }
                return Result.success(ranked)
            }

            // Lookup map only for ids actually needed downstream (recent
            // window + liked palette) — a full catalog associateBy builds a
            // 3.7k-entry map per call for a few dozen lookups.
            val neededIds = HashSet<String>(recentIds.size + preferences.likedWallpaperIds.size)
            neededIds.addAll(recentIds)
            neededIds.addAll(preferences.likedWallpaperIds)
            val wallpaperById = HashMap<String, WallpaperMetadata>(neededIds.size)
            for (wallpaper in allWallpapers) {
                if (wallpaper.id in neededIds) wallpaperById[wallpaper.id] = wallpaper
            }
            val categoryRows = categoryPreferenceRepository.getAllCategoryPreferences().first()
            val categoryStats = categoryRows.associate { row ->
                row.category to RankingEngine.CategoryStats(
                    likes = row.likes,
                    dislikes = row.dislikes,
                    views = row.views
                )
            }

            val context = RankingEngine.RankingContext(
                nowMillis = now,
                currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
                tasteMemory = tasteMemory,
                categoryStats = categoryStats,
                recentIds = recentIds.toList(),
                recentCategories = recentIds.mapNotNull { wallpaperById[it]?.category },
                moodAffinity = preferences.moodAffinity,
                styleAffinity = preferences.styleAffinity,
                likedColors = preferences.likedWallpaperIds
                    .mapNotNull { wallpaperById[it]?.colors }
                    .flatten()
                    .take(24)
            )

            Result.success(
                rankingEngine.rank(candidates, context)
                    .take(limit)
                    .map { it.wallpaper }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
