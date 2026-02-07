<div align="center">
    <img style="display: block; border-radius: 9999px;" src="Vanderwaals_logo_black.png" width="500" alt="Vanderwaals Logo">
    <h1>Vanderwaals</h1>
    <p>
        <img src="https://img.shields.io/github/v/release/avinaxhroy/Vanderwaals?style=for-the-badge" alt="Release">
        <img src="https://img.shields.io/github/downloads/avinaxhroy/Vanderwaals/total?style=for-the-badge" alt="Downloads">
        <img src="https://img.shields.io/github/license/avinaxhroy/Vanderwaals?style=for-the-badge" alt="License">
    </p>
    <p><strong>AI-Powered Wallpaper App That Learns Your Aesthetic</strong></p>
    <p><i>Next-generation wallpaper personalization powered by MobileNetV4</i></p>
</div>

---

## ✨ What is Vanderwaals?

**Vanderwaals** is a cutting-edge Android wallpaper application that leverages state-of-the-art on-device machine learning to understand your unique aesthetic preferences and automatically curate wallpapers you'll love. Powered by **MobileNetV4-Conv-Small**, it delivers unparalleled personalization without compromising your privacy.

### 🎯 Key Highlights

- **🤖 Advanced ML Engine**: MobileNetV4-Conv-Small neural network with 1280-dimensional embeddings for superior aesthetic understanding
- **📚 Massive Curated Library**: 6,000+ wallpapers from GitHub collections + 5,400+ from Bing's photography archive
- **🔒 Privacy-First**: 100% offline ML processing, zero analytics, no data collection
- **⚡ Lightning Fast**: 9x faster wallpaper changes (~5s) with intelligent pre-caching and chunked processing
- **🎨 Revolutionary Glassmorphism**: True Apple-style liquid glass effect that Jetpack Compose can't natively achieve—engineered around platform limitations
- **📦 90% Smaller Downloads**: Quantized embeddings reduce manifest size from 60MB to ~6MB

---

## 🆕 What's New in v4.5.0

### MobileNetV4 Upgrade 🚀
- **1280D Embeddings**: Upgraded from MobileNetV3 (576D) to MobileNetV4-Conv-Small for 2.2x more detailed aesthetic understanding
- **80% Faster Inference**: Optimized architecture delivers faster on-device processing
- **Improved Accuracy**: Better recognition of artistic styles, moods, and compositional elements

### Performance Improvements ⚡
- **9x Faster Changes**: Wallpaper change time reduced from ~45s to ~5s through chunked processing
- **Memory Efficient**: Peak memory usage reduced from 190MB to ~40MB with batched operations
- **Pre-Caching**: Second wallpaper change is near-instant with background computation
- **90% Smaller Downloads**: Quantized embeddings reduce manifest from 60MB to ~6MB

### Enhanced Features ✨
- **Fast Initialization**: First like immediately shapes recommendations (no more waiting)
- **Instant Dislike**: Automatically applies new wallpaper when you dislike one
- **Flexible Intervals**: New 3h, 6h, 12h auto-change options
- **Daily Playlist**: 15 pre-selected wallpapers for "Every Unlock" mode
- **Smart Migration**: Seamless upgrade from v3.x with user-friendly dialog

### Reliability & Stability 🛡️
- **Samsung Optimized**: WakeLock and foreground service for One UI battery restrictions
- **Auto-Change Fix**: Works reliably even after app swipe (foreground service)
- **Android 15+ Boot Fix**: Resolved ForegroundServiceStartNotAllowedException crashes
- **Smart Crop 2.0**: Lossless PNG caching and resolution preservation

### UI Polish 🎨
- **True Liquid Glass**: Revolutionary glassmorphism technique that solves Compose's blur limitations
  - Pre-rendered backgrounds with chromatic aberration and barrel distortion
  - Slice-based rendering for pixel-perfect glass cards
  - 80px Gaussian blur + RGB separation for authentic Apple-style frosted glass
- **Enhanced Glassmorphism**: Multi-layer blur effects and glassmorphic cards throughout
- **Light Mode**: Full light theme support for all screens
- **Bing Selection UI**: Interactive "Radio Cards" for meaningful source selection
- **Enhanced Analytics**: Personalization insights and learning progress tracking

---

## Screenshots

<div align="center">
    <br/>
      <img style="display: block; border-radius: 9999px;" src="Screenshots/1.png" width="150" alt="Free">
      <img style="display: block; border-radius: 9999px;" src="Screenshots/2.png" width="150" alt="Beautiful Wallpapers">
      <img style="display: block; border-radius: 9999px;" src="Screenshots/3.png" width="150" alt="Upload Favourite">
      <img style="display: block; border-radius: 9999px;" src="Screenshots/4.png" width="150" alt="Auto-Change Wallpaper">
    <br/>
    <img style="display: block; border-radius: 9999px;" src="Screenshots/5.png" width="150" alt="Personalization Insight">
    <img style="display: block; border-radius: 9999px;" src="Screenshots/6.png" width="150" alt="Tap and Learn">
    <img style="display: block; border-radius: 9999px;" src="Screenshots/7.png" width="150" alt="More Algorithm">
    <br/>
