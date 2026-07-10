package me.avinas.vanderwaals.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import me.avinas.vanderwaals.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
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
    val progress = (likedWallpapers.size.toFloat() / 4f).coerceAtMost(1f)

    LaunchedEffect(finishState) {
        if (finishState is FinishState.Success) {
            onContinue()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent,
        topBar = {
            // Transparent top navigation with back button and refresh button
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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = getOnboardingTextPrimary(isDark)
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    IconButton(onClick = { viewModel.refreshWallpapers() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = getOnboardingTextPrimary(isDark)
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
                buttonText = "Finish Setup",
                showBorderGradient = canContinue,
                showLoading = finishState is FinishState.Initializing,
                onButtonClick = { viewModel.finishOnboarding() }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            OnboardingBackdrop(
                isDark = isDark,
                modifier = Modifier.fillMaxSize()
            )

            if (displayedWallpapers.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = BrandPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            } else {
                LazyVerticalGrid(
                    state = scrollState,
                    columns = GridCells.Adaptive(minSize = metrics.galleryMinCellSize),
                    contentPadding = PaddingValues(
                        top = paddingValues.calculateTopPadding() + 12.dp,
                        bottom = paddingValues.calculateBottomPadding() + 24.dp,
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
                        Column(
                            modifier = Modifier
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.Start
                        ) {
                            OnboardingStepIndicator(
                                currentStep = currentStep - 1,
                                totalSteps = totalSteps,
                                isDark = isDark,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            // Step number above title
                            Text(
                                text = "${likedWallpapers.size}/4 likes needed · Step $currentStep of $totalSteps",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (likedWallpapers.size >= 4) BrandPrimary else getOnboardingTextSecondary(isDark),
                                fontWeight = if (likedWallpapers.size >= 4) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )

                            // Title
                            Text(
                                text = "We Found Matches",
                                style = LuxeHeadlineStyle,
                                color = getOnboardingTextPrimary(isDark),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            // Subheadline
                            Text(
                                text = "Like the ones that speak to you",
                                style = LuxeBodyStyle,
                                color = getOnboardingTextSecondary(isDark)
                            )
                        }
                    }

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Spacer(modifier = Modifier.height(metrics.sectionSpacing))
                    }

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.08f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(progress)
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                colors = listOf(BrandPrimary, Color(0xFFF97316))
                                            )
                                        )
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Tap to like, hold to hide.",
                                style = LuxeBodyStyle,
                                color = getOnboardingTextSecondary(isDark),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    items(displayedWallpapers) { wallpaper ->
                        val isLiked = likedWallpapers.contains(wallpaper.id)
                        val isDisliked = dislikedWallpapers.contains(wallpaper.id)
                        val rating = when {
                            isLiked -> true
                            isDisliked -> false
                            else -> null
                        }

                        GalleryWallpaperCard(
                            wallpaper = wallpaper,
                            rating = rating,
                            onRate = { liked ->
                                if (liked) {
                                    viewModel.toggleLike(wallpaper.id)
                                } else {
                                    viewModel.markDislike(wallpaper.id)
                                }
                            },
                            metrics = metrics
                        )
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun GalleryWallpaperCard(
    wallpaper: WallpaperMetadata,
    rating: Boolean?,
    onRate: (Boolean) -> Unit,
    metrics: OnboardingLayoutMetrics
) {
    val isDark = LocalThemeIsDark.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    // Spring-based scale response on touch
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bounceScale"
    )

    // Animated glow elevation
    val glowElevation by animateFloatAsState(
        targetValue = if (rating == true) 16f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "glowElevation"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(if (metrics.compactWidth) 0.72f else 0.65f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = glowElevation.dp,
                shape = RoundedCornerShape(metrics.cardCornerRadius),
                ambientColor = if (rating == true) BrandPrimary.copy(alpha = 0.3f) else Color.Transparent,
                spotColor = if (rating == true) BrandAccent.copy(alpha = 0.25f) else Color.Transparent
            )
            .clip(RoundedCornerShape(metrics.cardCornerRadius))
            .background(getOnboardingCardBackground(isDark))
            .border(
                width = if (rating == true) 2.dp else 1.dp,
                color = when {
                    rating == true -> BrandPrimary.copy(alpha = 0.8f)
                    rating == false -> Color.Black.copy(alpha = 0.3f)
                    else -> getOnboardingCardBorder(isDark)
                },
                shape = RoundedCornerShape(metrics.cardCornerRadius)
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onRate(true) },
                onLongClick = { onRate(false) }
            )
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(wallpaper.url)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .then(if (rating == false) Modifier.background(Color.Black.copy(alpha = 0.7f)) else Modifier),
            contentScale = ContentScale.Crop,
            alpha = when {
                rating == false -> 0.25f
                else -> 1f
            }
        )

        // Premium gradient overlay for liked state
        if (rating == true) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                BrandPrimary.copy(alpha = 0.15f)
                            )
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        width = 2.5.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(BrandPrimary, BrandAccent)
                        ),
                        shape = RoundedCornerShape(metrics.cardCornerRadius)
                    )
            )
        }

        // Premium action indicator at top right
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
        ) {
            AnimatedVisibility(
                visible = rating != null,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = if (rating == true) {
                                    listOf(Color(0xFFEC4899), Color(0xFFBE185D))
                                } else {
                                    listOf(Color.Black.copy(alpha = 0.6f), Color.Black.copy(alpha = 0.4f))
                                }
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (rating == true) Icons.Default.Favorite else Icons.Default.Close,
                        contentDescription = null,
                        tint = if (rating == true) Color.White else Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Tap hint indicator (visible only when no rating)
        AnimatedVisibility(
            visible = rating == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}