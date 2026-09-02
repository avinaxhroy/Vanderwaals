package me.avinas.vanderwaals.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import me.avinas.vanderwaals.ui.settings.RadicalIconBadge
import me.avinas.vanderwaals.ui.settings.RadicalPalette
import me.avinas.vanderwaals.ui.settings.RadicalTactileBackdrop
import me.avinas.vanderwaals.ui.settings.RadicalTactileCard
import me.avinas.vanderwaals.ui.theme.LocalThemeIsDark
import me.avinas.vanderwaals.ui.theme.PlayfairDisplayFamily

@Composable
fun ConfirmationGalleryScreen(
    onContinue: () -> Unit,
    onBack: () -> Unit,
    viewModel: ConfirmationGalleryViewModel = hiltViewModel(),
    currentStep: Int = 3,
    totalSteps: Int = 6
) {
    val displayedWallpapers by viewModel.displayedWallpapers.collectAsState()
    val likedWallpapers by viewModel.likedWallpapers.collectAsState()
    val dislikedWallpapers by viewModel.dislikedWallpapers.collectAsState()
    val canContinue by viewModel.canContinue.collectAsState()
    val finishState by viewModel.finishState.collectAsState()

    val isDark = LocalThemeIsDark.current
    val metrics = rememberOnboardingLayoutMetrics()
    val scrollState = rememberLazyGridState()
    val likedCount = likedWallpapers.size
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(finishState) {
        if (finishState is FinishState.Success) onContinue()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent,
        topBar = {
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
                                    Brush.verticalGradient(listOf(Color(0xFF1E2433), Color(0xFF111622)))
                                } else {
                                    Brush.verticalGradient(listOf(Color.White, Color(0xFFF1F5F9)))
                                }
                            )
                            .border(
                                1.dp,
                                Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = if (isDark) 0.25f else 0.9f),
                                        if (isDark) Color.Black.copy(alpha = 0.5f) else Color(0xFFCBD5E1)
                                    )
                                ),
                                CircleShape
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
                                    Brush.verticalGradient(listOf(Color(0xFF1E2433), Color(0xFF111622)))
                                } else {
                                    Brush.verticalGradient(listOf(Color.White, Color(0xFFF1F5F9)))
                                }
                            )
                            .border(
                                1.dp,
                                Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = if (isDark) 0.25f else 0.9f),
                                        if (isDark) Color.Black.copy(alpha = 0.5f) else Color(0xFFCBD5E1)
                                    )
                                ),
                                CircleShape
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = true),
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.refreshWallpapers()
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Shuffle",
                            tint = RadicalPalette.EmeraldJade,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        },
        bottomBar = {
            OnboardingBottomBar(
                isDark = isDark,
                metrics = metrics,
                buttonEnabled = canContinue && finishState !is FinishState.Initializing,
                buttonText = if (likedCount >= 4) "Complete Taste Setup" else "Like ${4 - likedCount} more to continue",
                showLoading = finishState is FinishState.Initializing,
                loadingText = "Saving taste profile…",
                accentColor = RadicalPalette.EmeraldJade,
                onButtonClick = { viewModel.finishOnboarding() }
            )
        }
    ) { paddingValues ->
        Box(Modifier.fillMaxSize()) {
            RadicalTactileBackdrop(isDark = isDark, modifier = Modifier.matchParentSize())

            if (displayedWallpapers.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = metrics.horizontalPadding)
                        .padding(top = paddingValues.calculateTopPadding()),
                    contentAlignment = Alignment.Center
                ) {
                    RadicalTactileCard(
                        isDark = isDark,
                        modifier = Modifier.widthIn(max = 360.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            RadicalIconBadge(
                                icon = Icons.Default.Wallpaper,
                                accentColor = RadicalPalette.EmeraldJade,
                                isDark = isDark,
                                size = 52.dp,
                                iconSize = 26.dp
                            )

                            Text(
                                text = "Finding Matches",
                                fontFamily = PlayfairDisplayFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = if (isDark) RadicalPalette.DarkCardTextPrimary else RadicalPalette.LightCardTextPrimary
                            )

                            Text(
                                text = "Finding wallpapers that match your style…",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isDark) RadicalPalette.DarkCardTextSecondary else RadicalPalette.LightCardTextSecondary,
                                textAlign = TextAlign.Center
                            )

                            CircularProgressIndicator(
                                color = RadicalPalette.EmeraldJade,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    state = scrollState,
                    columns = GridCells.Adaptive(minSize = metrics.galleryMinCellSize),
                    contentPadding = PaddingValues(
                        top = paddingValues.calculateTopPadding() + 8.dp,
                        bottom = paddingValues.calculateBottomPadding() + 28.dp,
                        start = metrics.horizontalPadding,
                        end = metrics.horizontalPadding
                    ),
                    verticalArrangement = Arrangement.spacedBy(metrics.cardSpacing),
                    horizontalArrangement = Arrangement.spacedBy(metrics.cardSpacing),
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = metrics.maxContentWidth)
                        .align(Alignment.TopCenter)
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                            OnboardingStepIndicator(
                                currentStep = currentStep - 1,
                                totalSteps = totalSteps,
                                isDark = isDark,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            OnboardingHeader(
                                title = "Refine your taste",
                                subtitle = "Like at least 4 wallpapers to continue.",
                                isDark = isDark,
                                accentColor = RadicalPalette.CoralRose
                            )
                        }
                    }
                    items(displayedWallpapers, key = { it.id }) { wallpaper ->
                        val isLiked = likedWallpapers.contains(wallpaper.id)
                        val isDisliked = dislikedWallpapers.contains(wallpaper.id)
                        val rating: Boolean? = when {
                            isLiked -> true
                            isDisliked -> false
                            else -> null
                        }

                        TactileGalleryWallpaperCard(
                            wallpaper = wallpaper,
                            rating = rating,
                            onLike = { viewModel.toggleLike(wallpaper.id) },
                            onHide = { viewModel.markDislike(wallpaper.id) },
                            metrics = metrics
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TactileGalleryWallpaperCard(
    wallpaper: WallpaperMetadata,
    rating: Boolean?,
    onLike: () -> Unit,
    onHide: () -> Unit,
    metrics: OnboardingLayoutMetrics
) {
    val isDark = LocalThemeIsDark.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.975f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
        label = "wallpaperPressScale"
    )

    val activeBorderColor = when (rating) {
        true -> RadicalPalette.EmeraldJade
        false -> Color.Black.copy(alpha = 0.35f)
        else -> if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.12f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(if (metrics.compactWidth) 0.74f else 0.70f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (rating == true) 6.dp else 2.dp,
                shape = RoundedCornerShape(metrics.cardCornerRadius),
                ambientColor = if (rating == true) RadicalPalette.EmeraldJade.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.12f),
                spotColor = if (rating == true) RadicalPalette.EmeraldJade.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.14f)
            )
            .clip(RoundedCornerShape(metrics.cardCornerRadius))
            .background(if (isDark) Color(0xFF141822) else Color(0xFFE2E8F0))
            .border(
                width = if (rating == true) 2.dp else 1.dp,
                color = activeBorderColor,
                shape = RoundedCornerShape(metrics.cardCornerRadius)
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onLike()
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onHide()
                }
            )
            .semantics {
                role = Role.Button
                contentDescription = when (rating) {
                    true -> "Liked wallpaper, tap to unlike, long press to hide"
                    false -> "Hidden wallpaper, tap to restore"
                    else -> "Wallpaper, tap to like, long press to hide"
                }
            }
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(wallpaper.url)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            loading = {
                WallpaperCardLoadingPlaceholder(isDark = isDark)
            },
            error = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (isDark) Color(0xFF171B26) else Color(0xFFE2E8F0)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BrokenImage,
                            contentDescription = "Failed to load",
                            tint = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8),
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Failed to load",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8)
                        )
                    }
                }
            },
            success = {
                SubcomposeAsyncImageContent()
            },
            alpha = if (rating == false) 0.20f else 1f
        )

        if (rating == false) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)))
        }

        // Vignette gradient for text & control legibility
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.20f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.55f)
                        )
                    )
                )
        )

        AnimatedVisibility(
            visible = rating == true,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .shadow(3.dp, CircleShape)
                    .clip(CircleShape)
                    .background(RadicalPalette.EmeraldJade)
                    .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isLiked = rating == true
            val isHidden = rating == false

            val likeBg = if (isLiked) {
                Brush.horizontalGradient(
                    listOf(RadicalPalette.EmeraldJade, RadicalPalette.EmeraldJade.copy(alpha = 0.85f))
                )
            } else {
                Brush.horizontalGradient(
                    listOf(Color.Black.copy(alpha = 0.60f), Color.Black.copy(alpha = 0.50f))
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(likeBg)
                    .border(
                        1.dp,
                        Color.White.copy(alpha = if (isLiked) 0.5f else 0.20f),
                        RoundedCornerShape(99.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = Color.White.copy(alpha = 0.3f)),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onLike()
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = Color.White
                    )
                    Text(
                        text = if (isLiked) "Liked" else "Like",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 11.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (isHidden) Color(0xFF1E2430) else Color.Black.copy(alpha = 0.60f))
                    .border(1.dp, Color.White.copy(alpha = 0.20f), CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = Color.White.copy(alpha = 0.3f)),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onHide()
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Hide",
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

@Composable
private fun WallpaperCardLoadingPlaceholder(isDark: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "card_shimmer")
    val shimmerTranslate by infiniteTransition.animateFloat(
        initialValue = -200f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_pos"
    )

    val baseColor = if (isDark) Color(0xFF141824) else Color(0xFFE2E8F0)
    val highlightColor = if (isDark) Color(0xFF222838) else Color(0xFFF1F5F9)

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset(shimmerTranslate, shimmerTranslate),
        end = Offset(shimmerTranslate + 200f, shimmerTranslate + 200f)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(shimmerBrush),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CircularProgressIndicator(
                color = RadicalPalette.EmeraldJade,
                strokeWidth = 2.dp,
                modifier = Modifier.size(22.dp)
            )

            Text(
                text = "Loading...",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
            )
        }
    }
}
