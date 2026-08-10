# Vanderwaals development setup guide

Guide for setting up Vanderwaals development, building, and testing environments.

## Table of contents

- [Prerequisites](#prerequisites)
- [Environment setup](#environment-setup)
- [Building the project](#building-the-project)
- [TensorFlow Lite model](#tensorflow-lite-model)
- [Configuration](#configuration)
- [Running and testing](#running-and-testing)
- [Troubleshooting](#troubleshooting)

---

## Prerequisites

### Required software

| Software | Version | Purpose |
|----------|---------|---------|
| **Java JDK** | 17 or later | Gradle and Android build |
| **Android Studio** | Ladybug (2024.2.1+) | IDE and Android SDK |
| **Android SDK** | API 30+ (min), API 36 (target) | Android platform |
| **Git** | Latest | Version control |

### System requirements

- **OS**: macOS, Windows, or Linux
- **RAM**: 8 GB minimum, 16 GB recommended
- **Storage**: 10 GB free space (SDK, project, and caches)
- **Network**: Internet connection for dependencies and wallpaper catalog synchronization

---

## Environment setup

### 1. Install Java 17

#### macOS (using Homebrew)
```bash
brew install openjdk@17
echo 'export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
java -version
```

#### Linux (Ubuntu/Debian)
```bash
sudo apt update
sudo apt install openjdk-17-jdk
java -version
```

#### Windows
Download and install OpenJDK 17 from [Adoptium](https://adoptium.net/).

### 2. Install Android Studio

1. Download Android Studio from [developer.android.com](https://developer.android.com/studio).
2. Install with default settings.
3. Run the setup wizard to install the Android SDK, SDK Platform-Tools, and Emulator.

### 3. Configure Android SDK

In Android Studio, navigate to **Settings → Appearance & Behavior → System Settings → Android SDK**.

**Required SDK Platforms**:
- Android 15.0 (API 36) - Target SDK
- Android 11.0 (API 30) - Minimum SDK

**Required SDK Tools**:
- Android SDK Build-Tools 35.0.1
- Android SDK Platform-Tools
- Android SDK Tools
- Android Emulator

### 4. Set environment variables

#### macOS/Linux (`~/.zshrc` or `~/.bashrc`)
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk  # macOS
# export ANDROID_HOME=$HOME/Android/Sdk       # Linux
export PATH=$PATH:$ANDROID_HOME/emulator
export PATH=$PATH:$ANDROID_HOME/platform-tools
export PATH=$PATH:$ANDROID_HOME/tools
export PATH=$PATH:$ANDROID_HOME/tools/bin
```

#### Windows (Environment Variables)
```
ANDROID_HOME=C:\Users\<YourUsername>\AppData\Local\Android\Sdk
Path=%Path%;%ANDROID_HOME%\platform-tools;%ANDROID_HOME%\tools
```

Verify setup:
```bash
adb version
```

---

## Building the project

### 1. Clone the repository

```bash
git clone https://github.com/avinaxhroy/Vanderwaals.git
cd Vanderwaals
```

### 2. Open in Android Studio

1. Select **File → Open** and choose the `Vanderwaals` directory.
2. Wait for Gradle sync to complete.

### 3. Build the app

#### Command line
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

**Output location**:
```
app/build/outputs/apk/debug/vanderwaals-v4.6.3.apk
app/build/outputs/apk/release/vanderwaals-v4.6.3.apk
```

---

## TensorFlow Lite model

### Download MobileNetV4-Conv-Small model

The app requires a TensorFlow Lite model for image feature extraction (1280D MobileNetV4-Conv-Small).

#### Option 1: Convert using Colab

1. Open [Google Colab](https://colab.research.google.com/).
2. Run `scripts/colab_one_cell.py` in a notebook cell to convert and export the model.
3. Place the output file at `app/src/main/assets/models/mobilenet_v4_conv_small.tflite`.

#### Option 2: Convert locally

```bash
pip install torch timm onnx onnx2tf tensorflow
python3 scripts/convert_mobilenetv4_to_tflite.py
```

### Verify model

```bash
python3 scripts/test_tflite.py
```

**Expected output**:
```
Model exists: app/src/main/assets/models/mobilenet_v4_conv_small.tflite
Model loaded successfully
Input shape: [1, 224, 224, 3]
Output shape: [1, 1280]
```

---

## Configuration

### Build configuration

Configured in `app/build.gradle.kts`:

```kotlin
android {
    namespace = "me.avinas.vanderwaals"
    compileSdk = 36
    
    defaultConfig {
        applicationId = "me.avinas.vanderwaals"
        minSdk = 30
        targetSdk = 36
        versionCode = 463
        versionName = "4.6.3"
    }
}
```

### Manifest URL configuration

In production, wallpaper metadata is fetched from GitHub:

```kotlin
buildTypes {
    release {
        buildConfigField("String", "MANIFEST_BASE_URL", 
            "\"https://raw.githubusercontent.com/avinaxhroy/Vanderwaals/main/\"")
    }
}
```

For local testing, place a sample manifest at `app/src/main/assets/sample-manifest.json` and configure `debug`:

```kotlin
buildTypes {
    debug {
        buildConfigField("boolean", "USE_LOCAL_MANIFEST", "true")
    }
}
```

### Signing configuration

Create `local.properties` (gitignored):
```properties
SIGNING_KEYSTORE_PATH=/path/to/keystore.jks
SIGNING_STORE_PASSWORD=your_store_password
SIGNING_KEY_ALIAS=your_key_alias
SIGNING_KEY_PASSWORD=your_key_password
```

---

## Running and testing

### Run on physical device

1. Enable **Developer Options** on your phone (tap **Settings → About Phone → Build Number** 7 times).
2. Enable **USB Debugging** under Developer Options.
3. Connect the device via USB and verify detection:
   ```bash
   adb devices
   ```
4. Install the debug build:
   ```bash
   ./gradlew installDebug
   ```

### Run on emulator

1. Create a virtual device in Android Studio (Pixel 8 Pro, API 36).
2. Launch the emulator and run:
   ```bash
   ./gradlew installDebug
   ```

### Logcat debugging

```bash
# Monitor embedding extraction
adb logcat -s ExtractEmbedding:V

# Monitor wallpaper selection
adb logcat -s SelectNextWallpaper:V

# Monitor implicit feedback processing
adb logcat -s ProcessImplicitFeedback:V
```

---

## Troubleshooting

### Dependency or sync failures

Clean local cache and retry:
```bash
./gradlew clean
./gradlew --refresh-dependencies
```

### SDK location missing

Create `local.properties` with the SDK path:
```properties
sdk.dir=/path/to/Android/Sdk
```

### Model missing error

Verify the TFLite asset is present:
```bash
ls -lh app/src/main/assets/models/mobilenet_v4_conv_small.tflite
```

---

## Useful commands

### ADB commands
```bash
adb devices
adb install app/build/outputs/apk/debug/app-debug.apk
adb uninstall me.avinas.vanderwaals
adb shell pm clear me.avinas.vanderwaals
adb logcat
```

### Gradle commands
```bash
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew installDebug
./gradlew test
./gradlew clean
```

---

## License

Copyright © 2024–2025 Avinas / Confused Coconut. Dual-licensed under AGPL-3.0 and Commercial terms. See [LICENSE](LICENSE) and [COMMERCIAL_LICENSE.md](COMMERCIAL_LICENSE.md).
