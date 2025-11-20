package me.avinas.vanderwaals.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton manager for tracking download progress across the app.
 * 
 * Provides a StateFlow that emits download progress updates in real-time.
 * Used by UI components (like InitializationViewModel) to show download status.
 * 
 * **Progress State:**
 * - bytesDownloaded: Bytes downloaded so far
 * - totalBytes: Total file size (from Content-Length header)
 * - progress: Float from 0.0 to 1.0
 * - isDone: Whether download is complete
 * 
 * **Usage:**
 * ```kotlin
 * @Inject lateinit var downloadProgressManager: DownloadProgressManager
 * 
 * // In ViewModel:
 * viewModelScope.launch {
 *     downloadProgressManager.progressState.collect { state ->
 *         val percentComplete = (state.progress * 100).toInt()
 *         val mbDownloaded = state.bytesDownloaded / (1024 * 1024)
 *         val mbTotal = state.totalBytes / (1024 * 1024)
 *         _status.value = "Downloading: $mbDownloaded MB / $mbTotal MB ($percentComplete%)"
 *     }
 * }
 * ```
 */
@Singleton
class DownloadProgressManager @Inject constructor() {
    
    private val _progressState = MutableStateFlow(DownloadProgress.idle())
    val progressState: StateFlow<DownloadProgress> = _progressState.asStateFlow()
    
    /**
     * Updates download progress.
     * Called by DownloadProgressInterceptor as bytes are read.
     * 
     * @param bytesDownloaded Bytes downloaded so far
     * @param totalBytes Total file size
     * @param isDone Whether download is complete
     */
    fun updateProgress(bytesDownloaded: Long, totalBytes: Long, isDone: Boolean) {
        val progress = if (totalBytes > 0) {
            bytesDownloaded.toFloat() / totalBytes.toFloat()
        } else {
            0f
        }
        
        _progressState.value = DownloadProgress(
            bytesDownloaded = bytesDownloaded,
            totalBytes = totalBytes,
            progress = progress,
            isDone = isDone
        )
    }
    
    /**
     * Resets progress to idle state.
     * Should be called before starting a new download.
     */
    fun reset() {
        _progressState.value = DownloadProgress.idle()
    }
}

/**
 * Download progress state.
 * 
 * @property bytesDownloaded Bytes downloaded so far
 * @property totalBytes Total file size (0 if unknown)
 * @property progress Download progress from 0.0 to 1.0
 * @property isDone Whether download is complete
 */
data class DownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val progress: Float,
    val isDone: Boolean
) {
    companion object {
        fun idle() = DownloadProgress(
            bytesDownloaded = 0L,
            totalBytes = 0L,
            progress = 0f,
            isDone = false
        )
    }
    
    /**
     * Formats progress as "X MB / Y MB (Z%)"
     */
    fun formatProgress(): String {
        val mbDownloaded = bytesDownloaded / (1024f * 1024f)
        val mbTotal = totalBytes / (1024f * 1024f)
        val percent = (progress * 100).toInt()
        
        return if (totalBytes > 0) {
            "%.1f MB / %.1f MB (%d%%)".format(mbDownloaded, mbTotal, percent)
        } else {
            "%.1f MB".format(mbDownloaded)
        }
    }
}
