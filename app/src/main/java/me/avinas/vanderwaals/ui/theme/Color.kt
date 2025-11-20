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
val BackgroundDark = Color(0xFF0A0A0F) // Deep dark background - Pure black with subtle blue tint
val SurfaceDark = Color(0xFF16161F) // Primary surface
val SurfaceElevated = Color(0xFF1E1E2D) // Elevated surface (cards, dialogs)
val SurfaceHighlight = Color(0xFF2A2A3E) // Highest elevation
val SurfaceTransparent = Color(0x1Aa8a095) // Transparent tan tint
val SurfaceGlass = Color(0x4D16161F) // Glassmorphism effect (30% opacity)
val SurfaceGlassHighlight = Color(0x661E1E2D) // Glassmorphism highlight (40% opacity)

// === TEXT COLORS - Enhanced Contrast ===
val TextPrimaryDark = Color(0xFFF5F5F7) // High emphasis text - Near white
val TextSecondaryDark = Color(0xFFB8B8BD) // Medium emphasis - Light grey
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
