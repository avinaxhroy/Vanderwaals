package me.avinas.vanderwaals.ui.history

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.glide.GlideImage
import me.avinas.vanderwaals.domain.usecase.FeedbackType
import java.io.File
import me.avinas.vanderwaals.ui.theme.*
import me.avinas.vanderwaals.ui.theme.components.*
import me.avinas.vanderwaals.ui.onboarding.*
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.historyGroups.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedWallpaper by remember { mutableStateOf<HistoryItemUiState?>(null) }
    
    val context = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    
    val isDark = LocalThemeIsDark.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val metrics = rememberOnboardingLayoutMetrics()

    androidx.activity.compose.BackHandler {
        onNavigateBack()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        OnboardingBackdrop(isDark = isDark, modifier = Modifier.matchParentSize())
        
        Scaffold(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = Color.Transparent,
            snackbarHost = { 
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
                ) 
            },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "History",
                            fontFamily = PlayfairDisplayFamily,
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            color = getOnboardingTextPrimary(isDark)
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.padding(start = 4.dp).bounceClick()
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = getOnboardingTextPrimary(isDark)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = if (isDark) Color(0xFF14120F).copy(alpha = 0.8f) else Color(0xFFF9F7F5).copy(alpha = 0.8f),
                        titleContentColor = getOnboardingTextPrimary(isDark),
                        navigationIconContentColor = getOnboardingTextPrimary(isDark)
                    ),
                    windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
                    scrollBehavior = scrollBehavior
                )
            }
        ) { paddingValues ->
            when (val state = uiState) {
                is HistoryViewModel.HistoryUiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .shadow(
                                    elevation = 4.dp,
                                    shape = RoundedCornerShape(metrics.cardCornerRadius),
                                    ambientColor = if (isDark) Color(0xFF3F3F46).copy(alpha = 0.12f) else Color(0x0A000000),
                                    spotColor = Color.Transparent
                                )
                                .border(1.dp, getOnboardingCardBorder(isDark), RoundedCornerShape(metrics.cardCornerRadius))
                                .clip(RoundedCornerShape(metrics.cardCornerRadius))
                                .background(getOnboardingCardBackground(isDark))
                                .padding(24.dp)
                                .widthIn(max = 160.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                CircularProgressIndicator(
                                    color = BrandPrimary,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = "Loading...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = getOnboardingTextSecondary(isDark)
                                )
                            }
                        }
                    }
                }
                is HistoryViewModel.HistoryUiState.Success -> {
                    val historyGroups = state.groups
                    if (historyGroups.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues)
                                .padding(horizontal = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 360.dp)
                                    .shadow(
                                        elevation = 4.dp,
                                        shape = RoundedCornerShape(metrics.cardCornerRadius),
                                        ambientColor = if (isDark) Color(0xFF3F3F46).copy(alpha = 0.12f) else Color(0x0A000000),
                                        spotColor = Color.Transparent
                                    )
                                    .border(1.dp, getOnboardingCardBorder(isDark), RoundedCornerShape(metrics.cardCornerRadius))
                                    .clip(RoundedCornerShape(metrics.cardCornerRadius))
                                    .background(getOnboardingCardBackground(isDark))
                                    .padding(32.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(20.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(80.dp)
                                            .background(
                                                brush = Brush.radialGradient(
                                                    colors = listOf(
                                                        BrandPrimary.copy(alpha = 0.25f),
                                                        BrandPrimary.copy(alpha = 0.05f),
                                                        Color.Transparent
                                                    )
                                                ),
                                                shape = CircleShape
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = BrandPrimary.copy(alpha = 0.15f),
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.History,
                                            contentDescription = null,
                                            tint = BrandPrimary,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }

                                    Text(
                                        text = "Your Gallery is Waiting",
                                        fontFamily = PlayfairDisplayFamily,
                                        fontStyle = FontStyle.Italic,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 24.sp,
                                        textAlign = TextAlign.Center,
                                        color = getOnboardingTextPrimary(isDark)
                                    )

                                    Text(
                                        text = "Wallpapers you've selected and applied will be preserved here in your personal collection.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center,
                                        color = getOnboardingTextSecondary(isDark),
                                        lineHeight = 20.sp
                                    )
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            LazyVerticalGrid(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .widthIn(max = metrics.maxContentWidth),
                                columns = GridCells.Adaptive(minSize = 340.dp),
                                contentPadding = PaddingValues(
                                    top = 8.dp,
                                    bottom = 32.dp,
                                    start = metrics.horizontalPadding,
                                    end = metrics.horizontalPadding
                                ),
                                horizontalArrangement = Arrangement.spacedBy(metrics.cardSpacing),
                                verticalArrangement = Arrangement.spacedBy(metrics.cardSpacing)
                            ) {
                                historyGroups.forEach { (dateHeader, items) ->
                                    item(key = "header_$dateHeader", span = { GridItemSpan(maxLineSpan) }) {
                                        Text(
                                            text = dateHeader.uppercase(),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = getOnboardingTextSecondary(isDark),
                                            letterSpacing = 1.2.sp,
                                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 8.dp)
                                        )
                                    }
                                    
                                    items.forEach { historyItem ->
                                        item(key = "item_${historyItem.id}") {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .shadow(
                                                        elevation = 4.dp,
                                                        shape = RoundedCornerShape(metrics.cardCornerRadius),
                                                        ambientColor = if (isDark) Color(0xFF3F3F46).copy(alpha = 0.12f) else Color(0x0A000000),
                                                        spotColor = Color.Transparent
                                                    )
                                                    .border(1.dp, getOnboardingCardBorder(isDark), RoundedCornerShape(metrics.cardCornerRadius))
                                                    .clip(RoundedCornerShape(metrics.cardCornerRadius))
                                                    .background(getOnboardingCardBackground(isDark))
                                                    .bounceClick { selectedWallpaper = historyItem }
                                            ) {
                                                HistoryItemRow(
                                                    item = historyItem,
                                                    isDark = isDark,
                                                    onThumbnailClick = { selectedWallpaper = historyItem },
                                                    onLikeClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        viewModel.updateFeedback(historyItem.id, FeedbackType.LIKE) {
                                                            viewModel.showSnackbar(snackbarHostState, "Preferences updated")
                                                        }
                                                    },
                                                    onDislikeClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        viewModel.updateFeedback(historyItem.id, FeedbackType.DISLIKE) {
                                                            viewModel.showSnackbar(snackbarHostState, "Preferences updated")
                                                        }
                                                    },
                                                    onDownloadClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        viewModel.downloadWallpaper(
                                                            wallpaperId = historyItem.wallpaper.id,
                                                            onSuccess = {
                                                                viewModel.showSnackbar(snackbarHostState, "Saved to gallery")
                                                            },
                                                            onError = { error ->
                                                                viewModel.showSnackbar(snackbarHostState, error)
                                                            }
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Full-screen Lock Screen simulation preview dialog
    selectedWallpaper?.let { item ->
        var showOverlay by remember { mutableStateOf(true) }
        
        Dialog(
            onDismissRequest = { selectedWallpaper = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showOverlay = !showOverlay }
            ) {
                val imageModel = if (File(item.localCroppedPath).exists()) {
                    File(item.localCroppedPath)
                } else {
                    item.wallpaper.thumbnailUrl
                }

                GlideImage(
                    imageModel = { imageModel },
                    imageOptions = ImageOptions(
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center
                    ),
                    modifier = Modifier.fillMaxSize()
                )
                
                // Simulated Lock Screen content overlay
                AnimatedVisibility(
                    visible = showOverlay,
                    enter = fadeIn(animationSpec = tween(300)) + expandVertically(animationSpec = tween(300)),
                    exit = fadeOut(animationSpec = tween(300)) + shrinkVertically(animationSpec = tween(300)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Padlock, Clock, and Date
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(top = 56.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "9:41",
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontSize = 84.sp,
                                    fontWeight = FontWeight.Light,
                                    color = Color.White.copy(alpha = 0.9f),
                                    letterSpacing = (-1).sp
                                )
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Thursday, May 22",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White.copy(alpha = 0.9f),
                                    letterSpacing = 0.5.sp
                                )
                            )
                        }

                        // Floating action control bar at the bottom
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(bottom = 32.dp)
                                .padding(horizontal = 24.dp)
                        ) {
                            GlassCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 320.dp),
                                shape = RoundedCornerShape(32.dp),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Like Button
                                    AnimatedFeedbackButton(
                                        isActive = item.feedback == FeedbackType.LIKE,
                                        activeIcon = Icons.Filled.Favorite,
                                        inactiveIcon = Icons.Outlined.FavoriteBorder,
                                        activeColor = Color(0xFFF43F5E),
                                        isDark = true,
                                        label = null,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.updateFeedback(item.id, FeedbackType.LIKE) {
                                                viewModel.showSnackbar(snackbarHostState, "Preferences updated")
                                            }
                                        }
                                    )

                                    // Dislike Button
                                    AnimatedFeedbackButton(
                                        isActive = item.feedback == FeedbackType.DISLIKE,
                                        activeIcon = Icons.Filled.ThumbDown,
                                        inactiveIcon = Icons.Outlined.ThumbDown,
                                        activeColor = BrandPrimary,
                                        isDark = true,
                                        label = null,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.updateFeedback(item.id, FeedbackType.DISLIKE) {
                                                viewModel.showSnackbar(snackbarHostState, "Preferences updated")
                                            }
                                        }
                                    )

                                    // Download Button
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.1f), CircleShape)
                                            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                                            .bounceClick {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                viewModel.downloadWallpaper(
                                                    wallpaperId = item.wallpaper.id,
                                                    onSuccess = {
                                                        viewModel.showSnackbar(snackbarHostState, "Saved to gallery")
                                                    },
                                                    onError = { error ->
                                                        viewModel.showSnackbar(snackbarHostState, error)
                                                    }
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = "Download",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Simulated iOS Home Indicator at the very bottom
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 8.dp)
                                .size(width = 140.dp, height = 5.dp)
                                .background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(2.5.dp))
                        )
                    }
                }

                // Close and toggle buttons at the top of the dialog
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Close button (Left)
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                            .bounceClick { selectedWallpaper = null },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Visibility Toggle button (Right)
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                            .bounceClick { showOverlay = !showOverlay },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (showOverlay) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle Overlay",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryItemRow(
    item: HistoryItemUiState,
    isDark: Boolean,
    onThumbnailClick: () -> Unit,
    onLikeClick: () -> Unit,
    onDislikeClick: () -> Unit,
    onDownloadClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val imageModel = if (File(item.localCroppedPath).exists()) {
            File(item.localCroppedPath)
        } else {
            when {
                item.wallpaper.thumbnailUrl.isNotEmpty() -> item.wallpaper.thumbnailUrl
                item.wallpaper.url.contains("images.unsplash.com") -> "${item.wallpaper.url}&w=200&q=80"
                item.wallpaper.url.contains("bing.com") -> "${item.wallpaper.url}&w=200"
                else -> item.wallpaper.url
            }
        }

        GlideImage(
            imageModel = { imageModel },
            imageOptions = ImageOptions(
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center
            ),
            modifier = Modifier
                .width(80.dp)
                .height(120.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                .bounceClick(onClick = onThumbnailClick)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .height(120.dp)
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = item.wallpaper.category.ifEmpty { "Wallpaper" }.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = getOnboardingTextPrimary(isDark),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = "Applied date",
                        tint = getOnboardingTextSecondary(isDark).copy(alpha = 0.8f),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = item.appliedAt,
                        style = MaterialTheme.typography.bodySmall,
                        color = getOnboardingTextSecondary(isDark),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedFeedbackButton(
                    isActive = item.feedback == FeedbackType.LIKE,
                    activeIcon = Icons.Filled.Favorite,
                    inactiveIcon = Icons.Outlined.FavoriteBorder,
                    activeColor = Color(0xFFF43F5E),
                    isDark = isDark,
                    label = null,
                    onClick = onLikeClick
                )

                AnimatedFeedbackButton(
                    isActive = item.feedback == FeedbackType.DISLIKE,
                    activeIcon = Icons.Filled.ThumbDown,
                    inactiveIcon = Icons.Outlined.ThumbDown,
                    activeColor = BrandPrimary,
                    isDark = isDark,
                    label = null,
                    onClick = onDislikeClick
                )

                Spacer(modifier = Modifier.weight(1f))

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            getOnboardingCardBackground(isDark),
                            CircleShape
                        )
                        .border(
                            width = 1.dp,
                            color = getOnboardingCardBorder(isDark),
                            shape = CircleShape
                        )
                        .bounceClick(onClick = onDownloadClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download",
                        tint = getOnboardingTextPrimary(isDark),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimatedFeedbackButton(
    isActive: Boolean,
    activeIcon: androidx.compose.ui.graphics.vector.ImageVector,
    inactiveIcon: androidx.compose.ui.graphics.vector.ImageVector,
    activeColor: Color,
    isDark: Boolean,
    label: String?,
    onClick: () -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = if (isActive) activeColor.copy(alpha = 0.2f) else Color.Transparent,
        animationSpec = tween(200),
        label = "container_color"
    )
    
    val contentColor by animateColorAsState(
        targetValue = if (isActive) activeColor else getOnboardingTextSecondary(isDark),
        animationSpec = tween(200),
        label = "content_color"
    )

    if (label != null) {
        Row(
            modifier = Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    if (isActive) activeColor.copy(alpha = 0.2f) else getOnboardingCardBackground(isDark),
                    RoundedCornerShape(18.dp)
                )
                .border(
                    width = 1.dp,
                    color = if (isActive) activeColor.copy(alpha = 0.4f) else getOnboardingCardBorder(isDark),
                    shape = RoundedCornerShape(18.dp)
                )
                .bounceClick(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isActive) activeIcon else inactiveIcon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
        }
    } else {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (isActive) activeColor.copy(alpha = 0.2f) else getOnboardingCardBackground(isDark),
                    CircleShape
                )
                .border(
                    width = 1.dp,
                    color = if (isActive) activeColor.copy(alpha = 0.4f) else getOnboardingCardBorder(isDark),
                    shape = CircleShape
                )
                .bounceClick(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isActive) activeIcon else inactiveIcon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

