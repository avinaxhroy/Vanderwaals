package me.avinas.vanderwaals.ui.onboarding

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.avinas.vanderwaals.ui.theme.*

@Composable
fun WelcomeScreen(
    onGetStarted: () -> Unit,
    onSkip: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val metrics = rememberOnboardingLayoutMetrics()

    Scaffold(
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Warm ambient backdrop
            OnboardingBackdrop(
                isDark = isDark,
                modifier = Modifier.matchParentSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = metrics.maxContentWidth)
                        .padding(horizontal = metrics.horizontalPadding)
                ) {
                    Spacer(modifier = Modifier.height(paddingValues.calculateTopPadding() + 12.dp))
                    // Skip button at top right
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onSkip) {
                            Text(
                                text = "Skip",
                                style = MaterialTheme.typography.labelLarge,
                                color = getOnboardingTextSecondary(isDark)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(if (metrics.compactHeight) 24.dp else 48.dp))

                    // Logo area
                    // App icon with soft glow
                    Box(
                        modifier = Modifier
                            .size(if (metrics.compactWidth) 72.dp else 88.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        BrandPrimary.copy(alpha = 0.2f),
                                        BrandAccent.copy(alpha = 0.15f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Vanderwaals",
                            modifier = Modifier.size(if (metrics.compactWidth) 36.dp else 44.dp),
                            tint = BrandPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Headline — Playfair Display italic
                    Text(
                        text = "Your phone.\nYour aesthetic.",
                        style = LuxeHeadlineStyle,
                        color = getOnboardingTextPrimary(isDark),
                        textAlign = TextAlign.Start
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Subheadline
                    Text(
                        text = "Wallpapers that match your taste, refreshed automatically.",
                        style = LuxeBodyStyle,
                        color = getOnboardingTextSecondary(isDark),
                        textAlign = TextAlign.Start,
                        lineHeight = 24.sp
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // 3 Value Cards
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        WelcomeValueCard(
                            icon = Icons.Default.AutoAwesome,
                            title = "Learns Your Taste",
                            description = "The more you use it, the better it gets",
                            isDark = isDark,
                            metrics = metrics
                        )
                        WelcomeValueCard(
                            icon = Icons.Default.Wallpaper,
                            title = "Auto Refresh",
                            description = "New wallpapers applied on your schedule",
                            isDark = isDark,
                            metrics = metrics
                        )
                        WelcomeValueCard(
                            icon = Icons.Default.Security,
                            title = "Stays Private",
                            description = "All processing happens on your device",
                            isDark = isDark,
                            metrics = metrics
                        )
                    }

                    Spacer(modifier = Modifier.height(40.dp))

                    // CTA Button — using OnboardingBottomBar style
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = metrics.maxContentWidth)
                            .shadow(16.dp, RoundedCornerShape(16.dp))
                            .height(metrics.buttonHeight)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(BrandPrimary, BrandAccent)
                                )
                            )
                            .bounceClick(onGetStarted),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Get Started",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(paddingValues.calculateBottomPadding() + 24.dp))
                }
            }
        }
    }
}

@Composable
private fun WelcomeValueCard(
    icon: ImageVector,
    title: String,
    description: String,
    isDark: Boolean,
    metrics: OnboardingLayoutMetrics
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(metrics.cardCornerRadius))
            .background(getOnboardingCardBackground(isDark))
            .border(1.dp, getOnboardingCardBorder(isDark), RoundedCornerShape(metrics.cardCornerRadius))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(BrandPrimary.copy(alpha = if (isDark) 0.15f else 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = BrandPrimary
            )
        }

        // Text
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = getOnboardingTextPrimary(isDark)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = getOnboardingTextSecondary(isDark)
            )
        }
    }
}
