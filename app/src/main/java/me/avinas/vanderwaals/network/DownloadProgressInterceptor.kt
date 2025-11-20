package me.avinas.vanderwaals.network

import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Source
import okio.buffer
import java.io.IOException

/**
 * OkHttp interceptor that tracks download progress in real-time.
 * 
 * Wraps the response body to monitor bytes read during download.
 * Calculates progress as: bytesRead / totalBytes
 * 
 * **Usage:**
 * ```kotlin
 * val progressInterceptor = DownloadProgressInterceptor { bytesRead, totalBytes, isDone ->
 *     val progress = if (totalBytes > 0) (bytesRead.toFloat() / totalBytes) else 0f
 *     Log.d("Download", "Progress: ${(progress * 100).toInt()}% ($bytesRead / $totalBytes bytes)")
 * }
 * 
 * val client = OkHttpClient.Builder()
 *     .addNetworkInterceptor(progressInterceptor)
 *     .build()
 * ```
 * 
 * @param progressListener Callback invoked as bytes are read (bytesRead, totalBytes, isDone)
 */
class DownloadProgressInterceptor(
    private val progressListener: (bytesRead: Long, totalBytes: Long, isDone: Boolean) -> Unit
) : Interceptor {
    
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalResponse = chain.proceed(chain.request())
        
        // Get the actual content length from headers
        // If Content-Length is missing or -1, we'll estimate based on what we download
        val contentLength = originalResponse.header("Content-Length")?.toLongOrNull()
            ?: originalResponse.body?.contentLength() ?: -1L
        
        return originalResponse.newBuilder()
            .body(originalResponse.body?.let { ProgressResponseBody(it, contentLength, progressListener) })
            .build()
    }
}

/**
 * ResponseBody wrapper that tracks bytes read during download.
 * 
 * Wraps the original source with a ForwardingSource that counts bytes
 * as they are read from the network.
 * 
 * **Note on compressed responses:**
 * GitHub/CDNs may send gzip-compressed responses. OkHttp automatically
 * decompresses them, so we track actual bytes read (decompressed size).
 * 
 * @param responseBody Original response body
 * @param expectedContentLength Expected content length from headers (may be compressed or uncompressed)
 * @param progressListener Progress callback
 */
private class ProgressResponseBody(
    private val responseBody: ResponseBody,
    private val expectedContentLength: Long,
    private val progressListener: (bytesRead: Long, totalBytes: Long, isDone: Boolean) -> Unit
) : ResponseBody() {
    
    private val bufferedSource: BufferedSource by lazy {
        source(responseBody.source()).buffer()
    }
    
    override fun contentType(): MediaType? = responseBody.contentType()
    
    override fun contentLength(): Long = responseBody.contentLength()
    
    override fun source(): BufferedSource = bufferedSource
    
    private fun source(source: Source): Source {
        return object : ForwardingSource(source) {
            private var totalBytesRead = 0L
            
            @Throws(IOException::class)
            override fun read(sink: Buffer, byteCount: Long): Long {
                val bytesRead = super.read(sink, byteCount)
                
                // Update total bytes read (this is the actual decompressed data)
                totalBytesRead += if (bytesRead != -1L) bytesRead else 0L
                
                // Use actual bytes read as total if we're reading more than expected
                // This handles cases where Content-Length is compressed size but we're reading decompressed
                val totalBytes = maxOf(expectedContentLength, totalBytesRead)
                val isDone = bytesRead == -1L
                
                progressListener(totalBytesRead, totalBytes, isDone)
                
                return bytesRead
            }
        }
    }
}
