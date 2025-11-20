package me.avinas.vanderwaals.domain.usecase

import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import me.avinas.vanderwaals.algorithm.PreferenceUpdater
import me.avinas.vanderwaals.data.entity.UserPreferences
import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import me.avinas.vanderwaals.data.repository.PreferenceRepository
import javax.inject.Inject

/**
 * Use case for initializing user preferences from uploaded wallpaper.
 * 
 * Orchestrates the onboarding flow where user uploads their favorite wallpaper
 * to bootstrap the personalization system:
 * 
 * 1. Extract embedding from uploaded image using MobileNetV3
 * 2. Calculate similarity against all wallpapers in catalog
 * 3. Return top 50 matches for confirmation gallery
 * 4. Average embeddings of liked wallpapers (minimum 3) to create initial preference vector
 * 5. Store preference vector in database
 * 
 * Also supports "Auto Mode" initialization with a universally appealing default vector.
 * 
 * @see me.avinas.vanderwaals.algorithm.EmbeddingExtractor
 * @see me.avinas.vanderwaals.algorithm.SimilarityCalculator
 * @see me.avinas.vanderwaals.data.repository.PreferenceRepository
 */
class InitializePreferencesUseCase @Inject constructor(
    private val preferenceRepository: PreferenceRepository,
    private val preferenceUpdater: PreferenceUpdater
) {
    
    companion object {
        private const val TAG = "InitializePreferences"
        /**
         * Embedding dimension for MobileNetV3-Small model (576 floats).
         */
        private const val EMBEDDING_SIZE = 576
    }
    
    /**
     * Initialize user preferences from liked/disliked wallpapers.
     * 
     * @param originalEmbedding The original embedding from uploaded image or category prototype
     * @param likedWallpapers Wallpapers the user liked
     * @param dislikedWallpapers Wallpapers the user disliked
     * @return Result indicating success or failure
     */
    suspend operator fun invoke(
        originalEmbedding: FloatArray,
        likedWallpapers: List<WallpaperMetadata>,
        dislikedWallpapers: List<WallpaperMetadata>
    ): Result<Unit> {
        return try {
            Log.d(TAG, "Initializing preferences with ${likedWallpapers.size} liked and ${dislikedWallpapers.size} disliked wallpapers")
            
            // Require at least one liked wallpaper
            if (likedWallpapers.isEmpty()) {
                return Result.failure(Exception("At least one liked wallpaper is required"))
            }
            
            // Calculate initial preference vector by averaging liked embeddings
            val preferenceVector = averageEmbeddings(likedWallpapers.map { it.embedding })
            
            // Apply negative feedback from disliked wallpapers
            var updatedVector = preferenceVector
            dislikedWallpapers.forEach { disliked ->
                val (newVector, _) = preferenceUpdater.updateWithNegativeFeedback(
                    currentVector = updatedVector,
                    targetEmbedding = disliked.embedding,
                    learningRate = 0.15f // Initial learning rate
                )
                updatedVector = newVector
            }
            
            // Create UserPreferences entity with DUAL-ANCHOR system:
            // 1. originalEmbedding: Prime reference from uploaded image/category
            // 2. preferenceVector: Learned from liked/disliked wallpapers
            val userPreferences = UserPreferences(
                id = 1,
                mode = "personalized",
                preferenceVector = updatedVector,
                originalEmbedding = originalEmbedding, // Store original as prime reference
                likedWallpaperIds = likedWallpapers.map { it.id },
                dislikedWallpaperIds = dislikedWallpapers.map { it.id },
                feedbackCount = likedWallpapers.size + dislikedWallpapers.size,
                epsilon = UserPreferences.DEFAULT_EPSILON,
                lastUpdated = System.currentTimeMillis()
            )
            
            Log.d(TAG, "About to save preferences: feedbackCount=${userPreferences.feedbackCount}, " +
                    "vectorNonZero=${userPreferences.preferenceVector.any { it != 0f }}")
            
            // Save to database
            preferenceRepository.insertUserPreferences(userPreferences)
            
            // CRITICAL FIX: Verify the preferences were actually saved
            // This ensures the database transaction is committed before returning
            // Use direct database read to avoid Flow caching issues
            var savedPreferences: UserPreferences? = null
            var attempts = 0
            while (savedPreferences == null && attempts < 5) {
                kotlinx.coroutines.delay(100L)
                savedPreferences = preferenceRepository.getUserPreferencesOnce()
                attempts++
                Log.d(TAG, "Verification attempt $attempts: feedbackCount=${savedPreferences?.feedbackCount}")
            }
            
            if (savedPreferences == null || savedPreferences.feedbackCount == 0) {
                Log.e(TAG, "CRITICAL: Preferences were not properly saved! savedPreferences=$savedPreferences")
                return Result.failure(Exception("Preferences not persisted to database"))
            }
            
            Log.d(TAG, "Successfully initialized and verified preferences with feedback count: ${userPreferences.feedbackCount}")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize preferences", e)
            Result.failure(e)
        }
    }
    
    /**
     * Averages a list of embedding vectors.
     * 
     * @param embeddings List of embedding vectors to average
     * @return Averaged and normalized embedding vector
     */
    private fun averageEmbeddings(embeddings: List<FloatArray>): FloatArray {
        if (embeddings.isEmpty()) {
            return FloatArray(EMBEDDING_SIZE) { 0f }
        }
        
        val averaged = FloatArray(EMBEDDING_SIZE) { 0f }
        
        // Sum all embeddings
        embeddings.forEach { embedding ->
            for (i in averaged.indices) {
                averaged[i] += embedding[i]
            }
        }
        
        // Divide by count
        val count = embeddings.size.toFloat()
        for (i in averaged.indices) {
            averaged[i] /= count
        }
        
        // Normalize to unit length
        return normalizeVector(averaged)
    }
    
    /**
     * Normalizes a vector to unit length.
     */
    private fun normalizeVector(vector: FloatArray): FloatArray {
        var magnitude = 0f
        for (value in vector) {
            magnitude += value * value
        }
        
        if (magnitude == 0f) {
            return vector
        }
        
        magnitude = kotlin.math.sqrt(magnitude)
        
        val normalized = FloatArray(vector.size)
        for (i in vector.indices) {
            normalized[i] = vector[i] / magnitude
        }
        
        return normalized
    }
}
