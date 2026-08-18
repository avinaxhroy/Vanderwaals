package me.avinas.vanderwaals.ui.history

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.ThumbDown
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.glide.GlideImage
import java.io.File
import me.avinas.vanderwaals.domain.usecase.FeedbackType
import me.avinas.vanderwaals.ui.onboarding.bounceClick
import me.avinas.vanderwaals.ui.onboarding.rememberOnboardingLayoutMetrics
import me.avinas.vanderwaals.ui.settings.RadicalButtonVariant
import me.avinas.vanderwaals.ui.settings.RadicalIconBadge
import me.avinas.vanderwaals.ui.settings.RadicalPalette
import me.avinas.vanderwaals.ui.settings.RadicalSectionHeader
import me.avinas.vanderwaals.ui.settings.RadicalTactileBackdrop
import me.avinas.vanderwaals.ui.settings.RadicalTactileButton
import me.avinas.vanderwaals.ui.settings.RadicalTactileCard
import me.avinas.vanderwaals.ui.settings.RadicalWatermarkBadge
import me.avinas.vanderwaals.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.historyGroups.collectAsState()
    val isApplying by viewModel.isApplying.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedWallpaper by remember { mutableStateOf<HistoryItemUiState?>(null) }

    val haptic = LocalHapticFeedback.current
    val isDark = LocalThemeIsDark.current
    val metrics = rememberOnboardingLayoutMetrics()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    androidx.activity.compose.BackHandler {
        onNavigateBack()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        RadicalTactileBackdrop(
            isDark = isDark,
            modifier = Modifier.matchParentSize()
        )

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
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
                        Column {
                            Text(
                                text = "History",
                                fontFamily = PlayfairDisplayFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp,
                                color = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A),
                                letterSpacing = (-0.3).sp
                            )
                            Text(
                                text = "Applied Wallpapers & Curation Log",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                                letterSpacing = 0.4.sp
                            )
                        }
                    },
                    navigationIcon = {
                        Box(
                            modifier = Modifier
                                .padding(start = 12.dp, end = 4.dp)
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
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color(0xFF1E2433),
                                                Color(0xFF111622)
                                            )
                                        )
                                    } else {
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.White,
                                                Color(0xFFF1F5F9)
                                            )
                                        )
                                    }
                                )
                                .border(
                                    width = 1.dp,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = if (isDark) 0.25f else 0.9f),
                                            if (isDark) Color.Black.copy(alpha = 0.5f) else Color(0xFFCBD5E1)
                                        )
                                    ),
                                    shape = CircleShape
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(bounded = true),
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onNavigateBack()
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
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = if (isDark) RadicalPalette.DarkCanvasBase.copy(alpha = 0.88f) else RadicalPalette.LightCanvasBase.copy(alpha = 0.88f),
                        titleContentColor = if (isDark) Color.White else Color(0xFF0F172A),
                        navigationIconContentColor = if (isDark) Color.White else Color(0xFF0F172A)
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
                        RadicalTactileCard(
                            isDark = isDark,
                            modifier = Modifier
                                .widthIn(max = 280.dp)
                                .padding(16.dp),
                            contentPadding = PaddingValues(24.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = if (isDark) RadicalPalette.EmeraldJade else RadicalPalette.LightCardTextPrimary,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = "Loading history…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isDark) RadicalPalette.DarkCardTextPrimary else RadicalPalette.LightCardTextPrimary
                                )
                            }
                        }
                    }
                }
                is HistoryViewModel.HistoryUiState.Success -> {
                    val stats = state.stats
                    val filteredGroups = state.filteredGroups
                    val selectedFilter = state.selectedFilter

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = metrics.maxContentWidth)
                            .padding(horizontal = metrics.horizontalPadding),
                        contentPadding = PaddingValues(
                            top = paddingValues.calculateTopPadding() + 8.dp,
                            bottom = paddingValues.calculateBottomPadding() + 36.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (stats.totalCount > 0) {
                            item(key = "filter_tabs") {
                                HistoryMinimalFilterTabs(
                                    selectedFilter = selectedFilter,
                                    stats = stats,
                                    onFilterSelected = { filter ->
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.setFilter(filter)
                                    },
                                    isDark = isDark
                                )
                            }
                        }

                        if (filteredGroups.isEmpty()) {
                            item(key = "empty_state") {
                                HistoryMinimalEmptyCard(
                                    filter = selectedFilter,
                                    isDark = isDark,
                                    onClearFilter = {
                                        viewModel.setFilter(HistoryFilter.ALL)
                                    },
                                    onNavigateBack = onNavigateBack
                                )
                            }
                        } else {
                            filteredGroups.forEach { (dateHeader, items) ->
                                item(key = "header_$dateHeader") {
                                    val headerAccent = when (dateHeader) {
                                        "Today" -> RadicalPalette.EmeraldJade
                                        "Yesterday" -> RadicalPalette.RoyalIndigo
                                        else -> RadicalPalette.RadiantAmber
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp, bottom = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadicalSectionHeader(
                                            title = dateHeader,
                                            isDark = isDark,
                                            accentColor = headerAccent,
                                            modifier = Modifier.weight(1f)
                                        )

                                        Text(
                                            text = if (items.size == 1) "1 item" else "${items.size} items",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                    }
                                }

                                items(items, key = { it.id }) { historyItem ->
                                    HistoryMinimalCard(
                                        item = historyItem,
                                        isDark = isDark,
                                        onOpen = {
                                            selectedWallpaper = historyItem
                                        },
                                        onLike = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.updateFeedback(historyItem.id, FeedbackType.LIKE) {
                                                viewModel.showSnackbar(snackbarHostState, "Liked. Showing more wallpapers like this")
                                            }
                                        },
                                        onDislike = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.updateFeedback(historyItem.id, FeedbackType.DISLIKE) {
                                                viewModel.showSnackbar(snackbarHostState, "Hidden. Showing fewer wallpapers like this")
                                            }
                                        },
                                        onDownload = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.downloadWallpaper(
                                                historyId = historyItem.id,
                                                wallpaperId = historyItem.wallpaper.id,
                                                onSuccess = { viewModel.showSnackbar(snackbarHostState, "Saved to gallery") },
                                                onError = { error -> viewModel.showSnackbar(snackbarHostState, error) }
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        item(key = "watermark") {
                            Spacer(modifier = Modifier.height(6.dp))
                            RadicalWatermarkBadge(
                                isDark = isDark,
                                version = me.avinas.vanderwaals.BuildConfig.VERSION_NAME
                            )
                        }
                    }
                }
            }
        }
    }

    selectedWallpaper?.let { item ->
        HistoryPreviewDialog(
            item = item,
            isDark = isDark,
            isApplying = isApplying == item.wallpaper.id,
            onDismiss = { selectedWallpaper = null },
            onLike = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.updateFeedback(item.id, FeedbackType.LIKE) {
                    viewModel.showSnackbar(snackbarHostState, "Liked. Showing more wallpapers like this")
                }
            },
            onDislike = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.updateFeedback(item.id, FeedbackType.DISLIKE) {
                    viewModel.showSnackbar(snackbarHostState, "Hidden. Showing fewer wallpapers like this")
                }
            },
            onDownload = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.downloadWallpaper(
                    historyId = item.id,
                    wallpaperId = item.wallpaper.id,
                    onSuccess = { viewModel.showSnackbar(snackbarHostState, "Saved to gallery") },
                    onError = { error -> viewModel.showSnackbar(snackbarHostState, error) }
                )
            },
            onApply = { target ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.applyWallpaper(
                    wallpaper = item.wallpaper,
                    targetScreen = target,
                    onSuccess = {
                        viewModel.showSnackbar(snackbarHostState, "Wallpaper applied")
                        selectedWallpaper = null
                    },
                    onError = { error -> viewModel.showSnackbar(snackbarHostState, error) }
                )
            }
        )
    }
}


