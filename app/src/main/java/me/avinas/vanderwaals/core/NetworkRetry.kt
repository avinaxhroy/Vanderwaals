package me.avinas.vanderwaals.core

import android.util.Log
import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.io.IOException
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * Centralized network retry utility with exponential backoff and jitter.
 * 
 * Provides reusable retry logic for all network operations in the application.
 * Implements industry-standard exponential backoff with jitter to prevent
 * thundering herd problems when multiple clients retry simultaneously.
 * 
 * **Features:**
 * - Generic suspend function support (works with any suspend operation)
 * - Configurable max retries, base delay, and max delay
 * - Exponential backoff: delay = min(baseDelay * 2^attempt, maxDelay)
 * - Jitter: adds random ±25% variance to prevent synchronized retries
 * - Selective retry: retries on IOException and HTTP 5xx, fails fast on HTTP 4xx
 * - Comprehensive logging for debugging
 * 
 * **Usage:**
 * ```kotlin
 * val result = NetworkRetry.retryWithBackoff(
 *     maxRetries = 3,
 *     baseDelayMs = 1000L,
 *     maxDelayMs = 30000L,
 *     onRetry = { attempt, error ->
 *         Log.d(TAG, "Retry attempt $attempt after error: ${error.message}")
 *     }
 * ) {
 *     manifestService.getManifest()
 * }
 * ```
 * 
 * @see retryWithBackoff
 */
object NetworkRetry {
    
    private const val TAG = "NetworkRetry"
    
    /**
     * Default maximum number of retry attempts.
     */
    const val DEFAULT_MAX_RETRIES = 3
    
    /**
     * Default base delay in milliseconds (1 second).
     */
    const val DEFAULT_BASE_DELAY_MS = 1000L
    
    /**
     * Default maximum delay in milliseconds (30 seconds).
     */
    const val DEFAULT_MAX_DELAY_MS = 30_000L
    
    /**
     * Jitter range as a percentage (±25%).
     */
    private const val JITTER_PERCENT = 0.25
    
