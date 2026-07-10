package me.avinas.vanderwaals.ui.analytics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.layout.ContentScale
import me.avinas.vanderwaals.ui.theme.*
import me.avinas.vanderwaals.ui.onboarding.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    onNavigateBack: () -> Unit,
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isDark = LocalThemeIsDark.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    androidx.activity.compose.BackHandler {
        onNavigateBack()
    }

    val metrics = rememberOnboardingLayoutMetrics()

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
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
                            text = "Personalization Insights",
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.TopCenter
            ) {
                when {
                    state.isLoading -> {
                        LoadingView()
                    }
                    state.error != null -> {
                        ErrorView(error = state.error!!, onRetry = { viewModel.refresh() })
                    }
                    else -> {
                        AnalyticsContent(state = state, isDark = isDark, metrics = metrics)
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsContent(
    state: AnalyticsState,
    isDark: Boolean,
    metrics: OnboardingLayoutMetrics
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = metrics.maxContentWidth)
            .padding(horizontal = metrics.horizontalPadding),
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(metrics.cardSpacing),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Status Section
        item {
            AnalyticsSectionHeader(title = "STATUS", isDark = isDark)
            PersonalizationStatusCard(state, isDark)
        }

        // Smart Insights
        if (state.insights.isNotEmpty()) {
            item {
                AnalyticsSectionHeader(title = "INSIGHTS", isDark = isDark)
                InsightsSection(state.insights, isDark)
            }
        }

        // Learning Progress
        if (state.isPersonalizationWorking) {
            item {
                AnalyticsSectionHeader(title = "LEARNING PROGRESS", isDark = isDark)
                LearningProgressCard(state, isDark)
            }
        }

        // Feedback Stats
        if (state.totalFeedbackCount > 0) {
            item {
                AnalyticsSectionHeader(title = "FEEDBACK", isDark = isDark)
                FeedbackStatsCard(state, isDark)
            }
        }

        // Category Breakdown
        if (state.topCategories.isNotEmpty()) {
            item {
                AnalyticsSectionHeader(title = "TOP CATEGORIES", isDark = isDark)
                CategoryBreakdownCard(state, isDark)
            }
        }

        // Recommendation Impact
        if (state.totalWallpapersViewed > 0 || state.isPersonalizationWorking) {
            item {
                AnalyticsSectionHeader(title = "IMPACT", isDark = isDark)
                RecommendationImpactCard(state, isDark)
            }
        }

        // History Stats
        if (state.totalWallpapersViewed > 0) {
            item {
                AnalyticsSectionHeader(title = "HISTORY", isDark = isDark)
                HistoryStatsCard(
                    totalViewed = state.totalWallpapersViewed,
                    avgDuration = state.averageWallpaperDuration,
                    favoriteCategory = state.mostLikedCategory,
                    isDark = isDark
                )
            }
        }

        // Advanced Metrics
        if (state.totalFeedbackCount > 10) {
            item {
                AnalyticsSectionHeader(title = "ADVANCED METRICS", isDark = isDark)
                AdvancedMetricsCard(state, isDark)
            }
        }
    }
}

// -------------------------------------------------------------------------
// UI Components
// -------------------------------------------------------------------------

@Composable
private fun AnalyticsSectionHeader(
    title: String,
    isDark: Boolean
) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = getOnboardingTextSecondary(isDark),
        letterSpacing = 1.2.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 12.dp, bottom = 4.dp),
        textAlign = TextAlign.Start
    )
}

@Composable
private fun PremiumAnalyticsCard(
    modifier: Modifier = Modifier,
    isDark: Boolean,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val metrics = rememberOnboardingLayoutMetrics()
    val cardModifier = modifier
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

    Column(
        modifier = cardModifier.padding(contentPadding),
        content = content
    )
}

@Composable
private fun AnalyticsIconBox(
    icon: ImageVector,
    isDark: Boolean,
    accentColor: Color = BrandPrimary
) {
    val metrics = rememberOnboardingLayoutMetrics()
    Box(
        modifier = Modifier
            .size(metrics.iconBoxSize)
            .clip(RoundedCornerShape(14.dp))
            .background(accentColor.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(metrics.iconSize)
        )
    }
}

// -------------------------------------------------------------------------
// Section Cards
// -------------------------------------------------------------------------

