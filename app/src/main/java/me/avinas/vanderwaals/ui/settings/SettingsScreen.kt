package me.avinas.vanderwaals.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import me.avinas.vanderwaals.ui.theme.LocalThemeIsDark
import me.avinas.vanderwaals.ui.theme.components.GlassCard

// Mockup Colors
private val DarkIndigo400 = Color(0xFF818CF8)
private val DarkRose400 = Color(0xFFFB7185)
private val DarkSky400 = Color(0xFF38BDF8)
private val DarkBackground = Color(0xFF111827)

private val LightPurple400 = Color(0xFFC084FC)
private val LightOrange400 = Color(0xFFFB923C)
private val LightTeal400 = Color(0xFF2DD4BF)
private val LightBackground = Color(0xFFF6F8F7)
private val LightPrimary = Color(0xFF13EC6D)

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
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else Color(0xFF111827)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = if (isDark) Color.Gray else Color.Gray
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                modifier = Modifier.statusBarsPadding()
            )
        },
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
            // Background Blobs
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                if (isDark) {
                    // Dark Mode Blobs
                    drawCircle(
                        color = DarkIndigo400.copy(alpha = 0.2f),
                        center = Offset(0f, 0f),
                        radius = 400.dp.toPx()
                    )
                    drawCircle(
                        color = DarkRose400.copy(alpha = 0.1f),
                        center = Offset(w, h * 0.8f),
                        radius = 400.dp.toPx()
                    )
                    drawCircle(
                        color = DarkSky400.copy(alpha = 0.1f),
                        center = Offset(0f, h),
                        radius = 320.dp.toPx()
                    )
                } else {
                    // Light Mode Blobs
                    drawCircle(
                        color = LightPurple400.copy(alpha = 0.3f),
                        center = Offset(0f, 0f),
                        radius = 500.dp.toPx()
                    )
                    drawCircle(
                        color = LightOrange400.copy(alpha = 0.2f),
                        center = Offset(w, h * 0.8f),
                        radius = 500.dp.toPx()
                    )
                    drawCircle(
                        color = LightTeal400.copy(alpha = 0.2f),
                        center = Offset(0f, h + 200f),
                        radius = 400.dp.toPx()
                    )
                }
            }
            
            // Blur effect over blobs
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(60.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // MODE Section
                item {
                    SettingsSectionHeader(title = "MODE")
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
                                            tint = if (isDark) DarkIndigo400 else LightPrimary,
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
                                        checkedTrackColor = if (isDark) DarkIndigo400 else LightPrimary,
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
                                        tint = if (isDark) Color.Gray else Color(0xFF9CA3AF)
                                    )
                                }
                            }
                        }
                    }
                }

                // APPEARANCE Section
                item {
                    SettingsSectionHeader(title = "APPEARANCE")
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
                    SettingsSectionHeader(title = "AUTO-CHANGE")
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
                        SettingsSectionHeader(title = "BATTERY & PERFORMANCE")
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
                    SettingsSectionHeader(title = "APPLY TO")
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
                    SettingsSectionHeader(title = "SOURCES")
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
                                            .background(if (enabled) (if (isDark) DarkIndigo400 else LightPrimary) else Color.Transparent)
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
                                    color = if (isDark) Color.Gray else Color(0xFF6B7280)
                                )
                                
                                if (settings.lastSynced == "Never synced") {
                                    Text(
                                        text = "Sync wallpaper catalog to start",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isDark) DarkRose400 else Color(0xFFEF4444),
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
                    SettingsSectionHeader(title = "STORAGE")
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
                                    containerColor = if (isDark) DarkRose400.copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.1f),
                                    contentColor = if (isDark) DarkRose400 else Color(0xFFEF4444)
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
                    SettingsSectionHeader(title = "INSIGHTS")
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
                                        tint = if (isDark) DarkIndigo400 else LightPrimary,
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
                    SettingsSectionHeader(title = "ABOUT")
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
                                    tint = if (isDark) Color.Gray else Color(0xFF9CA3AF)
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
}

@Composable
fun SettingsSectionHeader(title: String) {
    val isDark = LocalThemeIsDark.current
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = if (isDark) Color.Gray else Color(0xFF6B7280),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        letterSpacing = 1.2.sp
    )
}

@Composable
fun SettingsRow(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    textColor: Color = Color.Unspecified,
    trailing: @Composable (() -> Unit)? = null
) {
    val isDark = LocalThemeIsDark.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) Color.Gray else Color(0xFF6B7280)
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun SegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    isDark: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isDark) Color.Black.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.05f),
                RoundedCornerShape(8.dp)
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEachIndexed { index, item ->
            val isSelected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (isSelected) {
                            if (isDark) DarkIndigo400 else LightPrimary
                        } else {
                            Color.Transparent
                        }
                    )
                    .clickable { onItemSelected(index) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) Color.White else (if (isDark) Color.Gray else Color(0xFF4B5563))
                )
            }
        }
    }
}
