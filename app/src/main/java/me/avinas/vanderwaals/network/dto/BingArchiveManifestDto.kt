package me.avinas.vanderwaals.network.dto

import com.google.gson.annotations.SerializedName

/**
 * Data transfer object for Bing Wallpaper Archive API response.
 * 
 * Maps the JSON array from npanuhin/Bing-Wallpaper-Archive API endpoints
 * to Kotlin data classes. The archive contains 10,000+ historical Bing wallpapers
 * per region with rich metadata.
 * 
 * **Archive Source**:
 * - Base URL: https://bing.npanuhin.me/
 * - API Format: {country}/{language}.json (e.g., US/en.json, ROW/en.json)
 * - Year Format: {country}/{language}.{year}.json (e.g., US/en.2024.json)
 * - Image URL: https://bing.npanuhin.me/{country}/{language}/{date}.jpg
 * - Updated: Daily (automated workflow)
 * 
 * **API Response Structure** (Array, not object with "wallpapers" property):
 * ```json
 * [
 *   {
 *     "title": "Winter Berries",
 *     "caption": "Frozen beauty in nature",
 *     "subtitle": "A closer look",
 *     "copyright": "Frozen berries © Photographer Name/Getty Images",
 *     "description": "Detailed multi-line description about the image...",
 *     "date": "2024-01-15",
 *     "bing_url": "https://www.bing.com/th?id=OHR.WinterBerries_EN-US...",
 *     "url": "https://bing.npanuhin.me/US/en/2024-01-15.jpg"
 *   }
 * ]
 * ```
 * 
 * **Important**: The API response is a JSON array, NOT an object with a "wallpapers" property.
 * This differs from typical REST APIs. The service should expect `List<BingArchiveWallpaperDto>`.
 * 
 * @see BingArchiveWallpaperDto
 */
// Note: This wrapper class is no longer needed since API returns array directly
// Keeping it for backwards compatibility, but should use List<BingArchiveWallpaperDto> directly
@Deprecated(
    message = "API returns array directly. Use List<BingArchiveWallpaperDto> instead.",
    replaceWith = ReplaceWith("List<BingArchiveWallpaperDto>")
)
data class BingArchiveManifestDto(
    val wallpapers: List<BingArchiveWallpaperDto>
)

/**
 * Individual Bing archive wallpaper entry.
 * 
 * Represents a single historical Bing wallpaper with complete metadata from the archive.
 * All fields except `date` and `url` are nullable as per the API specification.
 * 
 * **API Response Format** (all fields except date and url are nullable):
 * ```json
 * {
 *   "title": "Winter Berries" | null,
 *   "caption": "Frozen beauty" | null,
 *   "subtitle": "A closer look" | null,
 *   "copyright": "© Photographer/Getty" | null,
 *   "description": "Multi-line description..." | null,
 *   "date": "2024-01-15",
 *   "bing_url": "https://www.bing.com/th?id=OHR..." | null,
 *   "url": "https://bing.npanuhin.me/US/en/2024-01-15.jpg"
 * }
 * ```
 * 
 * **Date Format**: YYYY-MM-DD (with dashes, e.g., "2024-01-15")
 * 
 * **Image URL**: Complete URL, no need to construct
 * - Format: https://bing.npanuhin.me/{country}/{language}/{date}.jpg
 * - Example: https://bing.npanuhin.me/US/en/2024-01-15.jpg
 * - Resolution: 3840×2160 (UHD)
 * 
 * **bing_url Field**: Original Microsoft Bing server URL
 * - May be null for older images (points to dummy image)
 * - For images from last ~2 years, contains valid Microsoft CDN URL
 * - Not recommended to use as primary source (use `url` field instead)
 * 
 * **Notes**:
 * - Images sorted by date (ascending: oldest first, newest last)
 * - `title` is usually present (de facto every image has it)
 * - `description` can span multiple lines
 * - `caption` and `subtitle` less commonly populated
 * 
 * @property title Wallpaper title (short description, usually present)
 * @property caption Brief caption text (optional)
 * @property subtitle Additional subtitle text (optional)
 * @property copyright Copyright and attribution (photographer, Getty Images, etc.)
 * @property description Detailed multi-line description (optional)
 * @property date Wallpaper date in YYYY-MM-DD format (e.g., "2024-01-15")
 * @property bingUrl Original Bing server URL (may be null for older images)
 * @property url Complete image URL (https://bing.npanuhin.me/...)
 * 
 * @see toWallpaperMetadata
 */
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

