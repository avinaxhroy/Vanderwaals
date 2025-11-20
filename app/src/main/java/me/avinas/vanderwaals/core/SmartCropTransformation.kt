package me.avinas.vanderwaals.core

import android.content.Context
import android.graphics.Bitmap
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Glide transformation that applies smart cropping to preview images
 * This ensures the preview matches the actual wallpaper that will be applied
 */
class SmartCropTransformation(
    private val targetWidth: Int,
    private val targetHeight: Int,
    private val cropMode: SmartCrop.CropMode = SmartCrop.CropMode.AUTO
) : BitmapTransformation() {

    override fun transform(
        pool: BitmapPool,
        toTransform: Bitmap,
        outWidth: Int,
        outHeight: Int
    ): Bitmap {
        // Use smart crop with the same logic as the wallpaper setter
        return SmartCrop.smartCropBitmap(
            source = toTransform,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            mode = cropMode
        )
    }

    override fun equals(other: Any?): Boolean {
        if (other is SmartCropTransformation) {
            return targetWidth == other.targetWidth && 
                   targetHeight == other.targetHeight &&
                   cropMode == other.cropMode
        }
        return false
    }

    override fun hashCode(): Int {
        var result = ID.hashCode()
        result = 31 * result + targetWidth
        result = 31 * result + targetHeight
        result = 31 * result + cropMode.hashCode()
        return result
    }

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        val data = ByteBuffer.allocate(12)
            .putInt(targetWidth)
            .putInt(targetHeight)
            .putInt(cropMode.ordinal)
            .array()
        messageDigest.update(data)
    }

    companion object {
        private const val ID = "me.avinas.vanderwaals.core.SmartCropTransformation"
    }
}
