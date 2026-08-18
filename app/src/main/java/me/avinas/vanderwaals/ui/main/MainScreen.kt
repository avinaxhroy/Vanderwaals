package me.avinas.vanderwaals.ui.main

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.bumptech.glide.request.RequestOptions
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.glide.GlideImage
import kotlinx.coroutines.launch
import me.avinas.vanderwaals.BuildConfig
import me.avinas.vanderwaals.R
import me.avinas.vanderwaals.core.SmartCropTransformation
import me.avinas.vanderwaals.core.getDeviceScreenSize
import me.avinas.vanderwaals.core.resolveWallpaperFile
import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import me.avinas.vanderwaals.ui.onboarding.bounceClick
import me.avinas.vanderwaals.ui.onboarding.rememberOnboardingLayoutMetrics
import me.avinas.vanderwaals.ui.settings.RadicalButtonVariant
import me.avinas.vanderwaals.ui.settings.RadicalIconBadge
import me.avinas.vanderwaals.ui.settings.RadicalPalette
import me.avinas.vanderwaals.ui.settings.RadicalTactileBackdrop
import me.avinas.vanderwaals.ui.settings.RadicalTactileButton
import me.avinas.vanderwaals.ui.settings.RadicalTactileCard
import me.avinas.vanderwaals.ui.settings.RadicalWatermarkBadge
import me.avinas.vanderwaals.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToOnboarding: () -> Unit = {},
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.currentWallpaper.collectAsState()
    val showOverlay by viewModel.showOverlay.collectAsState()
    val loadingState by viewModel.loadingState.collectAsState()
    val isLoading = loadingState != MainViewModel.KoalaLoadingState.IDLE

    val buttonText = when (loadingState) {
        MainViewModel.KoalaLoadingState.IDLE -> "Change Wallpaper"
        MainViewModel.KoalaLoadingState.THINKING -> "Selecting next wallpaper…"
        MainViewModel.KoalaLoadingState.FINDING -> "Finding best matches…"
        MainViewModel.KoalaLoadingState.APPLYING -> "Applying wallpaper…"
    }

    val context = LocalContext.current
    val screenSize = remember { getDeviceScreenSize(context) }
    val haptic = LocalHapticFeedback.current
    val isDark = LocalThemeIsDark.current

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()
    val showLiveWallpaperDialog by viewModel.showLiveWallpaperDialog.collectAsState()
    val liveWallpaperInfo by viewModel.liveWallpaperInfo.collectAsState()
    val showInstructionsDialog by viewModel.showInstructionsDialog.collectAsState()
    val showEmbeddingMigrationDialog by viewModel.showEmbeddingMigrationDialog.collectAsState()
    val totalLikes by viewModel.totalLikes.collectAsState()

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearErrorMessage()
        }
    }
    LaunchedEffect(successMessage) {
        successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccessMessage()
        }
    }

    val wallpaper = (uiState as? MainViewModel.MainUiState.Success)?.wallpaper

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) RadicalPalette.DarkCanvasBase else RadicalPalette.LightCanvasBase)
    ) {
        when (uiState) {
            is MainViewModel.MainUiState.Loading -> {
                HomeLoadingState(isDark = isDark)
            }
            is MainViewModel.MainUiState.Success -> {
                if (wallpaper != null) {
                    WallpaperStage(
                        wallpaper = wallpaper,
                        isDark = isDark,
                        screenWidth = screenSize.width,
                        screenHeight = screenSize.height,
                        showOverlay = showOverlay,
                        onWallpaperTap = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.toggleOverlay()
                        }
                    )

                    AnimatedVisibility(
                        visible = showOverlay,
                        enter = fadeIn(tween(220, easing = FastOutSlowInEasing)) + slideInVertically(
                            initialOffsetY = { -it / 2 },
                            animationSpec = tween(220, easing = FastOutSlowInEasing)
                        ),
                        exit = fadeOut(tween(160)) + slideOutVertically(
                            targetOffsetY = { -it / 2 },
                            animationSpec = tween(160)
                        ),
                        modifier = Modifier.align(Alignment.TopCenter)
                    ) {
                        HomeTopQuickAccessBar(
                            isDark = isDark,
                            onNavigateToHistory = onNavigateToHistory,
                            onNavigateToSettings = onNavigateToSettings
                        )
                    }

                    AnimatedVisibility(
                        visible = showOverlay,
                        enter = slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = tween(260, easing = FastOutSlowInEasing)
                        ) + fadeIn(tween(200)),
                        exit = slideOutVertically(
                            targetOffsetY = { it },
                            animationSpec = tween(200, easing = FastOutSlowInEasing)
                        ) + fadeOut(tween(160)),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .widthIn(max = 520.dp)
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Source attribution chip placed cleanly above card with no overlap
                            WallpaperAttributionChip(
                                wallpaper = wallpaper,
                                isDark = isDark
                            )

                            RadicalTactileCard(
                                isDark = isDark,
                                cornerRadius = 24.dp,
                                contentPadding = PaddingValues(16.dp)
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(RadicalPalette.EmeraldJade)
                                                .shadow(4.dp, CircleShape, ambientColor = RadicalPalette.EmeraldJade, spotColor = RadicalPalette.EmeraldJade)
                                        )
                                        Text(
                                            text = "AI TASTE ENGINE",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.2.sp,
                                            color = if (isDark) RadicalPalette.DarkCardTextSecondary else RadicalPalette.LightCardTextSecondary
                                        )
                                    }

                                    RadicalTactileButton(
                                        text = buttonText,
                                        icon = Icons.Default.Shuffle,
                                        variant = RadicalButtonVariant.Primary,
                                        isDark = isDark,
                                        isLoading = isLoading,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.changeNow()
                                        }
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        TactileTastingPill(
                                            label = "Love it",
                                            icon = Icons.Default.Favorite,
                                            accentColor = RadicalPalette.CoralRose,
                                            isDark = isDark,
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                viewModel.likeCurrentWallpaper(
                                                    onSuccess = {
                                                        scope.launch {
                                                            snackbarHostState.showSnackbar("Liked. Recommendations updated")
                                                        }
                                                    },
                                                    onError = { error ->
                                                        scope.launch { snackbarHostState.showSnackbar(error) }
                                                    }
                                                )
                                            }
                                        )

                                        TactileTastingPill(
                                            label = "Dislike",
                                            icon = Icons.Default.ThumbDown,
                                            accentColor = if (isDark) RadicalPalette.DarkCardTextSecondary else RadicalPalette.LightCardTextSecondary,
                                            isDark = isDark,
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                viewModel.dislikeCurrentWallpaper(
                                                    onSuccess = {
                                                        scope.launch {
                                                            snackbarHostState.showSnackbar("Hidden. Showing fewer wallpapers like this")
                                                        }
                                                    },
                                                    onError = { error ->
                                                        scope.launch { snackbarHostState.showSnackbar(error) }
                                                    }
                                                )
                                            }
                                        )

                                        TactileTastingPill(
                                            label = "Save",
                                            icon = Icons.Default.Download,
                                            accentColor = RadicalPalette.ElectricAzure,
                                            isDark = isDark,
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                viewModel.downloadCurrentWallpaper(
                                                    onSuccess = {
                                                        scope.launch {
                                                            snackbarHostState.showSnackbar("Saved wallpaper to gallery")
                                                        }
                                                    },
                                                    onError = { error ->
                                                        scope.launch { snackbarHostState.showSnackbar(error) }
                                                    }
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = !showOverlay,
                        enter = fadeIn(tween(240)) + scaleIn(initialScale = 0.9f),
                        exit = fadeOut(tween(160)) + scaleOut(targetScale = 0.9f),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 18.dp)
                    ) {
                        TactileImmersionPill(
                            isDark = isDark,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.toggleOverlay()
                            }
                        )
                    }
                } else {
                    EmptyWallpaperState(
                        isDark = isDark,
                        isLoading = isLoading,
                        buttonText = buttonText,
                        onChangeNow = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.changeNow()
                        },
                        onNavigateToSettings = onNavigateToSettings,
                        onNavigateToHistory = onNavigateToHistory
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (showOverlay) 160.dp else 76.dp)
                .padding(horizontal = 16.dp)
        )

        if (showLiveWallpaperDialog) {
            me.avinas.vanderwaals.ui.components.LiveWallpaperBlockedDialog(
                serviceName = liveWallpaperInfo.first,
                packageName = liveWallpaperInfo.second,
                onOpenSettings = { viewModel.onSettingsOpened() },
                onShowInstructions = { viewModel.showInstructions() },
                onDismiss = { viewModel.dismissLiveWallpaperDialog() }
            )
        }
        if (showInstructionsDialog) {
            me.avinas.vanderwaals.ui.components.LiveWallpaperInstructionsDialog(
                onRetrySettings = { viewModel.onSettingsOpened() },
                onDismiss = { viewModel.dismissInstructionsDialog() }
            )
        }
        if (showEmbeddingMigrationDialog) {
            me.avinas.vanderwaals.ui.components.EmbeddingMigrationDialog(
                onRePersonalize = { viewModel.onRePersonalize(onNavigateToOnboarding) },
                onAutoMode = { viewModel.onAutoMode() },
                onRemindLater = { viewModel.onRemindLater() },
                onDontShowAgain = { viewModel.onDontShowAgain() },
                onDismiss = { viewModel.onRemindLater() },
                totalLikes = totalLikes
            )
        }
    }
}


