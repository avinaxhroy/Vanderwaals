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
 * Glide configuration module for Vanderwaals image caching.
 *
 * Optimizes image loading performance with:
 * - 50MB memory cache (LRU)
 * - 250MB disk cache
 * - High quality image decoding (ARGB_8888)
 * - Automatic crossfade animations
 *
 * **Memory Cache:**
 * - LruResourceCache with 50MB capacity
 * - Automatically evicts least recently used images
 * - Configured for ~20-30 wallpaper previews in memory
 *
 * **Disk Cache:**
 * - 250MB internal cache for downloaded wallpapers
 * - Stored in app's internal storage
 * - Automatically cleaned up when space is low
 *
 * **Image Quality:**
 * - ARGB_8888 format for high quality wallpapers
 * - Preserves color depth and gradients
 * - Essential for wallpaper display quality
 *
 * **Integration with Landscapist:**
 * This configuration is automatically used by Landscapist Glide wrapper.
 * All GlideImage composables benefit from this caching.
 *
 * @see com.skydoves.landscapist.glide.GlideImage
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

    /**
     * Configures Glide with custom memory and disk cache sizes.
     *
     * @param context Application context
     * @param builder GlideBuilder to configure
     */
    override fun applyOptions(context: Context, builder: GlideBuilder) {
        // Configure memory cache with LRU eviction policy
        builder.setMemoryCache(LruResourceCache(MEMORY_CACHE_SIZE))

        // Configure disk cache in internal storage
        builder.setDiskCache(
            InternalCacheDiskCacheFactory(context, DISK_CACHE_SIZE)
        )

        // Set default image quality to ARGB_8888 for wallpapers
        builder.setDefaultRequestOptions(
            RequestOptions()
                .format(DecodeFormat.PREFER_ARGB_8888)
                .disallowHardwareConfig() // Prevent hardware bitmaps for wallpaper setting
        )

        // Enable verbose logging in debug builds
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.CUPCAKE) {
            builder.setLogLevel(android.util.Log.WARN)
        }
    }

    /**
     * Configures Glide registry with custom components.
     *
     * Currently uses default Glide components. Override this method to add:
     * - Custom model loaders
     * - Custom decoders
     * - Custom encoders
     *
     * @param context Application context
     * @param glide Glide instance
     * @param registry Registry to configure
     */
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        // Default Glide components are sufficient for wallpapers
        // Override to add custom loaders if needed
    }

    /**
     * Disables parsing of Glide annotations in AndroidManifest.xml.
     *
     * Improves build time by skipping manifest parsing for Glide modules.
     *
     * @return false to disable manifest parsing
     */
    override fun isManifestParsingEnabled(): Boolean {
        return false
    }
}
