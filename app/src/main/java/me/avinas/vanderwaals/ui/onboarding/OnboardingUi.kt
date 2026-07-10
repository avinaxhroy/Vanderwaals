package me.avinas.vanderwaals.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.avinas.vanderwaals.ui.theme.BrandPrimary
import me.avinas.vanderwaals.ui.theme.BrandAccent
import me.avinas.vanderwaals.ui.theme.LocalNavigationBarPadding
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.composed

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

    val compactWidth = widthDp < 380
    val compactHeight = heightDp < 700
    val expandedWidth = widthDp >= 720

    val horizontalPadding = when {
        expandedWidth -> 36.dp
        compactWidth -> 18.dp
        else -> 24.dp
    }

    val maxContentWidth = when {
        widthDp >= 960 -> 720.dp
        widthDp >= 720 -> 620.dp
        else -> 520.dp
    }

    val sectionSpacing = when {
        compactHeight -> 12.dp
        expandedWidth -> 24.dp
        else -> 18.dp
    }

    val cardSpacing = when {
        compactWidth -> 10.dp
        expandedWidth -> 16.dp
        else -> 12.dp
    }

    val buttonHeight = when {
        compactHeight -> 52.dp
        expandedWidth -> 60.dp
        else -> 56.dp
    }

    val styleColumns = when {
        widthDp >= 960 -> 4
        widthDp >= 640 -> 3
        else -> 2
    }

    val galleryMinCellSize = when {
        widthDp >= 960 -> 220.dp
        widthDp >= 720 -> 180.dp
        compactWidth -> 130.dp
        else -> 150.dp
    }

    val cardCornerRadius = when {
        compactWidth -> 20.dp
        else -> 24.dp
    }

    val iconBoxSize = when {
        compactWidth -> 42.dp
        else -> 48.dp
    }

    val iconSize = when {
        compactWidth -> 20.dp
        else -> 24.dp
    }

    val topBarHeight = when {
        compactHeight -> 56.dp
        else -> 64.dp
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

@Composable
fun OnboardingBackdrop(
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    // Create infinite transition for subtle floating animation
    val infiniteTransition = rememberInfiniteTransition(label = "backdrop")
    val waveOffset1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveOffset1"
    )
    val waveOffset2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveOffset2"
    )

    Canvas(modifier = modifier) {
        // Premium gradient background - Warm Luxe base
        val background = Brush.verticalGradient(
            colors = if (isDark) {
                listOf(
                    Color(0xFF0D0C0A), // Warm Jet Black
                    Color(0xFF14120F), // Dark Amber-Grey
                    Color(0xFF0F0E0D), // Warm Charcoal
                    Color(0xFF070706)  // Deepest Black
                )
            } else {
                listOf(
                    Color(0xFFF5EFE6), // Warm Alabaster/Cream
                    Color(0xFFEAE3D2), // Soft Beige
                    Color(0xFFF9F7F5), // Warm White
                    Color(0xFFEFECE9)  // Soft Warm Grey
                )
            }
        )

        drawRect(brush = background)

        // Floating center computation helpers
        val xOffset1 = kotlin.math.sin(waveOffset1)
        val yOffset1 = kotlin.math.cos(waveOffset1)
        val xOffset2 = kotlin.math.cos(waveOffset2)
        val yOffset2 = kotlin.math.sin(waveOffset2)

        // Primary glow - animated warm amber/bronze orb
        val primaryRadius = size.minDimension * (0.65f + xOffset1 * 0.08f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = if (isDark) {
                    listOf(
                        Color(0xFFD4A574).copy(alpha = 0.22f),  // Warm amber
                        Color(0xFFC4956A).copy(alpha = 0.11f),  // Deeper amber
                        Color(0xFFB8854D).copy(alpha = 0.04f),  // Edge fade
                        Color.Transparent
                    )
                } else {
                    listOf(
                        Color(0xFFE6C594).copy(alpha = 0.28f),  // Warm honey/amber
                        Color(0xFFD4A574).copy(alpha = 0.14f),  // Deeper amber
                        Color(0xFFC4956A).copy(alpha = 0.04f),  // Edge fade
                        Color.Transparent
                    )
                }
            ),
            radius = primaryRadius,
            center = Offset(
                x = size.width * (0.12f + xOffset1 * 0.05f),
                y = size.height * (0.15f + yOffset1 * 0.04f)
            )
        )

        // Secondary glow - animated peach/terracotta orb
        drawCircle(
            brush = Brush.radialGradient(
                colors = if (isDark) {
                    listOf(
                        Color(0xFFE8C4A0).copy(alpha = 0.16f),  // Peach
                        Color(0xFFD4A574).copy(alpha = 0.06f),  // Warm tone
                        Color.Transparent
                    )
                } else {
                    listOf(
                        Color(0xFFF3C29E).copy(alpha = 0.24f),  // Warm Peach
                        Color(0xFFE8C4A0).copy(alpha = 0.12f),  // Muted peach
                        Color.Transparent
                    )
                }
            ),
            radius = size.minDimension * (0.45f + yOffset2 * 0.06f),
            center = Offset(
                x = size.width * (0.78f + xOffset2 * 0.04f),
                y = size.height * (0.35f + yOffset2 * 0.05f)
            )
        )

        // Accent glow - subtle cream/white orb
        drawCircle(
            brush = Brush.radialGradient(
                colors = if (isDark) {
                    listOf(
                        Color(0xFFF5E6D3).copy(alpha = 0.12f),  // Cream
                        Color(0xFFEED5B8).copy(alpha = 0.04f),  // Warm cream
                        Color.Transparent
                    )
                } else {
                    listOf(
                        Color(0xFFFFF9F2).copy(alpha = 0.35f),  // Bright Warm White
                        Color(0xFFF5E6D3).copy(alpha = 0.18f),  // Cream
                        Color.Transparent
                    )
                }
            ),
            radius = size.minDimension * 0.5f,
            center = Offset(
                x = size.width * (0.85f + xOffset1 * 0.06f),
                y = size.height * (0.82f + yOffset2 * 0.04f)
            )
        )

        // Subtle accent glow for depth (Dusty Rose)
        drawCircle(
            brush = Brush.radialGradient(
                colors = if (isDark) {
                    listOf(
                        Color(0xFFE8C4C4).copy(alpha = 0.07f),  // Dusty rose
                        Color.Transparent
                    )
                } else {
                    listOf(
                        Color(0xFFF5D6D6).copy(alpha = 0.20f),  // Warm soft pink/rose
                        Color(0xFFE8C4C4).copy(alpha = 0.08f),  // Dusty rose
                        Color.Transparent
                    )
                }
            ),
            radius = size.minDimension * 0.35f,
            center = Offset(
                x = size.width * (0.25f + yOffset1 * 0.05f),
                y = size.height * (0.75f + xOffset2 * 0.04f)
            )
        )

        // Premium mesh pattern - subtle diagonal lines
        val stripeColor = if (isDark) {
            Color.White.copy(alpha = 0.02f)
        } else {
            Color.Black.copy(alpha = 0.015f)
        }

        val stripeGap = size.width / 12f
        var x = -size.height * 0.4f
        while (x < size.width + size.height * 0.4f) {
            drawLine(
                color = stripeColor,
                start = Offset(x = x, y = 0f),
                end = Offset(x = x + size.height * 0.4f, y = size.height),
                strokeWidth = 0.8f
            )
            x += stripeGap
        }

        // Noise texture overlay (subtle)
        if (isDark) {
            drawRect(
                color = Color.White.copy(alpha = 0.008f)
            )
        } else {
            drawRect(
                color = Color.Black.copy(alpha = 0.005f)
            )
        }
    }
}

