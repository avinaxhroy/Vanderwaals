package me.avinas.vanderwaals.algorithm

import android.graphics.Color
import kotlin.math.*

/**
 * Color analysis utility for palette extraction, perceptual LAB similarity, and color preference scoring.
 */
object ColorAnalyzer {
    
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
    
    fun calculatePaletteSimilarity(palette1: ColorPaletteAnalysis, palette2: ColorPaletteAnalysis): Float {
        if (palette1.isEmpty() || palette2.isEmpty()) {
            return 0.5f
        }
        
        val hueSimilarity = calculateCircularSimilarity(palette1.averageHue, palette2.averageHue, 360f)
        val saturationSimilarity = 1f - abs(palette1.averageSaturation - palette2.averageSaturation)
        val valueSimilarity = 1f - abs(palette1.averageValue - palette2.averageValue)
        val dominantSimilarity = calculateLabSimilarity(palette1.dominantColor, palette2.dominantColor)

        val accentSimilarity = if (palette1.accentColors.isNotEmpty() && palette2.accentColors.isNotEmpty()) {
            val accent1 = palette1.accentColors.first()
            val accent2 = palette2.accentColors.first()
            calculateLabSimilarity(accent1, accent2)
        } else {
            0.5f
        }
        
        val toneBonus = if (palette1.isWarmToned == palette2.isWarmToned) 0.1f else 0f
        val vibrancyBonus = if (palette1.isVibrant == palette2.isVibrant) 0.1f else 0f
        
        return (dominantSimilarity * 0.35f +
                hueSimilarity * 0.20f +
                saturationSimilarity * 0.15f +
                valueSimilarity * 0.15f +
                accentSimilarity * 0.15f +
                toneBonus + vibrancyBonus)
            .coerceIn(0f, 1f)
    }
    
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
    
    fun calculateColorPreferenceScore(
        palette: ColorPaletteAnalysis,
        preferences: ColorPreferenceProfile
    ): Float {
        if (palette.isEmpty() || preferences.confidence < 0.1f) {
            return 0f // Neutral when insufficient data
        }
        
        val hueInRange = isHueInRange(palette.averageHue, preferences.preferredHueRange)
        val hueScore = if (hueInRange) 1f else -0.5f
        
        val saturationDiff = abs(palette.averageSaturation - preferences.preferredSaturation)
        val saturationScore = 1f - saturationDiff
        
        val brightnessDiff = abs(palette.averageValue - preferences.preferredBrightness)
        val brightnessScore = 1f - brightnessDiff
        
        val toneScore = if (palette.isWarmToned == preferences.prefersWarmTones) 1f else -0.5f
        val vibrancyScore = if (palette.isVibrant == preferences.prefersVibrant) 1f else -0.5f
        
        val rawScore = (hueScore * 0.30f +
                        saturationScore * 0.25f +
                        brightnessScore * 0.25f +
                        toneScore * 0.10f +
                        vibrancyScore * 0.10f)
        
        return rawScore * preferences.confidence
    }
    
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
        return avgHue < 90f || avgHue > 300f
    }
    
    private fun isVibrant(hsvColors: List<HsvColor>): Boolean {
        val avgSaturation = hsvColors.map { it.saturation }.average().toFloat()
        val avgValue = hsvColors.map { it.value }.average().toFloat()
        return avgSaturation > 0.5f && avgValue > 0.4f
    }
    
    private fun detectColorHarmony(hsvColors: List<HsvColor>): ColorHarmony {
        if (hsvColors.size < 2) return ColorHarmony.MONOCHROMATIC
        
        val hues = hsvColors.map { it.hue }
        val hueRange = hues.maxOrNull()!! - hues.minOrNull()!!
        
        return when {
            hueRange < 30f -> ColorHarmony.MONOCHROMATIC
            hueRange < 60f -> ColorHarmony.ANALOGOUS
            hueRange > 150f -> ColorHarmony.COMPLEMENTARY
            else -> ColorHarmony.TRIADIC
        }
    }
    
    private fun calculateCircularSimilarity(value1: Float, value2: Float, maxValue: Float): Float {
        val diff = abs(value1 - value2)
        val circularDiff = min(diff, maxValue - diff)
        return 1f - (circularDiff / (maxValue / 2f))
    }

    /**
     * CIE76 delta E between two colors in CIELAB space.
     */
    fun deltaE76(rgb1: RgbColor, rgb2: RgbColor): Double =
        me.avinas.vanderwaals.core.ColorSpace.rgbDeltaE(rgb1.r, rgb1.g, rgb1.b, rgb2.r, rgb2.g, rgb2.b)

    fun calculateLabSimilarity(rgb1: RgbColor, rgb2: RgbColor): Float =
        (1.0 - (deltaE76(rgb1, rgb2) / 100.0).coerceIn(0.0, 1.0)).toFloat()

    private fun calculatePreferredHueRange(hues: List<Float>): HueRange {
        val avgHue = hues.average().toFloat()
        val stdDev = sqrt(hues.map { (it - avgHue).pow(2) }.average()).toFloat()
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
    
    data class RgbColor(val r: Int, val g: Int, val b: Int)
    
    data class HsvColor(val hue: Float, val saturation: Float, val value: Float)
    
    data class HueRange(val centerHue: Float, val rangeWidth: Float)
    
    enum class ColorHarmony {
        MONOCHROMATIC,
        ANALOGOUS,
        COMPLEMENTARY,
        TRIADIC
    }
}

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

data class ColorPreferenceProfile(
    val preferredHueRange: ColorAnalyzer.HueRange,
    val preferredSaturation: Float,
    val preferredBrightness: Float,
    val prefersWarmTones: Boolean,
    val prefersVibrant: Boolean,
    val confidence: Float
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
