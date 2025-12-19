package me.avinas.vanderwaals.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Dialog shown to users upgrading from older app versions to prompt manifest re-sync.
 * 
 * **When to Show:**
 * - User updates from v3.8.x or earlier to v4.0.0+
 * - Previous manifest version was v1 (unquantized) or old format
 * - Manifest format has changed significantly
 * 
 * **Benefits Explained:**
 * - Smaller data usage (60+ MB → ~6 MB with quantized embeddings)
 * - Faster sync times
 * - Better wallpaper recommendations with updated catalog
 * 
 * **Dialog Flow:**
 * 1. Show rationale explaining why re-sync is beneficial
 * 2. "Update Now" → Triggers manifest sync
 * 3. "Later" → Dismisses, user can sync from settings
 * 
 * @param onUpdateNow Callback when user taps "Update Now" (starts manifest sync)
 * @param onLater Callback when user taps "Later" (dismiss, remind later)
 * @param onDismiss Callback when dialog is dismissed
 * @param isLoading Whether sync is currently in progress
 * @param progress Progress of sync (0.0-1.0), null if indeterminate
 * @param progressMessage Current sync status message
 */
@Composable
fun ManifestMigrationDialog(
    onUpdateNow: () -> Unit,
    onLater: () -> Unit,
    onDismiss: () -> Unit,
    isLoading: Boolean = false,
    progress: Float? = null,
    progressMessage: String? = null
) {
    AlertDialog(
        onDismissRequest = if (isLoading) { {} } else onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Update,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Wallpaper Catalog Update",
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
                if (isLoading) {
                    // Show progress during sync
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (progress != null) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        
                        Text(
                            text = progressMessage ?: "Updating wallpaper catalog...",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    // Show explanation
                    Text(
                        text = "We've optimized the wallpaper catalog for a better experience. Please update to enjoy:",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Start
                    )
                    
                    // Benefits
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "What's New:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            BenefitItem("✓ 90% smaller download size")
                            BenefitItem("✓ Faster sync times")
                            BenefitItem("✓ 6,000+ curated wallpapers")
                            BenefitItem("✓ Improved recommendations")
                        }
                    }
                    
                    // Data usage info
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
                                text = "Data Usage:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "~6 MB download (Wi-Fi recommended)",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    
                    Text(
                        text = "You can also update later from Settings → Sync Catalog.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            if (!isLoading) {
                Button(
                    onClick = onUpdateNow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Update Now")
                }
            }
        },
        dismissButton = {
            if (!isLoading) {
                TextButton(
                    onClick = onLater,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Later")
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    )
}

@Composable
private fun BenefitItem(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall
    )
}
