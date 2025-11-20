package me.avinas.vanderwaals.feature.wallpaper.presentation

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.animation.AccelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import me.avinas.vanderwaals.ui.theme.VanderwaalsTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import me.avinas.vanderwaals.data.dao.UserPreferenceDao
import me.avinas.vanderwaals.domain.usecase.UserEngagementTracker
import me.avinas.vanderwaals.ui.InitializationViewModel
import me.avinas.vanderwaals.ui.VanderwaalsNavGraph
import me.avinas.vanderwaals.ui.components.LoadingScreen
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var userPreferenceDao: UserPreferenceDao
    
    @Inject
    lateinit var engagementTracker: UserEngagementTracker
    
    private companion object {
        const val TAG = "MainActivity"
    }

    private var showPermissionDeniedDialog by mutableStateOf(false)
    private var showPermissionPermanentlyDeniedDialog by mutableStateOf(false)
    private var showPermissionExplanationDialog by mutableStateOf(false)
    private var showAlarmPermissionDialog by mutableStateOf(false)
    
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d(TAG, "Storage permission granted")
            // After storage permission, check alarm permission
            checkAlarmPermission()
        } else {
            if (shouldShowRequestPermissionRationale(getStoragePermission())) {
                showPermissionDeniedDialog = true
            } else {
                showPermissionPermanentlyDeniedDialog = true
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        
        // Hide action bar / title completely
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
            
            LaunchedEffect(Unit) {
                // Record app launch for engagement tracking
                engagementTracker.recordAppLaunch()
                
                onboardingComplete = userPreferenceDao.exists()
                keepSplashScreen = false
                Log.d(TAG, "Onboarding complete: $onboardingComplete")
            }
            
            LaunchedEffect(Unit) {
                requestPermissionsIfNeeded()
            }
            
            VanderwaalsTheme(
                dynamicColor = false // Use brand colors, not dynamic
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .fillMaxSize(),
                        // .systemBarsPadding(), // REMOVED: Allow content to extend behind system bars for edge-to-edge
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Show loading screen while app is initializing
                    if (!isInitialized) {
                        LoadingScreen(
                            message = loadingMessage,
                            subMessage = loadingSubMessage,
                            progress = loadingProgress
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
                    
                    if (showPermissionDeniedDialog) {
                        PermissionRationaleDialog(
                            onDismiss = { showPermissionDeniedDialog = false },
                            onRetry = {
                                showPermissionDeniedDialog = false
                                storagePermissionLauncher.launch(getStoragePermission())
                            }
                        )
                    }
                    
                    if (showPermissionPermanentlyDeniedDialog) {
                        PermissionPermanentlyDeniedDialog(
                            onDismiss = { showPermissionPermanentlyDeniedDialog = false },
                            onOpenSettings = {
                                showPermissionPermanentlyDeniedDialog = false
                                openAppSettings()
                            }
                        )
                    }
                    
                    if (showPermissionExplanationDialog) {
                        PermissionExplanationDialog(
                            onDismiss = { showPermissionExplanationDialog = false },
                            onContinue = {
                                showPermissionExplanationDialog = false
                                storagePermissionLauncher.launch(getStoragePermission())
                            }
                        )
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
                }
            }
        }
    }
    
    private fun requestPermissionsIfNeeded() {
        val permission = getStoragePermission()
        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                Log.d(TAG, "Storage permission already granted")
                // Check alarm permission after storage
                checkAlarmPermission()
            }
            shouldShowRequestPermissionRationale(permission) -> {
                // User has previously denied, show rationale
                showPermissionExplanationDialog = true
            }
            else -> {
                // First time asking, show beautiful explanation first
                showPermissionExplanationDialog = true
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
    
    private fun getStoragePermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }
    
    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }
}

@Composable
private fun PermissionExplanationDialog(
    onDismiss: () -> Unit,
    onContinue: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Default.PhotoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = androidx.compose.ui.Modifier.size(48.dp)
            )
        },
        title = { 
            Text(
                "Access Your Photos",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            ) 
        },
        text = { 
            androidx.compose.foundation.layout.Column(
                horizontalAlignment = androidx.compose.ui.Alignment.Start,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Vanderwaals needs permission to access your photos to:",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
                
                PermissionReasonItem(
                    icon = androidx.compose.material.icons.Icons.Default.Wallpaper,
                    text = "Display beautiful wallpapers from curated collections"
                )
                
                PermissionReasonItem(
                    icon = androidx.compose.material.icons.Icons.Default.Download,
                    text = "Download and cache wallpapers for offline access"
                )
                
                PermissionReasonItem(
                    icon = androidx.compose.material.icons.Icons.Default.Sync,
                    text = "Automatically change your wallpapers at your chosen frequency"
                )
                
                androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(8.dp))
                
                Text(
                    "Your privacy is important. Vanderwaals only accesses images needed for wallpapers and never shares your data.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(
                onClick = onContinue,
                modifier = androidx.compose.ui.Modifier.fillMaxWidth(0.5f)
            ) {
                Text("Continue")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not Now")
            }
        }
    )
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
private fun PermissionRationaleDialog(
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Storage Permission Required") },
        text = { 
            Text("Vanderwaals needs access to your photos to load wallpapers. This permission is required for the app to function properly.")
        },
        confirmButton = {
            TextButton(onClick = onRetry) {
                Text("Grant Permission")
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
private fun PermissionPermanentlyDeniedDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Permission Required") },
        text = { 
            Text("Storage permission is required for Vanderwaals to function. Please grant the permission in app settings.")
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text("Open Settings")
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = FontStyle.Italic
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(0.5f)
            ) {
                Text("Grant Permission")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Skip for Now")
            }
        }
    )
}
