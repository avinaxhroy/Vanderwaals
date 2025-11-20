package me.avinas.vanderwaals.network.dto

/**
 * Data transfer object for Bing wallpaper API response.
 * 
 * Maps Bing's HPImageArchive API JSON structure to Kotlin data class.
 * 
 * API response structure:
 * ```json
 * {
 *   "images": [
 *     {
 *       "startdate": "20240115",
 *       "fullstartdate": "202401150800",
 *       "enddate": "20240116",
 *       "url": "/th?id=OHR.WinterBerries_EN-US1234567890_UHD.jpg",
 *       "urlbase": "/th?id=OHR.WinterBerries_EN-US1234567890",
 *       "copyright": "Frozen berries © Photographer Name/Getty Images",
 *       "copyrightlink": "https://www.bing.com/search?q=frozen+berries",
 *       "title": "Winter Berries",
 *       "hsh": "abc123def456"
 *     }
 *   ]
 * }
 * ```
 * 
 * @property images List of wallpaper image objects
 */
data class BingWallpaperDto(
    val images: List<BingImageDto>
)

/**
 * Individual Bing wallpaper image metadata.
 * 
 * @property startdate Start date in YYYYMMDD format
 * @property enddate End date in YYYYMMDD format
 * @property url Relative URL path (append to https://www.bing.com)
 * @property urlbase Base URL for different resolutions
 * @property copyright Copyright and attribution text
 * @property copyrightlink Link to Bing search for subject
 * @property title Wallpaper title
 * @property hsh Hash identifier
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
