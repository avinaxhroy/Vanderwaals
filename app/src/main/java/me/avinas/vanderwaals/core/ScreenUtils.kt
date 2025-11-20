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

/**
 * Get device screen size with orientation consideration.
 * 
 * Returns actual screen dimensions adjusted for portrait/landscape.
 * 
 * @param context Application context
 * @return Size with width and height in pixels
 */
fun getDeviceScreenSize(context: Context): Size {
    val orientation = context.resources.configuration.orientation
    val size = getScreenSize(context)
    return if (orientation == Configuration.ORIENTATION_PORTRAIT) {
        Size(minOf(size.width, size.height), maxOf(size.width, size.height))
    } else {
        Size(maxOf(size.width, size.height), minOf(size.width, size.height))
    }
}

/**
 * Get raw screen size regardless of orientation.
 * 
 * Uses WindowMetrics API on Android R+ for better accuracy,
 * falls back to DisplayMetrics on older versions.
 * 
 * @param context Application context
 * @return Size with width and height in pixels
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
