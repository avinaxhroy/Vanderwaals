package me.avinas.vanderwaals.data.model

/**
 * Domain model representing a wallpaper with its computed ranking score.
 * 
 * Used as the output of the similarity calculation algorithm. Contains the original
 * wallpaper metadata plus the final ranking score that combines embedding similarity,
 * color similarity, and category bonus.
 * 
 * This model is used for:
 * - Sorting wallpapers by relevance to user preferences
 * - Displaying ranked results in the UI
 * - Managing wallpaper queue for auto-rotation
 * 
 * @property wallpaperId Reference to WallpaperMetadata.id
 * @property url Download URL for the wallpaper
 * @property thumbnailUrl Preview thumbnail URL
 * @property category Wallpaper category
 * @property finalScore Combined ranking score (0.0 to 1.0)
 * @property embeddingScore Cosine similarity score (0.0 to 1.0)
 * @property colorScore Color similarity score (0.0 to 1.0)
 * @property categoryBonus Category match bonus (0.0 to 0.1)
 */
data class RankedWallpaper(
    val wallpaperId: String,
    val url: String,
    val thumbnailUrl: String,
    val category: String,
    val finalScore: Float,
    val embeddingScore: Float,
    val colorScore: Float,
    val categoryBonus: Float
)
