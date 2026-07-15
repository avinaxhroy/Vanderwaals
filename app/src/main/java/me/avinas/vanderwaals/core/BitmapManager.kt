package me.avinas.vanderwaals.core

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import java.io.File
import java.io.InputStream

/**
 * Safe bitmap loading via Glide with automatic downsampling, memory pooling,
 * and OOM retry. Use [AutoRecycleBitmap] for scoped cleanup.
 *
 * Must be initialized via [init] from [VanderwaalsApplication.onCreate].
 */
object BitmapManager {

    private const val TAG = "BitmapManager"

    private const val MAX_BITMAP_WIDTH = 4096
    private const val MAX_BITMAP_HEIGHT = 4096

    @Volatile
    private var appContext: Context? = null

    /**
     * Initializes BitmapManager with the application context.
     * Must be called from [VanderwaalsApplication.onCreate].
     */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun context(): Context =
        appContext ?: throw IllegalStateException(
            "BitmapManager not initialized. Call init() from Application.onCreate()"
        )

    /**
     * Loads a bitmap from a file with automatic downsampling and OOM protection.
     *
     * Glide handles accurate downsampling (exact target size, not just power-of-2),
     * bitmap pool reuse, and OOM retry with progressively smaller decodes.
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
            Glide.with(context())
                .asBitmap()
                .load(file)
                .override(maxWidth, maxHeight)
                .skipMemoryCache(true)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .submit()
                .get()
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OutOfMemoryError loading bitmap from ${file.name}", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bitmap from ${file.name}", e)
            null
        }
    }

    /**
     * Loads a bitmap from a Uri with automatic downsampling and OOM protection.
     *
     * Preferred over [loadBitmapFromStream] when a Uri is available — Glide handles
     * content provider access internally without an intermediate byte copy.
     *
     * @param uri Image source Uri (content://, file://, etc.)
     * @param maxWidth Maximum width (default: 4096)
     * @param maxHeight Maximum height (default: 4096)
     * @return Decoded bitmap or null if loading failed
     */
    fun loadBitmap(
        uri: Uri,
        maxWidth: Int = MAX_BITMAP_WIDTH,
        maxHeight: Int = MAX_BITMAP_HEIGHT
    ): Bitmap? {
        return try {
            Glide.with(context())
                .asBitmap()
                .load(uri)
                .override(maxWidth, maxHeight)
                .skipMemoryCache(true)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .submit()
                .get()
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OutOfMemoryError loading bitmap from uri: $uri", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bitmap from uri: $uri", e)
            null
        }
    }

    /**
     * Loads a bitmap from an InputStream with automatic downsampling and OOM protection.
     *
     * The stream is read into a byte array for Glide to decode. Prefer [loadBitmap]
     * with a [Uri] when available to avoid the intermediate copy.
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
            val bytes = inputStream.readBytes()
            Glide.with(context())
                .asBitmap()
                .load(bytes)
                .override(maxWidth, maxHeight)
                .skipMemoryCache(true)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .submit()
                .get()
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OutOfMemoryError loading bitmap from stream", e)
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