@Composable
private fun HistoryMinimalFilterTabs(
    selectedFilter: HistoryFilter,
    stats: HistoryStats,
    onFilterSelected: (HistoryFilter) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val trayShape = RoundedCornerShape(16.dp)
    val segmentShape = RoundedCornerShape(12.dp)

    val trayBackground = if (isDark) {
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

    val trayBorder = if (isDark) {
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
                Color.White.copy(alpha = 0.12f),
                RadicalPalette.LightCardBorderBottom
            )
        )
    }

    val trayElevation = if (isDark) 8.dp else 6.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = trayElevation,
                shape = trayShape,
                ambientColor = if (isDark) Color.Black.copy(alpha = 0.50f) else Color(0xFF022C22).copy(alpha = 0.25f),
                spotColor = if (isDark) Color.Black.copy(alpha = 0.40f) else Color(0xFF022C22).copy(alpha = 0.18f)
            )
            .clip(trayShape)
            .background(trayBackground)
            .border(width = 1.dp, brush = trayBorder, shape = trayShape)
            .padding(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            HistoryFilter.entries.forEach { filter ->
                val isSelected = selectedFilter == filter
                val count = when (filter) {
                    HistoryFilter.ALL -> stats.totalCount
                    HistoryFilter.LIKED -> stats.likedCount
                    HistoryFilter.HIDDEN -> stats.dislikedCount
                    HistoryFilter.SAVED -> stats.savedCount
                }

                val elevation by animateDpAsState(
                    targetValue = if (isSelected) 3.dp else 0.dp,
                    animationSpec = tween(180),
                    label = "filterElev_${filter.name}"
                )

                val selectedSegmentBrush = if (isDark) {
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF1E2433),
                            Color(0xFF111622)
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(
                            Color.White,
                            Color(0xFFF1F5F9)
                        )
                    )
                }

                val selectedBorderBrush = if (isDark) {
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.35f),
                            Color.Black.copy(alpha = 0.25f)
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(
                            Color.White,
                            Color(0xFFCBD5E1)
                        )
                    )
                }

                val textColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        if (isDark) Color.White else Color(0xFF064E3B)
                    } else {
                        if (isDark) Color(0xFF57534E) else Color(0xFFA7F3D0)
                    },
                    animationSpec = tween(180),
                    label = "filterTextCol_${filter.name}"
                )

                val badgeBackground by animateColorAsState(
                    targetValue = if (isSelected) {
                        if (isDark) {
                            when (filter) {
                                HistoryFilter.ALL -> Color.White.copy(alpha = 0.18f)
                                HistoryFilter.LIKED -> RadicalPalette.CoralRose
                                HistoryFilter.HIDDEN -> Color(0xFF64748B)
                                HistoryFilter.SAVED -> RadicalPalette.EmeraldJade
                            }
                        } else {
                            when (filter) {
                                HistoryFilter.ALL -> Color(0xFF064E3B).copy(alpha = 0.12f)
                                HistoryFilter.LIKED -> RadicalPalette.CoralRose.copy(alpha = 0.20f)
                                HistoryFilter.HIDDEN -> Color(0xFF64748B).copy(alpha = 0.20f)
                                HistoryFilter.SAVED -> Color(0xFF059669).copy(alpha = 0.20f)
                            }
                        }
                    } else {
                        if (isDark) {
                            Color(0xFF1C1917).copy(alpha = 0.08f)
                        } else {
                            Color.White.copy(alpha = 0.12f)
                        }
                    },
                    animationSpec = tween(180),
                    label = "filterBadgeBg_${filter.name}"
                )

                val badgeTextColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        if (isDark) {
                            Color.White
                        } else {
                            when (filter) {
                                HistoryFilter.ALL -> Color(0xFF064E3B)
                                HistoryFilter.LIKED -> RadicalPalette.CoralRose
                                HistoryFilter.HIDDEN -> Color(0xFF334155)
                                HistoryFilter.SAVED -> Color(0xFF065F46)
                            }
                        }
                    } else {
                        if (isDark) {
                            Color(0xFF57534E)
                        } else {
                            Color(0xFFA7F3D0)
                        }
                    },
                    animationSpec = tween(180),
                    label = "filterBadgeText_${filter.name}"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .shadow(
                            elevation = elevation,
                            shape = segmentShape,
                            ambientColor = Color.Black.copy(alpha = 0.25f),
                            spotColor = Color.Black.copy(alpha = 0.18f)
                        )
                        .clip(segmentShape)
                        .background(
                            if (isSelected) selectedSegmentBrush else Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                        )
                        .then(
                            if (isSelected) {
                                Modifier.border(
                                    width = 1.dp,
                                    brush = selectedBorderBrush,
                                    shape = segmentShape
                                )
                            } else Modifier
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true),
                            onClick = {
                                if (!isSelected) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onFilterSelected(filter)
                                }
                            }
                        )
                        .semantics {
                            role = Role.Tab
                            contentDescription = "${filter.label} filter, $count items"
                        }
                        .padding(vertical = 9.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = filter.label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(badgeBackground)
                                .padding(horizontal = 5.dp, vertical = 1.5.dp)
                        ) {
                            Text(
                                text = "$count",
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                color = badgeTextColor
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun HistoryMinimalCard(
    item: HistoryItemUiState,
    isDark: Boolean,
    onOpen: () -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    RadicalTactileCard(
        isDark = isDark,
        onClick = onOpen,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(78.dp)
                    .height(112.dp)
                    .shadow(
                        elevation = if (isDark) 3.dp else 4.dp,
                        shape = RoundedCornerShape(12.dp),
                        ambientColor = Color.Black.copy(alpha = 0.30f),
                        spotColor = Color.Black.copy(alpha = 0.20f)
                    )
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isDark) Color(0xFF1E2433) else Color(0xFF02241A))
                    .border(
                        1.dp,
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = if (isDark) 0.45f else 0.35f),
                                Color.Black.copy(alpha = 0.25f)
                            )
                        ),
                        RoundedCornerShape(12.dp)
                    )
            ) {
                val imageModel = when {
                    File(item.localCroppedPath).exists() -> File(item.localCroppedPath)
                    item.wallpaper.thumbnailUrl.isNotEmpty() -> item.wallpaper.thumbnailUrl
                    item.wallpaper.url.contains("images.unsplash.com") -> "${item.wallpaper.url}&w=280&q=80"
                    item.wallpaper.url.contains("bing.com") -> "${item.wallpaper.url}&w=280"
                    else -> item.wallpaper.url
                }

                GlideImage(
                    imageModel = { imageModel },
                    imageOptions = ImageOptions(
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center
                    ),
                    modifier = Modifier.fillMaxSize()
                )

                Canvas(modifier = Modifier.matchParentSize()) {
                    drawLine(
                        color = Color.White.copy(alpha = if (isDark) 0.40f else 0.20f),
                        start = Offset(4f, 1f),
                        end = Offset(size.width - 4f, 1f),
                        strokeWidth = 1f
                    )
                }

                if (item.feedback == FeedbackType.LIKE) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(5.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(RadicalPalette.CoralRose),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = "Liked",
                            tint = Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                } else if (item.feedback == FeedbackType.DISLIKE) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(5.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF475569)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ThumbDown,
                            contentDescription = "Hidden",
                            tint = Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                } else if (item.isDownloaded) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(5.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(RadicalPalette.EmeraldJade),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Saved",
                            tint = Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 112.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = item.wallpaper.category.ifEmpty { "Curated Wallpaper" }.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) RadicalPalette.DarkCardTextPrimary else RadicalPalette.LightCardTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    val sourceName = if (item.wallpaper.source.contains("Bing", ignoreCase = true)) "Bing" else "Curated"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = if (isDark) RadicalPalette.DarkCardTextSecondary else RadicalPalette.LightCardTextSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "${item.appliedAt} • $sourceName",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDark) RadicalPalette.DarkCardTextSecondary else RadicalPalette.LightCardTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HistoryMinimalActionPill(
                        isActive = item.feedback == FeedbackType.LIKE,
                        activeIcon = Icons.Filled.Favorite,
                        inactiveIcon = Icons.Outlined.FavoriteBorder,
                        label = "Like",
                        activeColor = RadicalPalette.CoralRose,
                        isDark = isDark,
                        onClick = onLike
                    )

                    HistoryMinimalActionPill(
                        isActive = item.feedback == FeedbackType.DISLIKE,
                        activeIcon = Icons.Filled.ThumbDown,
                        inactiveIcon = Icons.Outlined.ThumbDown,
                        label = "Hide",
                        activeColor = RadicalPalette.PlatinumSilver,
                        isDark = isDark,
                        onClick = onDislike
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .shadow(
                                elevation = if (item.isDownloaded) 2.dp else 1.dp,
                                shape = CircleShape,
                                ambientColor = Color.Black.copy(alpha = 0.18f)
                            )
                            .clip(CircleShape)
                            .background(
                                if (item.isDownloaded) {
                                    RadicalPalette.EmeraldJade.copy(alpha = if (isDark) 0.25f else 0.9f)
                                } else {
                                    if (isDark) RadicalPalette.DarkCardWell else Color.White.copy(alpha = 0.20f)
                                }
                            )
                            .border(
                                width = 1.dp,
                                brush = if (item.isDownloaded) {
                                    Brush.verticalGradient(listOf(RadicalPalette.EmeraldJade, RadicalPalette.EmeraldJade.copy(alpha = 0.5f)))
                                } else {
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = if (isDark) 0.3f else 0.4f),
                                            Color.Black.copy(alpha = 0.2f)
                                        )
                                    )
                                },
                                shape = CircleShape
                            )
                            .bounceClick(onClick = onDownload)
                            .semantics { role = Role.Button; contentDescription = "Save to gallery" },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (item.isDownloaded) Icons.Default.Check else Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = if (item.isDownloaded) {
                                if (isDark) RadicalPalette.EmeraldJade else Color.White
                            } else {
                                if (isDark) RadicalPalette.DarkCardTextPrimary else Color.White
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryMinimalActionPill(
    isActive: Boolean,
    activeIcon: ImageVector,
    inactiveIcon: ImageVector,
    label: String,
    activeColor: Color,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(99.dp)

    Box(
        modifier = modifier
            .heightIn(min = 32.dp)
            .sizeIn(minHeight = 44.dp, minWidth = 52.dp)
            .shadow(
                elevation = if (isActive) 2.dp else 0.dp,
                shape = shape,
                ambientColor = activeColor.copy(alpha = 0.30f),
                spotColor = activeColor.copy(alpha = 0.20f)
            )
            .clip(shape)
            .background(
                if (isActive) {
                    if (isDark) activeColor.copy(alpha = 0.20f) else activeColor.copy(alpha = 0.85f)
                } else {
                    if (isDark) RadicalPalette.DarkCardWell else Color.White.copy(alpha = 0.15f)
                }
            )
            .border(
                width = 1.dp,
                brush = if (isActive) {
                    Brush.verticalGradient(
                        listOf(activeColor, activeColor.copy(alpha = 0.4f))
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (isDark) 0.25f else 0.30f),
                            Color.Black.copy(alpha = 0.15f)
                        )
                    )
                },
                shape = shape
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = activeColor.copy(alpha = 0.20f)),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            )
            .semantics { role = Role.Button; contentDescription = label }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (isActive) activeIcon else inactiveIcon,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = if (isActive) {
                    if (isDark) activeColor else Color.White
                } else {
                    if (isDark) RadicalPalette.DarkCardTextSecondary else RadicalPalette.LightCardTextSecondary
                }
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = if (isActive) {
                    if (isDark) activeColor else Color.White
                } else {
                    if (isDark) RadicalPalette.DarkCardTextSecondary else RadicalPalette.LightCardTextSecondary
                }
            )
        }
    }
}


