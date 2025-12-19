# Changelog

## [4.0.0] - 2025-12-19

### Major Update: Optimized Wallpaper Catalog

- **90% Smaller Downloads**: Manifest now uses quantized embeddings, reducing download size from 60+ MB to ~6 MB while maintaining full recommendation quality.
- **Bing Wallpapers Support**: Integrated Bing's daily and archive wallpapers (5,400+ high-quality images) with full neural network embedding support for personalized recommendations.
  - Bing Lite manifest (~1000+ wallpapers from last 3 years)
  - Bing Full manifest (complete archive: 2009-present)
  - MobileNetV3 embeddings for all Bing wallpapers
  - Seamless integration with existing recommendation engine
- **Smart Migration System**: Users upgrading from v3.x automatically see a migration dialog prompting them to update their local catalog. The dialog:
  - Explains the benefits (faster sync, smaller data usage)
  - Shows real-time progress during update
  - Allows "Update Now" or "Later" options
  - Can be permanently dismissed if needed
- **Seamless Transition**: Existing users upgrading from Play Store will experience zero app breakage. Old catalog continues working until they choose to update.
- **Version Tracking**: App now tracks version codes to intelligently detect upgrades and trigger appropriate migrations for future updates.

### Technical Improvements
- **Backward Compatibility**: Full support for both v1 (legacy float32) and v2 (quantized int8) manifest formats, ensuring no disruption during the Play Store rollout period.
- **Manifest Versioning**: Infrastructure for versioned manifests enables smooth future catalog updates without breaking existing installations.
- **Multi-Source Architecture**: Enhanced codebase to support multiple wallpaper sources (GitHub repos + Bing) with unified embedding-based recommendations.

## [3.8.6] - 2025-12-17

### Critical Bug Fix
- **Auto-Change Reliability**: Fixed issue where auto-change wallpaper would not work when the app was swiped away from the recent apps menu. The fix uses a foreground service (inspired by Paperize) instead of WorkManager to ensure reliable execution even when the app's process was killed.

## [3.8.5] - 2025-12-16

### Smart Crop Quality Preservation
- **Lossless Cropped Cache**: Cropped wallpapers are now saved as PNG instead of JPEG, eliminating compression artifacts that degraded quality.
- **Resolution Preservation**: When source images are significantly larger than the screen (1.5x+), SmartCrop now preserves 25% more resolution for better visual quality.
- **No More Aggressive Downscaling**: High-resolution wallpapers (2+ MB) maintain their quality instead of being aggressively compressed.

## [3.8.0] - 2025-12-16

### Samsung Optimizations
- **Direct WakeLock Integration**: Implemented `SamsungPowerHelper` and Service-level WakeLocks to prevent `WallpaperMonitorService` from being killed by aggressive One UI power management.
- **Enhanced Reliability**: Added specific handling for Samsung's "Sleeping Apps" and battery usage limits to ensure consistent wallpaper changes on S23+ devices.

### Algorithm Improvements
- **Fast Auto-Mode Initialization**: Auto Mode now initializes preference vectors immediately upon the *first* like or download, dramatically speeding up the "learning" phase for new users.
- **Enhanced Composition Learning**: "Download" actions now carry 1.5x weight for learning specific composition preferences (symmetry, complexity, balance).

### Onboarding & UX
- **Instant Gratification**: The first wallpaper liked during onboarding is now immediately applied when clicking "Start Using Vanderwaals".
- **Improved Alarm Permissions**: Added a graceful fallback to "inexact scheduling" if the user denies exact alarm permissions, ensuring the app still functions (albeit with less precision).
- **Daily Playlist Pre-fetching**: Added logic to immediately download the initial daily playlist during onboarding for smoother first-run experience.

## [3.6.0] - 2025-12-01

### New Features
- **Dark Mode Aesthetics**: "Deep Ocean" theme with Deep Royal Blue, Deep Teal, and Cyan/Slate gradients.
- **Analytics Upgrade**: Complete overhaul of Analytics screen. Replaced "Activity Trend" with `PersonalizationStatusCard`, `InsightsSection`, and `LearningProgressCard`.
- **Enhanced Backgrounds**: Premium glassmorphic backgrounds for Settings and History screens.

### Improvements
- **UI Polish**: Refined TopAppBar blending and status bar padding.
- **Backend Robustness**: Enhanced input validation, network retry logic, and service lifecycle management.
- **Performance**: Optimized `WallpaperMonitorService` and added database indexes.
- **Tooling**: Major robustness improvements to `curate_wallpapers.py` including sparse checkout, retries, and better error handling.

### Bug Fixes
- **Status Bar**: Fixed overlaps on Analytics and Onboarding screens.
- **Wallpaper Logic**: Fixed issue with repeating wallpapers.
- **Crash Fixes**: Resolved database migration crashes (Download Queue, User Preferences) and image loading errors.
- **Build System**: Fixed Node path issues and Gradle build errors.

## [3.0.0] - 2025-11-23

### New Features
- **Glassmorphism UI**: Introduced a premium glassmorphism design language with `GlassMorphicCard`, `GlassSheet`, and dynamic background blur effects across the app.
- **LiteRT Migration**: Migrated on-device ML from TensorFlow Lite to LiteRT to ensure future compatibility with Android 15+ (16KB page size support).
- **Dynamic Wallpaper Conflict Handling**: Added smart detection and user guidance for devices with conflicting live wallpaper services (Xiaomi, Samsung, etc.).
- **Light Mode**: Introducing Light Mode Support
### Improvements
- **Performance Overhaul**: Significant optimizations in `AnalyticsScreen` and `HistoryScreen` to eliminate UI lag by moving heavy computations to background threads.
- **Network Optimization**: Tuned `OkHttpClient` configuration for faster and more reliable wallpaper downloads.
- **Smart Crop 2.0**: Enhanced cropping logic for landscape wallpapers on portrait devices and improved tablet support.
- **Haptic Feedback**: Added tactile feedback to key interactions in History and Wallpaper Preview screens.
- **Build Optimization**: Added `baseline-prof.txt` for improved startup performance and optimized WorkManager constraints.

### Bug Fixes
- **UI Glitches**: Resolved "empty state" flashes on Main and History screens by implementing proper loading states.
- **Onboarding**: Fixed layout issues where the "Start" button was overlapping with the system navigation bar.
- **Downloads**: Fixed the broken "Download Wallpaper" functionality in the History screen.
