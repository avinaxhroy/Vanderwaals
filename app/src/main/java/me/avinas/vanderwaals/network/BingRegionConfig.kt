package me.avinas.vanderwaals.network

/**
 * Region/language constants for Bing Wallpaper Archive queries.
 * Based on npanuhin/Bing-Wallpaper-Archive supported regions.
 */
object BingRegionConfig {
    
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
    
    val DEFAULT_REGIONS = listOf(
        US_ENGLISH,
        GB_ENGLISH,
        ROW_ENGLISH
    )
    
    /**
     * Primary region (used as fallback if no regions are enabled).
     */
    val PRIMARY_REGION = US_ENGLISH
    
    fun getRegionsByLanguage(language: String): List<Region> {
        return ALL_REGIONS.filter { it.language == language }
    }
    
    fun getRegionById(id: String): Region? {
        return ALL_REGIONS.find { it.getId() == id }
    }
    
    fun getRegion(country: String, language: String): Region? {
        return ALL_REGIONS.find { 
            it.country.equals(country, ignoreCase = true) && 
            it.language.equals(language, ignoreCase = true) 
        }
    }
    
    fun parseEnabledRegions(regionIds: Set<String>): List<Region> {
        if (regionIds.isEmpty()) {
            return DEFAULT_REGIONS
        }
        
        return regionIds.mapNotNull { getRegionById(it) }
            .ifEmpty { DEFAULT_REGIONS }
    }
    
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
