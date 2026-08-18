package me.avinas.vanderwaals.ui.analytics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.avinas.vanderwaals.BuildConfig
import me.avinas.vanderwaals.ui.onboarding.rememberOnboardingLayoutMetrics
import me.avinas.vanderwaals.ui.settings.RadicalButtonVariant
import me.avinas.vanderwaals.ui.settings.RadicalDivider
import me.avinas.vanderwaals.ui.settings.RadicalIconBadge
import me.avinas.vanderwaals.ui.settings.RadicalNoticeCard
import me.avinas.vanderwaals.ui.settings.RadicalPalette
import me.avinas.vanderwaals.ui.settings.RadicalSectionHeader
import me.avinas.vanderwaals.ui.settings.RadicalTactileBackdrop
import me.avinas.vanderwaals.ui.settings.RadicalTactileButton
import me.avinas.vanderwaals.ui.settings.RadicalTactileCard
import me.avinas.vanderwaals.ui.settings.RadicalWatermarkBadge
import me.avinas.vanderwaals.ui.theme.*
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    onNavigateBack: () -> Unit,
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isDark = LocalThemeIsDark.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val metrics = rememberOnboardingLayoutMetrics()
    val haptic = LocalHapticFeedback.current

    androidx.activity.compose.BackHandler { onNavigateBack() }

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
                                text = "Personalization",
                                fontFamily = PlayfairDisplayFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp,
                                color = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A),
                                letterSpacing = (-0.3).sp
                            )
                            Text(
                                text = "Taste Profile & Learning Insights",
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
                                    elevation = if (isDark) 3.dp else 2.dp,
                                    shape = CircleShape,
                                    ambientColor = Color.Black.copy(alpha = 0.20f),
                                    spotColor = Color.Black.copy(alpha = 0.15f)
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
                                            Color.White.copy(alpha = if (isDark) 0.20f else 0.85f),
                                            if (isDark) Color.Black.copy(alpha = 0.40f) else Color(0xFFCBD5E1)
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
                    actions = {
                        Box(
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(40.dp)
                                .shadow(
                                    elevation = if (isDark) 3.dp else 2.dp,
                                    shape = CircleShape,
                                    ambientColor = Color.Black.copy(alpha = 0.20f),
                                    spotColor = Color.Black.copy(alpha = 0.15f)
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
                                            Color.White.copy(alpha = if (isDark) 0.20f else 0.85f),
                                            if (isDark) Color.Black.copy(alpha = 0.40f) else Color(0xFFCBD5E1)
                                        )
                                    ),
                                    shape = CircleShape
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(bounded = true),
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.refresh()
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh telemetry",
                                tint = if (isDark) RadicalPalette.EmeraldJade else Color(0xFF064E3B),
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
            when {
                state.isLoading -> {
                    InsightsLoading(isDark = isDark, paddingValues = paddingValues)
                }
                state.error != null -> {
                    InsightsError(
                        error = state.error!!,
                        isDark = isDark,
                        paddingValues = paddingValues,
                        onRetry = { viewModel.refresh() }
                    )
                }
                else -> {
                    InsightsContent(
                        state = state,
                        isDark = isDark,
                        metrics = metrics,
                        paddingValues = paddingValues
                    )
                }
            }
        }
    }
}


@Composable
private fun InsightsContent(
    state: AnalyticsState,
    isDark: Boolean,
    metrics: me.avinas.vanderwaals.ui.onboarding.OnboardingLayoutMetrics,
    paddingValues: PaddingValues
) {
    val isWide = metrics.expandedWidth

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
        item(key = "hero_telemetry") {
            RadicalSectionHeader(
                title = "Neural Taste Telemetry",
                isDark = isDark,
                accentColor = RadicalPalette.EmeraldJade
            )
            InsightsHeroCard(state = state, isDark = isDark)
        }

        item(key = "status_quadrant") {
            InsightsStatusRow(state = state, isDark = isDark, isWide = isWide)
        }

        if (state.insights.isNotEmpty()) {
            item(key = "aesthetic_signals") {
                RadicalSectionHeader(
                    title = "Aesthetic Signals & Diagnostics",
                    isDark = isDark,
                    accentColor = RadicalPalette.AmethystPurple
                )
                AestheticSignalsFlatCard(
                    insights = state.insights,
                    isDark = isDark
                )
            }
        }

        if (state.isPersonalizationWorking || state.hasOriginalEmbedding) {
            item(key = "dual_anchor_learning") {
                RadicalSectionHeader(
                    title = "Learning Dynamics",
                    isDark = isDark,
                    accentColor = RadicalPalette.ElectricAzure
                )
                RadicalTactileCard(isDark = isDark) {
                    LearningProgressContent(state = state, isDark = isDark)
                }
            }
        }

        if (state.topCategories.isNotEmpty()) {
            item(key = "taste_affinity_spectrum") {
                RadicalSectionHeader(
                    title = "Category Affinities",
                    isDark = isDark,
                    accentColor = RadicalPalette.CoralRose
                )
                RadicalTactileCard(isDark = isDark) {
                    CategoryContent(state = state, isDark = isDark)
                }
            }
        }

        if (state.totalWallpapersViewed > 0 || state.isPersonalizationWorking) {
            item(key = "match_precision") {
                RadicalSectionHeader(
                    title = "Recommendation Precision",
                    isDark = isDark,
                    accentColor = RadicalPalette.TealCyan
                )
                RadicalTactileCard(isDark = isDark) {
                    MatchQualityContent(state = state, isDark = isDark)
                }
            }
        }

        if (state.totalFeedbackCount > 5 || state.isPersonalizationWorking) {
            item(key = "advanced_internals") {
                RadicalSectionHeader(
                    title = "Model Statistics",
                    isDark = isDark,
                    accentColor = RadicalPalette.RoyalIndigo
                )
                RadicalTactileCard(isDark = isDark) {
                    AdvancedContent(state = state, isDark = isDark)
                }
            }
        }

        item(key = "curation_tips") {
            RadicalNoticeCard(
                title = "Calibration Tip",
                message = "Liking wallpapers teaches Vanderwaals the styles you enjoy. Hiding wallpapers helps filter out colors and themes you dislike.",
                icon = Icons.Default.Lightbulb,
                accentColor = RadicalPalette.RadiantAmber,
                isDark = isDark
            )
        }

        item(key = "watermark") {
            Spacer(modifier = Modifier.height(4.dp))
            RadicalWatermarkBadge(
                isDark = isDark,
                version = BuildConfig.VERSION_NAME
            )
        }
    }
}