@Composable
private fun HomeTopQuickAccessBar(
    isDark: Boolean,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .height(42.dp)
                .shadow(
                    elevation = if (isDark) 6.dp else 4.dp,
                    shape = RoundedCornerShape(99.dp),
                    ambientColor = Color.Black.copy(alpha = 0.25f),
                    spotColor = Color.Black.copy(alpha = 0.18f)
                )
                .clip(RoundedCornerShape(99.dp))
                .background(
                    if (isDark) {
                        Brush.verticalGradient(
                            listOf(
                                RadicalPalette.DarkCardTop,
                                RadicalPalette.DarkCardBottom
                            )
                        )
                    } else {
                        Brush.verticalGradient(
                            listOf(
                                RadicalPalette.LightCardTop,
                                RadicalPalette.LightCardBottom
                            )
                        )
                    }
                )
                .border(
                    width = 1.dp,
                    brush = if (isDark) {
                        Brush.verticalGradient(
                            listOf(
                                RadicalPalette.DarkCardBorderTop,
                                RadicalPalette.DarkCardBorderBottom
                            )
                        )
                    } else {
                        Brush.verticalGradient(
                            listOf(
                                RadicalPalette.LightCardBorderTop,
                                RadicalPalette.LightCardBorderBottom
                            )
                        )
                    },
                    shape = RoundedCornerShape(99.dp)
                )
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Vanderwaals",
                fontFamily = PlayfairDisplayFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = if (isDark) RadicalPalette.DarkCardTextPrimary else RadicalPalette.LightCardTextPrimary,
                letterSpacing = (-0.3).sp
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TactileJewelTopButton(
                icon = Icons.Default.History,
                contentDescription = "History",
                accentColor = RadicalPalette.SapphireBlue,
                isDark = isDark,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onNavigateToHistory()
                }
            )

            TactileJewelTopButton(
                icon = Icons.Default.Settings,
                contentDescription = "Settings",
                accentColor = RadicalPalette.EmeraldJade,
                isDark = isDark,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onNavigateToSettings()
                }
            )
        }
    }
}

