# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
-keepattributes Signature
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep class kotlin.coroutines.Continuation
-keep class androidx.datastore.*.** {*;}

-keepclassmembers class me.avinas.vanderwaals.feature.wallpaper.domain.model.Album {
 !transient <fields>;
}

# ========== Vanderwaals-specific rules ==========

# Keep all data entities
-keep class me.avinas.vanderwaals.data.entity.** { *; }
-keep class me.avinas.vanderwaals.network.dto.** { *; }

# Gson
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# TensorFlow Lite
-keep class org.tensorflow.** { *; }
-keep interface org.tensorflow.** { *; }
-dontwarn org.tensorflow.**
-keep class org.tensorflow.lite.** { *; }
-keepclassmembers class org.tensorflow.lite.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Retrofit
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keepattributes Signature, InnerClasses, EnclosingMethod

# Keep Retrofit service interfaces and their methods
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Keep generic signature of Retrofit methods (fixes "Response must include generic type" error)
-keep,allowobfuscation,allowshrinking class retrofit2.Response { *; }
-keep,allowobfuscation,allowshrinking interface retrofit2.Call { *; }

-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# Keep specific API services to preserve their signatures
-keep,allowobfuscation interface me.avinas.vanderwaals.network.** { *; }

# Keep ML model files
-keep class **.tflite { *; }

# WorkManager
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.InputMerger
-keep class androidx.work.impl.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepclassmembers class me.avinas.vanderwaals.feature.wallpaper.domain.model.AlbumWithWallpaperAndFolder {
 !transient <fields>;
}
-keepclassmembers class me.avinas.vanderwaals.feature.wallpaper.domain.model.Wallpaper {
 !transient <fields>;
}
-keepclassmembers class me.avinas.vanderwaals.feature.wallpaper.domain.model.Folder {
 !transient <fields>;
}

# Keep wallpaper services and their action enums
-keep class me.avinas.vanderwaals.feature.wallpaper.wallpaper_service.HomeWallpaperService { *; }
-keep class me.avinas.vanderwaals.feature.wallpaper.wallpaper_service.HomeWallpaperService$Actions { *; }
-keep class me.avinas.vanderwaals.feature.wallpaper.wallpaper_service.LockWallpaperService { *; }
-keep class me.avinas.vanderwaals.feature.wallpaper.wallpaper_service.LockWallpaperService$Actions { *; }

# Keep WallpaperAction classes
-keep class me.avinas.vanderwaals.feature.wallpaper.wallpaper_alarmmanager.WallpaperAction { *; }
-keep class me.avinas.vanderwaals.feature.wallpaper.wallpaper_alarmmanager.WallpaperAction$* { *; }

# Keep Type enum
-keep class me.avinas.vanderwaals.core.Type { *; }

# OkHttp - Required for Retrofit networking
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }
-keep interface okio.** { *; }

# OkHttp platform classes used by Retrofit
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**