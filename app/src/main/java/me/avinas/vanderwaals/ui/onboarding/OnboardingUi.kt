package me.avinas.vanderwaals.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.avinas.vanderwaals.ui.settings.RadicalPalette
import me.avinas.vanderwaals.ui.settings.RadicalTactileBackdrop
import me.avinas.vanderwaals.ui.settings.RadicalTactileCard
import me.avinas.vanderwaals.ui.theme.LocalNavigationBarPadding
import me.avinas.vanderwaals.ui.theme.PlayfairDisplayFamily

@Immutable
data class OnboardingLayoutMetrics(
    val horizontalPadding: Dp,
    val maxContentWidth: Dp,
    val sectionSpacing: Dp,
    val cardSpacing: Dp,
    val buttonHeight: Dp,
    val compactWidth: Boolean,
    val compactHeight: Boolean,
    val expandedWidth: Boolean,
    val styleColumns: Int,
    val galleryMinCellSize: Dp,
    val cardCornerRadius: Dp,
    val iconBoxSize: Dp,
    val iconSize: Dp,
    val topBarHeight: Dp
)

@Composable
fun rememberOnboardingLayoutMetrics(): OnboardingLayoutMetrics {
    val configuration = LocalConfiguration.current
    val widthDp = configuration.screenWidthDp
    val heightDp = configuration.screenHeightDp

    val compactWidth = widthDp < 360
    val standardPhoneWidth = widthDp in 360..420
    val compactHeight = heightDp < 700
    val expandedWidth = widthDp >= 720

    val horizontalPadding = when {
        expandedWidth -> 32.dp
        compactWidth -> 14.dp
        standardPhoneWidth -> 18.dp
        else -> 22.dp
    }

    val maxContentWidth = when {
        widthDp >= 960 -> 720.dp
        widthDp >= 720 -> 640.dp
        else -> 560.dp
    }

    val sectionSpacing = when {
        compactHeight -> 12.dp
        expandedWidth -> 28.dp
        compactWidth -> 14.dp
        standardPhoneWidth -> 16.dp
        else -> 20.dp
    }

    val cardSpacing = when {
        compactWidth -> 8.dp
        standardPhoneWidth -> 10.dp
        expandedWidth -> 14.dp
        else -> 12.dp
    }

    val buttonHeight = when {
        compactHeight || compactWidth -> 48.dp
        standardPhoneWidth -> 52.dp
        else -> 56.dp
    }

    val styleColumns = when {
        widthDp >= 960 -> 4
        widthDp >= 640 -> 3
        else -> 2
    }

    val galleryMinCellSize = when {
        widthDp >= 960 -> 200.dp
        widthDp >= 720 -> 168.dp
        compactWidth -> 130.dp
        standardPhoneWidth -> 142.dp
        else -> 152.dp
    }

    val cardCornerRadius = when {
        compactWidth -> 14.dp
        standardPhoneWidth -> 16.dp
        else -> 18.dp
    }

    val iconBoxSize = when {
        compactWidth -> 38.dp
        standardPhoneWidth -> 42.dp
        else -> 44.dp
    }

    val iconSize = when {
        compactWidth -> 18.dp
        standardPhoneWidth -> 20.dp
        else -> 22.dp
    }

    val topBarHeight = when {
        compactHeight || compactWidth -> 48.dp
        standardPhoneWidth -> 52.dp
        else -> 56.dp
    }

    return remember(
        widthDp,
        heightDp,
        horizontalPadding,
        maxContentWidth,
        sectionSpacing,
        cardSpacing,
        buttonHeight,
        compactWidth,
        compactHeight,
        expandedWidth,
        styleColumns,
        galleryMinCellSize,
        cardCornerRadius,
        iconBoxSize,
        iconSize,
        topBarHeight
    ) {
        OnboardingLayoutMetrics(
            horizontalPadding = horizontalPadding,
            maxContentWidth = maxContentWidth,
            sectionSpacing = sectionSpacing,
            cardSpacing = cardSpacing,
            buttonHeight = buttonHeight,
            compactWidth = compactWidth,
            compactHeight = compactHeight,
            expandedWidth = expandedWidth,
            styleColumns = styleColumns,
            galleryMinCellSize = galleryMinCellSize,
            cardCornerRadius = cardCornerRadius,
            iconBoxSize = iconBoxSize,
            iconSize = iconSize,
            topBarHeight = topBarHeight
        )
    }
}

/** Shared across onboarding screens. */
@Composable
fun OnboardingBackdrop(
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    RadicalTactileBackdrop(isDark = isDark, modifier = modifier)
}

