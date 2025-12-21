package me.avinas.vanderwaals.ui.onboarding

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.FilterVintage
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import me.avinas.vanderwaals.ui.theme.components.*

@Composable
fun UploadWallpaperScreen(
    onMatchesFound: () -> Unit,
    onBackPressed: () -> Unit = {},
    viewModel: UploadWallpaperViewModel = hiltViewModel()
) {
    val uiState by viewModel.uploadState.collectAsState()
    val isDark = isSystemInDarkTheme()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.uploadWallpaper(it) }
    }

    // Handle side effects
    LaunchedEffect(uiState) {
        if (uiState is UploadState.Success) {
            onMatchesFound()
        }
        if (uiState is UploadState.Error) {
             val error = (uiState as UploadState.Error).message
             scope.launch {
                snackbarHostState.showSnackbar(error)
                viewModel.resetState()
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                    onBack = onBackPressed,
                    showBack = true // Allow back now
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 24.dp)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Your Style",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else Color(0xFF111827)
                    )
                    
                    Text(
                        text = "Upload a wallpaper you love, or pick a sample style below.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF4B5563),
                        modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
                    )
                    
                    // Upload Area
                    UploadSection(
                        isDark = isDark,
                        onClick = { launcher.launch("image/*") }
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Style Samples
                    LabelSectionHeader(title = "OR CHOOSE A STYLE")
                    
                    StylesGrid(
                        styles = WallpaperStyle.values().toList(),
                        onStyleSelected = { viewModel.selectSampleWallpaper(it) },
                        isDark = isDark
                    )
                }
            }
            
            // Loading Overlay
            AnimatedVisibility(
                visible = uiState is UploadState.Extracting || uiState is UploadState.FindingMatches,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(enabled = false) {}, // Block touches
                    contentAlignment = Alignment.Center
                ) {
                    GlassCard(
                        modifier = Modifier.size(160.dp),
                        contentPadding = PaddingValues(24.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            CircularProgressIndicator(
                                color = if (isDark) me.avinas.vanderwaals.ui.theme.InfoColorDark else MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (uiState is UploadState.Extracting) "Analyzing..." else "Finding Matches...",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDark) Color.White else Color.Black
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UploadSection(
    isDark: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isDark) Color.White.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.2f)
    
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(2.dp) // Gap for border inside glass
                .drawBehind {
                    drawRoundRect(
                        color = borderColor,
                        style = Stroke(
                            width = 4.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 20f), 0f)
                        ),
                        cornerRadius = CornerRadius(16.dp.toPx())
                    )
                }
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            if (isDark) me.avinas.vanderwaals.ui.theme.InfoColorDark.copy(alpha = 0.2f) 
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = if (isDark) me.avinas.vanderwaals.ui.theme.InfoColorDark else MaterialTheme.colorScheme.primary
                    )
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Upload Image",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else Color(0xFF111827)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap to browse gallery",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDark) Color.White.copy(alpha = 0.6f) else Color(0xFF6B7280)
                    )
                }
            }
        }
    }
}

@Composable
fun StylesGrid(
    styles: List<WallpaperStyle>,
    onStyleSelected: (WallpaperStyle) -> Unit,
    isDark: Boolean
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        items(styles) { style ->
            StyleSampleCard(
                style = style,
                onClick = { onStyleSelected(style) },
                isDark = isDark
            )
        }
    }
}

@Composable
fun StyleSampleCard(
    style: WallpaperStyle,
    onClick: () -> Unit,
    isDark: Boolean
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable { onClick() },
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
             // We don't have images for styles readily available in the enum, 
             // but we can use Icons or just colors/gradients.
             // Using Icons for now as in the original design.
             
             Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                when(style) {
                                    WallpaperStyle.NATURE -> Color(0xFF4CAF50)
                                    WallpaperStyle.MINIMAL -> Color(0xFF9E9E9E)
                                    WallpaperStyle.DARK -> Color(0xFF212121)
                                    WallpaperStyle.ABSTRACT -> Color(0xFF9C27B0)
                                    WallpaperStyle.COLORFUL -> Color(0xFFFF9800)
                                    WallpaperStyle.ANIME -> Color(0xFFE91E63)
                                }.copy(alpha = 0.3f),
                                Color.Transparent
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
             ) {
                 Icon(
                     imageVector = style.icon,
                     contentDescription = null,
                     modifier = Modifier.size(48.dp),
                     tint = if (isDark) Color.White else Color.Black.copy(alpha = 0.7f)
                 )
             }
            
            Text(
                text = style.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color.White else Color.Black,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            )
        }
    }
}

private val WallpaperStyle.icon: ImageVector
    get() = when (this) {
        WallpaperStyle.NATURE -> Icons.Default.Eco
        WallpaperStyle.MINIMAL -> Icons.Default.FilterVintage // Approximations
        WallpaperStyle.DARK -> Icons.Default.DarkMode
        WallpaperStyle.ABSTRACT -> Icons.Default.AutoAwesome
        WallpaperStyle.COLORFUL -> Icons.Default.Palette
        WallpaperStyle.ANIME -> Icons.Default.Animation
    }
