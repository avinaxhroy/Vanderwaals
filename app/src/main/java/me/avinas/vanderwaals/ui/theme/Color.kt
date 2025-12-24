package me.avinas.vanderwaals.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ===== VANDERWAALS MODERN DARK THEME - PREMIUM EDITION =====

// === PRIMARY BRAND COLORS - Warm Tan/Beige Spectrum ===
val VanderwaalsTan = Color(0xFFa8a095) // Main brand tan/beige (matches logo)
val VanderwaalsTanDark = Color(0xFF8a7d72) // Darker tan variant
val VanderwaalsTanLight = Color(0xFFc4b9ae) // Lighter tan for highlights
val VanderwaalsBrown = Color(0xFF6d6256) // Deep brown accent
val VanderwaalsAccent = Color(0xFFFF6B9D) // Pink accent (kept for contrast)
val VanderwaalsAccentLight = Color(0xFFFF8FB5) // Lighter pink accent

// === GRADIENT DEFINITIONS - For Premium Effects ===
val GradientPrimary = Brush.horizontalGradient(
    colors = listOf(VanderwaalsTan, VanderwaalsTanDark)
)

val GradientAccent = Brush.horizontalGradient(
    colors = listOf(VanderwaalsTan, VanderwaalsAccent)
)

val GradientVertical = Brush.verticalGradient(
    colors = listOf(VanderwaalsTan, VanderwaalsTanDark)
)

val GradientRadial = Brush.radialGradient(
    colors = listOf(VanderwaalsTanLight, VanderwaalsTan, VanderwaalsTanDark)
)

val GradientSunset = Brush.horizontalGradient(
    colors = listOf(VanderwaalsBrown, VanderwaalsTan, VanderwaalsAccent)
)

// === TAN PALETTE VARIATIONS (replacing purple) ===
val Tan80 = Color(0xFFc4b9ae)
val Tan40 = Color(0xFFa8a095)
val Tan20 = Color(0xFF8a7d72)
val PurpleGrey80 = Color(0xFF9CA3AF)
val PurpleGrey40 = Color(0xFF4B5563)
val PurpleGrey20 = Color(0xFF374151)
val Pink80 = Color(0xFFFF6B9D)
val Pink40 = Color(0xFFDB2777)
val Pink20 = Color(0xFFBE185D)

// === DARK THEME BACKGROUND HIERARCHY - OLED Optimized ===
// === DARK THEME BACKGROUND HIERARCHY - OLED Optimized ===
val BackgroundDark = Color(0xFF000000) // Pure black for OLED
val SurfaceDark = Color(0xFF1C1C1E) // Apple's dark gray surface
val SurfaceElevated = Color(0xFF2C2C2E) // Elevated surface
val SurfaceHighlight = Color(0xFF3A3A3C) // Highest elevation
val SurfaceTransparent = Color(0x00000000) // Fully transparent
val SurfaceGlass = Color(0x4D1C1C1E) // Glassmorphism (30% opacity dark gray) - Apple Style
val SurfaceGlassHighlight = Color(0x1AFFFFFF) // Highlight for glass edges

// === TEXT COLORS - Enhanced Contrast ===
val TextPrimaryDark = Color(0xFFF5F5F7) // High emphasis text - Near white
val TextSecondaryDark = Color(0xFFEBEBF5) // Medium emphasis - Lighter grey for better contrast on glass
val TextTertiaryDark = Color(0xFF8A8A90) // Low emphasis - Grey
val TextDisabledDark = Color(0xFF5A5A60) // Disabled state - Dark grey
val TextAccent = VanderwaalsTan // Accent colored text
val TextLink = Color(0xFF60A5FA) // Link text - Blue

// === BORDER & DIVIDER COLORS - Refined ===
val BorderDark = Color(0xFF2A2A3E) // Subtle borders
val BorderHighlight = Color(0xFF3A3A4E) // More prominent borders
val BorderGlow = Color(0xFFa8a095) // Glowing accent border
val DividerDark = Color(0xFF1E1E2D) // Dividers
val DividerSubtle = Color(0x1AFFFFFF) // Very subtle divider (10% white)

