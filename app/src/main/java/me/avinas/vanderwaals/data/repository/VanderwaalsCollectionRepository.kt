package me.avinas.vanderwaals.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import me.avinas.vanderwaals.data.dao.WallpaperMetadataDao
import me.avinas.vanderwaals.data.datastore.SettingsDataStore
import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import me.avinas.vanderwaals.network.VanderwaalsCollectionService
import me.avinas.vanderwaals.network.dto.ManifestDto
import me.avinas.vanderwaals.network.dto.toEntity
import retrofit2.Response
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for syncing the Vanderwaals Collection wallpaper manifest.
 *
 * The Vanderwaals Collection is the app's own curated wallpaper catalog, served
 * from `https://vanderwaalsapi.2626688.xyz/`. It uses the same [ManifestDto]
 * format as the GitHub and Bing sources (MobileNetV4-Conv-Small 1280D
 * quantized embeddings), so it integrates with the existing catalog pipeline.
 *
 * Two manifest variants are available:
 * - **Lite manifest** (`cat/lite.json`): curated subset, recommended for getting started
 * - **Full manifest** (`cat/full.json`): complete Vanderwaals Collection archive
 *
 * Features (mirrors [BingManifestRepository]):
 * - Smart sync with If-Modified-Since headers (304 Not Modified)
 * - Quarterly sync interval (90 days)
 * - Progress callbacks for UI feedback
 * - Retry logic with exponential backoff
 * - Source normalization to [SOURCE_KEY] for consistent filtering
 *
 * @see VanderwaalsCollectionService
 */
