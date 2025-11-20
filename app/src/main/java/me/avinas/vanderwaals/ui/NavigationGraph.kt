package me.avinas.vanderwaals.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import me.avinas.vanderwaals.ui.history.HistoryScreen
import me.avinas.vanderwaals.ui.main.MainScreen
import me.avinas.vanderwaals.ui.onboarding.*
import me.avinas.vanderwaals.ui.settings.SettingsScreen

/**
 * Navigation routes for Vanderwaals app.
 * 
 * **Onboarding Flow:**
 * ModeSelection → (Auto: ApplicationSettings) OR (Personalize: UploadWallpaper → ConfirmationGallery → ApplicationSettings)
 * 
 * **Main App Flow:**
 * Main → History → Main
 * Main → Settings → Main
 * Settings → Re-personalize → UploadWallpaper (onboarding)
 * 
 * **Back Stack Behavior:**
 * - Onboarding screens: No back to previous onboarding step
 * - Main screen: Back exits app
 * - History/Settings: Back to Main
 */
sealed class Screen(val route: String) {
    // Onboarding flow
    object InitialSync : Screen("initial_sync")
    object ModeSelection : Screen("mode_selection")
    object UploadWallpaper : Screen("upload_wallpaper")
    object ConfirmationGallery : Screen("confirmation_gallery")
    object ApplicationSettings : Screen("application_settings")
    
    // Main app
    object Main : Screen("main")
    object History : Screen("history")
    object Settings : Screen("settings")
    object Analytics : Screen("analytics")
}

/**
 * Main navigation graph for Vanderwaals.
 * 
 * Determines start destination based on onboarding completion flag.
 * If user has completed onboarding → Main
 * If new user → ModeSelection
 * 
 * @param onboardingComplete Whether user has completed onboarding
 * @param navController Optional NavController (defaults to rememberNavController)
 */
@Composable
fun VanderwaalsNavGraph(
    onboardingComplete: Boolean,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        // FIXED: Go directly to ModeSelection
        // InitializationViewModel handles catalog download during app startup
        // No need for separate InitialSyncScreen (was causing duplicate downloads)
        startDestination = if (onboardingComplete) Screen.Main.route else Screen.ModeSelection.route
    ) {
        // ========== ONBOARDING FLOW ==========
        
        // Step 1: Mode Selection (catalog already downloaded by InitializationViewModel)
        composable(Screen.ModeSelection.route) {
            ModeSelectionScreen(
                onAutoModeSelected = {
                    // Keep ModeSelection in backstack for back navigation
                    navController.navigate(Screen.ApplicationSettings.route)
                },
                onPersonalizeModeSelected = {
                    // Keep ModeSelection in backstack for back navigation
                    navController.navigate(Screen.UploadWallpaper.route)
                }
            )
        }
        
        composable(Screen.UploadWallpaper.route) {
            val uploadViewModel: UploadWallpaperViewModel = hiltViewModel()
            
            UploadWallpaperScreen(
                onMatchesFound = {
                    // Keep UploadWallpaper in backstack for ViewModel sharing
                    navController.navigate(Screen.ConfirmationGallery.route)
                },
                onBackPressed = {
                    // Check if we came from Settings (re-personalize flow)
                    val previousRoute = navController.previousBackStackEntry?.destination?.route
                    if (previousRoute == Screen.Settings.route) {
                        // Return to settings
                        navController.popBackStack()
                    } else {
                        // Return to previous onboarding screen or exit
                        navController.popBackStack()
                    }
                },
                viewModel = uploadViewModel
            )
        }
        
        composable(Screen.ConfirmationGallery.route) { backStackEntry ->
            // Get shared ViewModel from UploadWallpaper screen
            val parentEntry = androidx.compose.runtime.remember(backStackEntry) {
                navController.getBackStackEntry(Screen.UploadWallpaper.route)
            }
            val uploadViewModel: UploadWallpaperViewModel = hiltViewModel(parentEntry)
            val confirmationViewModel: ConfirmationGalleryViewModel = hiltViewModel()
            
            val similarWallpapers by uploadViewModel.similarWallpapers.collectAsState()
            val userEmbedding by uploadViewModel.userEmbedding.collectAsState()
            
            // Pass wallpapers AND original embedding to confirmation screen
            androidx.compose.runtime.LaunchedEffect(key1 = true, key2 = similarWallpapers.size) {
                android.util.Log.d("NavigationGraph", "LaunchedEffect: Checking wallpapers - count: ${similarWallpapers.size}")
                if (similarWallpapers.isNotEmpty()) {
                    android.util.Log.d("NavigationGraph", "LaunchedEffect: Setting ${similarWallpapers.size} wallpapers with original embedding")
                    confirmationViewModel.setSimilarWallpapers(similarWallpapers, userEmbedding)
                } else {
                    android.util.Log.w("NavigationGraph", "LaunchedEffect: No wallpapers available!")
                }
            }
            
            ConfirmationGalleryScreen(
                onContinue = {
                    // Try to return to Settings if present (re-personalize flow)
                    val returned = navController.popBackStack(Screen.Settings.route, inclusive = false)
                    if (!returned) {
                        // Regular onboarding flow: go to application settings
                        navController.navigate(Screen.ApplicationSettings.route)
                    }
                },
                onBackPressed = {
                    // Always go back to upload wallpaper screen
                    navController.popBackStack()
                },
                viewModel = confirmationViewModel
            )
        }
        
        composable(Screen.ApplicationSettings.route) {
            ApplicationSettingsScreen(
                onStartUsing = {
                    // Complete onboarding, navigate to main with clear back stack
                    navController.navigate(Screen.Main.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onBackPressed = {
                    android.util.Log.d("NavigationGraph", "APPLICATION_SETTINGS (main) back pressed")
                    navController.popBackStack()
                }
            )
        }
        
        // ========== MAIN APP FLOW ==========
        
        composable(Screen.Main.route) {
            MainScreen(
                onNavigateToHistory = {
                    navController.navigate(Screen.History.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }
        
        composable(Screen.History.route) {
            HistoryScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToOnboarding = {
                    // Re-personalize: go to upload wallpaper, then back to settings
                    navController.navigate(Screen.UploadWallpaper.route)
                },
                onNavigateToAnalytics = {
                    navController.navigate(Screen.Analytics.route)
                }
            )
        }
        
        composable(Screen.Analytics.route) {
            me.avinas.vanderwaals.ui.analytics.AnalyticsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}

/**
 * Determines if user has completed onboarding by checking UserPreferences.
 * Returns true if user preferences exist in the database (onboarding completed).
 */
@Composable
fun rememberOnboardingComplete(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    val database = me.avinas.vanderwaals.data.VanderwaalsDatabase.getInstance(context)
    
    // Collect the user preferences flow
    val userPreferences by database.userPreferenceDao.get().collectAsState(initial = null)
    
    // Onboarding is complete if user preferences exist
    return userPreferences != null
}