</div>

## 📥 Download
<div align="center">
  <a href="https://github.com/avinaxhroy/Vanderwaals/releases">
    <img src="https://raw.githubusercontent.com/Kunzisoft/Github-badge/main/get-it-on-github.png"
         alt="Download from GitHub"
         height="80">
  </a>
  <a href="https://play.google.com/store/apps/details?id=me.avinas.vanderwaals">
    <img src="https://camo.githubusercontent.com/2ea3b1d605be783034a4a4cf364f24cdd29b52f60caf0892b03c3e53e85b91ce/68747470733a2f2f706c61792e676f6f676c652e636f6d2f696e746c2f656e5f75732f6261646765732f7374617469632f696d616765732f6261646765732f656e5f62616467655f7765625f67656e657269632e706e67"
         alt="Download from GitHub"
         height="80">
  </a>
</div>

### Requirements
- **Android 12.0 (API 31)** or higher
- **~50MB** storage space (app + cache)
- **Internet connection** for initial wallpaper sync (then works offline)

---

## 🚀 Features

### 🎨 Intelligent Personalization

#### Auto Mode
Start fresh with algorithm-selected wallpapers from curated collections. The app learns your taste as you provide feedback with lightning-fast initialization—your first like immediately shapes recommendations.

#### Personalize Mode
Upload one favorite wallpaper and instantly get 100+ similar matches. The advanced ML algorithm analyzes:
- **Deep Visual Features (70%)**: MobileNetV4 1280D embeddings capture artistic style, composition, and mood with 2.2x more detail than previous models
- **Color Palette (20%)**: LAB color space analysis for perceptually accurate matching
- **Category Affinity (10%)**: Learns your preference for categories (gruvbox, nord, nature, minimal, etc.)

### 🧠 Advanced Learning Algorithm

- **MobileNetV4-Conv-Small**: State-of-the-art 1280-dimensional embeddings for superior aesthetic understanding
- **Fast Initialization**: First like/download immediately sets preference baseline for instant personalization
- **Exponential Moving Average (EMA)**: Smoothly integrates new preferences without forgetting old ones
- **Implicit Feedback**: Learns from wallpaper duration (quick changes = dislike, long duration = like)
- **Temporal Diversity**: Prevents repetitive categories with intelligent recency penalties
- **Progressive Trust**: Balances original embeddings with learned preferences over time

### 📚 Rich Content Sources