@Composable
private fun InsightsHeroCard(
    state: AnalyticsState,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val (qualityLabel, qualitySub) = when (state.personalizationQuality) {
        PersonalizationQuality.NOT_INITIALIZED -> "New Profile" to "Like or hide wallpapers to train your taste profile"
        PersonalizationQuality.LEARNING -> "Learning" to "${state.totalFeedbackCount} ratings recorded · Profile forming"
        PersonalizationQuality.DEVELOPING -> "Developing" to "${state.totalFeedbackCount} ratings recorded · Preferences emerging"
        PersonalizationQuality.ESTABLISHED -> "Established" to "${state.totalFeedbackCount} ratings recorded · Strong style matches"
        PersonalizationQuality.REFINED -> "Refined" to "${state.totalFeedbackCount} ratings recorded · High-precision curation"
        PersonalizationQuality.EXCELLENT -> "Tuned" to "${state.totalFeedbackCount} ratings recorded · Fully personalized curation"
    }

    val targetProgress = when (state.personalizationQuality) {
        PersonalizationQuality.NOT_INITIALIZED -> 0.08f
        PersonalizationQuality.LEARNING -> 0.22f
        PersonalizationQuality.DEVELOPING -> 0.48f
        PersonalizationQuality.ESTABLISHED -> 0.72f
        PersonalizationQuality.REFINED -> 0.88f
        PersonalizationQuality.EXCELLENT -> 1f
    }

    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "heroProgress"
    )

    val dialAccent = when (state.personalizationQuality) {
        PersonalizationQuality.NOT_INITIALIZED -> RadicalPalette.PlatinumSilver
        PersonalizationQuality.LEARNING -> RadicalPalette.RadiantAmber
        PersonalizationQuality.DEVELOPING -> RadicalPalette.SapphireBlue
        PersonalizationQuality.ESTABLISHED -> RadicalPalette.TealCyan
        PersonalizationQuality.REFINED -> RadicalPalette.EmeraldJade
        PersonalizationQuality.EXCELLENT -> RadicalPalette.AmethystPurple
    }

    RadicalTactileCard(
        isDark = isDark,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(RadicalPalette.EmeraldJade)
                            .shadow(
                                elevation = 2.dp,
                                shape = CircleShape,
                                ambientColor = Color.Black.copy(alpha = 0.25f),
                                spotColor = Color.Black.copy(alpha = 0.18f)
                            )
                    )
                    Text(
                        text = "TASTE ENGINE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) RadicalPalette.EmeraldJade else RadicalPalette.LightCardTextSecondary,
                        letterSpacing = 1.2.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isDark) dialAccent.copy(alpha = 0.18f) else dialAccent.copy(alpha = 0.10f)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isDark) dialAccent.copy(alpha = 0.40f) else dialAccent.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (state.mode == "personalized") "PERSONALIZED" else "ADAPTIVE AUTO",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = dialAccent,
                        letterSpacing = 0.6.sp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(72.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(72.dp)) {
                        val strokeWidth = 6.dp.toPx()
                        val diameter = size.minDimension - strokeWidth
                        val radius = diameter / 2f
                        val centerOffset = Offset(size.width / 2f, size.height / 2f)

                        drawCircle(
                            color = if (isDark) RadicalPalette.DarkCardWell else RadicalPalette.LightCardWell,
                            radius = radius,
                            center = centerOffset,
                            style = Stroke(width = strokeWidth)
                        )

                        drawArc(
                            brush = Brush.sweepGradient(
                                listOf(
                                    dialAccent.copy(alpha = 0.7f),
                                    dialAccent,
                                    dialAccent.copy(alpha = 0.9f)
                                )
                            ),
                            startAngle = -90f,
                            sweepAngle = animatedProgress * 360f,
                            useCenter = false,
                            topLeft = Offset(centerOffset.x - radius, centerOffset.y - radius),
                            size = Size(diameter, diameter),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${(animatedProgress * 100).toInt()}%",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isDark) RadicalPalette.DarkCardTextPrimary else RadicalPalette.LightCardTextPrimary
                        )
                        Text(
                            text = "CALIB",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) RadicalPalette.DarkCardTextTertiary else RadicalPalette.LightCardTextTertiary,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = qualityLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) RadicalPalette.DarkCardTextPrimary else RadicalPalette.LightCardTextPrimary
                    )
                    Text(
                        text = qualitySub,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) RadicalPalette.DarkCardTextSecondary else RadicalPalette.LightCardTextSecondary,
                        lineHeight = 18.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (isDark) RadicalPalette.DarkCardWell else RadicalPalette.LightCardWell
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedProgress.coerceIn(0.04f, 1f))
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(dialAccent.copy(alpha = 0.8f), dialAccent)
                            )
                        )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = if (isDark) RadicalPalette.DarkCardTextSecondary else RadicalPalette.LightCardTextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "${state.likeCount} likes · ${state.dislikeCount} hides",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDark) RadicalPalette.DarkCardTextSecondary else RadicalPalette.LightCardTextSecondary
                    )
                }

                val (trendIcon, trendText, trendColor) = when (state.similarityTrend) {
                    SimilarityTrend.IMPROVING -> Triple(Icons.AutoMirrored.Filled.TrendingUp, "Trend: Improving", RadicalPalette.EmeraldJade)
                    SimilarityTrend.DECLINING -> Triple(Icons.AutoMirrored.Filled.TrendingDown, "Trend: Needs Signal", RadicalPalette.RubyRed)
                    SimilarityTrend.STABLE -> Triple(Icons.AutoMirrored.Filled.TrendingFlat, "Trend: Stable", RadicalPalette.SapphireBlue)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isDark) trendColor.copy(alpha = 0.14f) else trendColor.copy(alpha = 0.10f)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isDark) trendColor.copy(alpha = 0.35f) else trendColor.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = trendIcon,
                            contentDescription = null,
                            tint = trendColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = trendText,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = trendColor
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun InsightsStatusRow(
    state: AnalyticsState,
    isDark: Boolean,
    isWide: Boolean
) {
    val durationText = remember(state.averageWallpaperDuration) {
        val sec = state.averageWallpaperDuration
        when {
            sec <= 0 -> "0s"
            sec < 60 -> "${sec}s"
            sec < 3600 -> "${sec / 60}m"
            else -> String.format("%.1fh", sec / 3600f)
        }
    }

    val items = listOf(
        InsightStatItem("Evaluated", state.totalWallpapersViewed.toString(), Icons.Default.Visibility, RadicalPalette.SapphireBlue),
        InsightStatItem("Avg Dwell", durationText, Icons.Default.Schedule, RadicalPalette.EmeraldJade),
        InsightStatItem("Top Focus", state.mostLikedCategory?.replaceFirstChar { it.uppercase() } ?: "—", Icons.Default.AutoAwesome, RadicalPalette.CoralRose),
        InsightStatItem("Curiosity", "${(state.explorationRate * 100).toInt()}%", Icons.Default.Explore, RadicalPalette.AmethystPurple)
    )

    if (isWide) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items.forEach { item ->
                InsightsStatCell(
                    label = item.label,
                    value = item.value,
                    icon = item.icon,
                    accentColor = item.accentColor,
                    isDark = isDark,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                InsightsStatCell(
                    label = items[0].label,
                    value = items[0].value,
                    icon = items[0].icon,
                    accentColor = items[0].accentColor,
                    isDark = isDark,
                    modifier = Modifier.weight(1f)
                )
                InsightsStatCell(
                    label = items[1].label,
                    value = items[1].value,
                    icon = items[1].icon,
                    accentColor = items[1].accentColor,
                    isDark = isDark,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                InsightsStatCell(
                    label = items[2].label,
                    value = items[2].value,
                    icon = items[2].icon,
                    accentColor = items[2].accentColor,
                    isDark = isDark,
                    modifier = Modifier.weight(1f)
                )
                InsightsStatCell(
                    label = items[3].label,
                    value = items[3].value,
                    icon = items[3].icon,
                    accentColor = items[3].accentColor,
                    isDark = isDark,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private data class InsightStatItem(
    val label: String,
    val value: String,
    val icon: ImageVector,
    val accentColor: Color
)

@Composable
private fun InsightsStatCell(
    label: String,
    value: String,
    icon: ImageVector,
    accentColor: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isDark) RadicalPalette.DarkCardWell else RadicalPalette.LightCardWell
            )
            .border(
                width = 1.dp,
                brush = if (isDark) {
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.04f)))
                } else {
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.35f)))
                },
                shape = RoundedCornerShape(14.dp)
            )
            .padding(vertical = 12.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .shadow(
                        elevation = if (isDark) 2.dp else 3.dp,
                        shape = RoundedCornerShape(10.dp),
                        ambientColor = accentColor.copy(alpha = 0.3f),
                        spotColor = accentColor.copy(alpha = 0.2f)
                    )
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isDark) {
                            Brush.verticalGradient(
                                listOf(accentColor.copy(alpha = 0.28f), accentColor.copy(alpha = 0.12f))
                            )
                        } else {
                            Brush.verticalGradient(
                                listOf(Color.White, Color(0xFFF1F5F9))
                            )
                        }
                    )
                    .border(
                        width = 1.dp,
                        brush = if (isDark) {
                            Brush.verticalGradient(listOf(accentColor.copy(alpha = 0.6f), accentColor.copy(alpha = 0.2f)))
                        } else {
                            Brush.verticalGradient(listOf(Color.White, Color(0xFFCBD5E1)))
                        },
                        shape = RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) RadicalPalette.DarkCardTextSecondary else RadicalPalette.LightCardTextSecondary,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = if (isDark) RadicalPalette.DarkCardTextPrimary else RadicalPalette.LightCardTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}


