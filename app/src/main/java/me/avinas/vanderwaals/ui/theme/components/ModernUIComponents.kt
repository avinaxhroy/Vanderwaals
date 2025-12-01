package me.avinas.vanderwaals.ui.theme.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.dp
import me.avinas.vanderwaals.ui.theme.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.ui.draw.scale
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode

/**
 * Modern UI Components for Vanderwaals
 * 
 * A collection of premium, reusable UI components featuring:
 * - Gradient effects
 * - Glassmorphism
 * - Smooth animations
 * - Consistent styling
 */

// ===== GRADIENT BUTTON =====

/**
 * Modern gradient button with smooth animations
 * 
 * Features:
 * - Gradient background
 * - Shadow and glow effects
 * - Smooth press animation
 * - Configurable appearance
 */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    gradient: Brush = GradientAccent,
    shape: Shape = PillShape,
    contentPadding: PaddingValues = PaddingValues(horizontal = 32.dp, vertical = 16.dp),
    textStyle: TextStyle = MaterialTheme.typography.labelLarge,
    icon: (@Composable () -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    // Scale animation on press
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "button_scale"
    )
    
    Box(
        modifier = modifier
            .scale(scale)
            .shadow(
                elevation = if (enabled) if (isPressed) 4.dp else 8.dp else 0.dp,
                shape = shape,
                ambientColor = VanderwaalsTan.copy(alpha = 0.3f),
                spotColor = VanderwaalsTan.copy(alpha = 0.3f)
            )
            .clip(shape)
            .background(if (enabled) gradient else Brush.horizontalGradient(
                colors = listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant)
            ))
            .clickable(
                onClick = onClick,
                enabled = enabled,
                interactionSource = interactionSource,
                indication = ripple(color = Color.White.copy(alpha = 0.3f))
            )
            .padding(contentPadding)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            icon?.let {
                it()
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = textStyle,
                color = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ===== GLASS CARD =====

// ===== GLASS CARD =====

/**
 * Modern glassmorphism card with premium Apple-style aesthetic
 *
 * Features:
 * - High-quality translucent background
 * - Subtle white border with gradient
 * - Soft shadow
 * - Noise texture simulation (via gradient)
 */
/**
 * Modern glassmorphism card with premium Apple-style aesthetic
 *
 * Features:
 * - High-quality translucent background
 * - Subtle white border with gradient
 * - Soft shadow
 * - Noise texture simulation (via gradient)
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    elevation: Dp = 0.dp, // Unused, kept for API compatibility
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = me.avinas.vanderwaals.ui.theme.LocalThemeIsDark.current
    val containerColor = if (isDark) GlassBackground else GlassBackgroundLight
    val borderColor = if (isDark) GlassBorder else GlassBorderLight
    
    Box(
        modifier = modifier
            .shadow(
                elevation = 16.dp,
                shape = shape,
                ambientColor = if (isDark) Color.Black.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.05f),
                spotColor = if (isDark) Color.Black.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.05f)
            )
            .clip(shape)
            .background(containerColor)
            .border(
                width = 1.dp,
                brush = if (isDark) GlassGradientTop else GlassGradientTopLight,
                shape = shape
            )
    ) {
        // CSS: inset 0 -1px 0 rgba(255, 255, 255, 0.1) -> Bottom Inner Highlight
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .align(Alignment.BottomCenter)
                .offset(y = (-1).dp) // Inset by 1px
                .background(if (isDark) GlassInsetBottom else Color.White.copy(alpha = 0.2f))
        )
        
        // CSS: inset 0 0 34px 17px rgba(255, 255, 255, 1.7) -> Strong Inner Glow
        // Simulated with a radial gradient
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            if (isDark) GlassInnerGlow else Color.White.copy(alpha = 0.4f),
                            Color.Transparent
                        ),
                        center = Offset.Zero,
                        radius = 1000f // Large radius for soft glow
                    )
                )
        )
        
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

/**
 * Glassmorphism card with a subtle color tint
 */
@Composable
fun TintedGlassCard(
    modifier: Modifier = Modifier,
    tintColor: Color,
    shape: Shape = RoundedCornerShape(24.dp),
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = me.avinas.vanderwaals.ui.theme.LocalThemeIsDark.current
    val containerColor = if (isDark) GlassBackground else GlassBackgroundLight
    
    Box(
        modifier = modifier
            .shadow(
                elevation = 16.dp,
                shape = shape,
                ambientColor = tintColor.copy(alpha = if (isDark) 0.1f else 0.05f),
                spotColor = tintColor.copy(alpha = if (isDark) 0.2f else 0.1f)
            )
            .clip(shape)
            .background(containerColor)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        tintColor.copy(alpha = 0.3f),
                        tintColor.copy(alpha = 0.05f)
                    )
                ),
                shape = shape
            )
    ) {
        // Tint Overlay
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            tintColor.copy(alpha = 0.15f),
                            tintColor.copy(alpha = 0.05f)
                        ),
                        start = Offset.Zero,
                        end = Offset.Infinite
                    )
                )
        )

        // CSS: inset 0 -1px 0 rgba(255, 255, 255, 0.1) -> Bottom Inner Highlight
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .align(Alignment.BottomCenter)
                .offset(y = (-1).dp) // Inset by 1px
                .background(if (isDark) GlassInsetBottom else Color.White.copy(alpha = 0.2f))
        )
        
        // CSS: inset 0 0 34px 17px rgba(255, 255, 255, 1.7) -> Strong Inner Glow
        // Simulated with a radial gradient
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            if (isDark) GlassInnerGlow else Color.White.copy(alpha = 0.4f),
                            Color.Transparent
                        ),
                        center = Offset.Zero,
                        radius = 1000f // Large radius for soft glow
                    )
                )
        )
        
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

