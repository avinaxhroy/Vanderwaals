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
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
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
import me.avinas.vanderwaals.ui.theme.components.GlassSheet
import androidx.compose.animation.core.animateDpAsState
import me.avinas.vanderwaals.ui.theme.*

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
    val screenSize = remember { getDeviceScreenSize(context) }
    val screenWidth = screenSize.width
    val screenHeight = screenSize.height

    // Haptic feedback
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    // Animate blur radius for background
    val blurRadius by animateDpAsState(
        targetValue = if (showOverlay) 20.dp else 0.dp,
        animationSpec = tween(durationMillis = 300),
        label = "background_blur"
    )
    
    // Theme check for blobs
    val isDark = me.avinas.vanderwaals.ui.theme.LocalThemeIsDark.current

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
        val uiState by viewModel.currentWallpaper.collectAsState()
        
        when (val state = uiState) {
            is MainViewModel.MainUiState.Loading -> {
                // Show loading indicator or keep previous frame
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
            is MainViewModel.MainUiState.Success -> {
                val currentWallpaper = state.wallpaper
                Crossfade(
                    targetState = currentWallpaper,
                    animationSpec = tween(durationMillis = 300),
                    label = "wallpaper_transition"
                ) { wallpaper ->
                    if (wallpaper != null) {
                        val croppedFile = java.io.File(context.cacheDir, "wallpapers/${wallpaper.id}_cropped.jpg")
                        val imageSource = if (croppedFile.exists()) {
                            croppedFile.absolutePath
                        } else {
                            wallpaper.url
                        }
                        
                        GlideImage(
                            imageModel = { imageSource },
                            imageOptions = ImageOptions(
                                contentScale = ContentScale.Crop,
                                alignment = Alignment.Center
                            ),
                            requestOptions = {
                                if (croppedFile.exists()) {
                                    RequestOptions()
                                } else {
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
                            modifier = Modifier
                                .fillMaxSize()
                                .blur(blurRadius),
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
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
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
            }
        }

        // Vanderwaals branding logo
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
                .statusBarsPadding() // Ensure logo doesn't overlap status bar
                .padding(top = 32.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(260.dp)
                    .height(80.dp)
            ) {
                // Outer Glow removed as per UI polish request
                
                // Glass Logo Container
                me.avinas.vanderwaals.ui.theme.components.GlassCard(
                    modifier = Modifier.matchParentSize(),
                    shape = RoundedCornerShape(44.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.vanderwaals_logo),
                            contentDescription = "Vanderwaals",
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                            contentScale = ContentScale.Fit,
                            alpha = 0.95f
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                // Dynamic Background Blobs removed as per UI polish request

                // Glassmorphism Bottom Sheet
                GlassSheet(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Content Layer
                    Column(
                        modifier = Modifier
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // Primary Action: Change Now (Premium Gradient)
                        // Primary Action: Change Now (Premium Gradient)
                        me.avinas.vanderwaals.ui.theme.components.GradientButton(
                            text = "Change Wallpaper",
                            onClick = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                viewModel.changeNow()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp),
                            enabled = !isLoading,
                            gradient = Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF8B5CF6), // Violet
                                    Color(0xFFD946EF)  // Fuchsia
                                )
                            ),
                            shape = RoundedCornerShape(24.dp),
                            textStyle = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            icon = {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = Color.White,
                                        strokeWidth = 3.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                        tint = Color.White
                                    )
                                }
                            }
                        )

                        // Secondary Actions: History & Settings
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // History Button using GlassCard
                            me.avinas.vanderwaals.ui.theme.components.GlassCard(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(64.dp)
                                    .clickable {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                        viewModel.toggleOverlay()
                                        onNavigateToHistory()
                                    },
                                contentPadding = PaddingValues(0.dp),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.History,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = if (isDark) Color.White else Color(0xFF111827)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "History",
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                            color = if (isDark) Color.White else Color(0xFF111827)
                                        )
                                    }
                                }
                            }

                            // Settings Button using GlassCard
                            me.avinas.vanderwaals.ui.theme.components.GlassCard(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(64.dp)
                                    .clickable {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                        viewModel.toggleOverlay()
                                        onNavigateToSettings()
                                    },
                                contentPadding = PaddingValues(0.dp),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = if (isDark) Color.White else Color(0xFF111827)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Settings",
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                            color = if (isDark) Color.White else Color(0xFF111827)
                                        )
                                    }
                                }
                            }
                        }

                        // Feedback buttons
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
                                        viewModel.likeCurrentWallpaper(
                                            onSuccess = { scope.launch { snackbarHostState.showSnackbar("Marked as liked") } },
                                            onError = { error -> scope.launch { snackbarHostState.showSnackbar(error) } }
                                        )
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
                                        viewModel.dislikeCurrentWallpaper(
                                            onSuccess = { scope.launch { snackbarHostState.showSnackbar("Marked as disliked") } },
                                            onError = { error -> scope.launch { snackbarHostState.showSnackbar(error) } }
                                        )
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
                                
                                Spacer(modifier = Modifier.width(8.dp))
                                
                                // Download button - Strongest learning signal
                                IconButton(
                                    onClick = { 
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        viewModel.downloadCurrentWallpaper(
                                            onSuccess = { scope.launch { snackbarHostState.showSnackbar("Saved to gallery") } },
                                            onError = { error -> scope.launch { snackbarHostState.showSnackbar(error) } }
                                        )
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "Download to gallery",
                                        tint = Color(0xFF22C55E), // Green for save/download
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }

                        // Source attribution
                        (currentWallpaper as? MainViewModel.MainUiState.Success)?.wallpaper?.let { wallpaper ->
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
                        
                        // Manual bottom padding for navigation bar
                        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
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
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        )
    }
}
