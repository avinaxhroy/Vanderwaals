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
 * Extracts 1280-dimensional embedding vectors from images using the MobileNetV4 model.
 */
@Singleton
class EmbeddingExtractor @Inject constructor(
    private val context: Context
) {
    private var interpreter: Interpreter? = null
    private var isModelLoaded = false

    private val inferenceLock = Any()

    // Reusable inference buffers (guarded by inferenceLock) to avoid allocations per extraction.
    private var inputBuffer: ByteBuffer? = null
    private var pixelBuffer: IntArray? = null
    private var outputBuffer: TensorBuffer? = null
    private var scaledBuffer: Bitmap? = null
    private var scaledCanvas: android.graphics.Canvas? = null
    private val scaledPaint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)

    companion object {
        private const val TAG = "EmbeddingExtractor"
        private const val MODEL_PATH = "models/mobilenet_v4_conv_small.tflite"
        private const val INPUT_SIZE = 224
        private const val EMBEDDING_SIZE = 1280

        private val IMAGENET_MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        private val IMAGENET_STD = floatArrayOf(0.229f, 0.224f, 0.225f)

        private val SCALE_DEST_RECT = android.graphics.RectF(0f, 0f, INPUT_SIZE.toFloat(), INPUT_SIZE.toFloat())
    }
    
    init {
        loadModel()
    }
    
    private fun loadModel(): Boolean {
        return try {
            val modelBuffer = loadModelFile()
            interpreter = Interpreter(modelBuffer)
            isModelLoaded = true
            if (me.avinas.vanderwaals.BuildConfig.DEBUG) Log.d(TAG, "TFLite model loaded successfully")
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
    
    private fun loadModelFile(): ByteBuffer {
        try {
            return FileUtil.loadMappedFile(context, MODEL_PATH)
        } catch (e: Exception) {
            throw FileNotFoundException("Model file not found at $MODEL_PATH. Please download from TensorFlow Hub.")
        }
    }
    
    fun extractEmbedding(bitmap: Bitmap): FloatArray? {
        if (!isModelLoaded) {
            Log.w(TAG, "Cannot extract embedding: model not loaded")
            return null
        }
        
        return try {
            val startTime = System.currentTimeMillis()

            val embedding: FloatArray
            synchronized(inferenceLock) {
                val scaled = scaledBuffer ?: Bitmap.createBitmap(
                    INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888
                ).also { scaledBuffer = it }
                val canvas = scaledCanvas ?: android.graphics.Canvas(scaled)
                    .also { scaledCanvas = it }
                canvas.drawBitmap(bitmap, null, SCALE_DEST_RECT, scaledPaint)

                val input = inputBuffer ?: ByteBuffer
                    .allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3)
                    .also {
                        it.order(java.nio.ByteOrder.nativeOrder())
                        inputBuffer = it
                    }
                input.rewind()

                val px = pixelBuffer ?: IntArray(INPUT_SIZE * INPUT_SIZE).also { pixelBuffer = it }
                scaled.getPixels(px, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

                // Input layout: 224x224x3 float32 ImageNet normalized tensor
                for (pixel in px) {
                    val r = ((pixel shr 16) and 0xFF) / 255f
                    val g = ((pixel shr 8) and 0xFF) / 255f
                    val b = (pixel and 0xFF) / 255f

                    input.putFloat((r - IMAGENET_MEAN[0]) / IMAGENET_STD[0])
                    input.putFloat((g - IMAGENET_MEAN[1]) / IMAGENET_STD[1])
                    input.putFloat((b - IMAGENET_MEAN[2]) / IMAGENET_STD[2])
                }
                input.rewind()

                if (me.avinas.vanderwaals.BuildConfig.DEBUG) Log.d(
                    TAG,
                    "Input buffer size: ${input.remaining()} bytes (expected: ${4 * INPUT_SIZE * INPUT_SIZE * 3})"
                )

                val output = outputBuffer ?: TensorBuffer.createFixedSize(
                    intArrayOf(1, EMBEDDING_SIZE),
                    org.tensorflow.lite.DataType.FLOAT32
                ).also { outputBuffer = it }

                interpreter?.run(input, output.buffer.rewind())
                embedding = output.floatArray
            }

            // L2-normalize to unit length for cosine similarity comparison
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

            val duration = System.currentTimeMillis() - startTime
            if (me.avinas.vanderwaals.BuildConfig.DEBUG) Log.d(TAG, "Embedding extracted in ${duration}ms (normalized magnitude: 1.0)")

            embedding

        } catch (e: Exception) {
            Log.e(TAG, "Error extracting embedding (expected input: ${4 * INPUT_SIZE * INPUT_SIZE * 3} bytes)", e)
            null
        }
    }
    
    fun isReady(): Boolean = isModelLoaded
    
    fun getErrorMessage(): String {
        return if (!isModelLoaded) {
            "TensorFlow Lite model not found. The mobilenet_v4_conv_small.tflite model " +
            "is missing from app/src/main/assets/models/"
        } else {
            ""
        }
    }
    
    fun close() {
        synchronized(inferenceLock) {
            interpreter?.close()
            interpreter = null
            isModelLoaded = false
            scaledBuffer?.recycle()
            scaledBuffer = null
            scaledCanvas = null
        }
        if (me.avinas.vanderwaals.BuildConfig.DEBUG) Log.d(TAG, "TFLite model resources released")
    }
}
