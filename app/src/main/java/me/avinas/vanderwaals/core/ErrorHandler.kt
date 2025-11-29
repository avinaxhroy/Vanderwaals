package me.avinas.vanderwaals.core

import android.database.sqlite.SQLiteException
import android.util.Log
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Centralized error handling for Vanderwaals backend operations.
 * 
 * Provides:
 * - Consistent error classification across all backend components
 * - Smart recovery strategies based on error type
 * - Retry logic with exponential backoff
 * - Structured error logging with context
 * 
 * **Usage:**
 * ```kotlin
 * try {
 *     // Risky operation
 *     downloadWallpaper(url)
 * } catch (e: Exception) {
 *     val error = ErrorHandler.classify(e, "WallpaperDownload")
 *     val action = ErrorHandler.determineRecoveryAction(error, attemptCount)
 *     
 *     when (action) {
 *         is ErrorRecoveryAction.Retry -> Result.retry()
 *         is ErrorRecoveryAction.RetryWithBackoff -> 
 *             Result.retry().copy(backoffDelayMillis = action.delayMs)
 *         is ErrorRecoveryAction.FallbackToCache -> useCachedWallpaper()
 *         is ErrorRecoveryAction.Fail -> Result.failure()
 *     }
 * }
 * ```
 */
object ErrorHandler {
    
    private const val TAG = "ErrorHandler"
    
    // Maximum retry attempts before giving up
    private const val MAX_RETRY_ATTEMPTS = 3
    
    // Initial backoff delay (will be multiplied exponentially)
    private const val INITIAL_BACKOFF_MS = 10_000L // 10 seconds
    
    // Backoff multiplier for exponential backoff
    private const val BACKOFF_MULTIPLIER = 2.0f
    
    /**
     * Classifies an exception into a VanderwaalsError for consistent handling.
     * 
     * @param exception The caught exception
     * @param context Description of where error occurred (e.g., "WallpaperDownload", "DatabaseQuery")
     * @return Classified error type
     */
    fun classify(exception: Throwable, context: String): VanderwaalsError {
        return when (exception) {
            // Network errors
            is UnknownHostException -> VanderwaalsError.NetworkError(
                message = "No internet connection",
                cause = exception,
                isRecoverable = true
            )
            is SocketTimeoutException -> VanderwaalsError.NetworkError(
                message = "Network request timed out",
                cause = exception,
                isRecoverable = true
            )
            is SSLException -> VanderwaalsError.NetworkError(
                message = "Secure connection failed",
                cause = exception,
                isRecoverable = false
            )
            is IOException -> VanderwaalsError.NetworkError(
                message = "Network I/O error: ${exception.message}",
                cause = exception,
                isRecoverable = true
            )
            
            // Database errors
            is SQLiteException -> {
                val message = exception.message ?: "Unknown database error"
                val isRecoverable = !message.contains("UNIQUE constraint", ignoreCase = true) &&
                                   !message.contains("NOT NULL constraint", ignoreCase = true)
                
                VanderwaalsError.DatabaseError(
                    message = "Database error: ${exception.message}",
                    cause = exception,
                    isRecoverable = isRecoverable
                )
            }
            
            // Memory errors
            is OutOfMemoryError -> VanderwaalsError.MemoryError(
                message = "Out of memory: ${exception.message}",
                cause = exception
            )
            
            // File system errors
            is java.io.FileNotFoundException -> VanderwaalsError.FileSystemError(
                message = "File not found: ${exception.message}",
                cause = exception,
                isRecoverable = false
            )
            is java.nio.file.AccessDeniedException -> VanderwaalsError.FileSystemError(
                message = "Access denied: ${exception.message}",
                cause = exception,
                isRecoverable = false
            )
            
            // Generic worker errors
            else -> VanderwaalsError.WorkerError(
                message = "Worker error: ${exception.message}",
                cause = exception,
                isRecoverable = true
            )
        }.also { error ->
            logError(error, context)
        }
    }
    
