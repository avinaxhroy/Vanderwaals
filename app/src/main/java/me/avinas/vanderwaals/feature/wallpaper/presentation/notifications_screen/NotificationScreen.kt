package me.avinas.vanderwaals.feature.wallpaper.presentation.notifications_screen

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import me.avinas.vanderwaals.ui.onboarding.TactileHeroBadge
import me.avinas.vanderwaals.ui.onboarding.rememberOnboardingLayoutMetrics
import me.avinas.vanderwaals.ui.settings.*
import me.avinas.vanderwaals.ui.theme.LocalThemeIsDark
import me.avinas.vanderwaals.ui.theme.PlayfairDisplayFamily

@Composable
fun NotificationScreen(
    onAgree: () -> Unit
) {
    val context = LocalContext.current
    val isDark = LocalThemeIsDark.current
    val metrics = rememberOnboardingLayoutMetrics()
    val scrollState = rememberScrollState()

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { _ -> onAgree() }
    )

    fun handleContinue() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                onAgree()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            onAgree()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        RadicalTactileBackdrop(isDark = isDark)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = metrics.maxContentWidth)
                    .padding(horizontal = metrics.horizontalPadding)
            ) {
                Spacer(Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Spacer(Modifier.height(12.dp))

                    TactileHeroBadge(
                        icon = Icons.Default.NotificationsActive,
                        accentColor = RadicalPalette.EmeraldJade,
                        isDark = isDark,
                        size = 88.dp,
                        iconSize = 42.dp
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Stay in Sync",
                            fontFamily = PlayfairDisplayFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp,
                            color = if (isDark) RadicalPalette.DarkCardTextPrimary else RadicalPalette.LightCardTextPrimary,
                            letterSpacing = (-0.4).sp,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Receive updates when wallpapers change or new collections are added.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isDark) RadicalPalette.DarkCardTextSecondary else RadicalPalette.LightCardTextSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 21.sp
                        )
                    }

                    RadicalTactileCard(isDark = isDark) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            NotificationFeatureRow(
                                icon = Icons.Default.Schedule,
                                title = "Change Confirmations",
                                description = "A brief notification whenever a new wallpaper is applied in the background.",
                                isDark = isDark
                            )
                            NotificationFeatureRow(
                                icon = Icons.Default.Security,
                                title = "No Marketing",
                                description = "Only wallpaper update alerts. No promotional notifications.",
                                isDark = isDark
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    RadicalTactileButton(
                        text = "Enable Notifications",
                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                        onClick = { handleContinue() },
                        isDark = isDark,
                        variant = RadicalButtonVariant.Primary
                    )

                    RadicalTactileButton(
                        text = "Skip for Now",
                        onClick = onAgree,
                        isDark = isDark,
                        variant = RadicalButtonVariant.Secondary
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationFeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    isDark: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        RadicalIconBadge(
            icon = icon,
            accentColor = RadicalPalette.EmeraldJade,
            isDark = isDark,
            size = 36.dp,
            iconSize = 18.dp
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
