package me.avinas.vanderwaals.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import android.app.WallpaperManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.bumptech.glide.request.RequestOptions
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.glide.GlideImage
import me.avinas.vanderwaals.R
import me.avinas.vanderwaals.core.SmartCropTransformation
import me.avinas.vanderwaals.core.getDeviceScreenSize
import java.io.File

/**
 * Compose screen for main wallpaper preview (primary user interface).
 * 
 * Design philosophy: Minimal UI, wallpaper takes 90% of screen.
 * 
 * **Layout:**
 * - Full-screen current wallpaper preview (zoomable)
 * - Tap anywhere to show/hide bottom sheet overlay
 * - No permanent UI chrome or navigation bars
 * 
 * **Bottom Sheet Overlay:**
 * - Large "Change Now" FAB with sparkle icon
 * - Two secondary buttons: "History" | "Settings"
 * - Source credit text: "From dharmx/walls" or "Bing Daily - Winter Berries"
 * - Swipe down to dismiss
 * 
 * **Interactions:**
 * - Tap: Toggle overlay visibility
 * - Swipe up: Show overlay
 * - Swipe down: Hide overlay
 * - Long press: Quick actions menu (like, dislike, download)
 * - Pinch zoom: Zoom into wallpaper preview
 * 
 * Leverages Paperize's existing:
 * - Landscapist Glide for image loading
 * - Zoomable for pinch-to-zoom
 * - Material 3 bottom sheet components
 * 
 * @see MainViewModel
 * @see me.avinas.vanderwaals.ui.history.HistoryScreen
 * @see me.avinas.vanderwaals.ui.settings.SettingsScreen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val currentWallpaper by viewModel.currentWallpaper.collectAsState()
    val showOverlay by viewModel.showOverlay.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    // Get device screen dimensions for SmartCrop
    val context = LocalContext.current
    val wallpaperManager = remember { WallpaperManager.getInstance(context) }
    val desiredWidth = wallpaperManager.desiredMinimumWidth
    val desiredHeight = wallpaperManager.desiredMinimumHeight
    val fallbackSize = remember { getDeviceScreenSize(context) }
    val screenWidth = if (desiredWidth > 0) desiredWidth else fallbackSize.width
    val screenHeight = if (desiredHeight > 0) desiredHeight else fallbackSize.height

    // Haptic feedback
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                viewModel.toggleOverlay()
            }
    ) {
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val errorMessage by viewModel.errorMessage.collectAsState()

        LaunchedEffect(errorMessage) {
            errorMessage?.let {
                snackbarHostState.showSnackbar(it)
                viewModel.clearErrorMessage()
            }
        }
        // Full-screen wallpaper background with crossfade animation
        Crossfade(
            targetState = currentWallpaper,
            animationSpec = tween(durationMillis = 300),
            label = "wallpaper_transition"
        ) { wallpaper ->
            if (wallpaper != null) {
                // CRITICAL: Load the cropped file created by Worker for pixel-perfect preview matching
                // File path: cache/wallpapers/{wallpaperId}_cropped.jpg
                val croppedFile = java.io.File(context.cacheDir, "wallpapers/${wallpaper.id}_cropped.jpg")
                val imageSource = if (croppedFile.exists()) {
                    croppedFile.absolutePath  // Load pre-cropped file
                } else {
                    wallpaper.url  // Fallback to URL if cropped file doesn't exist yet
                }
                
                GlideImage(
                    imageModel = { imageSource },
                    imageOptions = ImageOptions(
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center
                    ),
                    requestOptions = {
                        // No transformation needed - file is already cropped!
                        if (croppedFile.exists()) {
                            RequestOptions()  // Just load the cropped file as-is
                        } else {
                            // Fallback: transform the URL (for first load before Worker runs)
                            // CRITICAL: Remember this transformation to avoid recreation on recomposition
                            RequestOptions()
                                .override(com.bumptech.glide.request.target.Target.SIZE_ORIGINAL)
                                .transform(
                                    SmartCropTransformation(
                                        targetWidth = screenWidth,
                                        targetHeight = screenHeight
                                    )
                                )
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    loading = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    },
                    failure = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            Text(
                                text = "Failed to load wallpaper",
                                modifier = Modifier.align(Alignment.Center),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                )
            } else {
                // Placeholder when no wallpaper is set
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "No wallpaper set",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Tap \"Change Now\" to get started",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Vanderwaals branding logo - premium pill-shaped floating card
        AnimatedVisibility(
            visible = !showOverlay && currentWallpaper != null,
            enter = fadeIn(animationSpec = tween(600)) + slideInVertically(
                initialOffsetY = { -it / 2 },
                animationSpec = tween(600, easing = FastOutSlowInEasing)
            ) + scaleIn(initialScale = 0.85f, animationSpec = tween(600)),
            exit = fadeOut(animationSpec = tween(350)) + slideOutVertically(
                targetOffsetY = { -it / 2 },
                animationSpec = tween(350)
            ) + scaleOut(targetScale = 0.9f, animationSpec = tween(350)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding() // Add status bar padding for edge-to-edge
                .padding(top = 32.dp) // Increased top padding to ensure logo clears deep cutouts
        ) {
            // Premium 3D effect with enhanced shadow layers
            Box(
                modifier = Modifier
                    .width(240.dp)  // Reduced width for better proportions
                    .height(70.dp)  // Reduced height to match
            ) {
                // Shadow layer 1 (deep background glow) - OPTIMIZED: Single blur layer
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .offset(y = 8.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF9333EA).copy(alpha = 0.35f),
                                    Color.Transparent
                                ),
                                radius = 300f
                            ),
                            shape = RoundedCornerShape(44.dp)
                        )
                        .blur(16.dp) // Reduced from 3 layers to 1 for performance
                )
                
                // Main pill-shaped card with vibrant gradient
                Card(
                    modifier = Modifier.matchParentSize(),
                    shape = RoundedCornerShape(44.dp),  // PILL SHAPE - circular ends!
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Transparent
                    ),
                    border = BorderStroke(
                        width = 2.5.dp,
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFFFBBF24).copy(alpha = 0.7f),  // Gold glow left
                                Color(0xFFE879F9).copy(alpha = 0.8f),  // Fuchsia glow
                                Color.White.copy(alpha = 0.6f),         // White center
                                Color(0xFFA78BFA).copy(alpha = 0.8f),  // Purple glow
                                Color(0xFF60A5FA).copy(alpha = 0.7f)   // Blue glow right
                            )
                        )
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFF8B5CF6),  // Vibrant purple
                                        Color(0xFFD946EF),  // Hot fuchsia
                                        Color(0xFFEC4899),  // Bright pink
                                        Color(0xFFF43F5E)   // Vibrant red-pink
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        // Enhanced shine overlay with gradient
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.25f),
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.08f)
                                        )
                                    )
                                )
                        )
                        
                        // Horizontal shimmer effect
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.White.copy(alpha = 0.15f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                        
                        // Logo with perfect spacing
                        Image(
                            painter = painterResource(id = R.drawable.vanderwaals_logo),
                            contentDescription = "Vanderwaals",
                            modifier = Modifier
                                .fillMaxWidth(0.88f)  // Maximum visibility
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            contentScale = ContentScale.Fit,
                            alpha = 1.0f
                        )
                    }
                }
            }
        }

        // Bottom sheet overlay
        AnimatedVisibility(
            visible = showOverlay,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(200)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(200)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            // Glassmorphism Bottom Sheet
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding() // Push up from nav bar
                    .padding(16.dp)
            ) {
                // Glass Background Layer
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            color = Color(0xFF0F0F15).copy(alpha = 0.75f),
                            shape = RoundedCornerShape(32.dp)
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.15f),
                                    Color.White.copy(alpha = 0.05f)
                                )
                            ),
                            shape = RoundedCornerShape(32.dp)
                        )
                ) {}

                // Content Layer
                Column(
                    modifier = Modifier
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Primary Action: Change Now (Premium Gradient)
                    Button(
                        onClick = { 
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            viewModel.changeNow() 
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(68.dp)
                            .shadow(
                                elevation = 16.dp,
                                shape = RoundedCornerShape(24.dp),
                                ambientColor = Color(0xFF7C3AED).copy(alpha = 0.5f),
                                spotColor = Color(0xFF7C3AED).copy(alpha = 0.5f)
                            ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            disabledContainerColor = Color.Gray.copy(alpha = 0.3f)
                        ),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(24.dp),
                        enabled = !isLoading
                    ) {
                        // Gradient Container
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xFF8B5CF6), // Violet
                                            Color(0xFFD946EF)  // Fuchsia
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    color = Color.White,
                                    strokeWidth = 3.dp
                                )
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(26.dp),
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Change Wallpaper",
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        ),
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    // Secondary Actions: History & Settings
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // History Button
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .clickable {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    viewModel.toggleOverlay()
                                    onNavigateToHistory()
                                }
                                .border(
                                    1.dp,
                                    Color.White.copy(alpha = 0.1f),
                                    RoundedCornerShape(20.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = Color.White.copy(alpha = 0.9f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "History",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                        }

                        // Settings Button
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .clickable {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    viewModel.toggleOverlay()
                                    onNavigateToSettings()
                                }
                                .border(
                                    1.dp,
                                    Color.White.copy(alpha = 0.1f),
                                    RoundedCornerShape(20.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = Color.White.copy(alpha = 0.9f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Settings",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }

                    // Feedback buttons - CRITICAL for continuous learning
                    if (currentWallpaper != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Do you like this wallpaper?",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                            
                            // Like button
                            IconButton(
                                onClick = { 
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    viewModel.likeCurrentWallpaper()
                                    scope.launch { snackbarHostState.showSnackbar("Marked as liked") }
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = "Like",
                                    tint = Color(0xFFEC4899),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            
                            // Dislike button
                            IconButton(
                                onClick = { 
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    viewModel.dislikeCurrentWallpaper()
                                    scope.launch { snackbarHostState.showSnackbar("Marked as disliked") }
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ThumbDown,
                                    contentDescription = "Dislike",
                                    tint = Color(0xFF60A5FA),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    // Source attribution
                    currentWallpaper?.let { wallpaper ->
                        Text(
                            text = "From ${wallpaper.source}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        // Live Wallpaper Detection Dialogs
        val showLiveWallpaperDialog by viewModel.showLiveWallpaperDialog.collectAsState()
        val liveWallpaperInfo by viewModel.liveWallpaperInfo.collectAsState()
        val showInstructionsDialog by viewModel.showInstructionsDialog.collectAsState()

        // Main live wallpaper blocked dialog
        if (showLiveWallpaperDialog) {
            me.avinas.vanderwaals.ui.components.LiveWallpaperBlockedDialog(
                serviceName = liveWallpaperInfo.first,
                packageName = liveWallpaperInfo.second,
                onOpenSettings = {
                    viewModel.onSettingsOpened()
                },
                onShowInstructions = {
                    viewModel.showInstructions()
                },
                onDismiss = {
                    viewModel.dismissLiveWallpaperDialog()
                }
            )
        }

        // Instructions dialog
        if (showInstructionsDialog) {
            me.avinas.vanderwaals.ui.components.LiveWallpaperInstructionsDialog(
                onRetrySettings = {
                    viewModel.onSettingsOpened()
                },
                onDismiss = {
                    viewModel.dismissInstructionsDialog()
                }
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }
}
