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
    
    /**
     * Fetches the latest Bing daily wallpaper(s) from HPImageArchive API.
     * 
     * Retrieves today's or recent Bing homepage wallpapers with full metadata.
     * By default, fetches 8 wallpapers starting from today (covers the last week).
     * 
     * **API Details**:
     * - Base URL: https://www.bing.com/
     * - Endpoint: HPImageArchive.aspx
     * - Rate Limit: No official limit (public API)
     * - Cache: Response changes daily at ~00:00 UTC
     * 
     * **URL Format**:
     * - Returned `url` is relative, prepend "https://www.bing.com"
     * - UHD resolution: append "_UHD.jpg" to urlbase
     * - Example: `https://www.bing.com/th?id=OHR.WinterBerries_EN-US_UHD.jpg`
     * 
     * @param format Response format (always "js" for JSON)
     * @param idx Index offset for historical wallpapers (0 = today, 1 = yesterday, etc.)
     * @param count Number of wallpapers to return (1-8, default 8 for weekly coverage)
     * @param market Market/locale (default "en-US", also: "zh-CN", "ja-JP", etc.)
     * @return Response containing list of Bing wallpapers with metadata
     * 
     * Example:
     * ```kotlin
     * suspend fun syncBingDaily() {
     *     val response = bingService.getDailyWallpaper(count = 8)
     *     
     *     if (!response.isSuccessful) {
     *         Log.e(TAG, "Failed: ${response.code()}")
     *         return
     *     }
     *     
     *     val wallpapers = response.body()?.images ?: return
     *     
     *     wallpapers.forEach { image ->
     *         val fullUrl = "https://www.bing.com${image.urlbase}_UHD.jpg"
     *         val entity = WallpaperMetadata(
     *             id = "bing_${image.startdate}",
     *             url = fullUrl,
     *             source = "bing",
     *             attribution = image.copyright
     *             // ... other fields
     *         )
     *         database.insert(entity)
     *     }
     * }
     * ```
     */
    @GET("HPImageArchive.aspx")
    suspend fun getDailyWallpaper(
        @Query("format") format: String = "js",
        @Query("idx") idx: Int = 0,
        @Query("n") count: Int = 8,  // Fetch last 8 days (weekly)
        @Query("mkt") market: String = "en-US"
    ): Response<BingWallpaperDto>
    
    /**
     * Fetches the full archive manifest from npanuhin/Bing-Wallpaper-Archive.
     * 
     * The manifest contains metadata for 10,000+ historical Bing wallpapers organized
     * by date. This allows bulk import of high-quality professional photography from
     * multiple regions.
     * 
     * **Archive Structure**:
     * - Base URL: https://bing.npanuhin.me/
     * - Full Archive API: {country}/{language}.json (e.g., US/en.json)
     * - Image URL: https://bing.npanuhin.me/{country}/{language}/{date}.jpg
     * - Resolution: 3840×2160 (UHD)
     * 
     * **JSON Response Format**:
     * ```json
     * [
     *   {
     *     "title": "Winter Berries",
     *     "caption": "Frozen beauty",
     *     "subtitle": "Nature's art in winter",
     *     "copyright": "Frozen berries © Photographer/Getty Images",
     *     "description": "Detailed description...",
     *     "date": "2024-01-15",
     *     "bing_url": "https://www.bing.com/th?id=OHR.WinterBerries...",
     *     "url": "https://bing.npanuhin.me/US/en/2024-01-15.jpg"
     *   }
     * ]
     * ```
     * 
     * **Important Notes**:
     * - Files are large (2-5 MB per region)
     * - Images sorted by date (oldest first, newest last)
     * - `bing_url` may be null for older images
     * - All fields except `date` and `url` may be null
     * 
     * @param country Country code (US, GB, CA, FR, DE, IT, ES, IN, CN, JP, BR, ROW)
     * @param language Language code (en, fr, de, it, es, zh, ja, pt)
     * @return Response containing array of wallpaper entries
     * 
     * Example:
     * ```kotlin
     * suspend fun syncFullArchive(country: String = "US", language: String = "en") {
     *     val response = bingService.getArchiveManifest(country, language)
     *     
     *     if (!response.isSuccessful) {
     *         Log.e(TAG, "Failed: HTTP ${response.code()}")
     *         return
     *     }
     *     
     *     val wallpapers = response.body() ?: return
     *     Log.d(TAG, "Fetched ${wallpapers.size} wallpapers from $country/$language")
     *     
     *     // Process most recent wallpapers only (reduce load)
     *     val recent = wallpapers
     *         .sortedByDescending { it.date }
     *         .take(500)  // Last 500 wallpapers
     *     
     *     recent.forEach { wallpaper ->
     *         val entity = wallpaper.toWallpaperMetadata()
     *         database.insert(entity)
     *     }
     * }
     * ```
     */
    @GET("{country}/{language}.json")
    suspend fun getArchiveManifest(
        @retrofit2.http.Path("country") country: String = "US",
        @retrofit2.http.Path("language") language: String = "en"
    ): Response<List<BingArchiveWallpaperDto>>
    
    /**
     * Fetches archive manifest for a specific year (bandwidth-efficient).
     * 
     * Year-based APIs are minified and typically 100-500 KB (vs 2-5 MB for full archive).
     * Perfect for incremental sync strategies and reducing bandwidth usage.
     * 
     * **URL Format**: https://bing.npanuhin.me/{country}/{language}.{year}.json
     * 
     * **Benefits**:
     * - Much smaller file size (100-500 KB vs 2-5 MB)
     * - Faster download and parsing
     * - Ideal for incremental sync (sync current year only)
     * - Reduces mobile data usage
     * 
     * @param country Country code (US, GB, CA, FR, DE, IT, ES, IN, CN, JP, BR, ROW)
     * @param language Language code (en, fr, de, it, es, zh, ja, pt)
     * @param year Year to fetch (e.g., 2024, 2023, 2022)
     * @return Response containing array of wallpaper entries for that year, or 404 if no data
     * 
     * Example:
     * ```kotlin
     * suspend fun syncCurrentYear() {
     *     val currentYear = Calendar.getInstance().get(Calendar.YEAR)
     *     val response = bingService.getArchiveManifestYear("US", "en", currentYear)
     *     
     *     if (!response.isSuccessful) {
     *         if (response.code() == 404) {
     *             Log.w(TAG, "No data for year $currentYear")
     *         }
     *         return
     *     }
     *     
     *     val wallpapers = response.body() ?: return
     *     Log.d(TAG, "Synced ${wallpapers.size} wallpapers from $currentYear")
     *     
     *     wallpapers.forEach { wallpaper ->
     *         val entity = wallpaper.toWallpaperMetadata()
     *         database.insert(entity)
     *     }
     * }
     * 
     * // Sync last 3 years
     * suspend fun syncRecentYears() {
     *     val currentYear = Calendar.getInstance().get(Calendar.YEAR)
     *     for (year in (currentYear - 2)..currentYear) {
     *         syncYearData(year)
     *     }
     * }
     * ```
     */
    @GET("{country}/{language}.{year}.json")
    suspend fun getArchiveManifestYear(
        @retrofit2.http.Path("country") country: String,
        @retrofit2.http.Path("language") language: String,
        @retrofit2.http.Path("year") year: Int
    ): Response<List<BingArchiveWallpaperDto>>
}
