# Changelog

## [4.1.0] - 2025-12-21

### Major Performance Improvements
- **9x Faster Wallpaper Changes**: Reduced wallpaper change time from ~45 seconds to ~5 seconds through comprehensive optimization.
  - **Chunked Processing**: Algorithm now processes 8,000+ wallpapers in memory-efficient batches of 1,000, reducing peak memory from 190MB to ~40MB.
  - **Fire-and-Forget Pre-computation**: Background cache warming no longer blocks the UI—worker returns immediately after applying wallpaper.
  - **Extended Cache Validity**: Pre-computed recommendations now valid for 10 minutes (up from 5), reducing unnecessary recomputation.

### Architecture Cleanup
- **Removed Redundant Pre-download System**: Eliminated `QueueNextWallpapersUseCase` which was duplicating work already handled by the Daily Playlist system for "Every Unlock" mode and the pre-cache system for other modes.

### Technical Details
- Pre-cache system with generation counter ensures stale results are safely discarded
- Daily Playlist system (15 wallpapers for "Every Unlock" mode) remains unchanged and fully functional
- Core recommendation algorithm quality unchanged—same scoring, same recommendations

## [4.0.0] - 2025-12-20

### Major UI Overhaul & Glassmorphism
- **Premium UI Refinements**: Extensive polish across the app to deepen the Glassmorphism design language.
    - **Bing Wallpaper Selection**: Redesigned source selection with interactive "Radio Cards" for a meaningful, premium selection experience between "Recent Hits" and "Global Archive".
    - **Enhanced Share Cards**: Completely revamped Share Card UI with balanced spacing, better typography, and glassmorphic overlays for a stunning social sharing experience.
    - **Visual Consistency**: Fixed icon visibility issues in Light Mode and refined status bar blending for a seamless edge-to-edge look.

### Optimized Wallpaper Catalog & Bing Integration
- **90% Smaller Downloads**: Manifest now uses quantized embeddings, reducing download size from 60+ MB to ~6 MB while maintaining full recommendation quality.
- **Bing Wallpapers Support**: Integrated Bing's daily and archive wallpapers (5,400+ high-quality images) with full neural network embedding support.
  - **Bing Lite**: ~1000+ wallpapers from last 3 years.
  - **Bing Full**: Complete archive from 2009-present.
  - **MobileNetV3 Embeddings**: Full ML support for all Bing wallpapers.
- **Smart Migration System**: Users upgrading from v3.x automatically see a migration dialog prompting them to update their local catalog. The dialog:
  - Explains the benefits (faster sync, smaller data usage)
  - Shows real-time progress during update
  - Allows "Update Now" or "Later" options
- **Seamless Transition**: Existing users upgrading from Play Store will experience zero app breakage. Old catalog continues working until they choose to update.
- **Version Tracking**: App now tracks version codes to intelligently detect upgrades and trigger appropriate migrations for future updates.

### New Auto-Change Frequency Options
- **Flexible Scheduling**: Added 3-hour, 6-hour, and 12-hour auto-change intervals for users who want less frequent wallpaper changes.
- **Smart History Sizing**: Each new interval has optimized history sizes (16 for 3h, 12 for 6h, 10 for 12h) to prevent seeing the same wallpaper too soon.
- **Full System Integration**: New intervals work seamlessly with alarm scheduling, boot rescheduling, pre-caching, and all existing auto-change infrastructure.

### Performance & Stability
- **Instant Wallpaper Changes**: Introduced pre-caching system that computes the next wallpaper recommendation in the background. Second "Change Now" click is now near-instant instead of 3-5 seconds.
- **Optimized Feedback Processing**: Like/dislike actions now use indexed database lookups instead of loading the entire catalog, dramatically reducing response time.
- **Instant Dislike Response**: When you dislike a wallpaper, it's immediately replaced with a new one—no need to manually tap "Change Now".
- **Reduced Log Verbosity**: Eliminated per-wallpaper debug logging that was flooding logcat with thousands of entries per selection.
- **Smart Crop Optimization**: Fixed redundant processing on the Home Screen, ensuring instant wallpaper loading without quality loss.
- **Build System Modernization**: Complete overhaul of the build system. Migrated to KSP (Kotlin Symbol Processing), fixed all Gradle warnings, and upgraded dependencies for stability.
- **Download Reliability**: Fixed "Empty File" errors in `SegmentedDownloader` to ensure 100% reliable wallpaper downloads.

### Technical Improvements
- **Backward Compatibility**: Full support for both v1 (legacy float32) and v2 (quantized int8) manifest formats, ensuring no disruption during the Play Store rollout period.
- **Manifest Versioning**: Infrastructure for versioned manifests enables smooth future catalog updates without breaking existing installations.
- **Multi-Source Architecture**: Enhanced codebase to support multiple wallpaper sources (GitHub repos + Bing) with unified embedding-based recommendations.

## [3.8.9] - 2025-12-17

### Critical Bug Fix
- **Auto-Change Reliability**: Fixed issue where auto-change wallpaper would not work when the app was swiped away from the recent apps menu. The fix uses a foreground service (inspired by Paperize) instead of WorkManager to ensure reliable execution even when the app's process was killed.
- **Android 15+ Boot Crash**: Fixed `ForegroundServiceStartNotAllowedException` crash on Android 15+ (API 35+) that occurred when the "Every Unlock" service tried to start after device boot. The fix uses a deferred service start pattern with WorkManager, ensuring the service starts safely after the first user unlock.

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
