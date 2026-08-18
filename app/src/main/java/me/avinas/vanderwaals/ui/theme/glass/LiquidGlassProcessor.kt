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
 * Transforms a source bitmap into a frosted background with edge refraction and chromatic offset.
 */
object LiquidGlassProcessor {

    data class GlassConfig(
        val blurRadius: Float = 80f,
        val distortionStrength: Float = 0.15f,
        val chromaticOffset: Float = 3f,
        val saturationBoost: Float = 1.1f,
        val brightnessAdjust: Float = 1.05f
    )

    fun generateLiquidGlassBackground(
        source: Bitmap,
        config: GlassConfig = GlassConfig()
    ): Bitmap {
        val blurred = applyFastBlur(source, config.blurRadius)
        val colorAdjusted = adjustColors(blurred, config.saturationBoost, config.brightnessAdjust)
        val withChroma = applyChromaAberration(colorAdjusted, config.chromaticOffset)
        val final = applyEdgeDistortion(withChroma, config.distortionStrength)
        
        if (blurred != source) blurred.recycle()
        if (colorAdjusted != blurred && colorAdjusted != source) colorAdjusted.recycle()
        if (withChroma != colorAdjusted && withChroma != source) withChroma.recycle()
        
        return final
    }

    /**
     * Crops out the region of the processed background that lines up with a card.
     * The background is rendered once at screen size, then each card reads its own slice.
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
     * Fast blur via downscale-upscale. Works on all API levels.
     */
    private fun applyFastBlur(source: Bitmap, radius: Float): Bitmap {
        // More downscale at higher radius keeps the pass cheap
        val scale = (1f / (radius / 10f)).coerceIn(0.02f, 0.25f)
        
        val smallWidth = (source.width * scale).toInt().coerceAtLeast(1)
        val smallHeight = (source.height * scale).toInt().coerceAtLeast(1)
        
        val small = Bitmap.createScaledBitmap(source, smallWidth, smallHeight, true)
        
        // Upscaling the small bitmap back is what produces the blur
        val blurred = Bitmap.createScaledBitmap(small, source.width, source.height, true)
        
        if (small != blurred) small.recycle()
        
        return blurred
    }

    /**
     * Shifts each RGB channel a couple of pixels sideways to fake a prism edge.
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
     * Mild barrel distortion so the glass reads as thick near the edges.
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
     * Adjusts saturation and brightness of the blurred backdrop.
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
            
            r = (r * brightness).toInt().coerceIn(0, 255)
            g = (g * brightness).toInt().coerceIn(0, 255)
            b = (b * brightness).toInt().coerceIn(0, 255)
            
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