@Composable
private fun AestheticSignalsFlatCard(
    insights: List<SmartInsight>,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(16.dp)

    val cardBackground = Color.White
    val cardBorderColor = Color(0xFFE2E8F0)
    val dividerColor = Color(0xFFF1F5F9)
    val titleTextColor = Color(0xFF0F172A)
    val descTextColor = Color(0xFF475569)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(cardBackground)
            .border(width = 1.dp, color = cardBorderColor, shape = cardShape)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            insights.forEachIndexed { index, insight ->
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(dividerColor)
                    )
                }

                val accent = when (insight.type) {
                    InsightType.SUCCESS -> RadicalPalette.EmeraldJade
                    InsightType.LEARNING -> RadicalPalette.SapphireBlue
                    InsightType.NEED_FEEDBACK -> RadicalPalette.RadiantAmber
                    InsightType.DISCOVERY -> RadicalPalette.AmethystPurple
                    InsightType.TIP -> RadicalPalette.TealCyan
                    InsightType.WARNING -> RadicalPalette.RubyRed
                }

                val icon = when (insight.type) {
                    InsightType.SUCCESS -> Icons.Default.CheckCircle
                    InsightType.LEARNING -> Icons.Default.Psychology
                    InsightType.NEED_FEEDBACK -> Icons.AutoMirrored.Filled.HelpOutline
                    InsightType.DISCOVERY -> Icons.Default.Lightbulb
                    InsightType.TIP -> Icons.Default.Info
                    InsightType.WARNING -> Icons.Default.WarningAmber
                }

                val tagLabel = when (insight.type) {
                    InsightType.SUCCESS -> "OPTIMAL ALIGNMENT"
                    InsightType.LEARNING -> "LEARNING SIGNAL"
                    InsightType.NEED_FEEDBACK -> "INPUT REQUIRED"
                    InsightType.DISCOVERY -> "AESTHETIC PATTERN"
                    InsightType.TIP -> "CURATION TIP"
                    InsightType.WARNING -> "CALIBRATION NOTICE"
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(accent.copy(alpha = 0.10f))
                                .border(
                                    width = 1.dp,
                                    color = accent.copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(accent)
                                )
                                Text(
                                    text = tagLabel,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accent,
                                    letterSpacing = 0.6.sp
                                )
                            }
                        }

                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = insight.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = titleTextColor,
                            fontSize = 15.sp,
                            letterSpacing = (-0.2).sp
                        )
                        Text(
                            text = insight.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = descTextColor,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                    }

                    if (insight.actionable && !insight.actionText.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(accent.copy(alpha = 0.12f))
                                .border(
                                    width = 1.dp,
                                    color = accent.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = insight.actionText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = accent
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun LearningProgressContent(
    state: AnalyticsState,
    isDark: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (state.hasOriginalEmbedding) {
            val originalWeight = state.originalAnchorInfluence.coerceAtLeast(1f)
            val learnedWeight = state.learnedAnchorInfluence.coerceAtLeast(1f)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Anchor Vector Balance",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDark) RadicalPalette.DarkCardTextPrimary else RadicalPalette.LightCardTextPrimary
                    )
                    Text(
                        text = "Original ${state.originalAnchorInfluence.toInt()}% · Learned ${state.learnedAnchorInfluence.toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = RadicalPalette.ElectricAzure
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(
                            if (isDark) RadicalPalette.DarkCardWell else RadicalPalette.LightCardWell
                        )
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .weight(originalWeight)
                                .fillMaxHeight()
                                .background(
                                    if (isDark) Color(0xFF94A3B8) else Color(0xFFCBD5E1)
                                )
                        )
                        Box(
                            modifier = Modifier
                                .weight(learnedWeight)
                                .fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(RadicalPalette.ElectricAzure, RadicalPalette.SapphireBlue)
                                    )
                                )
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Foundational Taste Baseline",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isDark) RadicalPalette.DarkCardTextTertiary else RadicalPalette.LightCardTextTertiary
                    )
                    Text(
                        text = "Dynamic Learned Preferences",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isDark) RadicalPalette.DarkCardTextSecondary else RadicalPalette.LightCardTextSecondary
                    )
                }
            }

            RadicalDivider(isDark = isDark)
        } else {
            Text(
                text = "Vanderwaals actively tunes the recommendation vector with each like and hide on your device.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isDark) RadicalPalette.DarkCardTextSecondary else RadicalPalette.LightCardTextSecondary,
                lineHeight = 19.sp
            )
            RadicalDivider(isDark = isDark)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadicalIconBadge(
                    icon = Icons.Default.Explore,
                    accentColor = RadicalPalette.AmethystPurple,
                    isDark = isDark,
                    size = 36.dp,
                    iconSize = 18.dp
                )
                Column {
                    Text(
                        text = "Curiosity & Discovery Rate",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDark) RadicalPalette.DarkCardTextPrimary else RadicalPalette.LightCardTextPrimary
                    )
                    Text(
                        text = "Automatic variety vs affinity balance",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) RadicalPalette.DarkCardTextSecondary else RadicalPalette.LightCardTextSecondary
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isDark) RadicalPalette.AmethystPurple.copy(alpha = 0.15f) else RadicalPalette.AmethystPurple.copy(alpha = 0.10f)
                    )
                    .border(
                        width = 1.dp,
                        color = RadicalPalette.AmethystPurple.copy(alpha = if (isDark) 0.35f else 0.25f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${(state.explorationRate * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = RadicalPalette.AmethystPurple
                )
            }
        }
    }
}


private val CategorySpectrumColors = listOf(
    RadicalPalette.EmeraldJade,
    RadicalPalette.AmethystPurple,
    RadicalPalette.ElectricAzure,
    RadicalPalette.RadiantAmber,
    RadicalPalette.TealCyan,
    RadicalPalette.CoralRose,
    RadicalPalette.SapphireBlue,
    RadicalPalette.RoyalIndigo
)