/**
 * Glassmorphism Sheet for Bottom Sheets / Overlays
 */
/**
 * Glassmorphism Sheet for Bottom Sheets / Overlays
 */
@Composable
fun GlassSheet(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = me.avinas.vanderwaals.ui.theme.LocalThemeIsDark.current
    val containerColor = if (isDark) GlassBackground else GlassBackgroundLight
    val borderColor = if (isDark) GlassBorder else GlassBorderLight
    val topGradient = if (isDark) GlassGradientTop else GlassGradientTopLight
    val shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    
    Box(
        modifier = modifier
            .shadow(
                elevation = 32.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.15f),
                spotColor = Color.Black.copy(alpha = 0.15f)
            )
            .clip(shape)
            .background(containerColor)
            .border(
                width = 1.dp,
                brush = topGradient,
                shape = shape
            )
    ) {
        // Top Gradient Border (simulated with Box)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(topGradient)
                .align(Alignment.TopCenter)
        )

        // CSS: inset 0 1px 0 rgba(255, 255, 255, 0.5) -> Top Inner Highlight
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .align(Alignment.TopCenter)
                .offset(y = 1.dp) // Inset by 1px
                .background(if (isDark) GlassInsetTop else Color.White.copy(alpha = 0.5f))
        )

        // Inner Glow Simulation (Stronger)
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            if (isDark) GlassInnerGlow else Color.White.copy(alpha = 0.4f),
                            Color.Transparent
                        ),
                        endY = 500f
                    )
                )
        )
        
        Column(
            modifier = Modifier.padding(24.dp),
            content = content
        )
    }
}

// ===== PREMIUM CARD =====

/**
 * Premium elevated card with modern styling
 */
@Composable
fun PremiumCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = PremiumCardShape,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    elevation: Dp = 8.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    
    Card(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = VanderwaalsTan.copy(alpha = 0.1f)
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        onClick = onClick,
                        interactionSource = interactionSource,
                        indication = ripple(color = VanderwaalsTan)
                    )
                } else Modifier
            ),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = elevation
        )
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

// ===== OUTLINED CARD =====

/**
 * Modern outlined card with glow effect on hover
 */
