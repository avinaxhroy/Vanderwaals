package me.avinas.vanderwaals.ui.common

/**
 * Reusable loading state composables for Vanderwaals UI.
 * 
 * Provides consistent loading indicators across the app:
 * 
 * **WallpaperLoadingAnimation:**
 * - Lottie animation for wallpaper processing
 * - Shows during embedding extraction and matching
 * - Material 3 circular progress with custom animation
 * 
 * **SyncLoadingIndicator:**
 * - Linear progress indicator for catalog sync
 * - Shows download progress percentage
 * - Animated sync icon rotation
 * 
 * **EmptyStateView:**
 * - Illustration for empty history
 * - "No wallpapers yet" message
 * - Call-to-action button
 * 
 * **ErrorStateView:**
 * - Error illustration
 * - Error message display
 * - Retry action button
 * 
 * All components use:
 * - Material 3 design guidelines
 * - Consistent animation timing (300ms default)
 * - Accessible content descriptions
 * - Adaptive sizing for different screen sizes
 * 
 * Leverages:
 * - Lottie Compose for animations
 * - Material 3 progress indicators
 * - Paperize's animation utilities
 * 
 * @see me.avinas.vanderwaals.ui.onboarding.OnboardingScreen
 * @see me.avinas.vanderwaals.ui.settings.SettingsScreen
 */
class LoadingComponents {
    
}
