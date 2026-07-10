package me.avinas.vanderwaals.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ===== VANDERWAALS PREMIUM DESIGN SYSTEM =====
// A sophisticated, modern color palette with rich dark and clean light modes

// === PRIMARY BRAND COLORS - Electric Blue Spectrum ===
val BrandPrimary = Color(0xFF3B82F6) // Vibrant blue - main brand
val BrandPrimaryDark = Color(0xFF2563EB) // Deeper blue for dark mode
val BrandPrimaryLight = Color(0xFF60A5FA) // Lighter blue for highlights
val BrandPrimaryMuted = Color(0xFF93C5FD) // Muted blue for secondary elements
val BrandAccent = Color(0xFF8B5CF6) // Violet accent for contrast
val BrandAccentLight = Color(0xFFA78BFA) // Lighter violet

// === GRADIENT DEFINITIONS ===
val GradientPrimary = Brush.horizontalGradient(
    colors = listOf(BrandPrimary, BrandAccent)
)

val GradientPrimaryVertical = Brush.verticalGradient(
    colors = listOf(BrandPrimary, BrandAccent)
)

val GradientSubtle = Brush.horizontalGradient(
    colors = listOf(BrandPrimary.copy(alpha = 0.8f), BrandPrimaryLight)
)

val GradientHero = Brush.linearGradient(
    colors = listOf(BrandPrimary, BrandAccent, Color(0xFFEC4899))
)

// === PREMIUM ONBOARDING GRADIENTS ===
val GradientOnboardingPrimary = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF030712),
        Color(0xFF0A0F1A),
        Color(0xFF050810)
    )
)

val GradientOnboardingSecondary = Brush.verticalGradient(
    colors = listOf(
        Color(0xFFF8FAFC),
        Color(0xFFF1F5F9),
        Color(0xFFF8FAFC)
    )
)

val GradientCardDark = Brush.linearGradient(
    colors = listOf(
        Color(0xFF18181B),
        Color(0xFF0F0F12)
    )
)

val GradientCardLight = Brush.linearGradient(
    colors = listOf(
        Color(0xFFFFFFFF),
        Color(0xFFF8FAFC)
    )
)

val GradientButtonPrimary = Brush.horizontalGradient(
    colors = listOf(BrandPrimary, BrandAccent)
)

val GradientButtonSecondary = Brush.horizontalGradient(
    colors = listOf(BrandAccent, Color(0xFFEC4899))
)

val GradientOverlayDark = Brush.verticalGradient(
    colors = listOf(
        Color.Transparent,
        Color(0xCC030712),
        Color(0xFF030712)
    )
)

val GradientOverlayLight = Brush.verticalGradient(
    colors = listOf(
        Color.Transparent,
        Color(0xCCF8FAFC),
        Color(0xFFF8FAFC)
    )
)

// === GLOW EFFECTS ===
val GlowPrimaryLarge = Brush.radialGradient(
    colors = listOf(
        BrandPrimary.copy(alpha = 0.3f),
        BrandPrimary.copy(alpha = 0.1f),
        Color.Transparent
    )
)

val GlowAccentLarge = Brush.radialGradient(
    colors = listOf(
        BrandAccent.copy(alpha = 0.25f),
        BrandAccent.copy(alpha = 0.08f),
        Color.Transparent
    )
)

// === NEUTRAL PALETTE - Dark Mode ===

// === NEUTRAL PALETTE - Dark Mode ===
val Neutral50 = Color(0xFFFAFAFA)
val Neutral100 = Color(0xFFF5F5F5)
val Neutral200 = Color(0xFFE5E5E5)
val Neutral300 = Color(0xFFD4D4D4)
val Neutral400 = Color(0xFFA3A3A3)
val Neutral500 = Color(0xFF737373)
val Neutral600 = Color(0xFF525252)
val Neutral700 = Color(0xFF404040)
val Neutral800 = Color(0xFF262626)
val Neutral900 = Color(0xFF171717)
val Neutral950 = Color(0xFF0A0A0B)

// === DARK THEME BACKGROUND HIERARCHY ===
val BackgroundDark = Color(0xFF09090B) // Near pure black
val SurfaceDark = Color(0xFF111113) // Primary surface
val SurfaceElevatedDark = Color(0xFF18181B) // Elevated cards
val SurfaceOverlayDark = Color(0xFF27272A) // Modals, dialogs
val SurfaceHighlightDark = Color(0xFF3F3F46) // Interactive states

// === DARK THEME TEXT COLORS ===
val TextPrimaryDark = Color(0xFFFAFAFA) // Primary text
val TextSecondaryDark = Color(0xFFA1A1AA) // Secondary text
val TextTertiaryDark = Color(0xFF71717A) // Tertiary/disabled
val TextInverseDark = Color(0xFF09090B) // Text on light surfaces

