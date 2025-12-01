# Changelog

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
