package me.avinas.vanderwaals.network

/**
 * Configuration for Bing Wallpaper Archive regions and languages.
 * 
 * Based on npanuhin/Bing-Wallpaper-Archive supported regions.
 * Each region has different wallpapers based on Bing's localization.
 * 
 * **Supported Regions**:
 * - United States (en)
 * - United Kingdom (en)
 * - Canada (en, fr)
 * - France (fr)
 * - Germany (de)
 * - Italy (it)
 * - Spain (es)
 * - India (en)
 * - China (zh)
 * - Japan (ja)
 * - Brazil (pt)
 * - Rest of World (en)
 * 
 * **Usage**:
 * ```kotlin
 * // Get all enabled regions
 * val regions = BingRegionConfig.getEnabledRegions()
 * regions.forEach { region ->
 *     val wallpapers = bingService.getArchiveManifest(region.country, region.language)
 *     // Process wallpapers
 * }
 * 
 * // Get specific region
 * val usRegion = BingRegionConfig.US_ENGLISH
 * println(usRegion.displayName)  // "United States (English)"
 * 
 * // Get all available regions for user selection
 * val allRegions = BingRegionConfig.ALL_REGIONS
 * ```
 */
object BingRegionConfig {
    
    /**
     * Represents a Bing Wallpaper Archive region.
     * 
     * @property country Country code (US, GB, CA, FR, DE, IT, ES, IN, CN, JP, BR, ROW)
     * @property language Language code (en, fr, de, it, es, zh, ja, pt)
     * @property displayName Human-readable name for UI
     * @property flag Unicode emoji flag for the country (for UI display)
     */
    data class Region(
        val country: String,
        val language: String,
        val displayName: String,
        val flag: String
    ) {
        /**
         * Gets the API path for this region.
         * Format: {country}/{language} (e.g., "US/en")
         */
        fun getApiPath(): String = "$country/$language"
        
        /**
         * Gets a unique identifier for this region.
         * Format: {country}_{language} (e.g., "US_en")
         */
        fun getId(): String = "${country}_$language"
    }
    
    // United States
    val US_ENGLISH = Region("US", "en", "United States", "🇺🇸")
    
    // United Kingdom
    val GB_ENGLISH = Region("GB", "en", "United Kingdom", "🇬🇧")
    
    // Canada (two languages)
    val CA_ENGLISH = Region("CA", "en", "Canada (English)", "🇨🇦")
    val CA_FRENCH = Region("CA", "fr", "Canada (French)", "🇨🇦")
    
    // European countries
    val FR_FRENCH = Region("FR", "fr", "France", "🇫🇷")
    val DE_GERMAN = Region("DE", "de", "Germany", "🇩🇪")
    val IT_ITALIAN = Region("IT", "it", "Italy", "🇮🇹")
    val ES_SPANISH = Region("ES", "es", "Spain", "🇪🇸")
    
    // Asia-Pacific
    val IN_ENGLISH = Region("IN", "en", "India", "🇮🇳")
    val CN_CHINESE = Region("CN", "zh", "China", "🇨🇳")
    val JP_JAPANESE = Region("JP", "ja", "Japan", "🇯🇵")
    
    // Latin America
    val BR_PORTUGUESE = Region("BR", "pt", "Brazil", "🇧🇷")
    
    // Rest of World (fallback)
    val ROW_ENGLISH = Region("ROW", "en", "Rest of World", "🌍")
    
    /**
     * All available regions in the Bing Wallpaper Archive.
     * Ordered by priority/popularity for UI display.
     */
    val ALL_REGIONS = listOf(
        US_ENGLISH,
        GB_ENGLISH,
        CA_ENGLISH,
        CA_FRENCH,
        FR_FRENCH,
        DE_GERMAN,
        IT_ITALIAN,
        ES_SPANISH,
        IN_ENGLISH,
        CN_CHINESE,
        JP_JAPANESE,
        BR_PORTUGUESE,
        ROW_ENGLISH
    )
    
    /**
     * Default regions to sync (for fresh installs).
     * Includes most popular English-speaking regions to maximize content variety.
     */
    val DEFAULT_REGIONS = listOf(
        US_ENGLISH,
        GB_ENGLISH,
        ROW_ENGLISH
    )
    
    /**
     * Primary region (used as fallback if no regions are enabled).
     */
    val PRIMARY_REGION = US_ENGLISH
    
    /**
     * Gets regions by language code.
     * 
     * @param language Language code (en, fr, de, it, es, zh, ja, pt)
     * @return List of regions using that language
     */
    fun getRegionsByLanguage(language: String): List<Region> {
        return ALL_REGIONS.filter { it.language == language }
    }
    
    /**
     * Gets a region by its ID.
     * 
     * @param id Region ID in format "COUNTRY_language" (e.g., "US_en")
     * @return Region or null if not found
     */
    fun getRegionById(id: String): Region? {
        return ALL_REGIONS.find { it.getId() == id }
    }
    
    /**
     * Gets a region by country and language codes.
     * 
     * @param country Country code
     * @param language Language code
     * @return Region or null if not found
     */
    fun getRegion(country: String, language: String): Region? {
        return ALL_REGIONS.find { 
            it.country.equals(country, ignoreCase = true) && 
            it.language.equals(language, ignoreCase = true) 
        }
    }
    
    /**
     * Parses enabled region IDs from settings into Region objects.
     * 
     * @param regionIds Set of region IDs (e.g., setOf("US_en", "GB_en"))
     * @return List of Region objects
     */
    fun parseEnabledRegions(regionIds: Set<String>): List<Region> {
        if (regionIds.isEmpty()) {
            return DEFAULT_REGIONS
        }
        
        return regionIds.mapNotNull { getRegionById(it) }
            .ifEmpty { DEFAULT_REGIONS }
    }
    
    /**
     * Groups regions by language for organized UI display.
     * 
     * @return Map of language name to list of regions
     */
    fun getRegionsGroupedByLanguage(): Map<String, List<Region>> {
        return ALL_REGIONS.groupBy { 
            when (it.language) {
                "en" -> "English"
                "fr" -> "French"
                "de" -> "German"
                "it" -> "Italian"
                "es" -> "Spanish"
                "zh" -> "Chinese"
                "ja" -> "Japanese"
                "pt" -> "Portuguese"
                else -> "Other"
            }
        }
    }
}
