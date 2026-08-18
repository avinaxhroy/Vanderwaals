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

class DownloadProgressInterceptor(
    private val progressListener: (bytesRead: Long, totalBytes: Long, isDone: Boolean) -> Unit
) : Interceptor {
    
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalResponse = chain.proceed(chain.request())
        
        val contentLength = originalResponse.header("Content-Length")?.toLongOrNull()
            ?: originalResponse.body?.contentLength() ?: -1L
        
        return originalResponse.newBuilder()
            .body(originalResponse.body?.let { ProgressResponseBody(it, contentLength, progressListener) })
            .build()
    }
}

/**
 * ResponseBody wrapper that tracks bytes read during download.
 * GitHub/CDNs may send gzip-compressed responses; OkHttp auto-decompresses,
 * so what we track here is the actual (decompressed) size.
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
                
                // this is the actual decompressed data
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
