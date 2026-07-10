package me.avinas.vanderwaals.core

import android.util.Log
import me.avinas.vanderwaals.BuildConfig

/**
 * Structured logging utility. Formats output as:
 * [TAG] message | key1=value1, key2=value2
 * Suppressed in release builds.
 */
object Logger {
    
    /**
     * Log level enum for filtering.
     */
    enum class Level {
        VERBOSE, DEBUG, INFO, WARN, ERROR
    }
    
    // Minimum log level to output (configured per build type)
    private val minLevel: Level = if (BuildConfig.DEBUG) Level.DEBUG else Level.INFO
    
    /**
     * Logs an informational message.
     * 
     * Use for normal operation events (wallpaper applied, sync completed).
     * 
     * @param tag Log tag (usually class name)
     * @param message Human-readable message
     * @param metadata Additional context as key-value pairs
     */
    fun info(tag: String, message: String, metadata: Map<String, Any> = emptyMap()) {
        if (shouldLog(Level.INFO)) {
            Log.i(tag, formatMessage(message, metadata))
        }
    }
    
    /**
     * Logs a warning message.
     * 
     * Use for recoverable errors (network timeout, cache miss).
     * 
     * @param tag Log tag
     * @param message Human-readable message
     * @param metadata Additional context
     */
    fun warn(tag: String, message: String, metadata: Map<String, Any> = emptyMap()) {
        if (shouldLog(Level.WARN)) {
            Log.w(tag, formatMessage(message, metadata))
        }
    }
    
    /**
     * Logs an error message.
     * 
     * Use for failures that prevent operation completion.
     * 
     * @param tag Log tag
     * @param message Human-readable message
     * @param error Optional exception/error
     * @param metadata Additional context
     */
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
    
    /**
     * Logs a debug message (only in debug builds).
     * 
     * Use for detailed debugging information.
     * 
     * @param tag Log tag
     * @param message Human-readable message
     * @param metadata Additional context
     */
    fun debug(tag: String, message: String, metadata: Map<String, Any> = emptyMap()) {
        if (shouldLog(Level.DEBUG)) {
            Log.d(tag, formatMessage(message, metadata))
        }
    }
    
    /**
     * Logs a verbose message (only in debug builds).
     * 
     * Use for very detailed tracing (every step of algorithm).
     * 
     * @param tag Log tag
     * @param message Human-readable message
     * @param metadata Additional context
     */
    fun verbose(tag: String, message: String, metadata: Map<String, Any> = emptyMap()) {
        if (shouldLog(Level.VERBOSE)) {
            Log.v(tag, formatMessage(message, metadata))
        }
    }
    
    /**
     * Records a breadcrumb for crash reporting context.
     * 
     * Breadcrumbs are lightweight events that help understand the sequence
     * of actions leading to a crash.
     * 
     * @param category Breadcrumb category (e.g., "navigation", "network", "user_action")
     * @param message Description of the event
     * @param metadata Additional data
     */
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
    
    /**
     * Checks if a given level should be logged based on current configuration.
     */
    private fun shouldLog(level: Level): Boolean {
        return level.ordinal >= minLevel.ordinal
    }
    
    /**
     * Formats a log message with metadata.
     * 
     * @param message The core message
     * @param metadata Key-value pairs to append
     * @return Formatted string: "message | metadata: key1=value1, key2=value2"
     */
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