@Composable
private fun TactileJewelTopButton(
    icon: ImageVector,
    contentDescription: String,
    accentColor: Color,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(42.dp)
            .shadow(
                elevation = if (isDark) 6.dp else 4.dp,
                shape = CircleShape,
                ambientColor = Color.Black.copy(alpha = 0.25f),
                spotColor = Color.Black.copy(alpha = 0.18f)
            )
            .clip(CircleShape)
            .background(
                if (isDark) {
                    Brush.verticalGradient(
                        listOf(
                            RadicalPalette.DarkCardTop,
                            RadicalPalette.DarkCardBottom
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(
                            RadicalPalette.LightCardTop,
                            RadicalPalette.LightCardBottom
                        )
                    )
                }
            )
            .border(
                width = 1.dp,
                brush = if (isDark) {
                    Brush.verticalGradient(
                        listOf(
                            RadicalPalette.DarkCardBorderTop,
                            RadicalPalette.DarkCardBorderBottom
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(
                            RadicalPalette.LightCardBorderTop,
                            RadicalPalette.LightCardBorderBottom
                        )
                    )
                },
                shape = CircleShape
            )
            .bounceClick(onClick)
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isDark) RadicalPalette.DarkCardTextPrimary else RadicalPalette.LightCardTextPrimary,
            modifier = Modifier.size(19.dp)
        )
    }
}


