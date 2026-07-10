package me.avinas.vanderwaals.ui.onboarding

/**
 * Navigation routes for onboarding flow.
 * 
 * Flow:
 * 0. Welcome → Informational overview
 * 1. WallpaperSourceSelection → Choose wallpaper sources
 * 2. InitialSync → Download wallpaper catalog
 * 3. ModeSelection → Choose Auto or Personalize
 * 4. UploadWallpaper → Upload or select sample (Personalize only)
 * 5. ConfirmationGallery → Like/dislike wallpapers
 * 6. ApplicationSettings → Configure wallpaper settings
 * 
 * @see VanderwaalsStrategy.md
 */
object OnboardingRoutes {
    const val WELCOME = "onboarding/welcome"
    const val INITIAL_SYNC = "onboarding/initial_sync"
    const val MODE_SELECTION = "onboarding/mode_selection"
    const val UPLOAD_WALLPAPER = "onboarding/upload_wallpaper"
    const val CONFIRMATION_GALLERY = "onboarding/confirmation_gallery"
    const val WALLPAPER_SOURCE_SELECTION = "onboarding/wallpaper_source_selection"
    const val APPLICATION_SETTINGS = "onboarding/application_settings"
}
