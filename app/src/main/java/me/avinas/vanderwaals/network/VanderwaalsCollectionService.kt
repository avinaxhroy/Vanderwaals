package me.avinas.vanderwaals.network

import me.avinas.vanderwaals.network.dto.ManifestDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header

/**
 * Retrofit interface for downloading the Vanderwaals Collection wallpaper manifest.
 *
 * Served from the dedicated Vanderwaals API at `https://vanderwaalsapi.2626688.xyz/`.
 * The collection is the app's own curated wallpaper set, shipped with
 * MobileNetV4-Conv-Small 1280D embeddings and color/category metadata in the
 * same [ManifestDto] format used by the GitHub and Bing sources.
 *
 * **Endpoints:**
 * - `cat/lite.json`  — curated subset (recommended for getting started)
 * - `cat/full.json`  — complete Vanderwaals Collection archive
 *
 * Both endpoints support conditional requests via the `If-Modified-Since`
 * header, returning 304 Not Modified when the manifest is unchanged.
 */
interface VanderwaalsCollectionService {

    /**
     * Downloads the lite Vanderwaals Collection manifest.
     *
     * @return Response containing [ManifestDto] on success
     */
    @GET("cat/lite.json")
    suspend fun getVanderwaalsCollectionLite(): Response<ManifestDto>

    /**
     * Downloads the lite manifest only if modified since the given date.
     * Returns 304 Not Modified if unchanged.
     *
     * @param ifModifiedSince Last-Modified header from a previous successful download
     */
    @GET("cat/lite.json")
    suspend fun getVanderwaalsCollectionLiteConditional(
        @Header("If-Modified-Since") ifModifiedSince: String?
    ): Response<ManifestDto>

    /**
     * Downloads the full Vanderwaals Collection manifest (complete archive).
     *
     * @return Response containing [ManifestDto] on success
     */
    @GET("cat/full.json")
    suspend fun getVanderwaalsCollectionFull(): Response<ManifestDto>

    /**
     * Downloads the full manifest only if modified since the given date.
     * Returns 304 Not Modified if unchanged.
     *
     * @param ifModifiedSince Last-Modified header from a previous successful download
     */
    @GET("cat/full.json")
    suspend fun getVanderwaalsCollectionFullConditional(
        @Header("If-Modified-Since") ifModifiedSince: String?
    ): Response<ManifestDto>
}
