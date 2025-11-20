package me.avinas.vanderwaals.ui.analytics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skydoves.landscapist.glide.GlideImage
import com.skydoves.landscapist.ImageOptions
import androidx.compose.ui.layout.ContentScale

/**
 * Analytics Screen - Beautiful conversational dashboard
 * 
 * Shows personalization effectiveness, learning progress,
 * and actionable insights in a friendly, engaging way.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    onNavigateBack: () -> Unit,
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    // Handle system back button
    androidx.activity.compose.BackHandler {
        onNavigateBack()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Personalization Insights",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.statusBarsPadding()
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .navigationBarsPadding()
        ) {
            when {
                state.isLoading -> {
                    LoadingView()
                }
                state.error != null -> {
                    ErrorView(error = state.error!!, onRetry = { viewModel.refresh() })
                }
                else -> {
                    AnalyticsContent(
                        state = state,
                        scrollState = scrollState
                    )
                }
            }
        }
    }
}

@Composable
private fun AnalyticsContent(
    state: AnalyticsState,
    scrollState: androidx.compose.foundation.ScrollState
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Card - Personalization Status
        PersonalizationStatusCard(state)

        // Smart Insights
        if (state.insights.isNotEmpty()) {
            InsightsSection(state.insights)
        }

        // Learning Progress Card
        if (state.isPersonalizationWorking) {
            LearningProgressCard(state)
        }

        // Feedback Stats Card
        if (state.totalFeedbackCount > 0) {
            FeedbackStatsCard(state)
        }

        // Recommendation Impact Card - Show if we have any history data or personalization is active
        if (state.totalWallpapersViewed > 0 || state.isPersonalizationWorking) {
            RecommendationImpactCard(state)
        }

        // Category Breakdown
        if (state.topCategories.isNotEmpty()) {
            CategoryBreakdownCard(state)
        }

        // History Stats
        if (state.totalWallpapersViewed > 0) {
            HistoryStatsCard(state)
        }

        // Advanced Metrics (for power users)
        if (state.totalFeedbackCount > 10) {
            AdvancedMetricsCard(state)
        }

        // Recommendations removed as per user request

        // Bottom spacer
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PersonalizationStatusCard(state: AnalyticsState) {
    val (statusColor, statusTitle, qualityLevel) = when {
        !state.isPersonalizationActive -> Triple(Color(0xFFFFFFFF), "Ready to Learn", 0)
        !state.isPersonalizationWorking -> Triple(Color(0xFFFFFFFF), "Learning...", 1)
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
            Triple(Color(0xFFFFFFFF), title, level)
        }
    }

    VibrantCard(
        colors = listOf(Color(0xFF2196F3), Color(0xFF9C27B0)) // Blue to Purple
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Personalization Status",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Text(
                    text = statusTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            
            // Visual Level Indicator
            Box(contentAlignment = Alignment.Center) {
                CircularScoreIndicator(
                    score = qualityLevel / 5f,
                    size = 64.dp,
                    strokeWidth = 6.dp,
                    color = Color.White,
                    showPercentage = false  // Don't show percentage here, we'll show level instead
                )
                Text(
                    text = "Lvl $qualityLevel",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun QualityIndicator(quality: PersonalizationQuality, color: Color) {
    val progress = when (quality) {
        PersonalizationQuality.NOT_INITIALIZED -> 0f
        PersonalizationQuality.LEARNING -> 0.2f
        PersonalizationQuality.DEVELOPING -> 0.4f
        PersonalizationQuality.ESTABLISHED -> 0.6f
        PersonalizationQuality.REFINED -> 0.8f
        PersonalizationQuality.EXCELLENT -> 1.0f
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Learning Progress",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
        
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.2f),
        )
    }
}

@Composable
private fun InsightsSection(insights: List<SmartInsight>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "💡 Smart Insights",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        insights.forEach { insight ->
            InsightCard(insight)
        }
    }
}

@Composable
private fun InsightCard(insight: SmartInsight) {
    val backgroundColor = when (insight.type) {
        InsightType.SUCCESS -> Color(0xFF4CAF50).copy(alpha = 0.1f)
        InsightType.LEARNING -> Color(0xFF2196F3).copy(alpha = 0.1f)
        InsightType.NEED_FEEDBACK -> Color(0xFFFF9800).copy(alpha = 0.1f)
        InsightType.DISCOVERY -> Color(0xFF9C27B0).copy(alpha = 0.1f)
        InsightType.TIP -> Color(0xFF00BCD4).copy(alpha = 0.1f)
        InsightType.WARNING -> Color(0xFFF44336).copy(alpha = 0.1f)
    }

    val iconColor = when (insight.type) {
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

    VibrantCard(
        colors = listOf(Color(0xFF8E24AA), Color(0xFFD81B60)) // Violet to Pink
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.White.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = insight.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                
                Text(
                    text = insight.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f),
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.3f
                )
            }
        }
    }
}

@Composable
private fun LearningProgressCard(state: AnalyticsState) {
    VibrantCard(
        colors = listOf(Color(0xFF009688), Color(0xFF4CAF50)) // Teal to Green
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Preference Mix",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            // Visual Split Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                Box(
                    modifier = Modifier
                        .weight(0.4f)
                        .fillMaxHeight()
                        .background(Color.White.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("40%", style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
                Box(
                    modifier = Modifier
                        .weight(0.6f)
                        .fillMaxHeight()
                        .background(Color.White.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("60%", style = MaterialTheme.typography.labelSmall, color = Color(0xFF009688))
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Original Style", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                Text("Learned Style", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.2f))

            // Exploration Icon Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Explore, null, tint = Color.White)
                    Text("Exploration Rate", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                }
                Text(
                    text = "${(state.explorationRate * 100).toInt()}%",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun AnchorExplanation(
    title: String,
    description: String,
    percentage: Int,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Percentage circle
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(color.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$percentage%",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }

        // Explanation
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.2f
            )
        }
    }
}

@Composable
private fun FeedbackStatsCard(state: AnalyticsState) {
    VibrantCard(
        colors = listOf(Color(0xFFFF9800), Color(0xFFFF5722)) // Orange to Deep Orange
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("👍", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "Your Feedback",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Text(
                text = "You've shared your opinion ${state.totalFeedbackCount} times, helping me understand what you love!",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.9f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Likes
                StatBox(
                    value = state.likeCount.toString(),
                    label = "Liked",
                    icon = Icons.Default.ThumbUp,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )

                // Dislikes
                StatBox(
                    value = state.dislikeCount.toString(),
                    label = "Disliked",
                    icon = Icons.Default.ThumbDown,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
            }

            // Feedback ratio visualization
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Preference Balance",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                ) {
                    if (state.likeCount > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(state.likeCount.toFloat())
                                .background(Color(0xFF4CAF50)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (state.feedbackRatio > 0.3f) {
                                Text(
                                    text = "${(state.feedbackRatio * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    if (state.dislikeCount > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(state.dislikeCount.toFloat())
                                .background(Color(0xFFF44336)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (state.feedbackRatio < 0.7f) {
                                Text(
                                    text = "${((1 - state.feedbackRatio) * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Recent activity
            if (state.recentLikes > 0 || state.recentDislikes > 0) {
                HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Last 7 Days",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (state.recentLikes > 0) {
                            Text(
                                text = "+${state.recentLikes} ❤️",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (state.recentDislikes > 0) {
                            Text(
                                text = "${state.recentDislikes} 👎",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendationImpactCard(state: AnalyticsState) {
    VibrantCard(
        colors = listOf(Color(0xFF673AB7), Color(0xFF3F51B5)) // Deep Purple to Indigo
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("✨", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "Recommendation Quality",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // Similarity score
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Match Quality",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        text = "How well wallpapers match your taste",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
                
                CircularScoreIndicator(
                    score = state.averageSimilarityScore,
                    size = 64.dp
                )
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.2f))

            // Trend indicator
            val (trendIcon, trendText, trendColor) = when (state.similarityTrend) {
                SimilarityTrend.IMPROVING -> Triple(
                    Icons.AutoMirrored.Filled.TrendingUp,
                    "Recommendations are getting better! Your feedback is making a real impact.",
                    Color(0xFF4CAF50)
                )
                SimilarityTrend.DECLINING -> Triple(
                    Icons.AutoMirrored.Filled.TrendingDown,
                    "Recommendations could be better. More feedback will help realign the algorithm.",
                    Color(0xFFFF9800)
                )
                SimilarityTrend.STABLE -> Triple(
                    Icons.AutoMirrored.Filled.TrendingFlat,
                    "Consistent recommendations. The algorithm is stable and reliable.",
                    Color(0xFF2196F3)
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = trendIcon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = trendText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f),
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.3f
                )
            }
        }
    }
}

@Composable
private fun CategoryBreakdownCard(state: AnalyticsState) {
    VibrantCard(
        colors = listOf(Color(0xFF3F51B5), Color(0xFF2196F3)) // Indigo to Blue
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Top Categories",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            // Visual Grid/Cloud
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.topCategories.take(5).forEach { category ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(text = category.emoji, style = MaterialTheme.typography.headlineSmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = category.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        // Mini strength bar
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = 0.3f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(maxOf(0f, category.preferenceStrength))
                                    .fillMaxHeight()
                                    .background(Color.White)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryPreferenceRow(category: CategoryInsight) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = category.emoji,
            style = MaterialTheme.typography.headlineSmall
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = category.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (category.likeCount > 0) {
                    Text(
                        text = "${category.likeCount} ❤️",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF4CAF50)
                    )
                }
                if (category.dislikeCount > 0) {
                    Text(
                        text = "${category.dislikeCount} 👎",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFF44336)
                    )
                }
            }
        }

        // Preference strength indicator
        val strengthColor = when {
            category.preferenceStrength > 0.6f -> Color(0xFF4CAF50)
            category.preferenceStrength < -0.6f -> Color(0xFFF44336)
            else -> Color(0xFFFF9800)
        }
        
        Text(
            text = when {
                category.preferenceStrength > 0.6f -> "Love"
                category.preferenceStrength > 0.3f -> "Like"
                category.preferenceStrength < -0.6f -> "Dislike"
                category.preferenceStrength < -0.3f -> "Meh"
                else -> "Neutral"
            },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = strengthColor
        )
    }
}

@Composable
private fun HistoryStatsCard(state: AnalyticsState) {
    VibrantCard(
        colors = listOf(Color(0xFF607D8B), Color(0xFF455A64)) // Blue Grey
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("🕰️", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "Viewing History",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatColumn(
                    value = state.totalWallpapersViewed.toString(),
                    label = "Wallpapers\nViewed"
                )
                
                // Format duration nicely
                val durationText = if (state.averageWallpaperDuration < 60) {
                    "${state.averageWallpaperDuration}s"
                } else {
                    "${state.averageWallpaperDuration / 60}m"
                }
                
                StatColumn(
                    value = durationText,
                    label = "Avg. View\nTime"
                )
                
                val activityIcon = when (state.activityTrend) {
                    ActivityTrend.INCREASING -> "📈"
                    ActivityTrend.STABLE -> "➡️"
                    ActivityTrend.DECREASING -> "📉"
                }
                
                StatColumn(
                    value = activityIcon,
                    label = "Activity\nTrend"
                )
            }
        }
    }
}

@Composable
private fun AdvancedMetricsCard(state: AnalyticsState) {
    VibrantCard(
        colors = listOf(Color(0xFF795548), Color(0xFF5D4037)) // Brown
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Algorithm Metrics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            // Grid of Mini Gauges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MiniGauge(label = "Learning", value = state.learningRate, color = Color.White)
                MiniGauge(label = "Drift", value = state.preferenceDrift / 100f, color = Color.White)
                MiniGauge(label = "Vector", value = state.preferenceVectorMagnitude / 10f, color = Color.White) // Assuming max 10
            }
        }
    }
}

@Composable
private fun MiniGauge(label: String, value: Float, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { value.coerceIn(0f, 1f) },
                modifier = Modifier.size(40.dp),
                color = color,
                trackColor = color.copy(alpha = 0.3f),
            )
            Text(
                text = "${(value * 100).toInt()}",
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun AdvancedMetricRow(
    label: String,
    value: String,
    description: String
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f)
        )
    }
}



// ========== Helper Composables ==========

@Composable
private fun VibrantCard(
    modifier: Modifier = Modifier,
    colors: List<Color>,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = colors,
                        start = androidx.compose.ui.geometry.Offset.Zero,
                        end = androidx.compose.ui.geometry.Offset.Infinite
                    )
                )
        ) {
            content()
        }
    }
}

@Composable
private fun StatBox(
    value: String,
    label: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = color.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun StatColumn(
    value: String,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CircularScoreIndicator(
    score: Float,
    size: androidx.compose.ui.unit.Dp,
    strokeWidth: androidx.compose.ui.unit.Dp = 4.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    showPercentage: Boolean = true
) {
    Box(contentAlignment = Alignment.Center) {
        // Background circle to ensure visibility
        Box(
            modifier = Modifier
                .size(size)
                .border(strokeWidth, color.copy(alpha = 0.2f), CircleShape)
        )
        
        // Normalize score to 0-1 range (score is 0-100 for percentage mode, 0-1 for direct mode)
        val normalizedScore = if (score > 1f) {
            (score / 100f).coerceIn(0f, 1f)
        } else {
            score.coerceIn(0f, 1f)
        }
        
        CircularProgressIndicator(
            progress = { normalizedScore },
            modifier = Modifier.size(size),
            color = color,
            trackColor = Color.Transparent, // We use the border above for track
            strokeWidth = strokeWidth,
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        
        // Display score as text in the center (only if showPercentage is true)
        if (showPercentage && score > 1f) {
            Text(
                text = "${score.toInt()}%",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
private fun LoadingView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator()
            Text(
                text = "Analyzing your preferences...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun ErrorView(error: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = "Oops!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            Button(onClick = onRetry) {
                Text("Try Again")
            }
        }
    }
}

// Helper functions
private fun formatDuration(seconds: Long): String {
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m"
        seconds < 86400 -> "${seconds / 3600}h"
        else -> "${seconds / 86400}d"
    }
}

// Helper data class for tuple
private data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
