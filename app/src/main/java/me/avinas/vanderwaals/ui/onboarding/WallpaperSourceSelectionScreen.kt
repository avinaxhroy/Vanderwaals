package me.avinas.vanderwaals.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import me.avinas.vanderwaals.ui.theme.LiquidGlassBackground
import me.avinas.vanderwaals.ui.theme.components.*

@Composable
fun WallpaperSourceSelectionScreen(
    onContinue: () -> Unit,
    viewModel: WallpaperSourceSelectionViewModel = hiltViewModel()
) {
    val communityEnabled by viewModel.communityEnabled.collectAsState()
    val bingEnabled by viewModel.bingEnabled.collectAsState()
    val bingManifestType by viewModel.bingManifestType.collectAsState()
    val isDark = isSystemInDarkTheme()
    
    // Save preferences when leaving
    val handleContinue = {
        viewModel.savePreferences { onContinue() }
    }

    LiquidGlassBackground {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = Color.Transparent
        ) { paddingValues ->
            Box(
                modifier = Modifier.fillMaxSize()
            ) {


            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                     OnboardingTopAppBar(
                        onBack = { },
                        showBack = false 
                    )
                
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Content Sources",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else Color(0xFF111827)
                    )
                    
                    Text(
                        text = "Where should we find your wallpapers?",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF4B5563),
                        modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
                    )
                    
                    // Sources List
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        contentPadding = PaddingValues(bottom = 100.dp)
                    ) {
                         // GITHUB SOURCE
                        item {
                            SourceOptionCard(
                                title = "Community Collection",
                                subtitle = "Curated high-quality wallpapers from the open-source community.",
                                icon = Icons.Default.Public,
                                isEnabled = communityEnabled,
                                onToggle = { viewModel.toggleCommunity(it) },
                                isDark = isDark
                            )
                        }
                        
                        // BING SOURCE
                        item {
                            SourceOptionCard(
                                title = "Bing Daily Wallpapers",
                                subtitle = "Stunning photography from around the world, updated daily.",
                                icon = Icons.Default.Image,
                                isEnabled = bingEnabled,
                                onToggle = { viewModel.toggleBing(it) },
                                isDark = isDark
                            ) {
                                // Bing Options
                                if (bingEnabled) {
                                    ModernDivider(
                                        modifier = Modifier.padding(vertical = 16.dp),
                                        color = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f)
                                    )
                                    
                                    Text(
                                        text = "COLLECTION TYPE",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.5f),
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    )
                                    
                                    // Use Segmented Control style or Radio rows
                                    // Since ViewModel uses String "lite"/"full"
                                    val options = listOf("Recent Hits (Lite)", "Global Archive (Full)")
                                    val selectedIndex = if (bingManifestType == "lite") 0 else 1
                                    
                                    SegmentedControl(
                                        items = options,
                                        selectedIndex = selectedIndex,
                                        onItemSelected = { index ->
                                            viewModel.setBingManifestType(if (index == 0) "lite" else "full")
                                        },
                                        isDark = isDark
                                    )
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    Text(
                                        text = if (bingManifestType == "lite") 
                                            "Quick download. Best for getting started." 
                                        else 
                                            "Thousands of images. Requires larger download.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isDark) Color.White.copy(alpha = 0.5f) else Color(0xFF6B7280),
                                        modifier = Modifier.padding(start = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Bottom Bar
                GlassSheet(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val anyEnabled = communityEnabled || bingEnabled
                    
                    if (!anyEnabled) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                        ) {
                             Icon(
                                 imageVector = Icons.Default.Warning,
                                 contentDescription = null,
                                 tint = if (isDark) me.avinas.vanderwaals.ui.theme.ErrorColorDark else Color(0xFFEF4444),
                                 modifier = Modifier.size(16.dp)
                             )
                             Spacer(modifier = Modifier.width(8.dp))
                             Text(
                                 text = "Please select at least one source",
                                 style = MaterialTheme.typography.bodySmall,
                                 color = if (isDark) me.avinas.vanderwaals.ui.theme.ErrorColorDark else Color(0xFFEF4444)
                             )
                        }
                    }
                    
                    Button(
                        onClick = handleContinue,
                        enabled = anyEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDark) me.avinas.vanderwaals.ui.theme.InfoColorDark else me.avinas.vanderwaals.ui.theme.LightPrimary,
                            contentColor = Color.White,
                            disabledContainerColor = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f),
                            disabledContentColor = if (isDark) Color.White.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.3f)
                        )
                    ) {
                        Text(
                            text = "Continue",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null
                        )
                    }
                }
            }
        }

    }
    }
}
@Composable
fun SourceOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    isDark: Boolean,
    content: @Composable (ColumnScope.() -> Unit)? = null
) {
    LiquidGlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isEnabled) (if (isDark) me.avinas.vanderwaals.ui.theme.InfoColorDark else me.avinas.vanderwaals.ui.theme.LightPrimary)
                            else (if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isEnabled) Color.White else (if (isDark) Color.White.copy(alpha = 0.5f) else Color(0xFF6B7280))
                    )
                }
                
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else Color(0xFF111827)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDark) Color.White.copy(alpha = 0.6f) else Color(0xFF4B5563),
                        lineHeight = 20.sp
                    )
                }
            }
            
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = if (isDark) me.avinas.vanderwaals.ui.theme.InfoColorDark else me.avinas.vanderwaals.ui.theme.LightPrimary,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = if (isDark) Color.Gray.copy(alpha = 0.5f) else Color(0xFFE5E7EB)
                ),
                modifier = Modifier.padding(start = 12.dp)
            )
        }
        
        content?.let { 
            Column(modifier = Modifier.fillMaxWidth()) {
                it()
            }
        }
    }
}
