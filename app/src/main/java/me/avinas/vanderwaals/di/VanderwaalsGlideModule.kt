package me.avinas.vanderwaals.di

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.load.engine.cache.LruResourceCache
import com.bumptech.glide.module.AppGlideModule
import com.bumptech.glide.request.RequestOptions

/**
 * Glide caching tuned for wallpapers: 50 MB memory / 250 MB disk cache and
 * ARGB_8888 decoding. This module is picked up automatically by the
 * Landscapist Glide wrapper, so all GlideImage composables use it.
 */
@GlideModule
class VanderwaalsGlideModule : AppGlideModule() {

    companion object {
        /**
         * Memory cache size in bytes (50MB).
         *
         * Sized for approximately 20-30 high-resolution wallpaper previews.
         * Each UHD thumbnail (~2-3MB compressed) × 20 = ~50MB
         */
        private const val MEMORY_CACHE_SIZE = 50 * 1024 * 1024L // 50MB

        /**
         * Disk cache size in bytes (250MB).
         *
         * Stores full-resolution wallpapers for offline usage.
         * ~100 full-resolution wallpapers (2-3MB each) = ~250MB
         */
        private const val DISK_CACHE_SIZE = 250 * 1024 * 1024L // 250MB
    }

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        builder.setMemoryCache(LruResourceCache(MEMORY_CACHE_SIZE))

        builder.setDiskCache(
            InternalCacheDiskCacheFactory(context, DISK_CACHE_SIZE)
        )

        builder.setDefaultRequestOptions(
            RequestOptions()
                .format(DecodeFormat.PREFER_ARGB_8888)
                .disallowHardwareConfig() // Prevent hardware bitmaps for wallpaper setting
        )

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.CUPCAKE) {
            builder.setLogLevel(android.util.Log.WARN)
        }
    }

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        // Default Glide components are sufficient for wallpapers
    }

    /**
     * Skipping manifest parsing improves build time.
     */
    override fun isManifestParsingEnabled(): Boolean {
        return false
    }
}
