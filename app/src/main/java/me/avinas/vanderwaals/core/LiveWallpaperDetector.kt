package me.avinas.vanderwaals.core

import android.app.WallpaperManager
import android.content.Context
import android.util.Log

/**
 * Helper class for detecting live wallpaper services that may block wallpaper changes.
 * 
 * **Why This Matters:**
 * - Brand-supported dynamic wallpapers (Glance, Samsung Dynamic Wallpaper) prevent
 *   apps from changing wallpapers using WallpaperManager.setBitmap()
 * - These services run as live wallpaper implementations that take priority
 * - Users must disable these services before auto-change can work
 * 
 * **How It Works:**
 * 1. Check if live wallpaper is active using WallpaperManager.getWallpaperInfo()
 * 2. Identify if it's a known blocking service (Glance, etc.)
 * 3. Provide service name for user-facing messages
 * 
 * **Supported Detection:**
 * - Glance (Xiaomi, Samsung, Realme variants)
 * - Samsung Dynamic Wallpaper
 * - Generic live wallpaper services
 * 
 * @see android.app.WallpaperManager
 * @see android.service.wallpaper.WallpaperService
 */
object LiveWallpaperDetector {
    
    private const val TAG = "LiveWallpaperDetector"
    
    /**
     * Known blocking live wallpaper service package names and identifiers.
     * 
     * These services are known to prevent WallpaperManager.setBitmap() from working:
     * - Glance variants across different manufacturers
     * - Samsung's Dynamic Wallpaper services
     */
    private val KNOWN_BLOCKING_SERVICES = mapOf(
        // Glance - Xiaomi/Redmi
        "com.mi.android.globalminusscreen" to "Glance for MI",
        "com.glance.internet.mi" to "Glance",
        
        // Glance - Samsung
        "com.samsung.glance" to "Glance on Samsung",
        
        // Glance - Realme
        "com.realme.glance" to "Glance for Realme",
        "com.coloros.glancecenter" to "Glance",
        
        // Samsung Dynamic Wallpaper
        "com.samsung.android.dynamiclock" to "Dynamic Wallpaper",
        "com.samsung.android.app.dofviewer" to "Depth Wallpaper",
        
        // Other known services
        "com.nothing.glance" to "Glance",
        "lockscreen.wallpaper" to "Lock Screen Wallpaper Service"
    )
    
    /**
     * Checks if a live wallpaper is currently active.
     * 
     * Uses WallpaperManager.getWallpaperInfo() which returns:
     * - Non-null WallpaperInfo: Live wallpaper is active
     * - Null: Static wallpaper or no wallpaper set
     * 
     * **API Level:** Works on API 7+ (Android 2.1+)
     * 
     * @param context Application or activity context
     * @return true if live wallpaper is active, false otherwise
     */
    fun isLiveWallpaperActive(context: Context): Boolean {
        return try {
            val wallpaperManager = WallpaperManager.getInstance(context)
            val wallpaperInfo = wallpaperManager.wallpaperInfo
            
            val isActive = wallpaperInfo != null
            
            if (isActive) {
                Log.d(TAG, "Live wallpaper detected: ${wallpaperInfo?.packageName}")
            } else {
                Log.d(TAG, "No live wallpaper active (static wallpaper)")
            }
            
            isActive
        } catch (e: Exception) {
            Log.e(TAG, "Error checking live wallpaper status", e)
            false
        }
    }
    
    /**
     * Gets the package name of the currently active live wallpaper.
     * 
     * **Use Cases:**
     * - Logging which service is blocking wallpaper changes
     * - Opening app settings for the specific service
     * - Customizing user-facing messages
     * 
     * @param context Application or activity context
     * @return Package name of live wallpaper, or null if none active
     */
    fun getLiveWallpaperPackageName(context: Context): String? {
        return try {
            val wallpaperManager = WallpaperManager.getInstance(context)
            val wallpaperInfo = wallpaperManager.wallpaperInfo
            wallpaperInfo?.packageName
        } catch (e: Exception) {
            Log.e(TAG, "Error getting live wallpaper package name", e)
            null
        }
    }
    
