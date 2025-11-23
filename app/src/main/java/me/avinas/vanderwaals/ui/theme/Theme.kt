package me.avinas.vanderwaals.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Vanderwaals Modern Dark Color Scheme
 * 
 * Premium dark theme with:
 * - Vibrant purple brand colors
 * - Rich surface hierarchy for depth
 * - OLED-optimized deep blacks
 * - Enhanced contrast and accessibility
 * - Material 3 design principles
 */
private val VanderwaalsDarkColorScheme = darkColorScheme(
    // ===== PRIMARY COLORS - Brand Purple =====
    primary = VanderwaalsTan,
    onPrimary = Color.White,
    primaryContainer = VanderwaalsTanDark,
    onPrimaryContainer = VanderwaalsTanLight,
    
    // ===== SECONDARY COLORS - Indigo Accent =====
    secondary = VanderwaalsTanDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2D2F6F),
    onSecondaryContainer = Color(0xFF9CA3FF),
    
    // ===== TERTIARY COLORS - Pink Accent =====
    tertiary = VanderwaalsAccent,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF5E1841),
    onTertiaryContainer = VanderwaalsAccentLight,
    
    // ===== ERROR COLORS =====
    error = ErrorColor,
    onError = Color.White,
    errorContainer = ErrorColorDark,
    onErrorContainer = Color(0xFFFFDAD6),
    
    // ===== BACKGROUND HIERARCHY =====
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    
    // ===== SURFACE HIERARCHY =====
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondaryDark,
    surfaceTint = VanderwaalsTan,
    surfaceBright = SurfaceHighlight,
    surfaceDim = Color(0xFF0F0F15),
    
    // ===== SURFACE CONTAINERS - Elevation System =====
    surfaceContainer = SurfaceDark,
    surfaceContainerHigh = SurfaceElevated,
    surfaceContainerHighest = SurfaceHighlight,
    surfaceContainerLow = Color(0xFF121218),
    surfaceContainerLowest = BackgroundDark,
    
    // ===== INVERSE COLORS - For Snackbars, Tooltips =====
    inverseSurface = TextPrimaryDark,
    inverseOnSurface = BackgroundDark,
    inversePrimary = VanderwaalsTanDark,
    
    // ===== OUTLINE COLORS - Borders & Dividers =====
    outline = BorderDark,
    outlineVariant = BorderHighlight,
    
    // ===== SCRIM - Modal Overlays =====
    scrim = ScrimColor
)

/**
 * Vanderwaals Modern Light Color Scheme
 * 
 * Clean, airy light theme with:
 * - Warm white backgrounds
 * - High contrast dark text
 * - Same brand accent colors
 */
private val VanderwaalsLightColorScheme = androidx.compose.material3.lightColorScheme(
    // ===== PRIMARY COLORS - Brand Purple =====
    primary = VanderwaalsTanDark, // Darker tan for better contrast on light
    onPrimary = Color.White,
    primaryContainer = VanderwaalsTan,
    onPrimaryContainer = Color.White,
    
    // ===== SECONDARY COLORS - Indigo Accent =====
    secondary = VanderwaalsTan,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E7FF),
    onSecondaryContainer = Color(0xFF2D2F6F),
    
    // ===== TERTIARY COLORS - Pink Accent =====
    tertiary = VanderwaalsAccent,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD6E4),
    onTertiaryContainer = Color(0xFF5E1841),
    
    // ===== ERROR COLORS =====
    error = ErrorColorLight,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = ErrorColorDark,
    
    // ===== BACKGROUND HIERARCHY =====
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    
    // ===== SURFACE HIERARCHY =====
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceElevatedLight,
    onSurfaceVariant = TextSecondaryLight,
    surfaceTint = VanderwaalsTanDark,
    surfaceBright = SurfaceHighlightLight,
    surfaceDim = SurfaceElevatedLight,
    
    // ===== SURFACE CONTAINERS - Elevation System =====
    surfaceContainer = SurfaceLight,
    surfaceContainerHigh = SurfaceElevatedLight,
    surfaceContainerHighest = SurfaceHighlightLight,
    surfaceContainerLow = BackgroundLight,
    surfaceContainerLowest = Color.White,
    
    // ===== INVERSE COLORS =====
    inverseSurface = Color(0xFF2A2A3E), // Dark surface for inverse (tooltips on light bg)
    inverseOnSurface = Color.White, // White text on dark inverse surface
    inversePrimary = VanderwaalsTan,
    
    // ===== OUTLINE COLORS =====
    outline = BorderHighlightLight,
    outlineVariant = BorderLight,
    
    // ===== SCRIM =====
    scrim = ScrimColor
)

/**
 * Vanderwaals Modern Theme
 * 
 * Premium Material 3 theme featuring both Light and Dark modes:
 * 
 * **Visual Design:**
 * - Exclusive dark mode with OLED optimization
 * - Clean, airy light mode
 * - Vibrant purple gradient brand identity
 * - Rich surface elevation system
 * - Smooth, modern rounded corners
 * - Enhanced depth and hierarchy
 * 
 * **Typography:**
 * - System sans-serif with refined weights
 * - Optimized for readability
 * - Proper line heights and letter spacing
 * 
 * **Shapes:**
 * - Consistent rounded corner system
 * - Friendly, modern aesthetic
 * - Varied shapes for different contexts
 * 
 * **Accessibility:**
 * - High contrast ratios
 * - Clear visual hierarchy
 * - Proper touch target sizes
 * 
 * @param darkTheme Whether to use dark theme (default: follows system)
 * @param dynamicColor Use Material You colors on Android 12+ (default: false for brand consistency)
 * @param content The composable content to be themed
 */
@Composable
fun VanderwaalsTheme(
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // Determine color scheme - prioritize brand colors over dynamic
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else androidx.compose.material3.dynamicLightColorScheme(context)
        }
        darkTheme -> VanderwaalsDarkColorScheme
        else -> VanderwaalsLightColorScheme
    }
    
    // Configure system UI (status bar, navigation bar)
    val view = LocalView.current
    if (!view.isInEditMode) {
        // SideEffect removed: enableEdgeToEdge in MainActivity handles transparency
        // and we want the app background to show through
    }

    // Apply Material Theme with our custom design system
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
 * This allows components to know the actual app theme, which may differ from the system theme.
 */
val LocalThemeIsDark = androidx.compose.runtime.compositionLocalOf { false }

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
