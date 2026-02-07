package me.avinas.vanderwaals.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.avinas.vanderwaals.ui.theme.components.*
import me.avinas.vanderwaals.ui.theme.LiquidGlassBackground
import me.avinas.vanderwaals.worker.ChangeInterval
import java.time.LocalTime

@Composable
fun ApplicationSettingsScreen(
    onStartUsing: () -> Unit,
    onBackPressed: () -> Unit = {},
    selectedMode: OnboardingMode? = null,
    viewModel: ApplicationSettingsViewModel = hiltViewModel()
) {
    // Handle system back button
    androidx.activity.compose.BackHandler {
        onBackPressed()
    }

    val applyTo by viewModel.applyTo.collectAsStateWithLifecycle()
    val changeInterval by viewModel.changeInterval.collectAsStateWithLifecycle()
    val dailyTime by viewModel.dailyTime.collectAsStateWithLifecycle()
    val startState by viewModel.startState.collectAsStateWithLifecycle()
    val needsAlarmPermission by viewModel.needsAlarmPermission.collectAsStateWithLifecycle()
    val warningMessage by viewModel.warningMessage.collectAsStateWithLifecycle() // Assuming this exists or added

    val isDark = isSystemInDarkTheme()
    var showTimePicker by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Start using effect - trigger navigation
    LaunchedEffect(startState) {
        if (startState is StartState.Success) {
            onStartUsing()
        }
        if (startState is StartState.Error) {
             val error = (startState as StartState.Error).message
             snackbarHostState.showSnackbar(error)
             viewModel.resetStartState()
        }
    }

    LiquidGlassBackground {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Premium Background
            // Premium Background removed


            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                OnboardingTopAppBar(
                    onBack = onBackPressed,
                    showBack = true,
                    title = {
                        Text(
                            text = "Final Polish",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color.White else Color(0xFF111827)
                        )
                    }
                )

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    item {
                        Text(
                            text = "Customize how Vanderwaals works for you.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF4B5563),
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }

                    // APPLY TO SECTION
                    item {
                        LabelSectionHeader(title = "APPLY WALLPAPERS TO")
                        LiquidGlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(16.dp)
                        ) {
                             // Use ApplyTo enum values
                             val options = ApplyTo.values().toList()
                             val selectedIndex = options.indexOf(applyTo).coerceAtLeast(0)
                             
                             SegmentedControl(
                                 items = options.map { it.displayName },
                                 selectedIndex = selectedIndex,
                                 onItemSelected = { index ->
                                     viewModel.setApplyTo(options[index])
                                 },
                                 isDark = isDark
                             )
                        }
                    }

                    // CHANGE INTERVAL SECTION
                    item {
                        LabelSectionHeader(title = "UPDATE FREQUENCY")
                        LiquidGlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Column {
                                // We can use SettingsRow here for selection if list is long, 
                                // OR SegmentedControl if short. 
                                // ChangeInterval has 5 options. A vertical list of radio rows might be cleaner than a cramped 5-item segmented control.
                                
                                ChangeInterval.values().forEachIndexed { index, interval ->
                                    SettingsRadioButton(
                                        text = interval.displayName,
                                        selected = changeInterval == interval,
                                        onClick = { viewModel.setChangeInterval(interval) }
                                    )
                                    if (index < ChangeInterval.values().lastIndex) {
                                         ModernDivider(isDark = isDark)
                                    }
                                }
                            }
                        }
                    }
                    
                    // DAILY TIME SETTING (Conditional)
                    if (changeInterval == ChangeInterval.DAILY) {
                        item {
                            LiquidGlassCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showTimePicker = true },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                SettingsRow(
                                    title = "Update Time",
                                    subtitle = "Wallpapers will change at this time daily",
                                    onClick = { showTimePicker = true },
                                    trailing = {
                                        Text(
                                            text = String.format("%02d:%02d", dailyTime.hour, dailyTime.minute),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isDark) me.avinas.vanderwaals.ui.theme.InfoColorDark else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                )
                            }
                        }
                    }
                    
                    // Note regarding battery for high frequency
                    if (changeInterval == ChangeInterval.EVERY_UNLOCK) {
                        item {
                             Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle, // Using generic icon or battery if available
                                    contentDescription = null,
                                    tint = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Gray,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Changes happen once per minute max to preserve battery.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Gray
                                )
                            }
                        }
                    }
                }
                
                // Bottom Bar
                GlassSheet(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { viewModel.startUsing(selectedMode) },
                        enabled = startState !is StartState.Starting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDark) me.avinas.vanderwaals.ui.theme.InfoColorDark else MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        )
                    ) {
                        if (startState is StartState.Starting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = (startState as StartState.Starting).step,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            Text(
                                text = "Start Using Vanderwaals",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null
                            )
                        }
                    }
                }
            }
        }
    }
    }
    
    // Time Picker Logic (Simplified)
    if (showTimePicker) {
         // Use a custom Date/Time picker dialog or standard Material one
         // For simplicity here, assuming TimePickerDialog component exists or we use one from library.
         // If not, we might need to implement a simple one.
         // `ApplicationSettingsScreen` Step 145 included `TimePickerDialog`. I will assume it exists or needs to be retained.
         // Checking Step 144 view... it referenced `TimePickerDialog`.
         // I'll add a minimal implementation if needed, or rely on import if previously defined.
         // I'll add a basic AlertDialog with time inputs if needed.
         
         // Assuming TimePickerDialog composable exists in the file or nearby.
         // Wait, I am overwriting the file. If `TimePickerDialog` was in the file, I need to include it.
         // Step 144 showed `TimePickerDialog` at line 600+. I should copy it back or reimplement it.
         // I'll reimplement it briefly.
         
         BasicTimePickerDialog(
             initialTime = dailyTime,
             onConfirm = { 
                 viewModel.setDailyTime(it)
                 showTimePicker = false
             },
             onDismiss = { showTimePicker = false }
         )
    }
    
    // Alarm Permission
    if (needsAlarmPermission) {
         AlertDialog(
             onDismissRequest = { viewModel.dismissAlarmPermissionDialog() },
             title = { Text("Permission Required") },
             text = { Text("To change wallpapers at exact times, Vanderwaals needs 'Alarms & Reminders' permission. Without it, times may be inexact.") },
             confirmButton = {
                 TextButton(onClick = { viewModel.openAlarmPermissionSettings() }) {
                     Text("Grant")
                 }
             },
             dismissButton = {
                 TextButton(onClick = { viewModel.dismissAlarmPermissionDialog() }) {
                     Text("Use Inexact Timing")
                 }
             }
         )
    }
}

@Composable
fun SettingsRadioButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null // Handled by row
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BasicTimePickerDialog(
    initialTime: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onConfirm(LocalTime.of(timePickerState.hour, timePickerState.minute))
            }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        text = {
            TimePicker(state = timePickerState)
        }
    )
}
