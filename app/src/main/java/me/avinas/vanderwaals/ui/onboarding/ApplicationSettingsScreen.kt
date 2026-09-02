package me.avinas.vanderwaals.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.avinas.vanderwaals.ui.settings.DailyTime
import me.avinas.vanderwaals.ui.settings.RadicalAlertDialog
import me.avinas.vanderwaals.ui.settings.RadicalApplyToSelector
import me.avinas.vanderwaals.ui.settings.RadicalFrequencyStudio
import me.avinas.vanderwaals.ui.settings.RadicalPalette
import me.avinas.vanderwaals.ui.settings.RadicalTactileBackdrop
import me.avinas.vanderwaals.ui.settings.RadicalTactileCard
import me.avinas.vanderwaals.ui.settings.RadicalTimePickerDialog
import me.avinas.vanderwaals.ui.theme.LocalThemeIsDark
import me.avinas.vanderwaals.worker.ChangeInterval
import java.time.LocalTime

// Conversion helpers between onboarding ApplyTo and settings ApplyTo
private fun ApplyTo.toSettingsApplyTo(): me.avinas.vanderwaals.ui.settings.ApplyTo = when (this) {
    ApplyTo.LOCK_SCREEN -> me.avinas.vanderwaals.ui.settings.ApplyTo.LOCK_SCREEN
    ApplyTo.HOME_SCREEN -> me.avinas.vanderwaals.ui.settings.ApplyTo.HOME_SCREEN
    ApplyTo.BOTH -> me.avinas.vanderwaals.ui.settings.ApplyTo.BOTH
    ApplyTo.BOTH_DIFFERENT -> me.avinas.vanderwaals.ui.settings.ApplyTo.BOTH_DIFFERENT
}

private fun me.avinas.vanderwaals.ui.settings.ApplyTo.toOnboardingApplyTo(): ApplyTo = when (this) {
    me.avinas.vanderwaals.ui.settings.ApplyTo.LOCK_SCREEN -> ApplyTo.LOCK_SCREEN
    me.avinas.vanderwaals.ui.settings.ApplyTo.HOME_SCREEN -> ApplyTo.HOME_SCREEN
    me.avinas.vanderwaals.ui.settings.ApplyTo.BOTH -> ApplyTo.BOTH
    me.avinas.vanderwaals.ui.settings.ApplyTo.BOTH_DIFFERENT -> ApplyTo.BOTH_DIFFERENT
}

private fun me.avinas.vanderwaals.worker.ChangeInterval.toSettingsChangeInterval(): me.avinas.vanderwaals.ui.settings.ChangeInterval =
    me.avinas.vanderwaals.ui.settings.ChangeInterval.valueOf(this.name)

private fun me.avinas.vanderwaals.ui.settings.ChangeInterval.toWorkerChangeInterval(): me.avinas.vanderwaals.worker.ChangeInterval =
    me.avinas.vanderwaals.worker.ChangeInterval.valueOf(this.name)

