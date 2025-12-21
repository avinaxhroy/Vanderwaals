package me.avinas.vanderwaals.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import me.avinas.vanderwaals.ui.theme.components.*

@Composable
fun ConfirmationGalleryScreen(
    onContinue: () -> Unit,
    onBack: () -> Unit,
    viewModel: ConfirmationGalleryViewModel = hiltViewModel()
) {
    val displayedWallpapers by viewModel.displayedWallpapers.collectAsState()
    val likedWallpapers by viewModel.likedWallpapers.collectAsState()
    val dislikedWallpapers by viewModel.dislikedWallpapers.collectAsState()
    val canContinue by viewModel.canContinue.collectAsState()
    val finishState by viewModel.finishState.collectAsState()
    
    val isDark = isSystemInDarkTheme()
    val scrollState = rememberLazyGridState()

    // Count rated items
    val ratedCount = likedWallpapers.size + dislikedWallpapers.size
    
    // Create ratings map for UI simplification
    // true = like, false = dislike, null = unrated
    // This is derived state for UI
    
    // Handle Finish State side effects
    LaunchedEffect(finishState) {
        if (finishState is FinishState.Success) {
            onContinue()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Premium Background
            PremiumBackground(
                modifier = Modifier.fillMaxSize(),
                isDark = isDark
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                 OnboardingTopAppBar(
                    onBack = onBack,
                    showBack = true,
                    title = {
                         Column(horizontalAlignment = Alignment.CenterHorizontally) {
                             Text(
                                 text = "Refine Your Taste",
                                 style = MaterialTheme.typography.titleMedium,
                                 fontWeight = FontWeight.Bold,
                                 color = if (isDark) Color.White else Color(0xFF111827)
                             )
                             Text(
                                 text = "${likedWallpapers.size}/4 likes needed",
                                 style = MaterialTheme.typography.bodySmall,
                                 color = if (isDark) Color.White.copy(alpha = 0.6f) else Color(0xFF6B7280)
                             )
                         }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.refreshWallpapers() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = if (isDark) Color.White else Color(0xFF111827)
                            )
                        }
                    }
                )
                
                // Content
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (displayedWallpapers.isEmpty()) {
                        // Empty State / Loading
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = if (isDark) me.avinas.vanderwaals.ui.theme.InfoColorDark else MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            state = scrollState,
                            columns = GridCells.Adaptive(minSize = 150.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    text = "Tap to like, hold to hide.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF4B5563),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(bottom = 16.dp, start = 32.dp, end = 32.dp)
                                )
                            }
                            
                            items(displayedWallpapers) { wallpaper ->
                                val isLiked = likedWallpapers.contains(wallpaper.id)
                                val isDisliked = dislikedWallpapers.contains(wallpaper.id)
                                val rating = when {
                                    isLiked -> true
                                    isDisliked -> false
                                    else -> null
                                }
                                
                                WallpaperRatingCard(
                                    wallpaper = wallpaper,
                                    rating = rating,
                                    onRate = { liked -> 
                                        if (liked) {
                                            if (isLiked) viewModel.toggleLike(wallpaper.id) // Toggle off
                                            else viewModel.toggleLike(wallpaper.id) // Toggle on
                                        } else {
                                            if (isDisliked) viewModel.markDislike(wallpaper.id) // Toggle off (assuming markDislike toggles or we rely on logic)
                                            // VM logic check needed. Usually distinct like/dislike calls.
                                            else viewModel.markDislike(wallpaper.id)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                
                // Bottom Bar
                GlassSheet(
                    modifier = Modifier.fillMaxWidth()
                ) {
                     Button(
                        onClick = { 
                             viewModel.finishOnboarding()
                        },
                        enabled = canContinue && finishState !is FinishState.Initializing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDark) me.avinas.vanderwaals.ui.theme.InfoColorDark else MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        )
                    ) {
                        if (finishState is FinishState.Initializing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White
                            )
                        } else {
                            Text(
                                text = "Finish Setup",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun WallpaperRatingCard(
    wallpaper: WallpaperMetadata,
    rating: Boolean?,
    onRate: (Boolean) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.65f),
        contentPadding = PaddingValues(0.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(
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
                    .then(if (rating == false) Modifier.background(Color.Black.copy(alpha = 0.5f)) else Modifier),
                contentScale = ContentScale.Crop,
                alpha = if (rating == false) 0.4f else 1f
            )
            
            // Rating Indicator Overlay
            androidx.compose.animation.AnimatedVisibility(
                visible = rating != null,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            when(rating) {
                                true -> Color.Black.copy(alpha = 0.5f) // Dark bg for heart
                                false -> Color.Transparent
                                null -> Color.Transparent
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (rating == true) Icons.Default.Favorite else Icons.Default.Close,
                        contentDescription = null,
                        tint = if (rating == true) Color(0xFFEC4899) else Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            
            // Selection Border (if liked)
            if (rating == true) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            width = 3.dp, 
                            color = if (isDark) me.avinas.vanderwaals.ui.theme.InfoColorDark else me.avinas.vanderwaals.ui.theme.LightPrimary,
                            shape = RoundedCornerShape(16.dp)
                        )
                )
            }
        }
    }
}
