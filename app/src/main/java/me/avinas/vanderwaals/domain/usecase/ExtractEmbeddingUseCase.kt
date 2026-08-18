package me.avinas.vanderwaals.domain.usecase

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import me.avinas.vanderwaals.algorithm.EmbeddingExtractor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extracts embedding vectors from image URIs using the on-device embedding model.
 */
@Singleton
class ExtractEmbeddingUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val embeddingExtractor: EmbeddingExtractor
) {
    operator fun invoke(imageUri: Uri): Result<FloatArray> {
        return try {
            val bitmap = loadBitmapFromUri(imageUri)
                ?: return Result.failure(
                    IllegalArgumentException("Failed to load image from Uri: $imageUri")
                )
            
            val embedding = embeddingExtractor.extractEmbedding(bitmap)
                ?: return Result.failure(
                    IllegalStateException("Failed to extract embedding from image")
                )
            
            if (embedding.size != EXPECTED_EMBEDDING_SIZE) {
                return Result.failure(
                    IllegalStateException(
                        "Invalid embedding size: expected $EXPECTED_EMBEDDING_SIZE, got ${embedding.size}"
                    )
                )
            }
            
            if (!isValidEmbedding(embedding)) {
                return Result.failure(
                    IllegalStateException("Invalid embedding: contains invalid values (NaN or all zeros)")
                )
            }
            
            Result.success(embedding)
            
        } catch (e: SecurityException) {
            Result.failure(
                SecurityException("Permission denied to access image: ${e.message}", e)
            )
        } catch (e: OutOfMemoryError) {
            Result.failure(
                OutOfMemoryError("Not enough memory to process image: ${e.message}")
            )
        } catch (e: Exception) {
            Result.failure(
                Exception("Failed to extract embedding: ${e.message}", e)
            )
        }
    }
    
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
    
    private fun isValidEmbedding(embedding: FloatArray): Boolean {
        if (embedding.any { it.isNaN() }) {
            return false
        }
        
        // All zeros signals a model initialization failure.
        if (embedding.all { it == 0f }) {
            return false
        }
        
        // All identical values signals a preprocessing error.
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
