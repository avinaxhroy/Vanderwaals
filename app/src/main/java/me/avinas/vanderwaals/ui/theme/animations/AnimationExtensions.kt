package me.avinas.vanderwaals.ui.theme.animations

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Vanderwaals Animation System
 * 
 * A collection of smooth, polished animations and transitions for:
 * - Micro-interactions
 * - Content transitions
 * - State changes
 * - Loading states
 * - Gesture feedback
 */

// ===== ANIMATION SPECS =====

/**
 * Standard spring animation spec
 */
val standardSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessLow
)

/**
 * Bouncy spring animation spec
 */
val bouncySpring = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMedium
)

/**
 * Stiff spring animation spec
 */
val stiffSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessHigh
)

/**
 * Smooth tween animation spec
 */
val smoothTween = tween<Float>(
    durationMillis = 300,
    easing = FastOutSlowInEasing
)

/**
 * Quick tween animation spec
 */
val quickTween = tween<Float>(
    durationMillis = 150,
    easing = FastOutSlowInEasing
)

/**
 * Slow tween animation spec
 */
val slowTween = tween<Float>(
    durationMillis = 500,
    easing = FastOutSlowInEasing
)

// ===== MODIFIER EXTENSIONS =====

/**
 * Scale down on press animation
 * Creates a subtle press feedback
 */
fun Modifier.pressAnimation(
    scaleDown: Float = 0.95f
): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = standardSpring,
        label = "pressScale"
    )
    
    this
        .scale(scale)
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = {}
        )
}

/**
 * Bounce animation on appear
 */
fun Modifier.bounceOnAppear(
    initialScale: Float = 0.8f
): Modifier = composed {
    var isVisible by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else initialScale,
        animationSpec = bouncySpring,
        label = "bounceScale"
    )
    
    LaunchedEffect(Unit) {
        isVisible = true
    }
    
    this.scale(scale)
}

/**
 * Fade in animation
 */
fun Modifier.fadeInAnimation(
    durationMillis: Int = 300,
    delayMillis: Int = 0
): Modifier = composed {
    var isVisible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(
            durationMillis = durationMillis,
            delayMillis = delayMillis,
            easing = FastOutSlowInEasing
        ),
        label = "fadeAlpha"
    )
    
    LaunchedEffect(Unit) {
        isVisible = true
    }
    
    this.graphicsLayer { this.alpha = alpha }
}

/**
 * Slide in from left animation
 */
fun Modifier.slideInFromLeft(
    durationMillis: Int = 300
): Modifier = composed {
    var isVisible by remember { mutableStateOf(false) }
    val offsetX by animateFloatAsState(
        targetValue = if (isVisible) 0f else -100f,
        animationSpec = tween(
            durationMillis = durationMillis,
            easing = FastOutSlowInEasing
        ),
        label = "slideOffset"
    )
    
    LaunchedEffect(Unit) {
        isVisible = true
    }
    
    this.graphicsLayer { translationX = offsetX }
}

/**
 * Slide in from right animation
 */
fun Modifier.slideInFromRight(
    durationMillis: Int = 300
): Modifier = composed {
    var isVisible by remember { mutableStateOf(false) }
    val offsetX by animateFloatAsState(
        targetValue = if (isVisible) 0f else 100f,
        animationSpec = tween(
            durationMillis = durationMillis,
            easing = FastOutSlowInEasing
        ),
        label = "slideOffset"
    )
    
    LaunchedEffect(Unit) {
        isVisible = true
    }
    
    this.graphicsLayer { translationX = offsetX }
}

/**
 * Shake animation for errors
 */
fun Modifier.shakeAnimation(
    trigger: Boolean
): Modifier = composed {
    val shake by animateFloatAsState(
        targetValue = if (trigger) 1f else 0f,
        animationSpec = tween(
            durationMillis = 100,
            easing = LinearEasing
        ),
        label = "shake"
    )
    
    this.graphicsLayer {
        translationX = if (shake > 0) {
            (kotlin.math.sin(shake * kotlin.math.PI * 4) * 10).toFloat()
        } else 0f
    }
}

/**
 * Pulse animation for attention
 */
fun Modifier.pulseAnimation(
    enabled: Boolean = true,
    minScale: Float = 0.95f,
    maxScale: Float = 1.05f,
    durationMillis: Int = 1000
): Modifier = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = minScale,
        targetValue = maxScale,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = durationMillis,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    
    if (enabled) this.scale(scale) else this
}

/**
 * Shimmer loading animation
 */
fun Modifier.shimmerAnimation(
    enabled: Boolean = true
): Modifier = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1500,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )
    
    if (enabled) {
        this.graphicsLayer {
            alpha = 0.3f + (shimmerOffset * 0.3f)
        }
    } else this
}

/**
 * Rotate animation
 */
fun Modifier.rotateAnimation(
    enabled: Boolean = true,
    durationMillis: Int = 1000
): Modifier = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "rotate")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = durationMillis,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    if (enabled) this.graphicsLayer { rotationZ = rotation } else this
}

/**
 * Hover scale animation
 */
fun Modifier.hoverScale(
    hovered: Boolean,
    scale: Float = 1.05f
): Modifier = composed {
    val animatedScale by animateFloatAsState(
        targetValue = if (hovered) scale else 1f,
        animationSpec = standardSpring,
        label = "hoverScale"
    )
    
    this.scale(animatedScale)
}

/**
 * Elevation animation
 */
fun Modifier.animatedElevation(
    elevated: Boolean,
    normalElevation: Dp = 4.dp,
    elevatedValue: Dp = 12.dp
): Modifier = composed {
    val elevation by animateDpAsState(
        targetValue = if (elevated) elevatedValue else normalElevation,
        animationSpec = tween(
            durationMillis = 200,
            easing = FastOutSlowInEasing
        ),
        label = "elevation"
    )
    
    this.graphicsLayer {
        shadowElevation = elevation.toPx()
    }
}

// ===== TRANSITION SPECS =====

/**
 * Enter transition - fade in and slide up
 */
fun fadeInSlideUp(
    durationMillis: Int = 300
): EnterTransition = fadeIn(
    animationSpec = tween(durationMillis)
) + slideInVertically(
    animationSpec = tween(durationMillis),
    initialOffsetY = { it / 2 }
)

/**
 * Exit transition - fade out and slide down
 */
fun fadeOutSlideDown(
    durationMillis: Int = 300
): ExitTransition = fadeOut(
    animationSpec = tween(durationMillis)
) + slideOutVertically(
    animationSpec = tween(durationMillis),
    targetOffsetY = { it / 2 }
)

/**
 * Enter transition - fade in and scale up
 */
fun fadeInScaleUp(
    durationMillis: Int = 300
): EnterTransition = fadeIn(
    animationSpec = tween(durationMillis)
) + scaleIn(
    animationSpec = tween(durationMillis),
    initialScale = 0.8f
)

/**
 * Exit transition - fade out and scale down
 */
fun fadeOutScaleDown(
    durationMillis: Int = 300
): ExitTransition = fadeOut(
    animationSpec = tween(durationMillis)
) + scaleOut(
    animationSpec = tween(durationMillis),
    targetScale = 0.8f
)

/**
 * Standard content transition
 */
val standardContentTransition = ContentTransform(
    targetContentEnter = fadeInSlideUp(),
    initialContentExit = fadeOutSlideDown()
)
