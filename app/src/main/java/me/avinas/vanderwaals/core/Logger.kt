package me.avinas.vanderwaals.core

import android.util.Log
import me.avinas.vanderwaals.BuildConfig

/**
 * Structured logging utility. Formats output as:
 * [TAG] message | key1=value1, key2=value2
 * Suppressed in release builds.
 */
object Logger {
    
    enum class Level {
        VERBOSE, DEBUG, INFO, WARN, ERROR
    }
    
    // Minimum log level to output (configured per build type)
    private val minLevel: Level = if (BuildConfig.DEBUG) Level.DEBUG else Level.INFO
    
    /** For normal operation events (wallpaper applied, sync completed). */
    fun info(tag: String, message: String, metadata: Map<String, Any> = emptyMap()) {
        if (shouldLog(Level.INFO)) {
            Log.i(tag, formatMessage(message, metadata))
        }
    }
    
    /** For recoverable errors (network timeout, cache miss). */
    fun warn(tag: String, message: String, metadata: Map<String, Any> = emptyMap()) {
        if (shouldLog(Level.WARN)) {
            Log.w(tag, formatMessage(message, metadata))
        }
    }
    
    /** For failures that prevent operation completion. */
    fun error(
        tag: String,
        message: String,
        error: Throwable? = null,
        metadata: Map<String, Any> = emptyMap()
    ) {
        if (shouldLog(Level.ERROR)) {
            val formattedMessage = formatMessage(message, metadata)
            if (error != null) {
                Log.e(tag, formattedMessage, error)
            } else {
                Log.e(tag, formattedMessage)
            }
        }
    }
    
    /** Debug-only; for detailed debugging information. */
    fun debug(tag: String, message: String, metadata: Map<String, Any> = emptyMap()) {
        if (shouldLog(Level.DEBUG)) {
            Log.d(tag, formatMessage(message, metadata))
        }
    }
    
    /** Debug-only; for very detailed tracing (every step of algorithm). */
    fun verbose(tag: String, message: String, metadata: Map<String, Any> = emptyMap()) {
        if (shouldLog(Level.VERBOSE)) {
            Log.v(tag, formatMessage(message, metadata))
        }
    }
    
    /** Records a lightweight event for crash reporting context. */
    fun recordBreadcrumb(
        category: String,
        message: String,
        metadata: Map<String, Any> = emptyMap()
    ) {
        // Privacy-by-design: breadcrumbs are debug-only. Do NOT forward to any
        // third-party crash-reporting/analytics SDK, as metadata may contain
        // user context. Kept local to avoid leaking data off-device.
        if (BuildConfig.DEBUG) {
            debug("Breadcrumb[$category]", message, metadata)
        }
    }
    
    private fun shouldLog(level: Level): Boolean {
        return level.ordinal >= minLevel.ordinal
    }
    
    private fun formatMessage(message: String, metadata: Map<String, Any>): String {
        return if (metadata.isEmpty()) {
            message
        } else {
            val metadataStr = metadata.entries.joinToString(", ") { (key, value) ->
                "$key=$value"
            }
            "$message | $metadataStr"
        }
    }
}
