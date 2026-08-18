package me.avinas.vanderwaals.core

import android.util.Log
import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.io.IOException
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * Exponential backoff retry for suspend functions.
 * Retries on IOException and HTTP 5xx; fails fast on 4xx.
 * Adds ±25% jitter to prevent synchronized retries.
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
     * Retries on transient failures (IOException, HTTP 5xx) using exponential
     * backoff with ±25% jitter, and fails fast on client errors (4xx).
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
     * Exponential backoff, min(baseDelay * 2^attempt, maxDelay), with ±25% jitter
     * to avoid a thundering herd when multiple clients retry simultaneously.
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
    
    fun isRetryable(exception: Exception): Boolean {
        return when (exception) {
            is IOException -> true
            is HttpException -> exception.code() in 500..599
            else -> false
        }
    }
}

/** Thrown when all retry attempts are exhausted. */
class NetworkRetryException(
    message: String,
    cause: Throwable?
) : Exception(message, cause)
