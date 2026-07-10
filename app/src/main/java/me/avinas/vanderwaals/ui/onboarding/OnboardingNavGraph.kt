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
 * 0. **Welcome**: Informational overview with Get Started/Skip
 *    - Get Started → WallpaperSourceSelection
 *    - Skip → WallpaperSourceSelection
 * 
 * 1. **WallpaperSourceSelection**: Choose wallpaper sources
 *    - Continue → InitialSync
 * 
 * 2. **InitialSync**: Download wallpaper catalog
 *    - On complete → ModeSelection
 * 
 * 3. **ModeSelection**: Choose Auto or Personalize
 *    - Auto → ApplicationSettings
 *    - Personalize → UploadWallpaper
 * 
 * 4. **UploadWallpaper**: Upload image or select sample
 *    - After processing → ConfirmationGallery
 * 
 * 5. **ConfirmationGallery**: Like/dislike wallpapers
 *    - After 3+ likes → ApplicationSettings
 * 
 * 6. **ApplicationSettings**: Configure app settings
 *    - Start Using → Main screen (onOnboardingComplete)
 * 
 * **Shared ViewModels:**
 * - UploadWallpaperViewModel: Shares similar wallpapers with ConfirmationGallery
 * - ModeSelectionViewModel: Shared across onboarding to track selected mode
 * 
 * **Back Navigation Data Handling:**
 * - UploadWallpaper → ModeSelection: Clears upload data
 * - ConfirmationGallery → UploadWallpaper: Clears confirmation data, preserves upload results for reuse
 * - ApplicationSettings → Previous: Navigates to correct previous screen based on flow
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
        startDestination = OnboardingRoutes.WELCOME
    ) {
        // Screen 0: Welcome (NEW!)
        composable(OnboardingRoutes.WELCOME) {
            WelcomeScreen(
                onGetStarted = {
                    navController.navigate(OnboardingRoutes.WALLPAPER_SOURCE_SELECTION) {
                        popUpTo(OnboardingRoutes.WELCOME) { inclusive = true }
                    }
                },
                onSkip = {
                    navController.navigate(OnboardingRoutes.WALLPAPER_SOURCE_SELECTION) {
                        popUpTo(OnboardingRoutes.WELCOME) { inclusive = true }
                    }
                }
            )
        }

        // Screen 1: Wallpaper Source Selection
        composable(OnboardingRoutes.WALLPAPER_SOURCE_SELECTION) {
            WallpaperSourceSelectionScreen(
                onContinue = {
                    navController.navigate(OnboardingRoutes.INITIAL_SYNC)
                },
                onBack = {
                    navController.popBackStack()
                },
                currentStep = 1,
                totalSteps = 4
            )
        }

        // Screen 2: Initial Sync (Download)
        composable(OnboardingRoutes.INITIAL_SYNC) {
            InitialSyncScreen(
                onSyncComplete = {
                    navController.navigate(OnboardingRoutes.MODE_SELECTION)
                },
                currentStep = 2,
                totalSteps = 4
            )
        }

        // Screen 3: Mode Selection
        composable(OnboardingRoutes.MODE_SELECTION) {
            val selectedMode by modeSelectionViewModel.selectedMode.collectAsState()
            val totalSteps = if (selectedMode == OnboardingMode.AUTO) 4 else 6

            ModeSelectionScreen(
                onModeSelected = { mode ->
                    when (mode) {
                        OnboardingMode.AUTO -> navController.navigate(OnboardingRoutes.APPLICATION_SETTINGS)
                        OnboardingMode.PERSONALIZE -> navController.navigate(OnboardingRoutes.UPLOAD_WALLPAPER)
                    }
                },
                onBack = { navController.popBackStack() },
                viewModel = modeSelectionViewModel,
                currentStep = 3,
                totalSteps = totalSteps
            )
        }

        // Screen 4: Upload Wallpaper (Personalize only)
        composable(OnboardingRoutes.UPLOAD_WALLPAPER) {
            val uploadViewModel: UploadWallpaperViewModel = hiltViewModel()
            val similarWallpapers by uploadViewModel.similarWallpapers.collectAsState()

            UploadWallpaperScreen(
                onMatchesFound = {
                    navController.navigate(OnboardingRoutes.CONFIRMATION_GALLERY)
                },
                onBackPressed = {
                    android.util.Log.d("OnboardingNav", "UPLOAD_WALLPAPER back pressed")
                    uploadViewModel.resetState()
                    navController.popBackStack()
                },
                viewModel = uploadViewModel,
                currentStep = 4,
                totalSteps = 6
            )
        }

        // Screen 5: Confirmation Gallery (Personalize only)
        composable(OnboardingRoutes.CONFIRMATION_GALLERY) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(OnboardingRoutes.UPLOAD_WALLPAPER)
            }
            val uploadViewModel: UploadWallpaperViewModel = hiltViewModel(parentEntry)
            val confirmationViewModel: ConfirmationGalleryViewModel = hiltViewModel()

            val similarWallpapers by uploadViewModel.similarWallpapers.collectAsState()
            val userEmbedding by uploadViewModel.userEmbedding.collectAsState()

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
                    navController.navigate(OnboardingRoutes.APPLICATION_SETTINGS)
                },
                onBack = {
                    android.util.Log.d("OnboardingNav", "CONFIRMATION_GALLERY back pressed")
                    confirmationViewModel.resetStateForBackNavigation()
                    uploadViewModel.resetStateForBackNavigation()
                    navController.popBackStack()
                },
                viewModel = confirmationViewModel,
                currentStep = 5,
                totalSteps = 6
            )
        }

        // Screen 6: Application Settings (Both flows)
        composable(OnboardingRoutes.APPLICATION_SETTINGS) {
            val selectedMode by modeSelectionViewModel.selectedMode.collectAsState()
            val isAuto = selectedMode == OnboardingMode.AUTO
            val totalNum = if (isAuto) 4 else 6

            ApplicationSettingsScreen(
                onStartUsing = {
                    onOnboardingComplete()
                },
                onBackPressed = {
                    android.util.Log.d("OnboardingNav", "APPLICATION_SETTINGS back pressed")
                    val previousRoute = navController.previousBackStackEntry?.destination?.route
                    if (previousRoute == OnboardingRoutes.CONFIRMATION_GALLERY) {
                        val confirmEntry = runCatching { navController.getBackStackEntry(OnboardingRoutes.CONFIRMATION_GALLERY) }.getOrNull()
                        if (confirmEntry != null) {
                            confirmEntry.savedStateHandle["resetFinishState"] = true
                        }
                    }
                    navController.popBackStack()
                },
                selectedMode = selectedMode,
                currentStep = if (isAuto) 4 else 6,
                totalSteps = totalNum
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