@Composable
private fun CategoryAestheticIcon(
    category: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val cat = category.lowercase().trim().replace("-", "_").replace(" ", "_")

    when {
        cat.contains("horror") || cat.contains("skull") || cat.contains("gothic") || cat.contains("creepy") -> {
            Canvas(modifier = modifier) {
                val w = size.width
                val h = size.height
                // Cranium
                val skullPath = Path().apply {
                    moveTo(w * 0.22f, h * 0.50f)
                    cubicTo(w * 0.15f, h * 0.15f, w * 0.85f, h * 0.15f, w * 0.78f, h * 0.50f)
                    cubicTo(w * 0.76f, h * 0.62f, w * 0.65f, h * 0.68f, w * 0.65f, h * 0.82f)
                    lineTo(w * 0.35f, h * 0.82f)
                    cubicTo(w * 0.35f, h * 0.68f, w * 0.24f, h * 0.62f, w * 0.22f, h * 0.50f)
                    close()
                }
                drawPath(skullPath, color = tint)
                // Left Eye socket
                drawOval(
                    color = Color.Black.copy(alpha = 0.85f),
                    topLeft = Offset(w * 0.28f, h * 0.38f),
                    size = Size(w * 0.18f, h * 0.20f)
                )
                // Right Eye socket
                drawOval(
                    color = Color.Black.copy(alpha = 0.85f),
                    topLeft = Offset(w * 0.54f, h * 0.38f),
                    size = Size(w * 0.18f, h * 0.20f)
                )
                // Nasal cavity
                val nosePath = Path().apply {
                    moveTo(w * 0.50f, h * 0.58f)
                    lineTo(w * 0.44f, h * 0.68f)
                    lineTo(w * 0.56f, h * 0.68f)
                    close()
                }
                drawPath(nosePath, color = Color.Black.copy(alpha = 0.85f))
                // Teeth vertical slits
                drawLine(
                    color = Color.Black.copy(alpha = 0.85f),
                    start = Offset(w * 0.44f, h * 0.75f),
                    end = Offset(w * 0.44f, h * 0.82f),
                    strokeWidth = 1.2f.dp.toPx()
                )
                drawLine(
                    color = Color.Black.copy(alpha = 0.85f),
                    start = Offset(w * 0.56f, h * 0.75f),
                    end = Offset(w * 0.56f, h * 0.82f),
                    strokeWidth = 1.2f.dp.toPx()
                )
            }
        }

        cat.contains("forest") || cat.contains("tree") || cat.contains("wood") || cat.contains("jungle") || cat.contains("foliage") -> {
            Canvas(modifier = modifier) {
                val w = size.width
                val h = size.height
                // Center Tall Pine
                val centerPine = Path().apply {
                    moveTo(w * 0.50f, h * 0.15f)
                    lineTo(w * 0.72f, h * 0.62f)
                    lineTo(w * 0.56f, h * 0.62f)
                    lineTo(w * 0.56f, h * 0.80f)
                    lineTo(w * 0.44f, h * 0.80f)
                    lineTo(w * 0.44f, h * 0.62f)
                    lineTo(w * 0.28f, h * 0.62f)
                    close()
                }
                drawPath(centerPine, color = tint)
                // Left Pine
                val leftPine = Path().apply {
                    moveTo(w * 0.26f, h * 0.35f)
                    lineTo(w * 0.44f, h * 0.75f)
                    lineTo(w * 0.08f, h * 0.75f)
                    close()
                }
                drawPath(leftPine, color = tint.copy(alpha = 0.60f))
                // Right Pine
                val rightPine = Path().apply {
                    moveTo(w * 0.74f, h * 0.38f)
                    lineTo(w * 0.92f, h * 0.75f)
                    lineTo(w * 0.56f, h * 0.75f)
                    close()
                }
                drawPath(rightPine, color = tint.copy(alpha = 0.60f))
                // Ground line
                drawLine(
                    color = tint,
                    start = Offset(w * 0.05f, h * 0.82f),
                    end = Offset(w * 0.95f, h * 0.82f),
                    strokeWidth = 1.8f.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        cat.contains("aerial") || cat.contains("drone") || cat.contains("satellite") || cat.contains("topdown") -> {
            Canvas(modifier = modifier) {
                val w = size.width
                val h = size.height
                val strokeWidth = 1.8f.dp.toPx()
                // Central Fuselage / Camera lens
                drawCircle(color = tint, radius = w * 0.12f, center = Offset(w * 0.50f, h * 0.50f))
                drawCircle(
                    color = Color.White,
                    radius = w * 0.05f,
                    center = Offset(w * 0.50f, h * 0.50f)
                )
                // Diagonal cross arms
                drawLine(
                    color = tint,
                    start = Offset(w * 0.22f, h * 0.22f),
                    end = Offset(w * 0.78f, h * 0.78f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(w * 0.78f, h * 0.22f),
                    end = Offset(w * 0.22f, h * 0.78f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                // 4 Rotor Discs
                drawCircle(color = tint.copy(alpha = 0.55f), radius = w * 0.14f, center = Offset(w * 0.20f, h * 0.20f), style = Stroke(width = 1.2f.dp.toPx()))
                drawCircle(color = tint.copy(alpha = 0.55f), radius = w * 0.14f, center = Offset(w * 0.80f, h * 0.20f), style = Stroke(width = 1.2f.dp.toPx()))
                drawCircle(color = tint.copy(alpha = 0.55f), radius = w * 0.14f, center = Offset(w * 0.20f, h * 0.80f), style = Stroke(width = 1.2f.dp.toPx()))
                drawCircle(color = tint.copy(alpha = 0.55f), radius = w * 0.14f, center = Offset(w * 0.80f, h * 0.80f), style = Stroke(width = 1.2f.dp.toPx()))
            }
        }

        cat.contains("3d") || cat.contains("render") || cat.contains("cgi") || cat.contains("mesh") || cat.contains("lowpoly") -> {
            Canvas(modifier = modifier) {
                val w = size.width
                val h = size.height
                // Top isometric face
                val topFace = Path().apply {
                    moveTo(w * 0.50f, h * 0.15f)
                    lineTo(w * 0.85f, h * 0.35f)
                    lineTo(w * 0.50f, h * 0.55f)
                    lineTo(w * 0.15f, h * 0.35f)
                    close()
                }
                drawPath(topFace, color = tint)
                // Left face
                val leftFace = Path().apply {
                    moveTo(w * 0.15f, h * 0.35f)
                    lineTo(w * 0.50f, h * 0.55f)
                    lineTo(w * 0.50f, h * 0.90f)
                    lineTo(w * 0.15f, h * 0.70f)
                    close()
                }
                drawPath(leftFace, color = tint.copy(alpha = 0.65f))
                // Right face
                val rightFace = Path().apply {
                    moveTo(w * 0.50f, h * 0.55f)
                    lineTo(w * 0.85f, h * 0.35f)
                    lineTo(w * 0.85f, h * 0.70f)
                    lineTo(w * 0.50f, h * 0.90f)
                    close()
                }
                drawPath(rightFace, color = tint.copy(alpha = 0.40f))
                // 3D Perspective Wireframe Edges
                val wireframe = Path().apply {
                    moveTo(w * 0.50f, h * 0.15f)
                    lineTo(w * 0.85f, h * 0.35f)
                    lineTo(w * 0.85f, h * 0.70f)
                    lineTo(w * 0.50f, h * 0.90f)
                    lineTo(w * 0.15f, h * 0.70f)
                    lineTo(w * 0.15f, h * 0.35f)
                    close()
                    moveTo(w * 0.50f, h * 0.55f)
                    lineTo(w * 0.50f, h * 0.90f)
                    moveTo(w * 0.50f, h * 0.55f)
                    lineTo(w * 0.15f, h * 0.35f)
                    moveTo(w * 0.50f, h * 0.55f)
                    lineTo(w * 0.85f, h * 0.35f)
                }
                drawPath(wireframe, color = Color.White.copy(alpha = 0.4f), style = Stroke(width = 1.dp.toPx()))
            }
        }

        cat.contains("nature") || cat.contains("landscape") || cat.contains("mountain") || cat.contains("hills") -> {
            Canvas(modifier = modifier) {
                val w = size.width
                val h = size.height
                // Sun
                drawCircle(
                    color = tint.copy(alpha = 0.4f),
                    radius = w * 0.15f,
                    center = Offset(w * 0.72f, h * 0.28f)
                )
                // Mountain 1 (Back)
                val backPath = Path().apply {
                    moveTo(w * 0.25f, h * 0.75f)
                    lineTo(w * 0.60f, h * 0.35f)
                    lineTo(w * 0.95f, h * 0.75f)
                    close()
                }
                drawPath(backPath, color = tint.copy(alpha = 0.45f))
                // Mountain 2 (Front)
                val frontPath = Path().apply {
                    moveTo(w * 0.05f, h * 0.75f)
                    lineTo(w * 0.38f, h * 0.42f)
                    lineTo(w * 0.72f, h * 0.75f)
                    close()
                }
                drawPath(frontPath, color = tint)
                // Ground line
                drawLine(
                    color = tint,
                    start = Offset(w * 0.05f, h * 0.78f),
                    end = Offset(w * 0.95f, h * 0.78f),
                    strokeWidth = 1.8f.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        cat.contains("ocean") || cat.contains("sea") || cat.contains("water") || cat.contains("wave") || cat.contains("beach") -> {
            Canvas(modifier = modifier) {
                val w = size.width
                val h = size.height
                // Cresting Wave 1
                val wave1 = Path().apply {
                    moveTo(w * 0.05f, h * 0.45f)
                    cubicTo(w * 0.30f, h * 0.20f, w * 0.60f, h * 0.20f, w * 0.75f, h * 0.35f)
                    cubicTo(w * 0.80f, h * 0.40f, w * 0.75f, h * 0.48f, w * 0.68f, h * 0.48f)
                    cubicTo(w * 0.50f, h * 0.48f, w * 0.30f, h * 0.65f, w * 0.05f, h * 0.65f)
                    close()
                }
                drawPath(wave1, color = tint)
                // Wave 2
                val wave2 = Path().apply {
                    moveTo(w * 0.25f, h * 0.75f)
                    cubicTo(w * 0.50f, h * 0.55f, w * 0.80f, h * 0.55f, w * 0.95f, h * 0.70f)
                    lineTo(w * 0.95f, h * 0.85f)
                    lineTo(w * 0.25f, h * 0.85f)
                    close()
                }
                drawPath(wave2, color = tint.copy(alpha = 0.5f))
                // Spray droplet
                drawCircle(color = tint, radius = w * 0.04f, center = Offset(w * 0.82f, h * 0.30f))
            }
        }

        cat.contains("sunset") || cat.contains("sunrise") || cat.contains("dawn") || cat.contains("dusk") -> {
            Canvas(modifier = modifier) {
                val w = size.width
                val h = size.height
                // Half Sun above horizon
                val sunPath = Path().apply {
                    moveTo(w * 0.25f, h * 0.55f)
                    arcTo(
                        rect = Rect(w * 0.25f, h * 0.20f, w * 0.75f, h * 0.70f),
                        startAngleDegrees = 180f,
                        sweepAngleDegrees = 180f,
                        forceMoveTo = false
                    )
                    close()
                }
                drawPath(sunPath, color = tint)
                // Radiating Sun rays
                drawLine(color = tint.copy(alpha = 0.6f), start = Offset(w * 0.50f, h * 0.10f), end = Offset(w * 0.50f, h * 0.22f), strokeWidth = 1.8f.dp.toPx(), cap = StrokeCap.Round)
                drawLine(color = tint.copy(alpha = 0.6f), start = Offset(w * 0.22f, h * 0.22f), end = Offset(w * 0.32f, h * 0.32f), strokeWidth = 1.8f.dp.toPx(), cap = StrokeCap.Round)
                drawLine(color = tint.copy(alpha = 0.6f), start = Offset(w * 0.78f, h * 0.22f), end = Offset(w * 0.68f, h * 0.32f), strokeWidth = 1.8f.dp.toPx(), cap = StrokeCap.Round)
                // Horizon and reflection lines
                drawLine(color = tint, start = Offset(w * 0.08f, h * 0.58f), end = Offset(w * 0.92f, h * 0.58f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                drawLine(color = tint.copy(alpha = 0.5f), start = Offset(w * 0.25f, h * 0.70f), end = Offset(w * 0.75f, h * 0.70f), strokeWidth = 1.5f.dp.toPx(), cap = StrokeCap.Round)
                drawLine(color = tint.copy(alpha = 0.3f), start = Offset(w * 0.38f, h * 0.80f), end = Offset(w * 0.62f, h * 0.80f), strokeWidth = 1.5f.dp.toPx(), cap = StrokeCap.Round)
            }
        }

        cat.contains("minimal") || cat.contains("clean") || cat.contains("simple") -> {
            Canvas(modifier = modifier) {
                val w = size.width
                val h = size.height
                // Minimalist balance circle
                drawCircle(
                    color = tint,
                    radius = w * 0.18f,
                    center = Offset(w * 0.65f, h * 0.32f)
                )
                // Minimalist offset rectangle
                drawRoundRect(
                    color = tint.copy(alpha = 0.35f),
                    topLeft = Offset(w * 0.15f, h * 0.42f),
                    size = Size(w * 0.45f, h * 0.32f),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
                // Horizon line
                drawLine(
                    color = tint,
                    start = Offset(w * 0.12f, h * 0.78f),
                    end = Offset(w * 0.88f, h * 0.78f),
                    strokeWidth = 1.8f.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        cat.contains("abstract") || cat.contains("pattern") || cat.contains("geometric") -> {
            Canvas(modifier = modifier) {
                val w = size.width
                val h = size.height
                val strokeWidth = 2.dp.toPx()
                // Fluid S-curve ribbon
                val wavePath = Path().apply {
                    moveTo(w * 0.15f, h * 0.75f)
                    cubicTo(w * 0.15f, h * 0.35f, w * 0.85f, h * 0.65f, w * 0.85f, h * 0.25f)
                }
                drawPath(
                    wavePath,
                    color = tint,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                // Secondary wave
                val wavePath2 = Path().apply {
                    moveTo(w * 0.25f, h * 0.85f)
                    cubicTo(w * 0.25f, h * 0.50f, w * 0.95f, h * 0.80f, w * 0.95f, h * 0.40f)
                }
                drawPath(
                    wavePath2,
                    color = tint.copy(alpha = 0.4f),
                    style = Stroke(width = strokeWidth * 0.8f, cap = StrokeCap.Round)
                )
                // Radiant orbit node
                drawCircle(
                    color = tint,
                    radius = w * 0.12f,
                    center = Offset(w * 0.35f, h * 0.28f)
                )
            }
        }

        cat.contains("space") || cat.contains("galaxy") || cat.contains("cosmos") || cat.contains("planet") || cat.contains("nebula") || cat.contains("star") -> {
            Canvas(modifier = modifier) {
                val w = size.width
                val h = size.height
                // Planet body
                drawCircle(
                    color = tint,
                    radius = w * 0.22f,
                    center = Offset(w * 0.50f, h * 0.50f)
                )
                // Planetary Ring
                drawOval(
                    color = tint.copy(alpha = 0.55f),
                    topLeft = Offset(w * 0.10f, h * 0.38f),
                    size = Size(w * 0.80f, h * 0.24f),
                    style = Stroke(width = 1.8f.dp.toPx())
                )
                // Distant star
                drawCircle(
                    color = tint.copy(alpha = 0.85f),
                    radius = w * 0.06f,
                    center = Offset(w * 0.82f, h * 0.20f)
                )
            }
        }

        cat.contains("anime") || cat.contains("manga") || cat.contains("waifu") || cat.contains("character") || cat.contains("animated") -> {
            Canvas(modifier = modifier) {
                val w = size.width
                val h = size.height
                // Stylized Anime Eye Brow
                val browPath = Path().apply {
                    moveTo(w * 0.15f, h * 0.38f)
                    cubicTo(w * 0.35f, h * 0.22f, w * 0.65f, h * 0.22f, w * 0.85f, h * 0.38f)
                }
                drawPath(
                    browPath,
                    color = tint,
                    style = Stroke(width = 2.4f.dp.toPx(), cap = StrokeCap.Round)
                )
                // Iris
                drawOval(
                    color = tint.copy(alpha = 0.75f),
                    topLeft = Offset(w * 0.32f, h * 0.32f),
                    size = Size(w * 0.36f, h * 0.42f)
                )
                // Sparkle Highlight
                drawCircle(
                    color = Color.White,
                    radius = w * 0.07f,
                    center = Offset(w * 0.42f, h * 0.44f)
                )
                drawCircle(
                    color = Color.White,
                    radius = w * 0.035f,
                    center = Offset(w * 0.58f, h * 0.58f)
                )
                // Lower eye lash
                val lowerPath = Path().apply {
                    moveTo(w * 0.30f, h * 0.75f)
                    quadraticTo(w * 0.50f, h * 0.80f, w * 0.70f, h * 0.75f)
                }
                drawPath(
                    lowerPath,
                    color = tint,
                    style = Stroke(width = 1.6f.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        cat.contains("dark") || cat.contains("gruvbox") || cat.contains("nord") || cat.contains("night") || cat.contains("black") || cat.contains("moody") || cat.contains("amoled") -> {
            Canvas(modifier = modifier) {
                val w = size.width
                val h = size.height
                // Crescent Moon
                val moonPath = Path().apply {
                    moveTo(w * 0.45f, h * 0.15f)
                    cubicTo(w * 0.75f, h * 0.25f, w * 0.75f, h * 0.75f, w * 0.45f, h * 0.85f)
                    cubicTo(w * 0.60f, h * 0.70f, w * 0.60f, h * 0.30f, w * 0.45f, h * 0.15f)
                    close()
                }
                drawPath(moonPath, color = tint)
                // 4-Point Starburst
                val starPath = Path().apply {
                    moveTo(w * 0.28f, h * 0.30f)
                    lineTo(w * 0.32f, h * 0.38f)
                    lineTo(w * 0.40f, h * 0.42f)
                    lineTo(w * 0.32f, h * 0.46f)
                    lineTo(w * 0.28f, h * 0.54f)
                    lineTo(w * 0.24f, h * 0.46f)
                    lineTo(w * 0.16f, h * 0.42f)
                    lineTo(w * 0.24f, h * 0.38f)
                    close()
                }
                drawPath(starPath, color = tint.copy(alpha = 0.85f))
            }
        }

        cat.contains("city") || cat.contains("urban") || cat.contains("building") || cat.contains("street") || cat.contains("metropolis") -> {
            Canvas(modifier = modifier) {
                val w = size.width
                val h = size.height
                // Back Tower
                drawRect(
                    color = tint.copy(alpha = 0.4f),
                    topLeft = Offset(w * 0.38f, h * 0.20f),
                    size = Size(w * 0.24f, h * 0.60f)
                )
                // Left Skyscraper
                drawRect(
                    color = tint.copy(alpha = 0.7f),
                    topLeft = Offset(w * 0.15f, h * 0.38f),
                    size = Size(w * 0.25f, h * 0.42f)
                )
                // Right Skyscraper
                drawRect(
                    color = tint,
                    topLeft = Offset(w * 0.60f, h * 0.45f),
                    size = Size(w * 0.25f, h * 0.35f)
                )
                // Baseline
                drawLine(
                    color = tint,
                    start = Offset(w * 0.10f, h * 0.80f),
                    end = Offset(w * 0.90f, h * 0.80f),
                    strokeWidth = 1.8f.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        cat.contains("architecture") || cat.contains("interior") || cat.contains("facade") -> {
            Canvas(modifier = modifier) {
                val w = size.width
                val h = size.height
                val strokeWidth = 1.8f.dp.toPx()
                // Modernist Arch
                val archPath = Path().apply {
                    moveTo(w * 0.25f, h * 0.80f)
                    lineTo(w * 0.25f, h * 0.45f)
                    cubicTo(w * 0.25f, h * 0.22f, w * 0.75f, h * 0.22f, w * 0.75f, h * 0.45f)
                    lineTo(w * 0.75f, h * 0.80f)
                }
                drawPath(
                    archPath,
                    color = tint,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                // Structural Pillar
                drawLine(
                    color = tint.copy(alpha = 0.5f),
                    start = Offset(w * 0.50f, h * 0.32f),
                    end = Offset(w * 0.50f, h * 0.80f),
                    strokeWidth = strokeWidth * 0.8f
                )
                // Base Plinth
                drawLine(
                    color = tint,
                    start = Offset(w * 0.15f, h * 0.82f),
                    end = Offset(w * 0.85f, h * 0.82f),
                    strokeWidth = strokeWidth * 1.2f,
                    cap = StrokeCap.Round
                )
            }
        }

        cat.contains("game") || cat.contains("gaming") || cat.contains("cyberpunk") || cat.contains("neon") -> {
            Canvas(modifier = modifier) {
                val w = size.width
                val h = size.height
                // Gamepad body
                val pad = Path().apply {
                    moveTo(w * 0.25f, h * 0.30f)
                    lineTo(w * 0.75f, h * 0.30f)
                    cubicTo(w * 0.92f, h * 0.30f, w * 0.95f, h * 0.75f, w * 0.80f, h * 0.80f)
                    lineTo(w * 0.70f, h * 0.60f)
                    lineTo(w * 0.30f, h * 0.60f)
                    lineTo(w * 0.20f, h * 0.80f)
                    cubicTo(w * 0.05f, h * 0.75f, w * 0.08f, h * 0.30f, w * 0.25f, h * 0.30f)
                    close()
                }
                drawPath(pad, color = tint)
                // D-Pad cross
                drawLine(color = Color.Black.copy(alpha = 0.7f), start = Offset(w * 0.28f, h * 0.42f), end = Offset(w * 0.28f, h * 0.54f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                drawLine(color = Color.Black.copy(alpha = 0.7f), start = Offset(w * 0.22f, h * 0.48f), end = Offset(w * 0.34f, h * 0.48f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                // Action Buttons
                drawCircle(color = Color.Black.copy(alpha = 0.7f), radius = w * 0.035f, center = Offset(w * 0.72f, h * 0.42f))
                drawCircle(color = Color.Black.copy(alpha = 0.7f), radius = w * 0.035f, center = Offset(w * 0.78f, h * 0.48f))
            }
        }

        cat.contains("car") || cat.contains("auto") || cat.contains("vehicle") -> {
            Canvas(modifier = modifier) {
                val w = size.width
                val h = size.height
                // Sports car silhouette profile
                val carBody = Path().apply {
                    moveTo(w * 0.08f, h * 0.65f)
                    lineTo(w * 0.15f, h * 0.52f)
                    cubicTo(w * 0.30f, h * 0.50f, w * 0.40f, h * 0.30f, w * 0.65f, h * 0.30f)
                    lineTo(w * 0.82f, h * 0.50f)
                    lineTo(w * 0.92f, h * 0.55f)
                    lineTo(w * 0.92f, h * 0.65f)
                    lineTo(w * 0.82f, h * 0.65f)
                    // Rear wheel cutout
                    cubicTo(w * 0.80f, h * 0.55f, w * 0.66f, h * 0.55f, w * 0.64f, h * 0.65f)
                    lineTo(w * 0.36f, h * 0.65f)
                    // Front wheel cutout
                    cubicTo(w * 0.34f, h * 0.55f, w * 0.20f, h * 0.55f, w * 0.18f, h * 0.65f)
                    close()
                }
                drawPath(carBody, color = tint)
                // Wheels
                drawCircle(color = tint, radius = w * 0.08f, center = Offset(w * 0.27f, h * 0.65f))
                drawCircle(color = Color.White, radius = w * 0.03f, center = Offset(w * 0.27f, h * 0.65f))
                drawCircle(color = tint, radius = w * 0.08f, center = Offset(w * 0.73f, h * 0.65f))
                drawCircle(color = Color.White, radius = w * 0.03f, center = Offset(w * 0.73f, h * 0.65f))
            }
        }

        cat.contains("animal") || cat.contains("wildlife") || cat.contains("pet") || cat.contains("fauna") -> {
            Canvas(modifier = modifier) {
                val w = size.width
                val h = size.height
                // Paw Print
                val pad = Path().apply {
                    moveTo(w * 0.30f, h * 0.75f)
                    cubicTo(w * 0.20f, h * 0.55f, w * 0.80f, h * 0.55f, w * 0.70f, h * 0.75f)
                    cubicTo(w * 0.60f, h * 0.85f, w * 0.40f, h * 0.85f, w * 0.30f, h * 0.75f)
                    close()
                }
                drawPath(pad, color = tint)
                // 4 toe pads
                drawOval(color = tint, topLeft = Offset(w * 0.18f, h * 0.38f), size = Size(w * 0.13f, h * 0.18f))
                drawOval(color = tint, topLeft = Offset(w * 0.36f, h * 0.25f), size = Size(w * 0.13f, h * 0.18f))
                drawOval(color = tint, topLeft = Offset(w * 0.53f, h * 0.25f), size = Size(w * 0.13f, h * 0.18f))
                drawOval(color = tint, topLeft = Offset(w * 0.71f, h * 0.38f), size = Size(w * 0.13f, h * 0.18f))
            }
        }

        cat.contains("art") || cat.contains("painting") || cat.contains("illustration") || cat.contains("drawing") -> {
            Canvas(modifier = modifier) {
                val w = size.width
                val h = size.height
                // Palette Body
                val palettePath = Path().apply {
                    moveTo(w * 0.25f, h * 0.70f)
                    cubicTo(w * 0.10f, h * 0.45f, w * 0.30f, h * 0.15f, w * 0.65f, h * 0.20f)
                    cubicTo(w * 0.90f, h * 0.25f, w * 0.90f, h * 0.65f, w * 0.70f, h * 0.75f)
                    cubicTo(w * 0.55f, h * 0.82f, w * 0.40f, h * 0.80f, w * 0.25f, h * 0.70f)
                    close()
                }
                drawPath(
                    palettePath,
                    color = tint,
                    style = Stroke(width = 1.8f.dp.toPx(), cap = StrokeCap.Round)
                )
                // Pigments
                drawCircle(color = tint, radius = w * 0.06f, center = Offset(w * 0.40f, h * 0.35f))
                drawCircle(color = tint.copy(alpha = 0.7f), radius = w * 0.06f, center = Offset(w * 0.60f, h * 0.36f))
                drawCircle(color = tint.copy(alpha = 0.45f), radius = w * 0.06f, center = Offset(w * 0.72f, h * 0.52f))
                // Thumb aperture
                drawCircle(color = tint, radius = w * 0.05f, center = Offset(w * 0.45f, h * 0.65f), style = Stroke(width = 1.2f.dp.toPx()))
            }
        }

        cat.contains("tech") || cat.contains("code") || cat.contains("cyber") || cat.contains("digital") -> {
            Canvas(modifier = modifier) {
                val w = size.width
                val h = size.height
                val strokeWidth = 1.6f.dp.toPx()
                // Microchip body
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.30f, h * 0.30f),
                    size = Size(w * 0.40f, h * 0.40f),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.8f),
                    topLeft = Offset(w * 0.40f, h * 0.40f),
                    size = Size(w * 0.20f, h * 0.20f),
                    cornerRadius = CornerRadius(1.dp.toPx())
                )
                // Pins
                drawLine(color = tint, start = Offset(w * 0.38f, h * 0.15f), end = Offset(w * 0.38f, h * 0.30f), strokeWidth = strokeWidth)
                drawLine(color = tint, start = Offset(w * 0.62f, h * 0.15f), end = Offset(w * 0.62f, h * 0.30f), strokeWidth = strokeWidth)
                drawLine(color = tint, start = Offset(w * 0.38f, h * 0.70f), end = Offset(w * 0.38f, h * 0.85f), strokeWidth = strokeWidth)
                drawLine(color = tint, start = Offset(w * 0.62f, h * 0.70f), end = Offset(w * 0.62f, h * 0.85f), strokeWidth = strokeWidth)
                drawLine(color = tint, start = Offset(w * 0.15f, h * 0.38f), end = Offset(w * 0.30f, h * 0.38f), strokeWidth = strokeWidth)
                drawLine(color = tint, start = Offset(w * 0.15f, h * 0.62f), end = Offset(w * 0.30f, h * 0.62f), strokeWidth = strokeWidth)
                drawLine(color = tint, start = Offset(w * 0.70f, h * 0.38f), end = Offset(w * 0.85f, h * 0.38f), strokeWidth = strokeWidth)
                drawLine(color = tint, start = Offset(w * 0.70f, h * 0.62f), end = Offset(w * 0.85f, h * 0.62f), strokeWidth = strokeWidth)
            }
        }

        cat.contains("gradient") || cat.contains("color") || cat.contains("vibrant") -> {
            Canvas(modifier = modifier) {
                val w = size.width
                val h = size.height
                // Overlapping color spectrum circles
                drawCircle(color = tint, radius = w * 0.25f, center = Offset(w * 0.38f, h * 0.42f))
                drawCircle(color = tint.copy(alpha = 0.55f), radius = w * 0.25f, center = Offset(w * 0.62f, h * 0.42f))
                drawCircle(color = tint.copy(alpha = 0.35f), radius = w * 0.22f, center = Offset(w * 0.50f, h * 0.65f))
            }
        }

        else -> {
            Canvas(modifier = modifier) {
                val w = size.width
                val h = size.height
                drawRoundRect(
                    color = tint.copy(alpha = 0.4f),
                    topLeft = Offset(w * 0.18f, h * 0.18f),
                    size = Size(w * 0.64f, h * 0.64f),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                    style = Stroke(width = 1.8f.dp.toPx())
                )
                val diamondPath = Path().apply {
                    moveTo(w * 0.50f, h * 0.32f)
                    lineTo(w * 0.68f, h * 0.50f)
                    lineTo(w * 0.50f, h * 0.68f)
                    lineTo(w * 0.32f, h * 0.50f)
                    close()
                }
                drawPath(diamondPath, color = tint)
            }
        }
    }
}

@Composable
private fun CategoryContent(
    state: AnalyticsState,
    isDark: Boolean
) {
    val topCategories = state.topCategories
    if (topCategories.isEmpty()) return

    val totalLikes = remember(topCategories) {
        topCategories.sumOf { it.likeCount.coerceAtLeast(1) }.coerceAtLeast(1)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Aesthetic Distribution",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) RadicalPalette.DarkCardTextPrimary else RadicalPalette.LightCardTextPrimary
                )
                Text(
                    text = "Preference Weight",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDark) RadicalPalette.DarkCardTextSecondary else RadicalPalette.LightCardTextSecondary
                )
            }

                Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(
                        if (isDark) RadicalPalette.DarkCardTop.copy(alpha = 0.4f) else RadicalPalette.LightCardTop.copy(alpha = 0.4f)
                    )
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    topCategories.forEachIndexed { index, category ->
                        val weight = category.likeCount.coerceAtLeast(1).toFloat()
                        val color = CategorySpectrumColors[index % CategorySpectrumColors.size]
                        Box(
                            modifier = Modifier
                                .weight(weight)
                                .fillMaxHeight()
                                .background(color)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                topCategories.take(4).forEachIndexed { index, category ->
                    val color = CategorySpectrumColors[index % CategorySpectrumColors.size]
                    val pct = (category.likeCount.coerceAtLeast(1).toFloat() / totalLikes.toFloat() * 100).toInt()
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                        Text(
                            text = "${category.displayName} $pct%",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isDark) RadicalPalette.DarkCardTextSecondary else RadicalPalette.LightCardTextSecondary
                        )
                    }
                }
            }
        }

        RadicalDivider(isDark = isDark)

        val categoryPairs = remember(topCategories) { topCategories.chunked(2) }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            categoryPairs.forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    pair.forEach { category ->
                        val catIndex = topCategories.indexOf(category)
                        val color = CategorySpectrumColors[catIndex % CategorySpectrumColors.size]
                        CategoryAffinityTile(
                            category = category,
                            accentColor = color,
                            isDark = isDark,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (pair.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Category,
                    contentDescription = null,
                    tint = if (isDark) RadicalPalette.DarkCardTextTertiary else RadicalPalette.LightCardTextTertiary,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "${topCategories.size} visual domains tracked",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = if (isDark) RadicalPalette.DarkCardTextSecondary else RadicalPalette.LightCardTextSecondary
                )
            }

            Text(
                text = "On-Device Taste Profile",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = if (isDark) RadicalPalette.DarkCardTextTertiary else RadicalPalette.LightCardTextTertiary
            )
        }
    }
}

@Composable
private fun CategoryAffinityTile(
    category: CategoryInsight,
    accentColor: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val strength = category.preferenceStrength
    val isPositive = strength >= 0
    val badgeColor = if (isPositive) RadicalPalette.EmeraldJade else RadicalPalette.RubyRed
    val fillFraction = abs(strength).coerceIn(0.08f, 1f)

    val badgeText = when {
        strength >= 0.7f -> "+${(strength * 100).toInt()}%"
        strength > 0f -> "+${(strength * 100).toInt()}%"
        strength == 0f -> "0%"
        else -> "${(strength * 100).toInt()}%"
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isDark) RadicalPalette.DarkCardWell.copy(alpha = 0.60f) else RadicalPalette.LightCardWell.copy(alpha = 0.55f)
            )
            .border(
                width = 1.dp,
                color = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFE2E8F0),
                shape = RoundedCornerShape(14.dp)
            )
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = if (isDark) 0.18f else 0.12f))
                        .border(
                            width = 1.dp,
                            color = accentColor.copy(alpha = if (isDark) 0.40f else 0.30f),
                            shape = RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    CategoryAestheticIcon(
                        category = category.category,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeColor.copy(alpha = if (isDark) 0.18f else 0.12f))
                        .border(
                            width = 1.dp,
                            color = badgeColor.copy(alpha = if (isDark) 0.40f else 0.25f),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
                    )
                }
            }

            Text(
                text = category.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isDark) RadicalPalette.DarkCardTextPrimary else RadicalPalette.LightCardTextPrimary
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(2.5.dp))
                    .background(
                        if (isDark) RadicalPalette.DarkCardWell else RadicalPalette.LightCardWell
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fillFraction)
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(
                            Brush.horizontalGradient(listOf(accentColor.copy(alpha = 0.75f), accentColor))
                        )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${category.likeCount} likes",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = if (isDark) RadicalPalette.DarkCardTextSecondary else RadicalPalette.LightCardTextSecondary
                )
                Text(
                    text = "${category.dislikeCount} hides",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = if (isDark) RadicalPalette.DarkCardTextTertiary else RadicalPalette.LightCardTextTertiary
                )
            }
        }
    }
}


@Composable
private fun MatchQualityContent(
    state: AnalyticsState,
    isDark: Boolean
) {
    val matchFraction = (state.averageSimilarityScore / 100f).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "Aesthetic Alignment Score",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDark) RadicalPalette.DarkCardTextPrimary else RadicalPalette.LightCardTextPrimary
                )
                Text(
                    text = "Cosine similarity against your preference vector",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isDark) RadicalPalette.DarkCardTextSecondary else RadicalPalette.LightCardTextSecondary
                )
            }

            Box(
                modifier = Modifier.size(56.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { matchFraction },
                    modifier = Modifier.size(56.dp),
                    color = RadicalPalette.TealCyan,
                    trackColor = if (isDark) RadicalPalette.DarkCardWell else RadicalPalette.LightCardWell,
                    strokeWidth = 5.dp,
                    strokeCap = StrokeCap.Round
                )
                Text(
                    text = "${state.averageSimilarityScore.toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = if (isDark) RadicalPalette.DarkCardTextPrimary else RadicalPalette.LightCardTextPrimary
                )
            }
        }

        RadicalDivider(isDark = isDark)

        val (trendIcon, trendText, trendColor) = when (state.similarityTrend) {
            SimilarityTrend.IMPROVING -> Triple(Icons.AutoMirrored.Filled.TrendingUp, "Recommendations are closely tracking your aesthetic preferences", RadicalPalette.EmeraldJade)
            SimilarityTrend.DECLINING -> Triple(Icons.AutoMirrored.Filled.TrendingDown, "Add a few likes to realign recommendations to your latest style", RadicalPalette.RubyRed)
            SimilarityTrend.STABLE -> Triple(Icons.AutoMirrored.Filled.TrendingFlat, "Consistent aesthetic alignment across recent rotations", RadicalPalette.SapphireBlue)
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(trendColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = trendIcon,
                    contentDescription = null,
                    tint = trendColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = trendText,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isDark) RadicalPalette.DarkCardTextSecondary else RadicalPalette.LightCardTextSecondary,
                lineHeight = 18.sp
            )
        }

        RadicalDivider(isDark = isDark)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent 7-Day Velocity",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isDark) RadicalPalette.DarkCardTextPrimary else RadicalPalette.LightCardTextPrimary
            )
            Text(
                text = "${state.recentLikes} likes · ${state.recentDislikes} hides",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = RadicalPalette.EmeraldJade
            )
        }
    }
}


@Composable
private fun AdvancedContent(
    state: AnalyticsState,
    isDark: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            MiniTactileGauge(
                label = "Learning Rate",
                sublabel = "Adaptation",
                value = state.learningRate.coerceIn(0f, 1f),
                displayValue = String.format("%.2f", state.learningRate),
                color = RadicalPalette.SapphireBlue,
                isDark = isDark
            )

            MiniTactileGauge(
                label = "Preference Drift",
                sublabel = "Taste shift",
                value = (state.preferenceDrift / 100f).coerceIn(0f, 1f),
                displayValue = "${state.preferenceDrift.toInt()}%",
                color = RadicalPalette.AmethystPurple,
                isDark = isDark
            )

            MiniTactileGauge(
                label = "Vector Norm",
                sublabel = "Confidence",
                value = (state.preferenceVectorMagnitude / 5f).coerceIn(0f, 1f),
                displayValue = String.format("%.1f", state.preferenceVectorMagnitude),
                color = RadicalPalette.TealCyan,
                isDark = isDark
            )
        }

        Text(
            text = "100% of embeddings and vector calculations run on-device. No telemetry or preference coordinates ever leave your phone.",
            style = MaterialTheme.typography.bodySmall,
            color = if (isDark) RadicalPalette.DarkCardTextSecondary else RadicalPalette.LightCardTextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
            lineHeight = 17.sp
        )
    }
}

