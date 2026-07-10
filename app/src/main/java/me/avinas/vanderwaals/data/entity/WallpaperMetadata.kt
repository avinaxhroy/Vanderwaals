package me.avinas.vanderwaals.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

/**
 * Room entity for a wallpaper in the catalog.
 *
 * Stores the download URL, 1280-dim embedding vector, color palette, category,
 * brightness, and source attribution. Synced weekly from manifest.json.
 */
@Entity(
    tableName = "wallpaper_metadata",
    indices = [
        Index(value = ["category"]),
        Index(value = ["source"]),
        Index(value = ["brightness"]),
        Index(value = ["contrast"]),
        // Composite indexes for complex filters
        Index(value = ["category", "brightness"]),
        Index(value = ["source", "brightness"])
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
    val attribution: String?,
    val aestheticScore: Float = 0f,
    val mood: List<String> = emptyList(),
    val style: List<String> = emptyList()
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
        if (aestheticScore != other.aestheticScore) return false
        if (mood != other.mood) return false
        if (style != other.style) return false

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
    result = 31 * result + aestheticScore.hashCode()
    result = 31 * result + mood.hashCode()
    result = 31 * result + style.hashCode()
        return result
    }
}
