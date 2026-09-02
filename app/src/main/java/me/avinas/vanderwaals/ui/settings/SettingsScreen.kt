package me.avinas.vanderwaals.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import me.avinas.vanderwaals.ui.theme.*
import me.avinas.vanderwaals.ui.theme.components.*
import me.avinas.vanderwaals.ui.theme.components.LiquidGlassCard
import me.avinas.vanderwaals.core.BatteryOptimizationHelper
import me.avinas.vanderwaals.ui.onboarding.*
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Brush

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToOnboarding: () -> Unit,
    onNavigateToAnalytics: () -> Unit = {},
    onNavigateToPrivacyPolicy: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()
    val needsAlarmPermission by viewModel.needsAlarmPermission.collectAsState()
    val context = LocalContext.current
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showTimePickerDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val isDark = LocalThemeIsDark.current
    val TextPrimaryDark = getOnboardingTextPrimary(true)
    val TextPrimaryLight = getOnboardingTextPrimary(false)
    val TextSecondaryDark = getOnboardingTextSecondary(true)
    val TextSecondaryLight = getOnboardingTextSecondary(false)
    val TextTertiaryDark = getOnboardingTextSecondary(true).copy(alpha = 0.7f)
    val TextTertiaryLight = getOnboardingTextSecondary(false).copy(alpha = 0.7f)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    androidx.activity.compose.BackHandler {
        onNavigateBack()
    }

    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short
            )
            viewModel.clearToastMessage()
        }
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
            snackbarHost = { 
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
                ) 
            },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Settings",
                            fontFamily = PlayfairDisplayFamily,
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            color = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
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
                                tint = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = if (isDark) RadicalPalette.DarkCanvasBase.copy(alpha = 0.88f) else RadicalPalette.LightCanvasBase.copy(alpha = 0.88f),
                        titleContentColor = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A),
                        navigationIconContentColor = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
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

            item {
                SettingsSectionHeader(
                    title = "MODE",
                    isDark = isDark
                )
                PremiumSettingsCard(
                    isDark = isDark
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SettingsIconBox(
                                    icon = Icons.Default.Shuffle,
                                    isDark = isDark,
                                    accentColor = BrandPrimary
                                )
                                Column {
                                    Text(
                                        text = if (settings.mode == "personalized") "Personalized Mode" else "Auto Mode",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isDark) TextPrimaryDark else TextPrimaryLight
                                    )
                                    Text(
                                        text = if (settings.mode == "personalized") "Learning from your preferences" else "Automatic wallpaper selection",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isDark) TextSecondaryDark else TextSecondaryLight
                                    )
                                }
                            }
                            
                            Switch(
                                checked = settings.mode == "personalized",
                                onCheckedChange = { enabled ->
                                    viewModel.updateMode(if (enabled) "personalized" else "auto")
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = BrandPrimary,
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = if (isDark) SurfaceHighlightDark else SurfaceHighlightLight
                                )
                            )
                        }
                        
                        Text(
                            text = "In Auto Mode, wallpapers are selected automatically. Enable Personalized Mode to teach the app your style.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDark) TextTertiaryDark else TextTertiaryLight,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 18.dp)
                        )
                        
                        if (settings.mode == "personalized") {
                            SettingsDivider(isDark = isDark)
                            SettingsNavigationRow(
                                title = "Re-personalize Your Aesthetic",
                                subtitle = "Update your wallpaper preferences",
                                onClick = onNavigateToOnboarding,
                                isDark = isDark,
                                leadingIcon = Icons.Default.Brush,
                                iconAccentColor = BrandPrimary
                            )
                        }
                    }
                }
            }

            item {
                SettingsSectionHeader(
                    title = "AUTO-CHANGE",
                    isDark = isDark
                )
                PremiumSettingsCard(
                    isDark = isDark
                ) {
                    Column {
                        RadicalFrequencyStudio(
                            currentInterval = settings.interval,
                            onIntervalSelected = { viewModel.updateInterval(it) },
                            dailyTime = settings.dailyTime,
                            onOpenFullTimePicker = { showTimePickerDialog = true },
                            isDark = isDark,
                            accentColor = BrandPrimary
                        )

                        if (settings.interval == ChangeInterval.EVERY_UNLOCK) {
                            SettingsDivider(isDark = isDark)
                            
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        SettingsIconBox(
                                            icon = Icons.Default.Collections,
                                            isDark = isDark,
                                            accentColor = Color(0xFFF43F5E)
                                        )
                                        Text(
                                            text = "Daily Unlock Playlist Size",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isDark) TextPrimaryDark else TextPrimaryLight
                                        )
                                    }
                                    Text(
                                        text = "${settings.dailyPlaylistSize} wallpapers",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = BrandPrimary
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(10.dp))
                                
                                Slider(
                                    value = settings.dailyPlaylistSize.toFloat(),
                                    onValueChange = { viewModel.updateDailyPlaylistSize(it.toInt()) },
                                    valueRange = 10f..50f,
                                    steps = 39,
                                    colors = SliderDefaults.colors(
                                        thumbColor = BrandPrimary,
                                        activeTrackColor = BrandPrimary,
                                        inactiveTrackColor = if (isDark) SurfaceHighlightDark else SurfaceHighlightLight
                                    )
                                )
                                
                                Text(
                                    text = "A fresh set of wallpapers is downloaded daily and rotated on unlock.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isDark) TextTertiaryDark else TextTertiaryLight,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                                
                                Spacer(modifier = Modifier.height(10.dp))
                                WarningNotice(
                                    title = "Battery Notice",
                                    message = "This mode has a 1-minute cooldown between changes to save battery. Frequent wallpaper changes may increase battery usage.",
                                    isDark = isDark
                                )
                            }
                            
                            if (settings.isPlaylistDownloading) {
                                SettingsDivider(isDark = isDark)
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = BrandPrimary
                                        )
                                        Text(
                                            text = if (settings.playlistDownloadProgress.isApplying) 
                                                "Applying wallpaper..." 
                                            else 
                                                settings.playlistDownloadProgress.progressText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isDark) TextPrimaryDark else TextPrimaryLight,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    
                                    if (settings.playlistDownloadProgress.totalCount > 0) {
                                        LinearProgressIndicator(
                                            progress = { 
                                                settings.playlistDownloadProgress.downloadedCount.toFloat() / 
                                                    settings.playlistDownloadProgress.totalCount.toFloat() 
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(2.dp)),
                                            color = BrandPrimary,
                                            trackColor = if (isDark) SurfaceHighlightDark else SurfaceHighlightLight
                                        )
                                    }
                                }
                            }
                            
                            if (!settings.isPlaylistDownloading) {
                                SettingsDivider(isDark = isDark)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(18.dp)
                                        .shadow(8.dp, RoundedCornerShape(14.dp), ambientColor = BrandPrimary.copy(alpha = 0.25f), spotColor = Color.Transparent)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Brush.horizontalGradient(colors = listOf(BrandPrimary, BrandAccent)))
                                        .bounceClick { viewModel.triggerDailyPlaylistDownload() }
                                        .padding(vertical = 14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Download Playlist Now",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                val batteryOptimized = remember {
                    !BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
                }
                
                if (batteryOptimized && settings.interval != ChangeInterval.NEVER) {
                    SettingsSectionHeader(
                        title = "BATTERY & PERFORMANCE",
                        isDark = isDark
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(ErrorColor.copy(alpha = 0.1f))
                            .border(
                                width = 1.dp,
                                color = ErrorColor.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .padding(18.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(
                                    imageVector = Icons.Default.BatteryAlert,
                                    contentDescription = null,
                                    tint = ErrorColor
                                )
                                Column {
                                    Text(
                                        text = "Battery Optimization Active",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = ErrorColor
                                    )
                                    Text(
                                        text = "Auto-change may not work after restart.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isDark) TextSecondaryDark else TextSecondaryLight
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = 1.dp,
                                        color = ErrorColor.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ErrorColor.copy(alpha = 0.15f))
                                    .bounceClick { viewModel.openBatterySettings() }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Open Battery Settings",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = ErrorColor
                                )
                            }
                        }
                    }
                }
            }

            item {
                SettingsSectionHeader(
                    title = "APPLY TO",
                    isDark = isDark
                )
                PremiumSettingsCard(
                    isDark = isDark,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    RadicalApplyToSelector(
                        selectedTarget = settings.applyTo,
                        onTargetSelected = { viewModel.updateApplyTo(it) },
                        isDark = isDark,
                        accentColor = BrandPrimary
                    )
                }
            }

            item {
                SettingsSectionHeader(
                    title = "SOURCES",
                    isDark = isDark
                )
                PremiumSettingsCard(
                    isDark = isDark
                ) {
                    Column {
                        settings.sourcesEnabled.entries.forEachIndexed { index, (source, enabled) ->
                            if (index > 0) SettingsDivider(isDark = isDark)
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = source,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isDark) TextPrimaryDark else TextPrimaryLight
                                    )
                                    Text(
                                        text = if (enabled) "Enabled" else "Disabled",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isDark) TextSecondaryDark else TextSecondaryLight
                                    )
                                }
                                
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .then(
                                            if (enabled) {
                                                Modifier.background(Brush.linearGradient(colors = listOf(BrandPrimary, BrandPrimary.copy(alpha = 0.8f))))
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .border(
                                            width = if (enabled) 0.dp else 1.5.dp,
                                            color = if (enabled) Color.Transparent else getOnboardingCardBorder(isDark),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .bounceClick { viewModel.toggleSource(source, !enabled) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (enabled) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                        
                        val isBingSyncing by viewModel.isBingSyncing.collectAsState()
                        val bingSyncProgress by viewModel.bingSyncProgress.collectAsState()
                        val bingSyncMessage by viewModel.bingSyncMessage.collectAsState()
                        val bingWallpaperCount by viewModel.bingWallpaperCount.collectAsState()
                        
                        if (isBingSyncing) {
                            SettingsDivider(isDark = isDark)
                            
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp, horizontal = 18.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = BrandPrimary
                                        )
                                        Text(
                                            text = bingSyncMessage,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isDark) TextPrimaryDark else TextPrimaryLight
                                        )
                                    }
                                
                                    if (bingWallpaperCount > 0) {
                                        Text(
                                            text = "$bingWallpaperCount wallpapers",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = BrandPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                
                                LinearProgressIndicator(
                                    progress = { bingSyncProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = BrandPrimary,
                                    trackColor = if (isDark) SurfaceHighlightDark else SurfaceHighlightLight
                                )
                            }
                        }

                        val isVanderwaalsCollectionSyncing by viewModel.isVanderwaalsCollectionSyncing.collectAsState()
                        val vanderwaalsCollectionSyncProgress by viewModel.vanderwaalsCollectionSyncProgress.collectAsState()
                        val vanderwaalsCollectionSyncMessage by viewModel.vanderwaalsCollectionSyncMessage.collectAsState()
                        val vanderwaalsCollectionWallpaperCount by viewModel.vanderwaalsCollectionWallpaperCount.collectAsState()

                        if (isVanderwaalsCollectionSyncing) {
                            SettingsDivider(isDark = isDark)

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp, horizontal = 18.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = BrandAccent
                                        )
                                        Text(
                                            text = vanderwaalsCollectionSyncMessage,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isDark) TextPrimaryDark else TextPrimaryLight
                                        )
                                    }

                                    if (vanderwaalsCollectionWallpaperCount > 0) {
                                        Text(
                                            text = "$vanderwaalsCollectionWallpaperCount wallpapers",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = BrandAccent,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                LinearProgressIndicator(
                                    progress = { vanderwaalsCollectionSyncProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = BrandAccent,
                                    trackColor = if (isDark) SurfaceHighlightDark else SurfaceHighlightLight
                                )
                            }
                        }
                        
                        SettingsDivider(isDark = isDark)
                        
                        val isSyncing by viewModel.isSyncing.collectAsState()
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp)
                                .border(
                                    width = 1.dp,
                                    color = getOnboardingCardBorder(isDark),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clip(RoundedCornerShape(14.dp))
                                .background(getOnboardingCardBackground(isDark))
                                .bounceClick { if (!isSyncing) viewModel.syncNow() }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = if (isDark) TextPrimaryDark else TextPrimaryLight,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Syncing...",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isDark) TextPrimaryDark else TextPrimaryLight
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = if (isDark) TextPrimaryDark else TextPrimaryLight
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Sync Now",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isDark) TextPrimaryDark else TextPrimaryLight
                                    )
                                }
                            }
                        }
                        
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Last synced: ${settings.lastSynced}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isDark) TextTertiaryDark else TextTertiaryLight
                            )
                            
                            if (settings.lastSynced == "Never synced") {
                                Text(
                                    text = "Sync wallpaper catalog to start",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ErrorColor,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(18.dp))
                    }
                }
            }

            item {
                SettingsSectionHeader(
                    title = "STORAGE",
                    isDark = isDark
                )
                PremiumSettingsCard(
                    isDark = isDark
                ) {
                    Column {
                        SettingsInfoRow(
                            label = "Cache Size",
                            value = settings.cacheSize,
                            isDark = isDark,
                            leadingIcon = Icons.Default.SdStorage,
                            iconAccentColor = Color(0xFF10B981)
                        )
                        
                        SettingsDivider(isDark = isDark)
                        
                        SettingsInfoRow(
                            label = "Download Location",
                            value = "Pictures/Vanderwaals",
                            isDark = isDark,
                            leadingIcon = Icons.Default.FolderOpen,
                            iconAccentColor = Color(0xFF3B82F6)
                        )
                        
                        SettingsDivider(isDark = isDark)
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp)
                                .border(
                                    width = 1.dp,
                                    color = ErrorColor.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clip(RoundedCornerShape(14.dp))
                                .background(ErrorColor.copy(alpha = 0.1f))
                                .bounceClick { showClearCacheDialog = true }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Clear Cache",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = ErrorColor
                            )
                        }
                    }
                }
            }

            item {
                SettingsSectionHeader(
                    title = "INSIGHTS",
                    isDark = isDark
                )
                PremiumSettingsCard(
                    isDark = isDark,
                    onClick = { onNavigateToAnalytics() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SettingsIconBox(
                                icon = Icons.Default.Analytics,
                                isDark = isDark,
                                accentColor = BrandPrimary
                            )
                            Column {
                                Text(
                                    text = "Personalization Analytics",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isDark) TextPrimaryDark else TextPrimaryLight
                                )
                                Text(
                                    text = "See how personalization is working",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isDark) TextSecondaryDark else TextSecondaryLight
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = if (isDark) TextTertiaryDark else TextTertiaryLight,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            item {
                SettingsSectionHeader(
                    title = "ABOUT",
                    isDark = isDark
                )
                PremiumSettingsCard(
                    isDark = isDark
                ) {
                    Column {
                        SettingsInfoRow(
                            label = "Version",
                            value = "v${me.avinas.vanderwaals.BuildConfig.VERSION_NAME}",
                            isDark = isDark,
                            leadingIcon = Icons.Default.Info,
                            iconAccentColor = Color(0xFF6366F1)
                        )
                        
                        SettingsDivider(isDark = isDark)
                        
                        SettingsNavigationRow(
                            title = "View on GitHub",
                            subtitle = "Star us on GitHub",
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/avinaxhroy/Vanderwaals"))
                                context.startActivity(intent)
                            },
                            isDark = isDark,
                            leadingIcon = Icons.Default.Code,
                            iconAccentColor = if (isDark) Color.White else Color.Black,
                            trailingIcon = Icons.AutoMirrored.Filled.OpenInNew
                        )
                        
                        SettingsDivider(isDark = isDark)
                        
                        SettingsNavigationRow(
                            title = "Rate on Store",
                            subtitle = "Help us improve",
                            onClick = {
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("market://details?id=${context.packageName}")
                                )
                                try {
                                    context.startActivity(intent)
                                } catch (e: android.content.ActivityNotFoundException) {
                                    val webIntent = android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")
                                    )
                                    context.startActivity(webIntent)
                                }
                            },
                            isDark = isDark,
                            leadingIcon = Icons.Default.Star,
                            iconAccentColor = Color(0xFFF59E0B),
                            trailingIcon = Icons.AutoMirrored.Filled.OpenInNew
                        )
                        
                        SettingsDivider(isDark = isDark)
                        
                        SettingsNavigationRow(
                            title = "Privacy Policy",
                            subtitle = "How your data is (not) handled",
                            onClick = onNavigateToPrivacyPolicy,
                            isDark = isDark,
                            leadingIcon = Icons.Default.PrivacyTip,
                            iconAccentColor = BrandPrimary
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

    if (needsAlarmPermission) {
        PremiumAlertDialog(
            onDismissRequest = { viewModel.dismissAlarmPermissionDialog() },
            title = "Alarm Permission Required",
            message = "To schedule automatic wallpaper changes at precise intervals, Vanderwaals needs permission to set alarms.",
            confirmText = "Grant Permission",
            onConfirm = { viewModel.openAlarmPermissionSettings() },
            dismissText = "Cancel",
            onDismiss = { viewModel.dismissAlarmPermissionDialog() },
            isDark = isDark
        )
    }

    if (showClearCacheDialog) {
        PremiumAlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = "Clear Cache?",
            message = "This will delete all cached wallpapers. You can re-download them later.",
            confirmText = "Clear",
            onConfirm = {
                viewModel.clearCache()
                showClearCacheDialog = false
            },
            dismissText = "Cancel",
            onDismiss = { showClearCacheDialog = false },
            isDark = isDark,
            confirmColor = ErrorColor
        )
    }

    if (showTimePickerDialog) {
        val currentTime = settings.dailyTime ?: DailyTime(8, 0)
        var selectedHour by remember { mutableStateOf(currentTime.hour) }
        var selectedMinute by remember { mutableStateOf(currentTime.minute) }
        
        PremiumTimePickerDialog(
            onDismissRequest = { showTimePickerDialog = false },
            initialHour = selectedHour,
            initialMinute = selectedMinute,
            onHourChange = { selectedHour = it },
            onMinuteChange = { selectedMinute = it },
            onConfirm = {
                viewModel.updateDailyTime(DailyTime(selectedHour, selectedMinute))
                showTimePickerDialog = false
            },
            onDismiss = { showTimePickerDialog = false },
            isDark = isDark
        )
    }
    
    val showBingTypeDialog by viewModel.showBingTypeDialog.collectAsState()
    var selectedBingType by remember { mutableStateOf("lite") }
    
    if (showBingTypeDialog) {
        PremiumBingTypeDialog(
            onDismissRequest = { viewModel.dismissBingTypeDialog() },
            selectedType = selectedBingType,
            onTypeChange = { selectedBingType = it },
            onConfirm = { viewModel.onBingTypeSelected(selectedBingType) },
            isDark = isDark
        )
    }
}

@Composable
private fun SettingsSectionHeader(
    title: String,
    isDark: Boolean
) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
    )
}