@Composable
private fun MiniTactileGauge(
    label: String,
    sublabel: String,
    value: Float,
    displayValue: String,
    color: Color,
    isDark: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier.size(54.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = { value },
                modifier = Modifier.size(54.dp),
                color = color,
                trackColor = if (isDark) RadicalPalette.DarkCardWell else RadicalPalette.LightCardWell,
                strokeWidth = 4.5.dp,
                strokeCap = StrokeCap.Round
            )
            Text(
                text = displayValue,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = if (isDark) RadicalPalette.DarkCardTextPrimary else RadicalPalette.LightCardTextPrimary
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isDark) RadicalPalette.DarkCardTextPrimary else RadicalPalette.LightCardTextPrimary
            )
            Text(
                text = sublabel,
                fontSize = 10.sp,
                color = if (isDark) RadicalPalette.DarkCardTextTertiary else RadicalPalette.LightCardTextTertiary
            )
        }
    }
}


@Composable
private fun InsightsLoading(
    isDark: Boolean,
    paddingValues: PaddingValues
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentAlignment = Alignment.Center
    ) {
        RadicalTactileCard(
            isDark = isDark,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .padding(16.dp),
            contentPadding = PaddingValues(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CircularProgressIndicator(
                    color = if (isDark) RadicalPalette.EmeraldJade else RadicalPalette.LightCardTextPrimary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = "Compiling intelligence telemetry…",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (isDark) RadicalPalette.DarkCardTextPrimary else RadicalPalette.LightCardTextPrimary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun InsightsError(
    error: String,
    isDark: Boolean,
    paddingValues: PaddingValues,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        RadicalTactileCard(
            isDark = isDark,
            modifier = Modifier.widthIn(max = 380.dp),
            contentPadding = PaddingValues(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(RadicalPalette.RubyRed.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                        tint = RadicalPalette.RubyRed
                    )
                }

                Text(
                    text = "Telemetry Unavailable",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) RadicalPalette.DarkCardTextPrimary else RadicalPalette.LightCardTextPrimary,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) RadicalPalette.DarkCardTextSecondary else RadicalPalette.LightCardTextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                RadicalTactileButton(
                    text = "Retry Telemetry",
                    onClick = onRetry,
                    isDark = isDark,
                    variant = RadicalButtonVariant.Secondary
                )
            }
        }
    }
}
