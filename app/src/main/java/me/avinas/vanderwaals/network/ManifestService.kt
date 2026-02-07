package me.avinas.vanderwaals.network

import me.avinas.vanderwaals.network.dto.ManifestDto
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.HEAD
import retrofit2.http.Header

/**
 * Retrofit service interface for downloading the wallpaper manifest.
 * 
 * Provides endpoints to fetch the pre-computed manifest.json file
 * from jsDelivr CDN or GitHub. The manifest contains metadata and embeddings
 * for all 6000+ curated wallpapers from multiple GitHub repositories.
 * 
 * **Note:** The manifest contains wallpapers from ALL configured repositories
 * (dharmx/walls, D3Ext/aesthetic-wallpapers, makccr/wallpapers, etc.),
 * not just a single repository.
 * 
 * **Base URL (configured in NetworkModule):**
 * - Primary: `https://cdn.jsdelivr.net/gh/{owner}/{repo}@{branch}/`
 * - Fallback: GitHub raw URL
 * 
 * **Smart Update Support:**
 * - Use [checkManifestHeaders] to check if manifest was updated without downloading
 * - Use [getManifestConditional] with If-Modified-Since header to skip unchanged manifests
 * 
 * **Manifest file:** `manifest_v3.json`
 * - Size: ~10-15MB compressed (with MobileNetV4 1280D embeddings)
 * - Format: JSON with wallpaper metadata array
 * - Updated: Weekly via GitHub Actions
 * - Note: v3.8.x uses manifest.json, v4.0.0 uses manifest_v2.json, v5.0.0+ uses manifest_v3.json
 * 
 * @see ManifestDto
 * @see ManifestRepository
 */
interface ManifestService {
    
    /**
     * Downloads the full wallpaper manifest from the CDN.
     * 
     * Fetches the complete manifest.json file containing pre-computed metadata
     * for all wallpapers. The file is cached by jsDelivr CDN, so subsequent
     * requests within the same week will be served from cache.
     * 
     * **Endpoint:** `GET /app/src/main/assets/manifest.json`
     * 
     * **Response:**
     * - Success (200): ManifestDto with wallpaper list
     * - Not Found (404): Manifest file missing
     * - Server Error (5xx): CDN or GitHub issues
     * 
     * **Cache Headers:**
     * jsDelivr automatically adds cache headers:
     * - `Cache-Control: public, max-age=604800` (7 days)
     * - `ETag` for conditional requests
     * - `Last-Modified` timestamp
     * 
     * @return Response wrapper containing ManifestDto on success
     */
    @GET("app/src/main/assets/manifest_v3.json")
    suspend fun getManifest(): Response<ManifestDto>
    
    /**
     * Checks manifest metadata without downloading the full file.
     * 
     * Uses HTTP HEAD request to get headers only. Useful for checking
     * if the manifest has been updated since last sync before downloading.
     * 
     * **Key Headers Returned:**
     * - `Last-Modified`: Date when manifest was last updated
     * - `ETag`: Unique identifier for this version
     * - `Content-Length`: Size of the manifest file
     * 
     * @return Response with headers only, no body
     */
    @HEAD("app/src/main/assets/manifest_v3.json")
    suspend fun checkManifestHeaders(): Response<Void>
    
    /**
     * Downloads manifest only if modified since the given date.
     * 
     * Uses HTTP If-Modified-Since conditional request to avoid downloading
     * the manifest if it hasn't changed. Returns 304 Not Modified if unchanged.
     * 
     * **Usage:**
     * ```kotlin
     * val lastModified = prefs.getString("manifest_last_modified", null)
     * val response = manifestService.getManifestConditional(lastModified)
     * 
     * when (response.code()) {
     *     200 -> {
     *         // Manifest updated, process new data
     *         val manifest = response.body()!!
     *         prefs.edit().putString("manifest_last_modified", 
     *             response.headers()["Last-Modified"]).apply()
     *     }
     *     304 -> {
     *         // Not modified, skip download
     *         Log.d("Sync", "Manifest unchanged, skipping download")
     *     }
     * }
     * ```
     * 
     * @param ifModifiedSince Last-Modified header from previous successful download
     * @return Response with manifest (200) or empty body (304)
     */
    @GET("app/src/main/assets/manifest_v3.json")
    suspend fun getManifestConditional(
        @Header("If-Modified-Since") ifModifiedSince: String?
    ): Response<ManifestDto>
    
    // =========================================================================
    // BING MANIFEST ENDPOINTS
    // =========================================================================
    
    /**
     * Downloads the Bing wallpaper manifest (lite version - last 2 years).
     * 
     * Contains ~700 curated Bing wallpapers with MobileNetV4-Conv-Small 1280D embeddings.
     * Recommended for most users due to smaller size (~2MB).
     * 
     * @return Response containing ManifestDto on success
     */
    @GET("app/src/main/assets/bing_manifest_lite_v2.json")
    suspend fun getBingManifestLite(): Response<ManifestDto>
    
    /**
     * Downloads the Bing manifest (lite) only if modified since the given date.
     * Returns 304 Not Modified if unchanged.
     */
    @GET("app/src/main/assets/bing_manifest_lite_v2.json")
    suspend fun getBingManifestLiteConditional(
        @Header("If-Modified-Since") ifModifiedSince: String?
    ): Response<ManifestDto>
    
    /**
     * Downloads the full Bing wallpaper manifest (2009-present).
     * 
     * Contains ~5400+ curated Bing wallpapers with MobileNetV4-Conv-Small 1280D embeddings.
     * Larger download (~15MB) but includes complete archive.
     * 
     * @return Response containing ManifestDto on success
     */
    @GET("app/src/main/assets/bing_manifest_full_v2.json")
    suspend fun getBingManifestFull(): Response<ManifestDto>
    
    /**
     * Downloads the Bing manifest (full) only if modified since the given date.
     * Returns 304 Not Modified if unchanged.
     */
    @GET("app/src/main/assets/bing_manifest_full_v2.json")
    suspend fun getBingManifestFullConditional(
        @Header("If-Modified-Since") ifModifiedSince: String?
    ): Response<ManifestDto>
    
    /**
     * Checks Bing lite manifest headers without downloading.
     * Useful for checking Last-Modified date before sync.
     */
    @HEAD("app/src/main/assets/bing_manifest_lite_v2.json")
    suspend fun checkBingManifestLiteHeaders(): Response<Void>
    
    /**
     * Checks Bing full manifest headers without downloading.
     */
    @HEAD("app/src/main/assets/bing_manifest_full_v2.json")
    suspend fun checkBingManifestFullHeaders(): Response<Void>
}
