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
 * Vanderwaals Modern Theme
 * 
 * Premium Material 3 dark theme featuring:
 * 
 * **Visual Design:**
 * - Exclusive dark mode with OLED optimization
 * - Vibrant purple gradient brand identity
 * - Rich surface elevation system
 * - Smooth, modern rounded corners
 * - Enhanced depth and hierarchy
 * 
 * **Typography:**
 * - System sans-serif with refined weights
 * - Optimized for readability on dark backgrounds
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
 * @param dynamicColor Use Material You colors on Android 12+ (default: false for brand consistency)
 * @param content The composable content to be themed
 */
@Composable
fun VanderwaalsTheme(
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // Determine color scheme - prioritize brand colors over dynamic
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            dynamicDarkColorScheme(context)
        }
        else -> VanderwaalsDarkColorScheme
    }
    
    // Configure system UI (status bar, navigation bar)
    val view = LocalView.current
    if (!view.isInEditMode) {
    // Configure system UI (status bar, navigation bar)
    val view = LocalView.current
    if (!view.isInEditMode) {
        // SideEffect removed: enableEdgeToEdge in MainActivity handles transparency
        // and we want the app background to show through
    }
    }

    // Apply Material Theme with our custom design system
    MaterialTheme(
        colorScheme = colorScheme,
        typography = VanderwaalsTypography,
        shapes = VanderwaalsShapes,
        content = content
    )
}

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
