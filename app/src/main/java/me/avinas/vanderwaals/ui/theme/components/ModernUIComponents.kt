package me.avinas.vanderwaals.ui.theme.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.avinas.vanderwaals.ui.theme.*

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
    
    Box(
        modifier = modifier
            .shadow(
                elevation = if (enabled) 8.dp else 0.dp,
                shape = shape,
                ambientColor = VanderwaalsTan.copy(alpha = 0.3f),
                spotColor = VanderwaalsTan.copy(alpha = 0.3f)
            )
            .clip(shape)
            .background(if (enabled) gradient else Brush.horizontalGradient(
                colors = listOf(SurfaceElevated, SurfaceElevated)
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
                color = if (enabled) Color.White else TextDisabledDark,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ===== GLASS CARD =====

/**
 * Modern glassmorphism card with blur effect
 * 
 * Features:
 * - Translucent background
 * - Subtle border
 * - Smooth shadows
 * - Flexible content
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = PremiumCardShape,
    backgroundColor: Color = SurfaceGlass,
    borderColor: Color = BorderHighlight,
    borderWidth: Dp = 1.dp,
    elevation: Dp = 4.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.2f)
            )
            .border(
                width = borderWidth,
                color = borderColor,
                shape = shape
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

// ===== PREMIUM CARD =====

/**
 * Premium elevated card with modern styling
 */
@Composable
fun PremiumCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = PremiumCardShape,
    backgroundColor: Color = SurfaceElevated,
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
    backgroundColor: Color = SurfaceDark,
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
        selected -> Brush.horizontalGradient(listOf(SurfaceHighlight, SurfaceHighlight))
        else -> Brush.horizontalGradient(listOf(SurfaceElevated, SurfaceElevated))
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
                color = if (selected) TextPrimaryDark else TextSecondaryDark,
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
                color = TextPrimaryDark,
                fontWeight = FontWeight.Bold
            )
            subtitle?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondaryDark
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
    color: Color = DividerDark
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
