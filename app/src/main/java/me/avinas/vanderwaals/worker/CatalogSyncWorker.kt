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
 * Runs on a 7-day cycle syncing manifest.json from GitHub, constrained to
 * network-connected, battery-not-low, storage-not-low. Retries with exponential
 * backoff (max 3 attempts) and falls back to the cached catalog on failure.
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
        const val WORK_NAME = "catalog_sync_work"
        
        const val KEY_SYNCED_COUNT = "synced_count"
        
        const val KEY_ERROR_MESSAGE = "error_message"
    }
    
    override suspend fun doWork(): Result {
        return try {
            downloadProgressManager.reset()
            
            setProgress(workDataOf(
                "status" to "Starting download...",
                "progress" to 0.05f,
                "count" to 0
            ))
            
            // We can't use setProgress in the callback because it's suspend,
            // so progress is updated after sync completes
            val result = syncWallpaperCatalogUseCase.syncCatalog()
            
            result.fold(
                onSuccess = { count ->
                    setProgress(workDataOf(
                        "status" to "Download complete!",
                        "progress" to 1.0f,
                        "count" to count
                    ))
                    
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
            Result.retry()
        }
    }
}
