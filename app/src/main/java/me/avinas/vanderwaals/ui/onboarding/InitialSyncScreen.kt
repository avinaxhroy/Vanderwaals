package me.avinas.vanderwaals.ui.onboarding

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
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
        ) {
            // Premium Background
            val isDark = isSystemInDarkTheme()
            me.avinas.vanderwaals.ui.theme.components.PremiumBackground(
                modifier = Modifier.fillMaxSize(),
                isDark = isDark
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .systemBarsPadding()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                me.avinas.vanderwaals.ui.theme.components.GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentPadding = PaddingValues(32.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        when (val state = syncState) {
                            is SyncState.Loading -> {
                                // Animated cloud icon
                                Box(
                                    modifier = Modifier
                                        .size(120.dp)
                                        .offset(y = cloudOffset.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        tint = if (isDark) Color.White else MaterialTheme.colorScheme.primary 
                                    )
                                }
                                
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Setting Up Library",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        color = if (isDark) Color.White else Color(0xFF111827)
                                    )
                                    
                                    Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF4B5563),
                                        textAlign = TextAlign.Center
                                    )
                                }
                                
                                // Progress indicator
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    if (state.progress != null) {
                                        LinearProgressIndicator(
                                            progress = { state.progress },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp)),
                                            color = if (isDark) me.avinas.vanderwaals.ui.theme.InfoColorDark else MaterialTheme.colorScheme.primary,
                                            trackColor = if (isDark) Color.White.copy(alpha=0.1f) else MaterialTheme.colorScheme.surfaceVariant,
                                        )
                                        
                                        Text(
                                            text = "${(state.progress * 100).toInt()}%",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isDark) Color.White.copy(alpha = 0.9f) else Color(0xFF111827)
                                        )
                                    } else {
                                        CircularProgressIndicator(
                                            color = if (isDark) me.avinas.vanderwaals.ui.theme.InfoColorDark else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                
                                if (wallpaperCount > 0) {
                                    Text(
                                        text = "$wallpaperCount wallpapers found",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (isDark) me.avinas.vanderwaals.ui.theme.InfoColorDark else MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .background(
                                                color = if (isDark) me.avinas.vanderwaals.ui.theme.InfoColorDark.copy(alpha = 0.1f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
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
                                
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
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
                                        color = if (isDark) Color.White.copy(alpha = 0.7f) else Color.Unspecified
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                me.avinas.vanderwaals.ui.theme.components.GradientButton(
                                    text = "Try Again",
                                    onClick = { viewModel.startSync() },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                
                                Text(
                                    text = "Please check your internet connection",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isDark) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                            
                            is SyncState.Success -> {
                                // Brief success state before auto-navigation
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(80.dp),
                                    tint = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                                
                                Text(
                                    text = "Library Ready!",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    color = if (isDark) me.avinas.vanderwaals.ui.theme.InfoColorDark else MaterialTheme.colorScheme.primary
                                )
                                
                                Text(
                                    text = "${state.count} wallpapers downloaded",
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center,
                                    color = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF4B5563)
                                )
                                
                                CircularProgressIndicator(
                                    color = if (isDark) me.avinas.vanderwaals.ui.theme.InfoColorDark else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            
                            is SyncState.Idle -> {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }
    }
}
