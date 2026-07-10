package me.avinas.vanderwaals.core

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility for saving media files to the device's public gallery using MediaStore.
 * Handles Scoped Storage requirements for Android 10+ (API 29+).
 */
@Singleton
class MediaSaver @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    /**
     * Saves an image file to the public Pictures directory.
     *
     * @param file The source file to save.
     * @param fileName The desired name for the saved file (without extension).
     * @return Result<Uri> containing the URI of the saved image on success.
     */
    fun saveImageToGallery(file: File, fileName: String): Result<Uri> {
        return try {
            if (!file.exists() || file.length() <= 0) {
                return Result.failure(Exception("Source file is empty or does not exist"))
            }

            android.util.Log.d("MediaSaver", "Saving image to gallery (${file.length()} bytes)")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveImageScopedStorage(file, fileName)
            } else {
                saveImageLegacy(file, fileName)
            }
        } catch (e: Exception) {
            android.util.Log.e("MediaSaver", "Error saving image", e)
            Result.failure(e)
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun saveImageScopedStorage(file: File, fileName: String): Result<Uri> {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$fileName.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Vanderwaals")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: return Result.failure(Exception("Failed to create MediaStore entry"))

        try {
            resolver.openOutputStream(uri)?.use { outputStream ->
                file.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: return Result.failure(Exception("Failed to open output stream"))

            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            val rowsUpdated = resolver.update(uri, contentValues, null, null)
            
            if (rowsUpdated == 0) {
                 return Result.failure(Exception("Failed to publish image (update IS_PENDING)"))
            }

            return Result.success(uri)
        } catch (e: Exception) {
            // Clean up empty/partial file if possible
            try {
                resolver.delete(uri, null, null)
            } catch (deleteEx: Exception) {
                // Ignore delete failure
            }
            return Result.failure(e)
        }
    }

    private fun saveImageLegacy(file: File, fileName: String): Result<Uri> {
        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val appDir = File(picturesDir, "Vanderwaals")
        
        if (!appDir.exists()) {
            if (!appDir.mkdirs()) {
                return Result.failure(Exception("Failed to create media directory"))
            }
        }

        val targetFile = File(appDir, "$fileName.jpg")
        
        try {
            file.copyTo(targetFile, overwrite = true)
            
            // Scan the file so it shows up in Gallery immediately
            android.media.MediaScannerConnection.scanFile(
                context,
                arrayOf(targetFile.absolutePath),
                arrayOf("image/jpeg"),
                null
            )
            
            return Result.success(Uri.fromFile(targetFile))
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
}
