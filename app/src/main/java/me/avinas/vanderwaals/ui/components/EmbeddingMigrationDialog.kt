package me.avinas.vanderwaals.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Dialog shown to users with legacy 576D embeddings explaining why re-personalization is needed.
 * 
 * **When to Show:**
 * - User upgrades from v4.x (MobileNetV3 576D) to v5.0.0+ (MobileNetV4 1280D)
 * - User has existing preferences with legacy embedding dimension
 * - User has not dismissed this dialog before
 * 
 * **What It Explains:**
 * - The AI model has been upgraded for better recommendations
 * - Existing preferences need to be reset for compatibility
 * - User's liked/disliked wallpaper history is preserved
 * - Category, color, and composition preferences are preserved
 * 
 * **Dialog Flow:**
 * 1. Show rationale explaining the upgrade benefits
 * 2. "Re-personalize Now" → Goes to onboarding flow
 * 3. "Continue with Auto Mode" → Resets preferences, starts with auto mode
 * 4. "Remind Me Later" → Dismisses, shows again next session
 * 
 * @param onRePersonalize Callback when user chooses to re-personalize (goes to onboarding)
 * @param onAutoMode Callback when user chooses auto mode (reset preferences, skip onboarding)
 * @param onRemindLater Callback when user taps "Remind Me Later"
 * @param onDontShowAgain Callback when user taps "Don't Show Again" (permanent dismiss)
 * @param onDismiss Callback when dialog is dismissed
 * @param totalLikes Number of liked wallpapers (shown to reassure user history is preserved)
 */
@Composable
fun EmbeddingMigrationDialog(
    onRePersonalize: () -> Unit,
    onAutoMode: () -> Unit,
    onRemindLater: () -> Unit,
    onDontShowAgain: () -> Unit = {},
    onDismiss: () -> Unit,
    totalLikes: Int = 0
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Psychology,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "AI Model Upgraded 🚀",
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
                // Main explanation
                Text(
                    text = "We've upgraded our AI model for significantly better wallpaper recommendations. To take advantage of this improvement, we need to re-learn your preferences.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Start
                )
                
                // What's improved
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
                            text = "What's Improved:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        BenefitItem("✓ 2x more accurate recommendations")
                        BenefitItem("✓ Better understanding of aesthetics")
                        BenefitItem("✓ Faster preference learning")
                    }
                }
                
                // What's preserved
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
                            text = "What's Preserved:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        if (totalLikes > 0) {
                            BenefitItem("✓ Your $totalLikes liked wallpapers")
                        }
                        BenefitItem("✓ Category preferences")
                        BenefitItem("✓ Color preferences")
                        BenefitItem("✓ Wallpaper history")
                    }
                }
                
                Text(
                    text = "Re-personalizing takes just a minute and gives you the best experience!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Primary action: Re-personalize
                Button(
                    onClick = onRePersonalize,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Re-personalize Now")
                }
                
                // Secondary action: Auto mode
                OutlinedButton(
                    onClick = onAutoMode,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Use Auto Mode")
                }
            }
        },
        dismissButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(
                    onClick = onRemindLater,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Remind Me Later")
                }
                TextButton(
                    onClick = onDontShowAgain,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Don't Show Again",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
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
