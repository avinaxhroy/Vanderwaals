package me.avinas.vanderwaals.core

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.util.DisplayMetrics
import android.util.Size
import android.view.WindowManager
import androidx.annotation.RequiresApi
import android.os.Build
import android.view.WindowMetrics

/**
 * Utility functions for screen and display metrics.
 * 
 * Provides device screen dimensions across different Android versions.
 */
private var isTabletCached: Boolean? = null

/** Result is cached to avoid repeated resource checks. */
fun isTablet(context: Context): Boolean {
    if (isTabletCached == null) {
        val smallestScreenWidthDp = context.resources.configuration.smallestScreenWidthDp
        isTabletCached = smallestScreenWidthDp >= 600
    }
    return isTabletCached!!
}

fun getDeviceScreenSize(context: Context): Size {
    val size = getScreenSize(context)
    val isTabletDevice = isTablet(context)
    
    // For phones, always assume portrait orientation for wallpapers
    // This prevents issues where background workers run while phone is in landscape
    // but the wallpaper should still be generated for portrait launcher.
    if (!isTabletDevice) {
         return Size(minOf(size.width, size.height), maxOf(size.width, size.height))
    }

    val orientation = context.resources.configuration.orientation
    return if (orientation == Configuration.ORIENTATION_PORTRAIT) {
        Size(minOf(size.width, size.height), maxOf(size.width, size.height))
    } else {
        Size(maxOf(size.width, size.height), minOf(size.width, size.height))
    }
}

/**
 * Raw screen size regardless of orientation, using WindowMetrics on Android R+
 * (more accurate) and falling back to DisplayMetrics on older versions.
 */
private fun getScreenSize(context: Context): Size {
    val api: ScreenSizeApi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        ScreenSizeApiLevel30(context)
    } else {
        @Suppress("DEPRECATION")
        ScreenSizeApiLegacy(context)
    }
    return api.getScreenSize()
}

/**
 * Interface for getting screen size with API-level specific implementations.
 */
private interface ScreenSizeApi {
    fun getScreenSize(): Size
}

/**
 * Legacy implementation using DisplayMetrics (Android < R).
 */
@Suppress("DEPRECATION")
private class ScreenSizeApiLegacy(private val context: Context) : ScreenSizeApi {
    override fun getScreenSize(): Size {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = windowManager.defaultDisplay
        val metrics = if (display != null) {
            DisplayMetrics().also { display.getRealMetrics(it) }
        } else {
            Resources.getSystem().displayMetrics
        }
        return Size(metrics.widthPixels, metrics.heightPixels)
    }
}

/**
 * Modern implementation using WindowMetrics (Android R+).
 */
@RequiresApi(Build.VERSION_CODES.R)
private class ScreenSizeApiLevel30(private val context: Context) : ScreenSizeApi {
    override fun getScreenSize(): Size {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics: WindowMetrics = windowManager.currentWindowMetrics
        return Size(metrics.bounds.width(), metrics.bounds.height())
    }
}
