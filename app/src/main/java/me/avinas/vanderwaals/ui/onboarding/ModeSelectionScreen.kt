package me.avinas.vanderwaals.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import me.avinas.vanderwaals.ui.settings.RadicalIconBadge
import me.avinas.vanderwaals.ui.settings.RadicalPalette
import me.avinas.vanderwaals.ui.settings.RadicalTactileBackdrop
import me.avinas.vanderwaals.ui.settings.RadicalTactileCard
import me.avinas.vanderwaals.ui.theme.LocalThemeIsDark

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
            OnboardingTopBar(isDark = isDark, metrics = metrics, onBack = onBack)
        },
        bottomBar = {
            OnboardingBottomBar(
                isDark = isDark,
                metrics = metrics,
                buttonEnabled = selectedMode != null,
                buttonText = if (selectedMode == OnboardingMode.PERSONALIZE) "Personalize Taste" else "Continue with Auto Mode",
                accentColor = RadicalPalette.CyberMagenta,
                onButtonClick = {
                    selectedMode?.let { onModeSelected(it) }
                }
            )
        }
    ) { paddingValues ->
        Box(Modifier.fillMaxSize()) {
            RadicalTactileBackdrop(isDark = isDark, modifier = Modifier.matchParentSize())

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = metrics.horizontalPadding)
                    .padding(
                        top = paddingValues.calculateTopPadding() + 8.dp,
                        bottom = paddingValues.calculateBottomPadding() + 20.dp
                    )
                    .widthIn(max = metrics.maxContentWidth)
                    .align(Alignment.TopCenter),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OnboardingStepIndicator(
                        currentStep = currentStep - 1,
                        totalSteps = totalSteps,
                        isDark = isDark,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    OnboardingHeader(
                        title = "Choose curation mode",
                        subtitle = "",
                        isDark = isDark,
                        accentColor = RadicalPalette.CyberMagenta
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    RichModeOptionCard(
                        title = "Personalized Mode",
                        subtitle = "Learns your taste from favorite photos & likes.",
                        badge = "RECOMMENDED",
                        badgeBg = Color(0xFFFFE4EC),
                        badgeTextColor = Color(0xFFBE123C),
                        icon = Icons.Default.AutoAwesome,
                        accentColor = RadicalPalette.CyberMagenta,
                        features = listOf("On-Device AI", "1-Min Setup"),
                        isSelected = selectedMode == OnboardingMode.PERSONALIZE,
                        onClick = { viewModel.selectMode(OnboardingMode.PERSONALIZE) {} },
                        isDark = isDark
                    )

                    RichModeOptionCard(
                        title = "Auto Mode",
                        subtitle = "Instant curated wallpapers, learns as you go.",
                        badge = "INSTANT START",
                        badgeBg = Color(0xFFFEF3C7),
                        badgeTextColor = Color(0xFFB45309),
                        icon = Icons.Default.Shuffle,
                        accentColor = RadicalPalette.RadiantAmber,
                        features = listOf("Instant Start", "Learns As You Like"),
                        isSelected = selectedMode == OnboardingMode.AUTO,
                        onClick = { viewModel.selectMode(OnboardingMode.AUTO) {} },
                        isDark = isDark
                    )
                }
            }
        }
    }
}

@Composable
private fun RichModeOptionCard(
    title: String,
    subtitle: String,
    badge: String,
    badgeBg: Color,
    badgeTextColor: Color,
    icon: ImageVector,
    accentColor: Color,
    features: List<String>,
    isSelected: Boolean,
    onClick: () -> Unit,
    isDark: Boolean
) {
    val radioBorderColor by animateColorAsState(
        targetValue = if (isSelected) accentColor else if (isDark) Color(0xFF8C8275) else Color(0xFF6EE7B7),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "modeRadioBorder"
    )

    RadicalTactileCard(
        isDark = isDark,
        modifier = Modifier.bounceClick(onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                        icon = icon,
                        accentColor = accentColor,
                        isDark = isDark,
                        size = 44.dp,
                        iconSize = 22.dp
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color(0xFF1C1917) else Color(0xFFFFFFFF)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(badgeBg)
                                .border(
                                    1.dp,
                                    badgeTextColor.copy(alpha = 0.35f),
                                    RoundedCornerShape(99.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = badgeTextColor,
                                fontSize = 10.sp,
                                letterSpacing = 0.4.sp
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) accentColor else Color.Transparent)
                        .border(
                            width = if (isSelected) 0.dp else 2.dp,
                            color = radioBorderColor,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            modifier = Modifier.size(15.dp),
                            tint = Color.White
                        )
                    }
                }
            }

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isDark) Color(0xFF383532) else Color(0xFFA7F3D0),
                lineHeight = 20.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                features.forEach { feat ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(
                                if (isDark) Color(0xFFE4DDD2)
                                else Color(0xFF03261C)
                            )
                            .border(
                                1.dp,
                                if (isDark) Color(0xFFCBC3B5)
                                else Color(0xFF0D5E47),
                                RoundedCornerShape(99.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = feat,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDark) Color(0xFF1C1917) else Color(0xFFD1FAE5)
                        )
                    }
                }
            }
        }
    }
}
