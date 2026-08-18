package me.avinas.vanderwaals.core

/**
 * Centralized notification channel IDs and notification IDs.
 *
 * All notification channels are created in [me.avinas.vanderwaals.VanderwaalsApplication.createNotificationChannels].
 * Services and workers must reference these constants instead of hardcoding strings
 * to prevent silent notification failures from channel ID mismatches.
 */
object NotificationConstants {

    /** General wallpaper service operations (sync, batch download, etc.) */
    const val CHANNEL_WALLPAPER_SERVICE = "wallpaper_service_channel"

    /** Manifest and catalog sync notifications */
    const val CHANNEL_SYNC = "sync_channel"

    /** WallpaperMonitorService — persistent foreground notification */
    const val CHANNEL_WALLPAPER_MONITOR = "wallpaper_monitor_channel"

    /** WallpaperChangeService — short-lived change-in-progress notification */
    const val CHANNEL_WALLPAPER_CHANGE = "wallpaper_change_channel"

    /** WallpaperMonitorService foreground notification */
    const val NOTIFICATION_ID_MONITOR = 999

    /** WallpaperChangeService foreground notification */
    const val NOTIFICATION_ID_CHANGE = 1001

    /** Batch download progress notification */
    const val NOTIFICATION_ID_BATCH_DOWNLOAD = 1002
}
