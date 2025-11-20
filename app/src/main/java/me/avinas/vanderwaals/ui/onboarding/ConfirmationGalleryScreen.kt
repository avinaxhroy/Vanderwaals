package me.avinas.vanderwaals.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.skydoves.landscapist.glide.GlideImage
import com.skydoves.landscapist.ImageOptions
import me.avinas.vanderwaals.core.SmartCrop
import me.avinas.vanderwaals.core.SmartCropTransformation
import me.avinas.vanderwaals.data.entity.WallpaperMetadata

/**
 * Confirmation Gallery Screen - Third screen in personalize flow.
 * 
 * Displays 12 diverse wallpapers from top 20 matches.
 * User provides initial feedback:
 * - Tap to like (shows heart)
 * - Long-press to dislike (shows X)
 * - Must like 3+ to continue
 * 
 * **Preference Learning:**
 * On continue:
 * 1. Average embeddings of liked wallpapers
 * 2. Initialize preference vector
 * 3. Store feedback in database
 * 
 * **UI:**
 * - 2-column grid
 * - Each card shows thumbnail with overlay icons
 * - Continue button enabled after 3+ likes
 * 
 * @param onContinue Callback when user continues to settings
 * @param onBackPressed Callback when back button is pressed
 * @param viewModel ViewModel managing gallery state
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmationGalleryScreen(
    onContinue: () -> Unit,
    onBackPressed: () -> Unit = {},
    viewModel: ConfirmationGalleryViewModel = hiltViewModel()
) {
    // Handle system back button
    androidx.activity.compose.BackHandler {
        android.util.Log.d("ConfirmationGalleryScreen", "BackHandler triggered!")
        onBackPressed()
    }
    
    val displayedWallpapers by viewModel.displayedWallpapers.collectAsState()
    val likedWallpapers by viewModel.likedWallpapers.collectAsState()
    val dislikedWallpapers by viewModel.dislikedWallpapers.collectAsState()
    val canContinue by viewModel.canContinue.collectAsState()
    val finishState by viewModel.finishState.collectAsState()
    
    // Navigate when initialization succeeds
    LaunchedEffect(finishState) {
        if (finishState is FinishState.Success) {
            onContinue()
        }
    }
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = {
                        android.util.Log.d("ConfirmationGalleryScreen", "Back icon clicked!")
                        onBackPressed()
                    }) {
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
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(24.dp)
                ) {
                    Button(
                        onClick = {
                            // Pass all wallpapers for embedding lookup
                            viewModel.finishOnboarding(displayedWallpapers)
                        },
                        enabled = canContinue && finishState !is FinishState.Initializing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        if (finishState is FinishState.Initializing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(
                                text = "Continue",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    if (!canContinue) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val remaining = 4 - likedWallpapers.size
                        Text(
                            text = "Like at least $remaining more wallpaper${if (remaining > 1) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
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
                    center = Offset(size.width * 0.8f, size.height * 0.2f),
                    radius = size.minDimension * 0.4f
                )
                drawCircle(
                    color = secondaryColor.copy(alpha = 0.08f),
                    center = Offset(size.width * 0.2f, size.height * 0.5f),
                    radius = size.minDimension * 0.3f
                )
                drawCircle(
                    color = tertiaryColor.copy(alpha = 0.08f),
                    center = Offset(size.width * 0.8f, size.height * 0.8f),
                    radius = size.minDimension * 0.35f
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title with Refresh Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pick Your Favorites",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    
                    // Refresh Button
                    IconButton(
                        onClick = { viewModel.refreshWallpapers() },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh wallpapers",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                Text(
                    text = "Tap to like, long-press to dislike",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Check if wallpapers are available
                when {
                    displayedWallpapers.isEmpty() -> {
                        // Empty state - show loading or error
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    text = "Loading wallpapers...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    else -> {
                        // Gallery Grid
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(displayedWallpapers) { wallpaper ->
                                WallpaperCard(
                                    wallpaper = wallpaper,
                                    isLiked = likedWallpapers.contains(wallpaper.id),
                                    isDisliked = dislikedWallpapers.contains(wallpaper.id),
                                    onLike = { viewModel.toggleLike(wallpaper.id) },
                                    onDislike = { viewModel.markDislike(wallpaper.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Error Snackbar
        if (finishState is FinishState.Error) {
            Snackbar(
                modifier = Modifier
                    .padding(16.dp),
                action = {
                    TextButton(onClick = { viewModel.resetFinishState() }) {
                        Text("Dismiss")
                    }
                }
            ) {
                Text((finishState as FinishState.Error).message)
            }
        }
    }
}

/**
 * Wallpaper card with like/dislike interactions.
 * 
 * @param wallpaper Wallpaper metadata
 * @param isLiked Whether wallpaper is liked
 * @param isDisliked Whether wallpaper is disliked
 * @param onLike Like callback
 * @param onDislike Dislike callback
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WallpaperCard(
    wallpaper: WallpaperMetadata,
    isLiked: Boolean,
    isDisliked: Boolean,
    onLike: () -> Unit,
    onDislike: () -> Unit
) {
    // Get screen dimensions for smart crop
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val screenHeight = configuration.screenHeightDp
    
    // Calculate card dimensions (2 columns with spacing)
    // Use 9:16 aspect ratio for better wallpaper preview (matches phone screen proportions)
    val cardWidth = (screenWidth - 48 - 12) / 2 // padding + spacing
    val cardHeight = (cardWidth / 0.5625f).toInt() // aspect ratio 9:16 = 0.5625
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(9f / 16f) // 9:16 aspect ratio for phone-like preview
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onLike,
                onLongClick = onDislike
            ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Wallpaper Thumbnail
            GlideImage(
                imageModel = { wallpaper.thumbnailUrl.ifEmpty { wallpaper.url } },
                modifier = Modifier.fillMaxSize(),
                imageOptions = ImageOptions(contentScale = ContentScale.Crop)
            )
            
            // Like Icon (Top Right)
            if (isLiked) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Liked",
                        modifier = Modifier.padding(8.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                // Subtle overlay for unliked state to encourage interaction
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(40.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FavoriteBorder,
                        contentDescription = "Not liked",
                        tint = Color.White
                    )
                }
            }
            
            // Dislike Icon (Top Left)
            if (isDisliked) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Disliked",
                        modifier = Modifier.padding(8.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
