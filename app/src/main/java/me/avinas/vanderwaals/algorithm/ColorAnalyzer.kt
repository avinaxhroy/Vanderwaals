package me.avinas.vanderwaals.algorithm

import android.graphics.Color
import kotlin.math.*

/**
 * Advanced color analysis utility for wallpaper personalization.
 * 
 * Provides sophisticated color matching beyond basic RGB comparison:
 * - HSV color space analysis (hue, saturation, value)
 * - Color harmony detection (complementary, analogous, triadic)
 * - Dominant vs accent color weighting
 * - Warm/cool tone classification
 * - Vibrant/muted intensity detection
 * 
 * **Use Cases:**
 * - Learning color preferences from feedback
 * - Matching wallpapers to user's color palette
 * - Detecting color patterns across liked wallpapers
 * - Providing color-aware recommendations
 * 
 * **Color Spaces:**
 * - RGB: Device color representation (0-255 per channel)
 * - HSV: Perceptual color model (Hue 0-360°, Saturation 0-1, Value 0-1)
 * - HSL: Alternative perceptual model (used for lightness)
 * 
 * @see ColorPreference
 * @see SelectNextWallpaperUseCase
 */
object ColorAnalyzer {
    
    /**
     * Analyzes a color palette and returns detailed color characteristics.
     * 
     * @param colors List of hex color codes
     * @return ColorPaletteAnalysis with detailed insights
     */
    fun analyzePalette(colors: List<String>): ColorPaletteAnalysis {
        if (colors.isEmpty()) {
            return ColorPaletteAnalysis.empty()
        }
        
        val rgbColors = colors.mapNotNull { parseHexToRgb(it) }
        if (rgbColors.isEmpty()) {
            return ColorPaletteAnalysis.empty()
        }
        
        val hsvColors = rgbColors.map { rgbToHsv(it) }
        
        return ColorPaletteAnalysis(
            dominantColor = rgbColors.first(),
            accentColors = rgbColors.drop(1).take(2),
            averageHue = hsvColors.map { it.hue }.average().toFloat(),
            averageSaturation = hsvColors.map { it.saturation }.average().toFloat(),
            averageValue = hsvColors.map { it.value }.average().toFloat(),
            isWarmToned = isWarmToned(hsvColors),
            isVibrant = isVibrant(hsvColors),
            colorHarmony = detectColorHarmony(hsvColors),
            colorCount = colors.size
        )
    }
    
    /**
     * Calculates similarity between two color palettes using advanced metrics.
     * 
     * Considers:
     * - Hue similarity (perceptual color difference)
     * - Saturation similarity (vibrance matching)
     * - Value similarity (brightness matching)
     * - Dominant color weight (3x importance)
     * - Accent color matching
     * 
     * @param palette1 First color palette
     * @param palette2 Second color palette
     * @return Similarity score 0.0 (completely different) to 1.0 (identical)
     */
    fun calculatePaletteSimilarity(palette1: ColorPaletteAnalysis, palette2: ColorPaletteAnalysis): Float {
        if (palette1.isEmpty() || palette2.isEmpty()) {
            return 0.5f // Neutral when data unavailable
        }
        
        // Hue similarity (circular distance on color wheel)
        val hueSimilarity = calculateCircularSimilarity(palette1.averageHue, palette2.averageHue, 360f)
        
        // Saturation similarity (linear)
        val saturationSimilarity = 1f - abs(palette1.averageSaturation - palette2.averageSaturation)
        
        // Value (brightness) similarity (linear)
        val valueSimilarity = 1f - abs(palette1.averageValue - palette2.averageValue)
        
        // Dominant color RGB distance (weighted heavily)
        val dominantSimilarity = calculateRgbSimilarity(palette1.dominantColor, palette2.dominantColor)
        
        // Accent color matching (if both have accents)
        val accentSimilarity = if (palette1.accentColors.isNotEmpty() && palette2.accentColors.isNotEmpty()) {
            val accent1 = palette1.accentColors.first()
            val accent2 = palette2.accentColors.first()
            calculateRgbSimilarity(accent1, accent2)
        } else {
            0.5f // Neutral if no accents
        }
        
        // Warm/cool tone matching bonus
        val toneBonus = if (palette1.isWarmToned == palette2.isWarmToned) 0.1f else 0f
        
        // Vibrant/muted matching bonus
        val vibrancyBonus = if (palette1.isVibrant == palette2.isVibrant) 0.1f else 0f
        
        // Weighted combination
        return (dominantSimilarity * 0.35f +      // 35% dominant color
                hueSimilarity * 0.20f +           // 20% hue
                saturationSimilarity * 0.15f +    // 15% saturation
                valueSimilarity * 0.15f +         // 15% value/brightness
                accentSimilarity * 0.15f +        // 15% accent
                toneBonus + vibrancyBonus)        // Bonuses
            .coerceIn(0f, 1f)
    }
    
