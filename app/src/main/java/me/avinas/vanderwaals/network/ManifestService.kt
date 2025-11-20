package me.avinas.vanderwaals.network

import me.avinas.vanderwaals.network.dto.ManifestDto
import retrofit2.Response
import retrofit2.http.GET

/**
 * Retrofit service interface for downloading the wallpaper manifest.
 * 
 * Provides a single endpoint to fetch the pre-computed manifest.json file
 * from jsDelivr CDN or GitHub. The manifest contains metadata and embeddings
 * for all 6000+ curated wallpapers.
 * 
 * **Base URL (configured in NetworkModule):**
 * - Primary: `https://cdn.jsdelivr.net/gh/{owner}/{repo}@{branch}/`
 * - Fallback: GitHub raw URL
 * 
 * **Manifest file:** `manifest.json`
 * - Size: ~6MB compressed
 * - Format: JSON with wallpaper metadata array
 * - Updated: Weekly via GitHub Actions
 * 
 * **Usage:**
 * ```kotlin
 * @Inject lateinit var manifestService: ManifestService
 * 
 * viewModelScope.launch {
 *     val response = manifestService.getManifest()
 *     if (response.isSuccessful) {
 *         val manifest = response.body()!!
 *         val entities = manifest.toWallpaperEntities()
 *         database.wallpaperMetadataDao().insertAll(entities)
 *     } else {
 *         Log.e("Manifest", "Failed: ${response.code()}")
 *     }
 * }
 * ```
 * 
 * **Error Handling:**
 * - Network errors: IOException (no connection, timeout)
 * - HTTP errors: Response code 4xx/5xx
 * - Parse errors: JsonSyntaxException (malformed JSON)
 * 
 * @see ManifestDto
 * @see ManifestRepository
 */
interface ManifestService {
    
    /**
     * Downloads the wallpaper manifest from the CDN.
     * 
     * Fetches the complete manifest.json file containing pre-computed metadata
     * for all wallpapers. The file is cached by jsDelivr CDN, so subsequent
     * requests within the same week will be served from cache.
     * 
     * **Endpoint:** `GET /manifest.json`
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
     * 
     * @return Response wrapper containing ManifestDto on success
     * 
     * Example:
     * ```kotlin
     * suspend fun syncManifest(): Result<Int> {
     *     return try {
     *         val response = manifestService.getManifest()
     *         
     *         if (!response.isSuccessful) {
     *             return Result.failure(
     *                 Exception("HTTP ${response.code()}: ${response.message()}")
     *             )
     *         }
     *         
     *         val manifest = response.body() 
     *             ?: return Result.failure(Exception("Empty response body"))
     *         
     *         if (!manifest.isValid()) {
     *             return Result.failure(Exception("Invalid manifest structure"))
     *         }
     *         
     *         val entities = manifest.toWallpaperEntities()
     *         wallpaperDao.insertAll(entities)
     *         
     *         Result.success(entities.size)
     *     } catch (e: IOException) {
     *         Result.failure(Exception("Network error: ${e.message}"))
     *     } catch (e: JsonSyntaxException) {
     *         Result.failure(Exception("Parse error: ${e.message}"))
     *     }
     * }
     * ```
     */
    @GET("app/src/main/assets/manifest.json")
    suspend fun getManifest(): Response<ManifestDto>
}
