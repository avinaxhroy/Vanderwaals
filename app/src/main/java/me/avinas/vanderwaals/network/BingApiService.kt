package me.avinas.vanderwaals.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import me.avinas.vanderwaals.network.dto.BingWallpaperDto

/**
 * Retrofit API service for Bing wallpaper content.
 * 
 * Provides endpoints for:
 * - Fetching Bing's daily wallpaper (UHD 3840×2160)
 * - Accessing historical wallpaper archive (10,000+ images)
 * - Retrieving wallpaper metadata (title, description, attribution)
 * 
 * Bing Wallpaper API:
 * - Endpoint: https://www.bing.com/HPImageArchive.aspx
 * - Format: JSON
 * - Parameters: format=js, idx=[offset], n=[count], mkt=[market]
 * - Returns: URL, title, copyright, date
 * 
 * Archive integration:
 * - GitHub: npanuhin/Bing-Wallpaper-Archive
 * - Pre-downloaded historical wallpapers
 * - Rich metadata and source attribution
 * 
 * @see me.avinas.vanderwaals.network.dto.BingWallpaperDto
 * @see me.avinas.vanderwaals.domain.usecase.SyncWallpaperCatalogUseCase
 */
interface BingApiService {
    
    /**
     * Fetches Bing wallpaper(s) from the HPImageArchive API.
     * 
     * @param format Response format (always "js" for JSON)
     * @param idx Index offset for historical wallpapers (0 = today, 1 = yesterday, etc.)
     * @param n Number of wallpapers to return (1-8)
     * @param mkt Market/locale (e.g., "en-US", "zh-CN")
     * @return Response containing list of Bing wallpapers
     */
    @GET("HPImageArchive.aspx")
    suspend fun getWallpapers(
        @Query("format") format: String = "js",
        @Query("idx") idx: Int = 0,
        @Query("n") count: Int = 1,
        @Query("mkt") market: String = "en-US"
    ): Response<BingWallpaperDto>
}
