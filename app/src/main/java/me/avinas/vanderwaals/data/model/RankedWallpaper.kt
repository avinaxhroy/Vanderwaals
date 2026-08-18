package me.avinas.vanderwaals.data.model

/** A wallpaper with its computed ranking score: embedding + color similarity + category bonus. */
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
