package me.avinas.vanderwaals.data.entity

import androidx.room.ColumnInfo

/**
 * Lightweight projection of [WallpaperMetadata] for UI display.
 * 
 * Excludes the large [WallpaperMetadata.embedding] array (1280 floats) to reduce
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
    val attribution: String?,
    val aestheticScore: Float = 0f,
    val mood: List<String> = emptyList(),
    val style: List<String> = emptyList()
) {
    /** Converts back to a full [WallpaperMetadata] (embedding left empty). */
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
            embedding = FloatArray(0),
            resolution = resolution,
            attribution = attribution,
            aestheticScore = aestheticScore,
            mood = mood,
            style = style
        )
    }
}