// === STATE COLORS - Vibrant & Clear ===
val SuccessColor = Color(0xFF34D399) // Emerald green
val SuccessColorDark = Color(0xFF10B981) // Darker emerald
val ErrorColor = Color(0xFFEF4444) // Bright red
val ErrorColorDark = Color(0xFFDC2626) // Darker red
val WarningColor = Color(0xFFF59E0B) // Amber
val WarningColorDark = Color(0xFFD97706) // Darker amber
val InfoColor = Color(0xFF60A5FA) // Sky blue
val InfoColorDark = Color(0xFF3B82F6) // Darker blue

// === INTERACTIVE COLORS - Enhanced Feedback ===
val FeedbackLike = Color(0xFFEC4899) // Pink heart
val FeedbackDislike = Color(0xFF60A5FA) // Blue thumbs down
val FeedbackDownload = Color(0xFF34D399) // Green download
val FeedbackShare = Color(0xFFd4a574) // Tan/bronze share
val InteractiveHover = Color(0x1Aa8a095) // Hover state (10% tan)
val InteractivePressed = Color(0x33a8a095) // Pressed state (20% tan)

// === OVERLAY COLORS - Modernized ===
val OverlayDark = Color(0xCC000000) // 80% black overlay
val OverlayMedium = Color(0x99000000) // 60% black overlay
val OverlayLight = Color(0x66000000) // 40% black overlay
val OverlaySubtle = Color(0x33000000) // 20% black overlay
val ShimmerColor = Color(0xFF2A2A3E) // Shimmer effect base
val ShimmerHighlight = Color(0xFF3A3A4E) // Shimmer highlight
val RippleColor = Color(0x1AFFFFFF) // Ripple effect (10% white)
val ScrimColor = Color(0xB3000000) // Modal backdrop (70% black)

// === SPECIAL UI ELEMENTS - Cards, Inputs, Etc. ===
val CardBackground = Color(0xFF1E1E2D) // Card background
val CardBackgroundElevated = Color(0xFF2A2A3E) // Elevated card
val CardBorder = Color(0xFF2A2A3E) // Card border
val CardBorderHighlight = Color(0xFF3A3A4E) // Highlighted card border
val InputBackground = Color(0xFF16161F) // Input field background
val InputBorder = Color(0xFF3A3A4E) // Input field border
val InputBorderFocused = VanderwaalsTan // Focused input border
val InputBorderError = ErrorColor // Error input border

// === GRADIENT BACKGROUNDS - For Special Screens ===
val BackgroundGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF0A0A0F),
        Color(0xFF16161F),
        Color(0xFF0A0A0F)
    )
)

val BackgroundGradientTan = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF1A1512),
        Color(0xFF0A0A0F),
        Color(0xFF0A0A0F)
    )
)

// === GLOW & SHADOW EFFECTS ===
val GlowTan = Color(0x4Da8a095) // Tan glow (30% opacity)
val GlowAccent = Color(0x4DFF6B9D) // Accent glow (30% opacity)
val ShadowColor = Color(0x66000000) // Shadow color (40% black)

// === LIGHT THEME BACKGROUND HIERARCHY - Light ===
val BackgroundLight = Color(0xFFF2F2F7) // Apple's system gray 6
val SurfaceLight = Color(0xFFFFFFFF) // Pure white surface
val SurfaceElevatedLight = Color(0xFFFFFFFF) // Elevated surface
val SurfaceHighlightLight = Color(0xFFFFFFFF) // Highlighted surface
val SurfaceTransparentLight = Color(0x00000000) // Fully transparent
val SurfaceGlassLight = Color(0x66FFFFFF) // Glassmorphism (40% opacity white) - Apple Style
val SurfaceGlassHighlightLight = Color(0x40FFFFFF) // Highlight for glass edges

// === TEXT COLORS - Light ===
val TextPrimaryLight = Color(0xFF1C1917) // High emphasis - Near black
val TextSecondaryLight = Color(0xFF44403C) // Medium emphasis - Dark grey/brown
val TextTertiaryLight = Color(0xFF78716C) // Low emphasis - Grey/brown
val TextDisabledLight = Color(0xFFA8A29E) // Disabled state

// === BORDER & DIVIDER COLORS - Light ===
val BorderLight = Color(0xFFE7E5E4) // Subtle borders
val BorderHighlightLight = Color(0xFFD6D3D1) // More prominent borders
val BorderGlassLight = Color(0x40FFFFFF) // Glass border (25% white) for light mode
val DividerLight = Color(0xFFF5F5F4) // Dividers