@Composable
private fun PremiumSettingsCard(
    modifier: Modifier = Modifier,
    isDark: Boolean,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onClick: (() -> Unit)? = null,
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
        .let {
            if (onClick != null) it.bounceClick(onClick) else it
        }

    Column(
        modifier = cardModifier.padding(contentPadding),
        content = content
    )
}

@Composable
private fun SettingsIconBox(
    icon: ImageVector,
    isDark: Boolean,
    accentColor: Color,
    backgroundColor: Color? = null
) {
    val metrics = rememberOnboardingLayoutMetrics()
    val shape = RoundedCornerShape(14.dp)
    val bgModifier = if (backgroundColor != null) {
        Modifier.background(backgroundColor)
    } else {
        Modifier.background(accentColor.copy(alpha = if (isDark) 0.16f else 0.12f))
    }

    Box(
        modifier = Modifier
            .size(metrics.iconBoxSize)
            .clip(shape)
            .then(bgModifier)
            .border(
                1.dp,
                accentColor.copy(alpha = if (isDark) 0.35f else 0.22f),
                shape
            ),
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

@Composable
private fun SettingsDivider(isDark: Boolean) {
    HorizontalDivider(
        color = getOnboardingCardBorder(isDark)
    )
}

@Composable
private fun SettingsNavigationRow(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    isDark: Boolean,
    leadingIcon: ImageVector? = null,
    iconAccentColor: Color = BrandPrimary,
    trailingIcon: ImageVector? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leadingIcon?.let {
            SettingsIconBox(
                icon = it,
                isDark = isDark,
                accentColor = iconAccentColor
            )
        }
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = getOnboardingTextPrimary(isDark)
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = getOnboardingTextSecondary(isDark),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Icon(
            imageVector = trailingIcon ?: Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = getOnboardingTextSecondary(isDark),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SettingsInfoRow(
    label: String,
    value: String,
    isDark: Boolean,
    leadingIcon: ImageVector? = null,
    iconAccentColor: Color = BrandPrimary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leadingIcon?.let {
            SettingsIconBox(
                icon = it,
                isDark = isDark,
                accentColor = iconAccentColor
            )
        }
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = getOnboardingTextPrimary(isDark)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = getOnboardingTextSecondary(isDark),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun WarningNotice(
    title: String,
    message: String,
    isDark: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                color = WarningContainer.copy(alpha = if (isDark) 0.15f else 0.8f)
            )
            .border(
                width = 1.dp,
                color = WarningColor.copy(alpha = if (isDark) 0.25f else 0.15f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = WarningColor,
            modifier = Modifier.size(18.dp)
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = WarningColor
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = if (isDark) WarningColor.copy(alpha = 0.8f) else Color(0xFF78350F)
            )
        }
    }
}

@Composable
private fun PremiumAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String,
    onDismiss: () -> Unit,
    isDark: Boolean,
    confirmColor: Color? = null
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = if (isDark) Color(0xFF161B22) else Color(0xFFF9F7F5),
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569)
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = confirmColor ?: BrandPrimary
                )
            ) {
                Text(confirmText, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                )
            ) {
                Text(dismissText)
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun PremiumTimePickerDialog(
    onDismissRequest: () -> Unit,
    initialHour: Int,
    initialMinute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDark: Boolean
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = if (isDark) Color(0xFF161B22) else Color(0xFFF9F7F5),
        title = {
            Text(
                text = "Set Daily Change Time",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = { onHourChange((initialHour + 1) % 24) }) {
                            Icon(Icons.Default.ArrowUpward, "Up", tint = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A))
                        }
                        Text(
                            text = "%02d".format(initialHour),
                            style = MaterialTheme.typography.headlineLarge,
                            color = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A),
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { onHourChange(if (initialHour == 0) 23 else initialHour - 1) }) {
                            Icon(Icons.Default.ArrowDownward, "Down", tint = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A))
                        }
                    }
                    Text(
                        text = ":",
                        style = MaterialTheme.typography.headlineLarge,
                        color = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A),
                        modifier = Modifier.padding(horizontal = 8.dp),
                        fontWeight = FontWeight.Bold
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = { onMinuteChange((initialMinute + 15) % 60) }) {
                            Icon(Icons.Default.ArrowUpward, "Up", tint = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A))
                        }
                        Text(
                            text = "%02d".format(initialMinute),
                            style = MaterialTheme.typography.headlineLarge,
                            color = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A),
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { onMinuteChange(if (initialMinute == 0) 45 else initialMinute - 15) }) {
                            Icon(Icons.Default.ArrowDownward, "Down", tint = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = BrandPrimary
                )
            ) {
                Text("Set", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                )
            ) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun PremiumBingTypeDialog(
    onDismissRequest: () -> Unit,
    selectedType: String,
    onTypeChange: (String) -> Unit,
    onConfirm: () -> Unit,
    isDark: Boolean
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = if (isDark) Color(0xFF161B22) else Color(0xFFF9F7F5),
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Choose Bing Collection",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
                )
                Text(
                    text = "Select how much wallpaper history to download",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                BingTypeRadioCard(
                    title = "Recent Hits",
                    subtitle = "Last 3 years • ~1000 wallpapers",
                    description = "Faster download, newer wallpapers",
                    isSelected = selectedType == "lite",
                    onClick = { onTypeChange("lite") },
                    isDark = isDark
                )
                
                BingTypeRadioCard(
                    title = "Global Archive",
                    subtitle = "2009-present • ~5400 wallpapers",
                    description = "Complete collection, larger download",
                    isSelected = selectedType == "full",
                    onClick = { onTypeChange("full") },
                    isDark = isDark
                )
            }
        },
        confirmButton = {
            Box(
                modifier = Modifier
                    .shadow(4.dp, RoundedCornerShape(10.dp))
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.horizontalGradient(listOf(BrandPrimary, BrandAccent)))
                    .bounceClick(onConfirm)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Download", fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                )
            ) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun BingTypeRadioCard(
    title: String,
    subtitle: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    isDark: Boolean
) {
    val accent = BrandPrimary
    val borderBrush = if (isSelected) {
        Brush.linearGradient(colors = listOf(accent, accent.copy(alpha = 0.7f)))
    } else {
        SolidColor(getOnboardingCardBorder(isDark))
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isSelected) 8.dp else 0.dp,
                shape = RoundedCornerShape(14.dp),
                ambientColor = if (isSelected) accent.copy(alpha = 0.2f) else Color.Transparent,
                spotColor = if (isSelected) accent.copy(alpha = 0.15f) else Color.Transparent
            )
            .clip(RoundedCornerShape(14.dp))
            .background(getOnboardingCardBackground(isDark))
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                brush = borderBrush,
                shape = RoundedCornerShape(14.dp)
            )
            .bounceClick { onClick() }
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .padding(top = 2.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) {
                            Brush.linearGradient(colors = listOf(accent, accent.copy(alpha = 0.8f)))
                        } else {
                            Brush.linearGradient(
                                colors = listOf(
                                    if (isDark) Color(0xFF1C1A17) else Color(0xFFF7F5F0),
                                    if (isDark) Color(0xFF141210) else Color(0xFFECEAE3)
                                )
                            )
                        }
                    )
                    .border(
                        width = if (isSelected) 0.dp else 1.5.dp,
                        color = if (isSelected) Color.Transparent else (if (isDark) Color(0xFF4A443A) else Color(0xFFD1CBBF)),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
            
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = getOnboardingTextPrimary(isDark)
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandPrimary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = getOnboardingTextSecondary(isDark),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