@Composable
private fun WallpaperStage(
    wallpaper: WallpaperMetadata,
    isDark: Boolean,
    screenWidth: Int,
    screenHeight: Int,
    showOverlay: Boolean,
    onWallpaperTap: () -> Unit
) {
    val context = LocalContext.current

    Crossfade(
        targetState = wallpaper,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "wallpaper_transition"
    ) { target ->
        val croppedFile = java.io.File(context.cacheDir, "wallpapers/${target.id}_cropped.jpg")
        val originalFile = resolveWallpaperFile(context, target.id)
        val (imageSource, needsSmartCrop) = when {
            croppedFile.exists() -> croppedFile.absolutePath to false
            originalFile != null -> originalFile.absolutePath to true
            else -> target.url to true
        }

        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onWallpaperTap() }
                .semantics {
                    role = Role.Image
                    contentDescription = "Current wallpaper. Tap to toggle controls"
                }
        ) {
            GlideImage(
                imageModel = { imageSource },
                imageOptions = ImageOptions(
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center
                ),
                requestOptions = {
                    if (needsSmartCrop) {
                        RequestOptions()
                            .override(com.bumptech.glide.request.target.Target.SIZE_ORIGINAL)
                            .transform(
                                SmartCropTransformation(
                                    targetWidth = screenWidth,
                                    targetHeight = screenHeight
                                )
                            )
                    } else RequestOptions()
                },
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(if (isDark) RadicalPalette.DarkCanvasBase else RadicalPalette.LightCanvasBase),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = RadicalPalette.EmeraldJade,
                            strokeWidth = 2.5.dp
                        )
                    }
                },
                failure = {
                    WallpaperFailureState(
                        onRetry = onWallpaperTap,
                        isDark = isDark
                    )
                }
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = if (showOverlay) 0.35f else 0.10f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = if (showOverlay) 0.50f else 0.18f)
                            )
                        )
                    )
            )
        }
    }
}


