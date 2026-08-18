package me.avinas.vanderwaals.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val VanderwaalsDarkColorScheme = darkColorScheme(
    primary = BrandPrimary,
    onPrimary = Color.White,
    primaryContainer = BrandPrimaryDark,
    onPrimaryContainer = BrandPrimaryLight,
    
    secondary = BrandAccent,
    onSecondary = Color.White,
    secondaryContainer = BrandAccent.copy(alpha = 0.2f),
    onSecondaryContainer = BrandAccentLight,
    
    tertiary = BrandAccent,
    onTertiary = Color.White,
    tertiaryContainer = BrandAccent.copy(alpha = 0.15f),
    onTertiaryContainer = BrandAccentLight,
    
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
 * @param darkTheme always true (dark mode only)
 * @param dynamicColor use Material You colors on Android 12+ (default: false)
 * @param content the composable content to be themed
 */
@Composable
fun VanderwaalsTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            dynamicDarkColorScheme(context)
        }
        else -> VanderwaalsDarkColorScheme
    }
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = VanderwaalsTypography,
        shapes = VanderwaalsShapes,
        content = {
            androidx.compose.runtime.CompositionLocalProvider(
                LocalThemeIsDark provides true,
                content = content
            )
        }
    )
}

/**
 * CompositionLocal to provide the current theme mode (always true for dark mode only).
 */
val LocalThemeIsDark = androidx.compose.runtime.compositionLocalOf { true }

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
 * A simple background container that uses the theme's dark background color.
 */
@Composable
fun LiquidGlassBackground(
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        content()
    }
}
