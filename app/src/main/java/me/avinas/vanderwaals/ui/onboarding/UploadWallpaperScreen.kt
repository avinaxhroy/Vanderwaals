package me.avinas.vanderwaals.ui.onboarding

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import me.avinas.vanderwaals.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
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

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.uploadWallpaper(it) }
    }

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
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // Transparent top navigation with back button only
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(metrics.topBarHeight),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = metrics.maxContentWidth)
                        .padding(horizontal = metrics.horizontalPadding),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = getOnboardingTextPrimary(isDark)
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            OnboardingBackdrop(
                isDark = isDark,
                modifier = Modifier.fillMaxSize()
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(metrics.styleColumns),
                horizontalArrangement = Arrangement.spacedBy(metrics.cardSpacing),
                verticalArrangement = Arrangement.spacedBy(metrics.cardSpacing),
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding() + 12.dp,
                    bottom = paddingValues.calculateBottomPadding() + 24.dp,
                    start = metrics.horizontalPadding,
                    end = metrics.horizontalPadding
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = metrics.maxContentWidth)
                    .align(Alignment.TopCenter)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.Start
                    ) {
                        OnboardingStepIndicator(
                            currentStep = currentStep - 1,
                            totalSteps = totalSteps,
                            isDark = isDark,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // Step number above title
                        Text(
                            text = "Step $currentStep of $totalSteps",
                            style = MaterialTheme.typography.labelMedium,
                            color = BrandPrimary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        // Title
                        Text(
                            text = "Show Us What You Love",
                            style = LuxeHeadlineStyle,
                            color = getOnboardingTextPrimary(isDark),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // Subheadline below title
                        Text(
                            text = "Upload a wallpaper or pick a style you love",
                            style = LuxeBodyStyle,
                            color = getOnboardingTextSecondary(isDark)
                        )
                    }
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(modifier = Modifier.height(metrics.sectionSpacing))
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    UploadArea(
                        isDark = isDark,
                        metrics = metrics,
                        onClick = { launcher.launch("image/*") }
                    )
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(modifier = Modifier.height(metrics.sectionSpacing))
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = "OR CHOOSE A STYLE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = getOnboardingTextSecondary(isDark),
                        letterSpacing = 1.4.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                items(WallpaperStyle.values().toList()) { style ->
                    StyleCard(
                        style = style,
                        onClick = { viewModel.selectSampleWallpaper(style) },
                        isDark = isDark,
                        metrics = metrics
                    )
                }
            }
        }
    }

    AnimatedVisibility(
        visible = uiState is UploadState.Extracting || uiState is UploadState.FindingMatches,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(getOnboardingCardBackground(isDark))
                    .border(
                        width = 1.dp,
                        color = getOnboardingCardBorder(isDark),
                        shape = RoundedCornerShape(28.dp)
                    )
                    .shadow(16.dp, RoundedCornerShape(28.dp))
                    .padding(horizontal = 40.dp, vertical = 36.dp)
            ) {
                // Premium animated loading indicator
                Box(
                    modifier = Modifier.size(72.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Gradient ring
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                Brush.sweepGradient(
                                    colors = listOf(
                                        BrandPrimary.copy(alpha = 0.3f),
                                        BrandAccent.copy(alpha = 0.3f),
                                        BrandPrimary.copy(alpha = 0.1f)
                                    )
                                )
                            )
                    )

                    CircularProgressIndicator(
                        color = BrandPrimary,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(56.dp)
                    )

                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = BrandPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (uiState is UploadState.Extracting) "Analyzing Wallpaper" else "Finding Matches",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = getOnboardingTextPrimary(isDark)
                    )
                    Text(
                        text = if (uiState is UploadState.Extracting) "Extracting visual features..." else "Searching our collection...",
                        style = MaterialTheme.typography.bodySmall,
                        color = getOnboardingTextSecondary(isDark),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun UploadArea(
    isDark: Boolean,
    metrics: OnboardingLayoutMetrics,
    onClick: () -> Unit
) {
    val borderColor = getOnboardingCardBorder(isDark)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (metrics.compactHeight) 160.dp else 196.dp)
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(metrics.cardCornerRadius),
                ambientColor = BrandPrimary.copy(alpha = 0.1f),
                spotColor = Color.Transparent
            )
            .clip(RoundedCornerShape(metrics.cardCornerRadius))
            .background(getOnboardingCardBackground(isDark))
            .border(
                width = 1.5.dp,
                color = borderColor,
                shape = RoundedCornerShape(metrics.cardCornerRadius)
            )
            .bounceClick { onClick() }
            .padding(if (metrics.compactWidth) 20.dp else 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Premium upload icon with gradient background
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                BrandPrimary.copy(alpha = 0.15f),
                                BrandAccent.copy(alpha = 0.1f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = BrandPrimary
                )
            }

            Text(
                text = "Upload Wallpaper",
                style = if (metrics.compactWidth) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = getOnboardingTextPrimary(isDark)
            )

            Text(
                text = "Tap to browse your gallery",
                style = MaterialTheme.typography.bodySmall,
                color = getOnboardingTextSecondary(isDark)
            )
        }
    }
}

@Composable
private fun StyleCard(
    style: WallpaperStyle,
    onClick: () -> Unit,
    isDark: Boolean,
    metrics: OnboardingLayoutMetrics
) {
    val styleColor = when(style) {
        WallpaperStyle.NATURE -> Color(0xFF22C55E)
        WallpaperStyle.MINIMAL -> Color(0xFF9CA3AF)
        WallpaperStyle.DARK -> Color(0xFF3F3F46)
        WallpaperStyle.ABSTRACT -> Color(0xFF8B5CF6)
        WallpaperStyle.COLORFUL -> Color(0xFFF59E0B)
        WallpaperStyle.ANIME -> Color(0xFFEC4899)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(if (metrics.compactWidth) 1.05f else 1f)
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(metrics.cardCornerRadius),
                ambientColor = styleColor.copy(alpha = 0.15f),
                spotColor = Color.Transparent
            )
            .clip(RoundedCornerShape(metrics.cardCornerRadius))
            .background(getOnboardingCardBackground(isDark))
            .border(
                width = 1.dp,
                color = getOnboardingCardBorder(isDark),
                shape = RoundedCornerShape(metrics.cardCornerRadius)
            )
            .bounceClick { onClick() }
    ) {
        // Premium gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            styleColor.copy(alpha = 0.08f),
                            styleColor.copy(alpha = 0.02f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(if (metrics.compactWidth) 44.dp else 54.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                styleColor.copy(alpha = 0.15f),
                                styleColor.copy(alpha = 0.08f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = style.icon,
                    contentDescription = null,
                    modifier = Modifier.size(if (metrics.compactWidth) 20.dp else 26.dp),
                    tint = styleColor
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(if (metrics.compactWidth) 12.dp else 14.dp)
        ) {
            Text(
                text = style.displayName,
                style = if (metrics.compactWidth) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = getOnboardingTextPrimary(isDark)
            )
        }
    }
}

private val WallpaperStyle.icon: ImageVector
    get() = when (this) {
        WallpaperStyle.NATURE -> Icons.Default.Eco
        WallpaperStyle.MINIMAL -> Icons.Default.FilterVintage
        WallpaperStyle.DARK -> Icons.Default.DarkMode
        WallpaperStyle.ABSTRACT -> Icons.Default.AutoAwesome
        WallpaperStyle.COLORFUL -> Icons.Default.Palette
        WallpaperStyle.ANIME -> Icons.Default.Animation
    }