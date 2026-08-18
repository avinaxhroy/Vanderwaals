package me.avinas.vanderwaals.feature.wallpaper.presentation

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
            .fillMaxSize(),
    ) {
        composable<Onboarding> {
            OnboardingNavGraph(
                onOnboardingComplete = {
                    navController.navigate(Main) {
                        popUpTo<Onboarding> { inclusive = true }
                    }
                },
                onExitOnboarding = {
                    // back exits the app on the first onboarding screen
                }
            )
        }

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

        composable<History> {
            HistoryScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

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

        composable<Analytics> {
            AnalyticsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

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

        composable<Privacy> {
            me.avinas.vanderwaals.ui.settings.PrivacyPolicyScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