@Composable
private fun HistoryMinimalEmptyCard(
    filter: HistoryFilter,
    isDark: Boolean,
    onClearFilter: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    RadicalTactileCard(
        isDark = isDark,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            RadicalIconBadge(
                icon = if (filter != HistoryFilter.ALL) Icons.Default.FilterListOff else Icons.Default.History,
                accentColor = RadicalPalette.EmeraldJade,
                isDark = isDark,
                size = 50.dp,
                iconSize = 24.dp
            )

            Text(
                text = if (filter != HistoryFilter.ALL) "No ${filter.label} Wallpapers" else "No History Yet",
                style = MaterialTheme.typography.titleLarge,
                fontFamily = PlayfairDisplayFamily,
                fontWeight = FontWeight.Bold,
                color = if (isDark) RadicalPalette.DarkCardTextPrimary else RadicalPalette.LightCardTextPrimary,
                textAlign = TextAlign.Center
            )

            Text(
                text = if (filter != HistoryFilter.ALL) {
                    "No items match the \"${filter.label}\" filter."
                } else {
                    "Wallpapers you apply will appear here."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (isDark) RadicalPalette.DarkCardTextSecondary else RadicalPalette.LightCardTextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            if (filter != HistoryFilter.ALL) {
                RadicalTactileButton(
                    text = "Show All",
                    icon = Icons.Default.Collections,
                    onClick = onClearFilter,
                    isDark = isDark,
                    variant = RadicalButtonVariant.Primary
                )
            } else {
                RadicalTactileButton(
                    text = "Back to Home",
                    icon = Icons.Default.Home,
                    onClick = onNavigateBack,
                    isDark = isDark,
                    variant = RadicalButtonVariant.Primary
                )
            }
        }
    }
}


@Composable
private fun HistoryPreviewDialog(
    item: HistoryItemUiState,
    isDark: Boolean,
    isApplying: Boolean,
    onDismiss: () -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onDownload: () -> Unit,
    onApply: (target: String) -> Unit
) {
    var showOverlay by remember { mutableStateOf(true) }
    var showTargetSheet by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { showOverlay = !showOverlay }
                .semantics { contentDescription = "Preview. Tap to toggle controls" }
        ) {
            val imageModel = when {
                File(item.localCroppedPath).exists() -> File(item.localCroppedPath)
                item.wallpaper.thumbnailUrl.isNotEmpty() -> item.wallpaper.thumbnailUrl
                else -> item.wallpaper.url
            }

            GlideImage(
                imageModel = { imageModel },
                imageOptions = ImageOptions(
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center
                ),
                modifier = Modifier.fillMaxSize()
            )

            AnimatedVisibility(
                visible = showOverlay,
                enter = fadeIn(tween(220)),
                exit = fadeOut(tween(180)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(top = 44.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Color.White.copy(alpha = 0.90f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "9:41",
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontWeight = FontWeight.Light,
                                color = Color.White.copy(alpha = 0.96f)
                            ),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Thursday, August 18",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.80f),
                            textAlign = TextAlign.Center
                        )
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 20.dp, start = 16.dp, end = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 380.dp)
                                .shadow(
                                    elevation = 8.dp,
                                    shape = RoundedCornerShape(20.dp),
                                    ambientColor = Color.Black.copy(alpha = 0.60f),
                                    spotColor = Color.Black.copy(alpha = 0.40f)
                                )
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF0F172A).copy(alpha = 0.80f))
                                .border(
                                    width = 1.dp,
                                    brush = Brush.verticalGradient(
                                        listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.08f))
                                    ),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PreviewMinimalActionPod(
                                    isActive = item.feedback == FeedbackType.LIKE,
                                    icon = Icons.Filled.Favorite,
                                    inactiveIcon = Icons.Outlined.FavoriteBorder,
                                    tint = RadicalPalette.CoralRose,
                                    label = "Like",
                                    onClick = onLike
                                )

                                PreviewMinimalActionPod(
                                    isActive = item.feedback == FeedbackType.DISLIKE,
                                    icon = Icons.Filled.ThumbDown,
                                    inactiveIcon = Icons.Outlined.ThumbDown,
                                    tint = Color.White,
                                    label = "Hide",
                                    onClick = onDislike
                                )

                                PreviewMinimalActionPod(
                                    isActive = item.isDownloaded,
                                    icon = Icons.Default.DownloadDone,
                                    inactiveIcon = Icons.Default.Download,
                                    tint = RadicalPalette.EmeraldJade,
                                    label = "Save",
                                    onClick = onDownload
                                )

                                PreviewMinimalActionPod(
                                    isActive = false,
                                    icon = Icons.Default.Wallpaper,
                                    inactiveIcon = Icons.Default.Wallpaper,
                                    tint = RadicalPalette.SapphireBlue,
                                    label = if (isApplying) "Applying..." else "Apply",
                                    onClick = { showTargetSheet = true }
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(Color.Black.copy(alpha = 0.50f))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(99.dp))
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "${item.wallpaper.category.ifEmpty { "Wallpaper" }.replaceFirstChar { it.uppercase() }} • ${item.appliedAt}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.85f),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .shadow(4.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.4f))
                        .clip(CircleShape)
                        .background(Color(0xFF0F172A).copy(alpha = 0.70f))
                        .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                        .bounceClick(onClick = onDismiss)
                        .semantics { role = Role.Button; contentDescription = "Close preview" },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.White
                    )
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .shadow(4.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.4f))
                        .clip(CircleShape)
                        .background(Color(0xFF0F172A).copy(alpha = 0.70f))
                        .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                        .bounceClick { showOverlay = !showOverlay }
                        .semantics { role = Role.Button; contentDescription = if (showOverlay) "Hide overlay" else "Show overlay" },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (showOverlay) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.White
                    )
                }
            }

            if (showTargetSheet) {
                AlertDialog(
                    onDismissRequest = { showTargetSheet = false },
                    title = {
                        Text(
                            text = "Apply Wallpaper",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    },
                    text = {
                        Text(
                            text = "Where would you like to set this wallpaper?",
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    },
                    containerColor = Color(0xFF1E2433),
                    confirmButton = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    showTargetSheet = false
                                    onApply("home")
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = RadicalPalette.SapphireBlue),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
                            ) {
                                Text("Home Screen", fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    showTargetSheet = false
                                    onApply("lock")
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = RadicalPalette.EmeraldJade),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
                            ) {
                                Text("Lock Screen", fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    showTargetSheet = false
                                    onApply("both")
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = RadicalPalette.RoyalIndigo),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
                            ) {
                                Text("Both Screens", fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showTargetSheet = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancel", color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Medium)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun PreviewMinimalActionPod(
    isActive: Boolean,
    icon: ImageVector,
    inactiveIcon: ImageVector,
    tint: Color,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isActive) tint.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.08f)
            )
            .border(
                1.dp,
                if (isActive) tint.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.12f),
                RoundedCornerShape(12.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = tint.copy(alpha = 0.25f)),
                onClick = onClick
            )
            .semantics { role = Role.Button; contentDescription = label }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Icon(
            imageVector = if (isActive) icon else inactiveIcon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (isActive) tint else Color.White.copy(alpha = 0.90f)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}