@Composable
private fun PersonalizationStatusCard(state: AnalyticsState, isDark: Boolean) {
    val textPrimary = getOnboardingTextPrimary(isDark)
    val textSecondary = getOnboardingTextSecondary(isDark)
    
    val (statusTitle, qualityLevel) = when {
        !state.isPersonalizationActive -> Pair("Ready to Learn", 0)
        !state.isPersonalizationWorking -> Pair("Learning...", 1)
        else -> {
            val level = when (state.personalizationQuality) {
                PersonalizationQuality.LEARNING -> 1
                PersonalizationQuality.DEVELOPING -> 2
                PersonalizationQuality.ESTABLISHED -> 3
                PersonalizationQuality.REFINED -> 4
                PersonalizationQuality.EXCELLENT -> 5
                else -> 0
            }
            val title = when (state.personalizationQuality) {
                PersonalizationQuality.LEARNING -> "Level 1: Novice"
                PersonalizationQuality.DEVELOPING -> "Level 2: Apprentice"
                PersonalizationQuality.ESTABLISHED -> "Level 3: Pro"
                PersonalizationQuality.REFINED -> "Level 4: Expert"
                PersonalizationQuality.EXCELLENT -> "Level 5: Master"
                else -> "Unknown"
            }
            Pair(title, level)
        }
    }

    PremiumAnalyticsCard(isDark = isDark, contentPadding = PaddingValues(18.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnalyticsIconBox(icon = Icons.Default.VerifiedUser, isDark = isDark)
                Column {
                    Text(
                        text = statusTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = textPrimary
                    )
                    Text(
                        text = "Level $qualityLevel",
                        style = MaterialTheme.typography.bodyMedium,
                        color = textSecondary
                    )
                }
            }
            
            Box(contentAlignment = Alignment.Center) {
                CircularScoreIndicator(
                    score = qualityLevel / 5f,
                    size = 56.dp,
                    strokeWidth = 4.dp,
                    color = BrandPrimary,
                    showPercentage = false,
                    isDark = isDark
                )
                Text(
                    text = "${(qualityLevel / 5f * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
            }
        }
    }
}

@Composable
private fun InsightsSection(insights: List<SmartInsight>, isDark: Boolean) {
    val textPrimary = getOnboardingTextPrimary(isDark)
    val textSecondary = getOnboardingTextSecondary(isDark)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        insights.forEach { insight ->
            val mainColor = when (insight.type) {
                InsightType.SUCCESS -> Color(0xFF4CAF50)
                InsightType.LEARNING -> Color(0xFF2196F3)
                InsightType.NEED_FEEDBACK -> Color(0xFFFF9800)
                InsightType.DISCOVERY -> Color(0xFF9C27B0)
                InsightType.TIP -> Color(0xFF00BCD4)
                InsightType.WARNING -> Color(0xFFF44336)
            }
            
            val icon = when (insight.type) {
                InsightType.SUCCESS -> Icons.Default.CheckCircle
                InsightType.LEARNING -> Icons.Default.Psychology
                InsightType.NEED_FEEDBACK -> Icons.AutoMirrored.Filled.HelpOutline
                InsightType.DISCOVERY -> Icons.Default.Lightbulb
                InsightType.TIP -> Icons.Default.AutoAwesome
                InsightType.WARNING -> Icons.Default.Warning
            }

            PremiumAnalyticsCard(isDark = isDark, contentPadding = PaddingValues(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    AnalyticsIconBox(icon = icon, isDark = isDark, accentColor = mainColor)
                    
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = insight.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = textPrimary
                        )
                        Text(
                            text = insight.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = textSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LearningProgressCard(state: AnalyticsState, isDark: Boolean) {
    val textPrimary = getOnboardingTextPrimary(isDark)
    val textSecondary = getOnboardingTextSecondary(isDark)
    val dividerColor = if (isDark) BorderDark.copy(alpha = 0.4f) else BorderLight.copy(alpha = 0.6f)

    PremiumAnalyticsCard(isDark = isDark, contentPadding = PaddingValues(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (state.hasOriginalEmbedding) {
                val originalWeight = state.originalAnchorInfluence.coerceAtLeast(1f)
                val learnedWeight = state.learnedAnchorInfluence.coerceAtLeast(1f)
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .weight(originalWeight)
                            .fillMaxHeight()
                            .background(if (isDark) SurfaceHighlightDark else SurfaceHighlightLight)
                    )
                    Box(
                        modifier = Modifier
                            .weight(learnedWeight)
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(BrandPrimary, BrandAccent)
                                )
                            )
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Original Style (${state.originalAnchorInfluence.toInt()}%)",
                        style = MaterialTheme.typography.bodySmall,
                        color = textSecondary
                    )
                    Text(
                        text = "Learned Taste (${state.learnedAnchorInfluence.toInt()}%)",
                        style = MaterialTheme.typography.bodySmall,
                        color = textSecondary
                    )
                }
                HorizontalDivider(color = dividerColor)
            } else {
                Text(
                    text = "Learning from your feedback to understand your taste. The more you like/dislike, the better recommendations become!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = textSecondary
                )
                HorizontalDivider(color = dividerColor)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    AnalyticsIconBox(icon = Icons.Default.Explore, isDark = isDark)
                    Text(
                        text = "Exploration Rate",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = textPrimary
                    )
                }
                Text(
                    text = "${(state.explorationRate * 100).toInt()}%",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
            }
        }
    }
}

