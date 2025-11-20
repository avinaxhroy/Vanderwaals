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
 * Helper class for managing battery optimization settings.
 * 
 * Battery optimization can prevent WorkManager from running scheduled tasks
 * after device restart or when app is in background for extended periods.
 * 
 * **Why This Matters:**
 * - Android Doze mode restricts background work for battery optimization
 * - WorkManager tasks may be deferred or skipped entirely
 * - Auto-change wallpaper feature requires reliable background execution
 * - After phone restart, scheduled work may not resume unless exempted
 * 
 * **How It Works:**
 * 1. Check if app is exempt from battery optimization
 * 2. If not exempt, show dialog explaining the need
 * 3. Open system settings for user to whitelist the app
 * 4. Persist user's decision to avoid repeated prompts
 * 
 * **Best Practices:**
 * - Only request when auto-change is enabled
 * - Explain clearly why exemption is needed
 * - Respect user's choice (don't ask repeatedly)
 * - Provide manual trigger in settings
 * 
 * @property context Application context for system service access
 */
object BatteryOptimizationHelper {
    
    private const val TAG = "BatteryOptimization"
    private const val PREFS_NAME = "battery_optimization_prefs"
    private const val KEY_USER_DECLINED = "user_declined_battery_exemption"
    private const val KEY_LAST_PROMPT_TIME = "last_battery_prompt_time"
    private const val PROMPT_COOLDOWN_MS = 7 * 24 * 60 * 60 * 1000L // 7 days
    
    /**
     * Checks if app is currently ignoring battery optimizations.
     * 
     * **Return Values:**
     * - `true`: App is whitelisted, background work will run reliably
     * - `false`: App is subject to battery optimization, work may be restricted
     * 
     * **Android Versions:**
     * - Android 6.0+ (API 23+): Uses PowerManager.isIgnoringBatteryOptimizations()
     * - Below Android 6.0: Always returns true (optimization not enforced)
     * 
     * @param context Application context
     * @return true if battery optimization is disabled for this app
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
     * Checks if we should prompt user for battery exemption.
     * 
     * **Conditions:**
     * - Not currently exempt AND
     * - User hasn't declined AND
     * - Cooldown period expired (7 days since last prompt)
     * 
     * This prevents annoying users with repeated prompts.
     * 
     * @param context Application context
     * @return true if prompt should be shown
     */
    fun shouldPromptForExemption(context: Context): Boolean {
        if (isIgnoringBatteryOptimizations(context)) {
            return false
        }
        
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val userDeclined = prefs.getBoolean(KEY_USER_DECLINED, false)
        val lastPromptTime = prefs.getLong(KEY_LAST_PROMPT_TIME, 0)
        val currentTime = System.currentTimeMillis()
        
        // Don't prompt if user declined and cooldown not expired
        if (userDeclined && (currentTime - lastPromptTime) < PROMPT_COOLDOWN_MS) {
            return false
        }
        
        return true
    }
    
    /**
     * Records that user declined battery exemption.
     * 
     * Stores timestamp to implement cooldown period before asking again.
     * 
     * @param context Application context
     */
    fun recordUserDeclined(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_USER_DECLINED, true)
            .putLong(KEY_LAST_PROMPT_TIME, System.currentTimeMillis())
            .apply()
        
        android.util.Log.d(TAG, "User declined battery optimization exemption")
    }
    
    /**
     * Records that user was prompted (but didn't necessarily grant).
     * 
     * Updates timestamp for cooldown tracking.
     * 
     * @param context Application context
     */
    fun recordPromptShown(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong(KEY_LAST_PROMPT_TIME, System.currentTimeMillis())
            .apply()
        
        android.util.Log.d(TAG, "Battery optimization prompt shown")
    }
    
    /**
     * Clears user's previous decline choice.
     * 
     * Call this when user manually enables auto-change or explicitly
     * requests to be prompted again from settings.
     * 
     * @param context Application context
     */
    fun clearDeclineFlag(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_USER_DECLINED, false)
            .apply()
        
        android.util.Log.d(TAG, "Cleared battery optimization decline flag")
    }
    
    /**
     * Opens system settings for battery optimization exemption.
     * 
     * **Intent Actions:**
     * - Android 6.0+: ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
     *   (Opens dialog: "Allow [app] to ignore battery optimization?")
     * 
     * **User Flow:**
     * 1. Dialog appears with app name
     * 2. User taps "Allow" → App whitelisted
     * 3. User taps "Deny" → Returns to app
     * 
     * **Manifest Requirement:**
     * ```xml
     * <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"/>
     * ```
     * 
     * @param context Application context
     * @return true if intent was launched successfully
     */
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
            
            // Fallback: Open general battery optimization settings
            openBatteryOptimizationSettings(context)
        }
    }
    
    /**
     * Opens general battery optimization settings (fallback).
     * 
     * Shows list of all apps with their battery optimization status.
     * User can manually find and whitelist the app.
     * 
     * @param context Application context
     * @return true if settings were opened
     */
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
    
    /**
     * Gets user-friendly explanation for battery exemption need.
     * 
     * **Use Cases:**
     * - Show in dialog before requesting exemption
     * - Display in settings explanation section
     * - Help documentation
     * 
     * @return Multi-line explanation string
     */
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
     * Checks if auto-start permission is granted (manufacturer-specific).
     * 
     * **Manufacturer Background Restrictions:**
     * Many manufacturers (Xiaomi, Huawei, Oppo, Vivo, OnePlus) have
     * aggressive task killers that prevent background work even when
     * battery optimization is disabled.
     * 
     * **Detection:**
     * This is hard to detect programmatically because each manufacturer
     * uses different implementations. We can only provide guidance.
     * 
     * @param context Application context
     * @return Best-effort detection (may return false positives)
     */
    fun needsAutoStartPermission(context: Context): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        
        // List of manufacturers known for aggressive task killing
        val aggressiveManufacturers = listOf(
            "xiaomi", "huawei", "oppo", "vivo", "oneplus", 
            "realme", "asus", "samsung", "letv", "honor"
        )
        
        return aggressiveManufacturers.any { manufacturer.contains(it) }
    }
    
    /**
     * Gets manufacturer-specific auto-start guidance.
     * 
     * Provides instructions for disabling aggressive battery management
     * on various Android manufacturers.
     * 
     * @return Manufacturer-specific instructions, or generic guidance
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
    
    /**
     * Comprehensive system information for debugging battery issues.
     * 
     * @param context Application context
     * @return Map of diagnostic information
     */
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
