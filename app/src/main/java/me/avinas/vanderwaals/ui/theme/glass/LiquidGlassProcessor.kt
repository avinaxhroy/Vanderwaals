package me.avinas.vanderwaals.ui.theme.glass

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * LiquidGlassProcessor - The heart of the Smart Launcher-inspired glass effect.
 * 
 * This processor transforms a source image into a "liquid glass" background by applying:
 * 1. Heavy Gaussian blur - Creates the frosted glass base
 * 2. Edge distortion - Simulates refraction through thick glass
 * 3. Chromatic aberration - RGB channel separation for prism/rainbow edge effects
 * 
 * Key insight from Smart Launcher:
 * - Process ONCE, not per-frame
 * - Cards are OPAQUE, displaying pre-processed slices that match the background
 * - Creates ILLUSION of transparency
 */
object LiquidGlassProcessor {

    /**
     * Configuration for the liquid glass effect
     */
    data class GlassConfig(
        val blurRadius: Float = 80f,          // Blur intensity (higher = more frosted)
        val distortionStrength: Float = 0.15f, // Edge distortion (0 = none, 1 = extreme)
        val chromaticOffset: Float = 3f,       // RGB separation in pixels
        val saturationBoost: Float = 1.1f,     // Color saturation multiplier
        val brightnessAdjust: Float = 1.05f    // Brightness multiplier
    )

    /**
     * Process a source bitmap to create a liquid glass background.
     * 
     * @param source The source bitmap (gradient, texture, or captured background)
     * @param config Configuration for the effect parameters
     * @return Processed bitmap with liquid glass effect applied
     */
    fun generateLiquidGlassBackground(
        source: Bitmap,
        config: GlassConfig = GlassConfig()
    ): Bitmap {
        // Step 1: Apply blur
        val blurred = applyFastBlur(source, config.blurRadius)
        
        // Step 2: Apply color adjustments
        val colorAdjusted = adjustColors(blurred, config.saturationBoost, config.brightnessAdjust)
        
        // Step 3: Apply chromatic aberration for that premium prism effect
        val withChroma = applyChromaAberration(colorAdjusted, config.chromaticOffset)
        
        // Step 4: Apply subtle edge distortion
        val final = applyEdgeDistortion(withChroma, config.distortionStrength)
        
        // Clean up intermediate bitmaps
        if (blurred != source) blurred.recycle()
        if (colorAdjusted != blurred && colorAdjusted != source) colorAdjusted.recycle()
        if (withChroma != colorAdjusted && withChroma != source) withChroma.recycle()
        
        return final
    }

    /**
     * Extract a slice of the processed background for a specific screen region.
     * This is the "trick" - each card gets a perfectly aligned slice.
     * 
     * @param processedBackground The full processed liquid glass background
     * @param cardLeft Card's left position in pixels
     * @param cardTop Card's top position in pixels
     * @param cardWidth Card's width in pixels
     * @param cardHeight Card's height in pixels
     * @param screenWidth Total screen width
     * @param screenHeight Total screen height
     * @return Cropped and scaled slice for the card
     */
    fun extractSlice(
        processedBackground: Bitmap,
        cardLeft: Float,
        cardTop: Float,
        cardWidth: Float,
        cardHeight: Float,
        screenWidth: Float,
        screenHeight: Float
    ): ImageBitmap {
        // Map card position to bitmap coordinates
        val bgWidth = processedBackground.width.toFloat()
        val bgHeight = processedBackground.height.toFloat()
        
        val scaleX = bgWidth / screenWidth
        val scaleY = bgHeight / screenHeight
        
        val srcLeft = (cardLeft * scaleX).toInt().coerceIn(0, processedBackground.width - 1)
        val srcTop = (cardTop * scaleY).toInt().coerceIn(0, processedBackground.height - 1)
        val srcWidth = (cardWidth * scaleX).toInt().coerceIn(1, processedBackground.width - srcLeft)
        val srcHeight = (cardHeight * scaleY).toInt().coerceIn(1, processedBackground.height - srcTop)
        
        val slice = Bitmap.createBitmap(
            processedBackground,
            srcLeft,
            srcTop,
            srcWidth,
            srcHeight
        )
        
        return slice.asImageBitmap()
    }

