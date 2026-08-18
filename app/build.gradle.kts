import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.hiltAndroid)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlinSerialization)
}

// Load local.properties manually
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

fun localOrEnv(propertyKey: String, vararg envKeys: String): String {
    return envKeys.asSequence()
        .mapNotNull { key -> System.getenv(key)?.ifBlank { null } }
        .firstOrNull()
        ?: localProperties.getProperty(propertyKey)?.ifBlank { null }
        ?: ""
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "me.avinas.vanderwaals"
    compileSdk = 36


    signingConfigs {
        create("release") {
            // Try environment variables first, then fall back to local.properties
            val keyStorePath = System.getenv("SIGNING_KEYSTORE_PATH")?.ifEmpty { null }
                ?: (localProperties.getProperty("SIGNING_KEYSTORE_PATH") as? String)?.ifEmpty { null }
            val keyStorePassword = System.getenv("SIGNING_STORE_PASSWORD")?.ifEmpty { null }
                ?: (localProperties.getProperty("SIGNING_STORE_PASSWORD") as? String)?.ifEmpty { null }
            val kAlias = System.getenv("SIGNING_KEY_ALIAS")?.ifEmpty { null }
                ?: (localProperties.getProperty("SIGNING_KEY_ALIAS") as? String)?.ifEmpty { null }
            val kPassword = System.getenv("SIGNING_KEY_PASSWORD")?.ifEmpty { null }
                ?: (localProperties.getProperty("SIGNING_KEY_PASSWORD") as? String)?.ifEmpty { null }
            
            if (keyStorePath != null && keyStorePassword != null && kAlias != null && kPassword != null) {
                storeFile = file(keyStorePath)
                storePassword = keyStorePassword
                keyAlias = kAlias
                keyPassword = kPassword
            }
        }
    }

    defaultConfig {
        applicationId = "me.avinas.vanderwaals"
        minSdk = 30
        targetSdk = 36
        versionCode = 464 // Vanderwaals 4.6.2
        versionName = "4.6.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            //noinspection ChromeOsAbiSupport
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "ENABLE_DEBUG_SCREEN", "true")
            buildConfigField("boolean", "USE_LOCAL_MANIFEST", "false") // Use GitHub manifest
            buildConfigField("String", "MANIFEST_BASE_URL", "\"https://raw.githubusercontent.com/avinaxhroy/Vanderwaals/main/\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
            buildConfigField("boolean", "ENABLE_DEBUG_SCREEN", "false")
            buildConfigField("boolean", "USE_LOCAL_MANIFEST", "false") // Use GitHub manifest
            buildConfigField("String", "MANIFEST_BASE_URL", "\"https://raw.githubusercontent.com/avinaxhroy/Vanderwaals/main/\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    @Suppress("DEPRECATION")
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
    }

    androidResources {
        @Suppress("UnstableApiUsage")
        generateLocaleConfig = true
    }

    sourceSets {
        getByName("debug").assets.srcDirs("$projectDir/schemas")
        getByName("test").assets.srcDirs("$projectDir/schemas")
        getByName("test").resources.srcDirs("$projectDir/schemas")
    }

    buildToolsVersion = "35.0.1"
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
            excludes += listOf("**/libc++_shared.so")
        }
    }

    applicationVariants.all {
        this.outputs
            .map { it as com.android.build.gradle.internal.api.ApkVariantOutputImpl }
            .forEach { output ->
                val apkName = "vanderwaals-v${this.versionName}.apk"
                output.outputFileName = apkName
            }
    }

    lint {
        disable.add("RemoveWorkManagerInitializer")
        disable.add("DefaultLocale")
        disable.add("DiscouragedPrivateApi")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.material)
    implementation(libs.androidx.palette)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.animation)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.gson)
    implementation(libs.androidx.documentfile)
    implementation(libs.landscapist.glide)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    implementation(libs.lottie.compose)
    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.profileinstaller)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)
    implementation(libs.dfc)
    implementation (libs.kotlinx.serialization.json)

    implementation(libs.litert.api)
    implementation(libs.litert.support)
    implementation(libs.litert.gpu)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation("io.coil-kt:coil-compose:2.6.0")
}