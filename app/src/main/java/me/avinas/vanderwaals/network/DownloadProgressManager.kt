package me.avinas.vanderwaals.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Exposes download progress as a [StateFlow] for UI consumption. */
@Singleton
class DownloadProgressManager @Inject constructor() {
    
    private val _progressState = MutableStateFlow(DownloadProgress.idle())
    val progressState: StateFlow<DownloadProgress> = _progressState.asStateFlow()
    
    /**
     * Updates download progress. Called by DownloadProgressInterceptor as bytes are read.
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
