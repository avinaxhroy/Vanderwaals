package me.avinas.vanderwaals.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream

/**
 * Safe bitmap loading with OOM protection, size optimization,
 * and proper recycling. Use [AutoRecycleBitmap] for scoped cleanup.
 */
object BitmapManager {
    
    private const val TAG = "BitmapManager"
    
    // Maximum bitmap dimensions to prevent OOM
    private const val MAX_BITMAP_WIDTH = 4096
    private const val MAX_BITMAP_HEIGHT = 4096
    
    /**
     * Loads a bitmap from a file with OOM protection and size optimization.
     * 
     * Automatically downsamples if image is too large to prevent OutOfMemoryError.
     * 
     * @param file Image file to load
     * @param maxWidth Maximum width (default: 4096)
     * @param maxHeight Maximum height (default: 4096)
     * @return Decoded bitmap or null if loading failed
     */
    fun loadBitmap(
        file: File,
        maxWidth: Int = MAX_BITMAP_WIDTH,
        maxHeight: Int = MAX_BITMAP_HEIGHT
    ): Bitmap? {
        return try {
            if (!file.exists() || file.length() == 0L) {
                Log.w(TAG, "File does not exist or is empty: ${file.name}")
                return null
            }
            
            // First, decode bounds to check image size
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
            
            if (options.outWidth <= 0 || options.outHeight <= 0) {
                Log.e(TAG, "Invalid image dimensions: ${options.outWidth}x${options.outHeight}")
                return null
            }
            
            // Calculate sample size to downsample large images
            val sampleSize = calculateSampleSize(
                options.outWidth,
                options.outHeight,
                maxWidth,
                maxHeight
            )
            
            // Decode actual bitmap with sampling
            val bitmapOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inJustDecodeBounds = false
            }
            
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, bitmapOptions)
            
            if (bitmap != null) {
                Log.d(TAG, "Loaded bitmap: ${bitmap.width}x${bitmap.height}, " +
                        "sample size: $sampleSize, file: ${file.name}")
            } else {
                Log.e(TAG, "Failed to decode bitmap from file: ${file.name}")
            }
            
            bitmap
            
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OutOfMemoryError loading bitmap from ${file.name}", e)
            // Try to recover memory
            System.gc()
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bitmap from ${file.name}", e)
            null
        }
    }
    
    /**
     * Loads a bitmap from an InputStream with OOM protection.
     * 
     * @param inputStream Input stream containing image data
     * @param maxWidth Maximum width (default: 4096)
     * @param maxHeight Maximum height (default: 4096)
     * @return Decoded bitmap or null if loading failed
     */
    fun loadBitmapFromStream(
        inputStream: InputStream,
        maxWidth: Int = MAX_BITMAP_WIDTH,
        maxHeight: Int = MAX_BITMAP_HEIGHT
    ): Bitmap? {
        return try {
            // Wrap in BufferedInputStream to ensure mark/reset support
            // Some InputStreams (like from ContentResolver) don't support mark/reset
            val bufferedStream = if (inputStream is BufferedInputStream) {
                inputStream
            } else {
                BufferedInputStream(inputStream)
            }
            
            // First, decode bounds to check image size
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            
            // Mark the stream to allow reset after bounds decoding
            bufferedStream.mark(Int.MAX_VALUE)
            BitmapFactory.decodeStream(bufferedStream, null, options)
            bufferedStream.reset()
            
            if (options.outWidth <= 0 || options.outHeight <= 0) {
                Log.e(TAG, "Invalid image dimensions from stream: ${options.outWidth}x${options.outHeight}")
                return null
            }
            
            // Calculate sample size
            val sampleSize = calculateSampleSize(
                options.outWidth,
                options.outHeight,
                maxWidth,
                maxHeight
            )
            
            // Decode actual bitmap
            val bitmapOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inJustDecodeBounds = false
            }
            
            val bitmap = BitmapFactory.decodeStream(bufferedStream, null, bitmapOptions)
            
            if (bitmap != null) {
                Log.d(TAG, "Loaded bitmap from stream: ${bitmap.width}x${bitmap.height}, sample size: $sampleSize")
            } else {
                Log.e(TAG, "Failed to decode bitmap from stream")
            }
            
            bitmap
            
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OutOfMemoryError loading bitmap from stream", e)
            System.gc()
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bitmap from stream", e)
            null
        }
    }
    
    /**
     * Safely recycles a bitmap with null check and exception handling.
     * 
     * @param bitmap Bitmap to recycle (can be null)
     */
    fun recycleSafely(bitmap: Bitmap?) {
        if (bitmap != null && !bitmap.isRecycled) {
            try {
                bitmap.recycle()
                Log.d(TAG, "Recycled bitmap: ${bitmap.width}x${bitmap.height}")
            } catch (e: Exception) {
                Log.w(TAG, "Error recycling bitmap", e)
            }
        }
    }
    
    /**
     * Calculates optimal sample size for downsampling large images.
     * 
     * Uses power-of-2 sampling (1, 2, 4, 8...) which is most efficient
     * for BitmapFactory.
     * 
     * @param width Original image width
     * @param height Original image height
     * @param maxWidth Target maximum width
     * @param maxHeight Target maximum height
     * @return Sample size (power of 2)
     */
    private fun calculateSampleSize(
        width: Int,
        height: Int,
        maxWidth: Int,
        maxHeight: Int
    ): Int {
        var sampleSize = 1
        
        while (width / sampleSize > maxWidth || height / sampleSize > maxHeight) {
            sampleSize *= 2
        }
        
        return sampleSize
    }
    
    /**
     * Auto-cleanup wrapper for bitmaps using Kotlin's use() pattern.
     * 
     * Ensures bitmap is recycled even if exception occurs.
     * 
     * Example:
     * ```kotlin
     * AutoRecycleBitmap(file).use { wrapper ->
     *     wrapper.bitmap?.let { bitmap ->
     *         // Use bitmap safely
     *         processBitmap(bitmap)
     *     }
     * } // Bitmap automatically recycled here
     * ```
     */
    class AutoRecycleBitmap(
        file: File,
        maxWidth: Int = MAX_BITMAP_WIDTH,
        maxHeight: Int = MAX_BITMAP_HEIGHT
    ) : AutoCloseable {
        
        val bitmap: Bitmap? = loadBitmap(file, maxWidth, maxHeight)
        
        override fun close() {
            recycleSafely(bitmap)
        }
    }
    
    /**
     * Auto-cleanup wrapper for bitmaps loaded from streams.
     */
    class AutoRecycleBitmapFromStream(
        inputStream: InputStream,
        maxWidth: Int = MAX_BITMAP_WIDTH,
        maxHeight: Int = MAX_BITMAP_HEIGHT
    ) : AutoCloseable {
        
        val bitmap: Bitmap? = loadBitmapFromStream(inputStream, maxWidth, maxHeight)
        
        override fun close() {
            recycleSafely(bitmap)
        }
    }
}
