package me.avinas.vanderwaals.feature.wallpaper.presentation

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import me.avinas.vanderwaals.feature.wallpaper.presentation.notifications_screen.NotificationScreen
import me.avinas.vanderwaals.feature.wallpaper.util.navigation.*
import me.avinas.vanderwaals.ui.InitializationViewModel
import me.avinas.vanderwaals.ui.main.MainScreen
import me.avinas.vanderwaals.ui.history.HistoryScreen
import me.avinas.vanderwaals.ui.settings.SettingsScreen
import me.avinas.vanderwaals.ui.onboarding.OnboardingNavGraph
import me.avinas.vanderwaals.ui.analytics.AnalyticsScreen

/**
 * Vanderwaals App - Main navigation composable
 * Minimal navigation for algorithm-driven wallpaper experience
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VanderwaalsApp(
    firstLaunch: Boolean,
    initViewModel: InitializationViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = if (firstLaunch) Onboarding else Main,
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        // Onboarding flow - First launch experience
        composable<Onboarding> {
            // Use the full onboarding navigation graph with multi-step flow
            OnboardingNavGraph(
                onOnboardingComplete = {
                    navController.navigate(Main) {
                        popUpTo<Onboarding> { inclusive = true }
                    }
                },
                onExitOnboarding = {
                    // Exit app when back is pressed on first onboarding screen
                    // User can use system back button to exit
                }
            )
        }

        // Main screen - Current wallpaper display
        composable<Main> {
            MainScreen(
                onNavigateToHistory = {
                    navController.navigate(History)
                },
                onNavigateToSettings = {
                    navController.navigate(Settings)
                }
            )
        }

        // History screen - Wallpaper history with learning
        composable<History> {
            HistoryScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Settings screen - App configuration
        composable<Settings> {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToOnboarding = {
                    navController.navigate(Onboarding) {
                        popUpTo<Settings> { inclusive = true }
                    }
                },
                onNavigateToAnalytics = {
                    navController.navigate(Analytics)
                }
            )
        }

        // Analytics screen - Personalization insights
        composable<Analytics> {
            AnalyticsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Notification permission screen
        composable<Notification> {
            NotificationScreen(
                onAgree = {
                    if (firstLaunch) {
                        navController.navigate(Onboarding) {
                            popUpTo<Notification> { inclusive = true }
                        }
                    } else {
                        navController.navigate(Main) {
                            popUpTo<Notification> { inclusive = true }
                        }
                    }
                }
            )
        }

        // Privacy policy screen
        composable<Privacy> {
            // Simple privacy policy screen with Material 3 components
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Privacy Policy") },
                        navigationIcon = {
                            IconButton(
                                onClick = { navController.popBackStack() }
                            ) {
                                Icon(
                                    painter = painterResource(android.R.drawable.ic_menu_close_clear_cancel),
                                    contentDescription = "Back"
                                )
                            }
                        }
                    )
                }
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Privacy Policy",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Text(
                        text = """
                            Vanderwaals is designed with privacy in mind:
                            
                            • All wallpaper preferences are stored locally on your device
                            • No personal data is collected or transmitted
                            • Wallpaper downloads are from public sources (GitHub, Bing)
                            • No analytics or tracking
                            • No third-party data sharing
                            
                            Your wallpaper history and preferences never leave your device.
                        """.trimIndent(),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}