@Composable
fun OnboardingStepIndicator(
    currentStep: Int,
    totalSteps: Int,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until totalSteps) {
            val isActive = i <= currentStep
            val isCurrent = i == currentStep
            val isCompleted = i < currentStep

            val dotColor by animateColorAsState(
                targetValue = when {
                    isCurrent -> BrandPrimary
                    isCompleted -> Color(0xFFD4A574).copy(alpha = 0.8f)  // Warm amber for completed
                    isActive -> BrandPrimary.copy(alpha = 0.4f)
                    else -> if (isDark) Color(0xFF27272A) else Color(0xFFE5E5E5)
                },
                animationSpec = tween(300),
                label = "dot_color_$i"
            )

            // Premium step indicator with glow effect for active/completed steps
            Box(
                modifier = Modifier
                    .height(if (isCurrent) 4.dp else 3.dp)
                    .width(if (isCurrent) 28.dp else if (isCompleted) 14.dp else 10.dp)
                    .graphicsLayer {
                        // Subtle scale animation for current step
                        if (isCurrent) {
                            scaleX = 1.1f
                            scaleY = 1.1f
                        }
                    }
                    .clip(RoundedCornerShape(50))
                    .then(
                        if (isCurrent && isDark) {
                            Modifier.background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        BrandPrimary.copy(alpha = 0.6f),
                                        BrandAccent.copy(alpha = 0.4f)
                                    )
                                )
                            )
                        } else {
                            Modifier.background(dotColor)
                        }
                    )
            )
        }
    }
}

