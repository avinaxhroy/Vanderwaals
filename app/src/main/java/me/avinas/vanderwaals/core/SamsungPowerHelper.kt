package me.avinas.vanderwaals.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * Samsung devices (especially One UI 5.x on S23 and newer) run aggressive power
 * management that can kill foreground services even with battery optimization
 * disabled; this helper detects that and guides the user to the right settings.
 */
object SamsungPowerHelper {
    private const val TAG = "SamsungPowerHelper"
    
    fun isSamsungDevice(): Boolean {
        return Build.MANUFACTURER.equals("samsung", ignoreCase = true)
    }
    
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
    
    fun openSamsungBatterySettings(context: Context): Boolean {
        return try {
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
            
            openAppDetailsSettings(context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open Samsung battery settings", e)
            // Final fallback
            openAppDetailsSettings(context)
        }
    }
    
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
     * True on Samsung devices with battery optimization enabled. Does not detect
     * Samsung's "Sleeping apps" list, which requires manual user verification.
     */
    fun isBatteryRestricted(context: Context): Boolean {
        if (!isSamsungDevice()) return false
        
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return !pm.isIgnoringBatteryOptimizations(context.packageName)
    }
    
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