- **GitHub Collections** (6,000+ wallpapers):
  - [dharmx/walls](https://github.com/dharmx/walls) - Curated aesthetic wallpapers
  - [D3Ext/aesthetic-wallpapers](https://github.com/D3Ext/aesthetic-wallpapers) - Minimalist designs
  - [makccr/wallpapers](https://github.com/makccr/wallpapers) - Linux ricing wallpapers
  - [Gingeh/wallpapers](https://github.com/Gingeh/wallpapers) - Anime aesthetics
  - [FrenzyExists/wallpapers](https://github.com/FrenzyExists/wallpapers) - Dark themes
  - Plus additional curated collections

- **Bing Photography** (5,400+ archive):
  - **Bing Lite**: ~1,000 wallpapers from last 3 years
  - **Bing Full**: Complete archive from 2009-present
  - Daily featured wallpapers in UHD quality
  - Professional photography from around the world
  - Full MobileNetV4 embedding support

- **Smart Auto-Sync**: Automatic weekly content updates from all sources with incremental updates
- **90% Smaller Downloads**: Quantized embeddings reduce manifest from 60MB to ~6MB

### ⚙️ Powerful Automation

- **Auto-Change Modes**:
  - Every device unlock (with Daily Playlist system—15 pre-selected wallpapers for instant changes)
  - Fixed intervals (1h, 3h, 6h, 12h, 24h)
  - Daily at custom time
  - Manual only

- **Apply To**:
  - Lock screen
  - Home screen
  - Both screens simultaneously

- **Smart Features**:
  - **Pre-Caching**: Background computation for near-instant second changes
  - **Instant Dislike**: Automatically applies new wallpaper when you dislike one
  - **Samsung Optimized**: Dedicated "Keep-Alive" service with WakeLock for One UI battery restrictions
  - **Reliable Background Processing**: Foreground service ensures consistent auto-change even after app swipe
  - **Smart Crop**: Intelligent image cropping using saliency detection to focus on interesting regions

### 📊 History & Analytics

- **Chronological Timeline**: View all applied wallpapers with timestamps
- **Quick Actions**: Like ❤️, Dislike 👎, or Download wallpapers directly from history
- **Smart Grouping**: Date-based sections (Today / Yesterday / Month Year)
- **Full-Screen Preview**: Zoomable image viewer for detailed inspection
- **Personalization Insights**: Track learning progress and category preferences

### 🎨 Modern UI/UX

- **True Liquid Glass Effect**: Revolutionary Apple-style glassmorphism that Jetpack Compose can't natively achieve
  - **Pre-rendered backgrounds** with chromatic aberration and edge distortion
  - **Slice-based rendering** - cards display perfectly aligned background slices
  - **Multi-layer processing**: Heavy Gaussian blur (80px) + chromatic RGB separation + barrel distortion
  - **Smart Launcher technique**: Process once, render infinitely - solves Compose's real-time blur limitations
  - **Opaque illusion**: Cards are solid surfaces that create the *perception* of transparency
- **Material 3 Design**: Latest Material Design 3 guidelines with modern components
- **Dynamic Colors**: Automatic color extraction from wallpapers (Android 12+)
- **Dark Theme**: AMOLED-optimized pure black theme with "Deep Ocean" aesthetics
- **Light Mode**: Full light mode support for all screens
- **Smooth Animations**: Polished transitions with Lottie animations and haptic feedback
- **Adaptive Layout**: Optimized for phones and tablets with responsive design


## 🏗️ Architecture

Vanderwaals follows **Clean Architecture** principles with **MVVM** pattern, leveraging modern Android development best practices.

```
📁 me.avinas.vanderwaals/
├── 📁 algorithm/              # Machine Learning Components
│   ├── EmbeddingExtractor         # MobileNetV4 TFLite wrapper (1280D)
│   ├── SimilarityCalculator       # Cosine similarity + color matching
│   ├── PreferenceUpdater          # EMA learning algorithm
│   ├── EnhancedImageAnalyzer      # Color, composition, mood analysis
│   ├── WallpaperScorer            # Multi-factor scoring system
│   └── SmartCrop                  # Intelligent image cropping
│
├── 📁 data/                   # Data Layer
│   ├── 📁 entity/                 # Room Database Entities
│   │   ├── WallpaperMetadata          # ID, URL, embedding (1280D), colors, category
│   │   ├── WallpaperHistory           # Applied wallpapers with feedback
│   │   ├── UserPreferences            # Learned preference vector (1280D)
│   │   ├── CategoryPreferences        # Category-level tracking
│   │   └── DownloadQueue              # Queued downloads
│   ├── 📁 dao/                    # Database Access Objects
│   │   ├── WallpaperMetadataDao
│   │   ├── WallpaperHistoryDao
│   │   ├── UserPreferencesDao
│   │   └── DownloadQueueDao
│   ├── 📁 repository/             # Repository Pattern
│   │   ├── WallpaperRepository
│   │   ├── PreferenceRepository
│   │   ├── ManifestRepository         # Multi-version manifest support (v1/v2/v3)
│   │   └── BingRepository
│   └── 📁 datastore/              # Preferences Storage
│       └── SettingsDataStore          # Version-aware settings with migration
│
├── 📁 domain/                 # Business Logic Layer
│   └── 📁 usecase/                # Use Cases (Single Responsibility)
│       ├── ExtractEmbeddingUseCase        # TFLite inference (MobileNetV4)
│       ├── FindSimilarWallpapersUseCase   # Similarity search with chunked processing
│       ├── SelectNextWallpaperUseCase     # Epsilon-greedy selection with pre-caching
│       ├── ProcessFeedbackUseCase         # Explicit feedback learning
│       ├── ProcessImplicitFeedbackUseCase # Duration-based learning
│       ├── UpdatePreferencesUseCase       # EMA preference updates
│       ├── GetRankedWallpapersUseCase     # Similarity ranking
│       ├── SyncWallpaperCatalogUseCase    # Incremental manifest sync
│       └── InitializePreferencesUseCase   # Cold start with fast initialization
│
├── 📁 network/                # Network Layer
│   ├── GitHubApiService           # GitHub raw file API
│   ├── BingApiService             # Bing wallpaper API
│   └── 📁 dto/                    # Data Transfer Objects
│       └── ManifestDto                # Multi-version manifest support
│
├── 📁 worker/                 # Background Processing
│   ├── WallpaperChangeWorker      # Auto wallpaper changes with foreground service
│   ├── CatalogSyncWorker          # Weekly manifest sync with version detection
│   ├── BatchDownloadWorker        # Background downloads
│   ├── ImplicitFeedbackWorker     # Duration tracking
│   ├── CleanupWorker              # Cache management
│   └── WorkScheduler              # WorkManager coordination
│
├── 📁 receiver/               # Broadcast Receivers
│   ├── DeviceUnlockReceiver       # Unlock-triggered changes
│   └── BootCompletedReceiver      # Restart work schedules
│
├── 📁 service/                # Foreground Services
│   └── WallpaperMonitorService    # Samsung-optimized with WakeLock
│
└── 📁 ui/                     # Presentation Layer (Jetpack Compose)
    ├── 📁 main/                   # Main screen with wallpaper preview
    ├── 📁 history/                # Feedback history & analytics
    ├── 📁 settings/               # App configuration
    ├── 📁 onboarding/             # First-time setup wizard
    └── 📁 components/             # Reusable UI components (Glassmorphic cards)
```

## 🛠️ Technologies

### Core Stack

<div>
<img src="https://img.shields.io/badge/kotlin-%230095D5.svg?style=for-the-badge&logo=kotlin&logoColor=white" height="35"/>
<img src="https://img.shields.io/badge/jetpack%20compose-%234285F4.svg?style=for-the-badge&logo=jetpackcompose&logoColor=white" height="35"/>
<img src="https://img.shields.io/badge/TensorFlow-%23FF6F00.svg?style=for-the-badge&logo=TensorFlow&logoColor=white" height="35"/>
<img src="https://img.shields.io/badge/Material%20Design-757575?style=for-the-badge&logo=material-design&logoColor=white" height="35"/>
</div>

### Key Libraries

#### Machine Learning
- **[TensorFlow Lite / LiteRT](https://www.tensorflow.org/lite)** - On-device ML inference with GPU acceleration
- **[MobileNetV4-Conv-Small](https://tfhub.dev/)** - State-of-the-art image embedding model (1280 dimensions, 80% faster than V3)

#### Android Jetpack
- **[Jetpack Compose](https://developer.android.com/compose)** - Modern declarative UI toolkit
- **[Material 3](https://m3.material.io/)** - Latest Material Design components
- **[Room](https://developer.android.com/training/data-storage/room)** - Type-safe SQLite database with migrations
- **[WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager)** - Reliable background task scheduling
- **[DataStore](https://developer.android.com/topic/libraries/architecture/datastore)** - Preferences storage with Flow support
- **[Navigation Compose](https://developer.android.com/jetpack/compose/navigation)** - Type-safe screen navigation
- **[Lifecycle](https://developer.android.com/topic/libraries/architecture/lifecycle)** - Lifecycle-aware components

#### Dependency Injection & Networking
- **[Dagger Hilt](https://dagger.dev/hilt/)** - Dependency injection framework
- **[Retrofit](https://square.github.io/retrofit/)** - Type-safe REST API client
- **[OkHttp](https://square.github.io/okhttp/)** - HTTP client with interceptors and connection pooling
- **[Kotlin Serialization](https://github.com/Kotlin/kotlinx.serialization)** - Type-safe JSON serialization

#### Image Loading & UI
- **[Landscapist Glide](https://github.com/skydoves/landscapist)** - Compose-native image loading with LRU cache
- **[Lottie Compose](https://github.com/airbnb/lottie)** - High-quality animations
- **[Accompanist](https://google.github.io/accompanist/)** - Jetpack Compose utilities
- **[Zoomable](https://github.com/usuiat/Zoomable)** - Pinch-to-zoom image viewer
- **[Android Palette](https://developer.android.com/develop/ui/views/graphics/palette-colors)** - Color extraction from images

#### Other
- **[Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)** - Asynchronous programming with Flow
- **[KSP](https://github.com/google/ksp)** - Kotlin Symbol Processing for annotation processing
- **[DocumentFileCompat](https://github.com/ItzNotABug/DocumentFileCompat)** - SAF utilities

---

## 🧪 How It Works

### 1️⃣ Image Embedding Extraction

```kotlin
// MobileNetV4-Conv-Small extracts 1280-dimensional feature vector
val embedding = embeddingExtractor.extract(bitmap)
// Example: [0.23, -0.45, 0.12, ..., 0.67] (1280 floats)
```

The **MobileNetV4** neural network captures:
- **Artistic style** (minimalist, detailed, abstract, anime, photography)
- **Composition** (rule of thirds, symmetry, balance, focal points)
- **Subject matter** (landscape, portrait, architecture, nature, abstract)
- **Mood & atmosphere** (warm, cool, energetic, calm, moody, bright)
- **Technical quality** (sharpness, contrast, color grading)

**Why MobileNetV4?**
- 2.2x more detailed features (1280D vs 576D)
- 80% faster inference than MobileNetV3
- Better color and texture understanding
- Superior generalization across art styles

### 2️⃣ Similarity Calculation

```kotlin
// Multi-factor scoring system
finalScore = (
    embeddingSimilarity * 0.70 +  // Deep visual features (cosine similarity)
    colorSimilarity * 0.20 +       // LAB color space matching
    categoryBonus * 0.10           // Category preference learning
) + temporalDiversityBoost +       // Prevent recent repetition
    explorationBonus               // Encourage discovery
```

**Cosine Similarity** for embeddings (optimized for 1280D):
```
similarity = (A · B) / (||A|| × ||B||)
```

**LAB Color Distance** (perceptually uniform):
```
distance = √[(L₁-L₂)² + (a₁-a₂)² + (b₁-b₂)²]
```

### 3️⃣ Preference Learning (EMA)

```kotlin
// Exponential Moving Average for smooth learning
newPreference = α × newFeedback + (1 - α) × oldPreference
```

**Learning rate** adapts based on feedback count:
- New users: α = 0.30 (learn faster)
- Experienced users: α = 0.15 (stable preferences)

**Fast Initialization (Auto Mode)**:
- **First Reaction Matters**: Unlike standard EMA, the *very first* like or download immediately sets the baseline preference vector (1280D), jump-starting personalization without waiting for multiple interactions

**Implicit feedback** from duration:
- < 5 minutes = Dislike (30% strength)
- > 24 hours = Like (30% strength)

**Enhanced with Momentum**:
- Tracks learning velocity for smoother adaptation
- Prevents oscillation in preferences
- Category-level tracking for fine-grained control

### 4️⃣ Adaptive Exploration

**Epsilon-greedy** strategy with decay:
```
exploration_rate = 0.20 → 0.05 (decays over 100 interactions)
```

With 20% initial probability, discover:
- New categories (< 3 times viewed)
- Under-explored content from different sources
- High-variance wallpapers (unique aesthetics)

### 5️⃣ Chunked Processing & Pre-Caching

**Efficient Memory Management**:
```kotlin
// Process 8,000+ wallpapers in batches of 1,000
chunkSize = 1,000
chunks = totalWallpapers / chunkSize
// Peak memory: ~40MB (down from 190MB)
```

**Pre-Caching System**:
- Background computation of next recommendation
- Second "Change Now" click is near-instant
- Generation counter prevents stale results
- 10-minute cache validity

### 6️⃣ Smart Cropping

```kotlin
// Saliency detection using edge + color contrast
val focalPoint = smartCrop.detectSalientRegion(bitmap)
val croppedBitmap = smartCrop.cropToAspectRatio(bitmap, focalPoint, targetRatio)
```

**Features**:
- **Resolution preservation** for high-quality sources (1.5x+ screen size)
- PNG output for lossless quality (no JPEG artifacts)
- Landscape-to-portrait optimization
- Focus on visually interesting regions

### 7️⃣ True Glassmorphism - The Apple-Style Liquid Glass Challenge

**The Problem**: Jetpack Compose doesn't support real-time background blur like iOS `UIVisualEffectView`. The `blur()` modifier only blurs the content itself, not what's behind it, making true "frosted glass" transparency impossible to achieve directly.

**The Solution**: We engineered a revolutionary **pre-processing + slice extraction** technique inspired by Smart Launcher:

```kotlin
// 1. Pre-process background ONCE with multiple effects
val liquidGlass = LiquidGlassProcessor.generateLiquidGlassBackground(
    source = wallpaperBitmap,
    config = GlassConfig(
        blurRadius = 80f,              // Heavy Gaussian blur
        chromaticOffset = 3f,          // RGB channel separation (prism effect)
        distortionStrength = 0.15f,    // Barrel distortion (light refraction)
        saturationBoost = 1.1f,        // Enhanced colors
        brightnessAdjust = 1.05f       // Subtle brightness lift
    )
)

// 2. Each card extracts its EXACT background slice
val cardSlice = LiquidGlassProcessor.extractSlice(
    processedBackground = liquidGlass,
    cardLeft = cardX,
    cardTop = cardY,
    cardWidth = cardWidth,
    cardHeight = cardHeight
)

// 3. Render as OPAQUE surface with glass overlays
GlassCard {
    Image(bitmap = cardSlice)  // Perfectly aligned slice
    GlassOverlay()             // Tint + highlights + borders
    CardContent()              // Your UI
}
```

**Why It Works**:
1. **Zero Real-Time Cost**: Background processed once, not per-frame
2. **Perfect Alignment**: Each card gets pixel-perfect slice at its position
3. **Perception of Transparency**: Opaque cards displaying matching backgrounds create illusion of "seeing through"
4. **Triple-Effect Processing**:
   - **Chromatic Aberration**: RGB channels offset by 3px for rainbow prism edges
   - **Edge Distortion**: Barrel distortion simulates light refraction through thick glass
   - **Heavy Blur**: 80px Gaussian blur via downscale-upscale for frosted base
5. **Cross-API Support**: Works on all Android versions (no RenderEffect dependency for blur)

**Result**: Authentic Apple-style liquid glass that rivals iOS's native capabilities, achieved through clever engineering around Jetpack Compose's limitations.

---

## 🔒 Privacy & Security

Vanderwaals is designed with **privacy as a core principle**:

✅ **100% Offline ML Processing**
- All TensorFlow Lite inference runs on-device
- No cloud API calls for ML operations
- Your preferences never leave your phone

✅ **Zero Analytics & Tracking**
- No Firebase, Google Analytics, or any tracking SDKs
- No crash reporting services
- No advertising SDKs

✅ **No Personal Data Collection**
- App doesn't request contacts, location, or camera
- Only required permissions: Internet (wallpaper downloads), Storage (applied wallpapers)

✅ **Open Source & Auditable**
- Full source code available under GPL-3.0
- No obfuscated code or hidden functionality
- Community-verifiable privacy claims

### Network Requests

The **only** external network calls:
1. **GitHub API** - Fetch wallpaper manifest (weekly sync)
2. **Bing API** - Download daily wallpaper (optional)
3. **Direct Image URLs** - Download wallpapers from curated sources

All network calls are **transparent and documented** in the source code.

## 🚧 Building from Source

### Prerequisites

- **Java 17** or later
- **Android Studio Ladybug (2024.2.1)** or later
- **Android SDK 36** (minimum SDK 30, target SDK 36)
- **Gradle 8.10.2** (via wrapper)

### Setup Steps

1. **Clone the repository**:
   ```bash
   git clone https://github.com/avinaxhroy/Vanderwaals.git
   cd Vanderwaals
   ```

2. **Open in Android Studio**:
   - `File → Open` → Select the `Vanderwaals` directory
   - Wait for Gradle sync to complete

3. **TensorFlow Lite Model** (included in assets):
   - Model location: `app/src/main/assets/models/mobilenet_v4_conv_small.tflite`
   - If missing, download from project releases or convert using provided scripts

4. **Build the app**:
   - **Debug**: Click `▶ Run` or `./gradlew assembleDebug`
   - **Release**: `Build → Generate Signed Bundle/APK` or `./gradlew assembleRelease`

5. **Run on device/emulator**:
   ```bash
   ./gradlew installDebug
   ```

### Build Variants

- **Debug**: Development build with debug logging and local manifest support
  - Includes debug screen for testing
  - BuildConfig flags for development
- **Release**: Optimized with ProGuard/R8, code shrinking, and resource optimization
  - Minified and obfuscated code
  - Smaller APK size (~15MB)

### Configuration

Edit `app/build.gradle.kts` to customize:
- `versionCode` & `versionName` - Current version: 4.5.0 (450)
- `MANIFEST_BASE_URL` - GitHub raw URL for wallpaper manifest
- Signing configuration in `local.properties`

### Model Conversion Scripts

```bash
# Convert MobileNetV4 to TFLite (requires Python 3.9+)
cd scripts
pip install -r requirements.txt
python convert_mobilenetv4_to_tflite.py

# Curate wallpapers with embeddings
python curate_wallpapers.py --test  # Quick test
python curate_wallpapers.py         # Full curation
```

---

## 🙏 Acknowledgments

### Credits

**Original Inspiration**: Massive thanks to **[Anthony La (@Anthonyy232)](https://github.com/Anthonyy232)** for creating [Paperize](https://github.com/Anthonyy232/Paperize), which inspired Vanderwaals' architecture and wallpaper infrastructure.

**Wallpaper Sources**:
- [dharmx/walls](https://github.com/dharmx/walls) - Aesthetic wallpapers
- [D3Ext/aesthetic-wallpapers](https://github.com/D3Ext/aesthetic-wallpapers) - Minimalist designs
- [makccr/wallpapers](https://github.com/makccr/wallpapers) - Linux ricing collections
- [Gingeh/wallpapers](https://github.com/Gingeh/wallpapers) - Anime aesthetics
- [FrenzyExists/wallpapers](https://github.com/FrenzyExists/wallpapers) - Dark themes
- [npanuhin/Bing-Wallpaper-Archive](https://github.com/npanuhin/Bing-Wallpaper-Archive) - Bing daily wallpapers

**ML Models & Technology**:
- [MobileNetV4](https://arxiv.org/abs/2404.10518) - Google Research
- [TensorFlow Lite / LiteRT](https://www.tensorflow.org/lite) - Google Brain Team
- [PyTorch](https://pytorch.org/) & [timm](https://github.com/huggingface/pytorch-image-models) - Model training and conversion


## 🤝 Contributing

We welcome contributions from the community! Together, we can make Vanderwaals even better.

**Before contributing**, please:
1. Read our [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines
2. Review and accept the [Contributor License Agreement (CLA)](CLA.md)
3. Understand our [dual licensing model](#-license)

Your contributions will be licensed under AGPL-3.0 and available for commercial licensing as well. This ensures the project remains sustainable while maintaining transparency.

### Areas for Improvement

#### 🤖 Algorithm Enhancements
- Experiment with larger models (MobileNetV4-Hybrid for 1536D embeddings)
- Implement CLIP embeddings for text+image understanding
- Add vision transformers (ViT) for semantic scene understanding
- Multi-modal learning (user descriptions + images)
- A/B testing framework for algorithm improvements
- Neural architecture search for optimal model selection

#### 📚 Data Sources & Curation
- Reddit wallpaper scraping (r/wallpapers, r/EarthPorn, r/wallpaper)
- Wallhaven API integration
- User-submitted collections with moderation
- Community voting system for quality control
- Automatic NSFW filtering
- Smart deduplication across all sources

#### ⚡ Performance Optimization
- TFLite GPU delegate for 5-10x faster inference
- Quantization-aware training for smaller models
- Database query optimization with composite indexes
- Parallel similarity calculations with WorkManager
- Incremental manifest updates (delta sync)
- WebP format for smaller downloads

#### 🎨 UI/UX Improvements
- Tablet and foldable device layouts
- Home screen widgets (upcoming wallpaper preview)
- Live wallpaper support with parallax effects
- Built-in wallpaper editor (crop, filters, color adjustments)
- Accessibility improvements (TalkBack, high contrast, large text)
- Multi-language support (i18n)
- Landscape mode optimization
- Hardware-accelerated glass effects (RenderScript/RenderEffect)
- Glass effect intensity controls

#### 🧪 Testing & Quality
- Unit tests for algorithm components (JUnit, Mockito)
- UI tests with Compose Test and Espresso
- Integration tests for use cases
- Performance benchmarks and profiling
- End-to-end testing with Firebase Test Lab
- Crash reporting integration (optional, privacy-respecting)



---

## 📜 License

### Dual Licensing Model

Vanderwaals uses a **dual licensing approach** to balance openness with sustainability:

#### 🔓 Open Source: AGPL-3.0

For the community, Vanderwaals is licensed under **AGPL-3.0** (GNU Affero General Public License v3.0):

- ✅ **Free to use** for personal, educational, and open-source projects
- ✅ **Study and modify** the source code
- ✅ **Contribute improvements** back to the project
- ✅ **Full transparency** - see exactly how the app works

**Requirements**:
- If you modify and distribute Vanderwaals, you **must share your source code** under AGPL-3.0
- If you run a modified version as a network service, users must have access to the source code
- Any derivative work must also be licensed under AGPL-3.0

```
Vanderwaals - AI-Powered Wallpaper App
Copyright (C) 2024-2025 Avinash/Confused Coconut

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program. If not, see <https://www.gnu.org/licenses/>.
```

📄 **Read the full license**: [LICENSE](LICENSE)

#### 💼 Commercial: Proprietary License

For commercial use, rebranding, or proprietary distribution, a **commercial license** is required.

**You need a commercial license if you want to**:
- Distribute a modified or rebranded version without sharing source code
- Use Vanderwaals in a proprietary application
- Offer Vanderwaals as a commercial service (SaaS, hosted, etc.)
- Monetize through ads, subscriptions, or paid features

**Benefits**:
- No source code disclosure required
- Technical support and updates
- Custom licensing terms
- Legal protection and indemnification

📄 **Learn more**: [COMMERCIAL_LICENSE.md](COMMERCIAL_LICENSE.md)

### 🏷️ Trademark Protection

The **"Vanderwaals" name and logo are trademarks** and are **not** covered by the AGPL-3.0 license.

- ✅ You **can** use the code under AGPL-3.0
- ✗ You **cannot** use the "Vanderwaals" name or logo in modified versions

If you create a fork or derivative, you **must rebrand** it with a different name and logo.

📄 **Trademark policy**: [TRADEMARK.md](TRADEMARK.md)

### 🤝 Contributor License Agreement

Contributors retain ownership but grant the project rights to use contributions under both licenses.

📄 **CLA details**: [CLA.md](CLA.md)

### Why This Approach?

This multi-layered protection strategy ensures:

- **🌟 Transparency**: Anyone can audit the code for security and privacy
- **🤝 Community Growth**: Open contributions improve the app for everyone
- **🛡️ Protection**: Prevents unauthorized rebranding and exploitation
- **💰 Sustainability**: Commercial licensing supports long-term development
- **✅ Trust**: Clear legal framework benefits users and contributors

---

### 📋 Summary

| Use Case | License Required | Source Code Disclosure |
|----------|------------------|------------------------|
| Personal use | AGPL-3.0 (free) | Not required |
| Open-source project | AGPL-3.0 (free) | Yes (if distributed) |
| Modified distribution | AGPL-3.0 (free) | **Yes (required)** |
| Commercial/proprietary | Commercial (paid) | No |
| Rebranding | Commercial (paid) + Trademark permission | No |

📧 **Questions?** Contact: hi@avinas.me

---

### Derivative Work Notice

Vanderwaals is a derivative work inspired by [Paperize](https://github.com/Anthonyy232/Paperize) by Anthony La (Apache-2.0 license). The original wallpaper infrastructure and UI architecture provided inspiration for Vanderwaals' design.

**Vanderwaals' unique contributions**:
- **MobileNetV4 Integration**: State-of-the-art 1280D embeddings (upgraded from MobileNetV3 576D)
- **Revolutionary Liquid Glass System**: Apple-style glassmorphism engineered around Jetpack Compose limitations
  - Pre-rendering with chromatic aberration + barrel distortion + heavy blur
  - Slice-based rendering for pixel-perfect glass cards
  - Smart Launcher-inspired technique solving real-time blur impossibility
- **Advanced ML Algorithm**: EMA learning with fast initialization and momentum-based adaptation
- **Multi-Source Architecture**: GitHub + Bing integration with unified embedding support
- **Chunked Processing**: Memory-efficient batch processing of 8,000+ wallpapers
- **Pre-Caching System**: Background computation for instant wallpaper changes
- **Quantized Manifests**: 90% smaller downloads with int8 quantization
- **Smart Cropping 2.0**: Enhanced saliency detection with resolution preservation
- **Premium UI Components**: Custom glassmorphic cards, sheets, and backgrounds
- **Version-Aware Migrations**: Seamless upgrades between manifest versions (v1/v2/v3)
- **Samsung Optimizations**: WakeLock and foreground service for One UI compatibility
- **Bing Wallpaper Support**: Full archive integration with MobileNetV4 embeddings
- **Category Learning**: Fine-grained preference tracking at category level
- **Enhanced Analytics**: Personalization insights and learning progress visualization

---

## �️ Roadmap

### Near-Term (v4.6 - v4.8)
- [ ] TFLite GPU delegate support for 5-10x faster inference
- [ ] Wallpaper categories filter and search
- [ ] User-uploaded wallpapers collection
- [ ] Export/import preferences for device migration
- [ ] Advanced statistics dashboard
- [ ] Wear OS companion app

### Mid-Term (v5.0+)
- [ ] Multi-language support (i18n)
- [ ] Live wallpaper with parallax effects
- [ ] Home screen widgets
- [ ] Wallpaper editor (crop, filters, adjustments)
- [ ] Community features (share collections, rate wallpapers)
- [ ] Cloud backup (optional, encrypted)

### Long-Term
- [ ] CLIP embeddings for text-based search
- [ ] Vision transformers (ViT) for semantic understanding
- [ ] Federated learning for privacy-preserving model improvement
- [ ] Plugin system for custom wallpaper sources
- [ ] Cross-platform (iOS, desktop)

---

## 📞 Support & Community

## 📞 Support & Community

### Having Issues?

- 🐛 **Bug Reports**: [GitHub Issues](https://github.com/avinaxhroy/Vanderwaals/issues)
- 💡 **Feature Requests**: [GitHub Discussions](https://github.com/avinaxhroy/Vanderwaals/discussions)
- ❓ **Q&A & Help**: [GitHub Discussions - Q&A](https://github.com/avinaxhroy/Vanderwaals/discussions/categories/q-a)
- 📧 **Email**: [hi@avinas.me](mailto:hi@avinas.me)

### Documentation

- 📖 **Setup Guide**: [SETUP.md](SETUP.md) - Development environment setup
- 📖 **API Documentation**: [API.md](API.md) - Detailed API reference
- 📖 **Contributing**: [CONTRIBUTING.md](CONTRIBUTING.md) - Contribution guidelines
- 📖 **Changelog**: [CHANGELOG.md](CHANGELOG.md) - Full version history
- 📖 **Curation Pipeline**: [CURATION_PIPELINE_README.md](CURATION_PIPELINE_README.md) - How wallpapers are curated

### Community Guidelines

When reporting issues or requesting features:
1. **Search first**: Check if similar issues/requests exist
2. **Be specific**: Include app version, Android version, device model
3. **Provide logs**: For crashes, attach logcat output
4. **Be respectful**: Follow our [Code of Conduct](CONTRIBUTING.md)

### FAQ

**Q: Why does the app need storage permission?**  
A: To save wallpapers you download and apply wallpapers to your device.

**Q: Does it work offline?**  
A: Yes! After initial sync, all ML processing is offline. Internet only needed for new wallpapers.

**Q: How much data does it use?**  
A: Initial manifest download is ~6MB. Each wallpaper is 200KB-2MB (average 500KB).

**Q: Can I use my own wallpapers?**  
A: Yes! Use "Personalize Mode" to upload your favorite and get similar recommendations.

**Q: Is my data private?**  
A: Absolutely! 100% offline ML, zero analytics, open-source code for verification.

---

## 📊 Performance Metrics

### Wallpaper Change Speed

| Version | Time | Improvement |
|---------|------|-------------|
| v3.x (Before optimization) | ~45 seconds | Baseline |
| v4.1.0 (Chunked processing) | ~5 seconds | **9x faster** |
| v4.1.0+ (Pre-cached) | <1 second | **45x faster** |

### Memory Usage

| Operation | v3.x | v4.1.0+ | Improvement |
|-----------|------|---------|-------------|
| Peak similarity calculation | 190 MB | 40 MB | **79% reduction** |
| Average app usage | 85 MB | 60 MB | **29% reduction** |

### ML Inference

| Model | Dimensions | Size | Inference Time (CPU) |
|-------|-----------|------|---------------------|
| MobileNetV3-Small | 576D | 2.5 MB | ~80ms |
| MobileNetV4-Conv-Small | 1280D | 4.3 MB | ~60ms |

### Manifest Size

| Format | Wallpapers | Size | Download Time (10 Mbps) |
|--------|-----------|------|------------------------|
| v1 (float32) | 6,000 | 60 MB | 48 seconds |
| v2 (quantized) | 6,000 | 6 MB | 5 seconds |
| v3 (MobileNetV4 quantized) | 6,000 | 8 MB | 6.4 seconds |

---

## ⭐ Show Your Support

If you find Vanderwaals helpful:

- ⭐ **Star this repository** on GitHub to show appreciation
- 🐛 **Report bugs** and suggest features via Issues
- 🤝 **Contribute** code, documentation, or translations
- 💬 **Share** with friends who love wallpapers and customization
- 📝 **Write a review** on Google Play Store
- 💰 **Consider commercial licensing** if using in commercial products

Every star, issue, and contribution helps make Vanderwaals better for everyone!

---

<div align="center">
    <p><strong>Made with ❤️ by Avinas / Confused Coconut</strong></p>
    <p>
        <a href="https://github.com/avinaxhroy">GitHub</a> •
        <a href="https://github.com/avinaxhroy/Vanderwaals/releases">Releases</a> •
        <a href="https://github.com/avinaxhroy/Vanderwaals/issues">Issues</a> •
        <a href="https://github.com/avinaxhroy/Vanderwaals/discussions">Discussions</a>
    </p>
    <p><i>Powered by MobileNetV4, TensorFlow Lite & Jetpack Compose</i></p>
    <p>
        <img src="https://img.shields.io/badge/Version-4.5.0-blue?style=flat-square" alt="Version">
        <img src="https://img.shields.io/badge/Android-12%2B-green?style=flat-square" alt="Android">
        <img src="https://img.shields.io/badge/License-AGPL--3.0-orange?style=flat-square" alt="License">
    </p>
</div>
