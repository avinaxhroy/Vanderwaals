package me.avinas.vanderwaals.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

/**
 * Main app composable with navigation integration.
 * 
 * Checks if user has completed onboarding:
 * - If not completed: Show onboarding flow (ModeSelection → ...)
 * - If completed: Show main app (MainScreen with navigation)
 * 
 * **Usage in Activity:**
 * ```kotlin
 * @AndroidEntryPoint
 * class MainActivity : ComponentActivity() {
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *         setContent {
 *             VanderwaalsTheme {
 *                 VanderwaalsApp()
 *             }
 *         }
 *     }
 * }
 * ```
 * 
 * **Onboarding Persistence:**
 * Onboarding completion status is stored in SharedPreferences.
 * To reset onboarding (for testing):
 * ```kotlin
 * context.getSharedPreferences("vanderwaals_prefs", Context.MODE_PRIVATE)
 *     .edit()
 *     .putBoolean("onboarding_completed", false)
 *     .apply()
 * ```
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
