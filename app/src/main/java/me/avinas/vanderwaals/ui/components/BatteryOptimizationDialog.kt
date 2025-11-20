package me.avinas.vanderwaals.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.avinas.vanderwaals.core.BatteryOptimizationHelper

/**
 * Dialog prompting user to disable battery optimization for reliable background work.
 * 
 * **When to Show:**
 * - First time user enables auto-change feature
 * - After app restart if not yet exempt
 * - When user manually triggers from settings
 * 
 * **Dialog Flow:**
 * 1. Show rationale explaining why exemption is needed
 * 2. "Allow" button → Opens system battery settings
 * 3. "Not Now" button → Dismisses, will ask again after cooldown
 * 4. "Don't Ask Again" button → Permanently dismisses
 * 
 * **Design:**
 * - Material 3 AlertDialog
 * - Battery alert icon for visual clarity
 * - Clear, concise explanation
 * - Non-blocking (user can decline)
 * 
 * @param onAllow Callback when user taps "Allow" (opens system settings)
 * @param onDecline Callback when user taps "Not Now" (dismiss with cooldown)
 * @param onNeverAskAgain Callback when user taps "Don't Ask Again" (permanent dismiss)
 * @param onDismiss Callback when dialog is dismissed (back button, outside tap)
 */
@Composable
fun BatteryOptimizationDialog(
    onAllow: () -> Unit,
    onDecline: () -> Unit,
    onNeverAskAgain: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.BatteryAlert,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Battery Optimization",
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
                    text = "To ensure wallpapers change reliably on schedule, Vanderwaals needs to be excluded from battery optimization.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Start
                )
                
                // What happens without exemption
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
                            text = "Without permission:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "• Auto-change may not work after restart",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "• Scheduled changes may be delayed",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "• Background sync may fail",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                
                // What happens with exemption
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "With permission:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "✓ Wallpapers change exactly on schedule",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "✓ Works reliably after device reboot",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "✓ Minimal battery impact",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                
                // Manufacturer-specific guidance (if applicable)
                if (BatteryOptimizationHelper.needsAutoStartPermission(context)) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Additional Step Required:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = BatteryOptimizationHelper.getAutoStartGuidance(),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                
                Text(
                    text = "You can revoke this permission anytime from Android Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onAllow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Allow")
            }
        },
        dismissButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDecline,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Not Now")
                }
                
                TextButton(
                    onClick = onNeverAskAgain,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Don't Ask Again",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    )
}

/**
 * Simpler battery optimization prompt card for in-context display.
 * 
 * **Use Cases:**
 * - Show in settings screen below auto-change toggle
 * - Display in onboarding after user selects auto mode
 * - Show in main screen if exemption not granted
 * 
 * **Design:**
 * - Compact card with icon and message
 * - Single "Grant Permission" button
 * - Dismissible (X button in corner)
 * 
 * @param onGrantPermission Callback when user taps "Grant Permission"
 * @param onDismiss Callback when user dismisses the card
 */
@Composable
fun BatteryOptimizationPromptCard(
    onGrantPermission: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.BatteryAlert,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(32.dp)
                )
                
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Battery Optimization Detected",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = "For reliable auto-change, please disable battery optimization for this app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onGrantPermission,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Grant Permission")
                        }
                        
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Later")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Info card displaying current battery optimization status.
 * 
 * **Use Cases:**
 * - Show in settings screen under "Battery & Performance" section
 * - Display in diagnostics/about screen
 * 
 * **States:**
 * - Exempt: Green checkmark, "✓ Background work unrestricted"
 * - Not Exempt: Yellow warning, "Battery optimization active"
 * 
 * @param modifier Modifier for the card
 */
@Composable
fun BatteryOptimizationStatusCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isExempt = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
    val needsAutoStart = BatteryOptimizationHelper.needsAutoStartPermission(context)
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isExempt) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            }
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isExempt) {
                        Icons.Default.BatteryAlert // You can use a checkmark icon instead
                    } else {
                        Icons.Default.BatteryAlert
                    },
                    contentDescription = null,
                    tint = if (isExempt) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.size(24.dp)
                )
                
                Column {
                    Text(
                        text = if (isExempt) "Background Work Unrestricted" else "Battery Optimization Active",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = if (isExempt) {
                            "Auto-change will work reliably"
                        } else {
                            "Auto-change may not work after restart"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            if (!isExempt) {
                Button(
                    onClick = {
                        BatteryOptimizationHelper.requestBatteryOptimizationExemption(context)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open Battery Settings")
                }
            }
            
            // Show manufacturer-specific guidance
            if (needsAutoStart) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                Text(
                    text = "Additional Step Required",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
                
                Text(
                    text = BatteryOptimizationHelper.getAutoStartGuidance(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
