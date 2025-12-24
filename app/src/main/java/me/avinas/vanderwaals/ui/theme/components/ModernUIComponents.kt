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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

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
    val isDark = LocalThemeIsDark.current
    val containerColor = if (isDark) GlassBackground else GlassBackgroundLight
    
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
        // Inner Highlight (Crisp 1px border inset)
        // We simulate this with a box drawing a border inside
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(1.dp) // Inset by 1px
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            if (isDark) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.6f), // Top brighter
                            if (isDark) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.2f)  // Bottom subtle
                        )
                    ),
                    shape = shape
                )
        )
        
        // Bottom Inner Highlight (stronger)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .align(Alignment.BottomCenter)
                .offset(y = (-1).dp)
                .padding(horizontal = 24.dp) // Don't span full width for "button" feel
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                             Color.Transparent,
                             if (isDark) GlassInsetBottom else Color.White.copy(alpha = 0.3f),
                             Color.Transparent
                        )
                    )
                )
        )
        
        // Soft Inner Glow (Radial)
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
                        radius = 800f
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
    val isDark = LocalThemeIsDark.current
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

        // Inner Highlight (Crisp 1px border inset) - Matching GlassCard with Top Light Source
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(1.dp) // Inset by 1px
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            if (isDark) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.6f), // Top brighter
                            if (isDark) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.2f)  // Bottom subtle
                        )
                    ),
                    shape = shape
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
 * Gradient Glassmorphism card for a premium fusion look
 */
