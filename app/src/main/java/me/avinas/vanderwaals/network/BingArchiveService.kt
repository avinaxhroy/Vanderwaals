package me.avinas.vanderwaals.network

import me.avinas.vanderwaals.network.dto.BingArchiveManifestDto
import me.avinas.vanderwaals.network.dto.BingArchiveWallpaperDto
import me.avinas.vanderwaals.network.dto.BingWallpaperDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit API service for Bing Wallpaper Archive integration.
 * 
 * Provides endpoints for:
 * - Fetching the latest Bing daily wallpaper (UHD 3840×2160)
 * - Accessing the historical archive from npanuhin/Bing-Wallpaper-Archive
 * - Retrieving wallpaper metadata (title, caption, subtitle, description, copyright)
 * - Multi-region support (US, GB, CA, FR, DE, IT, ES, IN, CN, JP, BR, ROW)
 * 
 * **Strategy** (from VanderwaalsStrategy.md):
 * - Tier 2 content source: Professional photography
 * - Daily wallpaper via Bing API
 * - Historical archive: 10,000+ images per region
 * - UHD quality (3840×2160)
 * - Rich metadata and attribution
 * 
 * **Bing Daily Wallpaper API**:
 * - Base URL: https://www.bing.com/
 * - Endpoint: HPImageArchive.aspx
 * - Format: JSON
 * - Parameters: format=js, idx=[offset], n=[count], mkt=[market]
 * - Returns: URL, title, copyright, date
 * 
 * **Bing Wallpaper Archive** (npanuhin/Bing-Wallpaper-Archive):
 * - Base URL: https://bing.npanuhin.me/
 * - API Format: {country}/{language}.json (e.g., US/en.json, ROW/en.json)
 * - Year-based: {country}/{language}.{year}.json (e.g., US/en.2024.json)
 * - Supported regions: US/en, GB/en, CA/en, CA/fr, FR/fr, DE/de, IT/it, ES/es, IN/en, CN/zh, JP/ja, BR/pt, ROW/en
 * - Image URL: https://bing.npanuhin.me/{country}/{language}/{date}.jpg
 * - 10,000+ historical wallpapers per region
 * - Updated daily via automated workflow
 * 
 * **Available Countries and Languages**:
 * | Country | Language | Code   | Description                |
 * |---------|----------|--------|----------------------------|
 * | US      | English  | US/en  | United States             |
 * | GB      | English  | GB/en  | United Kingdom            |
 * | CA      | English  | CA/en  | Canada (English)          |
 * | CA      | French   | CA/fr  | Canada (French)           |
 * | FR      | French   | FR/fr  | France                    |
 * | DE      | German   | DE/de  | Germany                   |
 * | IT      | Italian  | IT/it  | Italy                     |
 * | ES      | Spanish  | ES/es  | Spain                     |
 * | IN      | English  | IN/en  | India                     |
 * | CN      | Chinese  | CN/zh  | China                     |
 * | JP      | Japanese | JP/ja  | Japan                     |
 * | BR      | Portuguese| BR/pt | Brazil                    |
 * | ROW     | English  | ROW/en | Rest of World             |
 * 
 * **Usage**:
 * ```kotlin
 * @Inject lateinit var bingService: BingArchiveService
 * 
 * // Fetch today's wallpaper from Bing API
 * val response = bingService.getDailyWallpaper()
 * if (response.isSuccessful) {
 *     val wallpaper = response.body()?.images?.firstOrNull()
 *     // Process wallpaper
 * }
 * 
 * // Fetch full archive for US region
 * val archiveResponse = bingService.getArchiveManifest("US", "en")
 * if (archiveResponse.isSuccessful) {
 *     val manifest = archiveResponse.body()  // Array of wallpaper entries
 *     // Process archive wallpapers
 * }
 * 
 * // Fetch specific year for bandwidth efficiency
 * val year2024 = bingService.getArchiveManifestYear("US", "en", 2024)
 * if (year2024.isSuccessful) {
 *     val wallpapers = year2024.body()  // Only 2024 wallpapers (100-500 KB)
 *     // Process year's wallpapers
 * }
 * ```
 * 
 * @see BingWallpaperDto
 * @see BingArchiveManifestDto
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
