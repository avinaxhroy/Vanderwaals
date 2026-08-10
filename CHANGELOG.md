# Changelog

All notable changes to Vanderwaals are documented in this file.

## [4.6.3] - 2026-08-10

### Fixed
- Resolved algorithm test discrepancies by testing production `PreferenceUpdater` instead of test-only EMA helpers.
- Fixed loss of composition scores on PNG wallpapers by probing for `.png` extensions in file resolvers.
- Renormalized similarity calculation weights so exact matches score 1.0.
- Replaced non-linear RGB-to-LAB conversions in image analyzers with standard D65 LAB color space calculations.
- Fixed negative popularity scores resulting from standard signed modulo operations.

## [4.1.0] - 2025-12-21

### Performance
- Reduced wallpaper change latency from ~45 seconds to ~5 seconds by computing similarity in batches of 1,000 wallpapers.
- Asynchronous cache pre-computation no longer blocks background worker execution.
- Increased pre-computed recommendation cache validity duration to 10 minutes.

### Architecture
- Removed redundant `QueueNextWallpapersUseCase` system in favor of the unified pre-cache and daily playlist handlers.

## [4.0.0] - 2025-12-20

### Added
- Integrated Bing daily and archive collections (5,400+ wallpapers) with MobileNetV4 embedding support.
- Added 3-hour, 6-hour, and 12-hour auto-change scheduling intervals.
- Added quantized int8 embedding support, reducing manifest download sizes from 60 MB to ~8 MB.

### Fixed
- Instant wallpaper change now applies immediately upon disliking a wallpaper.
- Database feedback updates now use indexed lookups rather than full table scans.

## [3.8.9] - 2025-12-17

### Fixed
- Fixed auto-change reliability when swiped from recent apps by switching from WorkManager to a persistent foreground service.
- Resolved `ForegroundServiceStartNotAllowedException` crashes on boot under Android 15 (API 35).

## [3.8.5] - 2025-12-16

### Fixed
- Saved cropped wallpaper cache as PNG to prevent JPEG compression artifacts.
- Preserved resolution on wallpapers exceeding 1.5x screen resolution.

## [3.8.0] - 2025-12-16

### Performance
- Added Service-level WakeLock handling for Samsung One UI battery optimization constraints.

### Algorithm
- First wallpaper preference rating now immediately sets the initial preference vector.

## [3.0.0] - 2025-11-23

### Added
- Implemented background slice frosted glass card UI.
- Migrated TFLite engine to LiteRT for Android 15 page size compatibility.
- Added Light Mode theme support across all screens.
