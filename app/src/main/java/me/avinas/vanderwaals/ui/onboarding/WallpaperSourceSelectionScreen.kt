package me.avinas.vanderwaals.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import me.avinas.vanderwaals.ui.theme.BorderDark
import me.avinas.vanderwaals.ui.theme.BorderLight
import me.avinas.vanderwaals.ui.theme.BrandPrimary
import me.avinas.vanderwaals.ui.theme.BrandAccent
import me.avinas.vanderwaals.ui.theme.ErrorColor
import me.avinas.vanderwaals.ui.theme.LocalThemeIsDark
import me.avinas.vanderwaals.ui.theme.LuxeBodyStyle
import me.avinas.vanderwaals.ui.theme.LuxeHeadlineStyle

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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

    val handleContinue = {
        viewModel.savePreferences { onContinue() }
    }

    val anyEnabled = communityEnabled || bingEnabled || vanderwaalsCollectionEnabled

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
                buttonEnabled = anyEnabled,
                onButtonClick = handleContinue,
                extraContent = {
                    AnimatedVisibility(
                        visible = !anyEnabled,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = metrics.maxContentWidth)
                                .clip(RoundedCornerShape(10.dp))
                                .background(ErrorColor.copy(alpha = 0.08f))
                                .padding(vertical = 10.dp, horizontal = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = ErrorColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Please select at least one source",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = ErrorColor
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            OnboardingBackdrop(
                isDark = isDark,
                modifier = Modifier.matchParentSize()
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = metrics.horizontalPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = metrics.maxContentWidth)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(metrics.cardSpacing),
                        contentPadding = PaddingValues(
                            top = paddingValues.calculateTopPadding() + 12.dp,
                            bottom = paddingValues.calculateBottomPadding() + 24.dp
                        )
                    ) {
                        // Move step indicators and headers to a scrollable header item
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = metrics.sectionSpacing),
                                horizontalAlignment = Alignment.Start
                            ) {
                                OnboardingStepIndicator(
                                    currentStep = currentStep - 1,
                                    totalSteps = totalSteps,
                                    isDark = isDark,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )

                                Text(
                                    text = "Step $currentStep of $totalSteps",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = BrandPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )

                                Text(
                                    text = "Choose Your Sources",
                                    style = LuxeHeadlineStyle,
                                    color = getOnboardingTextPrimary(isDark),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                Text(
                                    text = "Select where Vanderwaals finds your wallpapers",
                                    style = LuxeBodyStyle,
                                    color = getOnboardingTextSecondary(isDark),
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                        }

                        item {
                            SourceToggleCard(
                                title = "Community Collection",
                                subtitle = "Curated high-quality wallpapers from the open-source community.",
                                icon = Icons.Default.Public,
                                isEnabled = communityEnabled,
                                onToggle = { viewModel.toggleCommunity(it) },
                                isDark = isDark,
                                metrics = metrics
                            )
                        }

                        item {
                            SourceToggleCard(
                                title = "Bing Daily Wallpapers",
                                subtitle = "Stunning photography from around the world, updated daily.",
                                icon = Icons.Default.Image,
                                isEnabled = bingEnabled,
                                onToggle = { viewModel.toggleBing(it) },
                                isDark = isDark,
                                expandableContent = if (bingEnabled) {
                                    {
                                        HorizontalDivider(
                                            color = getOnboardingCardBorder(isDark),
                                            modifier = Modifier.padding(vertical = 14.dp)
                                        )

                                        Text(
                                            text = "COLLECTION TYPE",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = getOnboardingTextSecondary(isDark),
                                            letterSpacing = 1.2.sp,
                                            modifier = Modifier.padding(bottom = 10.dp)
                                        )

                                        val options = listOf("Recent Hits (Lite)", "Global Archive (Full)")
                                        val selectedIndex = if (bingManifestType == "lite") 0 else 1

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(
                                                    if (isDark) Color(0xFF0F0F12) else Color(0xFFF1F5F9),
                                                    RoundedCornerShape(14.dp)
                                                )
                                                .padding(4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            options.forEachIndexed { index, option ->
                                                val isSelected = index == selectedIndex
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(
                                                            brush = if (isSelected) {
                                                                Brush.horizontalGradient(
                                                                    colors = listOf(BrandPrimary, BrandAccent)
                                                                )
                                                            } else {
                                                                Brush.linearGradient(
                                                                    colors = listOf(
                                                                        Color.Transparent,
                                                                        Color.Transparent
                                                                    )
                                                                )
                                                            },
                                                            shape = RoundedCornerShape(12.dp)
                                                        )
                                                        .bounceClick {
                                                            viewModel.setBingManifestType(if (index == 0) "lite" else "full")
                                                        }
                                                        .padding(vertical = 12.dp, horizontal = 10.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = option,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Medium,
                                                        color = if (isSelected) Color.White else getOnboardingTextSecondary(isDark),
                                                        maxLines = 1,
                                                        textAlign = TextAlign.Center
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Text(
                                            text = if (bingManifestType == "lite") {
                                                "Quick download. Best for getting started."
                                            } else {
                                                "Thousands of images. Requires larger download."
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = getOnboardingTextSecondary(isDark)
                                        )
                                    }
                                } else {
                                    null
                                },
                                metrics = metrics
                            )
                        }

                        item {
                            SourceToggleCard(
                                title = "Vanderwaals Collection",
                                subtitle = "The app's own curated wallpaper archive, served fresh with smart embeddings.",
                                icon = Icons.Default.Wallpaper,
                                isEnabled = vanderwaalsCollectionEnabled,
                                onToggle = { viewModel.toggleVanderwaalsCollection(it) },
                                isDark = isDark,
                                expandableContent = if (vanderwaalsCollectionEnabled) {
                                    {
                                        HorizontalDivider(
                                            color = getOnboardingCardBorder(isDark),
                                            modifier = Modifier.padding(vertical = 14.dp)
                                        )

                                        Text(
                                            text = "COLLECTION TYPE",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = getOnboardingTextSecondary(isDark),
                                            letterSpacing = 1.2.sp,
                                            modifier = Modifier.padding(bottom = 10.dp)
                                        )

                                        val vdOptions = listOf("Curated (Lite)", "Full Archive (Full)")
                                        val vdSelectedIndex = if (vanderwaalsCollectionManifestType == "lite") 0 else 1

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(
                                                    if (isDark) Color(0xFF0F0F12) else Color(0xFFF1F5F9),
                                                    RoundedCornerShape(14.dp)
                                                )
                                                .padding(4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            vdOptions.forEachIndexed { index, option ->
                                                val isSelected = index == vdSelectedIndex
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(
                                                            brush = if (isSelected) {
                                                                Brush.horizontalGradient(
                                                                    colors = listOf(BrandPrimary, BrandAccent)
                                                                )
                                                            } else {
                                                                Brush.linearGradient(
                                                                    colors = listOf(
                                                                        Color.Transparent,
                                                                        Color.Transparent
                                                                    )
                                                                )
                                                            },
                                                            shape = RoundedCornerShape(12.dp)
                                                        )
                                                        .bounceClick {
                                                            viewModel.setVanderwaalsCollectionManifestType(if (index == 0) "lite" else "full")
                                                        }
                                                        .padding(vertical = 12.dp, horizontal = 10.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = option,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Medium,
                                                        color = if (isSelected) Color.White else getOnboardingTextSecondary(isDark),
                                                        maxLines = 1,
                                                        textAlign = TextAlign.Center
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Text(
                                            text = if (vanderwaalsCollectionManifestType == "lite") {
                                                "Curated highlights. Best for getting started."
                                            } else {
                                                "Complete collection. Requires larger download."
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = getOnboardingTextSecondary(isDark)
                                        )
                                    }
                                } else {
                                    null
                                },
                                metrics = metrics
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceToggleCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    isDark: Boolean,
    expandableContent: @Composable (() -> Unit)? = null,
    metrics: OnboardingLayoutMetrics = rememberOnboardingLayoutMetrics()
) {
    val targetBackgroundColor = if (isDark) {
        if (isEnabled) {
            Color.White.copy(alpha = 0.16f)
        } else {
            Color.White.copy(alpha = 0.09f)
        }
    } else {
        if (isEnabled) {
            Color.White.copy(alpha = 0.75f)
        } else {
            Color.White.copy(alpha = 0.40f)
        }
    }

    val targetBorderColor = if (isEnabled) {
        BrandPrimary.copy(alpha = 0.7f)
    } else {
        getOnboardingCardBorder(isDark)
    }

    val backgroundColor by androidx.compose.animation.animateColorAsState(
        targetValue = targetBackgroundColor,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 300),
        label = "cardBackground"
    )

    val borderColor by androidx.compose.animation.animateColorAsState(
        targetValue = targetBorderColor,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 300),
        label = "cardBorder"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick { onToggle(!isEnabled) }
            .shadow(
                elevation = if (isEnabled) 12.dp else 2.dp,
                shape = RoundedCornerShape(metrics.cardCornerRadius),
                ambientColor = if (isEnabled) BrandPrimary.copy(alpha = 0.2f) else Color.Transparent,
                spotColor = Color.Transparent
            )
            .clip(RoundedCornerShape(metrics.cardCornerRadius))
            .background(backgroundColor)
            .border(
                width = if (isEnabled) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(metrics.cardCornerRadius)
            )
            .padding(if (metrics.compactWidth) 16.dp else 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(metrics.iconBoxSize)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isEnabled) {
                                Brush.linearGradient(
                                    colors = listOf(BrandPrimary, BrandAccent)
                                )
                            } else {
                                Brush.linearGradient(
                                    colors = listOf(
                                        if (isDark) Color(0xFF27272A) else Color(0xFFF1F5F9),
                                        if (isDark) Color(0xFF1F1F23) else Color(0xFFE2E8F0)
                                    )
                                )
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isEnabled) Color.White else (if (isDark) Color(0xFF52525B) else Color(0xFF64748B)),
                        modifier = Modifier.size(metrics.iconSize)
                    )
                }

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = getOnboardingTextPrimary(isDark)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = getOnboardingTextSecondary(isDark),
                        lineHeight = 20.sp
                    )
                }
            }

            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = BrandPrimary,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = if (isDark) Color(0xFF27272A) else Color(0xFFCBD5E1)
                ),
                modifier = Modifier.padding(start = 12.dp)
            )
        }

        expandableContent?.let { it() }
    }
}