package me.avinas.vanderwaals.ui.theme.glass

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap

/**
 * State holder for pre-processed liquid glass background.
 * 
 * OPTIMIZATION: Only holds the CURRENT theme's background (not both).
 * This saves ~15-25 MB of memory since we don't hold unused bitmaps.
 * 
 * When theme changes, the provider regenerates for the new theme.
 */
data class LiquidGlassState(
    /**
     * Pre-processed liquid glass background for the current theme
     */
    val background: ImageBitmap,
    
    /**
     * Whether this is for dark mode (used to know when to regenerate)
     */
    val isDarkMode: Boolean,
    
    /**
     * Whether the background has been successfully generated
     */
    val isReady: Boolean = true
)

/**
 * CompositionLocal to provide the liquid glass state throughout the app.
 * 
 * Components can access this to:
 * 1. Use the pre-processed background for LiquidGlassBackground
 * 2. Extract position-aware slices for LiquidGlassCard
 * 
 * Falls back to null if not provided (use static fallback assets).
 */
val LocalLiquidGlassState = compositionLocalOf<LiquidGlassState?> { null }
