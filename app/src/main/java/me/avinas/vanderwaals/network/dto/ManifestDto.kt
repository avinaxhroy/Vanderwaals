package me.avinas.vanderwaals.network.dto

import com.google.gson.annotations.SerializedName
import me.avinas.vanderwaals.data.entity.WallpaperMetadata

data class ManifestDto(
    val version: String,
    @SerializedName("last_updated")
    val lastUpdated: String,
    @SerializedName("model_version")
    val modelVersion: String,
    @SerializedName("embedding_dim")
    val embeddingDim: Int,
    @SerializedName("total_wallpapers")
    val totalWallpapers: Int,
    val quantized: Boolean = false,  // Default false for backward compatibility
    val wallpapers: List<WallpaperMetadataDto>
)

fun ManifestDto.toWallpaperEntities(): List<WallpaperMetadata> {
    return wallpapers.map { it.toEntity() }
}

fun ManifestDto.getEstimatedSize(): Long {
    // v2 (quantized): ~1KB per wallpaper, v1: ~4KB per wallpaper
    val perWallpaperBytes = if (quantized) 1024L else 4096L
    return wallpapers.size * perWallpaperBytes
}

/**
 * Validates the manifest. Handles legacy v1 manifests (MobileNetV3 576D) that
 * lack the embeddingDim field by inferring the dimension from the actual data.
 */
fun ManifestDto.isValid(): Boolean {
    // Parse version string (handle "1.0.0", "2", etc.)
    val v = try {
        version.substringBefore('.').toInt()
    } catch (e: NumberFormatException) {
        0
    }

    if (v < 1 || lastUpdated.isBlank() || modelVersion.isBlank()) {
        return false
    }
    
    if (wallpapers.isEmpty()) {
        return false
    }
    
    // For v1 (legacy MobileNetV3), embeddingDim may be missing (defaults to 0 from Gson)
    // Infer dimension from actual embedding data and accept both 576D (legacy) and 1280D
    if (v == 1 && embeddingDim == 0) {
        val sample = wallpapers.firstOrNull() ?: return false
        val actualDim = sample.embedding?.size ?: 0
        return actualDim > 0  // Accept any non-empty embedding for v1
    }
    
    // For v2+ manifests, validate embeddingDim field
    val validDims = listOf(576, 1280)
    if (embeddingDim !in validDims) {
        return false
    }
    
    // totalWallpapers count should match (if present)
    if (totalWallpapers > 0 && totalWallpapers != wallpapers.size) {
        return false
    }
    
    // For v2+ (quantized), check that quantized format fields are present
    if (v >= 2 && quantized) {
        val sample = wallpapers.firstOrNull() ?: return false
        if (sample.e.isNullOrBlank()) {
            return false  // Quantized format requires 'e' field
        }
    } else {
        // For non-quantized, check that full embedding is present
        val sample = wallpapers.firstOrNull() ?: return false
        if (sample.embedding.isNullOrEmpty() || sample.embedding.size != embeddingDim) {
            return false
        }
    }
    
    return true
}

