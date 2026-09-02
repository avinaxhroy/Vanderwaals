package me.avinas.vanderwaals.ui.onboarding

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.FilterVintage
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import me.avinas.vanderwaals.ui.settings.RadicalIconBadge
import me.avinas.vanderwaals.ui.settings.RadicalPalette
import me.avinas.vanderwaals.ui.settings.RadicalProgressMeter
import me.avinas.vanderwaals.ui.settings.RadicalTactileBackdrop
import me.avinas.vanderwaals.ui.settings.RadicalTactileCard
import me.avinas.vanderwaals.ui.theme.LocalThemeIsDark
import me.avinas.vanderwaals.ui.theme.PlayfairDisplayFamily

@Composable
fun UploadWallpaperScreen(
    onMatchesFound: () -> Unit,
    onBackPressed: () -> Unit = {},
    viewModel: UploadWallpaperViewModel = hiltViewModel(),
    currentStep: Int = 2,
    totalSteps: Int = 6
) {
    val uiState by viewModel.uploadState.collectAsState()
    val isDark = LocalThemeIsDark.current
    val metrics = rememberOnboardingLayoutMetrics()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.uploadWallpaper(it) }
    }

    LaunchedEffect(uiState) {
        if (uiState is UploadState.Success) onMatchesFound()
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
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            OnboardingTopBar(isDark = isDark, metrics = metrics, onBack = onBackPressed)
        }
    ) { paddingValues ->
        Box(Modifier.fillMaxSize()) {
            RadicalTactileBackdrop(isDark = isDark, modifier = Modifier.matchParentSize())

            LazyColumn(
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding() + 8.dp,
                    bottom = paddingValues.calculateBottomPadding() + 24.dp,
                    start = metrics.horizontalPadding,
                    end = metrics.horizontalPadding
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = metrics.maxContentWidth)
                    .align(Alignment.TopCenter)
            ) {
                item {
                    Column(Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
                        OnboardingStepIndicator(
                            currentStep = currentStep - 1,
                            totalSteps = totalSteps,
                            isDark = isDark,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        OnboardingHeader(
                            title = "Pick your style",
                            subtitle = "Upload a photo or pick an aesthetic below.",
                            isDark = isDark,
                            accentColor = RadicalPalette.CyberMagenta
                        )
                    }
                }

                item {
                    HeroUploadDropzoneCard(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            launcher.launch("image/*")
                        },
                        isDark = isDark
                    )
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.10f)
                        )

                        Text(
                            text = "OR PICK AN AESTHETIC",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.2.sp,
                            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                        )

                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.10f)
                        )
                    }
                }

                item {
                    val styles = WallpaperStyle.values().toList()
                    val chunked = styles.chunked(2)

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        chunked.forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowItems.forEach { style ->
                                    DistinctAestheticCard(
                                        style = style,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.selectSampleWallpaper(style)
                                        },
                                        isDark = isDark,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    AnimatedVisibility(
        visible = uiState is UploadState.Extracting || uiState is UploadState.FindingMatches,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "ai_modal_radar")
        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(2200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "ai_radar_rotation"
        )

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.68f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            RadicalTactileCard(
                isDark = isDark,
                modifier = Modifier.widthIn(max = 360.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(70.dp)
                                .rotate(rotation)
                                .clip(CircleShape)
                                .border(
                                    1.2.dp,
                                    Brush.sweepGradient(
                                        listOf(
                                            Color.Transparent,
                                            RadicalPalette.CyberMagenta.copy(alpha = 0.3f),
                                            RadicalPalette.CyberMagenta,
                                            Color.Transparent
                                        )
                                    ),
                                    CircleShape
                                )
                        )

                        RadicalIconBadge(
                            icon = Icons.Default.Search,
                            accentColor = RadicalPalette.CyberMagenta,
                            isDark = isDark,
                            size = 50.dp,
                            iconSize = 25.dp
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (uiState is UploadState.Extracting) "Analyzing Photo" else "Matching Wallpapers",
                            fontFamily = PlayfairDisplayFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = if (isDark) RadicalPalette.DarkCardTextPrimary else RadicalPalette.LightCardTextPrimary,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = if (uiState is UploadState.Extracting)
                                "Building your taste profile on-device…"
                            else
                                "Finding matching wallpapers in catalog…",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDark) RadicalPalette.DarkCardTextSecondary else RadicalPalette.LightCardTextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }

                    RadicalProgressMeter(
                        progress = if (uiState is UploadState.Extracting) 0.45f else 0.85f,
                        label = if (uiState is UploadState.Extracting) "Visual Analysis" else "Catalog Matching",
                        sublabel = "On-Device AI",
                        isDark = isDark,
                        accentColor = RadicalPalette.CyberMagenta,
                        isLoading = true
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroUploadDropzoneCard(
    onClick: () -> Unit,
    isDark: Boolean
) {
    RadicalTactileCard(
        isDark = isDark,
        modifier = Modifier.bounceClick(onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadicalIconBadge(
                        icon = Icons.Default.AddPhotoAlternate,
                        accentColor = RadicalPalette.CyberMagenta,
                        isDark = isDark,
                        size = 48.dp,
                        iconSize = 24.dp
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Upload From Gallery",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color(0xFF1C1917) else Color(0xFFFFFFFF)
                        )
                        Text(
                            text = "Pick a favorite photo to train your taste profile",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isDark) Color(0xFF44403C) else Color(0xFFA7F3D0)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(
                            if (isDark) Color(0xFFFFE4EC)
                            else Color(0xFF03261C)
                        )
                        .border(
                            1.dp,
                            if (isDark) Color(0xFFFECDD3) else Color(0xFF0D5E47),
                            RoundedCornerShape(99.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Browse",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isDark) Color(0xFFBE123C) else Color(0xFF6EE7B7)
                    )
                }
            }
        }
    }
}

@Composable
private fun DistinctAestheticCard(
    style: WallpaperStyle,
    onClick: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val styleColor = when (style) {
        WallpaperStyle.NATURE -> RadicalPalette.EmeraldJade
        WallpaperStyle.MINIMAL -> RadicalPalette.PlatinumSilver
        WallpaperStyle.DARK -> Color(0xFF4F46E5)
        WallpaperStyle.ABSTRACT -> RadicalPalette.AmethystPurple
        WallpaperStyle.COLORFUL -> RadicalPalette.RadiantAmber
        WallpaperStyle.ANIME -> RadicalPalette.CyberMagenta
    }

    val styleSubtitle = when (style) {
        WallpaperStyle.NATURE -> "Lush landscapes"
        WallpaperStyle.MINIMAL -> "Clean geometry"
        WallpaperStyle.DARK -> "OLED midnight"
        WallpaperStyle.ABSTRACT -> "Surreal forms"
        WallpaperStyle.COLORFUL -> "Rich colors"
        WallpaperStyle.ANIME -> "Stylized aesthetic"
    }

    val styleIcon = when (style) {
        WallpaperStyle.NATURE -> Icons.Default.Eco
        WallpaperStyle.MINIMAL -> Icons.Default.Grain
        WallpaperStyle.DARK -> Icons.Default.DarkMode
        WallpaperStyle.ABSTRACT -> Icons.Default.FilterVintage
        WallpaperStyle.COLORFUL -> Icons.Default.Palette
        WallpaperStyle.ANIME -> Icons.Default.Animation
    }

    RadicalTactileCard(
        isDark = isDark,
        modifier = modifier.bounceClick(onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RadicalIconBadge(
                icon = styleIcon,
                accentColor = styleColor,
                isDark = isDark,
                size = 38.dp,
                iconSize = 20.dp
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = style.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color(0xFF1C1917) else Color(0xFFFFFFFF),
                    fontSize = 14.sp
                )
                Text(
                    text = styleSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = if (isDark) Color(0xFF44403C) else Color(0xFFA7F3D0),
                    fontSize = 11.sp
                )
            }
        }
    }
}
