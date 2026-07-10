package me.avinas.vanderwaals.ui.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import me.avinas.vanderwaals.ui.theme.BrandPrimary
import me.avinas.vanderwaals.ui.theme.BrandAccent
import me.avinas.vanderwaals.ui.theme.ErrorColor
import me.avinas.vanderwaals.ui.theme.LocalThemeIsDark
import me.avinas.vanderwaals.ui.theme.LuxeHeadlineStyle
import me.avinas.vanderwaals.ui.theme.SuccessColor

@Composable
fun InitialSyncScreen(
    onSyncComplete: () -> Unit,
    viewModel: InitialSyncViewModel = hiltViewModel(),
    currentStep: Int = 2,
    totalSteps: Int = 4
) {
    val syncState by viewModel.syncState.collectAsState()
    val wallpaperCount by viewModel.wallpaperCount.collectAsState()
    val isDark = LocalThemeIsDark.current
    val metrics = rememberOnboardingLayoutMetrics()

    LaunchedEffect(Unit) {
        viewModel.startSync()
    }

    LaunchedEffect(syncState) {
        if (syncState is SyncState.Success) {
            onSyncComplete()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Scaffold(
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            OnboardingBackdrop(
                isDark = isDark,
                modifier = Modifier.matchParentSize()
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = metrics.horizontalPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = metrics.maxContentWidth),
                    horizontalAlignment = Alignment.Start
                ) {
                    Spacer(modifier = Modifier.height(paddingValues.calculateTopPadding() + 12.dp))

                    OnboardingStepIndicator(
                        currentStep = currentStep - 1,
                        totalSteps = totalSteps,
                        isDark = isDark,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Text(
                        text = "Step $currentStep of $totalSteps",
                        style = MaterialTheme.typography.labelMedium,
                        color = BrandPrimary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    // Sub-column centered horizontally and vertically in the remaining space
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(metrics.sectionSpacing + 4.dp, Alignment.CenterVertically)
                    ) {
                        when (val state = syncState) {
                            is SyncState.Loading -> {
                                // Premium animated sync indicator
                                Box(
                                    modifier = Modifier
                                        .size(if (metrics.compactWidth) 100.dp else 120.dp)
                                        .scale(pulseScale)
                                        .shadow(
                                            elevation = 12.dp,
                                            shape = RoundedCornerShape(36.dp),
                                            ambientColor = BrandPrimary.copy(alpha = 0.2f),
                                            spotColor = Color.Transparent
                                        )
                                        .clip(RoundedCornerShape(36.dp))
                                        .background(getOnboardingCardBackground(isDark))
                                        .border(
                                            width = 1.5.dp,
                                            color = getOnboardingCardBorder(isDark),
                                            shape = RoundedCornerShape(36.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Inner glow
                                    Box(
                                        modifier = Modifier
                                            .size(70.dp)
                                            .clip(RoundedCornerShape(24.dp))
                                            .background(
                                                Brush.radialGradient(
                                                    colors = listOf(
                                                        BrandPrimary.copy(alpha = 0.12f),
                                                        Color.Transparent
                                                    )
                                                )
                                            )
                                    )

                                    Icon(
                                        imageVector = Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        modifier = Modifier.size(52.dp),
                                        tint = BrandPrimary
                                    )
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Syncing Wallpapers",
                                        style = LuxeHeadlineStyle,
                                        textAlign = TextAlign.Center,
                                        color = getOnboardingTextPrimary(isDark)
                                    )

                                    Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = getOnboardingTextSecondary(isDark),
                                        textAlign = TextAlign.Center
                                    )
                                }

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    if (state.progress != null) {
                                        // Premium progress bar
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(10.dp)
                                                .clip(RoundedCornerShape(5.dp))
                                                .background(if (isDark) Color(0xFF1F2937) else Color(0xFFE2E8F0))
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(state.progress)
                                                    .height(10.dp)
                                                    .clip(RoundedCornerShape(5.dp))
                                                    .background(
                                                        Brush.horizontalGradient(
                                                            colors = listOf(BrandPrimary, BrandAccent)
                                                        )
                                                    )
                                            )
                                        }

                                        Text(
                                            text = "${(state.progress * 100).toInt()}%",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = BrandPrimary
                                        )
                                    } else {
                                        CircularProgressIndicator(
                                            color = BrandPrimary,
                                            modifier = Modifier.size(36.dp),
                                            strokeWidth = 3.dp
                                        )
                                    }
                                }

                                if (wallpaperCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(getOnboardingCardBackground(isDark))
                                            .border(
                                                width = 1.dp,
                                                color = BrandPrimary.copy(alpha = 0.25f),
                                                shape = RoundedCornerShape(14.dp)
                                            )
                                            .padding(horizontal = 18.dp, vertical = 12.dp)
                                    ) {
                                        Text(
                                            text = "$wallpaperCount wallpapers found",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = BrandPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            is SyncState.Error -> {
                                Box(
                                    modifier = Modifier
                                        .size(if (metrics.compactWidth) 100.dp else 120.dp)
                                        .shadow(
                                            elevation = 8.dp,
                                            shape = RoundedCornerShape(36.dp),
                                            ambientColor = ErrorColor.copy(alpha = 0.15f),
                                            spotColor = Color.Transparent
                                        )
                                        .clip(RoundedCornerShape(36.dp))
                                        .background(ErrorColor.copy(alpha = 0.08f))
                                        .border(
                                            width = 1.5.dp,
                                            color = ErrorColor.copy(alpha = 0.3f),
                                            shape = RoundedCornerShape(36.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Error,
                                        contentDescription = null,
                                        modifier = Modifier.size(52.dp),
                                        tint = ErrorColor
                                    )
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "Sync Failed",
                                        style = LuxeHeadlineStyle,
                                        textAlign = TextAlign.Center,
                                        color = ErrorColor
                                    )

                                    Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.bodyLarge,
                                        textAlign = TextAlign.Center,
                                        color = getOnboardingTextSecondary(isDark)
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .shadow(16.dp, RoundedCornerShape(16.dp))
                                        .height(metrics.buttonHeight)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                colors = listOf(BrandPrimary, BrandAccent)
                                            )
                                        )
                                        .bounceClick { viewModel.startSync() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "Try Again",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                }

                                Text(
                                    text = "Please check your internet connection",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = getOnboardingTextSecondary(isDark),
                                    textAlign = TextAlign.Center
                                )
                            }

                            is SyncState.Success -> {
                                Box(
                                    modifier = Modifier
                                        .size(if (metrics.compactWidth) 100.dp else 120.dp)
                                        .shadow(
                                            elevation = 8.dp,
                                            shape = RoundedCornerShape(36.dp),
                                            ambientColor = SuccessColor.copy(alpha = 0.15f),
                                            spotColor = Color.Transparent
                                        )
                                        .clip(RoundedCornerShape(36.dp))
                                        .background(SuccessColor.copy(alpha = 0.08f))
                                        .border(
                                            width = 1.5.dp,
                                            color = SuccessColor.copy(alpha = 0.3f),
                                            shape = RoundedCornerShape(36.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(52.dp),
                                        tint = SuccessColor
                                    )
                                }

                                Text(
                                    text = "Library Ready!",
                                    style = LuxeHeadlineStyle,
                                    textAlign = TextAlign.Center,
                                    color = SuccessColor
                                )

                                Text(
                                    text = if (state.count > 0) "${state.count} wallpapers downloaded"
                                    else "Wallpapers load on demand",
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center,
                                    color = getOnboardingTextSecondary(isDark)
                                )

                                CircularProgressIndicator(
                                    color = BrandPrimary,
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 3.dp
                                )
                            }

                            is SyncState.Idle -> {
                                CircularProgressIndicator(
                                    color = BrandPrimary,
                                    modifier = Modifier.size(44.dp),
                                    strokeWidth = 3.dp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(paddingValues.calculateBottomPadding() + 24.dp))
                }
            }
        }
    }
}