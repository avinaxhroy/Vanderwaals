package me.avinas.vanderwaals.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.avinas.vanderwaals.ui.theme.BorderDark
import me.avinas.vanderwaals.ui.theme.BorderLight
import me.avinas.vanderwaals.ui.theme.BrandPrimary
import me.avinas.vanderwaals.ui.theme.LocalThemeIsDark
import me.avinas.vanderwaals.ui.theme.LuxeBodyStyle
import me.avinas.vanderwaals.ui.theme.LuxeCardBackground
import me.avinas.vanderwaals.ui.theme.LuxeCardBorder
import me.avinas.vanderwaals.ui.theme.LuxeHeadlineStyle
import me.avinas.vanderwaals.ui.theme.LuxeTextPrimary
import me.avinas.vanderwaals.ui.theme.LuxeTextSecondary
import me.avinas.vanderwaals.ui.theme.SurfaceElevatedDark
import me.avinas.vanderwaals.ui.theme.SurfaceHighlightDark
import me.avinas.vanderwaals.ui.theme.SurfaceHighlightLight
import me.avinas.vanderwaals.ui.theme.SurfaceLight
import me.avinas.vanderwaals.ui.theme.SurfaceOverlayDark
import me.avinas.vanderwaals.ui.theme.TextPrimaryDark
import me.avinas.vanderwaals.ui.theme.TextPrimaryLight
import me.avinas.vanderwaals.ui.theme.TextSecondaryDark
import me.avinas.vanderwaals.ui.theme.TextSecondaryLight
import me.avinas.vanderwaals.ui.theme.TextTertiaryDark
import me.avinas.vanderwaals.ui.theme.TextTertiaryLight
import me.avinas.vanderwaals.ui.theme.components.SegmentedControl
import me.avinas.vanderwaals.worker.ChangeInterval
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApplicationSettingsScreen(
    onStartUsing: () -> Unit,
    onBackPressed: () -> Unit = {},
    selectedMode: OnboardingMode? = null,
    viewModel: ApplicationSettingsViewModel = hiltViewModel(),
    currentStep: Int = 6,
    totalSteps: Int = 6
) {
    androidx.activity.compose.BackHandler {
        onBackPressed()
    }

    val applyTo by viewModel.applyTo.collectAsStateWithLifecycle()
    val changeInterval by viewModel.changeInterval.collectAsStateWithLifecycle()
    val dailyTime by viewModel.dailyTime.collectAsStateWithLifecycle()
    val startState by viewModel.startState.collectAsStateWithLifecycle()
    val needsAlarmPermission by viewModel.needsAlarmPermission.collectAsStateWithLifecycle()

    val isDark = LocalThemeIsDark.current
    val metrics = rememberOnboardingLayoutMetrics()
    var showTimePicker by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

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

    val isLoading = startState is StartState.Starting

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(metrics.topBarHeight),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = metrics.maxContentWidth)
                        .padding(horizontal = metrics.horizontalPadding),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = getOnboardingTextPrimary(isDark)
                        )
                    }
                }
            }
        },
        bottomBar = {
            OnboardingBottomBar(
                isDark = isDark,
                metrics = metrics,
                buttonEnabled = startState !is StartState.Starting,
                buttonText = "Start Using Vanderwaals",
                showBorderGradient = !isLoading,
                showLoading = isLoading,
                loadingText = if (isLoading) (startState as StartState.Starting).step else "",
                onButtonClick = { viewModel.startUsing(selectedMode) }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            OnboardingBackdrop(
                isDark = isDark,
                modifier = Modifier.matchParentSize()
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = metrics.horizontalPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = metrics.maxContentWidth)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = paddingValues.calculateTopPadding() + 12.dp,
                            bottom = paddingValues.calculateBottomPadding() + 24.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(metrics.cardSpacing)
                    ) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = metrics.sectionSpacing),
                                horizontalAlignment = Alignment.Start
                            ) {
                                OnboardingStepIndicator(
                                    currentStep = currentStep - 1,
                                    totalSteps = totalSteps,
                                    isDark = isDark,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )

                                Text(
                                    text = "Step $currentStep of $totalSteps",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = BrandPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )

                                Text(
                                    text = "Final Touches",
                                    style = LuxeHeadlineStyle,
                                    color = getOnboardingTextPrimary(isDark),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                Text(
                                    text = "Set how and when wallpapers change",
                                    style = LuxeBodyStyle,
                                    color = getOnboardingTextSecondary(isDark),
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                        }

                        item {
                            SettingsSectionHeader("APPLY WALLPAPERS TO", isDark)
                            PremiumSettingsCard(
                                isDark = isDark,
                                contentPadding = PaddingValues(10.dp)
                            ) {
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

                        item {
                            SettingsSectionHeader("UPDATE FREQUENCY", isDark)
                            PremiumSettingsCard(isDark = isDark) {
                                Column {
                                    ChangeInterval.values().forEachIndexed { index, interval ->
                                        RadioRow(
                                            text = interval.displayName,
                                            selected = changeInterval == interval,
                                            onClick = { viewModel.setChangeInterval(interval) },
                                            isDark = isDark
                                        )
                                        if (index < ChangeInterval.values().lastIndex) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(start = 56.dp),
                                                color = getOnboardingCardBorder(isDark)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (changeInterval == ChangeInterval.DAILY) {
                            item {
                                PremiumSettingsCard(
                                    isDark = isDark,
                                    onClick = { showTimePicker = true }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(18.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(metrics.iconBoxSize)
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .background(BrandPrimary.copy(alpha = 0.15f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Schedule,
                                                    contentDescription = null,
                                                    tint = BrandPrimary,
                                                    modifier = Modifier.size(metrics.iconSize)
                                                )
                                            }
                                            Column {
                                                Text(
                                                    text = "Update Time",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Medium,
                                                    color = getOnboardingTextPrimary(isDark)
                                                )
                                                Text(
                                                    text = "Wallpapers change daily at this time",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = getOnboardingTextSecondary(isDark)
                                                )
                                            }
                                        }
                                        Text(
                                            text = String.format("%02d:%02d", dailyTime.hour, dailyTime.minute),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = BrandPrimary
                                        )
                                    }
                                }
                            }
                        }

                        if (changeInterval == ChangeInterval.EVERY_UNLOCK) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(getOnboardingCardBackground(isDark))
                                        .border(1.dp, getOnboardingCardBorder(isDark), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = BrandPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Changes happen once per minute max to preserve battery.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = getOnboardingTextSecondary(isDark)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTimePicker) {
        PremiumTimePickerDialog(
            initialTime = dailyTime,
            onConfirm = {
                viewModel.setDailyTime(it)
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
            isDark = isDark
        )
    }

    if (needsAlarmPermission) {
        PremiumAlertDialog(
            onDismissRequest = { viewModel.dismissAlarmPermissionDialog() },
            title = "Permission Required",
            message = "To change wallpapers at exact times, Vanderwaals needs 'Alarms & Reminders' permission. Without it, times may be inexact.",
            confirmText = "Grant",
            onConfirm = { viewModel.openAlarmPermissionSettings() },
            dismissText = "Use Inexact Timing",
            onDismiss = { viewModel.dismissAlarmPermissionDialog() },
            isDark = isDark
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String, isDark: Boolean) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = getOnboardingTextSecondary(isDark),
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
    )
}

@Composable
private fun PremiumSettingsCard(
    isDark: Boolean,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val backgroundColor = getOnboardingCardBackground(isDark)
    val borderColor = getOnboardingCardBorder(isDark)
    val metrics = rememberOnboardingLayoutMetrics()

    val cardModifier = Modifier
        .fillMaxWidth()
        .shadow(
            elevation = 4.dp,
            shape = RoundedCornerShape(metrics.cardCornerRadius),
            ambientColor = if (isDark) Color(0xFF3F3F46).copy(alpha = 0.12f) else Color(0x0A000000),
            spotColor = Color.Transparent
        )
        .border(1.dp, borderColor, RoundedCornerShape(metrics.cardCornerRadius))
        .clip(RoundedCornerShape(metrics.cardCornerRadius))
        .background(backgroundColor)
        .let {
            if (onClick != null) it.bounceClick(onClick) else it
        }

    Column(
        modifier = cardModifier.padding(contentPadding),
        content = content
    )
}

@Composable
private fun RadioRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    isDark: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    color = if (selected) BrandPrimary else (if (isDark) Color(0xFF3F3F46) else Color(0xFFD4D4D4)),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(BrandPrimary)
                )
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = getOnboardingTextPrimary(isDark)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PremiumTimePickerDialog(
    initialTime: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
    isDark: Boolean
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (isDark) Color(0xFF14120F) else Color(0xFFF9F7F5),
        title = {
            Text(
                text = "Set Update Time",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = getOnboardingTextPrimary(isDark)
            )
        },
        text = {
            TimePicker(state = timePickerState)
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(LocalTime.of(timePickerState.hour, timePickerState.minute))
                },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = BrandPrimary
                )
            ) {
                Text("OK", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = getOnboardingTextSecondary(isDark)
                )
            ) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
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
    isDark: Boolean
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = if (isDark) Color(0xFF14120F) else Color(0xFFF9F7F5),
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = getOnboardingTextPrimary(isDark)
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = getOnboardingTextSecondary(isDark)
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = BrandPrimary
                )
            ) {
                Text(confirmText, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = getOnboardingTextSecondary(isDark)
                )
            ) {
                Text(dismissText)
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}