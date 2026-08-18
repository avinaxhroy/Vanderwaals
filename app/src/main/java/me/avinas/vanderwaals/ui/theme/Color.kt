package me.avinas.vanderwaals.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// primary brand colors
val CyberMagenta = Color(0xFFFF2A85)
val CyberMagentaDark = Color(0xFFE11D48)
val CyberMagentaDeep = Color(0xFF9F1239)
val CyberMagentaLight = Color(0xFFFDA4AF)
val CyberMagentaMuted = Color(0xFFFECDD3)

val BrandPrimary = CyberMagenta
val BrandPrimaryDark = CyberMagentaDark
val BrandPrimaryLight = CyberMagentaLight
val BrandPrimaryMuted = CyberMagentaMuted
val BrandAccent = CyberMagenta
val BrandAccentLight = CyberMagentaLight
val BrandAccentDark = CyberMagentaDark

val GradientMagenta = Brush.horizontalGradient(
    colors = listOf(CyberMagenta, CyberMagentaDark)
)

val GradientMagentaVertical = Brush.verticalGradient(
    colors = listOf(CyberMagenta, CyberMagentaDark)
)
val GradientAmber = Brush.horizontalGradient(
    colors = listOf(Color(0xFFF59E0B), Color(0xFFD97706))
)
val GradientAmberVertical = Brush.verticalGradient(
    colors = listOf(Color(0xFFF59E0B), Color(0xFFD97706))
)
val SolarAmber = Color(0xFFF59E0B)
val SolarAmberDark = Color(0xFFD97706)

// subtle tonal gradients
val GradientPrimary = Brush.horizontalGradient(
    colors = listOf(BrandPrimary, BrandPrimaryDark)
)

val GradientPrimaryVertical = Brush.verticalGradient(
    colors = listOf(BrandPrimary, BrandPrimaryDark)
)

val GradientSubtle = Brush.horizontalGradient(
    colors = listOf(BrandPrimary.copy(alpha = 0.9f), BrandPrimaryLight)
)

val GradientHero = Brush.linearGradient(
    colors = listOf(BrandPrimary, BrandPrimaryDark)
)

// onboarding ambient gradients
val GradientOnboardingPrimary = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF0C0F14),
        Color(0xFF111722),
        Color(0xFF0C0F14)
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
        Color(0xFF161B22),
        Color(0xFF0F141C)
    )
)

val GradientCardLight = Brush.linearGradient(
    colors = listOf(
        Color(0xFFFFFFFF),
        Color(0xFFF8FAFC)
    )
)

val GradientButtonPrimary = Brush.horizontalGradient(
    colors = listOf(BrandPrimary, BrandPrimaryDark)
)

val GradientButtonSecondary = Brush.horizontalGradient(
    colors = listOf(BrandPrimaryDark, BrandPrimary)
)

val GradientOverlayDark = Brush.verticalGradient(
    colors = listOf(
        Color.Transparent,
        Color(0xCC0C0F14),
        Color(0xFF0C0F14)
    )
)

val GradientOverlayLight = Brush.verticalGradient(
    colors = listOf(
        Color.Transparent,
        Color(0xCCF8FAFC),
        Color(0xFFF8FAFC)
    )
)

// neutral palette
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

// dark theme backgrounds
val BackgroundDark = Color(0xFF0E1117)
val SurfaceDark = Color(0xFF161B22)
val SurfaceElevatedDark = Color(0xFF1F242C)
val SurfaceOverlayDark = Color(0xFF282E38)
val SurfaceHighlightDark = Color(0xFF373E4B)

// dark theme text
val TextPrimaryDark = Color(0xFFF0F6FC)
val TextSecondaryDark = Color(0xFF8B949E)
val TextTertiaryDark = Color(0xFF6E7681)
val TextInverseDark = Color(0xFF0E1117)

// dark theme borders
val BorderDark = Color(0xFF30363D)
val BorderSubtleDark = Color(0xFF21262D)
val DividerDark = Color(0xFF21262D)

// light theme backgrounds
val BackgroundLight = Color(0xFFF6F8FA)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceElevatedLight = Color(0xFFFFFFFF)
val SurfaceOverlayLight = Color(0xFFF0F2F5)
val SurfaceHighlightLight = Color(0xFFE1E4E8)

// light theme text
val TextPrimaryLight = Color(0xFF1F2328)
val TextSecondaryLight = Color(0xFF656D76)
val TextTertiaryLight = Color(0xFF8C959F)
val TextInverseLight = Color(0xFFF0F6FC)