@Composable
fun OutlinedGlowCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = PremiumCardShape,
    borderColor: Color = BorderGlow,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    glowOnHover: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    
    Card(
        modifier = modifier
            .border(
                width = 2.dp,
                color = borderColor,
                shape = shape
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        onClick = onClick,
                        interactionSource = interactionSource,
                        indication = ripple(color = VanderwaalsTan)
                    )
                } else Modifier
            ),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        )
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

// ===== CHIP COMPONENT =====

/**
 * Modern chip with gradient option
 */
@Composable
fun ModernChip(
    text: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    gradient: Boolean = false,
    icon: (@Composable () -> Unit)? = null
) {
    val backgroundColor = when {
        gradient && selected -> GradientPrimary
        gradient && selected -> GradientPrimary
        selected -> Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.surfaceContainerHighest))
        else -> Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant))
    }
    
    val interactionSource = remember { MutableInteractionSource() }
    
    Box(
        modifier = modifier
            .clip(PillShape)
            .background(backgroundColor)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        onClick = onClick,
                        interactionSource = interactionSource,
                        indication = ripple(color = VanderwaalsTan)
                    )
                } else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.wrapContentWidth()
        ) {
            icon?.let {
                it()
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

// ===== GRADIENT TEXT =====

/**
 * Text with gradient color effect
 */
@Composable
fun GradientText(
    text: String,
    modifier: Modifier = Modifier,
    gradient: Brush = GradientAccent,
    style: TextStyle = MaterialTheme.typography.headlineMedium
) {
    Text(
        text = text,
        modifier = modifier,
        style = style.copy(
            brush = gradient
        )
    )
}

// ===== SECTION HEADER =====

/**
 * Modern section header with optional action
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            subtitle?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        action?.let {
            Spacer(modifier = Modifier.width(16.dp))
            it()
        }
    }
}

// ===== DIVIDER WITH GRADIENT =====

/**
 * Modern divider with optional gradient
 */
@Composable
fun ModernDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = 1.dp,
    gradient: Boolean = false,
    color: Color = MaterialTheme.colorScheme.outlineVariant
) {
    if (gradient) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(thickness)
                .background(GradientPrimary)
        )
    } else {
        HorizontalDivider(
            modifier = modifier,
            thickness = thickness,
            color = color
        )
    }
}

// ===== PREMIUM BACKGROUND =====

/**
 * Premium animated background for secondary screens
 */
@Composable
fun PremiumBackground(
    modifier: Modifier = Modifier,
    isDark: Boolean = LocalThemeIsDark.current
) {
    val infiniteTransition = rememberInfiniteTransition(label = "blobs")

    // Animate positions
    val offset1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "offset1"
    )
    val offset2 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(18000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "offset2"
    )
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isDark) DarkBackground else LightBackground)
    ) {
        Canvas(modifier = Modifier.fillMaxSize().blur(100.dp)) {
            val w = size.width
            val h = size.height

            if (isDark) {
                // Dark Mode Blobs - Deep Ocean Theme
                drawCircle(
                    color = Color(0xFF1E3A8A).copy(alpha = 0.2f), // Deep Royal Blue
                    center = Offset(w * 0.2f + (offset1 * 100f), h * 0.3f),
                    radius = 500.dp.toPx()
                )
                drawCircle(
                    color = Color(0xFF0F766E).copy(alpha = 0.15f), // Deep Teal
                    center = Offset(w * 0.8f - (offset2 * 100f), h * 0.6f),
                    radius = 450.dp.toPx()
                )
                drawCircle(
                    color = Color(0xFF0E7490).copy(alpha = 0.15f), // Cyan/Slate
                    center = Offset(w * 0.5f, h * 0.9f - (offset1 * 50f)),
                    radius = 500.dp.toPx()
                )
            } else {
                // Light Mode Blobs - Softer, pastel but vibrant
                drawCircle(
                    color = Color(0xFFE9D5FF).copy(alpha = 0.8f), // Purple 200
                    center = Offset(w * 0.2f + (offset1 * 100f), h * 0.3f),
                    radius = 600.dp.toPx()
                )
                drawCircle(
                    color = Color(0xFFFECDD3).copy(alpha = 0.7f), // Rose 200
                    center = Offset(w * 0.8f - (offset2 * 100f), h * 0.6f),
                    radius = 500.dp.toPx()
                )
                drawCircle(
                    color = Color(0xFF99F6E4).copy(alpha = 0.6f), // Teal 200
                    center = Offset(w * 0.5f, h * 0.9f - (offset1 * 50f)),
                    radius = 550.dp.toPx()
                )
            }
        }
        
        // Noise overlay for texture (optional, can simulate with a very subtle gradient)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            if (isDark) Color.Black.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.2f)
                        )
                    )
                )
        )
    }
}
// ===== BACKGROUND BLOBS =====