@Composable
fun OnboardingStepIndicator(
    currentStep: Int,
    totalSteps: Int,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    accentColor: Color = if (isDark) Color.White else Color(0xFF0F172A)
) {
    val trackBg = if (isDark) {
        Color(0xFF141822)
    } else {
        Color(0xFFE2E8F0)
    }
    val trackBorder = if (isDark) {
        Color.White.copy(alpha = 0.10f)
    } else {
        Color.Black.copy(alpha = 0.06f)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(99.dp))
            .background(trackBg)
            .border(0.8.dp, trackBorder, RoundedCornerShape(99.dp))
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until totalSteps) {
            val isCompleted = i < currentStep
            val isCurrent = i == currentStep

            val fraction by animateFloatAsState(
                targetValue = when {
                    isCompleted -> 1f
                    isCurrent -> 1f
                    else -> 0f
                },
                animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
                label = "step_fraction_$i"
            )

            val barColor by animateColorAsState(
                targetValue = when {
                    isCurrent -> accentColor
                    isCompleted -> accentColor.copy(alpha = 0.90f)
                    else -> Color.Transparent
                },
                animationSpec = tween(240),
                label = "step_color_$i"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f))
            ) {
                if (fraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(99.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        barColor,
                                        if (isCurrent) barColor.copy(alpha = 0.92f) else barColor
                                    )
                                )
                            )
                            .border(
                                0.5.dp,
                                if (isDark) Color.White.copy(alpha = if (isCurrent) 0.55f else 0.25f) else Color.Black.copy(alpha = 0.12f),
                                RoundedCornerShape(99.dp)
                            )
                    )
                }
            }
        }
    }
}

@Composable
fun OnboardingHeader(
    title: String,
    subtitle: String,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    accentColor: Color = RadicalPalette.EmeraldJade
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontFamily = PlayfairDisplayFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A),
            letterSpacing = (-0.4).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 28.sp
        )

        if (subtitle.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569),
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun OnboardingTopBar(
    isDark: Boolean,
    metrics: OnboardingLayoutMetrics,
    onBack: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(metrics.topBarHeight),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = metrics.maxContentWidth)
                .padding(horizontal = metrics.horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (onBack != null) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .shadow(
                            elevation = if (isDark) 4.dp else 2.dp,
                            shape = CircleShape,
                            ambientColor = Color.Black.copy(alpha = 0.25f),
                            spotColor = Color.Black.copy(alpha = 0.20f)
                        )
                        .clip(CircleShape)
                        .background(
                            if (isDark) {
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF1E2433),
                                        Color(0xFF111622)
                                    )
                                )
                            } else {
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White,
                                        Color(0xFFF1F5F9)
                                    )
                                )
                            }
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = if (isDark) 0.25f else 0.9f),
                                    if (isDark) Color.Black.copy(alpha = 0.5f) else Color(0xFFCBD5E1)
                                )
                            ),
                            shape = CircleShape
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onBack()
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = if (isDark) Color.White else Color(0xFF0F172A),
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                Spacer(Modifier.size(40.dp))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                trailing()
            }
        }
    }
}

