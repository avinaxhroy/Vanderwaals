package me.avinas.vanderwaals.ui.theme.components

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import me.avinas.vanderwaals.ui.theme.*
import me.avinas.vanderwaals.ui.theme.glass.LocalLiquidGlassState

private const val TAG = "LiquidGlassCard"

/**
 * LiquidGlassCard - Smart Launcher-style liquid glass card.
 * 
 * Creates a frosted glass effect by:
 * 1. Extracting the background slice at card's position
 * 2. Applying blur to the slice for frosted effect
 * 3. Overlaying glass tint and highlights
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    blurRadius: Dp = 15.dp,  // Frosted glass blur amount (reduced for light mode visibility)
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val glassState = LocalLiquidGlassState.current
    val isDark = LocalThemeIsDark.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    
    // Track card position and size
    var cardX by remember { mutableStateOf(0f) }
    var cardY by remember { mutableStateOf(0f) }
    var cardWidth by remember { mutableIntStateOf(0) }
    var cardHeight by remember { mutableIntStateOf(0) }
    
    // Screen dimensions for mapping
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    
    // Slice cache to avoid redundant extractions
    // Key includes glassState to clear cache on theme change
    val sliceCache = remember(glassState) { mutableMapOf<String, ImageBitmap>() }
    
    // Extract slice reactively with caching
    val backgroundSlice by remember(glassState) {
        derivedStateOf {
            if (glassState == null || !glassState.isReady) {
                return@derivedStateOf null
            }
            if (cardWidth <= 0 || cardHeight <= 0) {
                return@derivedStateOf null
            }
            if (screenWidthPx <= 0 || screenHeightPx <= 0) {
                return@derivedStateOf null
            }
            
            // Create cache key based on position and size
            val cacheKey = "${cardX.toInt()}_${cardY.toInt()}_${cardWidth}_${cardHeight}"
            
            // Return cached slice if available
            sliceCache[cacheKey]?.let {
                return@derivedStateOf it
            }
            
            try {
                val sourceBitmap = glassState.background.asAndroidBitmap()
                
                // Map card position to background position
                val scaleX = sourceBitmap.width / screenWidthPx
                val scaleY = sourceBitmap.height / screenHeightPx
                
                val srcX = (cardX * scaleX).toInt().coerceIn(0, sourceBitmap.width - 1)
                val srcY = (cardY * scaleY).toInt().coerceIn(0, sourceBitmap.height - 1)
                val srcWidth = (cardWidth * scaleX).toInt().coerceIn(1, sourceBitmap.width - srcX)
                val srcHeight = (cardHeight * scaleY).toInt().coerceIn(1, sourceBitmap.height - srcY)
                
                if (srcWidth <= 0 || srcHeight <= 0) return@derivedStateOf null
                
                // Extract the slice
                val slice = Bitmap.createBitmap(
                    sourceBitmap,
                    srcX,
                    srcY,
                    srcWidth,
                    srcHeight
                ).asImageBitmap()
                
                // Cache the slice
                sliceCache[cacheKey] = slice
                Log.d(TAG, "✅ Slice extracted and cached: $cacheKey")
                
                slice
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to extract slice", e)
                null
            }
        }
    }
    
    // Glass tint - subtle to let background colors show
    val glassTint = if (isDark) {
        Color.Black.copy(alpha = 0.15f)
    } else {
        Color.White.copy(alpha = 0.15f)  // Reduced from 0.3f for more vibrant pattern
    }
    
    val borderColor = if (isDark) {
        Color.White.copy(alpha = 0.15f)
    } else {
        Color.White.copy(alpha = 0.3f)  // Reduced from 0.5f for subtler border
    }
    
    Box(
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                val position = coordinates.positionInRoot()
                val size = coordinates.size
                if (position.x != cardX) cardX = position.x
                if (position.y != cardY) cardY = position.y
                if (size.width != cardWidth) cardWidth = size.width
                if (size.height != cardHeight) cardHeight = size.height
            }
            .clip(shape)
    ) {
        // Layer 1: Background slice with blur (frosted glass effect)
        val slice = backgroundSlice
        if (slice != null) {
            Image(
                bitmap = slice,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .matchParentSize()
                    .blur(
                        radius = blurRadius,
                        edgeTreatment = BlurredEdgeTreatment.Unbounded
                    )
            )
        } else {
            // Fallback: solid glass color if slice not available yet
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(if (isDark) GlassBackground else GlassBackgroundLight)
            )
        }
        
        // Layer 2: Glass tint overlay
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(glassTint)
        )
        
        // Layer 3: Glass border
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(
                    width = 1.dp,
                    color = borderColor,
                    shape = shape
                )
        )
        
        // Layer 4: Top highlight (gives depth)
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.1f),  // Reduced from 0.2f
                            Color.Transparent
                        ),
                        endY = with(density) { 40.dp.toPx() }
                    )
                )
        )
        
        // Layer 5: Inner highlight border
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(1.dp)
                .border(
                    width = 0.5.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            if (isDark) Color.White.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.15f),  // Reduced from 0.3f
                            Color.Transparent
                        )
                    ),
                    shape = shape
                )
        )
        
        // Layer 6: Content
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

/**
 * Simple glass card without position-aware slicing.
 */
@Composable
fun SimpleLiquidGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    elevation: Dp = 16.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = LocalThemeIsDark.current
    val containerColor = if (isDark) GlassBackground else GlassBackgroundLight
    val borderColor = if (isDark) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.3f)
    
    Box(
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            .border(1.dp, borderColor, shape)
    ) {
        // Top highlight
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.12f),
                            Color.Transparent
                        ),
                        endY = 100f
                    )
                )
        )
        
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}