    /**
     * Determines the appropriate recovery action based on error type and attempt count.
     * 
     * @param error The classified error
     * @param attemptCount Current retry attempt (0 for first failure)
     * @return Recovery action to take
     */
    fun determineRecoveryAction(
        error: VanderwaalsError,
        attemptCount: Int
    ): ErrorRecoveryAction {
        // Always fail if max retries exceeded
        if (attemptCount >= MAX_RETRY_ATTEMPTS) {
            return ErrorRecoveryAction.Fail(reason = "Max retry attempts ($MAX_RETRY_ATTEMPTS) exceeded")
        }
        
        return when (error) {
            is VanderwaalsError.NetworkError -> {
                if (!error.isRecoverable) {
                    ErrorRecoveryAction.Fail(reason = "Unrecoverable network error")
                } else if (attemptCount == 0) {
                    // First failure: try offline fallback if available
                    ErrorRecoveryAction.FallbackToCache
                } else {
                    // Subsequent failures: retry with exponential backoff
                    ErrorRecoveryAction.RetryWithBackoff(
                        delayMs = calculateBackoffDelay(attemptCount)
                    )
                }
            }
            
            is VanderwaalsError.DatabaseError -> {
                if (!error.isRecoverable) {
                    ErrorRecoveryAction.Fail(reason = "Database constraint violation")
                } else {
                    // Database locked or temporary issue: retry with backoff
                    ErrorRecoveryAction.RetryWithBackoff(
                        delayMs = calculateBackoffDelay(attemptCount)
                    )
                }
            }
            
            is VanderwaalsError.MemoryError -> {
                // Memory errors: clear caches and skip this operation
                ErrorRecoveryAction.SkipAndContinue(reason = "Memory pressure")
            }
            
            is VanderwaalsError.FileSystemError -> {
                if (!error.isRecoverable) {
                    ErrorRecoveryAction.Fail(reason = "File system access denied")
                } else {
                    ErrorRecoveryAction.FallbackToCache
                }
            }
            
            is VanderwaalsError.WorkerError -> {
                if (!error.isRecoverable) {
                    ErrorRecoveryAction.Fail(reason = "Unrecoverable worker error")
                } else {
                    ErrorRecoveryAction.RetryWithBackoff(
                        delayMs = calculateBackoffDelay(attemptCount)
                    )
                }
            }
            
            is VanderwaalsError.InvalidStateError -> {
                ErrorRecoveryAction.Fail(reason = "Invalid state: ${error.message}")
            }
        }
    }
    
    /**
     * Calculates exponential backoff delay.
     * 
     * @param attemptCount Current retry attempt (0-indexed)
     * @return Delay in milliseconds
     */
    fun calculateBackoffDelay(attemptCount: Int): Long {
        // Formula: INITIAL_BACKOFF_MS * (BACKOFF_MULTIPLIER ^ attemptCount)
        // Example: 10s, 20s, 40s for attempts 0, 1, 2
        return (INITIAL_BACKOFF_MS * Math.pow(BACKOFF_MULTIPLIER.toDouble(), attemptCount.toDouble())).toLong()
    }
    
    /**
     * Logs error with structured context for debugging.
     * 
     * @param error The error to log
     * @param context Description of where error occurred
     * @param metadata Additional contextual information
     */
    fun logError(
        error: VanderwaalsError,
        context: String,
        metadata: Map<String, Any> = emptyMap()
    ) {
        val severity = when (error) {
            is VanderwaalsError.MemoryError -> "CRITICAL"
            is VanderwaalsError.DatabaseError -> if (error.isRecoverable) "WARNING" else "ERROR"
            is VanderwaalsError.NetworkError -> "WARNING"
            is VanderwaalsError.FileSystemError -> "ERROR"
            is VanderwaalsError.WorkerError -> "WARNING"
            is VanderwaalsError.InvalidStateError -> "ERROR"
        }
        
        val metadataStr = if (metadata.isNotEmpty()) {
            metadata.entries.joinToString(", ") { "${it.key}=${it.value}" }
        } else {
            "none"
        }
        
        Log.e(
            TAG,
            "[$severity] $context: ${error.message} | Metadata: $metadataStr",
            error.cause
        )
    }
    
    /**
     * Checks if an error should trigger a retry based on its type and attempt count.
     * 
     * @param error The error to check
     * @param attemptCount Current retry attempt
     * @return true if should retry, false otherwise
     */
    fun shouldRetry(error: VanderwaalsError, attemptCount: Int): Boolean {
        if (attemptCount >= MAX_RETRY_ATTEMPTS) return false
        
        return when (error) {
            is VanderwaalsError.NetworkError -> error.isRecoverable
            is VanderwaalsError.DatabaseError -> error.isRecoverable
            is VanderwaalsError.MemoryError -> false // Don't retry memory errors
            is VanderwaalsError.FileSystemError -> error.isRecoverable
            is VanderwaalsError.WorkerError -> error.isRecoverable
            is VanderwaalsError.InvalidStateError -> false
        }
    }
    