/**
 * Animated background blobs for visual interest (Used in MainScreen)
 */
@Composable
fun BackgroundBlobs(
    modifier: Modifier = Modifier,
    isDark: Boolean = LocalThemeIsDark.current
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        if (isDark) {
            // Dark Mode Blobs
            drawCircle(
                color = DarkIndigo400.copy(alpha = 0.2f),
                center = Offset(w * 0.2f, h * 0.5f),
                radius = 400.dp.toPx()
            )
            drawCircle(
                color = DarkRose400.copy(alpha = 0.15f),
                center = Offset(w * 0.8f, h * 0.2f),
                radius = 400.dp.toPx()
            )
            drawCircle(
                color = DarkSky400.copy(alpha = 0.15f),
                center = Offset(w * 0.5f, h * 0.8f),
                radius = 320.dp.toPx()
            )
        } else {
            // Light Mode Blobs
            drawCircle(
                color = LightPurple400.copy(alpha = 0.3f),
                center = Offset(w * 0.2f, h * 0.5f),
                radius = 500.dp.toPx()
            )
            drawCircle(
                color = LightOrange400.copy(alpha = 0.25f),
                center = Offset(w * 0.8f, h * 0.2f),
                radius = 500.dp.toPx()
            )
            drawCircle(
                color = LightTeal400.copy(alpha = 0.25f),
                center = Offset(w * 0.5f, h * 0.8f),
                radius = 400.dp.toPx()
            )
        }
    }
}

// ===== GLASS TOP APP BAR BACKGROUND =====

/**
 * Glassmorphic background for TopAppBar
 * 
 * Features:
 * - Translucent background with blur effect
 * - Subtle bottom border
 * - Inner glow and highlights
 * - Matches GlassCard aesthetic
 */
@Composable
fun GlassTopAppBarBackground(
    modifier: Modifier = Modifier
) {
    val isDark = me.avinas.vanderwaals.ui.theme.LocalThemeIsDark.current
    val containerColor = if (isDark) GlassBackground else GlassBackgroundLight
    val borderColor = if (isDark) GlassBorder else GlassBorderLight
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                spotColor = if (isDark) Color.Black.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f),
                ambientColor = if (isDark) Color.Black.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f)
            )
            .background(containerColor)
    ) {
        // Bottom Border
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .align(Alignment.BottomCenter)
                .background(borderColor)
        )

        // CSS: inset 0 -1px 0 rgba(255, 255, 255, 0.1) -> Bottom Inner Highlight
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .align(Alignment.BottomCenter)
                .offset(y = (-1).dp) // Inset by 1px
                .background(if (isDark) GlassInsetBottom else Color.White.copy(alpha = 0.2f))
        )
        
        // Inner Glow Simulation
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            if (isDark) GlassInnerGlow else Color.White.copy(alpha = 0.4f),
                            Color.Transparent
                        ),
                        endY = 200f
                    )
                )
        )
    }
}

/**
 * Helper to observe pressed state from InteractionSource
 */
@Composable
fun InteractionSource.collectIsPressedAsState(): State<Boolean> {
    val isPressed = remember { mutableStateOf(false) }
    LaunchedEffect(this) {
        interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> isPressed.value = true
                is PressInteraction.Release -> isPressed.value = false
                is PressInteraction.Cancel -> isPressed.value = false
            }
        }
    }
    return isPressed
}
