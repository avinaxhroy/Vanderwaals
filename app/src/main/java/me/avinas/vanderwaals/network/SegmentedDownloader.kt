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
     *
     * Segmented download is currently disabled, so a single GET stream is used.
     * The previous HEAD pre-flight was removed — it added a full network
     * round-trip (~300-450ms) whose result (Content-Length / Accept-Ranges)
     * was never used once segmented download was turned off.
     */
    suspend fun download(url: String, targetFile: File): Result<File> = withContext(Dispatchers.IO) {
        try {
            downloadStandard(url, targetFile)

            if (targetFile.length() <= 0) {
                 if (targetFile.exists()) targetFile.delete()
                 return@withContext Result.failure(IOException("Download failed: File is empty"))
            }

            Result.success(targetFile)
        } catch (e: Exception) {
            if (targetFile.exists()) targetFile.delete()
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
            
            if (targetFile.length() == contentLength) {
                Result.success(targetFile)
            } else {
                Result.failure(IOException("Segmented download size mismatch"))
            }
        } catch (e: Exception) {
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
