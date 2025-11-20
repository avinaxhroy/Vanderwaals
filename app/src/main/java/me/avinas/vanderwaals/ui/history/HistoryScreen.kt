package me.avinas.vanderwaals.ui.history

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    val screenSize = remember { getDeviceScreenSize(context) }
    val screenWidth = screenSize.width
    val screenHeight = screenSize.height

    // Handle system back button
    androidx.activity.compose.BackHandler {
        onNavigateBack()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
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
                modifier = Modifier.statusBarsPadding()
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            // Ambient background for glassmorphism
            val primaryColor = MaterialTheme.colorScheme.primary
            val secondaryColor = MaterialTheme.colorScheme.secondary
            val tertiaryColor = MaterialTheme.colorScheme.tertiary
            
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = primaryColor.copy(alpha = 0.08f),
                    center = Offset(size.width * 0.2f, size.height * 0.2f),
                    radius = size.minDimension * 0.3f
                )
                drawCircle(
                    color = secondaryColor.copy(alpha = 0.08f),
                    center = Offset(size.width * 0.8f, size.height * 0.5f),
                    radius = size.minDimension * 0.4f
                )
                drawCircle(
                    color = tertiaryColor.copy(alpha = 0.08f),
                    center = Offset(size.width * 0.3f, size.height * 0.8f),
                    radius = size.minDimension * 0.35f
                )
            }

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
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .navigationBarsPadding(),
                    contentPadding = PaddingValues(vertical = 16.dp, horizontal = 20.dp),
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
                                    viewModel.updateFeedback(historyItem.id, FeedbackType.LIKE) {
                                        viewModel.showSnackbar(snackbarHostState, "Preferences updated")
                                    }
                                },
                                onDislikeClick = {
                                    viewModel.updateFeedback(historyItem.id, FeedbackType.DISLIKE) {
                                        viewModel.showSnackbar(snackbarHostState, "Preferences updated")
                                    }
                                },
                                onDownloadClick = {
                                    viewModel.downloadWallpaper(historyItem.wallpaper.id) {
                                        viewModel.showSnackbar(snackbarHostState, "Saved to gallery")
                                    }
                                }
                            )
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

@Composable
private fun HistoryItemCard(
    item: HistoryItemUiState,
    onThumbnailClick: () -> Unit,
    onLikeClick: () -> Unit,
    onDislikeClick: () -> Unit,
    onDownloadClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .border(
                BorderStroke(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.02f)
                        )
                    )
                ),
                RoundedCornerShape(16.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
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
                item.wallpaper.thumbnailUrl
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
                    activeColor = Color(0xFFEC4899), // Pink
                    label = if (item.feedback == FeedbackType.LIKE) "Liked" else null,
                    onClick = onLikeClick
                )

                // Dislike button
                AnimatedFeedbackButton(
                    isActive = item.feedback == FeedbackType.DISLIKE,
                    activeIcon = Icons.Filled.ThumbDown,
                    inactiveIcon = Icons.Outlined.ThumbDown,
                    activeColor = Color(0xFF60A5FA), // Blue
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
