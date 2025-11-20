package me.avinas.vanderwaals.network.dto

import com.google.gson.annotations.SerializedName
import me.avinas.vanderwaals.data.entity.WallpaperMetadata

/**
 * Data transfer object for wallpaper manifest downloaded from GitHub/jsDelivr.
 * 
 * The manifest.json file contains pre-computed metadata for all 6000+ wallpapers
 * in the curated catalog. Generated weekly by GitHub Actions curation pipeline
 * that processes wallpapers from multiple sources (dharmx/walls, makccr/wallpapers,
 * Bing daily, etc.).
 * 
 * **JSON Structure:**
 * ```json
 * {
 *   "version": 1,
 *   "last_updated": "2025-11-13T07:00:00Z",
 *   "model_version": "mobilenet_v3_small",
 *   "wallpapers": [
 *     {
 *       "id": "dharmx_gruvbox_001",
 *       "url": "https://cdn.jsdelivr.net/gh/yourrepo/wallpapers/001.jpg",
 *       "thumbnail": "https://cdn.jsdelivr.net/gh/yourrepo/thumbs/001.jpg",
 *       "source": "github",
 *       "repo": "dharmx/walls",
 *       "category": "gruvbox",
 *       "colors": ["#282828", "#cc241d"],
 *       "brightness": 35,
 *       "embedding": [0.234, -0.567, ...],
 *       "resolution": "2560x1440",
 *       "attribution": "dharmx/walls"
 *     }
 *   ]
 * }
 * ```
 * 
 * **Compressed size:** ~6MB for 6000 wallpapers with 576-dim embeddings.
 * 
 * **Download source:**
 * - Primary: `https://cdn.jsdelivr.net/gh/{owner}/{repo}@{branch}/manifest.json`
 * - Fallback: GitHub raw URL
 * 
 * @property version Manifest format version (integer for versioning schema changes)
 * @property lastUpdated ISO 8601 timestamp of last curation run
 * @property modelVersion ML model version used for embeddings (e.g., "mobilenet_v3_small")
 * @property wallpapers List of wallpaper metadata objects (6000+ entries)
 * 
 * @see WallpaperMetadataDto
 * @see toWallpaperEntities
 */
data class ManifestDto(
    val version: Int,
    @SerializedName("last_updated")
    val lastUpdated: String,
    @SerializedName("model_version")
    val modelVersion: String,
    @SerializedName("embedding_dim")
    val embeddingDim: Int,
    @SerializedName("total_wallpapers")
    val totalWallpapers: Int,
    val wallpapers: List<WallpaperMetadataDto>
)

/**
 * Converts ManifestDto to a list of WallpaperMetadata entities for database storage.
 * 
 * Bulk conversion of all wallpapers in the manifest from DTOs to Room entities.
 * This is called after downloading and parsing the manifest.json file.
 * 
 * @return List of WallpaperMetadata entities ready for batch insertion
 * 
 * Example:
 * ```kotlin
 * val response = manifestService.getManifest()
 * if (response.isSuccessful) {
 *     val manifest = response.body()!!
 *     val entities = manifest.toWallpaperEntities()
 *     
 *     // Bulk insert into database
 *     database.wallpaperMetadataDao().insertAll(entities)
 *     
 *     Log.d("Manifest", "Synced ${entities.size} wallpapers")
 *     Log.d("Manifest", "Version: ${manifest.version}")
 *     Log.d("Manifest", "Updated: ${manifest.lastUpdated}")
 *     Log.d("Manifest", "Model: ${manifest.modelVersion}")
 * }
 * ```
 */
fun ManifestDto.toWallpaperEntities(): List<WallpaperMetadata> {
    return wallpapers.map { it.toEntity() }
}

/**
 * Gets the total size estimate of the manifest in memory.
 * 
 * Useful for debugging and performance monitoring.
 * 
 * @return Estimated size in bytes
 */
fun ManifestDto.getEstimatedSize(): Long {
    // Rough estimate: each wallpaper ~4KB (2KB metadata + 2.3KB embedding)
    return wallpapers.size * 4096L
}

/**
 * Validates the manifest structure and data.
 * 
 * Checks for:
 * - Non-empty wallpaper list
 * - Valid version number
 * - Proper embedding dimensions (matches embeddingDim field)
 * - Total wallpapers count matches list size
 * 
 * @return true if manifest is valid, false otherwise
 */
fun ManifestDto.isValid(): Boolean {
    return version > 0 &&
            lastUpdated.isNotBlank() &&
            modelVersion.isNotBlank() &&
            embeddingDim == 576 &&
            wallpapers.isNotEmpty() &&
            totalWallpapers == wallpapers.size &&
            wallpapers.all { it.embedding.size == embeddingDim }
}

