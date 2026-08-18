package me.avinas.vanderwaals.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.avinas.vanderwaals.ui.onboarding.bounceClick
import me.avinas.vanderwaals.ui.onboarding.rememberOnboardingLayoutMetrics
import me.avinas.vanderwaals.ui.theme.*

private data class PrivacySection(
    val title: String,
    val icon: ImageVector,
    val accent: Color,
    val body: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(
    onNavigateBack: () -> Unit
) {
    val isDark = LocalThemeIsDark.current
    val metrics = rememberOnboardingLayoutMetrics()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    androidx.activity.compose.BackHandler { onNavigateBack() }

    val sections = listOf(
        PrivacySection(
            title = "What we do not collect",
            icon = Icons.Default.Block,
            accent = RadicalPalette.RubyRed,
            body = "Vanderwaals collects no personal identifiable information, no accounts or logins, no device identifiers, no location, no contacts or messages, no browsing history, and no usage or crash analytics. The app contains no analytics SDKs, no ad networks, and no third-party trackers."
        ),
        PrivacySection(
            title = "Data stored on your device",
            icon = Icons.Default.PhoneAndroid,
            accent = RadicalPalette.SapphireBlue,
            body = "Your aesthetic preference vector (a 1280-dimensional embedding), your likes and dislikes, your applied-wallpaper history, and cached wallpapers are stored only in the app's private storage and local database on your phone. Cloud backup is disabled, so this data never leaves your device and is deleted permanently when you uninstall."
        ),
        PrivacySection(
            title = "Images you choose for personalization",
            icon = Icons.Default.Image,
            accent = RadicalPalette.AmethystPurple,
            body = "If you enable Personalized Mode and select images to teach the app your taste, those images are processed entirely on-device by MobileNetV4 to compute your preference vector. The image bytes are never uploaded to any server. Only the resulting vector is kept locally."
        ),
        PrivacySection(
            title = "Network usage",
            icon = Icons.Default.Wifi,
            accent = RadicalPalette.EmeraldJade,
            body = "The app uses the internet only to download publicly available wallpapers and their catalog metadata from GitHub-hosted collections and Bing's public photography archive. No personal data is sent in any request."
        ),
        PrivacySection(
            title = "Permissions",
            icon = Icons.Default.VerifiedUser,
            accent = RadicalPalette.RadiantAmber,
            body = "The app requests only the minimum permissions required to function: INTERNET and ACCESS_NETWORK_STATE for downloads, SET_WALLPAPER to apply images, WAKE_LOCK and foreground-service permissions to complete scheduled changes reliably, RECEIVE_BOOT_COMPLETED to reschedule after restart, SCHEDULE_EXACT_ALARM for your chosen change time, and POST_NOTIFICATIONS for the change notification. Storage write is used only on Android 9 and older to save wallpapers. Permissions injected by the ML library that the app does not use (media/phone-state reads) are explicitly removed."
        ),
        PrivacySection(
            title = "Children's privacy",
            icon = Icons.Default.ChildCare,
            accent = RadicalPalette.CoralRose,
            body = "Vanderwaals is a wallpaper utility and is not directed at children under 13. The app does not knowingly collect any data from anyone, including children."
        ),
        PrivacySection(
            title = "Open source",
            icon = Icons.Default.Code,
            accent = if (isDark) RadicalPalette.PlatinumSilver else Color(0xFF1E293B),
            body = "Vanderwaals is open source. You can inspect, audit, and verify the entire application, including this privacy policy, at the project repository. Every claim here can be independently verified from the source."
        ),
        PrivacySection(
            title = "Contact",
            icon = Icons.Default.Mail,
            accent = RadicalPalette.TealCyan,
            body = "Questions about this privacy policy or the app's data practices can be sent to hi@avinas.me or raised as an issue on the project's GitHub repository."
        )
    )

    Box(modifier = Modifier.fillMaxSize()) {
        RadicalTactileBackdrop(
            isDark = isDark,
            modifier = Modifier.matchParentSize()
        )

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Privacy Policy",
                                fontFamily = PlayfairDisplayFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp,
                                color = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A),
                                letterSpacing = (-0.3).sp
                            )
                            Text(
                                text = "100% On-Device & Zero Tracking",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                                letterSpacing = 0.4.sp
                            )
                        }
                    },
                    navigationIcon = {
                        Box(
                            modifier = Modifier
                                .padding(start = 12.dp, end = 4.dp)
                                .size(40.dp)
                                .shadow(
                                    elevation = if (isDark) 4.dp else 2.dp,
                                    shape = CircleShape,
                                    ambientColor = Color.Black.copy(alpha = 0.25f),
                                    spotColor = Color.Black.copy(alpha = 0.20f)
                                )
                                .clip(CircleShape)
                                .background(
                                    if (isDark) {
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color(0xFF1E2433),
                                                Color(0xFF111622)
                                            )
                                        )
                                    } else {
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.White,
                                                Color(0xFFF1F5F9)
                                            )
                                        )
                                    }
                                )
                                .border(
                                    width = 1.dp,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = if (isDark) 0.25f else 0.9f),
                                            if (isDark) Color.Black.copy(alpha = 0.5f) else Color(0xFFCBD5E1)
                                        )
                                    ),
                                    shape = CircleShape
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(bounded = true),
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onNavigateBack()
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = if (isDark) Color.White else Color(0xFF0F172A),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = if (isDark) RadicalPalette.DarkCanvasBase.copy(alpha = 0.88f) else RadicalPalette.LightCanvasBase.copy(alpha = 0.88f),
                        titleContentColor = if (isDark) Color.White else Color(0xFF0F172A),
                        navigationIconContentColor = if (isDark) Color.White else Color(0xFF0F172A)
                    ),
                    windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
                    scrollBehavior = scrollBehavior
                )
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = metrics.maxContentWidth)
                    .padding(horizontal = metrics.horizontalPadding),
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding() + 8.dp,
                    bottom = paddingValues.calculateBottomPadding() + 36.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Summary header
                item {
                    RadicalTactileCard(
                        isDark = isDark,
                        contentPadding = PaddingValues(20.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                RadicalIconBadge(
                                    icon = Icons.Default.Lock,
                                    accentColor = RadicalPalette.SapphireBlue,
                                    isDark = isDark,
                                    size = 40.dp,
                                    iconSize = 20.dp
                                )
                                Column {
                                    Text(
                                        text = "Privacy by Design",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) RadicalPalette.DarkCardTextPrimary else RadicalPalette.LightCardTextPrimary
                                    )
                                    Text(
                                        text = "Last updated: July 16, 2026",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isDark) RadicalPalette.DarkCardTextSecondary else RadicalPalette.LightCardTextSecondary
                                    )
                                }
                            }
                            Text(
                                text = "Vanderwaals is a privacy-first wallpaper engine. Machine learning runs strictly on your device. There are no analytics SDKs, no ads, and no tracking — zero personal data is collected, stored in the cloud, or sold.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isDark) RadicalPalette.DarkCardTextSecondary else RadicalPalette.LightCardTextSecondary,
                                lineHeight = 22.sp
                            )
                        }
                    }
                }

                items(sections) { section ->
                    RadicalTactileCard(
                        isDark = isDark,
                        contentPadding = PaddingValues(18.dp),
                        onClick = if (section.title == "Open source") {
                            { openUrl(context, "https://github.com/avinaxhroy/Vanderwaals") }
                        } else null
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                RadicalIconBadge(
                                    icon = section.icon,
                                    accentColor = section.accent,
                                    isDark = isDark
                                )
                                Text(
                                    text = section.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isDark) RadicalPalette.DarkCardTextPrimary else RadicalPalette.LightCardTextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                if (section.title == "Open source") {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = null,
                                        tint = if (isDark) RadicalPalette.DarkCardTextSecondary else RadicalPalette.LightCardTextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Text(
                                text = section.body,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isDark) RadicalPalette.DarkCardTextSecondary else RadicalPalette.LightCardTextSecondary,
                                lineHeight = 22.sp
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        }
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}