    /**
     * Gets the service name of the currently active live wallpaper.
     * 
     * Returns the full component name (package + service class).
     * 
     * @param context Application or activity context
     * @return Service name, or null if none active
     */
    fun getLiveWallpaperServiceName(context: Context): String? {
        return try {
            val wallpaperManager = WallpaperManager.getInstance(context)
            val wallpaperInfo = wallpaperManager.wallpaperInfo
            wallpaperInfo?.serviceName
        } catch (e: Exception) {
            Log.e(TAG, "Error getting live wallpaper service name", e)
            null
        }
    }
    
    /**
     * Checks if the active live wallpaper is a known blocking service.
     * 
     * Identifies services like Glance, Samsung Dynamic Wallpaper, etc.
     * that are known to prevent wallpaper changes.
     * 
     * **Return Value:**
     * - Pair<Boolean, String>: (isBlocking, displayName)
     * - Examples:
     *   - (true, "Glance") - Known blocking service detected
     *   - (true, "Dynamic Wallpaper") - Known blocking service detected
     *   - (false, "Custom Live Wallpaper") - Unknown service, may or may not block
     *   - (false, null) - No live wallpaper active
     * 
     * @param context Application or activity context
     * @return Pair of (is blocking service, display name)
     */
    fun isKnownBlockingService(context: Context): Pair<Boolean, String?> {
        val packageName = getLiveWallpaperPackageName(context) ?: return Pair(false, null)
        
        // Check exact package match
        KNOWN_BLOCKING_SERVICES[packageName]?.let {
            Log.d(TAG, "Known blocking service detected: $it ($packageName)")
            return Pair(true, it)
        }
        
        // Check partial match (for services we might not have exact package for)
        for ((knownPackage, displayName) in KNOWN_BLOCKING_SERVICES) {
            if (packageName.contains(knownPackage, ignoreCase = true) || 
                knownPackage.contains(packageName, ignoreCase = true)) {
                Log.d(TAG, "Known blocking service detected (partial match): $displayName ($packageName)")
                return Pair(true, displayName)
            }
        }
        
        // Check for common keywords in package name
        when {
            packageName.contains("glance", ignoreCase = true) -> {
                Log.d(TAG, "Glance-like service detected: $packageName")
                return Pair(true, "Glance")
            }
            packageName.contains("lockscreen", ignoreCase = true) ||
            packageName.contains("lock.screen", ignoreCase = true) -> {
                Log.d(TAG, "Lock screen service detected: $packageName")
                return Pair(true, "Lock Screen Wallpaper")
            }
            else -> {
                Log.d(TAG, "Unknown live wallpaper service: $packageName")
                return Pair(false, "Live Wallpaper")
            }
        }
    }
    
    /**
     * Gets a user-friendly display name for the active live wallpaper.
     * 
     * **Priority:**
     * 1. Known service display name (e.g., "Glance for MI")
     * 2. Service label from WallpaperInfo
     * 3. Generic "Live Wallpaper"
     * 
     * @param context Application or activity context
     * @return Display name for user-facing messages
     */
    fun getLiveWallpaperDisplayName(context: Context): String {
        // Check if it's a known service first
        val (_, knownName) = isKnownBlockingService(context)
        if (knownName != null) {
            return knownName
        }
        
        // Try to get label from WallpaperInfo
        return try {
            val wallpaperManager = WallpaperManager.getInstance(context)
            val wallpaperInfo = wallpaperManager.wallpaperInfo
            wallpaperInfo?.loadLabel(context.packageManager)?.toString() ?: "Live Wallpaper"
        } catch (e: Exception) {
            Log.e(TAG, "Error getting live wallpaper display name", e)
            "Live Wallpaper"
        }
    }
    
    /**
     * Comprehensive diagnostic information for debugging.
     * 
     * **Use Cases:**
     * - Settings screen debug section
     * - Support requests
     * - Logging issues
     * 
     * @param context Application or activity context
     * @return Map of diagnostic information
     */
    fun getDiagnosticInfo(context: Context): Map<String, String> {
        val packageName = getLiveWallpaperPackageName(context)
        val serviceName = getLiveWallpaperServiceName(context)
        val (isBlocking, displayName) = isKnownBlockingService(context)
        
        return mapOf(
            "Live Wallpaper Active" to isLiveWallpaperActive(context).toString(),
            "Package Name" to (packageName ?: "N/A"),
            "Service Name" to (serviceName ?: "N/A"),
            "Display Name" to getLiveWallpaperDisplayName(context),
            "Is Known Blocking Service" to isBlocking.toString(),
            "Service Type" to (displayName ?: "Unknown")
        )
    }
}
