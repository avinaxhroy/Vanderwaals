package me.avinas.vanderwaals.ui.onboarding

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.avinas.vanderwaals.algorithm.EnhancedImageAnalyzer
import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import me.avinas.vanderwaals.domain.usecase.ExtractEmbeddingUseCase
import me.avinas.vanderwaals.domain.usecase.FindSimilarWallpapersUseCase
import javax.inject.Inject

/**
 * ViewModel for wallpaper upload screen.
 * 
 * Handles:
 * - User uploading their favorite wallpaper
 * - Selecting from 6 style samples (nature, minimal, dark, abstract, colorful, anime)
 * - Extracting embedding from selected image
 * - Finding top 50 similar wallpapers
 * 
 * **State:**
 * - uploadState: Current upload/processing state
 * - similarWallpapers: Top 50 matches based on embedding similarity
 * 
 * **Flow:**
 * 1. User selects image (upload or sample)
 * 2. Extract embedding (40-50ms)
 * 3. Find similar wallpapers (50ms)
 * 4. Navigate to confirmation gallery
 * 
 * @param extractEmbeddingUseCase Extracts 1280-dim embedding from image
 * @param findSimilarWallpapersUseCase Finds top N similar wallpapers
 */
@HiltViewModel
class UploadWallpaperViewModel @Inject constructor(
    private val extractEmbeddingUseCase: ExtractEmbeddingUseCase,
    private val findSimilarWallpapersUseCase: FindSimilarWallpapersUseCase,
    private val enhancedImageAnalyzer: EnhancedImageAnalyzer,
    private val wallpaperRepository: me.avinas.vanderwaals.data.repository.WallpaperRepository,
    private val settingsDataStore: me.avinas.vanderwaals.data.datastore.SettingsDataStore,
    @param:ApplicationContext private val context: Context
) : ViewModel() {
    
    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Initial)
    val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()
    
    private val _similarWallpapers = MutableStateFlow<List<WallpaperMetadata>>(emptyList())
    val similarWallpapers: StateFlow<List<WallpaperMetadata>> = _similarWallpapers.asStateFlow()
    
    private val _userEmbedding = MutableStateFlow<FloatArray?>(null)
    val userEmbedding: StateFlow<FloatArray?> = _userEmbedding.asStateFlow()
    
    private val _userImageAnalysis = MutableStateFlow<EnhancedImageAnalyzer.ImageAnalysis?>(null)
    val userImageAnalysis: StateFlow<EnhancedImageAnalyzer.ImageAnalysis?> = _userImageAnalysis.asStateFlow()
    
    /**
     * Upload and process user's wallpaper with ENHANCED ANALYSIS.
     * 
     * Steps:
     * 1. Load bitmap from URI
     * 2. Extract embedding from URI (MobileNetV4 deep features)
     * 3. Extract enhanced features (color, composition, mood)
     * 4. Find top 20 similar wallpapers using both analyses
     * 5. Update state
     * 
     * @param uri Image URI from picker
     */
    fun uploadWallpaper(uri: Uri) {
        viewModelScope.launch {
            _uploadState.value = UploadState.Extracting
            
            var bitmap: android.graphics.Bitmap? = null
            
            try {
                // Load bitmap for enhanced analysis using BitmapManager
                bitmap = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        me.avinas.vanderwaals.core.BitmapManager.loadBitmapFromStream(inputStream)
                    }
                }
                
                if (bitmap == null) {
                    _uploadState.value = UploadState.Error("Failed to load image")
                    return@launch
                }
                
                // Extract embedding (MobileNetV4)
                extractEmbeddingUseCase(uri).fold(
                    onSuccess = { embedding ->
                        _userEmbedding.value = embedding
                        
                        // LOG: Debug embedding extraction
                        val embeddingPreview = embedding.take(10).joinToString(", ", "[", ", ...]")
                        val embeddingEnd = embedding.takeLast(5).joinToString(", ", "[", "]")
                        android.util.Log.d("UploadWallpaper", "Extracted embedding from uploaded image")
                        android.util.Log.d("UploadWallpaper", "Embedding START: $embeddingPreview")
                        android.util.Log.d("UploadWallpaper", "Embedding END: $embeddingEnd")
                        android.util.Log.d("UploadWallpaper", "Embedding magnitude: ${embedding.map { it * it }.sum().let { kotlin.math.sqrt(it) }}")
                        android.util.Log.d("UploadWallpaper", "Embedding stats: min=${embedding.minOrNull()}, max=${embedding.maxOrNull()}, avg=${embedding.average()}")
                        
                        // Extract enhanced features (color, composition, mood)
                        val analysis = withContext(Dispatchers.IO) {
                            // Create local val for smart cast
                            val bitmapForAnalysis = bitmap ?: return@withContext null
                            enhancedImageAnalyzer.analyze(bitmapForAnalysis)
                        }
                        _userImageAnalysis.value = analysis
                        
                        // Find similar wallpapers using BOTH embedding and enhanced analysis
                        findSimilarWallpapers(embedding, analysis)
                        
                        // Clean up bitmap using BitmapManager
                        me.avinas.vanderwaals.core.BitmapManager.recycleSafely(bitmap)
                        bitmap = null
                    },
                    onFailure = { error ->
                        me.avinas.vanderwaals.core.BitmapManager.recycleSafely(bitmap)
                        bitmap = null
                        _uploadState.value = UploadState.Error(
                            error.message ?: "Failed to process image"
                        )
                    }
                )
            } catch (e: Exception) {
                me.avinas.vanderwaals.core.BitmapManager.recycleSafely(bitmap)
                _uploadState.value = UploadState.Error(
                    "Error loading image: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Select a pre-defined style sample.
     * 
     * Style samples:
     * - Nature: Green/blue tones, outdoor scenes
     * - Minimal: Simple, clean, monochromatic
     * - Dark: Dark backgrounds, high contrast
     * - Abstract: Geometric, artistic patterns
     * - Colorful: Vibrant, multi-colored
     * - Anime: Anime/manga art style
     * 
     * IMPROVED (Nov 2025): Uses actual wallpapers from category to generate
     * a real representative embedding, rather than placeholder vectors.
     * This ensures better matching quality.
     * 
     * @param style Sample style category
     */
    fun selectSampleWallpaper(style: WallpaperStyle) {
        viewModelScope.launch {
            _uploadState.value = UploadState.Extracting
            
            android.util.Log.d("UploadWallpaper", "Selecting sample wallpaper for style: ${style.displayName}")
            
            try {
                // Get user's enabled sources
                val settings = settingsDataStore.settings.first()
                val enabledSources = mutableListOf<String>()
                if (settings.githubEnabled) enabledSources.add("github")
                if (settings.bingEnabled) enabledSources.add("bing")
                
                // IMPROVED: Get actual wallpapers from category first
                // Then use their embeddings as the "user preference"
                val allCategoryWallpapers = withContext(Dispatchers.IO) {
                    wallpaperRepository.getWallpapersByCategory(style.categoryName)
                        .first()
                }
                
                // Filter by enabled sources
                val categoryWallpapers = if (enabledSources.isEmpty()) {
                    allCategoryWallpapers
                } else {
                    allCategoryWallpapers.filter { it.source.lowercase() in enabledSources }
                }
                
                if (categoryWallpapers.isEmpty()) {
                    android.util.Log.w("UploadWallpaper", "No wallpapers found for category: ${style.categoryName} from sources: $enabledSources")
                    _uploadState.value = UploadState.Error(
                        "No '${style.displayName}' wallpapers available from your enabled sources. Please try another style or wait for the catalog to sync."
                    )
                    return@launch
                }
                
                // Calculate average embedding from top wallpapers in this category
                // This creates a "prototype" that represents the category's essence
                val sampleSize = minOf(5, categoryWallpapers.size)
                val sampleWallpapers = categoryWallpapers.shuffled().take(sampleSize)
                
                val avgEmbedding = FloatArray(1280) { 0f }
                for (wallpaper in sampleWallpapers) {
                    for (i in wallpaper.embedding.indices) {
                        avgEmbedding[i] += wallpaper.embedding[i]
                    }
                }
                for (i in avgEmbedding.indices) {
                    avgEmbedding[i] /= sampleSize
                }
                
                _userEmbedding.value = avgEmbedding
                
                // LOG: Debug embedding generation
                val embeddingPreview = avgEmbedding.take(5).joinToString(", ", "[", ", ...]")
                android.util.Log.d("UploadWallpaper", "Created representative embedding from $sampleSize sample wallpapers")
                android.util.Log.d("UploadWallpaper", "Embedding preview: $embeddingPreview")
                android.util.Log.d("UploadWallpaper", "Embedding magnitude: ${avgEmbedding.map { it * it }.sum().let { kotlin.math.sqrt(it) }}")
                
                // CRITICAL: Use category filtering to show relevant wallpapers
                // When user selects a category, show wallpapers FROM that category
                // This ensures the confirmation gallery matches user's expectation
                findSimilarWallpapersByCategory(style.categoryName)
                
            } catch (e: Exception) {
                android.util.Log.e("UploadWallpaper", "Error selecting sample wallpaper: ${e.message}")
                _uploadState.value = UploadState.Error(
                    "Failed to load '${style.displayName}' samples: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Find similar wallpapers based on embedding AND enhanced analysis.
     * 
     * ENHANCED MATCHING: Uses both MobileNetV4 embeddings and semantic features
     * (color palette, composition, mood) to capture the ESSENCE of the image.
     * 
     * Returns top 50 matches for onboarding.
     * 
     * FLOW:
     * 1. User uploads image OR selects category → Generate embedding
     * 2. Find 50 similar wallpapers using cosine similarity
     * 3. Show 12 in confirmation gallery
     * 4. User likes/dislikes wallpapers
     * 5. Initialize preference vector from LIKED wallpapers
     * 6. Keep original embedding as prime reference
     * 
     * CRITICAL FIX: If database is empty, show helpful error message to sync catalog.
     * 
     * @param embedding User's wallpaper embedding (from upload or category prototype)
     * @param analysis Enhanced image analysis (color, composition, mood) - null for category samples
     */
    private suspend fun findSimilarWallpapers(
        embedding: FloatArray,
        analysis: EnhancedImageAnalyzer.ImageAnalysis?
    ) {
        _uploadState.value = UploadState.FindingMatches
        
        android.util.Log.d("UploadWallpaper", "Finding matches with enhanced analysis...")
        if (analysis != null) {
            android.util.Log.d("UploadWallpaper", "Analysis: warmth=${analysis.warmth}, energy=${analysis.energy}, " +
                    "complexity=${analysis.complexity}, composition=${analysis.compositionScore}")
        }
        
        // Get 50 similar wallpapers for onboarding
        // User sees 12 at a time in confirmation gallery (can refresh for more)
        // These will be the pool for like/dislike feedback
        findSimilarWallpapersUseCase(
            userEmbedding = embedding,
            userAnalysis = analysis,  // ENHANCED: Pass image analysis for semantic matching (null for category samples)
            limit = 50  // INCREASED: More variety for refresh functionality
        ).fold(
            onSuccess = { wallpapers ->
                // CRITICAL FIX: Check if database is empty
                if (wallpapers.isEmpty()) {
                    android.util.Log.w("UploadWallpaper", "No wallpapers found in database!")
                    _uploadState.value = UploadState.Error(
                        "Wallpaper catalog is empty. The app is syncing wallpapers in the background. Please wait 2-3 minutes for the initial sync to complete, then try again. Check Settings to monitor sync progress."
                    )
                } else {
                    android.util.Log.d("UploadWallpaper", "Found ${wallpapers.size} similar wallpapers using enhanced matching")
                    _similarWallpapers.value = wallpapers
                    _uploadState.value = UploadState.Success(wallpapers.size)
                }
            },
            onFailure = { error ->
                android.util.Log.e("UploadWallpaper", "Error finding wallpapers: ${error.message}")
                _uploadState.value = UploadState.Error(
                    error.message ?: "Failed to find similar wallpapers"
                )
            }
        )
    }
    
    /**
     * Find wallpapers by category instead of embedding similarity.
     * Used when user selects a style category during onboarding.
     * 
     * This provides better results than placeholder embeddings because it actually
     * filters wallpapers from the selected category.
     * 
     * IMPROVED (Nov 2025): Better shuffling with time-based seed to ensure
     * different users see different wallpapers, not the same sequence.
     * 
     * @param category Category name to filter by (e.g., "nature", "minimal", "anime")
     */
    private suspend fun findSimilarWallpapersByCategory(category: String) {
        _uploadState.value = UploadState.FindingMatches
        
        android.util.Log.d("UploadWallpaper", "Finding wallpapers by category: $category")
        
        try {
            // Get user's enabled sources
            val settings = settingsDataStore.settings.first()
            val enabledSources = mutableListOf<String>()
            if (settings.githubEnabled) enabledSources.add("github")
            if (settings.bingEnabled) enabledSources.add("bing")
            
            android.util.Log.d("UploadWallpaper", "Enabled sources: $enabledSources")
            
            // Get wallpapers directly from category
            val allCategoryWallpapers = withContext(Dispatchers.IO) {
                wallpaperRepository.getWallpapersByCategory(category)
                    .first()
            }
            
            // CRITICAL FIX: Filter by enabled sources
            val filteredWallpapers = if (enabledSources.isEmpty()) {
                allCategoryWallpapers
            } else {
                allCategoryWallpapers.filter { it.source.lowercase() in enabledSources }
            }
            
            android.util.Log.d("UploadWallpaper", "Category wallpapers: ${allCategoryWallpapers.size} total, ${filteredWallpapers.size} after source filter")
            
            if (filteredWallpapers.isEmpty()) {
                android.util.Log.w("UploadWallpaper", "No wallpapers found for category: $category with sources: $enabledSources")
                _uploadState.value = UploadState.Error(
                    "No wallpapers found in the '$category' category from your enabled sources. Try another style or wait for the catalog to sync."
                )
            } else {
                // IMPROVED: Use time-based seed for randomization
                // This ensures different users see different wallpapers in confirmation gallery
                val seed = System.currentTimeMillis() + android.os.SystemClock.uptimeMillis()
                val random = kotlin.random.Random(seed.toInt())
                
                val wallpapers = filteredWallpapers
                    .shuffled(random)  // Randomize with time-based seed for true variety
                    .take(50)          // Take 50 for onboarding (matches upload flow)
                
                android.util.Log.d("UploadWallpaper", "Found ${wallpapers.size} wallpapers in category: $category (sources: $enabledSources)")
                _similarWallpapers.value = wallpapers
                _uploadState.value = UploadState.Success(wallpapers.size)
            }
        } catch (e: Exception) {
            android.util.Log.e("UploadWallpaper", "Error finding wallpapers by category: ${e.message}")
            _uploadState.value = UploadState.Error(
                e.message ?: "Failed to find wallpapers"
            )
        }
    }
    
        /**
     * Get pre-computed embedding for a sample wallpaper style.
     * 
     * These embeddings represent characteristic features of each style category.
     * In production, these would be pre-computed from representative wallpapers
     * and stored in assets. For now, we use distinct placeholder vectors.
     * 
     * Note: The actual embeddings will be computed when the user uploads a wallpaper
     * or when they select wallpapers from the catalog during onboarding.
     * 
     * @param style Sample style category
     * @return 1280-dimensional embedding vector (MobileNetV4-Conv-Small)
     */
    private fun getSampleEmbedding(style: WallpaperStyle): FloatArray {
        // Generate distinct placeholder embeddings for each style
        // These create different similarity patterns for demonstration
        return when (style) {
            WallpaperStyle.NATURE -> FloatArray(1280) { i -> 
                (kotlin.math.sin(i * 0.1) * 0.5 + 0.5).toFloat()
            }
            WallpaperStyle.MINIMAL -> FloatArray(1280) { i -> 
                if (i < 640) 0.8f else 0.2f
            }
            WallpaperStyle.DARK -> FloatArray(1280) { i -> 
                (kotlin.math.cos(i * 0.15) * 0.3 + 0.3).toFloat()
            }
            WallpaperStyle.ABSTRACT -> FloatArray(1280) { i -> 
                ((i % 100) / 100f)
            }
            WallpaperStyle.COLORFUL -> FloatArray(1280) { i -> 
                (kotlin.math.sin(i * 0.05) * kotlin.math.cos(i * 0.1) * 0.5 + 0.5).toFloat()
            }
            WallpaperStyle.ANIME -> FloatArray(1280) { i -> 
                if (i % 3 == 0) 0.9f else if (i % 3 == 1) 0.6f else 0.3f
            }
        }
    }
    
    /**
     * Reset upload state.
     */
    fun resetState() {
        _uploadState.value = UploadState.Initial
        _similarWallpapers.value = emptyList()
        _userEmbedding.value = null
    }
    
    /**
     * Reset state for back navigation from ConfirmationGallery.
     * 
     * This resets the upload state to allow user to:
     * - Upload a different wallpaper
     * - Select a different style sample
     * 
     * CRITICAL: Does NOT clear similarWallpapers or userEmbedding
     * so that if user goes forward again without making changes,
     * they can continue with previous results.
     * 
     * Only resets uploadState from Success → Initial to prevent
     * auto-navigation back to ConfirmationGallery.
     */
    fun resetStateForBackNavigation() {
        android.util.Log.d("UploadWallpaperViewModel", "Resetting state for back navigation")
        _uploadState.value = UploadState.Initial
    }
    
    /**
     * Check if results are already available (user navigated back then forward).
     * If results exist, can navigate directly to confirmation gallery.
     * 
     * @return true if similar wallpapers were already found
     */
    fun hasExistingResults(): Boolean {
        return _similarWallpapers.value.isNotEmpty() && _userEmbedding.value != null
    }
    
    /**
     * Re-trigger success state if results already exist.
     * Used when user navigates back then forward without making changes.
     */
    fun useExistingResults() {
        if (hasExistingResults()) {
            android.util.Log.d("UploadWallpaperViewModel", "Using existing results: ${_similarWallpapers.value.size} wallpapers")
            _uploadState.value = UploadState.Success(_similarWallpapers.value.size)
        }
    }
}

/**
 * Upload and processing state.
 */
sealed class UploadState {
    /**
     * Initial state, no upload started.
     */
    data object Initial : UploadState()
    
    /**
     * Extracting embedding from image.
     */
    data object Extracting : UploadState()
    
    /**
     * Finding similar wallpapers.
     */
    data object FindingMatches : UploadState()
    
    /**
     * Successfully found matches.
     * 
     * @param matchCount Number of matches found
     */
    data class Success(val matchCount: Int) : UploadState()
    
    /**
     * Error occurred during processing.
     * 
     * @param message Error description
     */
    data class Error(val message: String) : UploadState()
}

/**
 * Pre-defined wallpaper style categories.
 * Maps to actual category names in the database.
 */
enum class WallpaperStyle(
    val displayName: String,
    val categoryName: String
) {
    NATURE("Nature", "nature"),
    MINIMAL("Minimal", "minimal"),
    DARK("Dark", "dark"),
    ABSTRACT("Abstract", "abstract"),
    COLORFUL("Colorful", "vibrant"),
    ANIME("Anime", "anime")
}
