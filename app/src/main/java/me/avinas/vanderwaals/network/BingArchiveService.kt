package me.avinas.vanderwaals.network

import me.avinas.vanderwaals.network.dto.BingArchiveWallpaperDto
import me.avinas.vanderwaals.network.dto.BingWallpaperDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit service for Bing daily wallpaper API and the
 * npanuhin/Bing-Wallpaper-Archive historical archive.
 * Supports per-region and per-year queries.
 */
interface BingArchiveService {
    
    @GET("HPImageArchive.aspx")
    suspend fun getDailyWallpaper(
        @Query("format") format: String = "js",
        @Query("idx") idx: Int = 0,
        @Query("n") count: Int = 8,  // Fetch last 8 days (weekly)
        @Query("mkt") market: String = "en-US"
    ): Response<BingWallpaperDto>
    
    @GET("{country}/{language}.json")
    suspend fun getArchiveManifest(
        @retrofit2.http.Path("country") country: String = "US",
        @retrofit2.http.Path("language") language: String = "en"
    ): Response<List<BingArchiveWallpaperDto>>
    
    @GET("{country}/{language}.{year}.json")
    suspend fun getArchiveManifestYear(
        @retrofit2.http.Path("country") country: String,
        @retrofit2.http.Path("language") language: String,
        @retrofit2.http.Path("year") year: Int
    ): Response<List<BingArchiveWallpaperDto>>
}
