<div align="center">
    <img style="display: block; border-radius: 9999px;" src="Vanderwaals_logo_black.png" width="500" alt="Vanderwaals Logo">
    <h1>Vanderwaals</h1>
    <p>
        <img src="https://img.shields.io/github/v/release/avinaxhroy/Vanderwaals?style=for-the-badge" alt="Release">
        <img src="https://img.shields.io/github/downloads/avinaxhroy/Vanderwaals/total?style=for-the-badge" alt="Downloads">
        <img src="https://img.shields.io/github/license/avinaxhroy/Vanderwaals?style=for-the-badge" alt="License">
    </p>
    <p><strong>On-device wallpaper personalization powered by MobileNetV4</strong></p>
</div>

---

## What is Vanderwaals?

Vanderwaals is an Android wallpaper application that uses on-device machine learning to personalize wallpaper recommendations based on visual taste. It runs MobileNetV4-Conv-Small feature extraction locally on your phone without sending image or preference data to external servers.

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

## Download

<div align="center">
  <a href="https://github.com/avinaxhroy/Vanderwaals/releases">
    <img src="https://raw.githubusercontent.com/Kunzisoft/Github-badge/main/get-it-on-github.png"
         alt="Download from GitHub"
         height="80">
  </a>
  <a href="https://play.google.com/store/apps/details?id=me.avinas.vanderwaals">
    <img src="https://camo.githubusercontent.com/2ea3b1d605be783034a4a4cf364f24cdd29b52f60caf0892b03c3e53e85b91ce/68747470733a2f2f706c61792e676f6f676c652e636f6d2f696e746c2f656e5f75732f6261646765732f7374617469632f696d616765732f6261646765732f656e5f62616467655f7765625f67656e657269632e706e67"
         alt="Get it on Google Play"
         height="80">
  </a>
</div>

### System requirements
- Android 11.0 (API 30) or higher
- 50 MB storage space for application assets and cache
- Internet connection for catalog synchronization and image downloads

---

## Core features

- **On-device machine learning**: MobileNetV4-Conv-Small extracts 1280-dimensional embeddings to match aesthetic styles locally.
- **Curated collections**: Over 6,000 wallpapers from community repositories and 5,400+ daily Bing photography wallpapers.
- **Privacy-first design**: 100% offline inference, zero tracking, and no telemetry SDKs.
- **Quantized manifests**: Embeddings are quantized to int8, reducing manifest payload size from 60 MB to ~8 MB.
- **Automated changes**: Schedules wallpaper rotation on device unlock, fixed intervals (1h to 24h), or daily at a set time.
- **Liquid glass UI**: Frosted glass interface built using background slice pre-rendering to overcome Jetpack Compose layout blur constraints.

---

## How it works

### 1. Feature extraction
Vanderwaals uses MobileNetV4-Conv-Small via TensorFlow Lite to convert images into 1280-dimensional feature vectors capturing composition, artistic style, color distribution, and visual tone.

```kotlin
val embedding = embeddingExtractor.extractEmbedding(bitmap)
```

### 2. Ranking
Every wallpaper selection goes through a single calibrated ranking path (`RankingEngine`). Six components, each normalized to [0, 1] with 0.5 meaning "no data", combine into a weighted score; the weights sum to exactly 1:

| Component | Weight | Signal |
|-----------|--------|--------|
| Taste | 0.60 | Multi-anchor embedding similarity |
| Category | 0.12 | Bayesian like-rate per category |
| Quality | 0.10 | Resolution, aspect ratio, tonal balance, aesthetic score |
| Color | 0.06 | Perceptual palette match (CIELAB ΔE) to liked wallpapers |
| Semantic | 0.06 | Mood/style tag affinity |
| Time of day | 0.06 | Brightness fit for the current hour |

```
score = (weighted taste/category/quality/color/semantic/time sum)
        × saturation × dislikeSuppression    (multiplicative, bounded)
        + explorationBonus                   (UCB-style, decays with feedback)
```

The top 50 candidates are re-ranked with Maximal Marginal Relevance for embedding-space diversity, and the final pick is drawn from a temperature-scaled softmax so the rotation never becomes deterministic. Matching an uploaded reference image (onboarding) uses a separate composite scorer (`SimilarityCalculator`) over the same embeddings.

### 3. Preference learning
Likes and dislikes become timestamped anchors in a multi-anchor taste memory (`TasteMemory`), replacing the single exponential-moving-average vector. A wallpaper scores well if it resembles *any* recent liked anchor, so distinct tastes coexist instead of averaging into a direction that matches neither. Anchor influence decays with a 14-day half-life for likes and 7 days for dislikes; dislikes act as suppression memory only and never steer the positive direction.

Anchor ages are measured relative to the newest explicit feedback, not the wall clock, so a user who configures taste once at onboarding keeps it indefinitely while new feedback still displaces old evidence. Implicit signals (how long a manually applied wallpaper stayed up, thresholded relative to the change interval) are recorded at 0.4 strength and never advance the reference clock.

