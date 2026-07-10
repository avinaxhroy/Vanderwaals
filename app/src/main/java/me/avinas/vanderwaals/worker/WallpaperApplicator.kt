package me.avinas.vanderwaals.worker

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import me.avinas.vanderwaals.core.BitmapManager
import me.avinas.vanderwaals.core.LiveWallpaperDetector
import me.avinas.vanderwaals.core.SmartCrop
import me.avinas.vanderwaals.core.getDeviceScreenSize
import me.avinas.vanderwaals.domain.usecase.UserEngagementTracker
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads a wallpaper bitmap, applies SmartCrop for the device screen,
 * saves the cropped file, and sets it via WallpaperManager.
 */
@Singleton
class WallpaperApplicator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engagementTracker: UserEngagementTracker
) {
    companion object {
        private const val TAG = "WallpaperApplicator"
        
        const val TARGET_HOME = "home"
        const val TARGET_LOCK = "lock"
        const val TARGET_BOTH = "both"
    }
    
    /**
     * Result of wallpaper application.
     */
    sealed class ApplyResult {
        /** Successfully applied wallpaper. */
        data object Success : ApplyResult()
        
        /** Failed to decode/load the bitmap. */
        data class DecodeFailed(val message: String) : ApplyResult()
        
        /** Live wallpaper is blocking the static wallpaper. */
        data class BlockedByLiveWallpaper(val serviceName: String) : ApplyResult()
        
        /** General error during application. */
        data class Error(val exception: Throwable) : ApplyResult()
        
        /** Invalid target screen specified. */
        data class InvalidTarget(val target: String) : ApplyResult()
    }
    
    /**
     * Applies wallpaper file to the specified screen(s) with SmartCrop processing.
     * 
     * Uses a "try then verify" approach for live wallpaper detection:
     * 1. Attempt to apply the wallpaper
     * 2. Verify the change was successful
     * 3. If failed, check if live wallpaper is blocking
     * 
     * @param wallpaperFile File containing the wallpaper image
     * @param targetScreen Target screen: "home", "lock", or "both"
     * @return ApplyResult indicating success or failure reason
     */
    suspend fun apply(wallpaperFile: File, targetScreen: String): ApplyResult {
        var originalBitmap: Bitmap? = null
        var processedBitmap: Bitmap? = null
        
        return try {
            val wallpaperManager = WallpaperManager.getInstance(context)
            
            // Step 1: Load bitmap safely with OOM protection
            originalBitmap = BitmapManager.loadBitmap(wallpaperFile)
            
            if (originalBitmap == null) {
                Log.e(TAG, "Failed to decode wallpaper file: ${wallpaperFile.name}")
                return ApplyResult.DecodeFailed("Failed to decode wallpaper file")
            }
            
            // Step 2: Apply SmartCrop to actual screen dimensions
            val screenSize = getDeviceScreenSize(context)
            
            processedBitmap = SmartCrop.smartCropBitmapAsync(
                source = originalBitmap,
                targetWidth = screenSize.width,
                targetHeight = screenSize.height,
                mode = SmartCrop.CropMode.AUTO
            )
            
            // Step 3: Save cropped bitmap for preview consistency
            saveCroppedWallpaper(wallpaperFile, processedBitmap)
            
            // Step 4: Recycle original to save memory
            if (processedBitmap !== originalBitmap) {
                BitmapManager.recycleSafely(originalBitmap)
                originalBitmap = null
            }
            
            // Step 5: Apply to WallpaperManager
            val applied = applyToWallpaperManager(wallpaperManager, processedBitmap, targetScreen)
            if (!applied) {
                return ApplyResult.InvalidTarget(targetScreen)
            }
            
            // Step 6: Recycle processed bitmap
            BitmapManager.recycleSafely(processedBitmap)
            processedBitmap = null
            
            // Step 7: Verify live wallpaper isn't blocking
            val wallpaperInfo = wallpaperManager.wallpaperInfo
            if (wallpaperInfo != null) {
                val (isBlocking, serviceName) = LiveWallpaperDetector.detectBlockingAfterFailure(context)
                if (isBlocking) {
                    Log.e(TAG, "Wallpaper change blocked by live wallpaper: $serviceName")
                    return ApplyResult.BlockedByLiveWallpaper(serviceName ?: "unknown")
                }
            }
            
            // Step 8: Record engagement
            engagementTracker.recordWallpaperChange()
            
            Log.d(TAG, "Successfully applied wallpaper with SmartCrop processing")
            ApplyResult.Success
            
        } catch (e: Exception) {
            Log.e(TAG, "Error applying wallpaper", e)
            ApplyResult.Error(e)
        } finally {
            // Ensure bitmaps are recycled even if exception occurs
            BitmapManager.recycleSafely(originalBitmap)
            BitmapManager.recycleSafely(processedBitmap)
        }
    }
    
    /**
     * Applies bitmap to WallpaperManager for the specified target.
     * @return true if target was valid and applied, false otherwise
     */
    private fun applyToWallpaperManager(
        wallpaperManager: WallpaperManager,
        bitmap: Bitmap,
        targetScreen: String
    ): Boolean {
        return when (targetScreen) {
            TARGET_HOME -> {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    wallpaperManager.setBitmap(
                        bitmap,
                        null,
                        true,
                        WallpaperManager.FLAG_SYSTEM
                    )
                } else {
                    wallpaperManager.setBitmap(bitmap)
                }
                true
            }
            TARGET_LOCK -> {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    wallpaperManager.setBitmap(
                        bitmap,
                        null,
                        true,
                        WallpaperManager.FLAG_LOCK
                    )
                } else {
                    // On older devices, just set system wallpaper
                    wallpaperManager.setBitmap(bitmap)
                }
                true
            }
            TARGET_BOTH -> {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    wallpaperManager.setBitmap(
                        bitmap,
                        null,
                        true,
                        WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                    )
                } else {
                    wallpaperManager.setBitmap(bitmap)
                }
                true
            }
            else -> {
                Log.e(TAG, "Invalid target screen: $targetScreen")
                false
            }
        }
    }
    
    /**
     * Saves the cropped bitmap to a file for preview consistency.
     * Uses JPEG at 90% quality — visually indistinguishable for wallpapers
     * but ~10x faster than PNG for large bitmaps (3024x4032, 3840x2160).
     */
    private fun saveCroppedWallpaper(originalFile: File, croppedBitmap: Bitmap) {
        val parentDir = originalFile.parentFile ?: return // Guard: skip if no parent directory
        val croppedFile = File(parentDir, "${originalFile.nameWithoutExtension}_cropped.jpg")
        try {
            croppedFile.outputStream().use { out ->
                croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            Log.d(TAG, "Saved cropped wallpaper: ${croppedFile.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save cropped wallpaper", e)
            // Non-fatal - we still have the bitmap in memory
        }
    }
    
    /**
     * Checks if applying to the specified target is supported on this device.
     */
    fun isTargetSupported(targetScreen: String): Boolean {
        return when (targetScreen) {
            TARGET_HOME, TARGET_BOTH -> true
            TARGET_LOCK -> android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N
            else -> false
        }
    }
}
