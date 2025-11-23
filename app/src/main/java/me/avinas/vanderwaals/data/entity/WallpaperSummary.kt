package me.avinas.vanderwaals.data.entity

import androidx.room.ColumnInfo

/**
 * Lightweight projection of [WallpaperMetadata] for UI display.
 * 
 * Excludes the large [WallpaperMetadata.embedding] array (576 floats) to reduce
 * memory usage and query time when loading lists of wallpapers for the UI.
 * 
 * Use [toWallpaperMetadata] to convert back to the full entity (with empty embedding)
 * if needed for compatibility with existing UI components.
 */
data class WallpaperSummary(
    val id: String,
    val url: String,
    val thumbnailUrl: String,
    val source: String,
    val category: String,
    val colors: List<String>,
    val brightness: Int,
    val contrast: Int,
    val resolution: String,
    val attribution: String?
) {
    /**
     * Converts this summary to a full [WallpaperMetadata] object.
     * 
     * The embedding field will be initialized with an empty FloatArray since
     * it wasn't loaded from the database.
     */
    fun toWallpaperMetadata(): WallpaperMetadata {
        return WallpaperMetadata(
            id = id,
            url = url,
            thumbnailUrl = thumbnailUrl,
            source = source,
            category = category,
            colors = colors,
            brightness = brightness,
            contrast = contrast,
            embedding = FloatArray(0), // Empty embedding
            resolution = resolution,
            attribution = attribution
        )
    }
}
