package me.avinas.vanderwaals.core

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * Helper class for opening brand-specific settings pages to disable live wallpapers.
 * 
 * **Why This Matters:**
 * - Each Android manufacturer has different settings paths for live wallpapers
 * - Direct intent navigation improves user experience (vs manual navigation)
 * - Fallback mechanisms ensure users can always find the right settings
 * 
 * **Supported Brands:**
 * - Xiaomi/Redmi: Lock screen settings or Glance settings
 * - Samsung: Wallpaper Services menu
 * - Realme/OPPO: App management for Glance
 * - Vivo: Lock screen poster settings
 * - Generic: Live wallpaper picker or wallpaper settings
 * 
 * **Pattern:**
 * Similar to BatteryOptimizationHelper, provides brand-specific intent handling
 * with fallback mechanism for robustness.
 * 
 * @see BatteryOptimizationHelper
 */
object BrandSettingsIntents {
    
    private const val TAG = "BrandSettingsIntents"
    
    /**
     * Gets the device manufacturer name in lowercase.
     * 
     * **Examples:**
     * - "xiaomi", "samsung", "realme", "oppo", "vivo", "google"
     * 
     * @return Manufacturer name (lowercase)
     */
    fun getDeviceManufacturer(): String {
        return Build.MANUFACTURER.lowercase()
    }
    
    /**
     * Gets the device brand name in lowercase.
     * 
     * May differ from manufacturer (e.g., Redmi is a brand of Xiaomi).
     * 
     * @return Brand name (lowercase)
     */
    fun getDeviceBrand(): String {
        return Build.BRAND.lowercase()
    }
    
    /**
     * Opens the appropriate settings page for disabling live wallpaper.
     * 
     * **Intent Strategy:**
     * 1. Try brand-specific intent (most direct path)
     * 2. If fails, try generic wallpaper settings
     * 3. If fails, try app settings for live wallpaper package
     * 4. Return false if all fail (caller should show instructions)
     * 
     * **User Flow:**
     * - Opens settings app to the most relevant page
     * - User can disable/uninstall live wallpaper service
     * - Returns to app to verify success
     * 
     * @param context Application context
     * @param liveWallpaperPackage Optional package name of live wallpaper to disable
     * @return true if settings were opened successfully, false otherwise
     */
    fun openLiveWallpaperSettings(context: Context, liveWallpaperPackage: String? = null): Boolean {
        val manufacturer = getDeviceManufacturer()
        
        Log.d(TAG, "Opening live wallpaper settings for manufacturer: $manufacturer")
        
        // Try brand-specific intent first
        val brandSpecificSuccess = when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> {
                openXiaomiLockScreenSettings(context) || 
                openXiaomiWallpaperSettings(context)
            }
            
            manufacturer.contains("samsung") -> {
                openSamsungWallpaperServices(context) ||
                openSamsungWallpaperSettings(context)
            }
            
            manufacturer.contains("realme") || manufacturer.contains("oppo") -> {
                // First try to open Glance app settings if package is known
                if (liveWallpaperPackage != null) {
                    openAppSettings(context, liveWallpaperPackage)
                } else {
                    openRealmeLockScreenSettings(context) ||
                    openGenericWallpaperSettings(context)
                }
            }
            
            manufacturer.contains("vivo") -> {
                openVivoLockScreenSettings(context) ||
                openGenericWallpaperSettings(context)
            }
            
            manufacturer.contains("oneplus") -> {
                openOnePlusWallpaperSettings(context) ||
                openGenericWallpaperSettings(context)
            }
            
            else -> {
                // Generic Android
                openLiveWallpaperPicker(context) ||
                openGenericWallpaperSettings(context)
            }
        }
        
        if (brandSpecificSuccess) {
            Log.d(TAG, "Brand-specific settings opened successfully")
            return true
        }
        
        Log.w(TAG, "Brand-specific intent failed, trying fallbacks")
        
        // Fallback 1: Try generic wallpaper settings
        if (openGenericWallpaperSettings(context)) {
            Log.d(TAG, "Generic wallpaper settings opened")
            return true
        }
        