// === DARK THEME BORDERS & DIVIDERS ===
val BorderDark = Color(0xFF27272A)
val BorderSubtleDark = Color(0xFF1F1F23)
val DividerDark = Color(0xFF27272A)

// === LIGHT THEME BACKGROUND HIERARCHY ===
val BackgroundLight = Color(0xFFFAFAFA) // Warm off-white
val SurfaceLight = Color(0xFFFFFFFF) // Pure white cards
val SurfaceElevatedLight = Color(0xFFFFFFFF) // Elevated surfaces
val SurfaceOverlayLight = Color(0xFFF5F5F5) // Subtle backgrounds
val SurfaceHighlightLight = Color(0xFFE5E5E5) // Interactive states

// === LIGHT THEME TEXT COLORS ===
val TextPrimaryLight = Color(0xFF0A0A0B) // Near black
val TextSecondaryLight = Color(0xFF525252) // Medium gray
val TextTertiaryLight = Color(0xFF737373) // Lighter gray
val TextInverseLight = Color(0xFFFAFAFA) // Text on dark surfaces

// === LIGHT THEME BORDERS & DIVIDERS ===
val BorderLight = Color(0xFFE5E5E5)
val BorderSubtleLight = Color(0xFFF0F0F0)
val DividerLight = Color(0xFFE5E5E5)

// === STATE COLORS - Universal ===
val SuccessColor = Color(0xFF22C55E) // Green
val SuccessContainer = Color(0xFF22C55E).copy(alpha = 0.1f)
val ErrorColor = Color(0xFFEF4444) // Red
val ErrorContainer = Color(0xFFEF4444).copy(alpha = 0.1f)
val WarningColor = Color(0xFFF59E0B) // Amber
val WarningContainer = Color(0xFFF59E0B).copy(alpha = 0.1f)
val InfoColor = Color(0xFF3B82F6) // Blue
val InfoContainer = Color(0xFF3B82F6).copy(alpha = 0.1f)

// === INTERACTIVE COLORS ===
val InteractiveHover = BrandPrimary.copy(alpha = 0.08f)
val InteractivePressed = BrandPrimary.copy(alpha = 0.12f)
val RippleColor = Color(0x1A000000)

// === OVERLAY & SCRIM COLORS ===
val ScrimDark = Color(0x99000000)
val ScrimLight = Color(0x99FFFFFF)
val OverlayDark = Color(0xCC000000)

// === CARD BACKGROUNDS ===
val CardBackgroundDark = SurfaceElevatedDark
val CardBackgroundLight = SurfaceLight
val CardBorderDark = BorderDark
val CardBorderLight = BorderLight

// === INPUT BACKGROUNDS ===
val InputBackgroundDark = SurfaceDark
val InputBackgroundLight = BackgroundLight
val InputBorderDark = BorderDark
val InputBorderLight = BorderLight
val InputBorderFocused = BrandPrimary

// === GLOW & SHADOW EFFECTS ===
val GlowPrimary = BrandPrimary.copy(alpha = 0.3f)
val GlowAccent = BrandAccent.copy(alpha = 0.3f)
val ShadowDark = Color(0x40000000)
val ShadowLight = Color(0x0A000000)

// === LUXE ONBOARDING COLORS ===
val LuxeBackground = Color(0xFF1A1A1A) // Warm dark background
val LuxeCardBackground = Color.White.copy(alpha = 0.06f) // Frosted glass
val LuxeCardBorder = Color.White.copy(alpha = 0.1f) // Glass edge
val LuxeTextPrimary = Color(0xFFF5F5F5) // Warm white text
val LuxeTextSecondary = Color(0xFFB0B0B0) // Muted secondary text
val LuxeGradientStart = Color(0xFF2D1B00) // Warm amber-dark
val LuxeGradientMid = Color(0xFF1A1A2E) // Deep indigo
val LuxeGradientEnd = Color(0xFF1A1A1A) // Warm dark

