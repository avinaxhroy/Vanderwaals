package me.avinas.vanderwaals.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * WorkManager worker for periodic wallpaper catalog synchronization.
 * 
 * Executes weekly background sync of manifest.json from GitHub:
 * - Scheduled via PeriodicWorkRequest (7 day interval)
 * - Requires network connectivity
 * - Runs in background with low priority
 * - Updates notification on sync progress/completion
 * 
 * Work constraints:
 * - NetworkType.CONNECTED (requires internet)
 * - BatteryNotLow (waits for sufficient battery)
 * - StorageNotLow (ensures space for metadata)
 * 
 * Failure handling:
 * - Exponential backoff retry policy
 * - Max 3 retry attempts
 * - Falls back to cached catalog on failure
 * 
 * @see me.avinas.vanderwaals.domain.usecase.SyncWallpaperCatalogUseCase
 */
@HiltWorker
class CatalogSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncWallpaperCatalogUseCase: me.avinas.vanderwaals.domain.usecase.SyncWallpaperCatalogUseCase,
    private val downloadProgressManager: me.avinas.vanderwaals.network.DownloadProgressManager
) : CoroutineWorker(appContext, workerParams) {
    
    companion object {
        /**
         * Unique work name for periodic catalog sync.
         */
        const val WORK_NAME = "catalog_sync_work"
        
        /**
         * Output data key for synced wallpaper count.
         */
        const val KEY_SYNCED_COUNT = "synced_count"
        
        /**
         * Output data key for error message.
         */
        const val KEY_ERROR_MESSAGE = "error_message"
    }
    
    override suspend fun doWork(): Result {
        return try {
            // Reset download progress manager for fresh tracking
            downloadProgressManager.reset()
            
            // Report initial progress
            setProgress(workDataOf(
                "status" to "Starting download...",
                "progress" to 0.05f,
                "count" to 0
            ))
            
            // Use SyncWallpaperCatalogUseCase with progress callback
            // Note: We can't use setProgress in the callback because it's suspend
            // Instead, we update progress after sync completes
            val result = syncWallpaperCatalogUseCase.syncCatalog()
            
            result.fold(
                onSuccess = { count ->
                    // Report final completion
                    setProgress(workDataOf(
                        "status" to "Download complete!",
                        "progress" to 1.0f,
                        "count" to count
                    ))
                    
                    // Success: Return count of synced wallpapers
                    Result.success(
                        workDataOf(KEY_SYNCED_COUNT to count)
                    )
                },
                onFailure = { error ->
                    // Determine retry strategy based on error type
                    when {
                        // Network errors: Retry with backoff
                        error is java.io.IOException -> {
                            Result.retry()
                        }
                        // HTTP errors: Retry (might be temporary server issue)
                        error is retrofit2.HttpException -> {
                            Result.retry()
                        }
                        // Parse errors: Don't retry (bad manifest structure)
                        error is com.google.gson.JsonSyntaxException -> {
                            Result.failure(
                                workDataOf(KEY_ERROR_MESSAGE to "Invalid manifest format: ${error.message}")
                            )
                        }
                        // Other errors: Fail without retry
                        else -> {
                            Result.failure(
                                workDataOf(KEY_ERROR_MESSAGE to error.message.orEmpty())
                            )
                        }
                    }
                }
            )
            
        } catch (e: Exception) {
            // Unexpected error: Retry once
            Result.retry()
        }
    }
}