// === STATE COLORS - Light ===
val ErrorColorLight = Color(0xFFDC2626) // Red
val SuccessColorLight = Color(0xFF059669) // Green
val WarningColorLight = Color(0xFFD97706) // Amber
val InfoColorLight = Color(0xFF2563EB) // Blue

// === OVERLAY COLORS - Light ===
val ScrimColorLight = Color(0x99FFFFFF) // Modal backdrop (60% white)

// === GRADIENTS - Light Mode ===
val GradientPrimaryLight = Brush.horizontalGradient(
    colors = listOf(VanderwaalsTanDark, VanderwaalsTan)
)

val GradientVerticalLight = Brush.verticalGradient(
    colors = listOf(VanderwaalsTanDark, VanderwaalsTan)
)

// === GLASSMORPHISM COLORS - CSS MATCHED ===
// Dark Mode - CSS Matched
// Dark Mode - CSS Matched
val GlassBackground = Color(0x541F2937) // Reduced opacity ~33% (was 38%) for "tempered glass" feel
val GlassBorder = Color(0x1AFFFFFF) // rgba(255, 255, 255, 0.1)
val GlassShadow = Color(0x1A000000) // rgba(0, 0, 0, 0.1)
val GlassHighlight = Color(0x40FFFFFF) // More subtle highlight
val GlassHighlightSubtle = Color(0x33FFFFFF)
val GlassInsetTop = Color(0x1AFFFFFF)
val GlassInsetBottom = Color(0x14FFFFFF)
val GlassInnerGlow = Color(0x1FFFFFFF)

// Light Mode - Adjusted for visibility on light backgrounds
val GlassBackgroundLight = Color(0x4DFFFFFF) // Reduced opacity ~30% (was 35%)
val GlassBorderLight = Color(0x33FFFFFF) // Slightly stronger border for separation
val GlassShadowLight = Color(0x14000000)
val GlassHighlightLight = Color(0xCCFFFFFF) // Strong white highlight for airy feel

// Gradients
val GlassGradientTop = Brush.horizontalGradient(
    colors = listOf(Color.Transparent, GlassHighlight, Color.Transparent)
)

val GlassGradientLeft = Brush.verticalGradient(
    colors = listOf(GlassHighlight, Color.Transparent, GlassHighlightSubtle)
)

val GlassGradientTopLight = Brush.horizontalGradient(
    colors = listOf(Color.Transparent, GlassHighlightLight, Color.Transparent)
)

val GlassGradientLeftLight = Brush.verticalGradient(
    colors = listOf(GlassHighlightLight, Color.Transparent, GlassBorderLight)
)



// === MOCKUP COLORS - For Blobs ===
// === MOCKUP COLORS - For Cinematic Blobs ===
// Dark Mode: Cinematic, Deep, Rich
val CinematicBlue = Color(0xFF1E3A8A)
val CinematicTeal = Color(0xFF115E59)
val CinematicPurple = Color(0xFF581C87)
val CinematicRose = Color(0xFF881337)
val CinematicRoseMuted = Color(0xFF602133) // Further Desaturated Rose (-10%)
val DarkBackground = Color(0xFF0F172A) // Richer dark blue-grey

// Light Mode: Airy, Vibrant, Separated
val AiryBlue = Color(0xFFBFDBFE)
val AiryPurple = Color(0xFFE9D5FF)
val AiryTeal = Color(0xFF99F6E4)
val AiryRose = Color(0xFFFECDD3)
val AiryAmber = Color(0xFFFDE68A)
val LightBackground = Color(0xFFF8FAFC) // Cool white
val LightPrimary = Color(0xFF10B981)

// === FUSION GRADIENTS - For Logo Card ===
val GradientFusion = Brush.horizontalGradient(
    colors = listOf(
        CinematicBlue.copy(alpha = 0.9f),   // Almost opaque for deep richness
        CinematicPurple.copy(alpha = 0.8f),
        CinematicTeal.copy(alpha = 0.7f)
    )
)

val GradientFusionLight = Brush.horizontalGradient(
    colors = listOf(
        Color(0xFF4F46E5), // Indigo-600: Deep rich blue
        Color(0xFF9333EA), // Purple-600: Vibrant purple
        Color(0xFFDB2777)  // Pink-600: Deep pink pop
    )
)
