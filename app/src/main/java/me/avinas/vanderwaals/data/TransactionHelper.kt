package me.avinas.vanderwaals.data

import android.util.Log
import androidx.room.withTransaction

/**
 * Database transaction utilities for safe, atomic operations.
 * 
 * Provides wrappers for executing database operations within Room transactions
 * with proper error handling and automatic rollback on failure.
 * 
 * **Usage:**
 * ```kotlin
 * // Simple transaction
 * TransactionHelper.withTransaction(database) {
 *     dao.insert(item1)
 *     dao.update(item2)
 *     // Both operations committed atomically
 * }
 * 
 * // Transaction with retry on conflict
 * TransactionHelper.withRetryableTransaction(database, maxRetries = 3) {
 *     dao.updateWithConflict(item)
 * }
 * ```
 */
object TransactionHelper {
    
    private const val TAG = "TransactionHelper"
    
    // Default maximum retry attempts for retryable transactions
    private const val DEFAULT_MAX_RETRIES = 3
    
    // Delay between retries in milliseconds
    private const val RETRY_DELAY_MS = 100L
    
    /**
     * Executes a block within a Room database transaction.
     * 
     * If the block throws an exception, the transaction is automatically rolled back.
     * If the block completes successfully, the transaction is committed.
     * 
     * @param database VanderwaalsDatabase instance
     * @param block Suspend function to execute within transaction
     * @return Result of the block
     * @throws Exception if block fails and rollback occurs
     */
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
    
    /**
     * Executes a block within a transaction with automatic retry on failure.
     * 
     * Useful for operations that may encounter temporary locks or conflicts.
     * Implements exponential backoff between retries.
     * 
     * @param database VanderwaalsDatabase instance
     * @param maxRetries Maximum number of retry attempts (default: 3)
     * @param block Suspend function to execute within transaction
     * @return Result of the block
     * @throws Exception if all retry attempts fail
     */
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
    
    /**
     * Executes a block within a transaction with custom retry logic.
     * 
     * Allows caller to determine whether to retry based on the exception type.
     * 
     * @param database VanderwaalsDatabase instance
     * @param maxRetries Maximum number of retry attempts
     * @param shouldRetry Function that determines if retry should occur based on exception
     * @param block Suspend function to execute within transaction
     * @return Result of the block
     * @throws Exception if retry logic determines failure or max retries exceeded
     */
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
