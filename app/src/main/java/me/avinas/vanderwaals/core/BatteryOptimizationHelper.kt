package me.avinas.vanderwaals.core

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.getSystemService

/**
 * Prompts the user to exempt the app from Doze battery optimization
 * so WorkManager jobs run reliably. Remembers if the user declined.
 */
object BatteryOptimizationHelper {
    
    private const val TAG = "BatteryOptimization"
    private const val PREFS_NAME = "battery_optimization_prefs"
    private const val KEY_USER_DECLINED = "user_declined_battery_exemption"
    private const val KEY_LAST_PROMPT_TIME = "last_battery_prompt_time"
    private const val PROMPT_COOLDOWN_MS = 7 * 24 * 60 * 60 * 1000L // 7 days
    
    /**
     * Android 6.0+ reports exemption via PowerManager.isIgnoringBatteryOptimizations();
     * below that the optimization isn't enforced and this always returns true.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // Battery optimization doesn't exist before Android 6.0
            return true
        }
        
        val powerManager = context.getSystemService<PowerManager>()
        val packageName = context.packageName
        
        return try {
            powerManager?.isIgnoringBatteryOptimizations(packageName) ?: false
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error checking battery optimization status", e)
            false
        }
    }
    
    /**
     * True when a prompt should be shown: not exempt, not declined
     * within the 7-day cooldown.
     */
    fun shouldPromptForExemption(context: Context): Boolean {
        if (isIgnoringBatteryOptimizations(context)) {
            return false
        }
        
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val userDeclined = prefs.getBoolean(KEY_USER_DECLINED, false)
        val lastPromptTime = prefs.getLong(KEY_LAST_PROMPT_TIME, 0)
        val currentTime = System.currentTimeMillis()
        
        if (userDeclined && (currentTime - lastPromptTime) < PROMPT_COOLDOWN_MS) {
            return false
        }
        
        return true
    }
    
    /** Records the decline and the current time so the cooldown is enforced. */
    fun recordUserDeclined(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_USER_DECLINED, true)
            .putLong(KEY_LAST_PROMPT_TIME, System.currentTimeMillis())
            .apply()
        
        android.util.Log.d(TAG, "User declined battery optimization exemption")
    }
    
    /** Records that the user was prompted. */
    fun recordPromptShown(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong(KEY_LAST_PROMPT_TIME, System.currentTimeMillis())
            .apply()
        
        android.util.Log.d(TAG, "Battery optimization prompt shown")
    }
    
    /** Clears the decline flag when the user enables auto-change or asks to be prompted again. */
    fun clearDeclineFlag(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_USER_DECLINED, false)
            .apply()
        
        android.util.Log.d(TAG, "Cleared battery optimization decline flag")
    }
    
    @SuppressLint("BatteryLife")
    fun requestBatteryOptimizationExemption(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false
        }
        
        return try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            
            android.util.Log.d(TAG, "Opened battery optimization exemption dialog")
            true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to open battery optimization settings", e)
            
            openBatteryOptimizationSettings(context)
        }
    }
    
    /** Fallback that opens the battery optimization list so the app can be whitelisted manually. */
    fun openBatteryOptimizationSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false
        }
        
        return try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            
            android.util.Log.d(TAG, "Opened battery optimization settings list")
            true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to open battery optimization settings", e)
            false
        }
    }
    
    fun getExemptionRationale(): String {
        return """
            To ensure your wallpaper changes reliably on schedule, Vanderwaals needs to be excluded from battery optimization.
            
            Without this permission:
            • Auto-change may not work after phone restart
            • Scheduled wallpaper changes may be delayed or skipped
            • Background sync may fail when screen is off
            
            With this permission:
            • Wallpapers change exactly when scheduled
            • Works reliably even after device reboot
            • Minimal battery impact (work runs briefly then stops)
            
            You can revoke this permission anytime from Android Settings → Battery → Battery Optimization.
        """.trimIndent()
    }
    
    /**
     * Many manufacturers (Xiaomi, Huawei, Oppo, Vivo, OnePlus) run aggressive task
     * killers that stop background work even with battery optimization disabled;
     * each uses a different implementation, so this is only a best-effort detection.
     */
    fun needsAutoStartPermission(context: Context): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        
        val aggressiveManufacturers = listOf(
            "xiaomi", "huawei", "oppo", "vivo", "oneplus", 
            "realme", "asus", "samsung", "letv", "honor"
        )
        
        return aggressiveManufacturers.any { manufacturer.contains(it) }
    }
    
    /**
     * Step-by-step guidance for disabling aggressive battery management
     * on the current manufacturer.
     */
    fun getAutoStartGuidance(): String {
        val manufacturer = Build.MANUFACTURER.lowercase()
        
        return when {
            manufacturer.contains("xiaomi") -> 
                "Open Security → Manage apps → Vanderwaals → Toggle 'Autostart' ON"
            
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> 
                "Open Settings → Battery → App launch → Vanderwaals → Manage manually → Enable all"
            
            manufacturer.contains("oppo") || manufacturer.contains("realme") -> 
                "Open Settings → Battery → Power saving mode → Vanderwaals → Allow background running"
            
            manufacturer.contains("vivo") -> 
                "Open i Manager → App manager → Vanderwaals → Toggle 'Auto-start' ON"
            
            manufacturer.contains("oneplus") -> 
                "Open Settings → Battery → Battery optimization → Vanderwaals → Don't optimize"
            
            manufacturer.contains("samsung") -> 
                "Open Settings → Apps → Vanderwaals → Battery → Allow background activity"
            
            manufacturer.contains("asus") -> 
                "Open Mobile Manager → PowerMaster → Auto-start Manager → Vanderwaals → Enable"
            
            else -> 
                "Check your device's battery/power management settings and allow Vanderwaals to run in background"
        }
    }
    
    /** System information for debugging battery issues. */
    fun getDiagnosticInfo(context: Context): Map<String, String> {
        return mapOf(
            "Battery Optimization Exempt" to isIgnoringBatteryOptimizations(context).toString(),
            "Android Version" to "${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})",
            "Manufacturer" to Build.MANUFACTURER,
            "Model" to Build.MODEL,
            "Needs Auto-Start Permission" to needsAutoStartPermission(context).toString(),
            "User Previously Declined" to run {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.getBoolean(KEY_USER_DECLINED, false).toString()
            },
            "Last Prompt Time" to run {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val time = prefs.getLong(KEY_LAST_PROMPT_TIME, 0)
                if (time == 0L) "Never" else java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(time)
            }
        )
    }
}