@Composable
fun ApplicationSettingsScreen(
    onStartUsing: () -> Unit,
    onBackPressed: () -> Unit = {},
    selectedMode: OnboardingMode? = null,
    viewModel: ApplicationSettingsViewModel = hiltViewModel(),
    currentStep: Int = 6,
    totalSteps: Int = 6
) {
    androidx.activity.compose.BackHandler { onBackPressed() }

    val applyTo by viewModel.applyTo.collectAsStateWithLifecycle()
    val changeInterval by viewModel.changeInterval.collectAsStateWithLifecycle()
    val dailyTime by viewModel.dailyTime.collectAsStateWithLifecycle()
    val dailyPlaylistSize by viewModel.dailyPlaylistSize.collectAsStateWithLifecycle()
    val startState by viewModel.startState.collectAsStateWithLifecycle()
    val needsAlarmPermission by viewModel.needsAlarmPermission.collectAsStateWithLifecycle()

    val isDark = LocalThemeIsDark.current
    val metrics = rememberOnboardingLayoutMetrics()
    var showTimePicker by remember { mutableStateOf(false) }
    var selectedHour by remember(dailyTime) { mutableStateOf(dailyTime.hour) }
    var selectedMinute by remember(dailyTime) { mutableStateOf(dailyTime.minute) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(startState) {
        if (startState is StartState.Success) onStartUsing()
        if (startState is StartState.Error) {
            val error = (startState as StartState.Error).message
            snackbarHostState.showSnackbar(error)
            viewModel.resetStartState()
        }
    }

    val isLoading = startState is StartState.Starting

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            OnboardingTopBar(isDark = isDark, metrics = metrics, onBack = onBackPressed)
        },
        bottomBar = {
            OnboardingBottomBar(
                isDark = isDark,
                metrics = metrics,
                buttonEnabled = startState !is StartState.Starting,
                buttonText = "Start Using Vanderwaals",
                showLoading = isLoading,
                loadingText = if (isLoading) (startState as StartState.Starting).step else "",
                accentColor = RadicalPalette.CyberMagenta,
                onButtonClick = { viewModel.startUsing(selectedMode) }
            )
        }
    ) { paddingValues ->
        Box(Modifier.fillMaxSize()) {
            RadicalTactileBackdrop(isDark = isDark, modifier = Modifier.matchParentSize())

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = metrics.maxContentWidth)
                    .align(Alignment.TopCenter)
                    .padding(horizontal = metrics.horizontalPadding),
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding() + 8.dp,
                    bottom = paddingValues.calculateBottomPadding() + 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Column(Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
                        OnboardingStepIndicator(
                            currentStep = currentStep - 1,
                            totalSteps = totalSteps,
                            isDark = isDark,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        OnboardingHeader(
                            title = "Schedule & targets",
                            subtitle = "",
                            isDark = isDark,
                            accentColor = RadicalPalette.RadiantAmber
                        )
                    }
                }

                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Apply Wallpapers To",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                            letterSpacing = 0.6.sp,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )

                        RadicalTactileCard(isDark = isDark) {
                            RadicalApplyToSelector(
                                selectedTarget = applyTo.toSettingsApplyTo(),
                                onTargetSelected = { newTarget ->
                                    viewModel.setApplyTo(newTarget.toOnboardingApplyTo())
                                },
                                isDark = isDark,
                                accentColor = RadicalPalette.TealCyan
                            )
                        }
                    }
                }

                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Rotation Schedule",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                            letterSpacing = 0.6.sp,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )

                        RadicalTactileCard(isDark = isDark) {
                            RadicalFrequencyStudio(
                                currentInterval = changeInterval.toSettingsChangeInterval(),
                                onIntervalSelected = { newInterval ->
                                    viewModel.setChangeInterval(newInterval.toWorkerChangeInterval())
                                },
                                dailyTime = DailyTime(dailyTime.hour, dailyTime.minute),
                                onOpenFullTimePicker = {
                                    selectedHour = dailyTime.hour
                                    selectedMinute = dailyTime.minute
                                    showTimePicker = true
                                },
                                isDark = isDark,
                                accentColor = RadicalPalette.CyberMagenta
                            )
                        }
                    }
                }
            }
        }
    }

    if (showTimePicker) {
        RadicalTimePickerDialog(
            onDismissRequest = { showTimePicker = false },
            initialHour = selectedHour,
            initialMinute = selectedMinute,
            onHourChange = { selectedHour = it },
            onMinuteChange = { selectedMinute = it },
            onConfirm = {
                viewModel.setDailyTime(LocalTime.of(selectedHour, selectedMinute))
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
            isDark = isDark
        )
    }

    if (needsAlarmPermission) {
        RadicalAlertDialog(
            onDismissRequest = { viewModel.dismissAlarmPermissionDialog() },
            title = "Allow Exact Schedule Timing?",
            message = "To update wallpapers at your exact chosen time, Vanderwaals needs the Alarms & Reminders permission. Without it, Android may delay updates.",
            confirmText = "Grant Permission",
            onConfirm = { viewModel.openAlarmPermissionSettings() },
            dismissText = "Keep Inexact",
            onDismiss = { viewModel.dismissAlarmPermissionDialog() },
            isDark = isDark
        )
    }
}