@Composable
fun GradientGlassCard(
    modifier: Modifier = Modifier,
    brush: Brush,
    shape: Shape = RoundedCornerShape(24.dp),
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = LocalThemeIsDark.current
    val containerColor = if (isDark) GlassBackground else GlassBackgroundLight
    
    Box(
        modifier = modifier
            .shadow(
                elevation = 16.dp,
                shape = shape,
                ambientColor = if (isDark) Color.Black.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f),
                spotColor = if (isDark) Color.Black.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.1f)
            )
            .clip(shape)
            .background(containerColor)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        if (isDark) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.4f),
                        if (isDark) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.1f)
                    )
                ),
                shape = shape
            )
    ) {
        // Gradient Tint Overlay (Fusion Effect)
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(brush)
        )

        // Inner Highlight (Crisp 1px border inset)
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(1.dp)
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            if (isDark) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.6f),
                            if (isDark) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.2f)
                        )
                    ),
                    shape = shape
                )
        )

        // Bottom Inner Highlight
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .align(Alignment.BottomCenter)
                .offset(y = (-1).dp)
                .background(if (isDark) GlassInsetBottom else Color.White.copy(alpha = 0.2f))
        )
        
        // Soft Inner Glow
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
                        radius = 1000f
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
@Composable
fun GlassSheet(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = LocalThemeIsDark.current
    val containerColor = if (isDark) GlassBackground else GlassBackgroundLight
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
            // Initial Border (Outer)
            .border(
                width = 1.dp,
                brush = topGradient,
                shape = shape
            )
    ) {
        // Inner Highlight (Crisp 1px border inset) - Top only
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .align(Alignment.TopCenter)
                .offset(y = 1.dp) // Inset
                .background(
                   brush = Brush.horizontalGradient(
                       colors = listOf(
                           Color.Transparent, 
                           if (isDark) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.5f),
                           Color.Transparent
                       )
                   )
                )
        )

        // Inner Glow Simulation (Stronger for Sheet)
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            if (isDark) GlassInnerGlow else Color.White.copy(alpha = 0.5f),
                            Color.Transparent
                        ),
                        endY = 600f
                    )
                )
        )
        
        Column(
            modifier = Modifier
                .padding(24.dp)
                .windowInsetsPadding(WindowInsets.navigationBars) // Respect nav bar
                .padding(bottom = 24.dp), // Extra safety buffer
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

    // Very slow, "breathing" animations for controlled chaos
    val t1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(35000, easing = LinearEasing), RepeatMode.Reverse), label = "t1"
    )
    val t2 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(28000, easing = LinearEasing), RepeatMode.Reverse), label = "t2"
    )
    val t3 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(42000, easing = LinearEasing), RepeatMode.Reverse), label = "t3"
    )
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isDark) DarkBackground else LightBackground)
    ) {
        Canvas(modifier = Modifier.fillMaxSize().blur(80.dp)) {
            val w = size.width
            val h = size.height
            
            // Helper to draw varied organic blobs
            fun drawOrganicBlob(center: Offset, radius: Float, color: Color, distortion: Float) {
                drawCircle(
                    color = color.copy(alpha = if (isDark) 0.4f else 0.6f),
                    center = center,
                    radius = radius * (0.8f + distortion * 0.4f)
                )
            }

            if (isDark) {
                // Layer 0: Shadow Pocket (Bottom Right) - Adds contrast
                drawOrganicBlob(
                    center = Offset(w * 0.9f, h * 0.9f),
                    radius = w * 0.4f,
                    color = Color.Black.copy(alpha = 0.3f),
                    distortion = t2
                )

                // Layer 1: Deep Base (Cinematic Blue) - Top Left (Source side)
                drawOrganicBlob(
                    center = Offset(w * 0.2f, h * 0.3f),
                    radius = w * 0.6f,
                    color = CinematicBlue,
                    distortion = t1
                )
                
                // Layer 2: Rich Accent (Cinematic Purple) - Moving opposite
                drawOrganicBlob(
                    center = Offset(w * 0.8f, h * 0.6f),
                    radius = w * 0.5f,
                    color = CinematicPurple,
                    distortion = 1f - t2
                )
                
                // Layer 3: Pop Color (Cinematic Teal) - Floating top right
                drawOrganicBlob(
                    center = Offset(w * (0.6f + t1 * 0.2f), h * 0.2f),
                    radius = w * 0.4f,
                    color = CinematicTeal,
                    distortion = t3
                )
                
                // Layer 4: Deep Rose Muted (Desaturated) - Bottom Left
                // Less attention stealing, more grounding
                drawOrganicBlob(
                    center = Offset(w * 0.2f, h * (0.85f - t2 * 0.1f)),
                    radius = w * 0.45f,
                    color = CinematicRoseMuted, 
                    distortion = t2
                )

                // Layer 5: Hero Light Glint - Top Left
                // Simulates light source hitting the background liquid
                drawOrganicBlob(
                    center = Offset(0f, 0f),
                    radius = w * 0.4f,
                    color = Color.White.copy(alpha = 0.03f),
                    distortion = t1
                )
                
            } else {
                // Light Mode - Airy and separated with Hero Light
                
                // Layer 0: Hero Light Source - Top Left (Stronger)
                drawOrganicBlob(
                    center = Offset(0f, 0f),
                    radius = w * 0.6f,
                    color = Color.White.copy(alpha = 0.6f), // Bright wash
                    distortion = 0f
                )

                // Layer 1: Airy Blue - Floating mid
                drawOrganicBlob(
                    center = Offset(w * 0.3f, h * 0.4f),
                    radius = w * 0.5f,
                    color = AiryBlue,
                    distortion = t1
                )
                
                // Layer 2: Airy Purple - Bottom Right
                drawOrganicBlob(
                    center = Offset(w * 0.8f, h * 0.7f),
                    radius = w * 0.5f,
                    color = AiryPurple,
                    distortion = 1f - t2
                )
                
                // Layer 3: Airy Amber (Warmth) - Top Rightish
                drawOrganicBlob(
                    center = Offset(w * 0.7f, h * 0.2f),
                    radius = w * 0.4f,
                    color = AiryAmber,
                    distortion = t3
                )
                
                // Layer 4: Airy Rose - Bottom Left
                drawOrganicBlob(
                    center = Offset(w * 0.2f, h * 0.9f),
                    radius = w * 0.4f,
                    color = AiryRose,
                    distortion = t2
                )
            }
        }
        
        // Noise Overlay for texture - reduces banding and adds "material" feel
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.02f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.02f)
                    )
                )
            )
        }
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
    // Re-use logic for MainScreen blobs but without the full PremiumBackground container
    // We just draw the blobs here
    val infiniteTransition = rememberInfiniteTransition(label = "blobs_main")

    val t1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(35000, easing = LinearEasing), RepeatMode.Reverse), label = "t1"
    )
    val t2 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(28000, easing = LinearEasing), RepeatMode.Reverse), label = "t2"
    )
    
    Canvas(modifier = modifier.fillMaxSize().blur(80.dp)) {
        val w = size.width
        val h = size.height
        
        fun drawOrganicBlob(center: Offset, radius: Float, color: Color) {
            drawCircle(
                color = color.copy(alpha = if (isDark) 0.3f else 0.5f),
                center = center,
                radius = radius
            )
        }

        if (isDark) {
            drawOrganicBlob(Offset(w * 0.2f, h * 0.3f), w * 0.5f, CinematicBlue)
            drawOrganicBlob(Offset(w * 0.8f, h * 0.7f), w * 0.45f, CinematicPurple)
            drawOrganicBlob(Offset(w * (0.5f + t1 * 0.2f), h * 0.5f), w * 0.4f, CinematicTeal)
        } else {
            drawOrganicBlob(Offset(w * 0.2f, h * 0.3f), w * 0.6f, AiryBlue)
            drawOrganicBlob(Offset(w * 0.8f, h * 0.7f), w * 0.5f, AiryRose)
            drawOrganicBlob(Offset(w * 0.5f, h * 0.8f), w * 0.55f, AiryTeal)
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
    val isDark = LocalThemeIsDark.current
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
        // Inner Highlight (Crisp 1px border inset) - Bottom only for top bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .align(Alignment.BottomCenter)
                .offset(y = (-1).dp) // Inset by 1px from bottom
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            if (isDark) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.8f), // Enhanced opacity
                            Color.Transparent
                        )
                    )
                )
        )
        
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

