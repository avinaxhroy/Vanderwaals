package me.avinas.vanderwaals.domain.usecase

import android.util.Log
import kotlinx.coroutines.flow.first
import me.avinas.vanderwaals.data.datastore.SettingsDataStore
import me.avinas.vanderwaals.data.repository.BingManifestRepository
import me.avinas.vanderwaals.data.repository.ManifestRepository
import me.avinas.vanderwaals.data.repository.VanderwaalsCollectionRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Syncs the wallpaper catalog from the enabled sources (GitHub manifest,
 * Bing, Vanderwaals Collection).
 *
 * The manifest carries precomputed data from the GitHub Actions curation
 * pipeline: download URLs, MobileNetV4 embeddings (1280 floats per
 * wallpaper), color palettes, categories, brightness, and source
 * attribution; sync inserts/updates these in Room.
 *
 * Triggered on app launch (> 7 days since last sync), via the manual
 * "Sync Now" button in settings, and by a WorkManager periodic worker.
 */
@Singleton
class SyncWallpaperCatalogUseCase @Inject constructor(
    private val manifestRepository: ManifestRepository,
    private val bingManifestRepository: BingManifestRepository,
    private val vanderwaalsCollectionRepository: VanderwaalsCollectionRepository,
    private val settingsDataStore: SettingsDataStore
) {
    
    companion object {
        private const val TAG = "SyncWallpaperCatalog"
    }
    
    suspend fun syncCatalog(
        onProgress: ((message: String, progress: Float, count: Int) -> Unit)? = null
    ): Result<Int> {
        return try {
            Log.d(TAG, "Starting catalog sync...")
            onProgress?.invoke("Starting sync...", 0.05f, 0)
            
            val settings = settingsDataStore.settings.first()
            val githubEnabled = settings.githubEnabled
            val bingEnabled = settings.bingEnabled
            val bingManifestType = settings.bingManifestType
            val vanderwaalsCollectionEnabled = settings.vanderwaalsCollectionEnabled
            val vanderwaalsCollectionManifestType = settings.vanderwaalsCollectionManifestType

            Log.d(TAG, "Sources enabled - GitHub: $githubEnabled, Bing: $bingEnabled, Vanderwaals Collection: $vanderwaalsCollectionEnabled")

            var totalCount = 0

            // Progress ranges: split evenly between manifest sources only
            val enabledCount = listOf(githubEnabled, bingEnabled, vanderwaalsCollectionEnabled).count { it }
            val sliceSize = if (enabledCount > 0) 1f / enabledCount else 1f
            var sliceIndex = 0

            fun nextRange(): Pair<Float, Float> {
                val start = sliceIndex * sliceSize
                val end = start + sliceSize
                sliceIndex++
                return start to end
            }
            
            if (githubEnabled) {
                Log.d(TAG, "Syncing GitHub manifest...")
                val range = nextRange()
                
                manifestRepository.syncManifest(
                    onProgress = { message, progress, count ->
                        val scaledProgress = range.first + (progress * (range.second - range.first))
                        onProgress?.invoke("Community: $message", scaledProgress, count)
                    }
                ).fold(
                    onSuccess = { count ->
                        totalCount += count
                        Log.d(TAG, "GitHub sync successful: $count wallpapers")
                    },
                    onFailure = { error ->
                        Log.e(TAG, "GitHub sync failed: ${error.message}", error)
                    }
                )
            }
            
            if (bingEnabled) {
                Log.d(TAG, "Syncing Bing manifest ($bingManifestType)...")
                val range = nextRange()
                
                bingManifestRepository.syncBingManifest(
                    manifestType = bingManifestType,
                    onProgress = { message, progress, count ->
                        val scaledProgress = range.first + (progress * (range.second - range.first))
                        onProgress?.invoke("Bing: $message", scaledProgress, totalCount + count)
                    }
                ).fold(
                    onSuccess = { count ->
                        totalCount += count
                        Log.d(TAG, "Bing sync successful: $count wallpapers")
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Bing sync failed: ${error.message}", error)
                    }
                )
            }

            if (vanderwaalsCollectionEnabled) {
                Log.d(TAG, "Syncing Vanderwaals Collection manifest ($vanderwaalsCollectionManifestType)...")
                val range = nextRange()

                vanderwaalsCollectionRepository.syncVanderwaalsCollectionManifest(
                    manifestType = vanderwaalsCollectionManifestType,
                    onProgress = { message, progress, count ->
                        val scaledProgress = range.first + (progress * (range.second - range.first))
                        onProgress?.invoke("Vanderwaals: $message", scaledProgress, totalCount + count)
                    }
                ).fold(
                    onSuccess = { count ->
                        totalCount += count
                        Log.d(TAG, "Vanderwaals Collection sync successful: $count wallpapers")
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Vanderwaals Collection sync failed: ${error.message}", error)
                    }
                )
            }

            if (totalCount == 0 && (githubEnabled || bingEnabled || vanderwaalsCollectionEnabled)) {
                Result.failure(Exception("No wallpapers synced from any source"))
            } else {
                Log.d(TAG, "Sync complete: $totalCount total wallpapers")
                onProgress?.invoke("Sync complete!", 1f, totalCount)
                Result.success(totalCount)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed with exception", e)
            Result.failure(e)
        }
    }
}

