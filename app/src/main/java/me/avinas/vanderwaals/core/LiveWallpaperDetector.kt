package me.avinas.vanderwaals.core

import android.app.WallpaperManager
import android.content.Context
import android.provider.Settings
import android.util.Log

/**
 * Detects active live wallpapers (Glance, Samsung Dynamic Wallpaper, etc.)
 * that block WallpaperManager.setBitmap(). Reports the service name
 * so the user can be prompted to disable it.
 */
object LiveWallpaperDetector {
    
    private const val TAG = "LiveWallpaperDetector"
    
    /**
     * Samsung system packages that may return non-null wallpaperInfo
     * but are NOT user-facing live wallpapers and should NOT trigger warnings.
     * 
     * These are Samsung's internal wallpaper system services that run even when
     * the user has a static wallpaper set.
     */
    private val SAMSUNG_SYSTEM_EXCLUSIONS = setOf(
        // Resource/asset packages
        "com.samsung.android.wallpaper.res",
        
        // System wallpaper services (not user-set live wallpapers)
        "com.samsung.android.wallpaper.live",
        "com.samsung.android.wallpaper",
        
        // Lock screen specific (doesn't block home screen changes)
        "com.samsung.android.livelock",
        
        // Wallpaper picker/utility components
        "com.samsung.android.app.wallpapericons",
        "com.sec.android.wallpapercropper",
        "com.sec.android.wallpaperpicker",
        
        // Theme management (not live wallpapers)
        "com.samsung.android.themecenter",
        "com.samsung.android.themestore",
        
        // Other Samsung system packages that might register as wallpaper services
        "com.sec.android.daemonapp",
        "com.samsung.android.forest",
        "com.samsung.android.homemode",
        
        // Samsung's default wallpaper provider service
        "com.samsung.android.app.wallpaper"
    )
    
    /**
     * Checks if the device is a Samsung device.
     */
    private fun isSamsungDevice(): Boolean {
        return android.os.Build.MANUFACTURER.equals("samsung", ignoreCase = true)
    }
    
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
            
            if (wallpaperInfo == null) {
                Log.d(TAG, "No live wallpaper active (static wallpaper)")
                return false
            }
            
            val packageName = wallpaperInfo.packageName
            
            // Check if this is a Samsung system service that should be excluded
            // Samsung devices have internal wallpaper services that return non-null wallpaperInfo
            // even when the user has a static wallpaper set
            if (isSamsungDevice() && packageName != null && SAMSUNG_SYSTEM_EXCLUSIONS.contains(packageName)) {
                Log.d(TAG, "Samsung system package excluded from detection: $packageName")
                return false
            }
            
            // Additional check: if it's Samsung and the package starts with Samsung/SEC prefixes
            // but isn't in our known blocking services, it's likely a system component
            if (isSamsungDevice() && packageName != null) {
                val isSamsungSystemPackage = (packageName.startsWith("com.samsung.android.") || 
                                              packageName.startsWith("com.sec.android.")) &&
                                             !KNOWN_BLOCKING_SERVICES.containsKey(packageName)
                if (isSamsungSystemPackage) {
                    // Check if it contains keywords that suggest it's NOT a user-facing live wallpaper
                    val isSystemComponent = packageName.contains("wallpaper", ignoreCase = true) &&
                                           !packageName.contains("dynamic", ignoreCase = true) &&
                                           !packageName.contains("glance", ignoreCase = true) &&
                                           !packageName.contains("depth", ignoreCase = true)
                    if (isSystemComponent) {
                        Log.d(TAG, "Samsung system wallpaper component excluded: $packageName")
                        return false
                    }
                }
            }
            
