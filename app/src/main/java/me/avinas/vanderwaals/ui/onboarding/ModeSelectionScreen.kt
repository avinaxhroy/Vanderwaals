package me.avinas.vanderwaals.ui.onboarding

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.blur
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import me.avinas.vanderwaals.ui.theme.animations.bounceOnAppear
import me.avinas.vanderwaals.ui.theme.animations.pressAnimation
import me.avinas.vanderwaals.ui.theme.components.GlassCard

/**
 * Mode Selection Screen - First screen in onboarding flow.
 * 
 * Presents two options:
 * - **Auto Mode**: Algorithm selects wallpapers, learns from usage
 * - **Personalize Mode**: User uploads sample for instant matching
 * 
 * **Layout:**
 * - App logo at top
 * - Two elevated cards with icons and descriptions
 * - Material 3 design with proper spacing
 * 
 * **Navigation:**
 * - Auto Mode → ApplicationSettings
 * - Personalize Mode → UploadWallpaper
 * 
 * @param onAutoModeSelected Callback when Auto Mode is selected
 * @param onPersonalizeModeSelected Callback when Personalize Mode is selected
 * @param onBackPressed Callback when back button is pressed (optional - exits onboarding)
 * @param viewModel ViewModel managing selection state
 */
@Composable
fun ModeSelectionScreen(
    onAutoModeSelected: () -> Unit,
    onPersonalizeModeSelected: () -> Unit,
    onBackPressed: (() -> Unit)? = null,
    viewModel: ModeSelectionViewModel = hiltViewModel()
) {
    val selectedMode by viewModel.selectedMode.collectAsState()
    
    // Handle system back button if callback provided
    if (onBackPressed != null) {
        androidx.activity.compose.BackHandler {
            onBackPressed()
        }
    }
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            // Ambient background for glassmorphism
            val primaryColor = MaterialTheme.colorScheme.primary
            val secondaryColor = MaterialTheme.colorScheme.secondary
            val tertiaryColor = MaterialTheme.colorScheme.tertiary
            
            Canvas(modifier = Modifier.fillMaxSize().blur(60.dp)) {
                drawCircle(
                    color = primaryColor.copy(alpha = 0.3f),
                    center = Offset(size.width * 0.85f, size.height * 0.15f),
                    radius = size.minDimension * 0.5f
                )
                drawCircle(
                    color = secondaryColor.copy(alpha = 0.3f),
                    center = Offset(size.width * 0.15f, size.height * 0.5f),
                    radius = size.minDimension * 0.4f
                )
                drawCircle(
                    color = tertiaryColor.copy(alpha = 0.3f),
                    center = Offset(size.width * 0.85f, size.height * 0.85f),
                    radius = size.minDimension * 0.5f
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .systemBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Logo at top with animation - clear background, larger size
                // Determine logo based on background luminance (low luminance = dark background = white logo)
                val isDarkBackground = androidx.compose.material3.MaterialTheme.colorScheme.background.luminance() < 0.5f
                val logoResId = if (isDarkBackground) {
                    me.avinas.vanderwaals.R.drawable.vanderwaals_logo
                } else {
                    me.avinas.vanderwaals.R.drawable.vanderwaals_logo_black
                }
                
                Image(
                    painter = painterResource(id = logoResId),
                    contentDescription = "Vanderwaals Logo",
                    modifier = Modifier
                        .size(160.dp)
                        .bounceOnAppear()
                )
                
                // Title
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Welcome to Vanderwaals",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    
                    Text(
                        text = "Choose how you'd like to start",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                
                // Auto Mode Card with Glassmorphism
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bounceOnAppear()
                        .clickable { 
                            viewModel.selectMode(OnboardingMode.AUTO)
                            onAutoModeSelected()
                        },
                    contentPadding = PaddingValues(24.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "Auto Mode",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "Let the algorithm pick great wallpapers and learn your style",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Personalize Mode Card with Glassmorphism
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bounceOnAppear()
                        .clickable { 
                            viewModel.selectMode(OnboardingMode.PERSONALIZE)
                            onPersonalizeModeSelected()
                        },
                    contentPadding = PaddingValues(24.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "Personalize",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "Upload your favorite wallpaper to find similar matches instantly",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