// ===== NEW SHARED COMPONENTS =====

@Composable
fun LabelSectionHeader(title: String) {
    val isDark = LocalThemeIsDark.current
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        // Reduced opacity for "label" feel
        color = if (isDark) Color.White.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.4f),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        letterSpacing = 2.0.sp // Increased spacing
    )
}

@Composable
fun SettingsRow(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    textColor: Color = Color.Unspecified,
    trailing: @Composable (() -> Unit)? = null
) {
    val isDark = LocalThemeIsDark.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) Color.White.copy(alpha = 0.6f) else Color(0xFF6B7280)
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun SegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    isDark: Boolean = LocalThemeIsDark.current
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isDark) Color.Black.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.05f),
                RoundedCornerShape(8.dp)
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEachIndexed { index, item ->
            val isSelected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (isSelected) {
                            if (isDark) InfoColorDark else LightPrimary
                        } else {
                            Color.Transparent
                        }
                    )
                    .clickable { onItemSelected(index) }
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) Color.White else (if (isDark) Color.White.copy(alpha = 0.6f) else Color(0xFF4B5563)),
                    maxLines = 2,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingTopAppBar(
    onBack: () -> Unit,
    showBack: Boolean = true,
    title: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = title,
        actions = actions,
        navigationIcon = {
            if (showBack) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            }
        },
        windowInsets = WindowInsets(0, 0, 0, 0),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent
        ),
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 16.dp) // Explicit extra padding for safety
    )
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

@Composable
fun ModernDivider(
    modifier: Modifier = Modifier,
    isDark: Boolean = LocalThemeIsDark.current
) {
    HorizontalDivider(
        modifier = modifier,
        color = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f)
    )
}
