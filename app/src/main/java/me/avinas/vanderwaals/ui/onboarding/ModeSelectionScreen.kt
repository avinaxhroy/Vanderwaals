package me.avinas.vanderwaals.ui.onboarding

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import me.avinas.vanderwaals.R
import me.avinas.vanderwaals.ui.theme.*

@Composable
fun ModeSelectionScreen(
    onModeSelected: (OnboardingMode) -> Unit,
    onBack: () -> Unit = {},
    viewModel: ModeSelectionViewModel = hiltViewModel(),
    currentStep: Int = 1,
    totalSteps: Int = 6
) {
    androidx.activity.compose.BackHandler(onBack = onBack)
    val selectedMode by viewModel.selectedMode.collectAsState()
    val isDark = LocalThemeIsDark.current
    val metrics = rememberOnboardingLayoutMetrics()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent,
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
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = getOnboardingTextPrimary(isDark)
                        )
                    }
                }
            }
        },
        bottomBar = {
            OnboardingBottomBar(
                isDark = isDark,
                metrics = metrics,
                buttonEnabled = selectedMode != null,
                onButtonClick = { selectedMode?.let { onModeSelected(it) } }
            )
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

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = metrics.horizontalPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(paddingValues.calculateTopPadding() + 12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = metrics.maxContentWidth)
                ) {
                    OnboardingStepIndicator(
                        currentStep = currentStep - 1,
                        totalSteps = totalSteps,
                        isDark = isDark,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Step number
                    Text(
                        text = "Step $currentStep of $totalSteps",
                        style = MaterialTheme.typography.labelMedium,
                        color = BrandPrimary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    // Title
                    Text(
                        text = "Choose Your Mode",
                        style = LuxeHeadlineStyle,
                        color = getOnboardingTextPrimary(isDark),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Description
                    Text(
                        text = "How Vanderwaals learns your taste",
                        style = LuxeBodyStyle,
                        color = getOnboardingTextSecondary(isDark)
                    )

                    Spacer(modifier = Modifier.height(metrics.sectionSpacing))

                    Column(
                        verticalArrangement = Arrangement.spacedBy(metrics.cardSpacing + 4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ModeOptionCard(
                            title = "Personalized Mode",
                            subtitle = "Upload & Match",
                            description = "Use a favorite wallpaper as reference and instantly build a tailored feed.",
                            icon = Icons.Default.Tune,
                            accent = Color(0xFFF97316),
                            selected = selectedMode == OnboardingMode.PERSONALIZE,
                            onClick = {
                                viewModel.selectMode(OnboardingMode.PERSONALIZE) {}
                            },
                            isDark = isDark,
                            metrics = metrics
                        )

                        ModeOptionCard(
                            title = "Auto Mode",
                            subtitle = "Set & Forget",
                            description = "Start immediately with curated recommendations and let the app learn over time.",
                            icon = Icons.Default.AutoAwesome,
                            accent = Color(0xFF2563EB),
                            selected = selectedMode == OnboardingMode.AUTO,
                            onClick = {
                                viewModel.selectMode(OnboardingMode.AUTO) {}
                            },
                            isDark = isDark,
                            metrics = metrics
                        )
                    }

                    Spacer(modifier = Modifier.height(paddingValues.calculateBottomPadding() + 24.dp))
                }
            }
        }
    }
}

@Composable
private fun ModeOptionCard(
    title: String,
    subtitle: String,
    description: String,
    icon: ImageVector,
    accent: Color,
    selected: Boolean,
    onClick: () -> Unit,
    isDark: Boolean,
    metrics: OnboardingLayoutMetrics = rememberOnboardingLayoutMetrics()
) {
    // Sliding bottom line indicator animation
    val lineWidthPercent by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "lineWidth"
    )

    // Gradient border when selected, glass border otherwise
    val borderBrush = if (selected) {
        Brush.linearGradient(
            colors = listOf(accent, accent.copy(alpha = 0.7f))
        )
    } else {
        SolidColor(getOnboardingCardBorder(isDark))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (selected) 16.dp else 0.dp,
                shape = RoundedCornerShape(metrics.cardCornerRadius),
                ambientColor = if (selected) accent.copy(alpha = 0.25f) else Color.Transparent,
                spotColor = if (selected) accent.copy(alpha = 0.2f) else Color.Transparent
            )
            .clip(RoundedCornerShape(metrics.cardCornerRadius))
            .background(getOnboardingCardBackground(isDark))
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                brush = borderBrush,
                shape = RoundedCornerShape(metrics.cardCornerRadius)
            )
            .bounceClick { onClick() }
    ) {
        val cardPadding = if (metrics.compactWidth) 16.dp else 20.dp
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(cardPadding),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(if (metrics.compactWidth) 12.dp else 14.dp)
        ) {
            // Premium icon container with luxe glass background
            Box(
                modifier = Modifier
                    .size(metrics.iconBoxSize)
                    .clip(RoundedCornerShape(14.dp))
                    .background(getOnboardingCardBackground(isDark)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) accent else (if (isDark) Color(0xFF8A8478) else Color(0xFF6E685C)),
                    modifier = Modifier.size(metrics.iconSize)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = getOnboardingTextPrimary(isDark)
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (selected) accent.copy(alpha = 0.15f) else getOnboardingCardBackground(isDark)
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = if (selected) accent else getOnboardingTextSecondary(isDark)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = getOnboardingTextSecondary(isDark),
                    lineHeight = 20.sp
                )
            }

            // Premium selection indicator
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) {
                            Brush.linearGradient(
                                colors = listOf(accent, accent.copy(alpha = 0.8f))
                            )
                        } else {
                            Brush.linearGradient(
                                colors = listOf(
                                    if (isDark) Color(0xFF1C1A17) else Color(0xFFF7F5F0),
                                    if (isDark) Color(0xFF141210) else Color(0xFFECEAE3)
                                )
                            )
                        }
                    )
                    .border(
                        width = if (selected) 0.dp else 1.5.dp,
                        color = if (selected) Color.Transparent else (if (isDark) Color(0xFF4A443A) else Color(0xFFD1CBBF)),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Sliding bottom line indicator for active selection - docked to the bottom edge and clipped by the card shape
        if (lineWidthPercent > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(lineWidthPercent)
                    .height(4.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(accent, accent.copy(alpha = 0.6f))
                        )
                    )
            )
        }
    }
}