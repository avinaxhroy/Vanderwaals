package me.avinas.vanderwaals.domain.usecase

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import me.avinas.vanderwaals.algorithm.EmbeddingExtractor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extracts 1280-dimensional embedding vectors from user-uploaded wallpaper images
 * using MobileNetV4 via TensorFlow Lite.
 *
 * Loads image from Uri, preprocesses it, and returns the embedding vector.
 */
@Singleton
class ExtractEmbeddingUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val embeddingExtractor: EmbeddingExtractor
) {
    /**
     * Extracts embedding vector from an image Uri.
     * Handles content://, file://, and android.resource:// schemes.
     * Should be called from a background coroutine (IO dispatcher).
     *
     * @param imageUri Uri pointing to the image
     * @return Result containing 1280-dimensional embedding or error
     */
    operator fun invoke(imageUri: Uri): Result<FloatArray> {
        return try {
            // Step 1: Load bitmap from Uri
            val bitmap = loadBitmapFromUri(imageUri)
                ?: return Result.failure(
                    IllegalArgumentException("Failed to load image from Uri: $imageUri")
                )
            
            // Step 2: Extract embedding using TensorFlow Lite model
            val embedding = embeddingExtractor.extractEmbedding(bitmap)
                ?: return Result.failure(
                    IllegalStateException("Failed to extract embedding from image")
                )
            
            // Step 3: Validate embedding dimensions
            if (embedding.size != EXPECTED_EMBEDDING_SIZE) {
                return Result.failure(
                    IllegalStateException(
                        "Invalid embedding size: expected $EXPECTED_EMBEDDING_SIZE, got ${embedding.size}"
                    )
                )
            }
            
            // Step 4: Validate embedding values (should not be all zeros or NaN)
            if (!isValidEmbedding(embedding)) {
                return Result.failure(
                    IllegalStateException("Invalid embedding: contains invalid values (NaN or all zeros)")
                )
            }
            
            Result.success(embedding)
            
        } catch (e: SecurityException) {
            // Permission denied or URI access not allowed
            Result.failure(
                SecurityException("Permission denied to access image: ${e.message}", e)
            )
        } catch (e: OutOfMemoryError) {
            // Image too large or device low on memory
            Result.failure(
                OutOfMemoryError("Not enough memory to process image: ${e.message}")
            )
        } catch (e: Exception) {
            // Catch-all for any other errors (network issues, corrupted files, etc.)
            Result.failure(
                Exception("Failed to extract embedding: ${e.message}", e)
            )
        }
    }
    
    /**
     * Loads a bitmap from an Android Uri.
     * 
     * Handles content provider queries and file system access.
     * Automatically downsamples large images to prevent OOM errors.
     * 
     * @param uri Source Uri for the image
     * @return Decoded Bitmap or null if loading failed
     */
    private fun loadBitmapFromUri(uri: Uri): android.graphics.Bitmap? {
        return try {
            me.avinas.vanderwaals.core.BitmapManager.loadBitmap(
                uri = uri,
                maxWidth = 1024,
                maxHeight = 1024
            )
        } catch (e: Exception) {
            Log.e("ExtractEmbeddingUseCase", "Error loading bitmap from URI", e)
            null
        }
    }
    
    /**
     * Validates embedding vector quality.
     * 
     * Checks for common failure modes:
     * - All zeros (model not initialized)
     * - NaN values (computation error)
     * - All same values (preprocessing error)
     * 
     * @param embedding Embedding vector to validate
     * @return true if valid, false if suspicious/invalid
     */
    private fun isValidEmbedding(embedding: FloatArray): Boolean {
        // Check for NaN values
        if (embedding.any { it.isNaN() }) {
            return false
        }
        
        // Check if all zeros (model initialization failure)
        if (embedding.all { it == 0f }) {
            return false
        }
        
        // Check if all values are identical (preprocessing error)
        val firstValue = embedding.first()
        if (embedding.all { it == firstValue }) {
            return false
        }
        
        return true
    }
    
    companion object {
        /**
         * Expected embedding dimension for MobileNetV4-Conv-Small model.
         */
        private const val EXPECTED_EMBEDDING_SIZE = 1280
    }
}