@Composable
fun OnboardingBottomBar(
    isDark: Boolean,
    metrics: OnboardingLayoutMetrics,
    buttonEnabled: Boolean = true,
    buttonText: String = "Continue",
    showBorderGradient: Boolean = true,
    showLoading: Boolean = false,
    loadingText: String = "",
    onButtonClick: () -> Unit,
    extraContent: @Composable (() -> Unit)? = null
) {
    val systemNavBarPadding = LocalNavigationBarPadding.current
    val bottomInset = if (systemNavBarPadding != Dp.Unspecified) {
        systemNavBarPadding
    } else {
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = if (isDark) {
                        listOf(
                            Color.Transparent,
                            Color(0xB30D0C0A),
                            Color(0xFF0D0C0A)
                        )
                    } else {
                        listOf(
                            Color.Transparent,
                            Color(0xB3F5EFE6),
                            Color(0xFFF5EFE6)
                        )
                    }
                )
            )
            .padding(horizontal = metrics.horizontalPadding)
            .padding(
                top = 24.dp,
                bottom = bottomInset + 24.dp
            )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            extraContent?.invoke()

            if (extraContent != null) {
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Premium gradient button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = metrics.maxContentWidth)
                    .shadow(16.dp, RoundedCornerShape(16.dp))
                    .height(metrics.buttonHeight)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (showBorderGradient && buttonEnabled) {
                            Brush.horizontalGradient(
                                colors = listOf(BrandPrimary, BrandAccent)
                            )
                        } else if (buttonEnabled) {
                            Brush.horizontalGradient(
                                colors = listOf(BrandPrimary, BrandPrimary)
                            )
                        } else {
                            Brush.horizontalGradient(
                                colors = if (isDark) {
                                    listOf(Color(0xFF1F2937), Color(0xFF1F2937))
                                } else {
                                    listOf(Color(0xFFE5E7EB), Color(0xFFE5E7EB))
                                }
                            )
                        }
                    )
            ) {
                Button(
                    onClick = onButtonClick,
                    enabled = buttonEnabled,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color.White,
                        disabledContainerColor = Color.Transparent,
                        disabledContentColor = if (isDark) Color(0xFF6B7280) else Color(0xFF9CA3AF)
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        disabledElevation = 0.dp
                    )
                ) {
                    if (showLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.5.dp
                        )
                        if (loadingText.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = loadingText, style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        Text(
                            text = buttonText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

// === PREMIUM THEMING COLOR HELPERS ===
fun getOnboardingTextPrimary(isDark: Boolean): Color {
    return if (isDark) Color(0xFFFFFDF9) else Color(0xFF1C1A17)
}

fun getOnboardingTextSecondary(isDark: Boolean): Color {
    return if (isDark) Color(0xFFC7C1B6) else Color(0xFF5C5549)
}

fun getOnboardingCardBackground(isDark: Boolean): Color {
    return if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f)
}

fun getOnboardingCardBorder(isDark: Boolean): Color {
    return if (isDark) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.08f)
}

fun Modifier.bounceClick(
    onClick: () -> Unit = {}
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bounceScale"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}