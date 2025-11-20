package me.avinas.vanderwaals.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

/**
 * Compose screen for app settings and preferences.
 * 
 * Organized into Material 3 preference sections:
 * 
 * **Mode Section:**
 * - Switch: Personalized / Auto
 * - Description text explaining current mode
 * - "Re-personalize Your Aesthetic" button (shows onboarding flow)
 * 
 * **Auto-Change Section:**
 * - Frequency dropdown: Every unlock / Hourly / Daily / Never
 * - Time picker (visible only if Daily selected)
 * - Visual indicator showing next scheduled change
 * 
 * **Apply To Section:**
 * - Radio buttons: Lock Screen / Home Screen / Both
 * - Preview cards showing where wallpaper will be applied
 * 
 * **Sources Section:**
 * - Checkboxes: GitHub Collections / Bing Wallpapers
 * - Last synced timestamp: "Last synced 2 days ago"
 * - "Sync Now" button (shows loading spinner when active)
 * - Expandable details showing wallpaper counts per source
 * 
 * **Storage Section:**
 * - Cache size display: "450 MB, 150 wallpapers"
 * - Download location: "Pictures/Vanderwaals"
 * - "Clear Cache" button with confirmation dialog
 * - Progress indicator for cache operations
 * 
 * **About Section:**
 * - App version number
 * - "Built on Paperize" credit with link
 * - Open source licenses
 * - GitHub repository link
 * - Privacy policy
 * 
 * Integrates with Paperize settings:
 * - Inherits existing SettingsDataStore for persistence
 * - Adds Vanderwaals-specific preferences
 * - Maintains compatibility with Paperize settings
 * 
 * @see SettingsViewModel
 * @see me.avinas.vanderwaals.data.datastore.SettingsDataStore
 * @see me.avinas.vanderwaals.domain.usecase.SyncWallpaperCatalogUseCase
 */
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
        topBar = {
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
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                modifier = Modifier.statusBarsPadding()
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            // Ambient background for glassmorphism
            val primaryColor = MaterialTheme.colorScheme.primary
            val secondaryColor = MaterialTheme.colorScheme.secondary
            val tertiaryColor = MaterialTheme.colorScheme.tertiary
            
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = primaryColor.copy(alpha = 0.08f),
                    center = Offset(size.width * 0.1f, size.height * 0.2f),
                    radius = size.minDimension * 0.3f
                )
                drawCircle(
                    color = secondaryColor.copy(alpha = 0.08f),
                    center = Offset(size.width * 0.9f, size.height * 0.5f),
                    radius = size.minDimension * 0.4f
                )
                drawCircle(
                    color = tertiaryColor.copy(alpha = 0.08f),
                    center = Offset(size.width * 0.2f, size.height * 0.8f),
                    radius = size.minDimension * 0.35f
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // MODE Section
            item {
                SettingsSection(title = "MODE") {
                    SettingsCard {
                        Column {
                            // Show different UI based on selected mode
                            if (settings.mode == "personalized") {
                                SettingsRow(
                                    icon = Icons.Default.AutoAwesome,
                                    title = "Personalized Mode",
                                    subtitle = "Learning from your preferences"
                                ) {
                                    Switch(
                                        checked = true,
                                        onCheckedChange = { enabled ->
                                            if (!enabled) viewModel.updateMode("auto")
                                        }
                                    )
                                }
                                
                                HorizontalDivider()
                                
                                SettingsRow(
                                    icon = Icons.Default.RestartAlt,
                                    title = "Re-personalize Your Aesthetic",
                                    subtitle = "Upload new favorite wallpapers",
                                    onClick = onNavigateToOnboarding
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                // Auto mode - no personalization toggle
                                SettingsRow(
                                    icon = Icons.Default.AutoAwesome,
                                    title = "Auto Mode",
                                    subtitle = "Automatic wallpaper selection"
                                ) {
                                    Switch(
                                        checked = false,
                                        onCheckedChange = { enabled ->
                                            if (enabled) viewModel.updateMode("personalized")
                                        }
                                    )
                                }
                                
                                HorizontalDivider()
                                
                                Text(
                                    text = "In Auto Mode, wallpapers are selected automatically without learning from your preferences. Enable Personalized Mode to teach the app your style.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // AUTO-CHANGE Section
            item {
                SettingsSection(title = "AUTO-CHANGE") {
                    SettingsCard {
                        Column {
                            var expanded by remember { mutableStateOf(false) }
                            
                            SettingsRow(
                                title = "Frequency",
                                subtitle = settings.interval.displayName
                            ) {
                                TextButton(onClick = { expanded = true }) {
                                    Text(settings.interval.displayName)
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null
                                    )
                                }
                                
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    ChangeInterval.entries.forEach { interval ->
                                        DropdownMenuItem(
                                            text = { Text(interval.displayName) },
                                            onClick = {
                                                viewModel.updateInterval(interval)
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                            
                            if (settings.interval == ChangeInterval.DAILY) {
                                HorizontalDivider()
                                
                                SettingsRow(
                                    title = "Change Time",
                                    subtitle = settings.dailyTime?.let { "${it.hour}:${it.minute.toString().padStart(2, '0')}" } ?: "Not set"
                                ) {
                                    TextButton(onClick = { showTimePickerDialog = true }) {
                                        Text(
                                            text = settings.dailyTime?.let { 
                                                String.format("%02d:%02d", it.hour, it.minute)
                                            } ?: "Set Time",
                                            color = Color(0xFFA8A095)
                                        )
                                    }
                                }
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
                    SettingsSection(title = "BATTERY & PERFORMANCE") {
                        me.avinas.vanderwaals.ui.components.BatteryOptimizationStatusCard(
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
            }
            
            // APPLY TO Section
            item {
                SettingsSection(title = "APPLY TO") {
                    SettingsCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ApplyTo.entries.forEach { option ->
                                FilterChip(
                                    selected = settings.applyTo == option,
                                    onClick = { viewModel.updateApplyTo(option) },
                                    label = { Text(option.displayName) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // SOURCES Section
            item {
                SettingsSection(title = "SOURCES") {
                    SettingsCard {
                        Column {
                            settings.sourcesEnabled.entries.forEachIndexed { index, (source, enabled) ->
                                if (index > 0) HorizontalDivider()
                                
                                SettingsRow(
                                    title = source,
                                    subtitle = if (enabled) "Enabled" else "Disabled"
                                ) {
                                    Checkbox(
                                        checked = enabled,
                                        onCheckedChange = { viewModel.toggleSource(source, it) }
                                    )
                                }
                            }
                            
                            HorizontalDivider()
                            
                            val isSyncing by viewModel.isSyncing.collectAsState()
                            
                            Button(
                                onClick = { viewModel.syncNow() },
                                enabled = !isSyncing,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFA8A095).copy(alpha = 0.2f),
                                    contentColor = Color(0xFFA8A095),
                                    disabledContainerColor = Color(0xFFA8A095).copy(alpha = 0.1f),
                                    disabledContentColor = Color(0xFFA8A095).copy(alpha = 0.5f)
                                )
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = Color(0xFFA8A095),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (isSyncing) "Syncing..." else "Sync Now")
                            }
                            
                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "Last synced: ${settings.lastSynced}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                
                                if (settings.lastSynced == "Never synced") {
                                    Text(
                                        text = "Sync wallpaper catalog to start using the app",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // STORAGE Section
            item {
                SettingsSection(title = "STORAGE") {
                    SettingsCard {
                        Column {
                            SettingsRow(
                                title = "Cache Size",
                                subtitle = settings.cacheSize
                            )
                            
                            HorizontalDivider()
                            
                            SettingsRow(
                                title = "Download Location",
                                subtitle = "Pictures/Vanderwaals"
                            )
                            
                            HorizontalDivider()
                            
                            Button(
                                onClick = { showClearCacheDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Red.copy(alpha = 0.1f),
                                    contentColor = Color.Red
                                )
                            ) {
                                Text("Clear Cache")
                            }
                        }
                    }
                }
            }

            // ANALYTICS Section
            item {
                SettingsSection(title = "INSIGHTS") {
                    SettingsCard {
                        SettingsRow(
                            icon = Icons.Default.Analytics,
                            title = "Personalization Analytics",
                            subtitle = "See how personalization is working for you",
                            onClick = onNavigateToAnalytics
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ABOUT Section
            item {
                SettingsSection(title = "ABOUT") {
                    SettingsCard {
                        Column {
                            SettingsRow(
                                title = "Version",
                                subtitle = "v${me.avinas.vanderwaals.BuildConfig.VERSION_NAME}"
                            )
                            
                            HorizontalDivider()
                            
                            SettingsRow(
                                title = "View on GitHub",
                                subtitle = "Star us on GitHub",
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/avinaxhroy/Vanderwaals"))
                                    context.startActivity(intent)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            HorizontalDivider()
                            
                            SettingsRow(
                                title = "Rate on Play Store",
                                subtitle = "Help us improve",
                                onClick = {
                                    val intent = android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("market://details?id=${context.packageName}")
                                    )
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: android.content.ActivityNotFoundException) {
                                        // Play Store not installed, open in browser
                                        val webIntent = android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")
                                        )
                                        context.startActivity(webIntent)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    }

    // Alarm permission dialog
    if (needsAlarmPermission) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissAlarmPermissionDialog() },
            title = { Text("Alarm Permission Required") },
            text = { 
                Text("To schedule automatic wallpaper changes at precise intervals, Vanderwaals needs permission to set alarms. This ensures your wallpaper changes reliably at the exact frequency you choose (15 minutes, hourly, or daily).")
            },
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

    // Clear cache confirmation dialog
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

    // Time picker dialog
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
                    Text(
                        text = "Select time for daily wallpaper change",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Hour picker
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(onClick = { 
                                selectedHour = (selectedHour + 1) % 24
                            }) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = "Increase hour"
                                )
                            }
                            
                            Text(
                                text = "%02d".format(selectedHour),
                                style = MaterialTheme.typography.headlineLarge,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            
                            IconButton(onClick = { 
                                selectedHour = if (selectedHour == 0) 23 else selectedHour - 1
                            }) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = "Decrease hour"
                                )
                            }
                        }
                        
                        Text(
                            text = ":",
                            style = MaterialTheme.typography.headlineLarge,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        
                        // Minute picker
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(onClick = { 
                                selectedMinute = (selectedMinute + 15) % 60
                            }) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = "Increase minute"
                                )
                            }
                            
                            Text(
                                text = "%02d".format(selectedMinute),
                                style = MaterialTheme.typography.headlineLarge,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            
                            IconButton(onClick = { 
                                selectedMinute = if (selectedMinute == 0) 45 else selectedMinute - 15
                            }) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = "Decrease minute"
                                )
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
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 12.dp),
            letterSpacing = 1.2.sp
        )
        content()
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
        ),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.02f)
                )
            )
        ),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        content()
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick)
                else Modifier
            )
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            icon?.let {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        trailing?.invoke()
    }
}