    /**
     * Extracts color preferences from a list of liked/disliked wallpapers.
     * 
     * Analyzes patterns:
     * - Most common hue ranges
     * - Preferred saturation level
     * - Preferred brightness level
     * - Warm vs cool preference
     * - Vibrant vs muted preference
     * 
     * @param likedPalettes Palettes from liked wallpapers
     * @param dislikedPalettes Palettes from disliked wallpapers
     * @return ColorPreferenceProfile with learned preferences
     */
    fun extractColorPreferences(
        likedPalettes: List<ColorPaletteAnalysis>,
        dislikedPalettes: List<ColorPaletteAnalysis>
    ): ColorPreferenceProfile {
        if (likedPalettes.isEmpty()) {
            return ColorPreferenceProfile.neutral()
        }
        
        val likedHues = likedPalettes.map { it.averageHue }
        val likedSaturations = likedPalettes.map { it.averageSaturation }
        val likedValues = likedPalettes.map { it.averageValue }
        
        val warmCount = likedPalettes.count { it.isWarmToned }
        val vibrantCount = likedPalettes.count { it.isVibrant }
        
        return ColorPreferenceProfile(
            preferredHueRange = calculatePreferredHueRange(likedHues),
            preferredSaturation = likedSaturations.average().toFloat(),
            preferredBrightness = likedValues.average().toFloat(),
            prefersWarmTones = warmCount > likedPalettes.size / 2,
            prefersVibrant = vibrantCount > likedPalettes.size / 2,
            confidence = min(likedPalettes.size / 10f, 1f) // Confidence grows to 100% after 10 likes
        )
    }
    
    /**
     * Calculates color preference score for a palette based on learned preferences.
     * 
     * @param palette Wallpaper color palette to score
     * @param preferences Learned color preferences
     * @return Score from -1.0 (strongly dislike) to +1.0 (strongly like)
     */
    fun calculateColorPreferenceScore(
        palette: ColorPaletteAnalysis,
        preferences: ColorPreferenceProfile
    ): Float {
        if (palette.isEmpty() || preferences.confidence < 0.1f) {
            return 0f // Neutral when insufficient data
        }
        
        // Hue match (circular distance)
        val hueInRange = isHueInRange(palette.averageHue, preferences.preferredHueRange)
        val hueScore = if (hueInRange) 1f else -0.5f
        
        // Saturation match
        val saturationDiff = abs(palette.averageSaturation - preferences.preferredSaturation)
        val saturationScore = 1f - saturationDiff
        
        // Brightness match
        val brightnessDiff = abs(palette.averageValue - preferences.preferredBrightness)
        val brightnessScore = 1f - brightnessDiff
        
        // Tone match
        val toneScore = if (palette.isWarmToned == preferences.prefersWarmTones) 1f else -0.5f
        
        // Vibrancy match
        val vibrancyScore = if (palette.isVibrant == preferences.prefersVibrant) 1f else -0.5f
        
        // Weighted combination
        val rawScore = (hueScore * 0.30f +           // 30% hue
                        saturationScore * 0.25f +     // 25% saturation
                        brightnessScore * 0.25f +     // 25% brightness
                        toneScore * 0.10f +           // 10% warm/cool
                        vibrancyScore * 0.10f)        // 10% vibrant/muted
        
        // Apply confidence factor
        return rawScore * preferences.confidence
    }
    
    // ========== Private Helper Methods ==========
    
    private fun parseHexToRgb(hex: String): RgbColor? {
        return try {
            val cleanHex = hex.removePrefix("#")
            if (cleanHex.length != 6) return null
            
            val r = cleanHex.substring(0, 2).toInt(16)
            val g = cleanHex.substring(2, 4).toInt(16)
            val b = cleanHex.substring(4, 6).toInt(16)
            
            RgbColor(r, g, b)
        } catch (e: Exception) {
            null
        }
    }
    
    private fun rgbToHsv(rgb: RgbColor): HsvColor {
        val hsv = FloatArray(3)
        Color.RGBToHSV(rgb.r, rgb.g, rgb.b, hsv)
        return HsvColor(hsv[0], hsv[1], hsv[2])
    }
    
    private fun isWarmToned(hsvColors: List<HsvColor>): Boolean {
        val avgHue = hsvColors.map { it.hue }.average().toFloat()
        // Warm tones: Red (0-60°) and Yellow-Orange (300-360°)
        return avgHue < 60f || avgHue > 300f
    }
    