@Singleton
class VanderwaalsCollectionRepository @Inject constructor(
    private val vanderwaalsCollectionService: VanderwaalsCollectionService,
    private val wallpaperDao: WallpaperMetadataDao,
    private val settingsDataStore: SettingsDataStore
) {
    companion object {
        private const val TAG = "VanderwaalsCollectionRepo"
        private const val SYNC_INTERVAL_DAYS = 90L  // Quarterly sync
        private const val MAX_RETRIES = 3
        private const val INITIAL_BACKOFF_MS = 1000L
        private const val CHUNK_SIZE = 100  // Insert in chunks for progress

        /**
         * Stable, lowercase source identifier stored in the database.
         *
         * The manifest reports `source = "Vanderwaals Collection"`, but every
         * wallpaper is normalized to this key so it matches the `github`/`bing`
         * convention used throughout the app for source filtering and cleanup.
         */
        const val SOURCE_KEY = "vanderwaals"
    }

    /**
     * Checks if a Vanderwaals Collection sync is needed based on the last sync timestamp.
     *
     * @return true if sync is needed (>90 days since last sync or never synced) and the source is enabled
     */
    suspend fun isSyncNeeded(): Boolean {
        val settings = settingsDataStore.settings.first()

        if (!settings.vanderwaalsCollectionEnabled) return false

        val lastSync = settings.vanderwaalsCollectionLastSyncTimestamp
        if (lastSync == 0L) return true  // Never synced

        val daysSinceSync = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - lastSync)
        return daysSinceSync >= SYNC_INTERVAL_DAYS
    }

    /**
     * Gets the number of Vanderwaals Collection wallpapers currently in the database.
     */
    suspend fun getVanderwaalsCollectionWallpaperCount(): Int {
        return wallpaperDao.countBySource(SOURCE_KEY)
    }

    /**
     * Syncs the Vanderwaals Collection manifest.
     *
     * @param manifestType "lite" for the curated subset, "full" for the complete archive
     * @param onProgress Progress callback: (message, progress 0-1, wallpaperCount)
     * @param forceUpdate If true, ignores If-Modified-Since and downloads fresh
     * @return Result with count of synced wallpapers or error
     */
    suspend fun syncVanderwaalsCollectionManifest(
        manifestType: String = "lite",
        onProgress: ((message: String, progress: Float, count: Int) -> Unit)? = null,
        forceUpdate: Boolean = false
    ): Result<Int> {
        Log.d(TAG, "Starting Vanderwaals Collection manifest sync (type: $manifestType, force: $forceUpdate)")

        var attempt = 0
        var backoffMs = INITIAL_BACKOFF_MS

        while (attempt < MAX_RETRIES) {
            attempt++

            try {
                onProgress?.invoke("Connecting to server...", 0.1f, 0)

                val lastModified = if (forceUpdate) {
                    null
                } else {
                    settingsDataStore.settings.first().vanderwaalsCollectionManifestLastModified
                }

                val response = fetchManifest(manifestType, lastModified)

                if (response.code() == 304) {
                    Log.d(TAG, "Manifest not modified, skipping download")
                    onProgress?.invoke("Already up to date", 1f, getVanderwaalsCollectionWallpaperCount())
                    return Result.success(getVanderwaalsCollectionWallpaperCount())
                }

                if (!response.isSuccessful) {
                    throw Exception("HTTP ${response.code()}: ${response.message()}")
                }

                val manifest = response.body()
                    ?: throw Exception("Empty response body")

                onProgress?.invoke("Processing wallpapers...", 0.3f, 0)

                // Convert and normalize the source to the stable SOURCE_KEY (off Main)
                val wallpapers = withContext(Dispatchers.Default) {
                    manifest.wallpapers.mapNotNull { dto -> convertToEntity(dto) }
                }

                Log.d(TAG, "Converted ${wallpapers.size} wallpapers")

                // Replace this source's wallpapers: delete stale entries first, then insert
                wallpaperDao.deleteBySource(SOURCE_KEY)
                var inserted = 0
                wallpapers.chunked(CHUNK_SIZE).forEachIndexed { _, chunk ->
                    wallpaperDao.insertAll(chunk)
                    inserted += chunk.size

                    val progress = 0.3f + (0.7f * (inserted.toFloat() / wallpapers.size))
                    onProgress?.invoke("Saving wallpapers...", progress, inserted)
                }

                // Update sync metadata
                settingsDataStore.updateVanderwaalsCollectionLastSyncTimestamp(System.currentTimeMillis())
                settingsDataStore.updateVanderwaalsCollectionManifestLastModified(
                    response.headers()["Last-Modified"]
                )
                settingsDataStore.updateVanderwaalsCollectionManifestType(manifestType)

                Log.i(TAG, "Vanderwaals Collection sync complete: ${wallpapers.size} wallpapers")
                onProgress?.invoke("Sync complete!", 1f, wallpapers.size)

                return Result.success(wallpapers.size)

            } catch (e: Exception) {
                Log.e(TAG, "Sync attempt $attempt failed: ${e.message}", e)

                if (attempt < MAX_RETRIES) {
                    onProgress?.invoke("Retrying... ($attempt/$MAX_RETRIES)", 0.1f, 0)
                    delay(backoffMs)
                    backoffMs *= 2  // Exponential backoff
                } else {
                    return Result.failure(e)
                }
            }
        }

        return Result.failure(Exception("Max retries exceeded"))
    }

    /**
     * Fetches the manifest based on type with optional conditional request.
     */
    private suspend fun fetchManifest(
        manifestType: String,
        lastModified: String?
    ): Response<ManifestDto> {
        return when (manifestType) {
            "full" -> {
                if (lastModified != null) {
                    vanderwaalsCollectionService.getVanderwaalsCollectionFullConditional(lastModified)
                } else {
                    vanderwaalsCollectionService.getVanderwaalsCollectionFull()
                }
            }
            else -> {  // "lite" or default
                if (lastModified != null) {
                    vanderwaalsCollectionService.getVanderwaalsCollectionLiteConditional(lastModified)
                } else {
                    vanderwaalsCollectionService.getVanderwaalsCollectionLite()
                }
            }
        }
    }

    /**
     * Converts a DTO wallpaper to a database entity, normalizing the source to
     * [SOURCE_KEY] so the entry matches the app's source-filtering convention.
     *
     * Also normalizes brightness/contrast to the 0-100 scale expected by the
     * app's scoring and filtering logic. The VC API serves brightness as raw
     * luma (0-255) and contrast without a 100 clamp, unlike the local curation
     * pipeline which normalizes both to 0-100.
     */
    private fun convertToEntity(
        dto: me.avinas.vanderwaals.network.dto.WallpaperMetadataDto
    ): WallpaperMetadata? {
        return try {
            dto.toEntity().copy(
                source = SOURCE_KEY,
                brightness = if (dto.brightness > 100) {
                    (dto.brightness * 100 / 255).coerceIn(0, 100)
                } else {
                    dto.brightness
                },
                contrast = dto.contrast.coerceIn(0, 100)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to convert wallpaper ${dto.id}: ${e.message}")
            null
        }
    }

    /**
     * Clears all Vanderwaals Collection wallpapers from the database.
     * Useful for refreshing or switching manifest types.
     */
    suspend fun clearVanderwaalsCollectionWallpapers() {
        wallpaperDao.deleteBySource(SOURCE_KEY)
        settingsDataStore.updateVanderwaalsCollectionManifestLastModified(null)
        Log.d(TAG, "Cleared all Vanderwaals Collection wallpapers")
    }
}
