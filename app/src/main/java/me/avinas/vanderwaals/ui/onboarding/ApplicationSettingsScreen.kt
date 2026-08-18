package me.avinas.vanderwaals.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
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
import me.avinas.vanderwaals.ui.settings.RadicalAlertDialog
import me.avinas.vanderwaals.ui.settings.RadicalApplyToSelector
import me.avinas.vanderwaals.ui.settings.RadicalDivider
import me.avinas.vanderwaals.ui.settings.RadicalPalette
import me.avinas.vanderwaals.ui.settings.RadicalRadioRow
import me.avinas.vanderwaals.ui.settings.RadicalTactileBackdrop
import me.avinas.vanderwaals.ui.settings.RadicalTactileCard
import me.avinas.vanderwaals.ui.settings.RadicalTactileSlider
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
                            accentColor = RadicalPalette.RadiantAmber,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        OnboardingHeader(
                            stepLabel = "STAGE 0$currentStep / 0$totalSteps · FINAL CONFIGURATION",
                            title = "Set your schedule & targets",
                            subtitle = "Choose which screens to update and how often wallpapers change.",
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
                            Column {
                                RadicalRadioRow(
                                    title = "Daily Refresh",
                                    subtitle = "Updates once per day",
                                    description = "Updates your wallpaper once a day at your chosen time.",
                                    isSelected = changeInterval == ChangeInterval.DAILY,
                                    onClick = { viewModel.setChangeInterval(ChangeInterval.DAILY) },
                                    isDark = isDark,
                                    accentColor = RadicalPalette.RadiantAmber
                                )

                                AnimatedVisibility(
                                    visible = changeInterval == ChangeInterval.DAILY,
                                    enter = expandVertically(animationSpec = tween(200, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(150)),
                                    exit = shrinkVertically(animationSpec = tween(150, easing = FastOutLinearInEasing)) + fadeOut(animationSpec = tween(100))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                    if (isDark) Color(0xFFE4DDD2)
                                                    else Color(0xFF03261C)
                                                )
                                                .border(
                                                    1.dp,
                                                    if (isDark) Color(0xFFCBC3B5) else Color(0xFF0D5E47),
                                                    RoundedCornerShape(12.dp)
                                                )
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = ripple(),
                                                    onClick = {
                                                        selectedHour = dailyTime.hour
                                                        selectedMinute = dailyTime.minute
                                                        showTimePicker = true
                                                    }
                                                )
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Schedule,
                                                    contentDescription = null,
                                                    tint = RadicalPalette.RadiantAmber,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Column {
                                                    Text(
                                                        text = "Update Time",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isDark) Color(0xFF1C1917) else Color(0xFFFFFFFF),
                                                        fontSize = 13.sp
                                                    )
                                                    Text(
                                                        text = "Tap to modify scheduled time",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = if (isDark) Color(0xFF57534E) else Color(0xFFA7F3D0),
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(
                                                        if (isDark) Color(0xFFFEF3C7)
                                                        else Color(0xFF0D5E47)
                                                    )
                                                    .border(
                                                        1.dp,
                                                        if (isDark) Color(0xFFFDE68A) else Color(0xFF6EE7B7),
                                                        RoundedCornerShape(8.dp)
                                                    )
                                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = String.format("%02d:%02d", dailyTime.hour, dailyTime.minute),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = if (isDark) Color(0xFFB45309) else Color(0xFF6EE7B7),
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                RadicalDivider(isDark = isDark)

                                RadicalRadioRow(
                                    title = "Every Screen Unlock",
                                    subtitle = "Fresh wallpaper on screen unlock",
                                    description = "Updates each time you unlock your phone, with a 1-minute cooldown to save battery.",
                                    isSelected = changeInterval == ChangeInterval.EVERY_UNLOCK,
                                    onClick = { viewModel.setChangeInterval(ChangeInterval.EVERY_UNLOCK) },
                                    isDark = isDark,
                                    accentColor = RadicalPalette.EmeraldJade
                                )

                                AnimatedVisibility(
                                    visible = changeInterval == ChangeInterval.EVERY_UNLOCK,
                                    enter = expandVertically(animationSpec = tween(200, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(150)),
                                    exit = shrinkVertically(animationSpec = tween(150, easing = FastOutLinearInEasing)) + fadeOut(animationSpec = tween(100))
                                ) {
                                    Column {
                                        RadicalDivider(isDark = isDark)
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Daily Unlock Playlist Size",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isDark) Color(0xFF1C1917) else Color(0xFFFFFFFF)
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(
                                                            if (isDark) Color(0xFFFFE4EC)
                                                            else Color(0xFF03261C)
                                                        )
                                                        .border(
                                                            1.dp,
                                                            if (isDark) Color(0xFFFECDD3) else Color(0xFF0D5E47),
                                                            RoundedCornerShape(8.dp)
                                                        )
                                                        .padding(horizontal = 10.dp, vertical = 3.dp)
                                                ) {
                                                    Text(
                                                        text = "$dailyPlaylistSize wallpapers",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isDark) Color(0xFFBE123C) else Color(0xFF6EE7B7)
                                                    )
                                                }
                                            }

                                            RadicalTactileSlider(
                                                value = dailyPlaylistSize.toFloat(),
                                                onValueChange = { viewModel.setDailyPlaylistSize(it.toInt()) },
                                                valueRange = 10f..50f,
                                                steps = 39,
                                                isDark = isDark,
                                                accentColor = RadicalPalette.CyberMagenta
                                            )

                                            Text(
                                                text = "Downloads a daily set and rotates on unlock, with a 1-minute cooldown.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (isDark) Color(0xFF383532) else Color(0xFFA7F3D0),
                                                lineHeight = 18.sp
                                            )
                                        }
                                    }
                                }

                                RadicalDivider(isDark = isDark)

                                RadicalRadioRow(
                                    title = "Manual Only",
                                    subtitle = "Never auto-change",
                                    description = "Browse, like, and apply wallpapers manually whenever you choose.",
                                    isSelected = changeInterval == ChangeInterval.NEVER,
                                    onClick = { viewModel.setChangeInterval(ChangeInterval.NEVER) },
                                    isDark = isDark,
                                    accentColor = RadicalPalette.PlatinumSilver
                                )
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "Your first wallpaper will be applied right away. You can adjust all settings anytime.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
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
