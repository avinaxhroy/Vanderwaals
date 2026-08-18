package me.avinas.vanderwaals.ui.onboarding

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import me.avinas.vanderwaals.ui.settings.RadicalButtonVariant
import me.avinas.vanderwaals.ui.settings.RadicalIconBadge
import me.avinas.vanderwaals.ui.settings.RadicalPalette
import me.avinas.vanderwaals.ui.settings.RadicalProgressMeter
import me.avinas.vanderwaals.ui.settings.RadicalTactileBackdrop
import me.avinas.vanderwaals.ui.settings.RadicalTactileButton
import me.avinas.vanderwaals.ui.settings.RadicalTactileCard
import me.avinas.vanderwaals.ui.theme.LocalThemeIsDark
import me.avinas.vanderwaals.ui.theme.PlayfairDisplayFamily

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
            // Auto advance after brief celebration
            kotlinx.coroutines.delay(1200)
            onSyncComplete()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Box(Modifier.fillMaxSize()) {
            RadicalTactileBackdrop(isDark = isDark, modifier = Modifier.matchParentSize())

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = metrics.horizontalPadding)
                    .padding(
                        top = paddingValues.calculateTopPadding() + 8.dp,
                        bottom = paddingValues.calculateBottomPadding() + 20.dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = metrics.maxContentWidth)
                ) {
                    OnboardingStepIndicator(
                        currentStep = currentStep - 1,
                        totalSteps = totalSteps,
                        isDark = isDark,
                        accentColor = RadicalPalette.CyberMagenta,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    OnboardingHeader(
                        stepLabel = "STAGE 0$currentStep / 0$totalSteps · CATALOG SYNC",
                        title = "Indexing wallpaper catalog",
                        subtitle = "Downloads and indexes wallpaper catalogs for offline matching.",
                        isDark = isDark,
                        accentColor = RadicalPalette.CyberMagenta
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .widthIn(max = metrics.maxContentWidth),
                    contentAlignment = Alignment.Center
                ) {
                    when (val state = syncState) {
                        is SyncState.Loading -> {
                            TactileLoadingCard(
                                message = state.message,
                                progress = state.progress,
                                count = wallpaperCount,
                                isDark = isDark
                            )
                        }
                        is SyncState.Error -> {
                            TactileErrorCard(
                                message = state.message,
                                isDark = isDark,
                                onRetry = { viewModel.startSync() }
                            )
                        }
                        is SyncState.Success -> {
                            TactileSuccessCard(
                                count = state.count,
                                isDark = isDark,
                                onContinue = onSyncComplete
                            )
                        }
                        is SyncState.Idle -> {
                            CircularProgressIndicator(
                                color = RadicalPalette.CyberMagenta,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }

                Text(
                    text = "Sync runs once during setup. Catalogs update automatically in the background.",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = metrics.maxContentWidth),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun TactileLoadingCard(
    message: String,
    progress: Float?,
    count: Int,
    isDark: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sync_pulse")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sync_rotation"
    )

    RadicalTactileCard(isDark = isDark) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .rotate(rotation)
                        .clip(CircleShape)
                        .border(
                            1.5.dp,
                            Brush.sweepGradient(
                                listOf(
                                    Color.Transparent,
                                    RadicalPalette.CyberMagenta.copy(alpha = 0.35f),
                                    RadicalPalette.CyberMagenta,
                                    Color.Transparent
                                )
                            ),
                            CircleShape
                        )
                )

                RadicalIconBadge(
                    icon = Icons.Default.CloudDownload,
                    accentColor = RadicalPalette.CyberMagenta,
                    isDark = isDark,
                    size = 54.dp,
                    iconSize = 28.dp
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Indexing Catalogs",
                    fontFamily = PlayfairDisplayFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = if (isDark) Color(0xFF1C1917) else Color(0xFFFFFFFF)
                )

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isDark) Color(0xFF383532) else Color(0xFFA7F3D0),
                    textAlign = TextAlign.Center
                )
            }

            RadicalProgressMeter(
                progress = progress ?: 0.5f,
                label = if (progress != null) "${(progress * 100).toInt()}% synchronized" else "Preparing local index...",
                sublabel = if (count > 0) "$count wallpapers indexed" else null,
                isDark = isDark,
                accentColor = RadicalPalette.CyberMagenta,
                isLoading = progress == null
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(
                            if (isDark) Color(0xFFE4DDD2)
                            else Color(0xFF03261C)
                        )
                        .border(
                            1.dp,
                            if (isDark) Color(0xFFCBC3B5) else Color(0xFF0D5E47),
                            RoundedCornerShape(99.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "100% On-Device",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDark) Color(0xFF1C1917) else Color(0xFFD1FAE5),
                        fontSize = 11.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(
                            if (isDark) Color(0xFFE4DDD2)
                            else Color(0xFF03261C)
                        )
                        .border(
                            1.dp,
                            if (isDark) Color(0xFFCBC3B5) else Color(0xFF0D5E47),
                            RoundedCornerShape(99.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "Fast Offline Matching",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDark) Color(0xFF1C1917) else Color(0xFFD1FAE5),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun TactileErrorCard(
    message: String,
    isDark: Boolean,
    onRetry: () -> Unit
) {
    RadicalTactileCard(isDark = isDark) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            RadicalIconBadge(
                icon = Icons.Default.ErrorOutline,
                accentColor = RadicalPalette.RubyRed,
                isDark = isDark,
                size = 54.dp,
                iconSize = 28.dp
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Sync Failed",
                    fontFamily = PlayfairDisplayFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = RadicalPalette.RubyRed
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isDark) Color(0xFF383532) else Color(0xFFA7F3D0),
                    textAlign = TextAlign.Center
                )
            }

            RadicalTactileButton(
                text = "Retry Sync",
                icon = Icons.Default.Refresh,
                onClick = onRetry,
                isDark = isDark,
                variant = RadicalButtonVariant.Primary
            )
        }
    }
}

@Composable
private fun TactileSuccessCard(
    count: Int,
    isDark: Boolean,
    onContinue: () -> Unit
) {
    RadicalTactileCard(isDark = isDark) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .shadow(
                        elevation = 6.dp,
                        shape = CircleShape,
                        ambientColor = RadicalPalette.CyberMagenta.copy(alpha = 0.35f),
                        spotColor = RadicalPalette.CyberMagenta.copy(alpha = 0.45f)
                    )
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                RadicalPalette.CyberMagenta,
                                RadicalPalette.CyberMagentaDark
                            )
                        )
                    )
                    .border(1.5.dp, Color.White.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Catalog Ready",
                    fontFamily = PlayfairDisplayFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = if (isDark) Color(0xFF1C1917) else Color(0xFFFFFFFF)
                )

                Text(
                    text = if (count > 0) "$count wallpapers indexed into local database" else "Catalog synchronized successfully",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isDark) Color(0xFF383532) else Color(0xFFA7F3D0),
                    textAlign = TextAlign.Center
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isDark) Color(0xFFFFE4EC)
                        else Color(0xFF03261C)
                    )
                    .border(
                        1.dp,
                        if (isDark) Color(0xFFFECDD3) else Color(0xFF0D5E47),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = if (isDark) Color(0xFFBE123C) else Color(0xFF6EE7B7),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "On-Device Recommendations Ready",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color(0xFFBE123C) else Color(0xFF6EE7B7)
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            RadicalTactileButton(
                text = "Continue",
                onClick = onContinue,
                isDark = isDark,
                variant = RadicalButtonVariant.Primary
            )
        }
    }
}
