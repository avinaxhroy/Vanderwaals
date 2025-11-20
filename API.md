# Vanderwaals API Documentation

This document provides comprehensive documentation of Vanderwaals's machine learning algorithms, architecture, and APIs.

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Machine Learning Algorithms](#machine-learning-algorithms)
- [Use Cases](#use-cases)
- [Data Models](#data-models)
- [Repositories](#repositories)
- [Workers](#workers)

---

## Architecture Overview

Vanderwaals follows **Clean Architecture** principles with three main layers:

### 1. Data Layer (`me.avinas.vanderwaals.data`)
- **Entities**: Room database models
- **DAOs**: Database access objects
- **Repositories**: Data abstraction
- **DataStore**: User preferences

### 2. Domain Layer (`me.avinas.vanderwaals.domain`)
- **Use Cases**: Business logic with single responsibility
- Independent of framework and UI

### 3. Presentation Layer (`me.avinas.vanderwaals.ui`)
- **ViewModels**: UI state management
- **Composables**: Jetpack Compose UI components

### 4. Algorithm Layer (`me.avinas.vanderwaals.algorithm`)
- **ML Components**: TensorFlow Lite, similarity calculations, learning algorithms

---

## Machine Learning Algorithms

### EmbeddingExtractor

**Location**: `me.avinas.vanderwaals.algorithm.EmbeddingExtractor`

Extracts 576-dimensional feature vectors from images using MobileNetV3-Small.

```kotlin
class EmbeddingExtractor(context: Context) {
    fun extract(bitmap: Bitmap): FloatArray
}
```

**Algorithm**:
1. Resize image to 224x224 (model input size)
2. Normalize pixel values to [0, 1]
3. Run TensorFlow Lite inference
4. Return 576-dimensional embedding vector

**Performance**:
- CPU: ~50-100ms
- GPU (with delegate): ~20-40ms

---

### SimilarityCalculator

**Location**: `me.avinas.vanderwaals.algorithm.SimilarityCalculator`

Calculates similarity between wallpapers using multiple features.

```kotlin
class SimilarityCalculator {
    fun calculateSimilarity(
        userEmbedding: FloatArray,
        userColors: List<String>,
        userCategory: String?,
        wallpaper: WallpaperMetadata
    ): Float
}
```

**Scoring Formula**:
```
finalScore = (embeddingSimilarity × 0.70) +
             (colorSimilarity × 0.20) +
             (categoryBonus × 0.10) +
             temporalDiversityBoost
```

#### Embedding Similarity (70%)

**Cosine Similarity**:
```kotlin
fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    val dotProduct = a.zip(b).sumOf { (x, y) -> x * y }
    val magnitudeA = sqrt(a.sumOf { it * it })
    val magnitudeB = sqrt(b.sumOf { it * it })
    return dotProduct / (magnitudeA * magnitudeB)
}
```

Range: [-1, 1], typical values: [0.3, 0.95]

#### Color Similarity (20%)

**LAB Color Space Distance**:
```kotlin
fun labDistance(lab1: LabColor, lab2: LabColor): Float {
    val deltaL = lab1.l - lab2.l
    val deltaA = lab1.a - lab2.a
    val deltaB = lab1.b - lab2.b
    return sqrt(deltaL² + deltaA² + deltaB²)
}
```

**Why LAB?**
- Perceptually uniform (distance matches human perception)
- Separates lightness (L) from color (a, b)
- Better than RGB for visual similarity

#### Category Bonus (10%)

```kotlin
fun categoryBonus(userCategory: String?, wallpaperCategory: String): Float {
    return if (userCategory == wallpaperCategory) 0.15f else 0.0f
}
```

---

### PreferenceUpdater

**Location**: `me.avinas.vanderwaals.algorithm.PreferenceUpdater`

Updates user preferences using Exponential Moving Average (EMA).

```kotlin
class PreferenceUpdater {
    fun updatePreferences(
        currentPreference: FloatArray,
        newEmbedding: FloatArray,
        feedbackType: FeedbackType,
        feedbackCount: Int
    ): FloatArray
}
```

**EMA Algorithm**:
```kotlin
// Learning rate decreases with more feedback
val alpha = when {
    feedbackCount < 10 -> 0.30f  // Fast learning
    feedbackCount < 50 -> 0.20f  // Moderate
    else -> 0.15f                 // Stable
}

// Feedback direction
val direction = when (feedbackType) {
    FeedbackType.LIKE -> 1.0f     // Move toward
    FeedbackType.DISLIKE -> -1.0f // Move away
}

// EMA update
newPreference[i] = alpha × (newEmbedding[i] × direction) + 
                   (1 - alpha) × currentPreference[i]
```

**Benefits**:
- Smooth learning curve
- Doesn't forget old preferences
- Adaptive learning rate

---

### EnhancedImageAnalyzer

**Location**: `me.avinas.vanderwaals.algorithm.EnhancedImageAnalyzer`

Analyzes image semantics beyond embeddings.

```kotlin
data class ImageAnalysis(
    val dominantColors: List<LabColor>,
    val colorfulness: Float,           // 0.0 to 1.0
    val warmth: Float,                 // -1.0 (cool) to 1.0 (warm)
    val energy: Float,                 // 0.0 (calm) to 1.0 (energetic)
    val complexity: Float,             // 0.0 (simple) to 1.0 (complex)
    val compositionScore: Float,       // Rule of thirds alignment
    val symmetryScore: Float,          // Left-right symmetry
    val visualBalance: Float           // Weight distribution
)
```

#### Features Extracted

1. **Color Analysis**:
   - LAB color space conversion
   - Dominant color extraction (top 5 colors)
   - Colorfulness metric (saturation variance)
   - Warmth score (LAB 'a' component average)

2. **Composition Analysis**:
   - Rule of thirds score (edge density at intersections)
   - Symmetry score (left-right comparison)
   - Visual balance (weight distribution)

3. **Mood Features**:
   - Energy level (colorfulness + complexity)
   - Complexity (edge density across image)

**Performance**: ~50ms per image

---

### ExplorationStrategies

**Location**: `me.avinas.vanderwaals.algorithm.ExplorationStrategies`

Balances exploitation (showing liked wallpapers) vs exploration (discovering new content).

```kotlin
object ExplorationStrategies {
    fun epsilonGreedy(
        rankedWallpapers: List<RankedWallpaper>,
        feedbackCount: Int,
        epsilon: Float = 0.20f
    ): WallpaperMetadata
}
```

**Epsilon-Greedy Algorithm**:
```kotlin
// Exploration rate decays over time
val currentEpsilon = epsilon * exp(-0.01 * feedbackCount)

if (Random.nextFloat() < currentEpsilon) {
    // EXPLORATION: Choose under-explored content
    return selectUnderexploredWallpaper(rankedWallpapers)
} else {
    // EXPLOITATION: Choose highest-ranked wallpaper
    return rankedWallpapers.first().wallpaper
}
```

**Exploration Criteria**:
- New categories (< 3 times viewed)
- High variance wallpapers
- Low feedback count
- Recent additions to catalog

---

### SmartCrop

**Location**: `me.avinas.vanderwaals.algorithm.SmartCrop`

Intelligently crops wallpapers to fit screen aspect ratio.

```kotlin
object SmartCrop {
    fun smartCropBitmap(
        bitmap: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        mode: CropMode = CropMode.AUTO
    ): Bitmap
    
    enum class CropMode {
        CENTER,           // Simple center crop
        RULE_OF_THIRDS,   // Align with rule of thirds
        SALIENCY,         // Focus on salient regions
        FACE_AWARE,       // Detect and center faces
        AUTO              // Best mode selection
    }
}
```

**Saliency Detection Algorithm**:
```kotlin
// 1. Divide image into 8x8 grid
val cells = divideIntoGrid(bitmap, 8, 8)

// 2. Calculate saliency for each cell
for (cell in cells) {
    val edgeScore = detectEdges(cell)       // Sobel operator
    val contrastScore = calculateContrast(cell)
    val saliency = 0.6 × edgeScore + 0.4 × contrastScore
    saliencyMap[cell] = saliency
}

// 3. Find focal points (top 33% cells)
val focalPoints = saliencyMap.sortedByDescending { it.value }
                             .take(cells.size / 3)

// 4. Calculate center of mass
val centerX = focalPoints.sumOf { it.x × it.saliency } / totalSaliency
val centerY = focalPoints.sumOf { it.y × it.saliency } / totalSaliency

// 5. Position crop rectangle around center
val cropRect = Rect(
    left = centerX - targetWidth / 2,
    top = centerY - targetHeight / 2,
    right = centerX + targetWidth / 2,
    bottom = centerY + targetHeight / 2
).clampToBounds(bitmap.width, bitmap.height)

// 6. Extract and scale
return Bitmap.createBitmap(bitmap, cropRect).scale(targetWidth, targetHeight)
```

**Performance**: ~50-100ms per image

---

## Use Cases

All use cases follow the single responsibility principle and are located in `me.avinas.vanderwaals.domain.usecase`.

### SelectNextWallpaperUseCase

Selects the best wallpaper to apply next.

```kotlin
class SelectNextWallpaperUseCase @Inject constructor(
    private val wallpaperRepository: WallpaperRepository,
    private val preferenceRepository: PreferenceRepository,
    private val similarityCalculator: SimilarityCalculator
) {
    suspend operator fun invoke(): Result<WallpaperMetadata>
}
```

**Algorithm**:
1. Get current user preferences
2. Get all available wallpapers
3. Calculate similarity score for each
4. Apply temporal diversity boost
5. Rank wallpapers by final score
6. Use epsilon-greedy selection
7. Return selected wallpaper

---

### ProcessFeedbackUseCase

Processes explicit user feedback (like/dislike).

```kotlin
class ProcessFeedbackUseCase @Inject constructor(
    private val wallpaperRepository: WallpaperRepository,
    private val updatePreferencesUseCase: UpdatePreferencesUseCase
) {
    suspend operator fun invoke(
        wallpaperId: String,
        feedbackType: FeedbackType
    ): Result<Unit>
}
```

**Steps**:
1. Get wallpaper embedding
2. Update user preferences with EMA
3. Record feedback in history
4. Update wallpaper statistics

---

### ProcessImplicitFeedbackUseCase

Learns from wallpaper duration (NEW).

```kotlin
class ProcessImplicitFeedbackUseCase @Inject constructor(
    private val wallpaperRepository: WallpaperRepository,
    private val updatePreferencesUseCase: UpdatePreferencesUseCase
) {
    suspend operator fun invoke(
        wallpaperId: String,
        durationMs: Long,
        isManualChange: Boolean
    ): Result<Unit>
}
```

**Thresholds**:
```kotlin
when {
    !isManualChange -> return // Only learn from manual changes
    durationMs < 5_MINUTES -> processFeedback(wallpaperId, DISLIKE, strength = 0.3f)
    durationMs > 24_HOURS -> processFeedback(wallpaperId, LIKE, strength = 0.3f)
    else -> return // Neutral duration
}
```

**Learning Strength**: 30% of explicit feedback

---

### FindSimilarWallpapersUseCase

Finds wallpapers similar to a given embedding.

```kotlin
class FindSimilarWallpapersUseCase @Inject constructor(
    private val wallpaperRepository: WallpaperRepository,
    private val similarityCalculator: SimilarityCalculator,
    private val enhancedImageAnalyzer: EnhancedImageAnalyzer
) {
    suspend operator fun invoke(
        userEmbedding: FloatArray,
        userAnalysis: ImageAnalysis? = null,
        limit: Int = 50
    ): Result<List<WallpaperMetadata>>
}
```

**Enhanced Matching** (when `userAnalysis` provided):
```kotlin
val embeddingSimilarity = cosineSimilarity(userEmbedding, wallpaper.embedding)
val semanticSimilarity = if (userAnalysis != null && wallpaper.analysis != null) {
    0.40 × colorSimilarity(userAnalysis.colors, wallpaper.analysis.colors) +
    0.25 × compositionSimilarity(userAnalysis, wallpaper.analysis) +
    0.20 × moodSimilarity(userAnalysis, wallpaper.analysis) +
    0.15 × energySimilarity(userAnalysis, wallpaper.analysis)
} else 0.0f

finalScore = 0.75 × embeddingSimilarity + 0.25 × semanticSimilarity
```

---

### ExtractEmbeddingUseCase

Extracts embedding from a bitmap.

```kotlin
class ExtractEmbeddingUseCase @Inject constructor(
    private val embeddingExtractor: EmbeddingExtractor
) {
    suspend operator fun invoke(bitmap: Bitmap): Result<FloatArray> {
        return withContext(Dispatchers.Default) {
            try {
                val embedding = embeddingExtractor.extract(bitmap)
                Result.success(embedding)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
```

---

### SyncWallpaperCatalogUseCase

Syncs wallpaper catalog from GitHub/Bing.

```kotlin
class SyncWallpaperCatalogUseCase @Inject constructor(
    private val manifestRepository: ManifestRepository,
    private val bingRepository: BingRepository,
    private val wallpaperRepository: WallpaperRepository
) {
    suspend operator fun invoke(): Result<Int>
}
```

**Steps**:
1. Fetch manifest from GitHub (6,000+ wallpapers)
2. Fetch Bing daily wallpaper
3. Fetch Bing archive (10,000+ wallpapers)
4. Merge with local database
5. Return count of new wallpapers

---

## Data Models

### WallpaperMetadata

```kotlin
@Entity(tableName = "wallpaper_metadata")
data class WallpaperMetadata(
    @PrimaryKey val id: String,
    val url: String,
    val thumbnailUrl: String?,
    val embedding: FloatArray,      // 576 dimensions
    val colors: List<String>,        // Hex colors
    val category: String?,           // gruvbox, nord, nature, etc.
    val source: String,              // github, bing
    val width: Int,
    val height: Int,
    val addedAt: Long
)
```

### UserPreferences

```kotlin
@Entity(tableName = "user_preferences")
data class UserPreferences(
    @PrimaryKey val id: Int = 1,
    val preferenceVector: FloatArray,  // 576 dimensions
    val feedbackCount: Int = 0,
    val likeCount: Int = 0,
    val dislikeCount: Int = 0,
    val lastUpdated: Long
)
```

### WallpaperHistory

```kotlin
@Entity(tableName = "wallpaper_history")
data class WallpaperHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val wallpaperId: String,
    val appliedAt: Long,
    val durationMs: Long?,
    val feedbackType: FeedbackType?,  // LIKE, DISLIKE, null
    val isManualChange: Boolean = false
)

enum class FeedbackType {
    LIKE, DISLIKE
}
```

---

## Repositories

### WallpaperRepository

```kotlin
interface WallpaperRepository {
    suspend fun getAllWallpapers(): List<WallpaperMetadata>
    suspend fun getWallpaperById(id: String): WallpaperMetadata?
    suspend fun insertWallpapers(wallpapers: List<WallpaperMetadata>)
    suspend fun getHistory(limit: Int = 100): List<WallpaperHistory>
    suspend fun insertHistory(history: WallpaperHistory)
    suspend fun updateFeedback(wallpaperId: String, feedback: FeedbackType)
}
```

### PreferenceRepository

```kotlin
interface PreferenceRepository {
    suspend fun getUserPreferences(): UserPreferences?
    suspend fun updateUserPreferences(preferences: UserPreferences)
    suspend fun initializePreferences(initialVector: FloatArray)
}
```

---

## Workers

### WallpaperChangeWorker

Applies wallpaper automatically on schedule.

```kotlin
class WallpaperChangeWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // 1. Select next wallpaper
        val wallpaper = selectNextWallpaperUseCase()
        
        // 2. Download if needed
        val bitmap = downloadWallpaper(wallpaper.url)
        
        // 3. Apply smart crop
        val cropped = smartCrop.smartCropBitmap(bitmap, screenWidth, screenHeight)
        
        // 4. Set as wallpaper
        wallpaperManager.setBitmap(cropped, null, true, FLAG_SYSTEM or FLAG_LOCK)
        
        // 5. Record to history
        recordHistory(wallpaper.id)
        
        return Result.success()
    }
}
```

### CatalogSyncWorker

Syncs wallpaper catalog weekly.

```kotlin
class CatalogSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val newCount = syncWallpaperCatalogUseCase()
        return Result.success(workDataOf("newCount" to newCount))
    }
}
```

**Schedule**: Weekly, requires network, battery not low

---

## Performance Considerations

### Database Optimization

```kotlin
// Index for similarity search
@Entity(
    tableName = "wallpaper_metadata",
    indices = [
        Index(value = ["category"]),
        Index(value = ["source"]),
        Index(value = ["addedAt"])
    ]
)
```

### Memory Management

- **LRU Cache**: 50MB for image loading (Glide)
- **Database**: Room with SQLite
- **Embeddings**: ~2.3KB per wallpaper (576 floats × 4 bytes)
- **TFLite Model**: ~2.9MB (MobileNetV3-Small)

### Network Optimization

- **Manifest**: Gzip compressed JSON (~500KB → 50KB)
- **Images**: Progressive JPEG loading
- **Batch Downloads**: Queue system with retry logic

---

## Testing

### Unit Tests

```kotlin
@Test
fun `cosine similarity between identical vectors is 1`() {
    val vector = FloatArray(576) { 0.5f }
    val similarity = SimilarityCalculator().cosineSimilarity(vector, vector)
    assertEquals(1.0f, similarity, 0.001f)
}

@Test
fun `EMA updates preferences correctly`() {
    val current = FloatArray(576) { 0.0f }
    val new = FloatArray(576) { 1.0f }
    val updated = PreferenceUpdater().updatePreferences(
        currentPreference = current,
        newEmbedding = new,
        feedbackType = FeedbackType.LIKE,
        feedbackCount = 0
    )
    // With alpha=0.30, result should be [0.30, 0.30, ...]
    assertTrue(updated.all { it in 0.29f..0.31f })
}
```

---

## FAQ

### How accurate is the similarity matching?

MobileNetV3 embeddings provide ~85% accuracy for aesthetic similarity based on our testing. Combined with color and category matching, overall accuracy is ~90%.

### Can I use a different ML model?

Yes! Replace `EmbeddingExtractor` with any TFLite model that outputs feature vectors. Ensure:
- Output is a 1D FloatArray
- Dimensions are consistent
- Update `EMBEDDING_DIMENSION` constant

### How does the app handle cold start (no preferences)?

`InitializePreferencesUseCase` uses the average embedding of all wallpapers as the initial preference vector.

### What happens if TensorFlow Lite fails?

The app falls back to color-based similarity only, with graceful degradation.

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines on contributing to Vanderwaals.

---

© 2024 Vanderwaals - Licensed under GPL-3.0
