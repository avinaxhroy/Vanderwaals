package me.avinas.vanderwaals.ui.history

import androidx.compose.animation.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.bumptech.glide.request.RequestOptions
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.glide.GlideImage
import me.avinas.vanderwaals.core.getDeviceScreenSize
import me.avinas.vanderwaals.domain.usecase.FeedbackType
import java.io.File
import me.avinas.vanderwaals.ui.theme.LiquidGlassBackground
import me.avinas.vanderwaals.ui.theme.components.* // This import might be redundant if specific components are imported, keeping for safety
import me.avinas.vanderwaals.ui.theme.components.GlassCard

/**
 * Compose screen for feedback history display and interaction.
 * 
 * **Layout:**
 * - Chronological list of wallpapers grouped by date
 * - Groups: "Today", "Yesterday", "[Month Year]" (e.g., "January 2024")
 * - Each item shows thumbnail (200×200), timestamp, category
 * - Scrollable with LazyColumnScrollbar for quick navigation
 * 
 * **List Item:**
 * - Thumbnail preview (Landscapist Glide with crossfade)
 * - Applied timestamp: "2 hours ago", "Yesterday at 3:45 PM"
 * - Category badge: Colored chip with category name
 * - Three action buttons: ♡ Like | 👎 Dislike | ⬇ Download
 * - Current feedback state shown (heart/thumbs down filled if already given)
 * 
 * **Interactions:**
 * - Tap thumbnail: Full-screen preview with zoom
 * - Tap Like: Record positive feedback, animate icon, update preferences
 * - Tap Dislike: Record negative feedback, animate icon, update preferences
 * - Tap Download: Save wallpaper to device storage
 * - Long press: Share or delete from history
 * 
 * **Learning Integration:**
 * - Every like/dislike updates preference vector via ProcessFeedbackUseCase
 * - Visual feedback animation (icon bounce, color change)
 * - Queue automatically reranks in background
 * - Snackbar confirmation: "Preferences updated"
 * 
 * **Data Management:**
 * - Keeps last 100 entries, auto-deletes older records
 * - Pull-to-refresh to sync with database
 * - Empty state: "No history yet" with illustration
 * 
 * @see HistoryViewModel
 * @see me.avinas.vanderwaals.domain.usecase.ProcessFeedbackUseCase
 * @see me.avinas.vanderwaals.data.repository.FeedbackRepository
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val historyGroups by viewModel.historyGroups.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedWallpaper by remember { mutableStateOf<HistoryItemUiState?>(null) }
    
    // Get device screen dimensions for SmartCrop
    val context = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val screenSize = remember { getDeviceScreenSize(context) }
    val screenWidth = screenSize.width
    val screenHeight = screenSize.height

    // Handle system back button
    androidx.activity.compose.BackHandler {
        onNavigateBack()
    }

    LiquidGlassBackground {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = Color.Transparent
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
            ) {

                val uiState by viewModel.historyGroups.collectAsState()
            
            // Track scroll state for dynamic TopAppBar background
            val listState = rememberLazyListState()
            val isScrolled by remember {
                derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
            }
            
            // Calculate top padding for content (StatusBar + TopAppBar height)
            // Use safeDrawing to account for cutouts and status bars
            val statusBarHeight = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
            val topBarHeight = 64.dp // Standard M3 TopAppBar height
            val contentTopPadding = statusBarHeight + topBarHeight + 16.dp

            when (val state = uiState) {
                is HistoryViewModel.HistoryUiState.Loading -> {
                    // Loading state
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is HistoryViewModel.HistoryUiState.Success -> {
                    val historyGroups = state.groups
                    if (historyGroups.isEmpty()) {
                        // Empty state
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = "No history yet",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Wallpapers you've used will appear here",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues),
                            contentPadding = PaddingValues(
                                top = contentTopPadding,
                                bottom = 16.dp,
                                start = 20.dp,
                                end = 20.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            historyGroups.forEach { (dateHeader, items) ->
                                item(key = "header_$dateHeader") {
                                    Text(
                                        text = dateHeader,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        letterSpacing = 1.2.dp.value.sp,
                                        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                                    )
                                }

                                items(
                                    items = items,
                                    key = { it.id }
                                ) { historyItem ->
                                        HistoryItemCard(
                                        item = historyItem,
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
                            
                            
                            item {
                                Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                            }
                        }
                    }
                }
            }

            // TopAppBar Overlay
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = isScrolled,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    me.avinas.vanderwaals.ui.theme.components.GlassTopAppBarBackground(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp + WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding())
                    )
                }

                TopAppBar(
                    title = {
                        Text(
                            text = "History",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(vertical = 16.dp)
                )
            }
        }

        // Full-screen preview dialog
        selectedWallpaper?.let { item ->
            Dialog(
                onDismissRequest = { selectedWallpaper = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .clickable { selectedWallpaper = null }
                ) {
                    // Full-screen preview - use local cropped file if available
                    val imageModel = if (File(item.localCroppedPath).exists()) {
                        File(item.localCroppedPath)
                    } else {
                        item.wallpaper.thumbnailUrl
                    }

                    GlideImage(
                        imageModel = { imageModel },
                        imageOptions = ImageOptions(
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.Center
                        ),
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    IconButton(
                        onClick = { selectedWallpaper = null },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
private fun HistoryItemCard(
    item: HistoryItemUiState,
    onThumbnailClick: () -> Unit,
    onLikeClick: () -> Unit,
    onDislikeClick: () -> Unit,
    onDownloadClick: () -> Unit
) {
    LiquidGlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
            // Thumbnail - use local cropped file if available
            val imageModel = if (File(item.localCroppedPath).exists()) {
                File(item.localCroppedPath)
            } else {
                // Smart Thumbnail Logic
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
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onThumbnailClick)
            )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = item.wallpaper.category,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.appliedAt,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                // Like button
                AnimatedFeedbackButton(
                    isActive = item.feedback == FeedbackType.LIKE,
                    activeIcon = Icons.Filled.Favorite,
                    inactiveIcon = Icons.Outlined.FavoriteBorder,
                    activeColor = MaterialTheme.colorScheme.tertiary, // Pink
                    label = if (item.feedback == FeedbackType.LIKE) "Liked" else null,
                    onClick = onLikeClick
                )

                // Dislike button
                AnimatedFeedbackButton(
                    isActive = item.feedback == FeedbackType.DISLIKE,
                    activeIcon = Icons.Filled.ThumbDown,
                    inactiveIcon = Icons.Outlined.ThumbDown,
                    activeColor = MaterialTheme.colorScheme.primary, // Blue/Tan
                    label = if (item.feedback == FeedbackType.DISLIKE) "Disliked" else null,
                    onClick = onDislikeClick
                )

                // Download button
                IconButton(
                    onClick = onDownloadClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
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
    label: String?,
    onClick: () -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = if (isActive) activeColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        animationSpec = tween(200),
        label = "container_color"
    )
    
    val contentColor by animateColorAsState(
        targetValue = if (isActive) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(200),
        label = "content_color"
    )

    if (label != null) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = containerColor,
                contentColor = contentColor
            ),
            shape = RoundedCornerShape(18.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            modifier = Modifier.height(36.dp)
        ) {
            Icon(
                imageVector = if (isActive) activeIcon else inactiveIcon,
                contentDescription = label,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    } else {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = if (isActive) activeIcon else inactiveIcon,
                contentDescription = null,
                tint = contentColor
            )
        }
    }
}
