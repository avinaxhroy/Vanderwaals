package me.avinas.vanderwaals.network.dto

import com.google.gson.annotations.SerializedName
import me.avinas.vanderwaals.data.entity.WallpaperMetadata

/**
 * Data transfer object for individual wallpaper metadata from manifest.
 * 
 * Maps JSON from manifest.json to Kotlin data class using Gson.
 * The manifest is generated weekly by GitHub Actions curation pipeline
 * and includes pre-computed embeddings and metadata for all wallpapers.
 * 
 * **JSON Structure:**
 * ```json
 * {
 *   "id": "dharmx_gruvbox_001",
 *   "url": "https://cdn.jsdelivr.net/gh/yourrepo/wallpapers/001.jpg",
 *   "thumbnail": "https://cdn.jsdelivr.net/gh/yourrepo/thumbs/001.jpg",
 *   "source": "github",
 *   "repo": "dharmx/walls",
 *   "category": "gruvbox",
 *   "colors": ["#282828", "#cc241d", "#98971a"],
 *   "brightness": 35,
 *   "embedding": [0.234, -0.567, ...],
 *   "resolution": "2560x1440",
 *   "attribution": "dharmx/walls"
 * }
 * ```
 * 
 * @property id Unique wallpaper identifier (format: "source_category_number")
 * @property url Direct download URL (jsDelivr CDN or GitHub raw)
 * @property thumbnail Preview thumbnail URL (smaller resolution for fast loading)
 * @property source Content source ("github" or "bing")
 * @property repo Source repository name (e.g., "dharmx/walls", "bing-daily")
 * @property category Category from folder structure (e.g., "gruvbox", "nord", "nature")
 * @property colors Hex color palette extracted during curation (5 dominant colors)
 * @property brightness Brightness level (0-100) for contextual filtering
 * @property embedding 576-dimensional MobileNetV3-Small embedding vector
 * @property resolution Image resolution string (e.g., "3840x2160", "2560x1440")
 * @property attribution Source attribution and photographer credit (optional)
 * 
 * @see WallpaperMetadata
 * @see toEntity
 */
data class WallpaperMetadataDto(
    val id: String,
    val url: String,
    @SerializedName("thumbnail")
    val thumbnail: String,
    val source: String,
    val repo: String,
    val category: String,
    val colors: List<String>,
    val brightness: Int,
    val contrast: Int,
    val embedding: List<Float>,
    val resolution: String,
    val attribution: String?
)

/**
 * Converts a WallpaperMetadataDto to a WallpaperMetadata entity for database storage.
 * 
 * Transforms the DTO from network JSON to the Room entity format:
 * - Converts List<Float> to FloatArray for memory efficiency
 * - Maps `thumbnail` field to `thumbnailUrl`
 * - Preserves all metadata fields
 * 
 * @return WallpaperMetadata entity ready for database insertion
 * 
 * Example:
 * ```kotlin
 * val dto = WallpaperMetadataDto(
 *     id = "dharmx_gruvbox_001",
 *     url = "https://cdn.jsdelivr.net/...",
 *     thumbnail = "https://cdn.jsdelivr.net/.../thumb.jpg",
 *     source = "github",
 *     repo = "dharmx/walls",
 *     category = "gruvbox",
 *     colors = listOf("#282828", "#cc241d"),
 *     brightness = 35,
 *     embedding = listOf(0.1f, 0.2f, ...),
 *     resolution = "2560x1440",
 *     attribution = "dharmx/walls"
 * )
 * val entity = dto.toEntity()
 * database.wallpaperMetadataDao().insert(entity)
 * ```
 */
fun WallpaperMetadataDto.toEntity(): WallpaperMetadata {
    return WallpaperMetadata(
        id = id,
        url = url,
        thumbnailUrl = thumbnail,
        source = source,
        category = category,
        colors = colors,
        brightness = brightness,
        contrast = contrast,
        embedding = embedding.toFloatArray(),
        resolution = resolution,
        attribution = attribution
    )
}