    /**
     * Fast blur using downscale-upscale technique.
     * Works on all API levels and is very performant.
     */
    private fun applyFastBlur(source: Bitmap, radius: Float): Bitmap {
        // Downscale factor based on blur radius
        // Higher radius = more downscale for efficiency
        val scale = (1f / (radius / 10f)).coerceIn(0.02f, 0.25f)
        
        val smallWidth = (source.width * scale).toInt().coerceAtLeast(1)
        val smallHeight = (source.height * scale).toInt().coerceAtLeast(1)
        
        // Downscale
        val small = Bitmap.createScaledBitmap(source, smallWidth, smallHeight, true)
        
        // Upscale back - the bilinear filtering creates the blur effect
        val blurred = Bitmap.createScaledBitmap(small, source.width, source.height, true)
        
        if (small != blurred) small.recycle()
        
        return blurred
    }

    /**
     * Apply chromatic aberration effect.
     * Separates RGB channels and offsets them slightly for a prism/rainbow edge effect.
     */
    private fun applyChromaAberration(source: Bitmap, offset: Float): Bitmap {
        if (offset <= 0f) return source
        
        val width = source.width
        val height = source.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val resultPixels = IntArray(width * height)
        
        val offsetInt = offset.toInt().coerceAtLeast(1)
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                
                // Red channel - shift left
                val rX = (x - offsetInt).coerceIn(0, width - 1)
                val rIndex = y * width + rX
                val r = Color.red(pixels[rIndex])
                
                // Green channel - no shift
                val g = Color.green(pixels[index])
                
                // Blue channel - shift right
                val bX = (x + offsetInt).coerceIn(0, width - 1)
                val bIndex = y * width + bX
                val b = Color.blue(pixels[bIndex])
                
                val a = Color.alpha(pixels[index])
                resultPixels[index] = Color.argb(a, r, g, b)
            }
        }
        
        result.setPixels(resultPixels, 0, width, 0, 0, width, height)
        return result
    }

    /**
     * Apply subtle edge distortion to simulate light refraction through thick glass.
     * Uses a simple barrel distortion effect focused on edges.
     */
    private fun applyEdgeDistortion(source: Bitmap, strength: Float): Bitmap {
        if (strength <= 0f) return source
        
        val width = source.width
        val height = source.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val resultPixels = IntArray(width * height)
        
        val centerX = width / 2f
        val centerY = height / 2f
        val maxRadius = sqrt(centerX * centerX + centerY * centerY)
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val dx = x - centerX
                val dy = y - centerY
                val distance = sqrt(dx * dx + dy * dy)
                val normalizedDist = distance / maxRadius
                
                // Barrel distortion - affects edges more than center
                val distortionFactor = 1f + strength * normalizedDist * normalizedDist
                
                val srcX = (centerX + dx / distortionFactor).toInt().coerceIn(0, width - 1)
                val srcY = (centerY + dy / distortionFactor).toInt().coerceIn(0, height - 1)
                
                val srcIndex = srcY * width + srcX
                val dstIndex = y * width + x
                
                resultPixels[dstIndex] = pixels[srcIndex]
            }
        }
        
        result.setPixels(resultPixels, 0, width, 0, 0, width, height)
        return result
    }

    /**
     * Adjust color saturation and brightness for a more vibrant glass effect.
     */
    private fun adjustColors(source: Bitmap, saturation: Float, brightness: Float): Bitmap {
        if (saturation == 1f && brightness == 1f) return source
        
        val width = source.width
        val height = source.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val a = Color.alpha(pixel)
            var r = Color.red(pixel)
            var g = Color.green(pixel)
            var b = Color.blue(pixel)
            
            // Apply brightness
            r = (r * brightness).toInt().coerceIn(0, 255)
            g = (g * brightness).toInt().coerceIn(0, 255)
            b = (b * brightness).toInt().coerceIn(0, 255)
            
            // Apply saturation
            val gray = (r * 0.299f + g * 0.587f + b * 0.114f).toInt()
            r = (gray + (r - gray) * saturation).toInt().coerceIn(0, 255)
            g = (gray + (g - gray) * saturation).toInt().coerceIn(0, 255)
            b = (gray + (b - gray) * saturation).toInt().coerceIn(0, 255)
            
            pixels[i] = Color.argb(a, r, g, b)
        }
        
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }
}
