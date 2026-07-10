package me.avinas.vanderwaals.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Vanderwaals Premium Dark Color Scheme
 * 
 * Rich, modern dark theme with:
 * - Near-pure black backgrounds for OLED
 * - Electric blue/violet brand colors
 * - Elevated card surfaces with subtle contrast
 * - High contrast text hierarchy
 */
private val VanderwaalsDarkColorScheme = darkColorScheme(
    primary = BrandPrimary,
    onPrimary = Color.White,
    primaryContainer = BrandPrimaryDark,
    onPrimaryContainer = BrandPrimaryLight,
    
    secondary = BrandAccent,
    onSecondary = Color.White,
    secondaryContainer = BrandAccent.copy(alpha = 0.2f),
    onSecondaryContainer = BrandAccentLight,
    
    tertiary = Color(0xFFEC4899),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEC4899).copy(alpha = 0.2f),
    onTertiaryContainer = Color(0xFFF472B6),
    
    error = ErrorColor,
    onError = Color.White,
    errorContainer = ErrorContainer,
    onErrorContainer = Color(0xFFFCA5A5),
    
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceElevatedDark,
    onSurfaceVariant = TextSecondaryDark,
    surfaceTint = BrandPrimary,
    surfaceBright = SurfaceHighlightDark,
    surfaceDim = Color(0xFF0C0C0E),
    
    surfaceContainer = SurfaceElevatedDark,
    surfaceContainerHigh = SurfaceOverlayDark,
    surfaceContainerHighest = SurfaceHighlightDark,
    surfaceContainerLow = SurfaceDark,
    surfaceContainerLowest = BackgroundDark,
    
    inverseSurface = TextPrimaryDark,
    inverseOnSurface = BackgroundDark,
    inversePrimary = BrandPrimaryDark,
    
    outline = BorderDark,
    outlineVariant = BorderSubtleDark,
    
    scrim = ScrimDark
)

/**
 * Vanderwaals Premium Light Color Scheme
 * 
 * Clean, modern light theme with:
 * - Warm off-white backgrounds
 * - Pure white elevated cards
 * - High contrast dark text
 * - Same vibrant brand accent
 */
private val VanderwaalsLightColorScheme = lightColorScheme(
    primary = BrandPrimaryDark,
    onPrimary = Color.White,
    primaryContainer = BrandPrimary,
    onPrimaryContainer = Color.White,
    
    secondary = BrandAccent,
    onSecondary = Color.White,
    secondaryContainer = BrandAccent.copy(alpha = 0.1f),
    onSecondaryContainer = BrandAccent.copy(alpha = 0.8f),
    
    tertiary = Color(0xFFEC4899),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEC4899).copy(alpha = 0.1f),
    onTertiaryContainer = Color(0xFFBE185D),
    
    error = ErrorColor,
    onError = Color.White,
    errorContainer = ErrorContainer,
    onErrorContainer = Color(0xFF991B1B),
    
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceOverlayLight,
    onSurfaceVariant = TextSecondaryLight,
    surfaceTint = BrandPrimaryDark,
    surfaceBright = SurfaceHighlightLight,
    surfaceDim = SurfaceOverlayLight,
    
    surfaceContainer = SurfaceLight,
    surfaceContainerHigh = SurfaceOverlayLight,
    surfaceContainerHighest = SurfaceHighlightLight,
    surfaceContainerLow = BackgroundLight,
    surfaceContainerLowest = Color.White,
    
    inverseSurface = SurfaceDark,
    inverseOnSurface = TextPrimaryDark,
    inversePrimary = BrandPrimaryLight,
    
    outline = BorderLight,
    outlineVariant = BorderSubtleLight,
    
    scrim = ScrimLight
)

/**
 * Vanderwaals Premium Theme
 * 
 * Modern, sophisticated design system featuring:
 * 
 * **Visual Design:**
 * - Rich dark mode with OLED optimization
 * - Clean light mode with warm whites
 * - Electric blue/violet brand identity
 * - Elevated surface hierarchy
 * - Premium card-based layouts
 * 
 * **Typography:**
 * - System sans-serif for clarity
 * - Optimized weights and spacing
 * - Clear hierarchy and readability
 * 
 * **Shapes:**
 * - Consistent rounded corners
 * - Modern, friendly aesthetic
 * - Context-appropriate variants
 * 
 * **Accessibility:**
 * - High contrast ratios
 * - Clear visual hierarchy
 * - Proper touch targets
 * 
 * @param darkTheme Whether to use dark theme (default: follows system)
 * @param dynamicColor Use Material You colors on Android 12+ (default: false)
 * @param content The composable content to be themed
 */
@Composable
fun VanderwaalsTheme(
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> VanderwaalsDarkColorScheme
        else -> VanderwaalsLightColorScheme
    }
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = VanderwaalsTypography,
        shapes = VanderwaalsShapes,
        content = {
            androidx.compose.runtime.CompositionLocalProvider(
                LocalThemeIsDark provides darkTheme,
                content = content
            )
        }
    )
}

/**
 * CompositionLocal to provide the current theme mode (Dark/Light) to the app.
 */
val LocalThemeIsDark = androidx.compose.runtime.compositionLocalOf { false }

/**
 * CompositionLocal to provide the unconsumed system navigation bar bottom padding.
 */
val LocalNavigationBarPadding = androidx.compose.runtime.compositionLocalOf { androidx.compose.ui.unit.Dp.Unspecified }

/**
 * Preview-friendly version of VanderwaalsTheme for Compose previews
 */
@Composable
fun VanderwaalsThemePreview(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = VanderwaalsDarkColorScheme,
        typography = VanderwaalsTypography,
        shapes = VanderwaalsShapes,
        content = content
    )
}

/**
 * A simple background container that uses the theme's background color.
 * Replaces the old LiquidGlassBackground with a clean, solid background.
 */
@Composable
fun LiquidGlassBackground(
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    content: @Composable () -> Unit
) {
    val isDark = LocalThemeIsDark.current
    
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isDark) BackgroundDark else BackgroundLight)
    ) {
        content()
    }
}
