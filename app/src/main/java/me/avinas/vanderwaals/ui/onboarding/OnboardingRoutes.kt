package me.avinas.vanderwaals.ui.onboarding

/**
 * Navigation routes for onboarding flow.
 * 
 * Flow:
 * 0. InitialSync → Download wallpaper catalog (first launch only)
 * 1. ModeSelection → Choose Auto or Personalize
 * 2. UploadWallpaper → Upload or select sample (Personalize only)
 * 3. ConfirmationGallery → Like/dislike wallpapers
 * 4. ApplicationSettings → Configure wallpaper settings
 * 
 * @see VanderwaalsStrategy.md
 */
object OnboardingRoutes {
    const val INITIAL_SYNC = "onboarding/initial_sync"
    const val MODE_SELECTION = "onboarding/mode_selection"
    const val UPLOAD_WALLPAPER = "onboarding/upload_wallpaper"
    const val CONFIRMATION_GALLERY = "onboarding/confirmation_gallery"
    const val APPLICATION_SETTINGS = "onboarding/application_settings"
}
