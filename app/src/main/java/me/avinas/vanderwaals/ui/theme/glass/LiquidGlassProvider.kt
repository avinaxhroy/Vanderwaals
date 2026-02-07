package me.avinas.vanderwaals.ui.theme.glass

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import me.avinas.vanderwaals.R
import me.avinas.vanderwaals.ui.theme.LocalThemeIsDark

private const val TAG = "LiquidGlassProvider"

/**
 * LiquidGlassProvider - Uses the existing curated liquid glass background assets.
 * 
 * Your existing bg_liquid_glass_dark.png and bg_liquid_glass_light.png are ALREADY
 * beautiful liquid glass images with wavy liquid distortion and chromatic aberration.
 * 
 * NO additional processing is applied to preserve the original quality!
 * The images are just loaded and scaled to screen size.
 */
@Composable
fun LiquidGlassProvider(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val isDark = LocalThemeIsDark.current
    
    // Get screen dimensions
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }.toInt()
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }.toInt()
    
    // State to hold the loaded background
    var glassState by remember { mutableStateOf<LiquidGlassState?>(null) }
    
    // Load background asynchronously to avoid blocking main thread
    // LaunchedEffect automatically cancels previous coroutine when keys change
    LaunchedEffect(screenWidthPx, screenHeightPx, isDark) {
        if (screenWidthPx <= 0 || screenHeightPx <= 0) {
            Log.d(TAG, "Screen size invalid, skipping load")
            return@LaunchedEffect
        }
        
        // Skip if we already have the correct theme's background
        if (glassState?.isDarkMode == isDark && glassState?.isReady == true) {
            Log.d(TAG, "Already have correct theme background ($isDark), skipping load")
            return@LaunchedEffect
        }
        
        Log.d(TAG, "Loading liquid glass background async: ${screenWidthPx}x${screenHeightPx}, isDark=$isDark")
        
        withContext(Dispatchers.IO) {
            try {
                // Check if we're still active (not cancelled by theme change)
                if (!isActive) {
                    Log.d(TAG, "Load cancelled (theme changed)")
                    return@withContext
                }
                
                // Load the existing curated liquid glass asset
                val resourceId = if (isDark) {
                    R.drawable.bg_liquid_glass_dark
                } else {
                    R.drawable.bg_liquid_glass_light
                }
                
                // Load bitmap at full quality (no downsampling)
                val loadOptions = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inScaled = false  // Don't scale during decode
                }
                
                val sourceBitmap = BitmapFactory.decodeResource(
                    context.resources, resourceId, loadOptions
                )
                
                if (sourceBitmap != null) {
                    // Check again before expensive scaling operation
                    if (!isActive) {
                        Log.d(TAG, "Load cancelled during decode (theme changed)")
                        sourceBitmap.recycle()
                        return@withContext
                    }
                    
                    Log.d(TAG, "Source bitmap loaded: ${sourceBitmap.width}x${sourceBitmap.height}")
                    
                    // Scale to screen size with high quality filtering
                    val scaledBitmap = Bitmap.createScaledBitmap(
                        sourceBitmap,
                        screenWidthPx,
                        screenHeightPx,
                        true  // Use bilinear filtering for quality
                    )
                    
                    Log.d(TAG, "Scaled bitmap: ${scaledBitmap.width}x${scaledBitmap.height}")
                    
                    // Clean up source if different
                    if (scaledBitmap != sourceBitmap) {
                        sourceBitmap.recycle()
                    }
                    
                    // Final check before updating state
                    if (!isActive) {
                        Log.d(TAG, "Load cancelled after scaling (theme changed)")
                        scaledBitmap.recycle()
                        return@withContext
                    }
                    
                    glassState = LiquidGlassState(
                        background = scaledBitmap.asImageBitmap(),
                        isDarkMode = isDark,
                        isReady = true
                    )
                    
                    Log.d(TAG, "✅ Liquid glass background ready!")
                } else {
                    Log.e(TAG, "Failed to decode liquid glass resource")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load liquid glass background", e)
            }
        }
    }
    
    CompositionLocalProvider(
        LocalLiquidGlassState provides glassState,
        content = content
    )
}
