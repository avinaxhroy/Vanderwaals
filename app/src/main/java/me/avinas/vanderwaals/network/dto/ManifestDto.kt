package me.avinas.vanderwaals.network.dto

import com.google.gson.annotations.SerializedName
import me.avinas.vanderwaals.data.entity.WallpaperMetadata

/**
 * Data transfer object for wallpaper manifest downloaded from GitHub/jsDelivr.
 * 
 * The manifest.json file contains pre-computed metadata for all 6000+ wallpapers
 * in the curated catalog. Generated weekly by GitHub Actions curation pipeline
 * that processes wallpapers from multiple sources (dharmx/walls, makccr/wallpapers,
 * Bing daily, etc.).
 * 
 * **Supports Three Versions:**
 * - **v1**: Full float32 embeddings (legacy, larger file)
 * - **v2**: Quantized int8 embeddings with MobileNetV3 576D (80% smaller)
 * - **v3**: MobileNetV4-Conv-Small 1280D embeddings (improved accuracy)
 * 
 * **JSON Structure:**
 * ```json
 * {
 *   "version": 3,
 *   "last_updated": "2025-11-13T07:00:00Z",
 *   "model_version": "mobilenet_v4_conv_small",
 *   "embedding_dim": 1280,
 *   "total_wallpapers": 6000,
 *   "quantized": true,
 *   "wallpapers": [...]
 * }
 * ```
 * 
 * **Compressed size:** ~10-15MB for 6000 wallpapers with 1280D embeddings.
 * 
 * @property version Manifest format version (1 = legacy, 2 = MobileNetV3, 3 = MobileNetV4)
 * @property lastUpdated ISO 8601 timestamp of last curation run
 * @property modelVersion ML model version used for embeddings (e.g., "mobilenet_v4_conv_small")
 * @property embeddingDim Embedding dimension (576 for v2, 1280 for v3)
 * @property totalWallpapers Total wallpaper count
 * @property quantized True if embeddings are quantized
 * @property wallpapers List of wallpaper metadata objects (6000+ entries)
 * 
 * @see WallpaperMetadataDto
 * @see toWallpaperEntities
 */
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

/**
 * Converts ManifestDto to a list of WallpaperMetadata entities for database storage.
 * 
 * Bulk conversion of all wallpapers in the manifest from DTOs to Room entities.
 * This is called after downloading and parsing the manifest.json file.
 * Handles both v1 (full embeddings) and v2 (quantized) formats automatically.
 * 
 * @return List of WallpaperMetadata entities ready for batch insertion
 */
fun ManifestDto.toWallpaperEntities(): List<WallpaperMetadata> {
    return wallpapers.map { it.toEntity() }
}

/**
 * Gets the total size estimate of the manifest in memory.
 * 
 * Useful for debugging and performance monitoring.
 * 
 * @return Estimated size in bytes
 */
fun ManifestDto.getEstimatedSize(): Long {
    // v2 (quantized): ~1KB per wallpaper, v1: ~4KB per wallpaper
    val perWallpaperBytes = if (quantized) 1024L else 4096L
    return wallpapers.size * perWallpaperBytes
}

/**
 * Validates the manifest structure and data.
 * 
 * Checks for:
 * - Non-empty wallpaper list
 * - Valid version number (1, 2, or 3)
 * - Proper embedding dimensions (matches embeddingDim field)
 * - Total wallpapers count matches list size
 * 
 * Handles legacy v1 manifests (MobileNetV3 576D) that lack the embeddingDim field
 * by inferring dimension from the actual embedding data.
 * 
 * @return true if manifest is valid, false otherwise
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

