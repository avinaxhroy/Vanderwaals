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

    androidx.activity.compose.BackHandler {
        onNavigateBack()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = if (isDark) BackgroundDark else BackgroundLight,
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
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDark) TextPrimaryDark else TextPrimaryLight
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = if (isDark) TextPrimaryDark else TextPrimaryLight
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isDark) BackgroundDark else BackgroundLight,
                    scrolledContainerColor = if (isDark) BackgroundDark else BackgroundLight,
                    titleContentColor = if (isDark) TextPrimaryDark else TextPrimaryLight,
                    navigationIconContentColor = if (isDark) TextPrimaryDark else TextPrimaryLight
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
                    CircularProgressIndicator(color = BrandPrimary)
                }
            }
            is HistoryViewModel.HistoryUiState.Success -> {
                val historyGroups = state.groups
                if (historyGroups.isEmpty()) {
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
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDark) TextPrimaryDark else TextPrimaryLight
                            )
                            Text(
                                text = "Wallpapers you've used will appear here",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isDark) TextSecondaryDark else TextSecondaryLight
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        modifier = Modifier
                            .fillMaxSize(),
                        columns = GridCells.Adaptive(minSize = 340.dp),
                        contentPadding = PaddingValues(
                            top = paddingValues.calculateTopPadding() + 8.dp,
                            bottom = paddingValues.calculateBottomPadding() + 32.dp,
                            start = 16.dp,
                            end = 16.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        historyGroups.forEach { (dateHeader, items) ->
                            item(key = "header_$dateHeader", span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    text = dateHeader.uppercase(),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) TextSecondaryDark else TextSecondaryLight,
                                    letterSpacing = 1.2.sp,
                                    modifier = Modifier.padding(bottom = 8.dp, start = 8.dp)
                                )
                            }
                            
                            items.forEach { historyItem ->
                                item(key = "item_${historyItem.id}") {
                                    PremiumSettingsCard(
                                        isDark = isDark,
                                        contentPadding = PaddingValues(0.dp)
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
                        .windowInsetsPadding(WindowInsets.safeDrawing)
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
                .width(72.dp)
                .height(110.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onThumbnailClick)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .height(110.dp)
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.wallpaper.category.ifEmpty { "Wallpaper" }.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) TextPrimaryDark else TextPrimaryLight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.appliedAt,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) TextSecondaryDark else TextSecondaryLight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(0.dp, Alignment.Start),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedFeedbackButton(
                    isActive = item.feedback == FeedbackType.LIKE,
                    activeIcon = Icons.Filled.Favorite,
                    inactiveIcon = Icons.Outlined.FavoriteBorder,
                    activeColor = Color(0xFFF43F5E), // Rose color
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

                IconButton(
                    onClick = onDownloadClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download",
                        tint = if (isDark) TextPrimaryDark else TextPrimaryLight,
                        modifier = Modifier.size(22.dp)
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
        targetValue = if (isActive) activeColor.copy(alpha = 0.15f) else Color.Transparent,
        animationSpec = tween(200),
        label = "container_color"
    )
    
    val contentColor by animateColorAsState(
        targetValue = if (isActive) activeColor else (if (isDark) TextSecondaryDark else TextSecondaryLight),
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
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(containerColor)
        ) {
            Icon(
                imageVector = if (isActive) activeIcon else inactiveIcon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun PremiumSettingsCard(
    isDark: Boolean,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val containerColor = if (isDark) SurfaceOverlayDark else SurfaceLight
    val borderColor = if (isDark) SurfaceHighlightDark else SurfaceHighlightLight
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(modifier = Modifier.padding(contentPadding)) {
            content()
        }
    }
}

@Composable
private fun SettingsDivider(
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    HorizontalDivider(
        modifier = modifier.padding(start = 16.dp, end = 16.dp),
        color = if (isDark) SurfaceHighlightDark else SurfaceHighlightLight,
        thickness = 1.dp
    )
}
