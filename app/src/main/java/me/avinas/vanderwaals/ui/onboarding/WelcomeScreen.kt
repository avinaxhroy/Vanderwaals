package me.avinas.vanderwaals.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.avinas.vanderwaals.ui.settings.RadicalDivider
import me.avinas.vanderwaals.ui.settings.RadicalIconBadge
import me.avinas.vanderwaals.ui.settings.RadicalPalette
import me.avinas.vanderwaals.ui.settings.RadicalTactileBackdrop
import me.avinas.vanderwaals.ui.settings.RadicalTactileCard
import me.avinas.vanderwaals.ui.theme.LocalThemeIsDark
import me.avinas.vanderwaals.ui.theme.PlayfairDisplayFamily

@Composable
fun WelcomeScreen(
    onGetStarted: () -> Unit,
    onSkip: () -> Unit
) {
    val isDark = LocalThemeIsDark.current
    val metrics = rememberOnboardingLayoutMetrics()
    val haptic = LocalHapticFeedback.current

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            OnboardingBottomBar(
                isDark = isDark,
                metrics = metrics,
                buttonText = "Get Started",
                accentColor = RadicalPalette.EmeraldJade,
                onButtonClick = onGetStarted
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
                        bottom = paddingValues.calculateBottomPadding() + 16.dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = metrics.maxContentWidth)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TactileStepChip(
                            text = "QUICK SETUP",
                            accentColor = RadicalPalette.EmeraldJade,
                            isDark = isDark
                        )

                        TextButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSkip()
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Skip",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) RadicalPalette.DarkCardTextSecondary else RadicalPalette.LightCardTextSecondary
                            )
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    RadicalIconBadge(
                        icon = Icons.Default.Wallpaper,
                        accentColor = RadicalPalette.EmeraldJade,
                        isDark = isDark,
                        size = if (metrics.compactWidth) 48.dp else 54.dp,
                        iconSize = if (metrics.compactWidth) 24.dp else 28.dp
                    )

                    Spacer(Modifier.height(14.dp))

                    Text(
                        text = "Wallpapers that learn your taste.",
                        fontFamily = PlayfairDisplayFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (metrics.compactWidth) 26.sp else 30.sp,
                        color = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A),
                        letterSpacing = (-0.5).sp,
                        lineHeight = if (metrics.compactWidth) 32.sp else 36.sp
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = "On-device AI curation that adapts as you explore, like, and save wallpapers, refreshed on your schedule.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569),
                        lineHeight = 22.sp
                    )

                    Spacer(Modifier.height(20.dp))

                    RadicalTactileCard(isDark = isDark) {
                        Column {
                            WelcomePillarRow(
                                icon = Icons.Default.AutoAwesome,
                                accentColor = RadicalPalette.AmethystPurple,
                                title = "On-Device Personalization",
                                description = "Learns your visual preferences locally without sending photos to a server.",
                                isDark = isDark
                            )

                            RadicalDivider(isDark = isDark)

                            WelcomePillarRow(
                                icon = Icons.Default.Schedule,
                                accentColor = RadicalPalette.RadiantAmber,
                                title = "Automated Rotation",
                                description = "Rotates on screen unlock or on schedule with battery-friendly background jobs.",
                                isDark = isDark
                            )

                            RadicalDivider(isDark = isDark)

                            WelcomePillarRow(
                                icon = Icons.Default.Security,
                                accentColor = RadicalPalette.EmeraldJade,
                                title = "Private & Offline-First",
                                description = "Zero tracking and zero cloud uploads. Your taste profile stays on your device.",
                                isDark = isDark
                            )
                        }
                    }
                }

                Text(
                    text = "You can adjust sources, modes, and schedules anytime in Settings.",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun WelcomePillarRow(
    icon: ImageVector,
    accentColor: Color,
    title: String,
    description: String,
    isDark: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadicalIconBadge(
            icon = icon,
            accentColor = accentColor,
            isDark = isDark,
            size = 40.dp,
            iconSize = 20.dp
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (isDark) RadicalPalette.DarkCardTextPrimary else RadicalPalette.LightCardTextPrimary
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = if (isDark) RadicalPalette.DarkCardTextSecondary else RadicalPalette.LightCardTextSecondary,
                lineHeight = 18.sp
            )
        }
    }
}