@Composable
private fun WallpaperAttributionChip(
    wallpaper: WallpaperMetadata,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val (sourceText, sourceUrl) = remember(wallpaper) {
        when (wallpaper.source.lowercase()) {
            "github" -> {
                val parts = wallpaper.url.split("/")
                if (parts.size >= 5 && parts[2] == "raw.githubusercontent.com") {
                    val user = parts[3]
                    val repo = parts[4]
                    "From $user/$repo" to "https://github.com/$user/$repo"
                } else "From Community Collection" to "https://github.com/dharmx/walls"
            }
            "bing" -> "From Bing Daily" to "https://www.bing.com"
            else -> "From ${wallpaper.source}" to null
        }
    }

    Row(
        modifier = modifier
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(99.dp),
                ambientColor = Color.Black.copy(alpha = 0.25f)
            )
            .clip(RoundedCornerShape(99.dp))
            .background(
                if (isDark) {
                    Brush.verticalGradient(
                        listOf(
                            RadicalPalette.DarkCardTop,
                            RadicalPalette.DarkCardBottom
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(
                            RadicalPalette.LightCardTop,
                            RadicalPalette.LightCardBottom
                        )
                    )
                }
            )
            .border(
                width = 1.dp,
                brush = if (isDark) {
                    Brush.verticalGradient(
                        listOf(
                            RadicalPalette.DarkCardBorderTop,
                            RadicalPalette.DarkCardBorderBottom
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(
                            RadicalPalette.LightCardBorderTop,
                            RadicalPalette.LightCardBorderBottom
                        )
                    )
                },
                shape = RoundedCornerShape(99.dp)
            )
            .clickable(enabled = sourceUrl != null) {
                sourceUrl?.let { runCatching { uriHandler.openUri(it) } }
            }
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics {
                role = Role.Button
                contentDescription = "$sourceText. Tap to open source"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(RadicalPalette.ElectricAzure)
                .shadow(3.dp, CircleShape, ambientColor = RadicalPalette.ElectricAzure)
        )
        Text(
            text = sourceText,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (isDark) RadicalPalette.DarkCardTextPrimary else RadicalPalette.LightCardTextPrimary
        )
        if (sourceUrl != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = if (isDark) RadicalPalette.DarkCardTextSecondary else RadicalPalette.LightCardTextSecondary
            )
        }
    }
}


@Composable
private fun TactileTastingPill(
    label: String,
    icon: ImageVector,
    accentColor: Color,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val pillShape = RoundedCornerShape(14.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = tween(100, easing = FastOutSlowInEasing),
        label = "feedbackScale"
    )

    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .sizeIn(minHeight = 48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (isPressed) 1.dp else 2.dp,
                shape = pillShape,
                ambientColor = Color.Black.copy(alpha = 0.12f)
            )
            .clip(pillShape)
            .background(
                if (isDark) {
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFFFAF8F5),
                            Color(0xFFE2DDD5)
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF033528),
                            Color(0xFF02241A)
                        )
                    )
                }
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        if (isDark) Color.White.copy(alpha = 0.90f) else Color.White.copy(alpha = 0.25f),
                        if (isDark) Color(0xFFB5ADA1) else Color.Black.copy(alpha = 0.40f)
                    )
                ),
                shape = pillShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = accentColor.copy(alpha = 0.15f)),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            )
            .semantics {
                role = Role.Button
                contentDescription = label
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = if (isDark) 0.18f else 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = accentColor
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = if (isDark) RadicalPalette.DarkCardTextPrimary else RadicalPalette.LightCardTextPrimary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                softWrap = false
            )
        }
    }
}


@Composable
private fun TactileImmersionPill(
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(99.dp),
                ambientColor = Color.Black.copy(alpha = 0.30f),
                spotColor = Color.Black.copy(alpha = 0.20f)
            )
            .clip(RoundedCornerShape(99.dp))
            .background(
                if (isDark) {
                    Brush.verticalGradient(
                        listOf(
                            RadicalPalette.DarkCardTop,
                            RadicalPalette.DarkCardBottom
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(
                            RadicalPalette.LightCardTop,
                            RadicalPalette.LightCardBottom
                        )
                    )
                }
            )
            .border(
                width = 1.dp,
                brush = if (isDark) {
                    Brush.verticalGradient(
                        listOf(
                            RadicalPalette.DarkCardBorderTop,
                            RadicalPalette.DarkCardBorderBottom
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(
                            RadicalPalette.LightCardBorderTop,
                            RadicalPalette.LightCardBorderBottom
                        )
                    )
                },
                shape = RoundedCornerShape(99.dp)
            )
            .bounceClick(onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .semantics {
                role = Role.Button
                contentDescription = "Show Controls"
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (isDark) RadicalPalette.DarkCardTextPrimary else RadicalPalette.LightCardTextPrimary
            )
            Text(
                text = "Show Controls",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (isDark) RadicalPalette.DarkCardTextPrimary else RadicalPalette.LightCardTextPrimary
            )
        }
    }
}


