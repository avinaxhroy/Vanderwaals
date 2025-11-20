package me.avinas.vanderwaals.ui.onboarding

/**
 * ViewModel for onboarding flow state management.
 * 
 * Manages:
 * - Current onboarding step (mode selection, upload, confirmation, settings)
 * - User's mode choice (auto or personalized)
 * - Uploaded wallpaper image processing
 * - Top 50 matching wallpapers for confirmation gallery
 * - User's likes from confirmation gallery (minimum 3 required)
 * - Application settings (apply to, frequency, time)
 * - Initial preference vector creation from liked wallpapers
 * 
 * StateFlow emissions:
 * - OnboardingState: Current step and UI state
 * - ProcessingState: Loading state for embedding extraction and matching
 * - MatchingResults: Top 50 wallpapers for confirmation
 * - ValidationState: Whether user can proceed (e.g., 3+ likes selected)
 * 
 * Uses cases:
 * - InitializePreferencesUseCase: Create initial preference vector
 * - GetRankedWallpapersUseCase: Get matching wallpapers
 * 
 * @see OnboardingScreen
 */
class OnboardingViewModel {
    
}
