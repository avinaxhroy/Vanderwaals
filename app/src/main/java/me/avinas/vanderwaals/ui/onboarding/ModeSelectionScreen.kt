package me.avinas.vanderwaals.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import me.avinas.vanderwaals.ui.theme.components.*

@Composable
fun ModeSelectionScreen(
    onModeSelected: (OnboardingMode) -> Unit,
    viewModel: ModeSelectionViewModel = hiltViewModel()
) {
    val selectedMode by viewModel.selectedMode.collectAsState()
    
    // Auto-navigate if mode is already selected and confirmed (optional logic, but here we just show selection)
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Premium Background
            val isDark = isSystemInDarkTheme()
            PremiumBackground(
                modifier = Modifier.fillMaxSize(),
                isDark = isDark
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Top Bar with consistent spacing 
                OnboardingTopAppBar(
                    onBack = {},
                    showBack = false
                )
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    // Logo and Welcome
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        // App Logo
                        // App Logo
                        Box(
                            modifier = Modifier
                                .width(260.dp)
                                .height(80.dp)
                        ) {
                            // Tinted Glass Logo Container
                            me.avinas.vanderwaals.ui.theme.components.TintedGlassCard(
                                modifier = Modifier.matchParentSize(),
                                shape = RoundedCornerShape(44.dp),
                                contentPadding = PaddingValues(0.dp),
                                tintColor = if (isDark) me.avinas.vanderwaals.ui.theme.InfoColorDark.copy(alpha = 0.3f) 
                                          else me.avinas.vanderwaals.ui.theme.LightPrimary.copy(alpha = 0.3f)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = androidx.compose.ui.res.painterResource(id = me.avinas.vanderwaals.R.drawable.vanderwaals_logo),
                                        contentDescription = "Vanderwaals",
                                        modifier = Modifier
                                            .fillMaxWidth(0.85f)
                                            .padding(horizontal = 24.dp, vertical = 16.dp),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                        alpha = 1.0f
                                    )
                                }
                            }
                        }
                        
                        Text(
                            text = "Welcome to Vanderwaals",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = if (isDark) Color.White else Color(0xFF111827)
                        )
                        
                        Text(
                            text = "Choose how you want to experience your wallpapers.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF4B5563)
                        )
                    }
                    
                    // Options
                    Column(
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        ModeOption(
                            title = "Personalized Mode",
                            description = "Upload one favorite wallpaper or choose category and instantly get similar matches. The algorithm analyzes Deep Visual Features (70%), Color Palette (20%), and Category Affinity (10%) to find your perfect style.",
                            icon = Icons.Default.Tune,
                            selected = selectedMode == OnboardingMode.PERSONALIZE,
                            onClick = { 
                                viewModel.selectMode(OnboardingMode.PERSONALIZE) {}
                            },
                            isDark = isDark
                        )
                        
                        ModeOption(
                            title = "Auto Mode",
                            description = "Start fresh with algorithm-selected wallpapers from curated collections. The app learns your taste as you provide feedback.",
                            icon = Icons.Default.AutoAwesome,
                            selected = selectedMode == OnboardingMode.AUTO,
                            onClick = { 
                                viewModel.selectMode(OnboardingMode.AUTO) {}
                            },
                            isDark = isDark
                        )
                    }
                }
                
                // Bottom Button
                GlassSheet(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { selectedMode?.let { onModeSelected(it) } },
                        enabled = selectedMode != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDark) me.avinas.vanderwaals.ui.theme.InfoColorDark else me.avinas.vanderwaals.ui.theme.LightPrimary,
                            contentColor = Color.White
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

@Composable
private fun ModeOption(
    title: String,
    description: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    isDark: Boolean
) {
    val borderColor by animateColorAsState(
        targetValue = if (selected) (if (isDark) me.avinas.vanderwaals.ui.theme.InfoColorDark else me.avinas.vanderwaals.ui.theme.LightPrimary) 
                      else Color.Transparent,
        label = "border"
    )
    
    val backgroundColor = if (selected) {
        if (isDark) me.avinas.vanderwaals.ui.theme.InfoColorDark.copy(alpha = 0.15f) 
        else me.avinas.vanderwaals.ui.theme.LightPrimary.copy(alpha = 0.1f)
    } else {
        Color.Transparent
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .border(2.dp, borderColor, RoundedCornerShape(24.dp))
            .clickable { onClick() },
        contentPadding = PaddingValues(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundColor)
                .padding(20.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Icon Box
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) (if (isDark) me.avinas.vanderwaals.ui.theme.InfoColorDark else me.avinas.vanderwaals.ui.theme.LightPrimary)
                        else (if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) Color.White else (if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF4B5563)),
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else Color(0xFF111827)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isDark) Color.White.copy(alpha = 0.6f) else Color(0xFF4B5563),
                    lineHeight = 20.sp
                )
            }
            
            // Radio Indicator
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .border(
                        2.dp, 
                        if (selected) (if (isDark) me.avinas.vanderwaals.ui.theme.InfoColorDark else me.avinas.vanderwaals.ui.theme.LightPrimary)
                        else (if (isDark) Color.White.copy(alpha = 0.3f) else Color(0xFFD1D5DB)),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (isDark) me.avinas.vanderwaals.ui.theme.InfoColorDark else me.avinas.vanderwaals.ui.theme.LightPrimary)
                    )
                }
            }
        }
    }
}
