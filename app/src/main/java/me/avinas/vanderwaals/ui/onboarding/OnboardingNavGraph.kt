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

        composable(OnboardingRoutes.INITIAL_SYNC) {
            InitialSyncScreen(
                onSyncComplete = {
                    navController.navigate(OnboardingRoutes.MODE_SELECTION)
                },
                currentStep = 2,
                totalSteps = 4
            )
        }

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

@Composable
private fun <T> remember(key: Any?, calculation: () -> T): T {
    return androidx.compose.runtime.remember(key) { calculation() }
}
