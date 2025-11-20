package me.avinas.vanderwaals.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import me.avinas.vanderwaals.data.entity.DownloadQueueItem
import me.avinas.vanderwaals.data.repository.WallpaperRepository

/**
 * WorkManager worker for batch downloading wallpapers in the background.
 * 
 * Downloads wallpapers from the download queue with the following features:
 * - Foreground service with progress notification
 * - Downloads top 50 wallpapers by priority
 * - Updates notification with current progress
 * - Supports cancellation
 * - Retries failed downloads (max 3 attempts per wallpaper)
 * 
 * Work constraints:
 * - NetworkType.UNMETERED (WiFi only, no cellular data)
 * - BatteryNotLow (preserve battery)
 * - StorageNotLow (ensure space for downloads)
 * 
 * Notification shows:
 * - Title: "Downloading wallpapers"
 * - Text: "15/50 wallpapers downloaded"
 * - Progress bar
 * - Cancel action
 * 
 * **Usage:**
 * Triggered automatically after manifest sync or manually from settings.
 * 
 * @see me.avinas.vanderwaals.data.repository.WallpaperRepository
 * @see me.avinas.vanderwaals.data.entity.DownloadQueueItem
 */
@HiltWorker
class BatchDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val wallpaperRepository: WallpaperRepository
) : CoroutineWorker(appContext, workerParams) {
    
    companion object {
        private const val TAG = "BatchDownloadWorker"
        
        /**
         * Unique work name for batch download.
         */
        const val WORK_NAME = "batch_download_work"
        
        /**
         * Notification channel ID.
         */
        private const val CHANNEL_ID = "batch_download_channel"
        
        /**
         * Notification ID.
         */
        private const val NOTIFICATION_ID = 2
        
        /**
         * Output data key for download count.
         */
        const val KEY_DOWNLOADED_COUNT = "downloaded_count"
        
        /**
         * Output data key for failed count.
         */
        const val KEY_FAILED_COUNT = "failed_count"
        
        /**
         * Maximum wallpapers to download in one batch.
         */
        private const val BATCH_SIZE = 50
    }
    
    override suspend fun doWork(): Result {
        return try {
            // Create notification channel
            createNotificationChannel()
            
            // Set foreground with initial notification
            setForeground(createForegroundInfo(0, BATCH_SIZE))
            
            // Get next batch of wallpapers to download
            val queueItems = wallpaperRepository.getNextToDownload(BATCH_SIZE)
            
            if (queueItems.isEmpty()) {
                Log.d(TAG, "No wallpapers to download")
                return Result.success(
                    workDataOf(
                        KEY_DOWNLOADED_COUNT to 0,
                        KEY_FAILED_COUNT to 0
                    )
                )
            }
            
            Log.d(TAG, "Starting batch download of ${queueItems.size} wallpapers")
            
            var downloadedCount = 0
            var failedCount = 0
            val totalCount = queueItems.size
            
            // Download wallpapers in parallel with concurrency limit
            val concurrencyLimit = 3
            val semaphore = kotlinx.coroutines.sync.Semaphore(concurrencyLimit)
            
            
            // Use coroutineScope to create a scope for async builders
            kotlinx.coroutines.coroutineScope {
                // Use async to start downloads in parallel
                val deferredResults = queueItems.mapIndexed { index, queueItem ->
                    async {
                        semaphore.withPermit {
                            // Check if work is cancelled
                            if (isStopped) {
                                throw CancellationException("Work cancelled by user")
                            }
                            
                            // Get wallpaper metadata
                            val wallpaper = wallpaperRepository.getAllWallpapers()
                                .first()
                                .find { it.id == queueItem.wallpaperId }
                            
                            if (wallpaper == null) {
                                Log.w(TAG, "Wallpaper ${queueItem.wallpaperId} not found in metadata")
                                return@withPermit false
                            }
                            
                            // Attempt download
                            val result = wallpaperRepository.downloadWallpaper(wallpaper)
                            
                            if (result.isSuccess) {
                                // Mark as downloaded in queue
                                wallpaperRepository.markAsDownloaded(wallpaper.id)
                                Log.d(TAG, "Downloaded wallpaper ${wallpaper.id}")
                                true
                            } else {
                                Log.e(TAG, "Failed to download ${wallpaper.id}: ${result.exceptionOrNull()?.message}")
                                false
                            }
                        }
                    }
                }
                
                // Wait for all downloads to complete and count successes
                val results = deferredResults.awaitAll()
                downloadedCount = results.count { it }
                failedCount = results.count { !it }
            }
            
            // Update notification with final status
            setForeground(createForegroundInfo(totalCount, totalCount))
            
            Log.d(TAG, "Batch download complete: $downloadedCount succeeded, $failedCount failed")
            
            // Return success with statistics
            Result.success(
                workDataOf(
                    KEY_DOWNLOADED_COUNT to downloadedCount,
                    KEY_FAILED_COUNT to failedCount
                )
            )
            
        } catch (e: CancellationException) {
            Log.d(TAG, "Batch download cancelled")
            Result.failure()
        } catch (e: Exception) {
            Log.e(TAG, "Error during batch download", e)
            Result.retry()
        }
    }
    
    /**
     * Creates notification channel for download progress.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Wallpaper Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of wallpaper downloads"
            }
            
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) 
                as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Creates foreground info with progress notification.
     * 
     * @param progress Current progress (number of wallpapers downloaded)
     * @param total Total number of wallpapers to download
     */
    private fun createForegroundInfo(progress: Int, total: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Downloading wallpapers")
            .setContentText("$progress/$total wallpapers downloaded")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(total, progress, false)
            .setOngoing(true)
            .setSilent(true)
            .build()
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }
}