/**
 * Converts a BingArchiveWallpaperDto to a WallpaperMetadata entity.
 * 
 * Transforms the DTO from the archive JSON to the Room entity format with:
 * - Generated unique ID from date
 * - Complete URL (already provided by API)
 * - Rich metadata (title, caption, subtitle, description, copyright)
 * - Default embedding (to be computed later)
 * - Category set to "bing"
 * - Intelligent metadata compilation
 * 
 * **Metadata Compilation Strategy**:
 * - Title: Uses `title` field (usually present)
 * - Description: Combines `description`, `subtitle`, and `caption` for rich context
 * - Attribution: Uses `copyright` field with photographer and source
 * 
 * **Note**: Embeddings must be computed separately using MobileNetV3 after
 * importing. This function provides default zero embeddings.
 * 
 * @return WallpaperMetadata entity ready for database insertion
 * 
 * Example:
 * ```kotlin
 * val archiveWallpaper = BingArchiveWallpaperDto(
 *     title = "Winter Berries",
 *     caption = "Frozen beauty",
 *     subtitle = "Nature's art in winter",
 *     copyright = "Frozen berries © Photographer/Getty Images",
 *     description = "Detailed description of the frozen berries...",
 *     date = "2024-01-15",
 *     bingUrl = "https://www.bing.com/th?id=OHR.WinterBerries...",
 *     url = "https://bing.npanuhin.me/US/en/2024-01-15.jpg"
 * )
 * 
 * val entity = archiveWallpaper.toWallpaperMetadata()
 * database.wallpaperMetadataDao().insert(entity)
 * 
 * // Later, compute embedding:
 * val bitmap = downloadImage(entity.url)
 * val embedding = embeddingExtractor.extract(bitmap)
 * database.wallpaperMetadataDao().updateEmbedding(entity.id, embedding)
 * ```
 */
fun BingArchiveWallpaperDto.toWallpaperMetadata(): me.avinas.vanderwaals.data.entity.WallpaperMetadata {
    // URL is already complete from API (no need to construct)
    val fullUrl = url
    
    // Create thumbnail URL by replacing .jpg with lower resolution (if pattern matches)
    val thumbnailUrl = url  // Archive doesn't provide multiple resolutions directly
    
    // Compile rich description from all available metadata
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
        colors = extractColorsFromMetadata(title, description, caption),  // Extract from all metadata
        brightness = 50,  // Default medium brightness (compute later from image)
        contrast = 50,  // Default medium contrast (compute later from image)
        embedding = FloatArray(576) { 0f },  // Zero embedding (compute later with MobileNetV3)
        resolution = "3840x2160",  // UHD resolution
        attribution = copyright ?: "Bing Wallpaper Archive"  // Use copyright or fallback
    )
}

/**
 * Extracts placeholder color palette from wallpaper metadata.
 * 
 * This is a simple heuristic for initial import using all available metadata fields.
 * Colors should be properly extracted from the actual image later using k-means clustering.
 * 
 * Maps common keywords from title, description, and caption to color palettes:
 * - "winter", "snow", "ice" → cool blues/whites
 * - "sunset", "autumn", "fall" → warm oranges/reds
 * - "ocean", "sea", "water" → blues/greens
 * - "mountain", "peak" → grays/blues
 * - etc.
 * 
 * @param title Wallpaper title (primary source)
 * @param description Detailed description (secondary source)
 * @param caption Brief caption (tertiary source)
 * @return List of 5 hex color codes
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

/**
 * Gets the full image URL from the archive wallpaper.
 * 
 * Convenience function that returns the complete URL.
 * The API already provides complete URLs, no construction needed.
 * 
 * @return Full UHD image URL (3840×2160)
 */
fun BingArchiveWallpaperDto.getImageUrl(): String {
    return url  // URL is already complete from API
}

/**
 * Gets the original Bing URL if available.
 * 
 * Returns the original Microsoft Bing server URL for the wallpaper.
 * May be null for older images (>2 years old).
 * 
 * @return Original Bing URL or null if not available
 */
fun BingArchiveWallpaperDto.getBingServerUrl(): String? {
    return bingUrl
}

/**
 * Checks if this is a recent wallpaper (within last 2 years).
 * 
 * Recent wallpapers typically have valid `bing_url` links to Microsoft servers.
 * Older wallpapers may only have archive URLs.
 * 
 * @return true if wallpaper is from last 2 years, false otherwise
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

/**
 * Gets a human-readable description combining all available metadata.
 * 
 * Combines title, caption, subtitle, and description into a single string
 * suitable for display or search indexing.
 * 
 * @return Combined description string
 */
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
