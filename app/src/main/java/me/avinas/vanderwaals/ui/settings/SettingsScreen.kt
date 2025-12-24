package me.avinas.vanderwaals.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import me.avinas.vanderwaals.ui.theme.LocalThemeIsDark
import me.avinas.vanderwaals.ui.theme.components.*
import me.avinas.vanderwaals.ui.theme.*

// Mockup Colors


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToOnboarding: () -> Unit,
    onNavigateToAnalytics: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()
    val needsAlarmPermission by viewModel.needsAlarmPermission.collectAsState()
    val context = LocalContext.current
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showTimePickerDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val isDark = me.avinas.vanderwaals.ui.theme.LocalThemeIsDark.current
    
    // Lifecycle observer to detect when user returns from permission settings
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

    // Handle system back button
    androidx.activity.compose.BackHandler {
        onNavigateBack()
    }

    // Show toast messages as Snackbar
    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short
            )
            viewModel.clearToastMessage()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = if (isDark) DarkBackground else LightBackground,
        snackbarHost = { 
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
            ) 
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Premium Background
            me.avinas.vanderwaals.ui.theme.components.PremiumBackground(
                modifier = Modifier.fillMaxSize(),
                isDark = isDark
            )

            // Calculate top padding for content (StatusBar + TopAppBar height)
            // Use safeDrawing to account for cutouts and status bars
            val statusBarHeight = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
            val topBarHeight = 64.dp // Standard M3 TopAppBar height
            val contentTopPadding = statusBarHeight + topBarHeight + 16.dp

            // Track scroll state for dynamic TopAppBar background
            val listState = rememberLazyListState()
            val isScrolled by remember {
                derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(
                    top = contentTopPadding,
                    bottom = 16.dp,
                    start = 16.dp,
                    end = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {

                // MODE Section
                item {
                    LabelSectionHeader(title = "MODE")
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (isDark) Color(0xFF111827).copy(alpha = 0.4f)
                                                else Color.Black.copy(alpha = 0.05f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Shuffle,
                                            contentDescription = null,
                                            tint = if (isDark) InfoColorDark else LightPrimary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = if (settings.mode == "personalized") "Personalized Mode" else "Auto Mode",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isDark) Color.White else Color(0xFF111827)
                                        )
                                        Text(
                                            text = if (settings.mode == "personalized") "Learning from your preferences" else "Automatic wallpaper selection",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isDark) Color.Gray else Color(0xFF4B5563)
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
                                        checkedTrackColor = if (isDark) InfoColorDark else LightPrimary,
                                        uncheckedThumbColor = Color.White,
                                        uncheckedTrackColor = if (isDark) Color.Gray else Color(0xFFE5E7EB)
                                    )
                                )
                            }
                            
                            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp).padding(bottom = 16.dp)) {
                                Text(
                                    text = "In Auto Mode, wallpapers are selected automatically. Enable Personalized Mode to teach the app your style.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isDark) Color.Gray else Color(0xFF4B5563),
                                    lineHeight = 20.sp
                                )
                            }
                            
                            if (settings.mode == "personalized") {
                                HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f))
                                SettingsRow(
                                    title = "Re-personalize Your Aesthetic",
                                    onClick = onNavigateToOnboarding,
                                    textColor = if (isDark) Color.White else Color(0xFF111827)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = if (isDark) Color.White.copy(alpha = 0.6f) else Color(0xFF9CA3AF)
                                    )
                                }
                            }
                        }
                    }
                }

                // APPEARANCE Section
                item {
                    LabelSectionHeader(title = "APPEARANCE")
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        SegmentedControl(
                            items = ThemeMode.entries.map { it.displayName },
                            selectedIndex = settings.themeMode.ordinal,
                            onItemSelected = { index ->
                                viewModel.updateThemeMode(ThemeMode.entries[index])
                            },
                            isDark = isDark
                        )
                    }
                }

                // AUTO-CHANGE Section
                item {
                    LabelSectionHeader(title = "AUTO-CHANGE")
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Column {
                            // Frequency
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Frequency",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isDark) Color.White else Color(0xFF111827)
                                    )
                                    Text(
                                        text = settings.interval.displayName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isDark) Color.Gray else Color(0xFF4B5563)
                                    )
                                }
                                
                                var expanded by remember { mutableStateOf(false) }
                                Box {
                                    Row(
                                        modifier = Modifier.clickable { expanded = true },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = settings.interval.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isDark) Color.Gray else Color(0xFF4B5563),
                                            fontWeight = FontWeight.Medium
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            tint = if (isDark) Color.Gray else Color(0xFF4B5563)
                                        )
                                    }
                                    
                                    DropdownMenu(
                                        expanded = expanded,
                                        onDismissRequest = { expanded = false },
                                        modifier = Modifier.background(if (isDark) Color(0xFF1F2937) else Color.White)
                                    ) {
                                        ChangeInterval.entries.forEach { interval ->
                                            DropdownMenuItem(
                                                text = { 
                                                    Text(
                                                        interval.displayName,
                                                        color = if (isDark) Color.White else Color(0xFF111827)
                                                    ) 
                                                },
                                                onClick = {
                                                    viewModel.updateInterval(interval)
                                                    expanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // Playlist Size Slider for Every Unlock
                            if (settings.interval == ChangeInterval.EVERY_UNLOCK) {
                                HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f))
                                
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Daily Playlist Size",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isDark) Color.White else Color(0xFF111827)
                                        )
                                        Text(
                                            text = "${settings.dailyPlaylistSize} wallpapers",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isDark) InfoColorDark else LightPrimary
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    Slider(
                                        value = settings.dailyPlaylistSize.toFloat(),
                                        onValueChange = { viewModel.updateDailyPlaylistSize(it.toInt()) },
                                        valueRange = 10f..50f,
                                        steps = 39,
                                        colors = SliderDefaults.colors(
                                            thumbColor = if (isDark) InfoColorDark else LightPrimary,
                                            activeTrackColor = if (isDark) InfoColorDark else LightPrimary,
                                            inactiveTrackColor = if (isDark) Color.Gray.copy(alpha = 0.3f) else Color(0xFFE5E7EB)
                                        )
                                    )
                                    
                                    Text(
                                        text = "A fresh set of wallpapers is downloaded daily and rotated on unlock.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isDark) Color.White.copy(alpha = 0.6f) else Color(0xFF6B7280),
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                    
                                    // Battery and cooldown warning note
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                color = if (isDark) Color(0xFFF59E0B).copy(alpha = 0.1f) else Color(0xFFFEF3C7),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = if (isDark) Color(0xFFF59E0B) else Color(0xFFD97706),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "Battery Notice",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isDark) Color(0xFFF59E0B) else Color(0xFFD97706)
                                            )
                                            Text(
                                                text = "This mode has a 1-minute cooldown between changes to save battery. Frequent wallpaper changes may increase battery usage.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isDark) Color.White.copy(alpha = 0.6f) else Color(0xFF6B7280)
                                            )
                                        }
                                    }
                                }
                                
                                // Progress Indicator with actual count
                                if (settings.isPlaylistDownloading) {
                                    HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f))
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                                color = if (isDark) InfoColorDark else LightPrimary
                                            )
                                            Text(
                                                text = if (settings.playlistDownloadProgress.isApplying) 
                                                    "Applying wallpaper..." 
                                                else 
                                                    settings.playlistDownloadProgress.progressText,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isDark) Color.White else Color(0xFF111827),
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        
                                        // Show progress bar if we have total count
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
                                                color = if (isDark) InfoColorDark else LightPrimary,
                                                trackColor = if (isDark) Color.Gray.copy(alpha = 0.3f) else Color(0xFFE5E7EB)
                                            )
                                        }
                                    }
                                }
                                
                                // Apply Button - triggers immediate playlist download
                                if (!settings.isPlaylistDownloading) {
                                    HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f))
                                    Button(
                                        onClick = { viewModel.triggerDailyPlaylistDownload() },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isDark) InfoColorDark else LightPrimary,
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(vertical = 12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Download Playlist Now",
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            if (settings.interval == ChangeInterval.DAILY) {
                                HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showTimePickerDialog = true }
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Change Time",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isDark) Color.White else Color(0xFF111827)
                                        )
                                        Text(
                                            text = settings.dailyTime?.let { "${it.hour}:${it.minute.toString().padStart(2, '0')}" } ?: "9:00 AM",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isDark) Color.Gray else Color(0xFF4B5563)
                                        )
                                    }
                                    Text(
                                        text = settings.dailyTime?.let { String.format("%02d:%02d", it.hour, it.minute) } ?: "09:00",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isDark) Color.Gray else Color(0xFF4B5563),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }


                // BATTERY & PERFORMANCE Section
                item {
                    val batteryOptimized = remember {
                        !me.avinas.vanderwaals.core.BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
                    }
                    
                    if (batteryOptimized && settings.interval != ChangeInterval.NEVER) {
                        LabelSectionHeader(title = "BATTERY & PERFORMANCE")
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isDark) Color(0xFFF43F5E).copy(alpha = 0.1f) else Color(0xFFEF4444).copy(alpha = 0.1f))
                                .border(
                                    1.dp, 
                                    if (isDark) Color(0xFFF43F5E).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f),
                                    RoundedCornerShape(16.dp)
                                )
                                .padding(16.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.BatteryAlert,
                                        contentDescription = null,
                                        tint = if (isDark) Color(0xFFF43F5E) else Color(0xFFEF4444)
                                    )
                                    Column {
                                        Text(
                                            text = "Battery Optimization Active",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isDark) Color(0xFFFECDD3) else Color(0xFF991B1B)
                                        )
                                        Text(
                                            text = "Auto-change may not work after restart.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isDark) Color(0xFFFDA4AF) else Color(0xFFB91C1C)
                                        )
                                    }
                                }
                                Button(
                                    onClick = { viewModel.openBatterySettings() },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isDark) Color(0xFFF43F5E).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f),
                                        contentColor = if (isDark) Color(0xFFFECDD3) else Color(0xFF991B1B)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(vertical = 10.dp)
                                ) {
                                    Text("Open Battery Settings", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                // APPLY TO Section
                item {
                    LabelSectionHeader(title = "APPLY TO")
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        SegmentedControl(
                            items = ApplyTo.entries.map { it.displayName },
                            selectedIndex = ApplyTo.entries.indexOf(settings.applyTo),
                            onItemSelected = { index ->
                                viewModel.updateApplyTo(ApplyTo.entries[index])
                            },
                            isDark = isDark
                        )
                    }
                }

                // SOURCES Section
                item {
                    LabelSectionHeader(title = "SOURCES")
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            settings.sourcesEnabled.entries.forEachIndexed { index, (source, enabled) ->
                                if (index > 0) HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = source,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isDark) Color.White else Color(0xFF111827)
                                        )
                                        Text(
                                            text = if (enabled) "Enabled" else "Disabled",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isDark) Color.Gray else Color(0xFF4B5563)
                                        )
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (enabled) (if (isDark) InfoColorDark else LightPrimary) else Color.Transparent)
                                            .border(
                                                1.dp, 
                                                if (enabled) Color.Transparent else (if (isDark) Color.Gray else Color(0xFFD1D5DB)),
                                                RoundedCornerShape(6.dp)
                                            )
                                            .clickable { viewModel.toggleSource(source, !enabled) },
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
                            
                            // Bing Sync Progress Section
                            val isBingSyncing by viewModel.isBingSyncing.collectAsState()
                            val bingSyncProgress by viewModel.bingSyncProgress.collectAsState()
                            val bingSyncMessage by viewModel.bingSyncMessage.collectAsState()
                            val bingWallpaperCount by viewModel.bingWallpaperCount.collectAsState()
                            
                            if (isBingSyncing) {
                                HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f))
                                
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                                color = if (isDark) InfoColorDark else LightPrimary
                                            )
                                            Text(
                                                text = bingSyncMessage,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                color = if (isDark) Color.White else Color(0xFF111827)
                                            )
                                        }
                                        
                                        if (bingWallpaperCount > 0) {
                                            Text(
                                                text = "$bingWallpaperCount wallpapers",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isDark) InfoColorDark else LightPrimary,
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
                                        color = if (isDark) InfoColorDark else LightPrimary,
                                        trackColor = if (isDark) Color.Gray.copy(alpha = 0.3f) else Color(0xFFE5E7EB)
                                    )
                                }
                            }
                            
                            HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f))
                            
                            val isSyncing by viewModel.isSyncing.collectAsState()
                            
                            Button(
                                onClick = { viewModel.syncNow() },
                                enabled = !isSyncing,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f),
                                    contentColor = if (isDark) Color.White else Color(0xFF111827),
                                    disabledContainerColor = if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.02f)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = if (isDark) Color.White else Color(0xFF111827),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Syncing...")
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Sync Now")
                                }
                            }
                            
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Last synced: ${settings.lastSynced}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isDark) Color.White.copy(alpha = 0.6f) else Color(0xFF6B7280)
                                )
                                
                                if (settings.lastSynced == "Never synced") {
                                    Text(
                                        text = "Sync wallpaper catalog to start",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isDark) ErrorColorDark else Color(0xFFEF4444),
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // STORAGE Section
                item {
                    LabelSectionHeader(title = "STORAGE")
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            // Cache Size
                            Column {
                                Text(
                                    text = "Cache Size",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isDark) Color.White else Color(0xFF111827)
                                )
                                Text(
                                    text = settings.cacheSize,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isDark) Color.Gray else Color(0xFF4B5563)
                                )
                            }
                            
                            HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f))
                            
                            // Download Location
                            Column {
                                Text(
                                    text = "Download Location",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isDark) Color.White else Color(0xFF111827)
                                )
                                Text(
                                    text = "Pictures/Vanderwaals",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isDark) Color.Gray else Color(0xFF4B5563)
                                )
                            }
                            
                            HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f))
                            
                            Button(
                                onClick = { showClearCacheDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isDark) ErrorColorDark.copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.1f),
                                    contentColor = if (isDark) ErrorColorDark else Color(0xFFEF4444)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(vertical = 10.dp)
                            ) {
                                Text("Clear Cache", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // INSIGHTS Section
                item {
                    LabelSectionHeader(title = "INSIGHTS")
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToAnalytics() },
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isDark) Color(0xFF111827).copy(alpha = 0.4f)
                                            else Color.Black.copy(alpha = 0.05f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Analytics,
                                        contentDescription = null,
                                        tint = if (isDark) InfoColorDark else LightPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Personalization Analytics",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isDark) Color.White else Color(0xFF111827)
                                    )
                                    Text(
                                        text = "See how personalization is working",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isDark) Color.Gray else Color(0xFF4B5563)
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = if (isDark) Color.Gray else Color(0xFF9CA3AF),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // ABOUT Section
                item {
                    LabelSectionHeader(title = "ABOUT")
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Column {
                            SettingsRow(
                                title = "Version",
                                subtitle = "v${me.avinas.vanderwaals.BuildConfig.VERSION_NAME}",
                                textColor = if (isDark) Color.White else Color(0xFF111827)
                            )
                            
                            HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f))
                            
                            SettingsRow(
                                title = "View on GitHub",
                                subtitle = "Star us on GitHub",
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/avinaxhroy/Vanderwaals"))
                                    context.startActivity(intent)
                                },
                                textColor = if (isDark) Color.White else Color(0xFF111827)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    tint = if (isDark) Color.White.copy(alpha = 0.6f) else Color(0xFF9CA3AF)
                                )
                            }
                            
                            HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f))
                            
                            SettingsRow(
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
                                textColor = if (isDark) Color.White else Color(0xFF111827)
                            )
                        }
                    }
                }
                
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }

            // TopAppBar Overlay
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = isScrolled,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    me.avinas.vanderwaals.ui.theme.components.GlassTopAppBarBackground(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp + WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding())
                    )
                }

                TopAppBar(
                    title = {
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    ),
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(vertical = 16.dp)
                )
            }
        }
    }

    // Dialogs (Alarm, Clear Cache, Time Picker) - Kept mostly same but updated colors if needed
    if (needsAlarmPermission) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissAlarmPermissionDialog() },
            title = { Text("Alarm Permission Required") },
            text = { Text("To schedule automatic wallpaper changes at precise intervals, Vanderwaals needs permission to set alarms.") },
            confirmButton = {
                TextButton(onClick = { viewModel.openAlarmPermissionSettings() }) {
                    Text("Grant Permission")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissAlarmPermissionDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("Clear Cache?") },
            text = { Text("This will delete all cached wallpapers. You can re-download them later.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearCache()
                        showClearCacheDialog = false
                    }
                ) {
                    Text("Clear", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showTimePickerDialog) {
        val currentTime = settings.dailyTime ?: DailyTime(8, 0)
        var selectedHour by remember { mutableStateOf(currentTime.hour) }
        var selectedMinute by remember { mutableStateOf(currentTime.minute) }
        
        AlertDialog(
            onDismissRequest = { showTimePickerDialog = false },
            title = { Text("Set Daily Change Time") },
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
                        // Simple Time Picker Implementation
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(onClick = { selectedHour = (selectedHour + 1) % 24 }) {
                                Icon(Icons.Default.ArrowUpward, "Up")
                            }
                            Text(
                                text = "%02d".format(selectedHour),
                                style = MaterialTheme.typography.headlineLarge
                            )
                            IconButton(onClick = { selectedHour = if (selectedHour == 0) 23 else selectedHour - 1 }) {
                                Icon(Icons.Default.ArrowDownward, "Down")
                            }
                        }
                        Text(":", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(horizontal = 8.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(onClick = { selectedMinute = (selectedMinute + 15) % 60 }) {
                                Icon(Icons.Default.ArrowUpward, "Up")
                            }
                            Text(
                                text = "%02d".format(selectedMinute),
                                style = MaterialTheme.typography.headlineLarge
                            )
                            IconButton(onClick = { selectedMinute = if (selectedMinute == 0) 45 else selectedMinute - 15 }) {
                                Icon(Icons.Default.ArrowDownward, "Down")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateDailyTime(DailyTime(selectedHour, selectedMinute))
                        showTimePickerDialog = false
                    }
                ) {
                    Text("Set")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePickerDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Bing Manifest Type Selection Dialog
    val showBingTypeDialog by viewModel.showBingTypeDialog.collectAsState()
    var selectedBingType by remember { mutableStateOf("lite") }
    
    if (showBingTypeDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissBingTypeDialog() },
            containerColor = if (isDark) Color(0xFF1F2937) else Color.White,
            title = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Choose Bing Collection",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else Color(0xFF111827)
                    )
                    Text(
                        text = "Select how much wallpaper history to download",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) Color.Gray else Color(0xFF6B7280),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Recent Hits (Lite) Option
                    BingTypeRadioCard(
                        title = "Recent Hits",
                        subtitle = "Last 3 years • ~1000 wallpapers",
                        description = "Faster download, newer wallpapers",
                        isSelected = selectedBingType == "lite",
                        onClick = { selectedBingType = "lite" },
                        isDark = isDark
                    )
                    
                    // Global Archive (Full) Option
                    BingTypeRadioCard(
                        title = "Global Archive",
                        subtitle = "2009-present • ~5400 wallpapers",
                        description = "Complete collection, larger download",
                        isSelected = selectedBingType == "full",
                        onClick = { selectedBingType = "full" },
                        isDark = isDark
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.onBingTypeSelected(selectedBingType) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDark) InfoColorDark else LightPrimary
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Download", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissBingTypeDialog() }) {
                    Text("Cancel", color = if (isDark) Color.Gray else Color(0xFF6B7280))
                }
            }
        )
    }
}

/**
 * Radio card for Bing manifest type selection dialog.
 */
@Composable
private fun BingTypeRadioCard(
    title: String,
    subtitle: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    isDark: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) {
                    if (isDark) InfoColorDark.copy(alpha = 0.15f) else LightPrimary.copy(alpha = 0.1f)
                } else {
                    if (isDark) Color(0xFF111827).copy(alpha = 0.5f) else Color(0xFFF3F4F6)
                }
            )
            .border(
                width = 2.dp,
                color = if (isSelected) {
                    if (isDark) InfoColorDark else LightPrimary
                } else {
                    Color.Transparent
                },
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Radio indicator
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .padding(top = 2.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) {
                            if (isDark) InfoColorDark else LightPrimary
                        } else {
                            Color.Transparent
                        }
                    )
                    .border(
                        width = 2.dp,
                        color = if (isSelected) {
                            if (isDark) InfoColorDark else LightPrimary
                        } else {
                            if (isDark) Color.Gray else Color(0xFFD1D5DB)
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
            }
            
            // Text content
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDark) Color.White else Color(0xFF111827)
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) InfoColorDark else LightPrimary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) Color.Gray else Color(0xFF6B7280),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}


