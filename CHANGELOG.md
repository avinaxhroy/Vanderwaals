# Changelog

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
