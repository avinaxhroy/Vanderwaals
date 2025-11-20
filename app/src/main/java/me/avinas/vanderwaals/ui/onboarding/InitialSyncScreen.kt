package me.avinas.vanderwaals.ui.onboarding

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

/**
 * Initial Sync Screen - First screen shown to new users.
 * 
 * Automatically downloads wallpaper catalog on first launch:
 * - Shows animated progress indicator
 * - Downloads ~3670 wallpapers metadata (~5MB)
 * - Takes 30-60 seconds depending on network speed
 * - Automatic navigation on success
 * - Retry button on error
 * 
 * **Why This Screen Exists:**
 * Personalization mode requires wallpaper catalog to find similar matches.
 * Without pre-synced catalog, users would see "No wallpapers" errors.
 * 
 * **UX Flow:**
 * 1. User installs app and opens it
 * 2. This screen appears automatically
 * 3. Downloads catalog in background
 * 4. Shows progress and wallpaper count
 * 5. Auto-navigates to ModeSelection when complete
 * 
 * **Error Handling:**
 * - Network errors: Shows retry button with helpful message
 * - Parse errors: Shows error with instructions to check connection
 * - Timeout: Auto-retries up to 3 times
 * 
 * @param onSyncComplete Callback when sync finishes successfully
 * @param viewModel ViewModel managing sync state
 */
@Composable
fun InitialSyncScreen(
    onSyncComplete: () -> Unit,
    viewModel: InitialSyncViewModel = hiltViewModel()
) {
    val syncState by viewModel.syncState.collectAsState()
    val wallpaperCount by viewModel.wallpaperCount.collectAsState()
    
    // Auto-start sync on first composition
    LaunchedEffect(Unit) {
        viewModel.startSync()
    }
    
    // Auto-navigate on success
    LaunchedEffect(syncState) {
        if (syncState is SyncState.Success) {
            onSyncComplete()
        }
    }
    
    // Animated cloud download icon
    val infiniteTransition = rememberInfiniteTransition(label = "cloud_animation")
    val cloudOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cloud_offset"
    )
    
    Scaffold { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .systemBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                when (val state = syncState) {
                    is SyncState.Loading -> {
                        // Animated cloud icon
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier
                                .size(120.dp)
                                .offset(y = cloudOffset.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        
                        Text(
                            text = "Downloading Wallpaper Catalog",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        
                        // Progress indicator
                        if (state.progress != null) {
                            LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                            
                            Text(
                                text = "${(state.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            CircularProgressIndicator()
                        }
                        
                        if (wallpaperCount > 0) {
                            Text(
                                text = "$wallpaperCount wallpapers downloaded",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    
                    is SyncState.Error -> {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        
                        Text(
                            text = "Sync Failed",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.error
                        )
                        
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Button(
                            onClick = { viewModel.startSync() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Text(
                                text = "Retry",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "Make sure you have a stable internet connection",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                    
                    is SyncState.Success -> {
                        // Brief success state before auto-navigation
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        
                        Text(
                            text = "Catalog Downloaded!",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Text(
                            text = "${state.count} wallpapers ready",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        
                        CircularProgressIndicator()
                    }
                    
                    is SyncState.Idle -> {
                        // Should never be shown - sync starts automatically
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}