    private fun isVibrant(hsvColors: List<HsvColor>): Boolean {
        val avgSaturation = hsvColors.map { it.saturation }.average().toFloat()
        val avgValue = hsvColors.map { it.value }.average().toFloat()
        // Vibrant: High saturation (>0.5) and moderate-high value (>0.4)
        return avgSaturation > 0.5f && avgValue > 0.4f
    }
    
    private fun detectColorHarmony(hsvColors: List<HsvColor>): ColorHarmony {
        if (hsvColors.size < 2) return ColorHarmony.MONOCHROMATIC
        
        val hues = hsvColors.map { it.hue }
        val hueRange = hues.maxOrNull()!! - hues.minOrNull()!!
        
        return when {
            hueRange < 30f -> ColorHarmony.MONOCHROMATIC  // Same hue family
            hueRange < 60f -> ColorHarmony.ANALOGOUS      // Adjacent on color wheel
            hueRange > 150f -> ColorHarmony.COMPLEMENTARY // Opposite colors
            else -> ColorHarmony.TRIADIC                   // Evenly spaced
        }
    }
    
    private fun calculateCircularSimilarity(value1: Float, value2: Float, maxValue: Float): Float {
        val diff = abs(value1 - value2)
        val circularDiff = min(diff, maxValue - diff)
        return 1f - (circularDiff / (maxValue / 2f))
    }
    
    private fun calculateRgbSimilarity(rgb1: RgbColor, rgb2: RgbColor): Float {
        val dr = rgb1.r - rgb2.r
        val dg = rgb1.g - rgb2.g
        val db = rgb1.b - rgb2.b
        val distance = sqrt((dr * dr + dg * dg + db * db).toFloat())
        val maxDistance = sqrt(3f * 255f * 255f)
        return 1f - (distance / maxDistance).coerceIn(0f, 1f)
    }
    
    private fun calculatePreferredHueRange(hues: List<Float>): HueRange {
        val avgHue = hues.average().toFloat()
        val stdDev = sqrt(hues.map { (it - avgHue).pow(2) }.average()).toFloat()
        
        // Range: ±2 standard deviations, clamped to reasonable width
        val rangeWidth = (stdDev * 2).coerceIn(30f, 90f)
        
        return HueRange(
            centerHue = avgHue,
            rangeWidth = rangeWidth
        )
    }
    
    private fun isHueInRange(hue: Float, range: HueRange): Boolean {
        val diff = abs(hue - range.centerHue)
        val circularDiff = min(diff, 360f - diff)
        return circularDiff <= range.rangeWidth / 2f
    }
    
    // ========== Data Classes ==========
    
    data class RgbColor(val r: Int, val g: Int, val b: Int)
    
    data class HsvColor(val hue: Float, val saturation: Float, val value: Float)
    
    data class HueRange(val centerHue: Float, val rangeWidth: Float)
    
    enum class ColorHarmony {
        MONOCHROMATIC,  // Single color family
        ANALOGOUS,      // Adjacent colors on wheel
        COMPLEMENTARY,  // Opposite colors
        TRIADIC         // Evenly spaced colors
    }
}

/**
 * Detailed analysis of a wallpaper's color palette.
 */
data class ColorPaletteAnalysis(
    val dominantColor: ColorAnalyzer.RgbColor,
    val accentColors: List<ColorAnalyzer.RgbColor>,
    val averageHue: Float,
    val averageSaturation: Float,
    val averageValue: Float,
    val isWarmToned: Boolean,
    val isVibrant: Boolean,
    val colorHarmony: ColorAnalyzer.ColorHarmony,
    val colorCount: Int
) {
    fun isEmpty(): Boolean = colorCount == 0
    
    companion object {
        fun empty() = ColorPaletteAnalysis(
            dominantColor = ColorAnalyzer.RgbColor(128, 128, 128),
            accentColors = emptyList(),
            averageHue = 0f,
            averageSaturation = 0f,
            averageValue = 0f,
            isWarmToned = false,
            isVibrant = false,
            colorHarmony = ColorAnalyzer.ColorHarmony.MONOCHROMATIC,
            colorCount = 0
        )
    }
}

/**
 * Learned color preferences from user feedback.
 */
data class ColorPreferenceProfile(
    val preferredHueRange: ColorAnalyzer.HueRange,
    val preferredSaturation: Float,
    val preferredBrightness: Float,
    val prefersWarmTones: Boolean,
    val prefersVibrant: Boolean,
    val confidence: Float  // 0.0 to 1.0, grows with more feedback
) {
    companion object {
        fun neutral() = ColorPreferenceProfile(
            preferredHueRange = ColorAnalyzer.HueRange(180f, 360f),
            preferredSaturation = 0.5f,
            preferredBrightness = 0.5f,
            prefersWarmTones = false,
            prefersVibrant = false,
            confidence = 0f
        )
    }
}