@Composable
private fun EmptyWallpaperState(
    isDark: Boolean,
    isLoading: Boolean,
    buttonText: String,
    onChangeNow: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory: () -> Unit
) {
    val metrics = rememberOnboardingLayoutMetrics()

    Box(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = metrics.horizontalPadding)
    ) {
        RadicalTactileBackdrop(
            isDark = isDark,
            modifier = Modifier.matchParentSize()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Vanderwaals",
                fontFamily = PlayfairDisplayFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TactileJewelTopButton(
                    icon = Icons.Default.History,
                    contentDescription = "History",
                    accentColor = RadicalPalette.SapphireBlue,
                    isDark = isDark,
                    onClick = onNavigateToHistory
                )
                TactileJewelTopButton(
                    icon = Icons.Default.Settings,
                    contentDescription = "Settings",
                    accentColor = RadicalPalette.EmeraldJade,
                    isDark = isDark,
                    onClick = onNavigateToSettings
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 56.dp)
                .verticalScroll(rememberScrollState())
                .widthIn(max = metrics.maxContentWidth)
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            RadicalTactileCard(
                isDark = isDark,
                contentPadding = PaddingValues(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    RadicalIconBadge(
                        icon = Icons.Default.Wallpaper,
                        accentColor = RadicalPalette.EmeraldJade,
                        isDark = isDark,
                        size = 56.dp,
                        iconSize = 28.dp
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "No Wallpaper Active",
                            fontFamily = PlayfairDisplayFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = if (isDark) RadicalPalette.DarkCardTextPrimary else RadicalPalette.LightCardTextPrimary,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Curates wallpapers matched to your taste and device resolution.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isDark) RadicalPalette.DarkCardTextSecondary else RadicalPalette.LightCardTextSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    }

                    RadicalTactileButton(
                        text = if (isLoading) buttonText else "Curate First Wallpaper",
                        icon = Icons.Default.Shuffle,
                        isDark = isDark,
                        isLoading = isLoading,
                        variant = RadicalButtonVariant.Primary,
                        onClick = onChangeNow
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            RadicalWatermarkBadge(
                isDark = isDark,
                version = BuildConfig.VERSION_NAME
            )
        }
    }
}

@Composable
private fun HomeLoadingState(isDark: Boolean) {
    Box(
        Modifier
            .fillMaxSize()
            .background(if (isDark) RadicalPalette.DarkCanvasBase else RadicalPalette.LightCanvasBase),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            RadicalIconBadge(
                icon = Icons.Default.AutoAwesome,
                accentColor = RadicalPalette.EmeraldJade,
                isDark = isDark,
                size = 56.dp,
                iconSize = 28.dp
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Preparing Your Wallpaper",
                    fontFamily = PlayfairDisplayFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Matching wallpapers to your taste…",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                    textAlign = TextAlign.Center
                )
            }
            CircularProgressIndicator(
                color = RadicalPalette.EmeraldJade,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun WallpaperFailureState(
    onRetry: () -> Unit,
    isDark: Boolean
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(if (isDark) RadicalPalette.DarkCanvasBase else RadicalPalette.LightCanvasBase),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            RadicalIconBadge(
                icon = Icons.Default.Warning,
                accentColor = RadicalPalette.RadiantAmber,
                isDark = isDark,
                size = 52.dp,
                iconSize = 26.dp
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Couldn’t Load Wallpaper",
                    fontFamily = PlayfairDisplayFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Try another wallpaper or check your internet connection.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                    textAlign = TextAlign.Center
                )
            }
            RadicalTactileButton(
                text = "Try Another",
                icon = Icons.Default.Shuffle,
                isDark = isDark,
                variant = RadicalButtonVariant.Secondary,
                onClick = onRetry,
                modifier = Modifier.widthIn(max = 200.dp)
            )
        }
    }
}
