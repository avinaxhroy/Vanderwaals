package me.avinas.vanderwaals.core

import android.content.Context
import java.io.File

/**
 * Resolves the local image file for a wallpaper by probing both the cache and
 * permanent storage directories for [.jpg] and [.png] extensions.
 *
 * Wallpapers are downloaded to `cacheDir/wallpapers/{id}.jpg` by the repository,
 * but SmartCrop 2.0 saves lossless PNG copies. Some code paths previously looked
 * only in `filesDir` with a hardcoded `.jpg`, silently failing for any wallpaper
 * stored as `.png` or in the cache directory.
 *
 * @param context   Android context providing access to filesystem dirs.
 * @param wallpaperId  The wallpaper identifier (without extension).
 * @return the first existing, non-empty file, or `null` if none is found.
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
