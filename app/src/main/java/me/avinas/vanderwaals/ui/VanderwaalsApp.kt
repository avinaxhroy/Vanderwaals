package me.avinas.vanderwaals.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

/**
 * Root composable. Shows onboarding if not yet completed, otherwise MainScreen.
 */
@Composable
fun VanderwaalsApp() {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("vanderwaals_prefs", android.content.Context.MODE_PRIVATE)
    }
    
    val onboardingComplete = remember {
        prefs.getBoolean("onboarding_completed", false)
    }
    
    // Use unified navigation graph
    VanderwaalsNavGraph(onboardingComplete = onboardingComplete)
}
