# Vanderwaals Development Setup Guide

Complete guide to setting up Vanderwaals for development, building, and testing.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Environment Setup](#environment-setup)
- [Building the Project](#building-the-project)
- [TensorFlow Lite Model](#tensorflow-lite-model)
- [Configuration](#configuration)
- [Running & Testing](#running--testing)
- [Troubleshooting](#troubleshooting)

---

## Prerequisites

### Required Software

| Software | Version | Purpose |
|----------|---------|---------|
| **Java JDK** | 17 or later | Gradle and Android build |
| **Android Studio** | Ladybug (2024.2.1+) | IDE and Android SDK |
| **Android SDK** | API 31+ (min), API 36 (target) | Android platform |
| **Git** | Latest | Version control |

### System Requirements

- **OS**: macOS, Windows, or Linux
- **RAM**: 8GB minimum, 16GB recommended
- **Storage**: 10GB free space (SDK + project + caches)
- **Network**: Stable internet for dependencies and wallpaper sync

---

## Environment Setup

### 1. Install Java 17

#### macOS (using Homebrew)
```bash
brew install openjdk@17
echo 'export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
java -version  # Verify: openjdk version "17.x.x"
```

#### Linux (Ubuntu/Debian)
```bash
sudo apt update
sudo apt install openjdk-17-jdk
java -version
```

#### Windows
Download from [Adoptium](https://adoptium.net/) and install OpenJDK 17.

### 2. Install Android Studio

1. Download from [developer.android.com](https://developer.android.com/studio)
2. Install with default settings
3. Run Android Studio Setup Wizard:
   - Install Android SDK
   - Install Android SDK Platform-Tools
   - Install Android Emulator

### 3. Configure Android SDK

Open Android Studio → Settings (⌘,) → Appearance & Behavior → System Settings → Android SDK

**Required SDK Platforms**:
- ✅ Android 15.0 (API 36) - Target SDK
- ✅ Android 12.0 (API 31) - Minimum SDK

**Required SDK Tools**:
- ✅ Android SDK Build-Tools 35.0.1
- ✅ Android SDK Platform-Tools
- ✅ Android SDK Tools
- ✅ Android Emulator

### 4. Set Environment Variables

#### macOS/Linux (~/.zshrc or ~/.bashrc)
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk  # macOS
# export ANDROID_HOME=$HOME/Android/Sdk       # Linux
export PATH=$PATH:$ANDROID_HOME/emulator
export PATH=$PATH:$ANDROID_HOME/platform-tools
export PATH=$PATH:$ANDROID_HOME/tools
export PATH=$PATH:$ANDROID_HOME/tools/bin
```

#### Windows (System Environment Variables)
```
ANDROID_HOME=C:\Users\<YourUsername>\AppData\Local\Android\Sdk
Path=%Path%;%ANDROID_HOME%\platform-tools;%ANDROID_HOME%\tools
```

Verify:
```bash
adb version  # Android Debug Bridge
```

---

## Building the Project

### 1. Clone the Repository

```bash
git clone https://github.com/avinaxhroy/Vanderwaals.git
cd Vanderwaals/Vanderwaals
```

**Project Structure**:
```
Vanderwaals/
├── Vanderwaals/           # Main Android project
│   ├── app/               # Application module
│   ├── build.gradle.kts   # Project build config
│   └── settings.gradle.kts
├── scripts/               # Python curation scripts
├── docs/                  # Documentation
└── README.md
```

### 2. Open in Android Studio

1. **Open Project**:
   - File → Open
   - Select `Vanderwaals/Vanderwaals` directory
   - Click "Trust Project" if prompted

2. **Gradle Sync**:
   - Android Studio automatically syncs Gradle
   - Wait for "Gradle build finished" notification
   - If errors, see [Troubleshooting](#troubleshooting)

### 3. Build the App

#### Using Android Studio
- **Debug Build**: Build → Make Project (⌘F9)
- **Release Build**: Build → Generate Signed Bundle/APK

#### Using Command Line
```bash
# Debug APK
./gradlew assembleDebug

# Release APK (unsigned)
./gradlew assembleRelease

# Install debug APK to connected device
./gradlew installDebug

# Clean build
./gradlew clean build
```

**Output Location**:
```
app/build/outputs/apk/debug/vanderwaals-v2.7.0.apk
app/build/outputs/apk/release/vanderwaals-v2.7.0.apk
```

---

## TensorFlow Lite Model

### Download MobileNetV4-Conv-Small Model

The app requires a TensorFlow Lite model for image embeddings (1280D MobileNetV4-Conv-Small).

#### Option 1: Convert Using Colab (Recommended)

1. Open [Google Colab](https://colab.research.google.com/)
2. Copy contents of `scripts/colab_one_cell.py` into a cell
3. Run the cell — it installs dependencies, converts, and verifies
4. Download the generated `mobilenet_v4_conv_small.tflite`
5. Place at: `app/src/main/assets/models/mobilenet_v4_conv_small.tflite`

#### Option 2: Convert Locally

```bash
pip install torch timm onnx onnx2tf tensorflow
python3 scripts/convert_mobilenetv4_to_tflite.py
```

This script:
- Downloads MobileNetV4-Conv-Small from timm (PyTorch)
- Exports to ONNX, then converts to TFLite via onnx2tf
- Places in `app/src/main/assets/models/`
- Verifies 1280D output dimension

### Verify Model

```bash
python3 scripts/test_tflite.py
```

**Expected Output**:
```
✅ Model exists: app/src/main/assets/models/mobilenet_v4_conv_small.tflite
✅ Model loaded successfully
✅ Input shape: [1, 224, 224, 3]
✅ Output shape: [1, 1280]
```

---

## Configuration

### Build Configuration

Edit `app/build.gradle.kts`:

```kotlin
android {
    namespace = "me.avinas.vanderwaals"
    compileSdk = 36
    
    defaultConfig {
        applicationId = "me.avinas.vanderwaals"
        minSdk = 31
        targetSdk = 36
        versionCode = 270
        versionName = "2.7.0"
    }
}
```

### Manifest URL Configuration

For **production**, wallpaper manifest is fetched from GitHub:

```kotlin
// app/build.gradle.kts
buildTypes {
    release {
        buildConfigField("String", "MANIFEST_BASE_URL", 
            "\"https://raw.githubusercontent.com/avinaxhroy/Vanderwaals/main/\"")
    }
}
```

For **local testing**, use local manifest:

```kotlin
// app/build.gradle.kts
buildTypes {
    debug {
        buildConfigField("boolean", "USE_LOCAL_MANIFEST", "true")
    }
}
```

Then place manifest at: `app/src/main/assets/sample-manifest.json`

### Signing Configuration (Release Builds)

Create `local.properties` (gitignored):
```properties
SIGNING_KEYSTORE_PATH=/path/to/keystore.jks
SIGNING_STORE_PASSWORD=your_store_password
SIGNING_KEY_ALIAS=your_key_alias
SIGNING_KEY_PASSWORD=your_key_password
```

Or use environment variables (for CI/CD):
```bash
export SIGNING_KEYSTORE_PATH=/path/to/keystore.jks
export SIGNING_STORE_PASSWORD=your_store_password
export SIGNING_KEY_ALIAS=your_key_alias
export SIGNING_KEY_PASSWORD=your_key_password
```

---

## Running & Testing

### Run on Physical Device

1. **Enable Developer Options**:
   - Settings → About Phone → Tap "Build Number" 7 times

2. **Enable USB Debugging**:
   - Settings → Developer Options → USB Debugging → ON

3. **Connect Device**:
   ```bash
   adb devices  # Verify device is detected
   ```

4. **Run App**:
   - Android Studio: Run → Run 'app' (⇧F10)
   - Command line: `./gradlew installDebug`

### Run on Emulator

1. **Create Emulator**:
   - Tools → Device Manager → Create Virtual Device
   - Select: Pixel 8 Pro, API 36 (Android 15)
   - Download system image if needed

2. **Start Emulator**:
   ```bash
   emulator -avd Pixel_8_Pro_API_36
   ```

3. **Run App**:
   ```bash
   ./gradlew installDebug
   ```

### Testing Features

#### Test ML Inference
```bash
adb logcat -s ExtractEmbedding:V
# Upload a wallpaper in the app
# Check logs for: "Embedding extracted: [0.23, -0.45, ...]"
```

#### Test Similarity Calculation
```bash
adb logcat -s SelectNextWallpaper:V
# Tap "Change Now" button
# Check logs for similarity scores
```

#### Test Implicit Feedback
```bash
adb logcat -s ProcessImplicitFeedback:V
# Change wallpaper manually after 2 minutes
# Check logs for: "Implicit DISLIKE detected"
```

#### Test Smart Crop
```bash
adb logcat -s SmartCrop:V
# Apply any wallpaper
# Check logs for focal point coordinates
```

---

## Troubleshooting

### Gradle Sync Failed

**Error**: "Could not resolve all dependencies"

**Solution**:
```bash
./gradlew clean
./gradlew --refresh-dependencies
```

**Error**: "SDK location not found"

**Solution**: Create `local.properties`:
```properties
sdk.dir=/path/to/Android/Sdk
```

### Build Errors

**Error**: "Execution failed for task ':app:kspDebugKotlin'"

**Solution**: Clean and rebuild:
```bash
./gradlew clean
./gradlew build
```

**Error**: "TensorFlow Lite model not found"

**Solution**: Verify model exists at correct location:
```bash
ls -lh app/src/main/assets/models/mobilenet_v4_conv_small.tflite
```

### Runtime Errors

**Error**: App crashes on startup

**Solution**: Check logcat:
```bash
adb logcat | grep -i "AndroidRuntime\|FATAL"
```

**Error**: "Failed to load TFLite model"

**Solution**: 
1. Verify model exists in APK:
   ```bash
   unzip -l app/build/outputs/apk/debug/*.apk | grep mobilenet
   ```
2. Rebuild clean: `./gradlew clean assembleDebug`

**Error**: "Manifest sync failed"

**Solution**: 
1. Check internet connection
2. Verify GitHub URL in `build.gradle.kts`
3. Use local manifest for testing

### Performance Issues

**Slow inference (~500ms+)**:
- Enable GPU delegate in `EmbeddingExtractor.kt`:
  ```kotlin
  val options = Interpreter.Options()
  options.addDelegate(GpuDelegate())
  ```

**High memory usage**:
- Reduce LRU cache size in `AppModule.kt`:
  ```kotlin
  Glide.get(context).setMemoryCategory(MemoryCategory.LOW)
  ```

---

## Development Workflow

### Recommended Workflow

1. **Feature Branch**:
   ```bash
   git checkout -b feature/your-feature-name
   ```

2. **Make Changes** → **Test** → **Commit**:
   ```bash
   ./gradlew test  # Run unit tests
   ./gradlew installDebug  # Test on device
   git add .
   git commit -m "feat: your feature description"
   ```

3. **Push & PR**:
   ```bash
   git push origin feature/your-feature-name
   # Create Pull Request on GitHub
   ```

### Code Style

- **Kotlin Style**: Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- **Format Code**: ⌥⌘L (Android Studio)
- **Organize Imports**: ⌃⌥O (Android Studio)

### Logging

Use Android's `Log` class:
```kotlin
Log.d("TagName", "Debug message")
Log.i("TagName", "Info message")
Log.w("TagName", "Warning message")
Log.e("TagName", "Error message", exception)
```

Filter logs in logcat:
```bash
adb logcat -s TagName:V  # Verbose
adb logcat -s TagName:D  # Debug
adb logcat -s TagName:I  # Info
```

---

## Useful Commands

### ADB Commands
```bash
# List connected devices
adb devices

# Install APK
adb install app/build/outputs/apk/debug/vanderwaals-v2.7.0.apk

# Uninstall app
adb uninstall me.avinas.vanderwaals

# Clear app data
adb shell pm clear me.avinas.vanderwaals

# View logs
adb logcat

# Take screenshot
adb shell screencap /sdcard/screen.png
adb pull /sdcard/screen.png

# Record screen
adb shell screenrecord /sdcard/demo.mp4
```

### Gradle Commands
```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Install on connected device
./gradlew installDebug

# Run unit tests
./gradlew test

# Clean build files
./gradlew clean

# View dependencies
./gradlew app:dependencies

# Build with stacktrace
./gradlew assembleDebug --stacktrace
```

### Git Commands
```bash
# View status
git status

# View diff
git diff

# Commit changes
git add .
git commit -m "message"

# Push to remote
git push origin main

# Pull latest changes
git pull origin main

# View commit history
git log --oneline
```

---

## IDE Setup

### Recommended Plugins

- **Kotlin** (pre-installed)
- **Android** (pre-installed)
- **Rainbow Brackets** - Color-coded bracket matching
- **Git ToolBox** - Enhanced Git integration
- **Key Promoter X** - Learn keyboard shortcuts

### Android Studio Settings

- **Editor → Code Style → Kotlin**: Set to 4 spaces indentation
- **Editor → Inspections → Kotlin**: Enable all warnings
- **Build, Execution, Deployment → Compiler**: Enable parallel compilation
- **Appearance & Behavior → System Settings**: Enable auto-import

---

## Resources

### Documentation
- [Android Developer Docs](https://developer.android.com/)
- [Jetpack Compose Docs](https://developer.android.com/jetpack/compose)
- [TensorFlow Lite Docs](https://www.tensorflow.org/lite)
- [Kotlin Docs](https://kotlinlang.org/docs/home.html)

### Community
- [Vanderwaals GitHub Issues](https://github.com/avinaxhroy/Vanderwaals/issues)
- [Vanderwaals Discussions](https://github.com/avinaxhroy/Vanderwaals/discussions)
- [Android Development Discord](https://discord.gg/android)
- [r/androiddev](https://reddit.com/r/androiddev)

---

## Next Steps

1. ✅ Complete environment setup
2. ✅ Build debug APK
3. ✅ Run on device/emulator
4. 📖 Read [API.md](API.md) for architecture details
5. 📖 Read [CONTRIBUTING.md](CONTRIBUTING.md) for contribution guidelines
6. 🚀 Start contributing!

---

© 2024 Vanderwaals - Licensed under GPL-3.0