// === LEGACY COMPATIBILITY (for existing code references) ===
val VanderwaalsTan = BrandPrimary
val VanderwaalsTanDark = BrandPrimaryDark
val VanderwaalsTanLight = BrandPrimaryLight
val VanderwaalsAccent = BrandAccent
val VanderwaalsAccentLight = BrandAccentLight
val LightPrimary = BrandPrimaryDark
val InfoColorDark = BrandPrimary
val ErrorColorDark = ErrorColor
val SuccessColorDark = SuccessColor
val WarningColorDark = WarningColor
val PurpleGrey80 = Neutral400
val PurpleGrey40 = Neutral600
val PurpleGrey20 = Neutral700
val Pink80 = BrandAccent
val Pink40 = BrandAccent
val Pink20 = BrandAccent
val Tan80 = BrandPrimaryLight
val Tan40 = BrandPrimary
val Tan20 = BrandPrimaryDark
val GlassBackground = SurfaceElevatedDark
val GlassBorder = BorderDark
val GlassShadow = ShadowDark
val GlassHighlight = SurfaceHighlightDark
val GlassHighlightSubtle = SurfaceHighlightDark.copy(alpha = 0.5f)
val GlassInsetTop = Color.Transparent
val GlassInsetBottom = Color.Transparent
val GlassInnerGlow = Color.Transparent
val GlassBackgroundLight = SurfaceLight
val GlassBorderLight = BorderLight
val GlassShadowLight = ShadowLight
val GlassHighlightLight = SurfaceHighlightLight
val GlassGradientTop = Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent, Color.Transparent))
val GlassGradientLeft = Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color.Transparent))
val GlassGradientTopLight = Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent, Color.Transparent))
val GlassGradientLeftLight = Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color.Transparent))
val SurfaceGlass = SurfaceDark
val SurfaceGlassHighlight = SurfaceHighlightDark
val SurfaceGlassLight = SurfaceLight
val SurfaceGlassHighlightLight = SurfaceHighlightLight
val TextPrimary = TextPrimaryDark
val TextSecondary = TextSecondaryDark
val TextTertiary = TextTertiaryDark
val BorderGlow = BrandPrimary
val DividerSubtle = DividerDark.copy(alpha = 0.5f)
val FeedbackLike = Color(0xFFEC4899)
val FeedbackDislike = BrandPrimary
val FeedbackDownload = SuccessColor
val FeedbackShare = BrandAccent
val InteractiveHover1 = InteractiveHover
val InteractivePressed1 = InteractivePressed
val ShimmerColor = SurfaceElevatedDark
val ShimmerHighlight = SurfaceOverlayDark
val CardBackground = CardBackgroundDark
val CardBackgroundElevated = SurfaceOverlayDark
val CardBorder = CardBorderDark
val CardBorderHighlight = SurfaceHighlightDark
val InputBackground = InputBackgroundDark
val InputBorder = InputBorderDark
val InputBorderFocused1 = InputBorderFocused
val InputBorderError = ErrorColor
val BackgroundGradient = Brush.verticalGradient(listOf(BackgroundDark, SurfaceDark, BackgroundDark))
val BackgroundGradientTan = GradientPrimaryVertical
val GlowTan = GlowPrimary
val GlowAccent1 = GlowAccent
val ShadowColor = ShadowDark
val BorderHighlight = SurfaceHighlightDark
val BorderHighlightLight = SurfaceHighlightLight
val BorderGlassLight = BorderLight
val ScrimColor = ScrimDark
val ScrimColorLight = ScrimLight
val OverlayMedium = OverlayDark
val OverlayLight1 = Color(0x66000000)
val OverlaySubtle = Color(0x33000000)
val RippleColor1 = RippleColor
val SurfaceTransparent = Color.Transparent
val SurfaceTransparentLight = Color.Transparent
val GradientAccent = GradientPrimary
val GradientVertical = GradientPrimaryVertical
val GradientRadial = Brush.radialGradient(listOf(BrandPrimaryLight, BrandPrimary, BrandPrimaryDark))
val GradientSunset = GradientHero
val GradientFusion = GradientHero
val GradientFusionLight = GradientPrimary
val GradientPrimaryLight = Brush.horizontalGradient(listOf(BrandPrimaryDark, BrandPrimary))
val GradientVerticalLight = Brush.verticalGradient(listOf(BrandPrimaryDark, BrandPrimary))
val CinematicBlue = BrandPrimary.copy(alpha = 0.3f)
val CinematicTeal = BrandAccent.copy(alpha = 0.3f)
val CinematicPurple = BrandAccent
val CinematicRose = Color(0xFFEC4899).copy(alpha = 0.3f)
val CinematicRoseMuted = Color(0xFFEC4899).copy(alpha = 0.2f)
val DarkBackground = BackgroundDark
val AiryBlue = BrandPrimaryLight.copy(alpha = 0.3f)
val AiryPurple = BrandAccentLight.copy(alpha = 0.3f)
val AiryTeal = SuccessColor.copy(alpha = 0.3f)
val AiryRose = Color(0xFFEC4899).copy(alpha = 0.3f)
val AiryAmber = WarningColor.copy(alpha = 0.3f)
val LightBackground = BackgroundLight