@Composable
private fun FeedbackStatsCard(state: AnalyticsState, isDark: Boolean) {
    PremiumAnalyticsCard(isDark = isDark, contentPadding = PaddingValues(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "You've shared your opinion ${state.totalFeedbackCount} times.",
                style = MaterialTheme.typography.bodyLarge,
                color = getOnboardingTextSecondary(isDark)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatBox(
                    value = state.likeCount.toString(),
                    label = "Liked",
                    icon = Icons.Default.ThumbUp,
                    color = Color(0xFF22C55E),
                    isDark = isDark,
                    modifier = Modifier.weight(1f)
                )
                StatBox(
                    value = state.dislikeCount.toString(),
                    label = "Disliked",
                    icon = Icons.Default.ThumbDown,
                    color = Color(0xFFEF4444),
                    isDark = isDark,
                    modifier = Modifier.weight(1f)
                )
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            ) {
                if (state.likeCount > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(state.likeCount.toFloat())
                            .background(Color(0xFF22C55E))
                    )
                }
                if (state.dislikeCount > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(state.dislikeCount.toFloat())
                            .background(Color(0xFFEF4444))
                    )
                }
            }
        }
    }
}

@Composable
private fun RecommendationImpactCard(state: AnalyticsState, isDark: Boolean) {
    val textPrimary = getOnboardingTextPrimary(isDark)
    val textSecondary = getOnboardingTextSecondary(isDark)
    val dividerColor = if (isDark) BorderDark.copy(alpha = 0.4f) else BorderLight.copy(alpha = 0.6f)

    PremiumAnalyticsCard(isDark = isDark, contentPadding = PaddingValues(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnalyticsIconBox(icon = Icons.Default.Stars, isDark = isDark, accentColor = BrandAccent)
                    Column {
                        Text(
                            text = "Match Quality",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = textPrimary
                        )
                        Text(
                            text = "How well wallpapers match your taste",
                            style = MaterialTheme.typography.bodySmall,
                            color = textSecondary
                        )
                    }
                }
                
                CircularScoreIndicator(
                    score = state.averageSimilarityScore,
                    size = 56.dp,
                    isPercentage = true,
                    color = BrandAccent,
                    isDark = isDark
                )
            }

            HorizontalDivider(color = dividerColor)

            val (trendIcon, trendText, trendColor) = when (state.similarityTrend) {
                SimilarityTrend.IMPROVING -> Triple(Icons.AutoMirrored.Filled.TrendingUp, "Recommendations are getting better!", Color(0xFF22C55E))
                SimilarityTrend.DECLINING -> Triple(Icons.AutoMirrored.Filled.TrendingDown, "Recommendations could be better.", Color(0xFFEF4444))
                SimilarityTrend.STABLE -> Triple(Icons.AutoMirrored.Filled.TrendingFlat, "Consistent recommendations.", Color(0xFF3B82F6))
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = trendIcon, contentDescription = null, tint = trendColor, modifier = Modifier.size(24.dp))
                Text(
                    text = trendText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textPrimary
                )
            }
        }
    }
}