    /**
     * Handles worker errors with classification and recovery logic.
     * 
     * Simplifies error handling in Worker.doWork() implementations.
     * 
     * @param exception The caught exception
     * @param context Description of worker operation
     * @param attemptCount Current retry attempt count
     * @param metadata Additional context for logging
     * @return Pair of (VanderwaalsError, ErrorRecoveryAction)
     */
    fun handleWorkerError(
        exception: Throwable,
        context: String,
        attemptCount: Int,
        metadata: Map<String, Any> = emptyMap()
    ): Pair<VanderwaalsError, ErrorRecoveryAction> {
        val error = classify(exception, context)
        logError(error, context, metadata)
        val action = determineRecoveryAction(error, attemptCount)
        return Pair(error, action)
    }
    
    /**
     * Creates standardized Worker output data for errors.
     * 
     * Provides consistent error information across all workers for UI/monitoring.
     * 
     * @param error The classified error
     * @param attemptCount Current retry attempt count
     * @param additionalData Extra data to include in output
     * @return WorkData map for Worker.Result
     */
    fun createWorkDataForError(
        error: VanderwaalsError,
        attemptCount: Int,
        additionalData: Map<String, Any> = emptyMap()
    ): Map<String, Any> {
        val baseData = mutableMapOf<String, Any>(
            "error_type" to error.javaClass.simpleName,
            "error_message" to error.message,
            "is_recoverable" to when (error) {
                is VanderwaalsError.NetworkError -> error.isRecoverable
                is VanderwaalsError.DatabaseError -> error.isRecoverable
                is VanderwaalsError.MemoryError -> false
                is VanderwaalsError.FileSystemError -> error.isRecoverable
                is VanderwaalsError.WorkerError -> error.isRecoverable
                is VanderwaalsError.InvalidStateError -> false
            },
            "retry_count" to attemptCount
        )
        
        baseData.putAll(additionalData)
        return baseData
    }
}

/**
 * Sealed class hierarchy for error types in Vanderwaals.
 */
sealed class VanderwaalsError {
    abstract val message: String
    abstract val cause: Throwable?
    
    /**
     * Network-related errors (timeouts, connectivity issues, SSL errors).
     */
    data class NetworkError(
        override val message: String,
        override val cause: Throwable?,
        val isRecoverable: Boolean
    ) : VanderwaalsError()
    
    /**
     * Database errors (constraint violations, locks, corruption).
     */
    data class DatabaseError(
        override val message: String,
        override val cause: Throwable?,
        val isRecoverable: Boolean
    ) : VanderwaalsError()
    
    /**
     * Memory errors (OOM when loading bitmaps).
     */
    data class MemoryError(
        override val message: String,
        override val cause: Throwable?
    ) : VanderwaalsError()
    
    /**
     * File system errors (file not found, access denied).
     */
    data class FileSystemError(
        override val message: String,
        override val cause: Throwable?,
        val isRecoverable: Boolean
    ) : VanderwaalsError()
    
    /**
     * Generic worker errors that don't fit other categories.
     */
    data class WorkerError(
        override val message: String,
        override val cause: Throwable?,
        val isRecoverable: Boolean
    ) : VanderwaalsError()
    
    /**
     * Invalid application state (e.g., preferences not initialized).
     */
    data class InvalidStateError(
        override val message: String
    ) : VanderwaalsError() {
        override val cause: Throwable? = null
    }
}

/**
 * Sealed class hierarchy for error recovery actions.
 */
sealed class ErrorRecoveryAction {
    /**
     * Retry immediately without delay.
     */
    object Retry : ErrorRecoveryAction()
    
    /**
     * Retry after exponential backoff delay.
     */
    data class RetryWithBackoff(val delayMs: Long) : ErrorRecoveryAction()
    
    /**
     * Attempt to use cached data instead of failing.
     */
    object FallbackToCache : ErrorRecoveryAction()
    
    /**
     * Skip this operation and continue with next item.
     */
    data class SkipAndContinue(val reason: String) : ErrorRecoveryAction()
    
    /**
     * Give up and return failure.
     */
    data class Fail(val reason: String) : ErrorRecoveryAction()
}
