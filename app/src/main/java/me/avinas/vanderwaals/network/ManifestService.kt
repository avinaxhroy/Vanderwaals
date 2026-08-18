package me.avinas.vanderwaals.network

import me.avinas.vanderwaals.network.dto.ManifestDto
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.HEAD
import retrofit2.http.Header

/**
 * Retrofit interface for downloading the wallpaper manifest (manifest_v3.json)
 * from jsDelivr CDN. Supports conditional requests via If-Modified-Since.
 */
interface ManifestService {
    
    @GET("app/src/main/assets/manifest_v3.json")
    suspend fun getManifest(): Response<ManifestDto>
    
    @HEAD("app/src/main/assets/manifest_v3.json")
    suspend fun checkManifestHeaders(): Response<Void>
    
    @GET("app/src/main/assets/manifest_v3.json")
    suspend fun getManifestConditional(
        @Header("If-Modified-Since") ifModifiedSince: String?
    ): Response<ManifestDto>
    
    /**
     * Lite Bing manifest (last 2 years): ~700 curated wallpapers, ~2MB.
     */
    @GET("app/src/main/assets/bing_manifest_lite_v2.json")
    suspend fun getBingManifestLite(): Response<ManifestDto>
    
    @GET("app/src/main/assets/bing_manifest_lite_v2.json")
    suspend fun getBingManifestLiteConditional(
        @Header("If-Modified-Since") ifModifiedSince: String?
    ): Response<ManifestDto>
    
    /**
     * Full Bing manifest (2009-present): ~5400+ wallpapers, ~15MB.
     */
    @GET("app/src/main/assets/bing_manifest_full_v2.json")
    suspend fun getBingManifestFull(): Response<ManifestDto>
    
    @GET("app/src/main/assets/bing_manifest_full_v2.json")
    suspend fun getBingManifestFullConditional(
        @Header("If-Modified-Since") ifModifiedSince: String?
    ): Response<ManifestDto>
    
    @HEAD("app/src/main/assets/bing_manifest_lite_v2.json")
    suspend fun checkBingManifestLiteHeaders(): Response<Void>
    
    @HEAD("app/src/main/assets/bing_manifest_full_v2.json")
    suspend fun checkBingManifestFullHeaders(): Response<Void>
}
