package me.avinas.vanderwaals.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.avinas.vanderwaals.core.BrandSettingsIntents
import me.avinas.vanderwaals.core.LiveWallpaperDetector

/**
 * Dialog shown when a live wallpaper service is detected that blocks wallpaper changes.
 * 
 * **Purpose:**
 * - Inform user that a live wallpaper (Glance, Dynamic Wallpaper) is blocking changes
 * - Provide three action options: direct settings, instructions, or dismiss
 * - Guide user through disabling the blocking service
 * 
 * **Design Pattern:**
 * - Similar to BatteryOptimizationDialog for consistency
 * - Material 3 AlertDialog with custom button layout
 * - Clear explanation with service name
 * - Non-blocking (user can dismiss)
 * 
 * **User Flow:**
 * 1. Dialog appears when live wallpaper is detected
 * 2. User chooses action:
 *    - "Take me there" → Opens brand-specific settings
 *    - "Show me how" → Opens instruction dialog
 *    - "Maybe later" → Dismisses (with cooldown)
 * 
 * @param serviceName Display name of the blocking service (e.g., "Glance", "Dynamic Wallpaper")
 * @param packageName Package name of the live wallpaper service
 * @param onOpenSettings Callback when user wants to open settings directly
 * @param onShowInstructions Callback when user wants step-by-step instructions
 * @param onDismiss Callback when user dismisses the dialog
 */
@Composable
fun LiveWallpaperBlockedDialog(
    serviceName: String,
    packageName: String?,
    onOpenSettings: () -> Unit,
    onShowInstructions: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var settingsOpened by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = "Live Wallpaper Detected",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "We detected \"$serviceName\" running on your device. This service prevents Vanderwaals from changing your wallpaper automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Start
                )
                
                Text(
                    text = "Would you like to disable it so auto-change can work?",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Start
                )
                
                // Info card explaining the situation
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Why this happens:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "• $serviceName is a live wallpaper service",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "• It takes priority over static wallpapers",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "• Android prevents apps from overriding it",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                
                if (settingsOpened) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "✓ Settings opened",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "After disabling the service, return to this app and try changing wallpaper again.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Primary action: Open settings directly
                Button(
                    onClick = {
                        val success = BrandSettingsIntents.openLiveWallpaperSettings(context, packageName)
                        settingsOpened = success
                        if (success) {
                            onOpenSettings()
                        } else {
                            // If settings can't be opened, show instructions instead
                            onShowInstructions()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Wallpaper,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Take me there")
                }
                
                // Secondary action: Show instructions
                OutlinedButton(
                    onClick = onShowInstructions,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Show me how")
                }
                
                // Tertiary action: Dismiss
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Maybe later",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    )
}

/**
 * Dialog displaying step-by-step instructions for disabling live wallpaper.
 * 
 * **Purpose:**
 * - Show brand-specific instructions when direct settings link fails
 * - Provide manual guidance for users who prefer written steps
 * - Fallback when automatic navigation doesn't work
 * 
 * **Content:**
 * - Brand-specific step-by-step instructions
 * - Formatted for readability (numbered steps, bold headers)
 * - Scrollable for longer instruction sets
 * - Retry button to attempt settings navigation again
 * 
 * @param onRetrySettings Callback when user wants to try opening settings again
 * @param onDismiss Callback when user closes the instructions
 */
@Composable
fun LiveWallpaperInstructionsDialog(
    onRetrySettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val instructions = BrandSettingsIntents.getBrandSpecificInstructions()
    val manufacturer = BrandSettingsIntents.getDeviceManufacturer()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Wallpaper,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "How to Disable Live Wallpaper",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Device info
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Your device:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = manufacturer.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                
                // Instructions text
                Text(
                    text = instructions,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight.times(1.4f)
                )
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                // Additional tips
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "💡 Tips:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "• After disabling, you may need to restart your device",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "• Look for settings containing 'Glance', 'Lock screen magazine', or 'Dynamic wallpaper'",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "• If you can't find it, try uninstalling the service app from Apps settings",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val packageName = LiveWallpaperDetector.getLiveWallpaperPackageName(context)
                        BrandSettingsIntents.openLiveWallpaperSettings(context, packageName)
                        onRetrySettings()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Try opening settings again")
                }
                
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Got it")
                }
            }
        }
    )
}

/**
 * Compact info card showing live wallpaper status.
 * 
 * **Use Cases:**
 * - Display in settings screen
 * - Show in main screen when live wallpaper is detected
 * - Provide quick access to disable action
 * 
 * **States:**
 * - Live wallpaper detected: Warning card with action button
 * - No live wallpaper: Success card (optional, or hide completely)
 * 
 * @param onOpenSettings Callback when user wants to disable live wallpaper
 * @param modifier Modifier for the card
 */
@Composable
fun LiveWallpaperStatusCard(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isLiveWallpaperActive = remember { LiveWallpaperDetector.isLiveWallpaperActive(context) }
    val serviceName = remember { LiveWallpaperDetector.getLiveWallpaperDisplayName(context) }
    
    if (!isLiveWallpaperActive) {
        // Don't show card if no live wallpaper is active
        return
    }
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
                
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Live Wallpaper Detected",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = "\"$serviceName\" is preventing auto-change from working. Tap below to disable it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Button(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Disable Live Wallpaper")
            }
        }
    }
}
