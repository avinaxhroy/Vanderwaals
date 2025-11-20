package me.avinas.vanderwaals.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

/**
 * Onboarding navigation graph.
 * 
 * Flow:
 * 1. **ModeSelection**: Choose Auto or Personalize
 *    - Auto → ApplicationSettings
 *    - Personalize → UploadWallpaper
 * 
 * 2. **UploadWallpaper**: Upload image or select sample
 *    - After processing → ConfirmationGallery
 * 
 * 3. **ConfirmationGallery**: Like/dislike wallpapers
 *    - After 3+ likes → ApplicationSettings
 * 
 * 4. **ApplicationSettings**: Configure app settings
 *    - Start Using → Main screen (onOnboardingComplete)
 * 
 * **Shared ViewModels:**
 * - UploadWallpaperViewModel: Shares similar wallpapers with ConfirmationGallery
 * - ModeSelectionViewModel: Shared across onboarding to track selected mode
 * 
 * @param onOnboardingComplete Callback when onboarding finishes
 */
@Composable
fun OnboardingNavGraph(
    navController: NavHostController = rememberNavController(),
    onOnboardingComplete: () -> Unit,
    onExitOnboarding: (() -> Unit)? = null
) {
    // Shared ViewModel across all onboarding screens to track mode selection
    val modeSelectionViewModel: ModeSelectionViewModel = hiltViewModel()
    
    NavHost(
        navController = navController,
        startDestination = OnboardingRoutes.MODE_SELECTION
    ) {
        // Screen 1: Mode Selection
        composable(OnboardingRoutes.MODE_SELECTION) {
            ModeSelectionScreen(
                onAutoModeSelected = {
                    // Auto mode: Keep MODE_SELECTION in backstack for back navigation
                    navController.navigate(OnboardingRoutes.APPLICATION_SETTINGS)
                },
                onPersonalizeModeSelected = {
                    // Personalize mode: Go to upload screen
                    navController.navigate(OnboardingRoutes.UPLOAD_WALLPAPER)
                },
                onBackPressed = onExitOnboarding,
                viewModel = modeSelectionViewModel
            )
        }
        
        // Screen 2: Upload Wallpaper (Personalize only)
        composable(OnboardingRoutes.UPLOAD_WALLPAPER) {
            val uploadViewModel: UploadWallpaperViewModel = hiltViewModel()
            val similarWallpapers by uploadViewModel.similarWallpapers.collectAsState()
            
            UploadWallpaperScreen(
                onMatchesFound = {
                    navController.navigate(OnboardingRoutes.CONFIRMATION_GALLERY)
                },
                onBackPressed = {
                    android.util.Log.d("OnboardingNav", "UPLOAD_WALLPAPER back pressed")
                    navController.popBackStack()
                },
                viewModel = uploadViewModel
            )
        }
        
        // Screen 3: Confirmation Gallery (Personalize only)
        composable(OnboardingRoutes.CONFIRMATION_GALLERY) { backStackEntry ->
            // Get shared ViewModel from parent navigation graph
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(OnboardingRoutes.UPLOAD_WALLPAPER)
            }
            val uploadViewModel: UploadWallpaperViewModel = hiltViewModel(parentEntry)
            val confirmationViewModel: ConfirmationGalleryViewModel = hiltViewModel()
            
            val similarWallpapers by uploadViewModel.similarWallpapers.collectAsState()
            val userEmbedding by uploadViewModel.userEmbedding.collectAsState()
            
            // CRITICAL: Set wallpapers on first composition or when they change
            // Using 'true' as key ensures this runs once on composition
            // Using similarWallpapers as additional dependency catches updates
            LaunchedEffect(key1 = true, key2 = similarWallpapers.size) {
                android.util.Log.d("OnboardingNav", "LaunchedEffect: Checking wallpapers - count: ${similarWallpapers.size}")
                if (similarWallpapers.isNotEmpty()) {
                    android.util.Log.d("OnboardingNav", "LaunchedEffect: Setting ${similarWallpapers.size} wallpapers")
                    confirmationViewModel.setSimilarWallpapers(similarWallpapers, userEmbedding)
                } else {
                    android.util.Log.w("OnboardingNav", "LaunchedEffect: No wallpapers available!")
                }
            }
            
            ConfirmationGalleryScreen(
                onContinue = {
                    // Keep backstack for back navigation
                    navController.navigate(OnboardingRoutes.APPLICATION_SETTINGS)
                },
                onBackPressed = {
                    android.util.Log.d("OnboardingNav", "CONFIRMATION_GALLERY back pressed")
                    navController.popBackStack()
                },
                viewModel = confirmationViewModel
            )
        }
        
        // Screen 4: Application Settings (Both flows)
        composable(OnboardingRoutes.APPLICATION_SETTINGS) {
            val selectedMode by modeSelectionViewModel.selectedMode.collectAsState()
            
            ApplicationSettingsScreen(
                onStartUsing = {
                    onOnboardingComplete()
                },
                onBackPressed = {
                    // Navigate back - always goes to previous screen in backstack
                    android.util.Log.d("OnboardingNav", "APPLICATION_SETTINGS back pressed")
                    android.util.Log.d("OnboardingNav", "Previous entry: ${navController.previousBackStackEntry?.destination?.route}")
                    
                    // Just pop - navController handles the backstack
                    val popped = navController.popBackStack()
                    android.util.Log.d("OnboardingNav", "Pop result: $popped")
                },
                selectedMode = selectedMode
            )
        }
    }
}

/**
 * Remember function for imports.
 */
@Composable
private fun <T> remember(key: Any?, calculation: () -> T): T {
    return androidx.compose.runtime.remember(key) { calculation() }
}
