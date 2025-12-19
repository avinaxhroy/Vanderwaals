package me.avinas.vanderwaals.network.dto

import android.util.Base64
import com.google.gson.annotations.SerializedName
import me.avinas.vanderwaals.data.entity.WallpaperMetadata

/**
 * Data transfer object for individual wallpaper metadata from manifest.
 * 
 * Maps JSON from manifest.json to Kotlin data class using Gson.
 * The manifest is generated weekly by GitHub Actions curation pipeline
 * and includes pre-computed embeddings and metadata for all wallpapers.
 * 
 * **Supports Two Embedding Formats:**
 * 1. **Full embeddings (v1)**: Standard float array
 * 2. **Quantized embeddings (v2)**: Base64-encoded int8 with min/max for dequantization
 * 
 * **JSON Structure (v2 - quantized):**
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
 *   "e": "base64EncodedInt8Data...",
 *   "eMin": -0.234,
 *   "eMax": 0.567,
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
 * @property contrast Contrast level (0-100)
 * @property embedding Full 576-dimensional embedding (legacy v1 format, optional)
 * @property e Base64-encoded quantized embedding (v2 format, optional)
 * @property eMin Minimum value for dequantization (v2 format)
 * @property eMax Maximum value for dequantization (v2 format)
 * @property resolution Image resolution string (e.g., "3840x2160", "2560x1440")
 * @property attribution Source attribution and photographer credit (optional)
 * 
 * @see WallpaperMetadata
 * @see toEntity
 * @see getEmbeddingArray
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
    val resolution: String,
    val attribution: String?,
    
    // Legacy full embedding (v1 format)
    val embedding: List<Float>? = null,
    
    // Quantized embedding (v2 format)
    val e: String? = null,
    val eMin: Float? = null,
    val eMax: Float? = null
)

/**
 * Gets the embedding as a FloatArray, handling both v1 (full) and v2 (quantized) formats.
 * 
 * If quantized format is available (e, eMin, eMax), dequantizes it.
 * Otherwise falls back to the full embedding.
 * 
 * @return 576-dimensional FloatArray, or empty array if no embedding available
 */
fun WallpaperMetadataDto.getEmbeddingArray(): FloatArray {
    // Try quantized format first (v2)
    if (e != null && eMin != null && eMax != null) {
        return dequantizeEmbedding(e, eMin, eMax)
    }
    
    // Fall back to full embedding (v1)
    return embedding?.toFloatArray() ?: FloatArray(576)
}

/**
 * Dequantizes a base64-encoded int8 embedding back to float32.
 * 
 * @param base64Data Base64-encoded embedding bytes
 * @param min Minimum value of original embedding
 * @param max Maximum value of original embedding
 * @return Dequantized FloatArray
 */
private fun dequantizeEmbedding(base64Data: String, min: Float, max: Float): FloatArray {
    return try {
        val bytes = Base64.decode(base64Data, Base64.DEFAULT)
        
        if (max <= min) {
            // Constant embedding - all values are the same
            return FloatArray(bytes.size) { min }
        }
        
        // Restore original range: value = (byte / 255) * (max - min) + min
        FloatArray(bytes.size) { i ->
            val byteValue = bytes[i].toInt() and 0xFF  // Convert to unsigned
            min + (byteValue / 255f) * (max - min)
        }
    } catch (e: Exception) {
        // Fallback to empty embedding if dequantization fails
        FloatArray(576)
    }
}

/**
 * Converts a WallpaperMetadataDto to a WallpaperMetadata entity for database storage.
 * 
 * Transforms the DTO from network JSON to the Room entity format:
 * - Handles both full and quantized embedding formats
 * - Maps `thumbnail` field to `thumbnailUrl`
 * - Preserves all metadata fields
 * 
 * @return WallpaperMetadata entity ready for database insertion
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
        embedding = getEmbeddingArray(),  // Handles both v1 and v2 formats
        resolution = resolution,
        attribution = attribution
    )
}