### 4. Background blur implementation
Jetpack Compose does not support dynamic background blur behind layout cards across all Android API levels. Vanderwaals generates frosted glass cards by pre-processing the active wallpaper once with Gaussian blur, chromatic aberration, and edge distortion, then cropping matching positional slices for each UI card.

---

## Codebase architecture

The project follows Clean Architecture and MVVM patterns.

```
me.avinas.vanderwaals/
├── algorithm/              # Machine learning, ranking, taste memory, smart crop
│   ├── EmbeddingExtractor.kt
│   ├── RankingEngine.kt
│   ├── TasteMemory.kt
│   ├── RecommenderConfig.kt
│   ├── SimilarityCalculator.kt
│   └── SmartCrop.kt
├── data/                   # Room database, data sources, and repositories
│   ├── entity/
│   ├── dao/
│   └── repository/
├── domain/                 # Use cases for recommendations and catalog sync
│   └── usecase/
├── network/                # GitHub and Bing API interfaces & DTOs
├── worker/                 # WorkManager background tasks for updates and rotation
├── service/                # Foreground service for unlock tracking
└── ui/                     # Jetpack Compose UI components, themes, and screens
```

---

## Tech stack

- **Language & UI**: Kotlin, Jetpack Compose, Material 3
- **Machine Learning**: TensorFlow Lite / LiteRT, MobileNetV4-Conv-Small (1280D)
- **Database & Storage**: Room, DataStore Preferences
- **Dependency Injection**: Dagger Hilt
- **Asynchronous Work**: Kotlin Coroutines, Flow, WorkManager
- **Networking & Media**: Retrofit, OkHttp, Landscapist Glide

---

## Building from source

### Prerequisites
- Java 17 JDK
- Android Studio Ladybug (2024.2.1) or higher
- Android SDK 36 (Minimum SDK 30, Target SDK 36)

### Build steps

1. Clone the repository:
   ```bash
   git clone https://github.com/avinaxhroy/Vanderwaals.git
   cd Vanderwaals
   ```

2. Open the project in Android Studio and wait for Gradle sync to finish.

3. Verify model placement:
   Ensure `mobilenet_v4_conv_small.tflite` exists in `app/src/main/assets/models/`.

4. Build the debug APK:
   ```bash
   ./gradlew assembleDebug
   ```

5. Install on a connected device:
   ```bash
   ./gradlew installDebug
   ```

---

## Privacy and security

- **100% On-device processing**: Inference runs locally using TensorFlow Lite. Your preference vector remains on your device.
- **No telemetry**: The app contains zero analytics, tracking scripts, or ad networks.
- **Minimal permissions**: Requests network access for wallpaper downloads and storage permissions for applying wallpapers.

---

## Performance benchmarks

### Wallpaper change latency

| Operation | Time |
|-----------|------|
| Standard change | ~5 seconds |
| Pre-cached recommendation | <1 second |

### Manifest payload size

| Version | Format | Vector dimensions | Size |
|---------|--------|-------------------|------|
| v1 | Unquantized float32 | 576D | 60 MB |
| v3 | Quantized int8 MobileNetV4 | 1280D | ~8 MB |

### Embedding storage (Room)

The ranking path reads the full catalog plus all taste anchors on every selection, so embedding deserialization is on the hot path.

| Database version | Format | Full-catalog deserialize | Per embedding | Catalog total |
|------------------|--------|--------------------------|---------------|---------------|
| 12 | JSON text | ~690 ms | ~14 KB | ~55 MB |
| 13 | float32 BLOB | ~4 ms | 5 KB | ~18 MB |

Values are stored as raw IEEE-754 bits, so migration from v12 is bit-exact and ranking results are unchanged.

---

## License

### Dual licensing

Vanderwaals is dual-licensed:

- **Open Source (AGPL-3.0)**: Free for personal use and open-source projects. Source modifications and derived distributions must remain under AGPL-3.0. See [LICENSE](LICENSE).
- **Commercial License**: Required for commercial distribution, closed-source derivative work, or monetization. See [COMMERCIAL_LICENSE.md](COMMERCIAL_LICENSE.md).

### Trademark notice

The "Vanderwaals" name and brand assets are protected trademarks. Derived builds distributed publicly must be rebranded under a different name and logo. See [TRADEMARK.md](TRADEMARK.md).

---

## Support and community

- **Bug reports**: [GitHub Issues](https://github.com/avinaxhroy/Vanderwaals/issues)
- **Discussions**: [GitHub Discussions](https://github.com/avinaxhroy/Vanderwaals/discussions)
- **Setup guide**: [SETUP.md](SETUP.md)
- **API reference**: [API.md](API.md)
- **Changelog**: [CHANGELOG.md](CHANGELOG.md)

---

<div align="center">
    <p>Maintained by Avinas / Confused Coconut</p>
    <p>
        <a href="https://github.com/avinaxhroy">GitHub</a> •
        <a href="https://github.com/avinaxhroy/Vanderwaals/releases">Releases</a> •
        <a href="https://github.com/avinaxhroy/Vanderwaals/issues">Issues</a>
    </p>
    <p>Version 4.6.3 • Android 11+ • AGPL-3.0 / Commercial</p>
</div>
