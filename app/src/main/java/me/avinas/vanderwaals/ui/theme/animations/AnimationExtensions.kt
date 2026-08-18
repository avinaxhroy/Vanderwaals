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

// animation specs

val standardSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMedium
)

val bouncySpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMedium
)

val stiffSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessHigh
)

val smoothTween = tween<Float>(
    durationMillis = 300,
    easing = FastOutSlowInEasing
)

val quickTween = tween<Float>(
    durationMillis = 150,
    easing = FastOutSlowInEasing
)

val slowTween = tween<Float>(
    durationMillis = 400,
    easing = FastOutSlowInEasing
)

// modifier extensions

fun Modifier.pressAnimation(
    scaleDown: Float = 0.98f
): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
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

fun Modifier.bounceOnAppear(
    initialScale: Float = 0.95f
): Modifier = composed {
    var isVisible by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else initialScale,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "appearScale"
    )
    
    LaunchedEffect(Unit) {
        isVisible = true
    }
    
    this.scale(scale)
}

fun Modifier.fadeInAnimation(
    durationMillis: Int = 250,
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

fun Modifier.slideInFromLeft(
    durationMillis: Int = 250
): Modifier = composed {
    var isVisible by remember { mutableStateOf(false) }
    val offsetX by animateFloatAsState(
        targetValue = if (isVisible) 0f else -50f,
        animationSpec = tween(
            durationMillis = durationMillis,
            easing = FastOutSlowInEasing
        ),
        label = "slideLeft"
    )
    
    LaunchedEffect(Unit) {
        isVisible = true
    }
    
    this.graphicsLayer { translationX = offsetX }
}

fun Modifier.slideInFromRight(
    durationMillis: Int = 250
): Modifier = composed {
    var isVisible by remember { mutableStateOf(false) }
    val offsetX by animateFloatAsState(
        targetValue = if (isVisible) 0f else 50f,
        animationSpec = tween(
            durationMillis = durationMillis,
            easing = FastOutSlowInEasing
        ),
        label = "slideRight"
    )
    
    LaunchedEffect(Unit) {
        isVisible = true
    }
    
    this.graphicsLayer { translationX = offsetX }
}

fun Modifier.slideInFromBottom(
    durationMillis: Int = 250
): Modifier = composed {
    var isVisible by remember { mutableStateOf(false) }
    val offsetY by animateFloatAsState(
        targetValue = if (isVisible) 0f else 50f,
        animationSpec = tween(
            durationMillis = durationMillis,
            easing = FastOutSlowInEasing
        ),
        label = "slideBottom"
    )
    
    LaunchedEffect(Unit) {
        isVisible = true
    }
    
    this.graphicsLayer { translationY = offsetY }
}

// shake for errors/warnings
fun Modifier.shakeAnimation(
    shakeTrigger: Boolean = false
): Modifier = composed {
    var isShaking by remember { mutableStateOf(false) }
    
    LaunchedEffect(shakeTrigger) {
        if (shakeTrigger) {
            isShaking = true
        }
    }
    
    val shake by animateFloatAsState(
        targetValue = if (isShaking) 1f else 0f,
        animationSpec = tween(
            durationMillis = 100,
            easing = LinearEasing
        ),
        label = "shake",
        finishedListener = { isShaking = false }
    )
    
    this.graphicsLayer {
        translationX = if (shake > 0) {
            (kotlin.math.sin(shake * kotlin.math.PI * 4) * 10).toFloat()
        } else 0f
    }
}

// pulse to draw attention
fun Modifier.pulseAnimation(
    enabled: Boolean = true,
    minScale: Float = 0.98f,
    maxScale: Float = 1.02f,
    durationMillis: Int = 1200
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

fun Modifier.hoverScale(
    hovered: Boolean,
    scale: Float = 1.02f
): Modifier = composed {
    val animatedScale by animateFloatAsState(
        targetValue = if (hovered) scale else 1f,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "hoverScale"
    )
    
    this.scale(animatedScale)
}

fun Modifier.animatedElevation(
    elevated: Boolean,
    normalElevation: Dp = 2.dp,
    elevatedValue: Dp = 6.dp
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

// transition specs

fun fadeInSlideUp(
    durationMillis: Int = 250
): EnterTransition = fadeIn(
    animationSpec = tween(durationMillis, easing = FastOutSlowInEasing)
) + slideInVertically(
    animationSpec = tween(durationMillis, easing = FastOutSlowInEasing),
    initialOffsetY = { it / 4 }
)

fun fadeOutSlideDown(
    durationMillis: Int = 200
): ExitTransition = fadeOut(
    animationSpec = tween(durationMillis, easing = FastOutSlowInEasing)
) + slideOutVertically(
    animationSpec = tween(durationMillis, easing = FastOutSlowInEasing),
    targetOffsetY = { it / 4 }
)

fun fadeInScaleUp(
    durationMillis: Int = 250
): EnterTransition = fadeIn(
    animationSpec = tween(durationMillis, easing = FastOutSlowInEasing)
) + scaleIn(
    animationSpec = tween(durationMillis, easing = FastOutSlowInEasing),
    initialScale = 0.95f
)

fun fadeOutScaleDown(
    durationMillis: Int = 200
): ExitTransition = fadeOut(
    animationSpec = tween(durationMillis, easing = FastOutSlowInEasing)
) + scaleOut(
    animationSpec = tween(durationMillis, easing = FastOutSlowInEasing),
    targetScale = 0.95f
)

val standardContentTransition = ContentTransform(
    targetContentEnter = fadeInSlideUp(),
    initialContentExit = fadeOutSlideDown()
)