package me.avinas.vanderwaals.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.avinas.vanderwaals.R
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

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = paddingValues.calculateTopPadding(),
                        bottom = paddingValues.calculateBottomPadding()
                    )
            ) {
                // Top bar skip button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = metrics.horizontalPadding, vertical = 8.dp)
                        .align(Alignment.TopCenter)
                        .widthIn(max = metrics.maxContentWidth),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
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

                // Centered scrollable content
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = metrics.horizontalPadding)
                        .padding(top = 48.dp, bottom = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = metrics.maxContentWidth),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(
                                id = if (isDark) R.drawable.vanderwaals_logo else R.drawable.vanderwaals_logo_black
                            ),
                            contentDescription = "Vanderwaals",
                            modifier = Modifier.height(if (metrics.compactWidth) 38.dp else 44.dp),
                            contentScale = ContentScale.Fit
                        )

                        Spacer(Modifier.height(14.dp))

                        Text(
                            text = "Wallpapers that learn your taste.",
                            fontFamily = PlayfairDisplayFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (metrics.compactWidth) 18.sp else 20.sp,
                            color = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A),
                            letterSpacing = (-0.4).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(20.dp))

                        RadicalTactileCard(isDark = isDark) {
                            Column {
                                WelcomePillarRow(
                                    icon = Icons.Default.AutoAwesome,
                                    accentColor = RadicalPalette.AmethystPurple,
                                    title = "On-Device AI",
                                    description = "Learns your visual taste privately on your device.",
                                    isDark = isDark
                                )

                                RadicalDivider(isDark = isDark)

                                WelcomePillarRow(
                                    icon = Icons.Default.Schedule,
                                    accentColor = RadicalPalette.RadiantAmber,
                                    title = "Smart Rotation",
                                    description = "Fresh wallpapers on screen unlock or schedule.",
                                    isDark = isDark
                                )

                                RadicalDivider(isDark = isDark)

                                WelcomePillarRow(
                                    icon = Icons.Default.Security,
                                    accentColor = RadicalPalette.EmeraldJade,
                                    title = "Private & Offline",
                                    description = "Zero tracking and zero cloud uploads.",
                                    isDark = isDark
                                )
                            }
                        }
                    }
                }
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