@Composable
private fun CategoryBreakdownCard(state: AnalyticsState, isDark: Boolean) {
    val textPrimary = getOnboardingTextPrimary(isDark)
    PremiumAnalyticsCard(isDark = isDark, contentPadding = PaddingValues(18.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            state.topCategories.take(5).forEach { category ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(getOnboardingCardBackground(isDark))
                        .border(1.dp, getOnboardingCardBorder(isDark), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Text(text = category.emoji, style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = category.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Box(
                        modifier = Modifier
                            .width(48.dp)
                            .height(5.dp)
                            .clip(RoundedCornerShape(2.5.dp))
                            .background(if (isDark) SurfaceHighlightDark else SurfaceHighlightLight)
                    ) {
                        val fillRatio = category.preferenceStrength.coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fillRatio)
                                .fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(BrandPrimary, BrandAccent)
                                    )
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryStatsCard(
    totalViewed: Int,
    avgDuration: Long,
    favoriteCategory: String?,
    isDark: Boolean
) {
    PremiumAnalyticsCard(isDark = isDark, contentPadding = PaddingValues(18.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatColumn(label = "Viewed", value = totalViewed.toString(), isDark = isDark)
            
            val durationText = if (avgDuration < 60) "${avgDuration}s" else "${avgDuration / 60}m"
            StatColumn(label = "Avg. Time", value = durationText, isDark = isDark)
            
            StatColumn(label = "Favorite", value = favoriteCategory?.replaceFirstChar { it.uppercase() } ?: "-", isDark = isDark)
        }
    }
}

@Composable
private fun AdvancedMetricsCard(state: AnalyticsState, isDark: Boolean) {
    PremiumAnalyticsCard(isDark = isDark, contentPadding = PaddingValues(18.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            MiniGauge(label = "Learning", value = state.learningRate, color = BrandPrimary, isDark = isDark)
            MiniGauge(label = "Drift", value = state.preferenceDrift / 100f, color = BrandAccent, isDark = isDark)
            MiniGauge(label = "Vector", value = state.preferenceVectorMagnitude / 10f, color = BrandPrimaryMuted, isDark = isDark)
        }
    }
}

@Composable
private fun StatBox(
    value: String,
    label: String,
    icon: ImageVector,
    color: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(getOnboardingCardBackground(isDark))
            .border(1.dp, getOnboardingCardBorder(isDark), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
            Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = getOnboardingTextSecondary(isDark))
        }
    }
}

@Composable
private fun StatColumn(
    value: String,
    label: String,
    isDark: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = getOnboardingTextPrimary(isDark)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = getOnboardingTextSecondary(isDark)
        )
    }
}

@Composable
private fun CircularScoreIndicator(
    score: Float,
    size: androidx.compose.ui.unit.Dp,
    strokeWidth: androidx.compose.ui.unit.Dp = 4.dp,
    color: Color = BrandPrimary,
    showPercentage: Boolean = true,
    isPercentage: Boolean = false,
    isDark: Boolean
) {
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(size)
                .border(strokeWidth, color.copy(alpha = 0.12f), CircleShape)
        )
        
        val normalizedScore = if (isPercentage) (score / 100f).coerceIn(0f, 1f) else score.coerceIn(0f, 1f)
        
        CircularProgressIndicator(
            progress = { normalizedScore },
            modifier = Modifier.size(size),
            color = color,
            trackColor = Color.Transparent,
            strokeWidth = strokeWidth,
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        
        if (showPercentage) {
            val displayValue = if (isPercentage) score.toInt() else (score * 100).toInt()
            Text(
                text = "$displayValue%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = getOnboardingTextPrimary(isDark)
            )
        }
    }
}

@Composable
private fun MiniGauge(label: String, value: Float, color: Color, isDark: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { value.coerceIn(0f, 1f) },
                modifier = Modifier.size(48.dp),
                color = color,
                trackColor = color.copy(alpha = 0.12f),
                strokeWidth = 3.5.dp,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            Text(
                text = "${(value * 100).toInt()}",
                style = MaterialTheme.typography.labelSmall,
                color = getOnboardingTextPrimary(isDark),
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = getOnboardingTextSecondary(isDark)
        )
    }
}

@Composable
private fun LoadingView() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            color = BrandPrimary,
            strokeWidth = 3.dp
        )
    }
}

@Composable
private fun ErrorView(
    error: String,
    onRetry: () -> Unit
) {
    val isDark = LocalThemeIsDark.current
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = ErrorColor,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "Failed to load insights",
                style = MaterialTheme.typography.titleMedium,
                color = getOnboardingTextPrimary(isDark),
                textAlign = TextAlign.Center
            )
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = getOnboardingTextSecondary(isDark),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandPrimary,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Retry",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
