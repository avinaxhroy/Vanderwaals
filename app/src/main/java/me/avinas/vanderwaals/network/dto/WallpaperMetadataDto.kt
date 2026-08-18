package me.avinas.vanderwaals.network.dto

import android.util.Base64
import com.google.gson.annotations.SerializedName
import me.avinas.vanderwaals.data.entity.WallpaperMetadata

data class WallpaperMetadataDto(
    val id: String,
    val url: String,
    @SerializedName("thumbnail")
    val thumbnail: String?,  // Nullable to handle missing fields in JSON
    val source: String,
    val repo: String? = null,
    val category: String,
    val colors: List<String>,
    val brightness: Int,
    val contrast: Int,
    val resolution: String,
    val attribution: String?,

    // Vanderwaals Collection semantic metadata (optional, absent for GitHub/Bing sources)
    @SerializedName("aestheticScore")
    val aestheticScore: Float = 0f,
    val mood: List<String> = emptyList(),
    val style: List<String> = emptyList(),

    // Legacy full embedding (v1 format)
    val embedding: List<Float>? = null,
    
    // Quantized embedding (v2 format)
    val e: String? = null,
    val eMin: Float? = null,
    val eMax: Float? = null
)

/**
 * Embedding dimension is determined dynamically: quantized = Base64-decoded
 * byte count, full = embedding list size.
 */
fun WallpaperMetadataDto.getEmbeddingArray(): FloatArray {
    // Try quantized format first (v2/v3) - dimension is determined by Base64 byte count
    if (e != null && eMin != null && eMax != null) {
        return dequantizeEmbedding(e, eMin, eMax)
    }
    
    // Fall back to full embedding (v1) - dimension is determined by list size
    return embedding?.toFloatArray() ?: floatArrayOf()  // Empty signals missing, not 576D assumed
}

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
        // Return empty array to signal dequantization failure (don't assume dimension)
        floatArrayOf()
    }
}

fun WallpaperMetadataDto.toEntity(): WallpaperMetadata {
    return WallpaperMetadata(
        id = id,
        url = url,
        thumbnailUrl = thumbnail ?: url, // Fallback to full URL if thumbnail is missing
        source = source,
        category = category,
        colors = colors,
        brightness = brightness,
        contrast = contrast,
        embedding = getEmbeddingArray(),  // Handles both v1 and v2 formats
        resolution = resolution,
        attribution = attribution,
        aestheticScore = aestheticScore,
        mood = mood,
        style = style
    )
}