@Composable
fun OnboardingBottomBar(
    isDark: Boolean,
    metrics: OnboardingLayoutMetrics,
    buttonEnabled: Boolean = true,
    buttonText: String = "Continue",
    showLoading: Boolean = false,
    loadingText: String = "",
    onButtonClick: () -> Unit,
    extraContent: @Composable (() -> Unit)? = null,
    accentColor: Color = RadicalPalette.EmeraldJade
) {
    val systemNavBarPadding = LocalNavigationBarPadding.current
    val bottomInset = if (systemNavBarPadding != Dp.Unspecified) {
        systemNavBarPadding
    } else {
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    }
    val haptic = LocalHapticFeedback.current

    val scrimColors = if (isDark) {
        listOf(
            Color.Transparent,
            RadicalPalette.DarkCanvasBase.copy(alpha = 0.75f),
            RadicalPalette.DarkCanvasBase.copy(alpha = 0.95f),
            RadicalPalette.DarkCanvasBase
        )
    } else {
        listOf(
            Color.Transparent,
            RadicalPalette.LightCanvasBase.copy(alpha = 0.75f),
            RadicalPalette.LightCanvasBase.copy(alpha = 0.95f),
            RadicalPalette.LightCanvasBase
        )
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val buttonScale by animateFloatAsState(
        targetValue = if (isPressed && buttonEnabled && !showLoading) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
        label = "btnScale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(colors = scrimColors))
            .padding(horizontal = metrics.horizontalPadding)
            .padding(top = 10.dp, bottom = bottomInset + 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        extraContent?.let {
            Box(modifier = Modifier.widthIn(max = metrics.maxContentWidth).fillMaxWidth()) { it() }
        }

        val buttonShape = RoundedCornerShape(16.dp)

        val activeGradient = Brush.verticalGradient(
            colors = listOf(
                accentColor,
                accentColor.copy(alpha = 0.88f)
            )
        )

        val disabledGradient = if (isDark) {
            Brush.verticalGradient(listOf(Color(0xFF1E2433), Color(0xFF141A26)))
        } else {
            Brush.verticalGradient(listOf(Color(0xFFE2E8F0), Color(0xFFCBD5E1)))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = metrics.maxContentWidth)
                .graphicsLayer {
                    scaleX = buttonScale
                    scaleY = buttonScale
                }
                .shadow(
                    elevation = if (buttonEnabled && !showLoading) if (isPressed) 2.dp else 6.dp else 0.dp,
                    shape = buttonShape,
                    ambientColor = if (buttonEnabled) accentColor.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.1f),
                    spotColor = if (buttonEnabled) accentColor.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.1f)
                )
                .height(metrics.buttonHeight)
                .clip(buttonShape)
                .background(if (buttonEnabled && !showLoading) activeGradient else disabledGradient)
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (buttonEnabled && !showLoading) 0.40f else 0.12f),
                            Color.Black.copy(alpha = if (buttonEnabled && !showLoading) 0.25f else 0.20f)
                        )
                    ),
                    shape = buttonShape
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(color = Color.White.copy(alpha = 0.25f)),
                    enabled = buttonEnabled && !showLoading,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onButtonClick()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (showLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.2.dp
                    )
                    if (loadingText.isNotEmpty()) {
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = loadingText,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                } else {
                    Text(
                        text = buttonText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (buttonEnabled) Color.White else if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8),
                        letterSpacing = 0.2.sp
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (buttonEnabled) Color.White else if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8)
                    )
                }
            }
        }
    }
}

@Composable
fun TactileStepChip(
    text: String,
    accentColor: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(99.dp))
            .background(
                if (isDark) {
                    accentColor.copy(alpha = 0.15f)
                } else {
                    accentColor.copy(alpha = 0.12f)
                }
            )
            .border(
                1.dp,
                if (isDark) {
                    accentColor.copy(alpha = 0.35f)
                } else {
                    accentColor.copy(alpha = 0.25f)
                }
            ,
                RoundedCornerShape(99.dp)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(accentColor)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (isDark) accentColor else accentColor.copy(alpha = 0.95f),
            letterSpacing = 0.3.sp
        )
    }
}

@Composable
fun TactileHeroBadge(
    icon: ImageVector,
    accentColor: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    iconSize: Dp = 32.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = if (isDark) 8.dp else 4.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = accentColor.copy(alpha = 0.35f),
                spotColor = accentColor.copy(alpha = 0.45f)
            )
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isDark) {
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF222B3D),
                            Color(0xFF141924)
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White,
                            Color(0xFFF1F5F9)
                        )
                    )
                }
            )
            .border(
                width = 1.2.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.8f),
                        accentColor.copy(alpha = 0.25f)
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size * 0.65f)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = if (isDark) 0.18f else 0.12f))
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = accentColor
        )
    }
}

/**
 * Tactile helper card wrapping RadicalTactileCard with consistent bounceClick behavior.
 */
@Composable
fun TactileOnboardingCard(
    isDark: Boolean,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    RadicalTactileCard(
        isDark = isDark,
        modifier = modifier.let { if (onClick != null) it.bounceClick(onClick) else it },
        contentPadding = contentPadding,
        content = content
    )
}

fun getOnboardingTextPrimary(isDark: Boolean): Color =
    if (isDark) RadicalPalette.DarkCardTextPrimary else RadicalPalette.LightCardTextPrimary

fun getOnboardingTextSecondary(isDark: Boolean): Color =
    if (isDark) RadicalPalette.DarkCardTextSecondary else RadicalPalette.LightCardTextSecondary

fun getOnboardingCardBackground(isDark: Boolean): Color =
    if (isDark) RadicalPalette.DarkCardTop else RadicalPalette.LightCardTop

fun getOnboardingCardBorder(isDark: Boolean): Color =
    if (isDark) RadicalPalette.DarkCardBorderBottom else RadicalPalette.LightCardBorderBottom

fun Modifier.bounceClick(
    onClick: () -> Unit = {}
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.982f else 1f,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pressScale"
    )
    val haptic = LocalHapticFeedback.current
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(
            interactionSource = interactionSource,
            indication = ripple(),
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
        )
}
