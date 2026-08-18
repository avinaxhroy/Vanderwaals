package me.avinas.vanderwaals.network.dto

data class BingWallpaperDto(
    val images: List<BingImageDto>
)

/**
 * Individual Bing wallpaper image metadata.
 * `url`/`urlbase` are relative paths — append "https://www.bing.com".
 */
data class BingImageDto(
    val startdate: String,
    val enddate: String,
    val url: String,
    val urlbase: String,
    val copyright: String,
    val copyrightlink: String,
    val title: String,
    val hsh: String
)
