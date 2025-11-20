package me.avinas.vanderwaals.ui.onboarding

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

/**
 * Upload Wallpaper Screen - Second screen in personalize flow.
 * 
 * Allows user to:
 * - Upload their favorite wallpaper from gallery
 * - Select from 6 pre-defined style samples
 * 
 * **Process:**
 * 1. User picks image (upload or sample)
 * 2. Extract embedding (40-50ms) - shows loading
 * 3. Find top 50 similar wallpapers (50ms)
 * 4. Navigate to confirmation gallery
 * 
 * **Permission:**
 * Handles READ_MEDIA_IMAGES (Android 13+) or READ_EXTERNAL_STORAGE
 * 
 * @param onMatchesFound Callback with similar wallpapers found
 * @param onBackPressed Callback when back button is pressed
 * @param viewModel ViewModel managing upload and processing
 */
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun UploadWallpaperScreen(
    onMatchesFound: () -> Unit,
    onBackPressed: () -> Unit = {},
    viewModel: UploadWallpaperViewModel = hiltViewModel()
) {
    // Handle system back button
    androidx.activity.compose.BackHandler {
        android.util.Log.d("UploadWallpaperScreen", "BackHandler triggered!")
        onBackPressed()
    }
    
    val uploadState by viewModel.uploadState.collectAsState()
    val similarWallpapers by viewModel.similarWallpapers.collectAsState()
    
    // Permission state
    val permissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(Manifest.permission.READ_MEDIA_IMAGES)
    } else {
        rememberPermissionState(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    
    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.uploadWallpaper(it) }
    }
    
    // Navigate when matches found
    LaunchedEffect(uploadState) {
        android.util.Log.d("UploadWallpaperScreen", "Upload state changed: $uploadState")
        if (uploadState is UploadState.Success) {
            android.util.Log.d("UploadWallpaperScreen", "Success! Navigating to confirmation gallery...")
            onMatchesFound()
        }
    }
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = {
                        android.util.Log.d("UploadWallpaperScreen", "Back icon clicked!")
                        onBackPressed()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                modifier = Modifier.statusBarsPadding()
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            // Ambient background for glassmorphism
            val primaryColor = MaterialTheme.colorScheme.primary
            val secondaryColor = MaterialTheme.colorScheme.secondary
            
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = primaryColor.copy(alpha = 0.08f),
                    center = Offset(size.width * 0.2f, size.height * 0.2f),
                    radius = size.minDimension * 0.4f
                )
                drawCircle(
                    color = secondaryColor.copy(alpha = 0.08f),
                    center = Offset(size.width * 0.8f, size.height * 0.8f),
                    radius = size.minDimension * 0.4f
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    
                    // Title
                    Text(
                        text = "Upload Your Favorite Wallpaper",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    
                    Text(
                        text = "We'll find similar wallpapers you'll love",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Upload Button with Glassmorphism
                    val primaryColor = MaterialTheme.colorScheme.primary
                    Card(
                        onClick = {
                            if (permissionState.status.isGranted) {
                                imagePickerLauncher.launch("image/*")
                            } else {
                                permissionState.launchPermissionRequest()
                            }
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.02f)
                                )
                            )
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .drawBehind {
                                val stroke = Stroke(
                                    width = 2.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 20f), 0f)
                                )
                                drawRoundRect(
                                    color = primaryColor.copy(alpha = 0.5f),
                                    style = stroke,
                                    cornerRadius = CornerRadius(12.dp.toPx())
                                )
                            }
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Upload,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                text = "Tap to Upload",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    
                    // Divider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f))
                        Text(
                            text = "OR CHOOSE A STYLE",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f))
                    }
                    
                    // Style Samples Grid
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(WallpaperStyle.values()) { style ->
                            StyleSampleCard(
                                style = style,
                                onClick = { viewModel.selectSampleWallpaper(style) }
                            )
                        }
                    }
                }
                
                // Loading Overlay
                if (uploadState is UploadState.Extracting || uploadState is UploadState.FindingMatches) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f) // More transparent for glass feel
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize().background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                                    )
                                )
                            )
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(64.dp)
                                )
                                
                                Spacer(modifier = Modifier.height(24.dp))
                                
                                Text(
                                    text = when (uploadState) {
                                        is UploadState.Extracting -> "Analyzing your wallpaper..."
                                        is UploadState.FindingMatches -> "Finding perfect matches..."
                                        else -> ""
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
                
                // Error Snackbar
                if (uploadState is UploadState.Error) {
                    Snackbar(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                        action = {
                            TextButton(onClick = { viewModel.resetState() }) {
                                Text("Dismiss")
                            }
                        }
                    ) {
                        Text((uploadState as UploadState.Error).message)
                    }
                }
            }
        }
    }
}

