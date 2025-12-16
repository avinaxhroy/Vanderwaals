package me.avinas.vanderwaals.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * Helper for Samsung-specific power management issues.
 * 
 * Samsung devices (especially One UI 5.x on S23 and newer) have aggressive
 * power management that can kill foreground services even when battery
 * optimization is disabled.
 * 
 * This helper provides:
 * - Detection of Samsung devices
 * - Guidance to Samsung's app power management settings
 * - Detection of One UI version
 */
object SamsungPowerHelper {
    private const val TAG = "SamsungPowerHelper"
    
    /**
     * Checks if the device is manufactured by Samsung.
     */
    fun isSamsungDevice(): Boolean {
        return Build.MANUFACTURER.equals("samsung", ignoreCase = true)
    }
    
    /**
     * Gets the One UI version if available.
     * 
     * @return One UI version string (e.g., "5.1") or null if not available
     */
    fun getOneUIVersion(): String? {
        return try {
            val semPlatformInt = Build::class.java.getField("VERSION").type
                .getField("SEM_PLATFORM_INT")
                .getInt(null)
            
            // SEM_PLATFORM_INT format: XXYYZZ where XX.YY is One UI version
            val major = semPlatformInt / 10000
            val minor = (semPlatformInt % 10000) / 100
            "$major.$minor"
        } catch (e: Exception) {
            Log.w(TAG, "Unable to get One UI version: ${e.message}")
            null
        }
    }
    
    /**
     * Attempts to open Samsung's Device Care > Battery > App power management.
     * 
     * @param context Application context
     * @return true if intent was launched, false if failed
     */
    fun openSamsungBatterySettings(context: Context): Boolean {
        return try {
            // Try Samsung-specific Device Care intent first
            val samsungIntent = Intent().apply {
                component = android.content.ComponentName(
                    "com.samsung.android.lool",
                    "com.samsung.android.sm.battery.ui.BatteryActivity"
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            val pm = context.packageManager
            if (samsungIntent.resolveActivity(pm) != null) {
                context.startActivity(samsungIntent)
                Log.d(TAG, "Opened Samsung Device Care battery settings")
                return true
            }
            
            // Fallback: Try opening app details
            openAppDetailsSettings(context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open Samsung battery settings", e)
            // Final fallback
            openAppDetailsSettings(context)
        }
    }
    
    /**
     * Opens app details settings as a fallback.
     */
    private fun openAppDetailsSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Log.d(TAG, "Opened app details settings")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app details settings", e)
            false
        }
    }
    
    /**
     * Checks if the app is being battery-restricted by Samsung.
     * 
     * Returns true if:
     * - Device is Samsung AND
     * - Battery optimization is enabled for the app
     * 
     * Note: This doesn't detect Samsung's "Sleeping apps" list,
     * which requires manual user verification.
     */
    fun isBatteryRestricted(context: Context): Boolean {
        if (!isSamsungDevice()) return false
        
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return !pm.isIgnoringBatteryOptimizations(context.packageName)
    }
    
    /**
     * Gets user-friendly instructions for Samsung power management.
     */
    fun getSamsungPowerInstructions(): String {
        return """
            To ensure wallpaper changes work reliably on Samsung devices:
            
            1. Open Settings > Apps > Vanderwaals
            2. Tap "Battery" 
            3. Select "Unrestricted"
            
            Additionally:
            1. Open Settings > Battery and device care
            2. Tap "Battery"
            3. Tap "Background usage limits"
            4. Remove Vanderwaals from "Sleeping apps" and "Deep sleeping apps"
        """.trimIndent()
    }
    
    /**
     * Logs Samsung device information for debugging.
     */
    fun logDeviceInfo() {
        if (!isSamsungDevice()) {
            Log.d(TAG, "Not a Samsung device: ${Build.MANUFACTURER}")
            return
        }
        
        Log.d(TAG, """
            Samsung Device Info:
            - Model: ${Build.MODEL}
            - Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
            - One UI: ${getOneUIVersion() ?: "Unknown"}
        """.trimIndent())
    }
}
