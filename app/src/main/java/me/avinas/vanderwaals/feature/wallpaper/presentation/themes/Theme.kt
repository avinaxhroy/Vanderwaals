package me.avinas.vanderwaals.feature.wallpaper.presentation.themes

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Modern dark color scheme with purple brand aesthetic.
 * Features proper surface hierarchy and contrast.
 */
private val DarkColors = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,
    tertiary = md_theme_dark_tertiary,
    onTertiary = md_theme_dark_onTertiary,
    tertiaryContainer = md_theme_dark_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_onTertiaryContainer,
    error = md_theme_dark_error,
    errorContainer = md_theme_dark_errorContainer,
    onError = md_theme_dark_onError,
    onErrorContainer = md_theme_dark_onErrorContainer,
    background = md_theme_dark_background,
    onBackground = md_theme_dark_onBackground,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    outline = md_theme_dark_outline,
    inverseOnSurface = md_theme_dark_inverseOnSurface,
    inverseSurface = md_theme_dark_inverseSurface,
    inversePrimary = md_theme_dark_inversePrimary,
    surfaceTint = md_theme_dark_surfaceTint,
    outlineVariant = md_theme_dark_outlineVariant,
    scrim = md_theme_dark_scrim,
)

private val AmoledDarkColors = darkColorScheme(
    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    onPrimaryContainer = md_theme_light_onPrimaryContainer,
    secondary = Color.Black,
    onSecondary = Color.White,
    secondaryContainer = Color.Black,
    onSecondaryContainer = Color.White,
    tertiary = md_theme_light_tertiary,
    onTertiary = md_theme_light_onTertiary,
    tertiaryContainer = Color.Black,
    onTertiaryContainer = Color.White,
    error = Color.Red,
    errorContainer = Color.Black,
    onError = Color.White,
    onErrorContainer = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color.Black,
    onSurfaceVariant = Color.White,
    outline = Color.White,
    inverseOnSurface = Color.Black,
    inverseSurface = Color.White,
    inversePrimary = Color.White,
    surfaceTint = Color.Black,
    outlineVariant = md_theme_dark_outlineVariant,
    scrim = md_theme_dark_scrim,
)


/**
 * Modern Dark Theme for Vanderwaals - Dark Mode Only
 * 
 * Features:
 * - Pure dark mode aesthetic (light mode removed)
 * - Optional AMOLED mode with true black backgrounds
 * - Optional dynamic color support on Android 12+
 * - Purple brand identity with modern gradient
 * - Optimized for OLED displays
 */
@Composable
fun VanderwaalsTheme(
    darkMode: Boolean? = true, // Always dark, parameter kept for compatibility
    amoledMode: Boolean = false,
    dynamicTheming: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val dynamicThemingSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    
    // Always use dark theme - Vanderwaals is dark mode only
    val colors = when {
        dynamicTheming && dynamicThemingSupported -> dynamicDarkColorScheme(context)
        amoledMode -> AmoledDarkColors
        else -> DarkColors
    }

    // Set the status bar and navigation bar to dark with dark icons
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Always use dark icons/buttons for dark theme
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = MaterialTheme.typography,
        content = content,
        shapes = MaterialTheme.shapes
    )
}