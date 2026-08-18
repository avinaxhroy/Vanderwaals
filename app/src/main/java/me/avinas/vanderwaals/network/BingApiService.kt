package me.avinas.vanderwaals.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import me.avinas.vanderwaals.network.dto.BingWallpaperDto

interface BingApiService {
    
    @GET("HPImageArchive.aspx")
    suspend fun getWallpapers(
        @Query("format") format: String = "js",
        @Query("idx") idx: Int = 0,
        @Query("n") count: Int = 1,
        @Query("mkt") market: String = "en-US"
    ): Response<BingWallpaperDto>
}
