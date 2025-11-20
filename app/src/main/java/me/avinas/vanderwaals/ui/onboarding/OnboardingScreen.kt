package me.avinas.vanderwaals.ui.onboarding

/**
 * Compose screen for user onboarding flow (60 seconds max).
 * 
 * Multi-step onboarding process:
 * 
 * **Screen 1: Mode Selection**
 * - Two large Material 3 cards: "Auto Mode" | "Personalize"
 * - Auto Mode: Auto-select universally appealing wallpapers, learn from usage
 * - Personalize: Upload wallpaper for instant matching
 * 
 * **Screen 2: Personalization Upload** (if Personalize selected)
 * - Image picker for favorite wallpaper
 * - Or select from 6 style samples (minimal, nature, gruvbox, nord, anime, vibrant)
 * - Shows loading animation while extracting embedding (40ms) and finding matches (2-3s)
 * 
 * **Screen 2B: Confirmation Gallery**
 * - Grid of 8 diverse matches from top 50 results
 * - Tap to like (heart icon), long-press to dislike (X icon)
 * - Requires minimum 3 likes to continue
 * - Preference vector updates in real-time as user selects
 * 
 * **Screen 3: Application Settings**
 * - Apply to: Lock Screen / Home Screen / Both
 * - Change frequency: Every unlock / Hourly / Daily at [time] / Never
 * - "Start Using" button applies first wallpaper immediately
 * 
 * Navigation uses Compose Navigation with shared element transitions.
 * 
 * @see OnboardingViewModel
 * @see me.avinas.vanderwaals.domain.usecase.InitializePreferencesUseCase
 */
class OnboardingScreen {
    
}
