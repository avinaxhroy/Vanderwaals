package me.avinas.vanderwaals.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import me.avinas.vanderwaals.ui.settings.RadicalDivider
import me.avinas.vanderwaals.ui.settings.RadicalIconBadge
import me.avinas.vanderwaals.ui.settings.RadicalNoticeCard
import me.avinas.vanderwaals.ui.settings.RadicalPalette
import me.avinas.vanderwaals.ui.settings.RadicalSegmentedControl
import me.avinas.vanderwaals.ui.settings.RadicalTactileBackdrop
import me.avinas.vanderwaals.ui.settings.RadicalTactileCard
import me.avinas.vanderwaals.ui.settings.RadicalTactileSwitch
import me.avinas.vanderwaals.ui.theme.LocalThemeIsDark

@Composable
fun WallpaperSourceSelectionScreen(
    onContinue: () -> Unit,
    onBack: () -> Unit = {},
    viewModel: WallpaperSourceSelectionViewModel = hiltViewModel(),
    currentStep: Int = 1,
    totalSteps: Int = 4
) {
    androidx.activity.compose.BackHandler(onBack = onBack)
    val communityEnabled by viewModel.communityEnabled.collectAsState()
    val bingEnabled by viewModel.bingEnabled.collectAsState()
    val bingManifestType by viewModel.bingManifestType.collectAsState()
    val vanderwaalsCollectionEnabled by viewModel.vanderwaalsCollectionEnabled.collectAsState()
    val vanderwaalsCollectionManifestType by viewModel.vanderwaalsCollectionManifestType.collectAsState()
    val isDark = LocalThemeIsDark.current
    val metrics = rememberOnboardingLayoutMetrics()

    val handleContinue = { viewModel.savePreferences { onContinue() } }
    val anyEnabled = communityEnabled || bingEnabled || vanderwaalsCollectionEnabled
    val selectedCount = listOf(communityEnabled, bingEnabled, vanderwaalsCollectionEnabled).count { it }

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
                buttonEnabled = anyEnabled,
                buttonText = "Continue to Sync",
                accentColor = RadicalPalette.EmeraldJade,
                onButtonClick = handleContinue,
                extraContent = {
                    AnimatedVisibility(
                        visible = !anyEnabled,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        RadicalNoticeCard(
                            title = "Source Required",
                            message = "Please enable at least one wallpaper source to continue setup.",
                            icon = Icons.Default.WarningAmber,
                            accentColor = RadicalPalette.RubyRed,
                            isDark = isDark
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(Modifier.fillMaxSize()) {
            RadicalTactileBackdrop(isDark = isDark, modifier = Modifier.matchParentSize())

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = metrics.maxContentWidth)
                    .align(Alignment.TopCenter)
                    .padding(horizontal = metrics.horizontalPadding),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding() + 8.dp,
                    bottom = paddingValues.calculateBottomPadding() + 28.dp
                )
            ) {
                item {
                    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                        OnboardingStepIndicator(
                            currentStep = currentStep - 1,
                            totalSteps = totalSteps,
                            isDark = isDark,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        OnboardingHeader(
                            title = "Wallpaper sources",
                            subtitle = "",
                            isDark = isDark,
                            accentColor = RadicalPalette.CyberMagenta
                        )
                    }
                }

                item {
                    SourceSelectionCard(
                        title = "Bing Daily Wallpapers",
                        subtitle = "Daily landscape & nature photography",
                        icon = Icons.Default.Language,
                        accentColor = RadicalPalette.RadiantAmber,
                        isEnabled = bingEnabled,
                        onToggle = { viewModel.toggleBing(it) },
                        isDark = isDark,
                        depthContent = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Catalog Depth",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) Color(0xFF1C1917) else Color(0xFFFFFFFF)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(99.dp))
                                            .background(
                                                if (isDark) Color(0xFFFEF3C7)
                                                else Color(0xFF03261C)
                                            )
                                            .border(
                                                1.dp,
                                                if (isDark) Color(0xFFFDE68A) else Color(0xFF0D5E47),
                                                RoundedCornerShape(99.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (bingManifestType == "lite") "~1,000 Recent" else "~5,400 Archive",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (isDark) Color(0xFFB45309) else Color(0xFFFDE68A)
                                            )
                                        }
                                }

                                RadicalSegmentedControl(
                                    items = listOf("Lite (Recent)", "Full (Archive)"),
                                    selectedIndex = if (bingManifestType == "lite") 0 else 1,
                                    onItemSelected = { viewModel.setBingManifestType(if (it == 0) "lite" else "full") },
                                    isDark = isDark,
                                    accentColor = RadicalPalette.RadiantAmber
                                )
                            }
                        }
                    )
                }

                item {
                    SourceSelectionCard(
                        title = "Community Collections",
                        subtitle = "Curated open-source photography",
                        icon = Icons.Default.CloudDownload,
                        accentColor = RadicalPalette.AmethystPurple,
                        isEnabled = communityEnabled,
                        onToggle = { viewModel.toggleCommunity(it) },
                        isDark = isDark
                    )
                }

                item {
                    SourceSelectionCard(
                        title = "Vanderwaals Collection",
                        subtitle = "AI-curated aesthetic collection",
                        icon = Icons.Default.AutoAwesome,
                        accentColor = RadicalPalette.TealCyan,
                        isEnabled = vanderwaalsCollectionEnabled,
                        onToggle = { viewModel.toggleVanderwaalsCollection(it) },
                        isDark = isDark,
                        depthContent = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Catalog Depth",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) Color(0xFF1C1917) else Color(0xFFFFFFFF)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(99.dp))
                                            .background(
                                                if (isDark) Color(0xFFCCFBF1)
                                                else Color(0xFF03261C)
                                            )
                                            .border(
                                                1.dp,
                                                if (isDark) Color(0xFF99F6E4) else Color(0xFF0D5E47),
                                                RoundedCornerShape(99.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (vanderwaalsCollectionManifestType == "lite") "Curated Core" else "Full Archive",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (isDark) Color(0xFF0F766E) else Color(0xFF6EE7B7)
                                            )
                                        }
                                }

                                RadicalSegmentedControl(
                                    items = listOf("Lite (Core)", "Full (Complete)"),
                                    selectedIndex = if (vanderwaalsCollectionManifestType == "lite") 0 else 1,
                                    onItemSelected = {
                                        viewModel.setVanderwaalsCollectionManifestType(if (it == 0) "lite" else "full")
                                    },
                                    isDark = isDark,
                                    accentColor = RadicalPalette.TealCyan
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceSelectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    isDark: Boolean,
    depthContent: (@Composable () -> Unit)? = null
) {
    RadicalTactileCard(isDark = isDark) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
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
                        size = 42.dp,
                        iconSize = 20.dp
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color(0xFF1C1917) else Color(0xFFFFFFFF)
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isDark) Color(0xFF383532) else Color(0xFFA7F3D0),
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                RadicalTactileSwitch(
                    checked = isEnabled,
                    onCheckedChange = onToggle,
                    isDark = isDark,
                    accentColor = accentColor
                )
            }

            if (depthContent != null) {
                AnimatedVisibility(
                    visible = isEnabled,
                    enter = expandVertically(animationSpec = tween(200, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(150)),
                    exit = shrinkVertically(animationSpec = tween(150, easing = FastOutLinearInEasing)) + fadeOut(animationSpec = tween(100))
                ) {
                    Column {
                        RadicalDivider(isDark = isDark)
                        depthContent()
                    }
                }
            }
        }
    }
}
