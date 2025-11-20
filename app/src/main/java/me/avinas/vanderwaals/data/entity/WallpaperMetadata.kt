package me.avinas.vanderwaals.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

/**
 * Room entity representing wallpaper metadata from GitHub and Bing sources.
 * 
 * Stores pre-computed data for all wallpapers in the content catalog:
 * - URL and thumbnail for downloading
 * - Pre-computed embedding vector (576 floats) for similarity matching
 * - Color palette extracted during curation
 * - Category and brightness for contextual filtering
 * - Source attribution for credits
 * 
 * This data is synced weekly from the GitHub manifest.json file that contains
 * metadata for 6000+ wallpapers from curated sources.
 * 
 * **Database Indexes:**
 * - `category`: Enables fast filtering by wallpaper category
 * - `source`: Allows efficient querying by content source
 * - `brightness`: Supports brightness-based filtering
 * 
 * **Type Converters:**
 * - Uses [Converters] to serialize `colors` (List<String>) and `embedding` (FloatArray)
 * 
 * @property id Unique identifier for the wallpaper
 * @property url Direct download URL (GitHub raw or jsDelivr CDN)
 * @property thumbnailUrl URL for thumbnail preview
 * @property source Content source ("github" or "bing")
 * @property category Category from folder structure (e.g., "gruvbox", "nord", "nature")
 * @property colors List of hex color codes representing the color palette
 * @property brightness Brightness level (0-100)
 * @property embedding 576-dimensional MobileNetV3 embedding vector
 * @property resolution Image resolution (e.g., "3840x2160")
 * @property attribution Source attribution and photographer credit
 */
@Entity(
    tableName = "wallpaper_metadata",
    indices = [
        Index(value = ["category"]),
        Index(value = ["source"]),
        Index(value = ["brightness"]),
        Index(value = ["contrast"])
    ]
)
@TypeConverters(Converters::class)
data class WallpaperMetadata(
    @PrimaryKey
    val id: String,
    val url: String,
    val thumbnailUrl: String,
    val source: String,
    val category: String,
    val colors: List<String>,
    val brightness: Int,
    val contrast: Int,
    val embedding: FloatArray,
    val resolution: String,
    val attribution: String?
) {
    /**
     * Override equals to properly compare FloatArray.
     * Auto-generated equals from data class doesn't handle arrays correctly.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as WallpaperMetadata

        if (id != other.id) return false
        if (url != other.url) return false
        if (thumbnailUrl != other.thumbnailUrl) return false
        if (source != other.source) return false
        if (category != other.category) return false
        if (colors != other.colors) return false
        if (brightness != other.brightness) return false
        if (contrast != other.contrast) return false
        if (!embedding.contentEquals(other.embedding)) return false
        if (resolution != other.resolution) return false
        if (attribution != other.attribution) return false

        return true
    }

    /**
     * Override hashCode to properly hash FloatArray.
     * Auto-generated hashCode from data class doesn't handle arrays correctly.
     */
    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + url.hashCode()
        result = 31 * result + thumbnailUrl.hashCode()
        result = 31 * result + source.hashCode()
        result = 31 * result + category.hashCode()
        result = 31 * result + colors.hashCode()
        result = 31 * result + brightness
        result = 31 * result + contrast
        result = 31 * result + embedding.contentHashCode()
        result = 31 * result + resolution.hashCode()
        result = 31 * result + (attribution?.hashCode() ?: 0)
        return result
    }
}
