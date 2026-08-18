package me.avinas.vanderwaals.data

import android.util.Log
import androidx.room.withTransaction

/** Wrappers for Room transactions with error handling and optional retry. */
object TransactionHelper {
    
    private const val TAG = "TransactionHelper"
    
    // Default maximum retry attempts for retryable transactions
    private const val DEFAULT_MAX_RETRIES = 3
    
    // Delay between retries in milliseconds
    private const val RETRY_DELAY_MS = 100L
    
    suspend fun <T> withTransaction(
        database: VanderwaalsDatabase,
        block: suspend () -> T
    ): T {
        return try {
            database.withTransaction {
                block()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Transaction failed and rolled back", e)
            throw e
        }
    }
    
    suspend fun <T> withRetryableTransaction(
        database: VanderwaalsDatabase,
        maxRetries: Int = DEFAULT_MAX_RETRIES,
        block: suspend () -> T
    ): T {
        var lastException: Exception? = null
        
        repeat(maxRetries + 1) { attempt ->
            try {
                return database.withTransaction {
                    block()
                }
            } catch (e: Exception) {
                lastException = e
                
                if (attempt < maxRetries) {
                    val delay = RETRY_DELAY_MS * (attempt + 1)
                    Log.w(TAG, "Transaction attempt ${attempt + 1} failed, retrying in ${delay}ms", e)
                    kotlinx.coroutines.delay(delay)
                } else {
                    Log.e(TAG, "Transaction failed after ${maxRetries + 1} attempts", e)
                }
            }
        }
        
        // All retries exhausted
        throw lastException ?: IllegalStateException("Transaction failed with no exception")
    }
    
    suspend fun <T> withConditionalRetryTransaction(
        database: VanderwaalsDatabase,
        maxRetries: Int = DEFAULT_MAX_RETRIES,
        shouldRetry: (Exception) -> Boolean = { true },
        block: suspend () -> T
    ): T {
        var lastException: Exception? = null
        
        repeat(maxRetries + 1) { attempt ->
            try {
                return database.withTransaction {
                    block()
                }
            } catch (e: Exception) {
                lastException = e
                
                if (attempt < maxRetries && shouldRetry(e)) {
                    val delay = RETRY_DELAY_MS * (attempt + 1)
                    Log.w(TAG, "Transaction attempt ${attempt + 1} failed, retrying in ${delay}ms", e)
                    kotlinx.coroutines.delay(delay)
                } else {
                    if (!shouldRetry(e)) {
                        Log.e(TAG, "Transaction failed with non-retryable error", e)
                    } else {
                        Log.e(TAG, "Transaction failed after ${maxRetries + 1} attempts", e)
                    }
                    throw e
                }
            }
        }
        
        throw lastException ?: IllegalStateException("Transaction failed with no exception")
    }
}
