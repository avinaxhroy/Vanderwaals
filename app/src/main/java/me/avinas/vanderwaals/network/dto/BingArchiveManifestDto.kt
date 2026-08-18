package me.avinas.vanderwaals.network.dto

import com.google.gson.annotations.SerializedName

// Note: This wrapper class is no longer needed since API returns array directly
// Keeping it for backwards compatibility, but should use List<BingArchiveWallpaperDto> directly
@Deprecated(
    message = "API returns array directly. Use List<BingArchiveWallpaperDto> instead.",
    replaceWith = ReplaceWith("List<BingArchiveWallpaperDto>")
)
data class BingArchiveManifestDto(
    val wallpapers: List<BingArchiveWallpaperDto>
)

data class BingArchiveWallpaperDto(
    val title: String? = null,
    val caption: String? = null,
    val subtitle: String? = null,
    val copyright: String? = null,
    val description: String? = null,
    val date: String,  // Required: YYYY-MM-DD format
    @SerializedName("bing_url")
    val bingUrl: String? = null,
    val url: String  // Required: Complete image URL
)

fun BingArchiveWallpaperDto.toWallpaperMetadata(): me.avinas.vanderwaals.data.entity.WallpaperMetadata {
    // URL is already complete from API (no need to construct)
    val fullUrl = url
    
    val thumbnailUrl = url  // Archive doesn't provide multiple resolutions directly
    
    val compiledDescription = buildString {
        if (!description.isNullOrBlank()) {
            append(description)
        }
        if (!subtitle.isNullOrBlank()) {
            if (isNotEmpty()) append("\n\n")
            append(subtitle)
        }
        if (!caption.isNullOrBlank() && caption != title) {
            if (isNotEmpty()) append("\n\n")
            append(caption)
        }
    }.ifBlank { null }
    
    // Extract country/language from URL for better categorization
    // Format: https://bing.npanuhin.me/US/en/2024-01-15.jpg
    val urlParts = url.split("/")
    val country = if (urlParts.size >= 5) urlParts[3] else "US"
    val language = if (urlParts.size >= 6) urlParts[4] else "en"
    
    return me.avinas.vanderwaals.data.entity.WallpaperMetadata(
        id = "bing_archive_${country}_${language}_$date",
        url = fullUrl,
        thumbnailUrl = thumbnailUrl,
        source = "bing",
        category = "photography",  // Bing wallpapers are professional photography
        colors = extractColorsFromMetadata(title, description, caption),
        brightness = 50,  // Default medium brightness (compute later from image)
        contrast = 50,  // Default medium contrast (compute later from image)
        embedding = FloatArray(1280) { 0f },  // Zero embedding (compute later with MobileNetV4)
        resolution = "3840x2160",  // UHD resolution
        attribution = copyright ?: "Bing Wallpaper Archive"
    )
}

/**
 * Placeholder color palette heuristic for initial import.
 * Colors should be extracted from the actual image later (k-means clustering).
 */
private fun extractColorsFromMetadata(
    title: String?,
    description: String?,
    caption: String?
): List<String> {
    val combinedText = listOfNotNull(title, caption, description)
        .joinToString(" ")
        .lowercase()
    
    return when {
        // Winter/Cold themes
        "winter" in combinedText || "snow" in combinedText || "ice" in combinedText || 
        "frost" in combinedText || "frozen" in combinedText -> 
            listOf("#e8f4f8", "#b3d9f2", "#7fb3d5", "#4682b4", "#1e4d7b")
        
        // Sunset/Warm themes
        "sunset" in combinedText || "autumn" in combinedText || "fall" in combinedText || 
        "orange" in combinedText || "dusk" in combinedText || "dawn" in combinedText -> 
            listOf("#ff6b35", "#ff8c42", "#ffa552", "#ffbe62", "#f4d35e")
        
        // Ocean/Water themes
        "ocean" in combinedText || "sea" in combinedText || "beach" in combinedText || 
        "water" in combinedText || "coast" in combinedText || "marine" in combinedText -> 
            listOf("#06aed5", "#086788", "#0a9396", "#94d2bd", "#e9d8a6")
        
        // Spring/Floral themes
        "spring" in combinedText || "flower" in combinedText || "blossom" in combinedText || 
        "garden" in combinedText || "petal" in combinedText -> 
            listOf("#ffcad4", "#f4acb7", "#9d8189", "#6c584c", "#84a59d")
        
        // Night/Dark themes
        "night" in combinedText || "star" in combinedText || "galaxy" in combinedText || 
        "astro" in combinedText || "constellation" in combinedText || "moon" in combinedText -> 
            listOf("#1a1a2e", "#16213e", "#0f3460", "#533483", "#e94560")
        
        // Forest/Green themes
        "forest" in combinedText || "tree" in combinedText || "jungle" in combinedText || 
        "woodland" in combinedText || "rainforest" in combinedText -> 
            listOf("#606c38", "#283618", "#fefae0", "#dda15e", "#bc6c25")
        
        // Mountain/Rock themes
        "mountain" in combinedText || "peak" in combinedText || "cliff" in combinedText || 
        "rock" in combinedText || "canyon" in combinedText -> 
            listOf("#8b7355", "#6d5a4b", "#9da39a", "#5a5a5a", "#b8b8b8")
        
        // Desert/Sand themes
        "desert" in combinedText || "sand" in combinedText || "dune" in combinedText || 
        "arid" in combinedText || "sahara" in combinedText -> 
            listOf("#f4d03f", "#f39c12", "#e67e22", "#d35400", "#a04000")
        
        // Tropical themes
        "tropical" in combinedText || "paradise" in combinedText || "caribbean" in combinedText || 
        "palm" in combinedText -> 
            listOf("#00b4d8", "#0077b6", "#03045e", "#90e0ef", "#caf0f8")
        
        // Default neutral palette
        else -> 
            listOf("#3a506b", "#5bc0be", "#6fffe9", "#0b132b", "#1c2541")
    }
}

fun BingArchiveWallpaperDto.getImageUrl(): String {
    return url  // URL is already complete from API
}

/**
 * Original Bing server URL; may be null for older images (>2 years).
 */
fun BingArchiveWallpaperDto.getBingServerUrl(): String? {
    return bingUrl
}

/**
 * Recent wallpapers typically have a valid `bing_url` to Microsoft servers;
 * older ones only have archive URLs.
 */
fun BingArchiveWallpaperDto.isRecent(): Boolean {
    return try {
        val wallpaperYear = date.substring(0, 4).toInt()
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        (currentYear - wallpaperYear) <= 2
    } catch (e: Exception) {
        false
    }
}

fun BingArchiveWallpaperDto.getFullDescription(): String {
    return buildString {
        if (!title.isNullOrBlank()) {
            append(title)
        }
        if (!caption.isNullOrBlank()) {
            if (isNotEmpty()) append(" — ")
            append(caption)
        }
        if (!subtitle.isNullOrBlank()) {
            if (isNotEmpty()) append("\n")
            append(subtitle)
        }
        if (!description.isNullOrBlank()) {
            if (isNotEmpty()) append("\n\n")
            append(description)
        }
    }
}
