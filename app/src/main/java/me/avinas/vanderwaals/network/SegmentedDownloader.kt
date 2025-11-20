package me.avinas.vanderwaals.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper class for performing segmented (multi-part) downloads.
 *
 * Improves download speed for large files by splitting them into chunks
 * and downloading them in parallel.
 */
@Singleton
class SegmentedDownloader @Inject constructor(
    private val okHttpClient: OkHttpClient
) {

    companion object {
        private const val MIN_SEGMENT_SIZE = 1024 * 1024L // 1 MB
        private const val CHUNK_COUNT = 4
    }

    /**
     * Downloads a file from the given URL to the target file.
     * Uses segmented download if possible, otherwise falls back to standard download.
     */
    suspend fun download(url: String, targetFile: File): Result<File> = withContext(Dispatchers.IO) {
        try {
            // Step 1: Get content length and check support for Range headers
            val headRequest = Request.Builder().url(url).head().build()
            val headResponse = okHttpClient.newCall(headRequest).execute()

            if (!headResponse.isSuccessful) {
                return@withContext Result.failure(IOException("Failed to fetch file info: ${headResponse.code}"))
            }

            val contentLength = headResponse.header("Content-Length")?.toLongOrNull() ?: -1L
            val acceptRanges = headResponse.header("Accept-Ranges")

            headResponse.close()

            // Step 2: Decide strategy
            if (contentLength > MIN_SEGMENT_SIZE && acceptRanges == "bytes") {
                downloadSegmented(url, targetFile, contentLength)
            } else {
                downloadStandard(url, targetFile)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun downloadSegmented(url: String, targetFile: File, contentLength: Long): Result<File> = withContext(Dispatchers.IO) {
        try {
            // Create empty file with specific size
            RandomAccessFile(targetFile, "rw").use { it.setLength(contentLength) }

            val chunkSize = contentLength / CHUNK_COUNT
            val chunks = (0 until CHUNK_COUNT).map { index ->
                val start = index * chunkSize
                val end = if (index == CHUNK_COUNT - 1) contentLength - 1 else (start + chunkSize - 1)
                Triple(index, start, end)
            }

            val deferreds = chunks.map { (index, start, end) ->
                async(Dispatchers.IO) {
                    downloadChunk(url, targetFile, start, end)
                }
            }

            deferreds.awaitAll()
            
            // Verify file size
            if (targetFile.length() == contentLength) {
                Result.success(targetFile)
            } else {
                Result.failure(IOException("Segmented download size mismatch"))
            }
        } catch (e: Exception) {
            // Clean up on failure
            if (targetFile.exists()) targetFile.delete()
            Result.failure(e)
        }
    }

    private fun downloadChunk(url: String, targetFile: File, start: Long, end: Long) {
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=$start-$end")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Chunk download failed: ${response.code}")
            
            val body = response.body ?: throw IOException("Empty body")
            
            val buffer = ByteArray(8192)
            var bytesRead: Int
            
            // Use RandomAccessFile to write to specific position
            RandomAccessFile(targetFile, "rw").use { raf ->
                raf.seek(start)
                val inputStream = body.byteStream()
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    raf.write(buffer, 0, bytesRead)
                }
            }
        }
    }

    private fun downloadStandard(url: String, targetFile: File): Result<File> {
        return try {
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return Result.failure(IOException("Download failed: ${response.code}"))
                
                val body = response.body ?: return Result.failure(IOException("Empty body"))
                
                targetFile.outputStream().use { output ->
                    body.byteStream().copyTo(output)
                }
                
                Result.success(targetFile)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
