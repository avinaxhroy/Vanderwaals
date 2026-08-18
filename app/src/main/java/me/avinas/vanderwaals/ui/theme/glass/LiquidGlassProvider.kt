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
 * Loads the pre-made liquid glass background assets
 * (bg_liquid_glass_dark.png / bg_liquid_glass_light.png) and exposes them via
 * LocalLiquidGlassState. No processing is applied — the assets are already
 * the finished effect; they are only scaled to the screen.
 */
@Composable
fun LiquidGlassProvider(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val isDark = LocalThemeIsDark.current
    
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }.toInt()
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }.toInt()
    
    var glassState by remember { mutableStateOf<LiquidGlassState?>(null) }
    
    LaunchedEffect(screenWidthPx, screenHeightPx, isDark) {
        if (screenWidthPx <= 0 || screenHeightPx <= 0) {
            Log.d(TAG, "Screen size invalid, skipping load")
            return@LaunchedEffect
        }
        
        // Reuse the background already loaded for this theme
        if (glassState?.isDarkMode == isDark && glassState?.isReady == true) {
            Log.d(TAG, "Already have correct theme background ($isDark), skipping load")
            return@LaunchedEffect
        }
        
        Log.d(TAG, "Loading liquid glass background async: ${screenWidthPx}x${screenHeightPx}, isDark=$isDark")
        
        withContext(Dispatchers.IO) {
            try {
                // Theme may have changed while we were queued
                if (!isActive) {
                    Log.d(TAG, "Load cancelled (theme changed)")
                    return@withContext
                }
                
                val resourceId = if (isDark) {
                    R.drawable.bg_liquid_glass_dark
                } else {
                    R.drawable.bg_liquid_glass_light
                }
                
                val loadOptions = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inScaled = false  // decode at native resolution
                }
                
                val sourceBitmap = BitmapFactory.decodeResource(
                    context.resources, resourceId, loadOptions
                )
                
                if (sourceBitmap != null) {
                    // Re-check before the expensive scale in case the theme flipped
                    if (!isActive) {
                        Log.d(TAG, "Load cancelled during decode (theme changed)")
                        sourceBitmap.recycle()
                        return@withContext
                    }
                    
                    Log.d(TAG, "Source bitmap loaded: ${sourceBitmap.width}x${sourceBitmap.height}")
                    
                    val scaledBitmap = Bitmap.createScaledBitmap(
                        sourceBitmap,
                        screenWidthPx,
                        screenHeightPx,
                        true
                    )
                    
                    Log.d(TAG, "Scaled bitmap: ${scaledBitmap.width}x${scaledBitmap.height}")
                    
                    if (scaledBitmap != sourceBitmap) {
                        sourceBitmap.recycle()
                    }
                    
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