// light theme borders
val BorderLight = Color(0xFFD0D7DE)
val BorderSubtleLight = Color(0xFFE1E4E8)
val DividerLight = Color(0xFFD0D7DE)

// state colors
val SuccessColor = Color(0xFF16A34A)
val SuccessContainer = Color(0xFF16A34A).copy(alpha = 0.1f)
val ErrorColor = Color(0xFFDC2626)
val ErrorContainer = Color(0xFFDC2626).copy(alpha = 0.1f)
val WarningColor = Color(0xFFD97706)
val WarningContainer = Color(0xFFD97706).copy(alpha = 0.1f)
val InfoColor = Color(0xFF38BDF8)
val InfoContainer = Color(0xFF38BDF8).copy(alpha = 0.1f)

// interactive colors
val InteractiveHover = BrandPrimary.copy(alpha = 0.08f)
val InteractivePressed = BrandPrimary.copy(alpha = 0.12f)
val RippleColor = Color(0x1A000000)

// overlays and scrims
val ScrimDark = Color(0x99000000)
val ScrimLight = Color(0x99FFFFFF)
val OverlayDark = Color(0xCC000000)

// card backgrounds
val CardBackgroundDark = SurfaceElevatedDark
val CardBackgroundLight = SurfaceLight
val CardBorderDark = BorderDark
val CardBorderLight = BorderLight

// input backgrounds
val InputBackgroundDark = SurfaceDark
val InputBackgroundLight = BackgroundLight
val InputBorderDark = BorderDark
val InputBorderLight = BorderLight
val InputBorderFocused = BrandPrimary

// elevation shadows
val ShadowDark = Color(0x40000000)
val ShadowLight = Color(0x0A000000)

// onboarding colors
val LuxeBackground = Color(0xFF12151B)
val LuxeCardBackground = Color.White.copy(alpha = 0.05f)
val LuxeCardBorder = Color.White.copy(alpha = 0.08f)
val LuxeTextPrimary = Color(0xFFF0F6FC)
val LuxeTextSecondary = Color(0xFF9BA3AF)
val LuxeGradientStart = Color(0xFF161B22)
val LuxeGradientMid = Color(0xFF1F242C)
val LuxeGradientEnd = Color(0xFF12151B)

// compatibility bindings
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
val BorderGlow = BorderDark
val DividerSubtle = DividerDark.copy(alpha = 0.5f)
val FeedbackLike = CyberMagenta
val FeedbackDislike = Color(0xFF64748B)
val FeedbackDownload = Color(0xFF10B981)
val FeedbackShare = BrandPrimary
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
val GlowTan = BrandPrimary.copy(alpha = 0.1f)
val GlowAccent1 = BrandAccent.copy(alpha = 0.1f)
val GlowPrimary = BrandPrimary.copy(alpha = 0.1f)
val GlowAccent = BrandAccent.copy(alpha = 0.1f)
val GlowPrimaryLarge = Brush.radialGradient(listOf(Color.Transparent, Color.Transparent))
val GlowAccentLarge = Brush.radialGradient(listOf(Color.Transparent, Color.Transparent))
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
val GradientSunset = GradientPrimary
val GradientFusion = GradientPrimary
val GradientFusionLight = GradientPrimary
val GradientPrimaryLight = Brush.horizontalGradient(listOf(BrandPrimaryDark, BrandPrimary))
val GradientVerticalLight = Brush.verticalGradient(listOf(BrandPrimaryDark, BrandPrimary))
val CinematicBlue = BrandPrimary.copy(alpha = 0.15f)
val CinematicTeal = BrandAccent.copy(alpha = 0.15f)
val CinematicPurple = BrandPrimary.copy(alpha = 0.15f)
val CinematicRose = Color(0xFF64748B).copy(alpha = 0.15f)
val CinematicRoseMuted = Color(0xFF64748B).copy(alpha = 0.1f)
val DarkBackground = BackgroundDark
val AiryBlue = BrandPrimaryLight.copy(alpha = 0.15f)
val AiryPurple = BrandAccentLight.copy(alpha = 0.15f)
val AiryTeal = SuccessColor.copy(alpha = 0.15f)
val AiryRose = Color(0xFF94A3B8).copy(alpha = 0.15f)
val AiryAmber = WarningColor.copy(alpha = 0.15f)
val LightBackground = BackgroundLight