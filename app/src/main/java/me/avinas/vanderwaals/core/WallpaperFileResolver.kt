package me.avinas.vanderwaals.core

import android.content.Context
import java.io.File

/**
 * Probes both cache and permanent storage for [.jpg] and [.png]. Wallpapers are
 * downloaded to `cacheDir/wallpapers/{id}.jpg`, but SmartCrop 2.0 saves lossless
 * PNG copies; earlier code looked only in `filesDir` for a hardcoded `.jpg`, so it
 * silently failed for any `.png` or cache-stored wallpaper.
 */
fun resolveWallpaperFile(context: Context, wallpaperId: String): File? {
    val cacheDir = File(context.cacheDir, "wallpapers")
    val filesDir = File(context.filesDir, "wallpapers")
    val candidates = listOf(
        File(cacheDir, "$wallpaperId.jpg"),
        File(cacheDir, "$wallpaperId.png"),
        File(filesDir, "$wallpaperId.jpg"),
        File(filesDir, "$wallpaperId.png"),
    )
    return candidates.firstOrNull { it.exists() && it.length() > 0 }
}