/**
 * Style sample card component.
 * 
 * @param style Wallpaper style
 * @param onClick Click callback
 */
@Composable
private fun StyleSampleCard(
    style: WallpaperStyle,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
        ),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.02f)
                )
            )
        ),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = style.icon,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = style.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Icon for each wallpaper style.
 */
private val WallpaperStyle.icon: ImageVector
    get() = when (this) {
        WallpaperStyle.NATURE -> Icons.Filled.Nature
        WallpaperStyle.MINIMAL -> Icons.Filled.Minimalist
        WallpaperStyle.DARK -> Icons.Filled.DarkMode
        WallpaperStyle.ABSTRACT -> Icons.Filled.AutoAwesome
        WallpaperStyle.COLORFUL -> Icons.Filled.Palette
        WallpaperStyle.ANIME -> Icons.Filled.Animation
    }

// Custom icons (Material Icons equivalents)
private val Icons.Filled.Nature: ImageVector
    get() = ImageVector.Builder(
        name = "Nature",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 1.0f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f,
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(14f, 6f)
            lineToRelative(-3.75f, 5f)
            lineToRelative(2.85f, 3.8f)
            lineToRelative(-1.6f, 1.2f)
            curveTo(11.81f, 17.2f, 12.23f, 19f, 13f, 19f)
            horizontalLineToRelative(7f)
            curveToRelative(0.72f, 0f, 1.12f, -1.76f, 1.34f, -2.95f)
            curveToRelative(-1.56f, -0.48f, -3.09f, -1.78f, -3.59f, -3.05f)
            close()
        }
    }.build()

private val Icons.Filled.Minimalist: ImageVector
    get() = ImageVector.Builder(
        name = "Minimalist",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Black)
        ) {
            moveTo(12f, 2f)
            curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
            reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
            reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
            reflectiveCurveTo(17.52f, 2f, 12f, 2f)
            close()
        }
    }.build()

private val Icons.Filled.DarkMode: ImageVector
    get() = ImageVector.Builder(
        name = "DarkMode",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Black)
        ) {
            moveTo(12f, 3f)
            curveToRelative(-4.97f, 0f, -9f, 4.03f, -9f, 9f)
            reflectiveCurveToRelative(4.03f, 9f, 9f, 9f)
            reflectiveCurveToRelative(9f, -4.03f, 9f, -9f)
            curveToRelative(0f, -0.46f, -0.04f, -0.92f, -0.1f, -1.36f)
            curveToRelative(-0.98f, 1.37f, -2.58f, 2.26f, -4.4f, 2.26f)
            curveToRelative(-2.98f, 0f, -5.4f, -2.42f, -5.4f, -5.4f)
            curveToRelative(0f, -1.81f, 0.89f, -3.42f, 2.26f, -4.4f)
            curveTo(12.92f, 3.04f, 12.46f, 3f, 12f, 3f)
            close()
        }
    }.build()

private val Icons.Filled.Animation: ImageVector
    get() = ImageVector.Builder(
        name = "Animation",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Black)
        ) {
            moveTo(15f, 2f)
            curveToRelative(-1.83f, 0f, -3.53f, 0.55f, -4.95f, 1.48f)
            curveTo(9.03f, 4.79f, 8f, 6.79f, 8f, 9f)
            curveToRelative(0f, 3.31f, 2.69f, 6f, 6f, 6f)
            reflectiveCurveToRelative(6f, -2.69f, 6f, -6f)
            reflectiveCurveTo(17.31f, 3f, 14f, 3f)
            close()
            moveTo(9f, 11f)
            curveToRelative(-1.66f, 0f, -3f, -1.34f, -3f, -3f)
            reflectiveCurveToRelative(1.34f, -3f, 3f, -3f)
            reflectiveCurveToRelative(3f, 1.34f, 3f, 3f)
            reflectiveCurveToRelative(-1.34f, 3f, -3f, 3f)
            close()
        }
    }.build()
