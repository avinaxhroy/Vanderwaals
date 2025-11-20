package me.avinas.vanderwaals.algorithm

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.FileNotFoundException
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extracts 576-dimensional embedding vectors from wallpaper images using MobileNetV3-Small model.
 * 
 * This class handles loading the TensorFlow Lite model, preprocessing images, and extracting
 * feature embeddings that represent the aesthetic characteristics of wallpapers.
 * 
 * The embeddings are used for:
 * - Initial wallpaper matching based on user's uploaded sample
 * - Learning user preferences through feedback
 * - Calculating similarity between wallpapers
 * 
 * @see SimilarityCalculator for computing similarity between embeddings
 * @see PreferenceUpdater for learning from user feedback
 */
@Singleton
class EmbeddingExtractor @Inject constructor(
    private val context: Context
) {
    private var interpreter: Interpreter? = null
    private var isModelLoaded = false
    
    companion object {
        private const val TAG = "EmbeddingExtractor"
        private const val MODEL_PATH = "models/mobilenet_v3_small.tflite"
        // MobileNetV3-Small standard input size
        private const val INPUT_SIZE = 224
        // MobileNetV3-Small outputs 576 floats (2304 bytes)
        // 2304 bytes ÷ 4 bytes per float = 576 floats
        private const val EMBEDDING_SIZE = 576
    }
    
    init {
        loadModel()
    }
    
    /**
     * Loads the TensorFlow Lite model from assets.
     * 
     * Handles errors gracefully:
     * - FileNotFoundException: Model file not downloaded
     * - Other exceptions: Corrupted model or incompatible device
     * 
     * @return true if model loaded successfully, false otherwise
     */
    private fun loadModel(): Boolean {
        return try {
            val modelBuffer = loadModelFile()
            interpreter = Interpreter(modelBuffer)
            isModelLoaded = true
            Log.d(TAG, "TFLite model loaded successfully")
            true
        } catch (e: FileNotFoundException) {
            Log.e(TAG, "Model file not found: $MODEL_PATH. Please download the model.", e)
            isModelLoaded = false
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load TFLite model", e)
            isModelLoaded = false
            false
        }
    }
    
    /**
     * Loads the model file from assets into a ByteBuffer.
     * 
     * @return ByteBuffer containing the model data
     * @throws FileNotFoundException if model file doesn't exist
     */
    private fun loadModelFile(): ByteBuffer {
        try {
            return FileUtil.loadMappedFile(context, MODEL_PATH)
        } catch (e: Exception) {
            throw FileNotFoundException("Model file not found at $MODEL_PATH. Please download from TensorFlow Hub.")
        }
    }
    
    /**
     * Extracts embedding vector from a bitmap image.
     * 
     * Process:
     * 1. Resize image to 224x224 (model input size)
     * 2. Convert to normalized float values (0-255 -> 0.0-1.0)
     * 3. Run inference through TFLite model
     * 4. Extract 576-dimensional output vector
     * 
     * @param bitmap Input image (any size, will be resized)
     * @return FloatArray of size 576 representing the embedding, or null if model not loaded
     */
    fun extractEmbedding(bitmap: Bitmap): FloatArray? {
        if (!isModelLoaded) {
            Log.w(TAG, "Cannot extract embedding: model not loaded")
            return null
        }
        
        return try {
            val startTime = System.currentTimeMillis()
            
            // Resize bitmap to standard MobileNetV3-Small input size
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
            
            // Create input buffer with float32 values
            // Model expects 224x224x3 floats = 602,112 bytes (4 bytes per float)
            val inputBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3)
            inputBuffer.order(java.nio.ByteOrder.nativeOrder())
            
            // Convert bitmap pixels to float values [0-255]
            // CRITICAL: Must match Python curation script preprocessing!
            // Python uses: tf.keras.preprocessing.image.img_to_array() which returns [0-255]
            // Then: tf.keras.applications.mobilenet_v3.preprocess_input() which does nothing for this model
            // Result: Model expects [0-255] range, NOT [0-1]
            val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
            resizedBitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
            
            for (pixel in pixels) {
                // Extract RGB values - keep as [0-255], do NOT divide by 255!
                val r = ((pixel shr 16) and 0xFF).toFloat()
                val g = ((pixel shr 8) and 0xFF).toFloat()
                val b = (pixel and 0xFF).toFloat()
                
                inputBuffer.putFloat(r)
                inputBuffer.putFloat(g)
                inputBuffer.putFloat(b)
            }
            
            inputBuffer.rewind()
            
            Log.d(TAG, "Input buffer size: ${inputBuffer.remaining()} bytes (expected: ${4 * INPUT_SIZE * INPUT_SIZE * 3})")
            
            val outputBuffer = TensorBuffer.createFixedSize(
                intArrayOf(1, EMBEDDING_SIZE),
                org.tensorflow.lite.DataType.FLOAT32
            )
            
            interpreter?.run(inputBuffer, outputBuffer.buffer.rewind())
            
            val embedding = outputBuffer.floatArray
            
            // CRITICAL FIX: Normalize embedding to unit length (same as Python curation script)
            // Python script does: embedding = embedding / np.linalg.norm(embedding)
            // This ensures consistent cosine similarity comparison
            var sumSq = 0.0f
            for (f in embedding) {
                sumSq += f * f
            }
            val magnitude = kotlin.math.sqrt(sumSq)
            if (magnitude > 0f) {
                for (i in embedding.indices) {
                    embedding[i] = embedding[i] / magnitude
                }
            }
            
            // Clean up resized bitmap if it's different from original
            if (resizedBitmap !== bitmap) {
                resizedBitmap.recycle()
            }
            
            val duration = System.currentTimeMillis() - startTime
            Log.d(TAG, "Embedding extracted in ${duration}ms (normalized magnitude: 1.0)")
            
            embedding
            
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting embedding (expected input: ${4 * INPUT_SIZE * INPUT_SIZE * 3} bytes)", e)
            null
        }
    }
    
    /**
     * Checks if the model is loaded and ready for inference.
     * 
     * @return true if model is loaded, false otherwise
     */
    fun isReady(): Boolean = isModelLoaded
    
    /**
     * Provides a user-friendly error message when model is not loaded.
     * 
     * @return Error message string
     */
    fun getErrorMessage(): String {
        return if (!isModelLoaded) {
            "TensorFlow Lite model not found. Please download mobilenet_v3_small.tflite " +
            "from TensorFlow Hub and place it in app/src/main/assets/models/"
        } else {
            ""
        }
    }
    
    /**
     * Releases interpreter resources.
     * Call this when the extractor is no longer needed.
     */
    fun close() {
        interpreter?.close()
        interpreter = null
        isModelLoaded = false
        Log.d(TAG, "TFLite model resources released")
    }
}