            Log.d(TAG, "Live wallpaper detected: $packageName")
            true
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
     * Checks if a proprietary blocking service is active via system settings.
     * 
     * **Why this is needed:**
     * Some services (Samsung Dynamic Lock Screen, Xiaomi Glance) do not run as 
     * standard Live Wallpapers but still block wallpaper changes.
     * 
     * **Heuristics:**
     * - Checks specific system settings keys (e.g., "dls_state", "wallpaper_carousel_switch")
     * - Checks for presence of specific packages that might indicate the feature is active
     * 
     * @param context Application context
     * @return Pair of (is blocking, display name)
     */
    fun isProprietaryBlockerActive(context: Context): Pair<Boolean, String?> {
        try {
            val contentResolver = context.contentResolver
            
            // --- Samsung Dynamic Lock Screen ---
            // Key: "dls_state" (0 = off, 1/2 = on)
            try {
                val dlsState = Settings.System.getInt(contentResolver, "dls_state", 0)
                if (dlsState > 0) {
                    Log.d(TAG, "Samsung Dynamic Lock Screen detected (dls_state=$dlsState)")
                    return Pair(true, "Samsung Dynamic Lock Screen")
                }
            } catch (e: Exception) {
                // Key might not exist on non-Samsung devices
            }
            
            // --- Xiaomi Glance / Wallpaper Carousel ---
            // Key: "miui_dls_enable" or "wallpaper_carousel_switch"
            try {
                val miuiDls = Settings.System.getInt(contentResolver, "miui_dls_enable", 0)
                if (miuiDls == 1) {
                    Log.d(TAG, "Xiaomi Wallpaper Carousel detected (miui_dls_enable=1)")
                    return Pair(true, "Xiaomi Wallpaper Carousel")
                }
            } catch (e: Exception) { }
            
            try {
                val carouselSwitch = Settings.Secure.getInt(contentResolver, "wallpaper_carousel_switch", 0)
                if (carouselSwitch == 1) {
                    Log.d(TAG, "Xiaomi Wallpaper Carousel detected (wallpaper_carousel_switch=1)")
                    return Pair(true, "Xiaomi Wallpaper Carousel")
                }
            } catch (e: Exception) { }

            // --- Generic Check for Glance Package Presence ---
            // If we can't read settings, check if the package is just present? 
            // No, presence doesn't mean active. But some users might want to know.
            // For now, we rely on settings keys as they are more accurate for "active" state.
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking proprietary blockers", e)
        }
        
        return Pair(false, null)
    }

    /**
     * Unified check for any blocking service (Live Wallpaper OR Proprietary).
     * 
     * @param context Application context
     * @return Pair of (is blocking, display name)
     */
    fun detectBlockingService(context: Context): Pair<Boolean, String?> {
        // 1. Check standard Live Wallpaper (with Samsung exclusions)
        if (isLiveWallpaperActive(context)) {
            val (isKnown, name) = isKnownBlockingService(context)
            if (isKnown) {
                return Pair(true, name)
            }
            // Even if not "known", it's a live wallpaper, so it blocks static wallpaper changes.
            // We return true, but maybe with a generic name if not in our list.
            return Pair(true, getLiveWallpaperDisplayName(context))
        }
        
        // 2. Check proprietary blockers (Samsung DLS, Xiaomi Glance)
        val (isProprietary, proprietaryName) = isProprietaryBlockerActive(context)
        if (isProprietary) {
            return Pair(true, proprietaryName)
        }
        
        return Pair(false, null)
    }
    
    /**
     * Checks if live wallpaper is blocking AFTER a failed wallpaper change attempt.
     * 
     * This is the most reliable detection method - we only check for live wallpaper
     * blocking when the wallpaper change actually fails. This avoids false positives
     * from manufacturer-specific system services.
     * 
     * **How it works:**
     * 1. Called after setBitmap() completes but verification shows wallpaper didn't change
     * 2. Checks if a live wallpaper is currently set (raw check, no exclusions)
     * 3. If live wallpaper exists, it's definitely blocking our static wallpaper
     * 
     * @param context Application context
     * @return Pair of (is blocking, display name) - only returns true if definitely blocking
     */
    fun detectBlockingAfterFailure(context: Context): Pair<Boolean, String?> {
        try {
            val wallpaperManager = WallpaperManager.getInstance(context)
            val wallpaperInfo = wallpaperManager.wallpaperInfo
            
            // If wallpaperInfo is non-null after our setBitmap failed, 
            // it means a live wallpaper is definitely active and blocking us
            if (wallpaperInfo != null) {
                val packageName = wallpaperInfo.packageName ?: "unknown"
                Log.d(TAG, "Confirmed live wallpaper blocking after failure: $packageName")
                
                // Get display name for user message
                val displayName = getLiveWallpaperDisplayName(context)
                return Pair(true, displayName)
            }
            
            // Also check proprietary blockers
            val (isProprietary, proprietaryName) = isProprietaryBlockerActive(context)
            if (isProprietary) {
                Log.d(TAG, "Confirmed proprietary blocker after failure: $proprietaryName")
                return Pair(true, proprietaryName)
            }
            
            // No live wallpaper found - failure was due to something else
            Log.d(TAG, "No live wallpaper detected after failure - other cause")
            return Pair(false, null)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting blocking service after failure", e)
            return Pair(false, null)
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
        val (isProprietary, proprietaryName) = isProprietaryBlockerActive(context)
        
        return mapOf(
            "Live Wallpaper Active" to isLiveWallpaperActive(context).toString(),
            "Package Name" to (packageName ?: "N/A"),
            "Service Name" to (serviceName ?: "N/A"),
            "Display Name" to getLiveWallpaperDisplayName(context),
            "Is Known Blocking Service" to isBlocking.toString(),
            "Service Type" to (displayName ?: "Unknown"),
            "Proprietary Blocker Active" to isProprietary.toString(),
            "Proprietary Blocker Name" to (proprietaryName ?: "N/A")
        )
    }
}