        // Fallback 2: Try live wallpaper picker
        if (openLiveWallpaperPicker(context)) {
            Log.d(TAG, "Live wallpaper picker opened")
            return true
        }
        
        // Fallback 3: Try app settings for the live wallpaper package
        if (liveWallpaperPackage != null && openAppSettings(context, liveWallpaperPackage)) {
            Log.d(TAG, "App settings opened for package: $liveWallpaperPackage")
            return true
        }
        
        Log.e(TAG, "All intents failed to open settings")
        return false
    }
    
    /**
     * Opens Xiaomi/Redmi lock screen settings.
     * 
     * **Path:** Settings → Lock screen
     * 
     * @param context Application context
     * @return true if successful
     */
    private fun openXiaomiLockScreenSettings(context: Context): Boolean {
        return try {
            val intent = Intent("miui.intent.action.LOCK_SCREEN_SETTINGS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.d(TAG, "Xiaomi lock screen settings intent not found")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error opening Xiaomi lock screen settings", e)
            false
        }
    }
    
    /**
     * Opens Xiaomi wallpaper settings.
     * 
     * @param context Application context
     * @return true if successful
     */
    private fun openXiaomiWallpaperSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_HOME_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.d(TAG, "Xiaomi wallpaper settings not available")
            false
        }
    }
    
    /**
     * Opens Samsung Wallpaper Services menu.
     * 
     * **Path:** Settings → Lock screen → Wallpaper services
     * 
     * @param context Application context
     * @return true if successful
     */
    private fun openSamsungWallpaperServices(context: Context): Boolean {
        return try {
            // Samsung-specific intent for wallpaper services
            val intent = Intent("com.samsung.android.app.aodservice.settings.LOCKSCREEN_SERVICES").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.d(TAG, "Samsung wallpaper services intent not found")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error opening Samsung wallpaper services", e)
            false
        }
    }
    
    /**
     * Opens Samsung wallpaper settings.
     * 
     * @param context Application context
     * @return true if successful
     */
    private fun openSamsungWallpaperSettings(context: Context): Boolean {
        return try {
            val intent = Intent("android.settings.WALLPAPER_SETTINGS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.d(TAG, "Samsung wallpaper settings not available")
            false
        }
    }
    
    /**
     * Opens Realme/OPPO lock screen settings.
     * 
     * **Path:** Settings → Home screen & Lock screen
     * 
     * @param context Application context
     * @return true if successful
     */
    private fun openRealmeLockScreenSettings(context: Context): Boolean {
        return try {
            val intent = Intent("com.oppo.launcher.action.LOCKSCREEN_SETTINGS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.d(TAG, "Realme lock screen settings not available")
            false
        }
    }
    
    /**
     * Opens Vivo lock screen settings.
     * 
     * **Path:** Settings → Lock screen & wallpaper
     * 
     * @param context Application context
     * @return true if successful
     */
    private fun openVivoLockScreenSettings(context: Context): Boolean {
        return try {
            val intent = Intent("vivo.intent.action.LOCK_SCREEN_SETTINGS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.d(TAG, "Vivo lock screen settings not available")
            false
        }
    }
    
    /**
     * Opens OnePlus wallpaper settings.
     * 
     * @param context Application context
     * @return true if successful
     */
    private fun openOnePlusWallpaperSettings(context: Context): Boolean {
        return try {
            val intent = Intent("android.settings.WALLPAPER_SETTINGS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.d(TAG, "OnePlus wallpaper settings not available")
            false
        }
    }
    
    /**
     * Opens generic Android wallpaper settings.
     * 
     * **Standard Android Action:** Settings.ACTION_WALLPAPER_SETTINGS
     * 
     * @param context Application context
     * @return true if successful
     */
    private fun openGenericWallpaperSettings(context: Context): Boolean {
        return try {
            val intent = Intent("android.settings.WALLPAPER_SETTINGS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.d(TAG, "Generic wallpaper settings not available")
            false
        }
    }
    
    /**
     * Opens live wallpaper picker (shows all available live wallpapers).
     * 
     * User can select "None" or different wallpaper from here.
     * 
     * @param context Application context
     * @return true if successful
     */
    @SuppressLint("QueryPermissionsNeeded")
    private fun openLiveWallpaperPicker(context: Context): Boolean {
        return try {
            val intent = Intent(android.app.WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            // Check if any app can handle this intent
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                true
            } else {
                Log.d(TAG, "No app can handle live wallpaper picker intent")
                false
            }
        } catch (e: Exception) {
            Log.d(TAG, "Live wallpaper picker not available")
            false
        }
    }
    
    /**
     * Opens app settings page for a specific package.
     * 
     * **Use Cases:**
     * - Open Glance app settings to uninstall/disable
     * - Fallback when direct settings intents fail
     * 
     * **User Actions:**
     * - Force stop
     * - Uninstall
     * - Disable
     * - Clear data
     * 
     * @param context Application context
     * @param packageName Package name of app to open settings for
     * @return true if successful
     */
    fun openAppSettings(context: Context, packageName: String): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "Opened app settings for: $packageName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app settings for: $packageName", e)
            false
        }
    }
    
    /**
     * Gets brand-specific instructions for disabling live wallpaper.
     * 
     * **Format:**
     * - Step-by-step instructions
     * - Clear, concise language
     * - Matches actual device settings path
     * 
     * **Use Cases:**
     * - Show in instruction dialog
     * - Help/FAQ section
     * - Support documentation
     * 
     * @return Formatted instruction string
     */
    fun getBrandSpecificInstructions(): String {
        val manufacturer = getDeviceManufacturer()
        
        return when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> """
                **Xiaomi/Redmi Instructions:**
                
                1. Open **Settings** app
                2. Go to **Lock screen & password** or **Lock screen**
                3. Look for **Glance for MI** or **Wallpaper carousel**
                4. Toggle it **OFF** or select **None**
                5. Return to Vanderwaals app
                
                Alternatively:
                • Go to **Settings → Home screen → Wallpaper**
                • Select a static wallpaper instead of Glance
            """.trimIndent()
            
            manufacturer.contains("samsung") -> """
                **Samsung Instructions:**
                
                1. Open **Settings** app
                2. Go to **Lock screen**
                3. Tap **Wallpaper services**
                4. Select **None** instead of Glance or Dynamic Wallpaper
                5. Go back and select a static wallpaper
                6. Return to Vanderwaals app
                
                Or:
                • Long-press home screen → **Wallpapers**
                • Choose a static wallpaper
            """.trimIndent()
            
            manufacturer.contains("realme") || manufacturer.contains("oppo") -> """
                **Realme/OPPO Instructions:**
                
                1. Open **Settings** app
                2. Go to **Home screen & Lock screen magazine**
                3. Find **Glance for Realme** or **Lock screen magazine**
                4. Toggle it **OFF**
                5. Return to Vanderwaals app
                
                Alternative method:
                • Go to **Settings → Apps → Glance**
                • Tap **Disable** or **Uninstall**
            """.trimIndent()
            
            manufacturer.contains("vivo") -> """
                **Vivo Instructions:**
                
                1. Open **Settings** app
                2. Go to **Lock screen & wallpaper**
                3. Find **Lock screen poster** or **Dynamic wallpaper**
                4. Turn it **OFF** or select **None**
                5. Choose a static wallpaper
                6. Return to Vanderwaals app
            """.trimIndent()
            
            manufacturer.contains("oneplus") -> """
                **OnePlus Instructions:**
                
                1. Open **Settings** app
                2. Go to **Wallpaper & style**
                3. If using a live wallpaper, select a static one
                4. Return to Vanderwaals app
            """.trimIndent()
            
            else -> """
                **General Android Instructions:**
                
                1. Open **Settings** app
                2. Go to **Wallpaper** or **Display → Wallpaper**
                3. If a live wallpaper is active, select a static wallpaper
                4. Or go to **Apps**, find the live wallpaper app, and disable it
                5. Return to Vanderwaals app
                
                **Tip:** Look for settings related to:
                • Lock screen wallpaper
                • Dynamic wallpaper
                • Glance
                • Wallpaper services
            """.trimIndent()
        }
    }
}
