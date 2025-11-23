package me.avinas.vanderwaals.ui.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
            // Dynamic Background Blobs
            val isDark = me.avinas.vanderwaals.ui.theme.LocalThemeIsDark.current
            val infiniteTransition = rememberInfiniteTransition(label = "blobs")

            // Animate positions
            val offset1 by infiniteTransition.animateFloat(
                initialValue = 0f, targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(10000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ), label = "offset1"
            )
            val offset2 by infiniteTransition.animateFloat(
                initialValue = 0f, targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(15000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ), label = "offset2"
            )

            Canvas(modifier = Modifier.fillMaxSize().blur(80.dp)) {
                val w = size.width
                val h = size.height

                if (isDark) {
                    // Dark Mode Blobs (Indigo/Rose/Sky)
                    drawCircle(
                        color = Color(0xFF5C6BC0).copy(alpha = 0.2f), // Indigo 400
                        center = Offset(w * 0.2f + (offset1 * 100f), h * 0.2f),
                        radius = 400.dp.toPx()
                    )
                    drawCircle(
                        color = Color(0xFFEC407A).copy(alpha = 0.15f), // Rose 400
                        center = Offset(w * 0.8f - (offset2 * 100f), h * 0.5f),
                        radius = 350.dp.toPx()
                    )
                    drawCircle(
                        color = Color(0xFF29B6F6).copy(alpha = 0.15f), // Sky 400
                        center = Offset(w * 0.4f, h * 0.8f + (offset1 * 50f)),
                        radius = 450.dp.toPx()
                    )
                } else {
                    // Light Mode Blobs (Purple/Orange/Teal)
                    drawCircle(
                        color = Color(0xFFAB47BC).copy(alpha = 0.3f), // Purple 400
                        center = Offset(w * 0.8f - (offset1 * 100f), h * 0.1f),
                        radius = 500.dp.toPx()
                    )
                    drawCircle(
                        color = Color(0xFFFFA726).copy(alpha = 0.25f), // Orange 400
                        center = Offset(w * 0.1f + (offset2 * 100f), h * 0.6f),
                        radius = 400.dp.toPx()
                    )
                    drawCircle(
                        color = Color(0xFF26A69A).copy(alpha = 0.25f), // Teal 400
                        center = Offset(w * 0.6f, h * 0.9f - (offset1 * 50f)),
                        radius = 450.dp.toPx()
                    )
                }
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
                    shape = RoundedCornerShape(24.dp),
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
                                tint = MaterialTheme.colorScheme.onSurface
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
                    shape = RoundedCornerShape(24.dp),
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