    /**
     * Executes a suspend operation with automatic retry logic.
     * 
     * Retries the operation on transient failures (network errors, server errors)
     * using exponential backoff with jitter. Fails fast on client errors (4xx).
     * 
     * **Retry Logic:**
     * - Retries on: IOException (network errors), HTTP 500-599 (server errors)
     * - Fails immediately on: HTTP 400-499 (client errors), other exceptions
     * - Delay formula: min(baseDelay * 2^attempt, maxDelay) with ±25% jitter
     * 
     * **Example Delays (with baseDelay=1000ms):**
     * - Attempt 0: ~1000ms (750-1250ms with jitter)
     * - Attempt 1: ~2000ms (1500-2500ms with jitter)
     * - Attempt 2: ~4000ms (3000-5000ms with jitter)
     * - Attempt 3: ~8000ms (6000-10000ms with jitter)
     * 
     * @param maxRetries Maximum number of retry attempts (default: 3)
     * @param baseDelayMs Base delay in milliseconds for exponential backoff (default: 1000)
     * @param maxDelayMs Maximum delay cap in milliseconds (default: 30000)
     * @param onRetry Optional callback invoked before each retry with (attempt, error)
     * @param operation Suspend function to execute with retry logic
     * @return Result of the operation if successful
     * @throws Exception if all retries are exhausted or non-retryable error occurs
     * 
     * Example:
     * ```kotlin
     * suspend fun downloadManifest(): Manifest {
     *     return NetworkRetry.retryWithBackoff(
     *         maxRetries = 3,
     *         onRetry = { attempt, error ->
     *             _syncProgress.value = "Retry attempt $attempt..."
     *         }
     *     ) {
     *         manifestService.getManifest().also { response ->
     *             if (!response.isSuccessful) {
     *                 throw HttpException(response)
     *             }
     *             response.body() ?: throw IOException("Empty response body")
     *         }
     *     }
     * }
     * ```
     */
    suspend fun <T> retryWithBackoff(
        maxRetries: Int = DEFAULT_MAX_RETRIES,
        baseDelayMs: Long = DEFAULT_BASE_DELAY_MS,
        maxDelayMs: Long = DEFAULT_MAX_DELAY_MS,
        onRetry: ((attempt: Int, error: Exception) -> Unit)? = null,
        operation: suspend () -> T
    ): T {
        var lastError: Exception? = null
        
        repeat(maxRetries) { attempt ->
            try {
                // Attempt the operation
                return operation()
                
            } catch (e: IOException) {
                // Network errors - retry with backoff
                lastError = e
                Log.w(TAG, "Network error on attempt ${attempt + 1}/$maxRetries: ${e.message}")
                
                if (attempt < maxRetries - 1) {
                    onRetry?.invoke(attempt + 1, e)
                    val delayMs = calculateBackoffDelay(attempt, baseDelayMs, maxDelayMs)
                    Log.d(TAG, "Retrying after ${delayMs}ms...")
                    delay(delayMs)
                }
                
            } catch (e: HttpException) {
                // HTTP errors - check status code
                val statusCode = e.code()
                lastError = e
                
                when {
                    statusCode in 500..599 -> {
                        // Server errors (5xx) - retry with backoff
                        Log.w(TAG, "Server error $statusCode on attempt ${attempt + 1}/$maxRetries")
                        
                        if (attempt < maxRetries - 1) {
                            onRetry?.invoke(attempt + 1, e)
                            val delayMs = calculateBackoffDelay(attempt, baseDelayMs, maxDelayMs)
                            Log.d(TAG, "Retrying after ${delayMs}ms...")
                            delay(delayMs)
                        }
                    }
                    
                    statusCode in 400..499 -> {
                        // Client errors (4xx) - fail immediately (don't retry)
                        Log.e(TAG, "Client error $statusCode - not retrying")
                        throw e
                    }
                    
                    else -> {
                        // Other HTTP errors - fail immediately
                        Log.e(TAG, "HTTP error $statusCode - not retrying")
                        throw e
                    }
                }
                
            } catch (e: Exception) {
                // Other exceptions - fail immediately
                Log.e(TAG, "Non-retryable error: ${e.javaClass.simpleName} - ${e.message}")
                throw e
            }
        }
        
        // All retries exhausted
        val errorMessage = "Operation failed after $maxRetries attempts: ${lastError?.message}"
        Log.e(TAG, errorMessage)
        throw NetworkRetryException(errorMessage, lastError)
    }
    
    /**
     * Calculates the backoff delay with exponential backoff and jitter.
     * 
     * Formula: min(baseDelay * 2^attempt, maxDelay) with ±25% jitter
     * 
     * Jitter prevents thundering herd when multiple clients retry simultaneously.
     * 
     * @param attempt Current attempt number (0-indexed)
     * @param baseDelayMs Base delay in milliseconds
     * @param maxDelayMs Maximum delay cap in milliseconds
     * @return Delay in milliseconds with jitter applied
     */
    private fun calculateBackoffDelay(
        attempt: Int,
        baseDelayMs: Long,
        maxDelayMs: Long
    ): Long {
        // Exponential backoff: baseDelay * 2^attempt
        val exponentialDelay = baseDelayMs * (2.0.pow(attempt.toDouble())).toLong()
        
        // Cap at maxDelay
        val cappedDelay = min(exponentialDelay, maxDelayMs)
        
        // Add jitter: ±25% random variance
        val jitterRange = (cappedDelay * JITTER_PERCENT).toLong()
        val jitter = Random.nextLong(-jitterRange, jitterRange + 1)
        
        return (cappedDelay + jitter).coerceAtLeast(0)
    }
    
    /**
     * Checks if an exception is retryable.
     * 
     * @param exception Exception to check
     * @return true if the exception should trigger a retry, false otherwise
     */
    fun isRetryable(exception: Exception): Boolean {
        return when (exception) {
            is IOException -> true
            is HttpException -> exception.code() in 500..599
            else -> false
        }
    }
}

/**
 * Exception thrown when all retry attempts are exhausted.
 * 
 * @property message Error message describing the failure
 * @property cause Original exception that caused the retries
 */
class NetworkRetryException(
    message: String,
    cause: Throwable?
) : Exception(message, cause)
