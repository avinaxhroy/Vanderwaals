package me.avinas.vanderwaals.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.avinas.vanderwaals.ui.onboarding.*
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

    androidx.activity.compose.BackHandler { onNavigateBack() }

    val sections = listOf(
        PrivacySection(
            title = "What we do not collect",
            icon = Icons.Default.Block,
            accent = ErrorColor,
            body = "Vanderwaals collects no personal identifiable information, no accounts or logins, no device identifiers, no location, no contacts or messages, no browsing history, and no usage or crash analytics. The app contains no analytics SDKs, no ad networks, and no third-party trackers."
        ),
        PrivacySection(
            title = "Data stored on your device",
            icon = Icons.Default.PhoneAndroid,
            accent = BrandPrimary,
            body = "Your aesthetic preference vector (a 1280-dimensional embedding), your likes and dislikes, your applied-wallpaper history, and cached wallpapers are stored only in the app's private storage and local database on your phone. Cloud backup is disabled, so this data never leaves your device and is deleted permanently when you uninstall."
        ),
        PrivacySection(
            title = "Images you choose for personalization",
            icon = Icons.Default.Image,
            accent = BrandAccent,
            body = "If you enable Personalized Mode and select images to teach the app your taste, those images are processed entirely on-device by MobileNetV4 to compute your preference vector. The image bytes are never uploaded to any server. Only the resulting vector is kept locally."
        ),
        PrivacySection(
            title = "Network usage",
            icon = Icons.Default.Wifi,
            accent = Color(0xFF10B981),
            body = "The app uses the internet only to download publicly available wallpapers and their catalog metadata from GitHub-hosted collections and Bing's public photography archive. No personal data is sent in any request."
        ),
        PrivacySection(
            title = "Permissions",
            icon = Icons.Default.VerifiedUser,
            accent = Color(0xFFF59E0B),
            body = "The app requests only the minimum permissions required to function: INTERNET and ACCESS_NETWORK_STATE for downloads, SET_WALLPAPER to apply images, WAKE_LOCK and foreground-service permissions to complete scheduled changes reliably, RECEIVE_BOOT_COMPLETED to reschedule after restart, SCHEDULE_EXACT_ALARM for your chosen change time, and POST_NOTIFICATIONS for the change notification. Storage write is used only on Android 9 and older to save wallpapers. Permissions injected by the ML library that the app does not use (media/phone-state reads) are explicitly removed."
        ),
        PrivacySection(
            title = "Children's privacy",
            icon = Icons.Default.ChildCare,
            accent = Color(0xFFEC4899),
            body = "Vanderwaals is a wallpaper utility and is not directed at children under 13. The app does not knowingly collect any data from anyone, including children."
        ),
        PrivacySection(
            title = "Open source",
            icon = Icons.Default.Code,
            accent = if (isDark) Color.White else Color.Black,
            body = "Vanderwaals is open source. You can inspect, audit, and verify the entire application, including this privacy policy, at the project repository. Every claim here can be independently verified from the source."
        ),
        PrivacySection(
            title = "Contact",
            icon = Icons.Default.Mail,
            accent = BrandPrimary,
            body = "Questions about this privacy policy or the app's data practices can be sent to hi@avinas.me or raised as an issue on the project's GitHub repository."
        )
    )

    Box(modifier = Modifier.fillMaxSize()) {
        OnboardingBackdrop(
            isDark = isDark,
            modifier = Modifier.matchParentSize()
        )

        Scaffold(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Privacy Policy",
                            fontFamily = PlayfairDisplayFamily,
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            color = getOnboardingTextPrimary(isDark)
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = getOnboardingTextPrimary(isDark)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = if (isDark) Color(0xFF14120F).copy(alpha = 0.8f) else Color(0xFFF9F7F5).copy(alpha = 0.8f),
                        titleContentColor = getOnboardingTextPrimary(isDark),
                        navigationIconContentColor = getOnboardingTextPrimary(isDark)
                    ),
                    windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
                    scrollBehavior = scrollBehavior
                )
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.TopCenter)
                    .widthIn(max = metrics.maxContentWidth)
                    .padding(horizontal = metrics.horizontalPadding),
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding() + 8.dp,
                    bottom = paddingValues.calculateBottomPadding() + 32.dp
                ),
                verticalArrangement = Arrangement.spacedBy(metrics.cardSpacing)
            ) {
                // Summary header
                item {
                    PrivacySummaryCard(isDark = isDark, lastUpdated = "July 16, 2026")
                }

                items(sections) { section ->
                    PrivacySectionCard(
                        section = section,
                        isDark = isDark,
                        onOpenSource = if (section.title == "Open source") {
                            { openUrl(context, "https://github.com/avinaxhroy/Vanderwaals") }
                        } else null
                    )
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

@Composable
private fun PrivacySummaryCard(isDark: Boolean, lastUpdated: String) {
    val metrics = rememberOnboardingLayoutMetrics()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(metrics.cardCornerRadius),
                ambientColor = BrandPrimary.copy(alpha = 0.18f),
                spotColor = Color.Transparent
            )
            .border(1.dp, getOnboardingCardBorder(isDark), RoundedCornerShape(metrics.cardCornerRadius))
            .clip(RoundedCornerShape(metrics.cardCornerRadius))
            .background(getOnboardingCardBackground(isDark))
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = BrandPrimary,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = "Privacy by design",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = getOnboardingTextPrimary(isDark)
            )
        }
        Text(
            text = "Vanderwaals is a privacy-first wallpaper app. Machine learning runs entirely on your device. There are no analytics, no ads, and no tracking \u2014 and no personal data is ever collected or sold.",
            style = MaterialTheme.typography.bodyMedium,
            color = getOnboardingTextSecondary(isDark),
            lineHeight = 22.sp
        )
        Text(
            text = "Last updated: $lastUpdated",
            style = MaterialTheme.typography.labelSmall,
            color = getOnboardingTextSecondary(isDark).copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun PrivacySectionCard(
    section: PrivacySection,
    isDark: Boolean,
    onOpenSource: (() -> Unit)? = null
) {
    val metrics = rememberOnboardingLayoutMetrics()
    val cardModifier = Modifier
        .fillMaxWidth()
        .shadow(
            elevation = 4.dp,
            shape = RoundedCornerShape(metrics.cardCornerRadius),
            ambientColor = if (isDark) Color(0xFF3F3F46).copy(alpha = 0.12f) else Color(0x0A000000),
            spotColor = Color.Transparent
        )
        .border(1.dp, getOnboardingCardBorder(isDark), RoundedCornerShape(metrics.cardCornerRadius))
        .clip(RoundedCornerShape(metrics.cardCornerRadius))
        .background(getOnboardingCardBackground(isDark))
        .let { if (onOpenSource != null) it.clickable(onClick = onOpenSource) else it }

    Column(
        modifier = cardModifier.padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(metrics.iconBoxSize)
                    .clip(RoundedCornerShape(14.dp))
                    .background(section.accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = section.icon,
                    contentDescription = null,
                    tint = section.accent,
                    modifier = Modifier.size(metrics.iconSize)
                )
            }
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = getOnboardingTextPrimary(isDark),
                modifier = Modifier.weight(1f)
            )
            if (onOpenSource != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    tint = getOnboardingTextSecondary(isDark),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Text(
            text = section.body,
            style = MaterialTheme.typography.bodyMedium,
            color = getOnboardingTextSecondary(isDark),
            lineHeight = 22.sp
        )
    }
}
