# Vanderwaals API documentation

Technical documentation for Vanderwaals machine learning components, use cases, data models, and background workers.

## Table of contents

- [Architecture overview](#architecture-overview)
- [Machine learning components](#machine-learning-components)
- [Use cases](#use-cases)
- [Data models](#data-models)
- [Repositories](#repositories)
- [Background workers](#background-workers)

---

## Architecture overview

Vanderwaals follows Clean Architecture and MVVM patterns across four primary packages:

1. **Data layer** (`me.avinas.vanderwaals.data`): Room database entities, DAOs, repositories, and DataStore settings.
2. **Domain layer** (`me.avinas.vanderwaals.domain`): Single-responsibility use cases.
3. **Presentation layer** (`me.avinas.vanderwaals.ui`): ViewModels and Jetpack Compose UI.
4. **Algorithm layer** (`me.avinas.vanderwaals.algorithm`): TensorFlow Lite feature extraction, similarity math, and preference learning algorithms.

---

## Machine learning components

### EmbeddingExtractor

**Package**: `me.avinas.vanderwaals.algorithm.EmbeddingExtractor`

Extracts 1280-dimensional feature vectors from input images using MobileNetV4-Conv-Small.

```kotlin
class EmbeddingExtractor(context: Context) {
    fun extract(bitmap: Bitmap): FloatArray
}
```

**Execution steps**:
1. Resize input bitmap to $224 \times 224$ pixels.
2. Normalize RGB pixel values to $[0.0, 1.0]$.
3. Execute synchronized TensorFlow Lite inference.
4. Return a 1280-dimensional `FloatArray`.

---

### SimilarityCalculator

**Package**: `me.avinas.vanderwaals.algorithm.SimilarityCalculator`

Calculates aesthetic similarity scores between user preference profiles and wallpaper metadata entries.

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

**Scoring formula**:
$$\text{Score} = (S_{\text{embedding}} \times 0.75) + (S_{\text{color}} \times 0.12) + (S_{\text{category}} \times 0.02) + (S_{\text{composition}} \times 0.11)$$

#### Embedding similarity (70–75%)
Cosine similarity between feature vectors:

$$\text{Similarity}(A, B) = \frac{A \cdot B}{\|A\| \|B\|}$$

#### Color space matching (12–20%)
Distance calculated in perceptual CIELAB color space ($D65$ illuminant):

$$\Delta E_{76} = \sqrt{(L_1 - L_2)^2 + (a_1 - a_2)^2 + (b_1 - b_2)^2}$$

---

### PreferenceUpdater

**Package**: `me.avinas.vanderwaals.algorithm.PreferenceUpdater`

Updates user preference vectors using Exponential Moving Average (EMA):

$$\text{Vector}_{\text{new}} = \alpha \cdot \text{Feedback} + (1 - \alpha) \cdot \text{Vector}_{\text{current}}$$

Learning rate $\alpha$ adjusts based on interaction history:
- $\alpha = 0.30$ for initial ratings ($<10$ interactions)
- $\alpha = 0.20$ for moderate history ($10\text{--}50$ interactions)
- $\alpha = 0.15$ for established profiles ($>50$ interactions)

---

## Use cases

Located in `me.avinas.vanderwaals.domain.usecase`.

### SelectNextWallpaperUseCase
Selects the next wallpaper to apply based on user preferences, diversity rules, and pre-caching state.

```kotlin
class SelectNextWallpaperUseCase @Inject constructor(
    private val wallpaperRepository: WallpaperRepository,
    private val preferenceRepository: PreferenceRepository,
    private val similarityCalculator: SimilarityCalculator
) {
    suspend operator fun invoke(): Result<WallpaperMetadata>
}
```

### ProcessFeedbackUseCase
Processes explicit user ratings (like or dislike) and triggers vector updates.

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

### ProcessImplicitFeedbackUseCase
Evaluates wallpaper retention duration to infer feedback:
- Retention $< 5$ minutes: Dislike signal (30% weight)
- Retention $> 24$ hours: Like signal (30% weight)

---

## Data models

### WallpaperMetadata

```kotlin
@Entity(tableName = "wallpaper_metadata")
data class WallpaperMetadata(
    @PrimaryKey val id: String,
    val url: String,
    val thumbnailUrl: String?,
    val embedding: FloatArray,      // 1280 dimensions
    val colors: List<String>,        // Hex color codes
    val category: String?,
    val source: String,
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
    val preferenceVector: FloatArray,  // 1280 dimensions
    val feedbackCount: Int = 0,
    val likeCount: Int = 0,
    val dislikeCount: Int = 0,
    val lastUpdated: Long
)
```

---

## Background workers

### WallpaperChangeWorker
Executes wallpaper rotation tasks scheduled via WorkManager.

### CatalogSyncWorker
Synchronizes wallpaper manifests periodically from CDN sources.

---

## License

Copyright © 2024–2025 Avinas / Confused Coconut. Dual-licensed under AGPL-3.0 and Commercial terms. See [LICENSE](LICENSE) and [COMMERCIAL_LICENSE.md](COMMERCIAL_LICENSE.md).
