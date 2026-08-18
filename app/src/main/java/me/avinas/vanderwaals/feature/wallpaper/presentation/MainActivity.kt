package me.avinas.vanderwaals.feature.wallpaper.presentation

import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AccelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import me.avinas.vanderwaals.ui.theme.VanderwaalsTheme
import me.avinas.vanderwaals.ui.theme.LocalNavigationBarPadding
import androidx.compose.foundation.layout.navigationBars
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import me.avinas.vanderwaals.data.dao.UserPreferenceDao
import me.avinas.vanderwaals.domain.usecase.UserEngagementTracker
import me.avinas.vanderwaals.ui.InitializationViewModel
import me.avinas.vanderwaals.ui.VanderwaalsNavGraph
import me.avinas.vanderwaals.ui.components.LoadingScreen
import me.avinas.vanderwaals.ui.components.ManifestMigrationDialog
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var userPreferenceDao: UserPreferenceDao
    
    @Inject
    lateinit var engagementTracker: UserEngagementTracker
    
    @Inject
    lateinit var settingsDataStore: me.avinas.vanderwaals.data.datastore.SettingsDataStore
    
    private companion object {
        const val TAG = "MainActivity"
    }

    private var showAlarmPermissionDialog by mutableStateOf(false)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // app is dark-mode-only, so force dark system bars
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        
        actionBar?.hide()
        
        val splashScreen = installSplashScreen()
        var keepSplashScreen = true
        
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
            splashScreen.setOnExitAnimationListener { splashScreenViewProvider ->
                val fadeOut = ObjectAnimator.ofFloat(splashScreenViewProvider.view, View.ALPHA, 1f, 0f)
                fadeOut.interpolator = AccelerateInterpolator()
                fadeOut.duration = 300L
                fadeOut.doOnEnd { splashScreenViewProvider.remove() }
                fadeOut.start()
            }
        }
        splashScreen.setKeepOnScreenCondition { keepSplashScreen }
        
        setContent {
            val scope = rememberCoroutineScope()
            var onboardingComplete by remember { mutableStateOf<Boolean?>(null) }
            val initViewModel: InitializationViewModel = viewModel()
            val isInitialized by initViewModel.isInitialized.collectAsState()
            val loadingMessage by initViewModel.loadingMessage.collectAsState()
            val loadingSubMessage by initViewModel.loadingSubMessage.collectAsState()
            val loadingProgress by initViewModel.loadingProgress.collectAsState()
            val syncFailed by initViewModel.syncFailed.collectAsState()
            
            val showMigrationDialog by initViewModel.showMigrationDialog.collectAsState()
            val migrationInProgress by initViewModel.migrationInProgress.collectAsState()
            val migrationProgress by initViewModel.migrationProgress.collectAsState()
            val migrationMessage by initViewModel.migrationMessage.collectAsState()
            
            LaunchedEffect(Unit) {
                engagementTracker.recordAppLaunch()
                
                onboardingComplete = userPreferenceDao.exists()
                keepSplashScreen = false
                Log.d(TAG, "Onboarding complete: $onboardingComplete")
            }
            
            // prefs turning null after being set means a reset wiped them
            LaunchedEffect(Unit) {
                userPreferenceDao.get().collect { prefs ->
                    if (prefs == null && onboardingComplete == true) {
                        onboardingComplete = false
                    }
                }
            }
            
            LaunchedEffect(Unit) {
                checkAlarmPermission()
            }
            
            VanderwaalsTheme(
                darkTheme = true,
                dynamicColor = false // brand colors, not dynamic
            ) {
                me.avinas.vanderwaals.ui.theme.glass.LiquidGlassProvider {
                    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    CompositionLocalProvider(LocalNavigationBarPadding provides navBarPadding) {
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .fillMaxSize(),
                                // .systemBarsPadding(), // removed: content extends behind system bars for edge-to-edge
                            color = MaterialTheme.colorScheme.background
                        ) {
                        if (!isInitialized) {
                            LoadingScreen(
                                message = loadingMessage,
                                subMessage = loadingSubMessage,
                                progress = loadingProgress,
                                isError = syncFailed,
                                onRetry = { initViewModel.retryInitialization() }
                            )
                        } else {
                            when (onboardingComplete) {
                                null -> {
                                }
                                else -> {
                                    VanderwaalsNavGraph(onboardingComplete = onboardingComplete!!)
                                }
                            }
                        }
                        
                        if (showAlarmPermissionDialog) {
                            AlarmPermissionExplanationDialog(
                                onDismiss = { showAlarmPermissionDialog = false },
                                onContinue = {
                                    showAlarmPermissionDialog = false
                                    openAlarmPermissionSettings()
                                }
                            )
                        }
                        
                        // migration dialog for users upgrading from older versions
                        if (showMigrationDialog) {
                            ManifestMigrationDialog(
                                onUpdateNow = { initViewModel.startMigration() },
                                onLater = { initViewModel.dismissMigrationDialog() },
                                onDismiss = { initViewModel.dismissMigrationDialog() },
                                isLoading = migrationInProgress,
                                progress = migrationProgress,
                                progressMessage = migrationMessage
                            )
                        }
                    }
                }
            }
            }
        }
    }
    
    private fun checkAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                Log.d(TAG, "Alarm permission not granted, showing dialog")
                showAlarmPermissionDialog = true
            } else {
                Log.d(TAG, "Alarm permission already granted")
            }
        }
    }
    
    private fun openAlarmPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open alarm permission settings", e)
            }
        }
    }
}

@Composable
private fun PermissionReasonItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = androidx.compose.ui.Modifier.size(24.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun AlarmPermissionExplanationDialog(
    onDismiss: () -> Unit,
    onContinue: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Alarm,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
        },
        title = { 
            Text(
                "Schedule Exact Alarms",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            ) 
        },
        text = { 
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "To automatically change your wallpaper at precise times, Vanderwaals needs permission to schedule exact alarms:",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                
                PermissionReasonItem(
                    icon = Icons.Default.Schedule,
                    text = "Change wallpapers exactly every 15 minutes, hourly, or daily"
                )
                
                PermissionReasonItem(
                    icon = Icons.Default.Wallpaper,
                    text = "Update your wallpaper at the specific time you choose"
                )
                
                PermissionReasonItem(
                    icon = Icons.Default.BatteryFull,
                    text = "Work reliably even when your device is in battery-saving mode"
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    "Without this permission, wallpaper changes may be delayed or skipped. You can grant this permission now or enable it later in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onContinue,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
            ) {
                Text("Grant Permission", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
            ) {
                Text("Skip for Now", fontWeight = FontWeight.Medium)
            }
        }
    )
}
