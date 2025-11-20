package me.avinas.vanderwaals.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.avinas.vanderwaals.ui.theme.animations.bounceOnAppear
import me.avinas.vanderwaals.ui.theme.animations.pressAnimation
import me.avinas.vanderwaals.worker.ChangeInterval
import me.avinas.vanderwaals.worker.WorkScheduler
import java.time.LocalTime

/**
 * Application Settings Screen - Final screen in onboarding flow.
 * 
 * Configures wallpaper application settings:
 * - **Apply To**: Lock Screen, Home Screen, or Both
 * - **Change Interval**: Every unlock, Hourly, Daily, or Never
 * - **Daily Time**: Time picker (if Daily selected)
 * 
 * **On Start:**
 * 1. Save settings to SharedPreferences
 * 2. Schedule WorkManager tasks
 * 3. Apply first wallpaper immediately
 * 4. Navigate to main screen
 * 
 * @param onStartUsing Callback when app starts
 * @param onBackPressed Callback when back button is pressed
 * @param selectedMode Selected mode from ModeSelectionScreen (Auto or Personalize)
 * @param viewModel ViewModel managing settings
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApplicationSettingsScreen(
    onStartUsing: () -> Unit,
    onBackPressed: () -> Unit = {},
    selectedMode: OnboardingMode? = null,
    viewModel: ApplicationSettingsViewModel = hiltViewModel()
) {
    // Handle system back button
    androidx.activity.compose.BackHandler {
        android.util.Log.d("ApplicationSettingsScreen", "BackHandler triggered!")
        onBackPressed()
    }
    
    val applyTo by viewModel.applyTo.collectAsStateWithLifecycle()
    val changeInterval by viewModel.changeInterval.collectAsStateWithLifecycle()
    val dailyTime by viewModel.dailyTime.collectAsStateWithLifecycle()
    val startState by viewModel.startState.collectAsStateWithLifecycle()
    val needsAlarmPermission by viewModel.needsAlarmPermission.collectAsStateWithLifecycle()
    
    var showTimePicker by remember { mutableStateOf(false) }
    
    // Navigate when start succeeds
    LaunchedEffect(startState) {
        if (startState is StartState.Success) {
            onStartUsing()
        }
    }
    
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = {
                        android.util.Log.d("ApplicationSettingsScreen", "Back icon clicked!")
                        android.util.Log.d("ApplicationSettingsScreen", "About to call onBackPressed")
                        try {
                            onBackPressed()
                            android.util.Log.d("ApplicationSettingsScreen", "Called onBackPressed successfully")
                        } catch (e: Exception) {
                            android.util.Log.e("ApplicationSettingsScreen", "ERROR calling onBackPressed!", e)
                        }
                    }) {
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
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 48.dp)
                ) {
                    // Show progress indicator when starting
                    if (startState is StartState.Starting) {
                        val progress = (startState as StartState.Starting).progress
                        val step = (startState as StartState.Starting).step
                        
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Progress text
                            Text(
                                text = step,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            // Progress bar or indeterminate indicator
                            if (progress != null) {
                                // Show determinate progress with percentage
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp),
                                    color = me.avinas.vanderwaals.ui.theme.VanderwaalsTan,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                                
                                // Percentage text
                                Text(
                                    text = "${(progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            } else {
                                // Show indeterminate progress
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp),
                                    color = me.avinas.vanderwaals.ui.theme.VanderwaalsTan,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                            }
                        }
                    } else {
                        // Show button when not starting
                        Button(
                            onClick = { viewModel.startUsing(selectedMode) },
                            enabled = startState !is StartState.Starting,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = me.avinas.vanderwaals.ui.theme.VanderwaalsTan
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .pressAnimation()
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Start Using Vanderwaals",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
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

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))
                
                // Title with animation
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .bounceOnAppear()
                ) {
                    Text(
                        text = "Configure Settings",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Choose how and when to change wallpapers",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                
                // Apply To Section
                SettingsSection(
                    title = "Apply To",
                    description = "Where should wallpapers be applied?"
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectableGroup()
                    ) {
                        ApplyTo.values().forEach { option ->
                            SettingsRadioButton(
                                text = option.displayName,
                                selected = applyTo == option,
                                onClick = { viewModel.setApplyTo(option) }
                            )
                        }
                    }
                }
                
                // Change Interval Section
                SettingsSection(
                    title = "Change Wallpaper",
                    description = "How often should wallpapers change?"
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectableGroup()
                    ) {
                        ChangeInterval.values().forEach { option ->
                            SettingsRadioButton(
                                text = option.displayName,
                                selected = changeInterval == option,
                                onClick = { viewModel.setChangeInterval(option) }
                            )
                        }
                    }
                    
                    // Time Picker for Daily
                    if (changeInterval == ChangeInterval.DAILY) {
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Card(
                            onClick = { showTimePicker = true },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Change at",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "${dailyTime.hour.toString().padStart(2, '0')}:${dailyTime.minute.toString().padStart(2, '0')}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Time Picker Dialog
        if (showTimePicker) {
            TimePickerDialog(
                initialTime = dailyTime,
                onDismiss = { showTimePicker = false },
                onConfirm = { time ->
                    viewModel.setDailyTime(time)
                    showTimePicker = false
                }
            )
        }
        
        // Alarm Permission Dialog
        if (needsAlarmPermission) {
            AlarmPermissionDialog(viewModel = viewModel)
        }
        
        // Error Snackbar
        if (startState is StartState.Error) {
            Snackbar(
                modifier = Modifier.padding(16.dp),
                action = {
                    TextButton(onClick = { viewModel.resetStartState() }) {
                        Text("Dismiss")
                    }
                }
            ) {
                Text((startState as StartState.Error).message)
            }
        }
    }
}

/**
 * Settings section with title and description.
 * 
 * @param title Section title
 * @param description Section description
 * @param content Section content
 */
@Composable
private fun SettingsSection(
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
        
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                content = content
            )
        }
    }
}

/**
 * Radio button option.
 * 
 * @param text Option text
 * @param selected Whether option is selected
 * @param onClick Click callback
 */
@Composable
private fun SettingsRadioButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .background(
                if (selected) 
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else 
                    Color.Transparent
            )
            .padding(vertical = 12.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary,
                unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Time picker dialog.
 * 
 * @param initialTime Initial time
 * @param onDismiss Dismiss callback
 * @param onConfirm Confirm callback with selected time
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialTime: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = true
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Time") },
        text = {
            TimePicker(
                state = timePickerState
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        LocalTime.of(
                            timePickerState.hour,
                            timePickerState.minute
                        )
                    )
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun AlarmPermissionDialog(
    viewModel: ApplicationSettingsViewModel
) {
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
