package me.avinas.vanderwaals.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.avinas.vanderwaals.data.entity.Converters
import me.avinas.vanderwaals.data.entity.DownloadQueueItem
import me.avinas.vanderwaals.data.entity.UserPreferences
import me.avinas.vanderwaals.data.entity.WallpaperHistory
import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Instrumented tests for VanderwaalsDatabase.
 *
 * Tests Room database functionality with actual Android SQLite implementation:
 * - Entity CRUD operations
 * - TypeConverter serialization/deserialization
 * - Database indexes and queries
 * - Foreign key constraints
 * - Flow reactivity
 *
 * Uses in-memory database for fast, isolated tests.
 */
@RunWith(AndroidJUnit4::class)
class VanderwaalsDatabaseTest {

    private lateinit var database: VanderwaalsDatabase
    private lateinit var context: Context

    @Before
    fun createDb() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(
            context,
            VanderwaalsDatabase::class.java
        )
            .addTypeConverter(Converters(Gson()))
            .allowMainThreadQueries() // OK for tests
            .build()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        database.close()
    }

    // ==================== WallpaperMetadata Tests ====================

    @Test
    fun insertAndRetrieveWallpaperMetadata() = runBlocking {
        // Given
        val metadata = WallpaperMetadata(
            id = "github_dharmx_walls_001",
            sourceUrl = "https://github.com/dharmx/walls/blob/main/forest.jpg",
            thumbnailUrl = "https://github.com/dharmx/walls/blob/main/forest_thumb.jpg",
            category = "nature",
            source = "github",
            embedding = FloatArray(576) { it.toFloat() / 576f },
            colors = listOf("#2D5A3D", "#4A7C59", "#6B9B7E"),
            brightness = 0.45f,
            contrast = 0.65f,
            dominantColor = "#4A7C59"
        )

        // When
        database.wallpaperMetadataDao().insert(metadata)
        val retrieved = database.wallpaperMetadataDao().getById(metadata.id)

        // Then
        assertNotNull("Should retrieve inserted metadata", retrieved)
        assertEquals(metadata.id, retrieved?.id)
        assertEquals(metadata.category, retrieved?.category)
        assertEquals(metadata.source, retrieved?.source)
        assertArrayEquals(metadata.embedding, retrieved?.embedding)
        assertEquals(metadata.colors, retrieved?.colors)
        assertEquals(metadata.brightness, retrieved?.brightness, 0.01f)
    }

    @Test
    fun queryMetadataByCategory() = runBlocking {
        // Given
        val metadata1 = createSampleMetadata("001", "nature", "github")
        val metadata2 = createSampleMetadata("002", "minimal", "github")
        val metadata3 = createSampleMetadata("003", "nature", "bing")

        database.wallpaperMetadataDao().insertAll(listOf(metadata1, metadata2, metadata3))

        // When
        val natureWallpapers = database.wallpaperMetadataDao().getByCategory("nature").first()

        // Then
        assertEquals(2, natureWallpapers.size)
        assertTrue(natureWallpapers.any { it.id == "001" })
        assertTrue(natureWallpapers.any { it.id == "003" })
    }

    @Test
    fun queryMetadataBySource() = runBlocking {
        // Given
        val metadata1 = createSampleMetadata("001", "nature", "github")
        val metadata2 = createSampleMetadata("002", "minimal", "bing")
        val metadata3 = createSampleMetadata("003", "dark", "github")

        database.wallpaperMetadataDao().insertAll(listOf(metadata1, metadata2, metadata3))

        // When
        val githubWallpapers = database.wallpaperMetadataDao().getBySource("github").first()

        // Then
        assertEquals(2, githubWallpapers.size)
        assertTrue(githubWallpapers.all { it.source == "github" })
    }

    @Test
    fun deleteAllMetadata() = runBlocking {
        // Given
        val metadata1 = createSampleMetadata("001", "nature", "github")
        val metadata2 = createSampleMetadata("002", "minimal", "github")
        database.wallpaperMetadataDao().insertAll(listOf(metadata1, metadata2))

        // When
        database.wallpaperMetadataDao().deleteAll()
        val allMetadata = database.wallpaperMetadataDao().getAll().first()

        // Then
        assertTrue("Should delete all metadata", allMetadata.isEmpty())
    }

    // ==================== UserPreferences Tests ====================

    @Test
    fun insertAndRetrieveUserPreferences() = runBlocking {
        // Given
        val preferences = UserPreferences(
            id = 1,
            preferenceVector = FloatArray(576) { 0.5f },
            likedWallpaperIds = listOf("001", "002", "003"),
            dislikedWallpaperIds = listOf("004", "005"),
            feedbackCount = 5,
            learningRate = 0.15f,
            lastUpdated = System.currentTimeMillis()
        )

        // When
        database.userPreferenceDao().upsert(preferences)
        val retrieved = database.userPreferenceDao().get().first()

        // Then
        assertNotNull("Should retrieve preferences", retrieved)
        assertArrayEquals(preferences.preferenceVector, retrieved?.preferenceVector)
        assertEquals(preferences.likedWallpaperIds, retrieved?.likedWallpaperIds)
        assertEquals(preferences.dislikedWallpaperIds, retrieved?.dislikedWallpaperIds)
        assertEquals(preferences.feedbackCount, retrieved?.feedbackCount)
        assertEquals(preferences.learningRate, retrieved?.learningRate, 0.001f)
    }

    @Test
    fun updateUserPreferences() = runBlocking {
        // Given
        val initial = UserPreferences(
            id = 1,
            preferenceVector = FloatArray(576) { 0.0f },
            likedWallpaperIds = emptyList(),
            dislikedWallpaperIds = emptyList(),
            feedbackCount = 0,
            learningRate = 0.2f,
            lastUpdated = System.currentTimeMillis()
        )
        database.userPreferenceDao().upsert(initial)

        // When - Update with feedback
        val updated = initial.copy(
            preferenceVector = FloatArray(576) { 0.1f },
            likedWallpaperIds = listOf("001"),
            feedbackCount = 1,
            learningRate = 0.19f,
            lastUpdated = System.currentTimeMillis()
        )
        database.userPreferenceDao().upsert(updated)

        val retrieved = database.userPreferenceDao().get().first()

        // Then
        assertNotNull(retrieved)
        assertEquals(1, retrieved?.feedbackCount)
        assertEquals(listOf("001"), retrieved?.likedWallpaperIds)
        assertEquals(0.19f, retrieved?.learningRate, 0.001f)
    }

    // ==================== WallpaperHistory Tests ====================

    @Test
    fun insertAndRetrieveHistory() = runBlocking {
        // Given
        val history = WallpaperHistory(
            wallpaperId = "001",
            appliedAt = System.currentTimeMillis(),
            feedback = "like",
            source = "github",
            category = "nature"
        )

        // When
        database.wallpaperHistoryDao().insert(history)
        val allHistory = database.wallpaperHistoryDao().getAll().first()

        // Then
        assertEquals(1, allHistory.size)
        assertEquals(history.wallpaperId, allHistory[0].wallpaperId)
        assertEquals(history.feedback, allHistory[0].feedback)
        assertEquals(history.source, allHistory[0].source)
        assertEquals(history.category, allHistory[0].category)
    }

    @Test
    fun queryHistoryByFeedback() = runBlocking {
        // Given
        val liked1 = createSampleHistory("001", "like", "nature")
        val disliked1 = createSampleHistory("002", "dislike", "minimal")
        val liked2 = createSampleHistory("003", "like", "dark")

        database.wallpaperHistoryDao().insertAll(listOf(liked1, disliked1, liked2))

        // When
        val likedHistory = database.wallpaperHistoryDao().getByFeedback("like").first()

        // Then
        assertEquals(2, likedHistory.size)
        assertTrue(likedHistory.all { it.feedback == "like" })
    }

    @Test
    fun deleteOldHistory() = runBlocking {
        // Given - Insert 10 history entries with different timestamps
        val historyList = (1..10).map { i ->
            WallpaperHistory(
                wallpaperId = "00$i",
                appliedAt = System.currentTimeMillis() - (i * 1000L),
                feedback = null,
                source = "github",
                category = "nature"
            )
        }
        database.wallpaperHistoryDao().insertAll(historyList)

        // When - Keep only 5 most recent
        database.wallpaperHistoryDao().deleteOldEntries(5)
        val remaining = database.wallpaperHistoryDao().getAll().first()

        // Then
        assertEquals(5, remaining.size)
    }

    // ==================== DownloadQueueItem Tests ====================

    @Test
    fun insertAndRetrieveDownloadQueue() = runBlocking {
        // Given
        val queueItem = DownloadQueueItem(
            wallpaperId = "001",
            sourceUrl = "https://github.com/dharmx/walls/blob/main/forest.jpg",
            priority = 0.95f,
            category = "nature",
            addedAt = System.currentTimeMillis(),
            downloaded = false
        )

        // When
        database.downloadQueueDao().insert(queueItem)
        val queue = database.downloadQueueDao().getAll().first()

        // Then
        assertEquals(1, queue.size)
        assertEquals(queueItem.wallpaperId, queue[0].wallpaperId)
        assertEquals(queueItem.priority, queue[0].priority, 0.001f)
        assertFalse(queue[0].downloaded)
    }

    @Test
    fun queryQueueByPriority() = runBlocking {
        // Given - Insert items with different priorities
        val items = listOf(
            createQueueItem("001", 0.95f, false),
            createQueueItem("002", 0.85f, false),
            createQueueItem("003", 0.90f, true)
        )
        database.downloadQueueDao().insertAll(items)

        // When - Get undownloaded items ordered by priority
        val queue = database.downloadQueueDao().getUndownloaded().first()

        // Then
        assertEquals(2, queue.size)
        assertTrue("Should order by priority desc", queue[0].priority >= queue[1].priority)
        assertFalse(queue[0].downloaded)
    }

    @Test
    fun markAsDownloaded() = runBlocking {
        // Given
        val item = createQueueItem("001", 0.95f, false)
        database.downloadQueueDao().insert(item)

        // When
        database.downloadQueueDao().markAsDownloaded("001")
        val retrieved = database.downloadQueueDao().getById("001")

        // Then
        assertNotNull(retrieved)
        assertTrue("Should mark as downloaded", retrieved!!.downloaded)
    }

    @Test
    fun deleteDownloadedItems() = runBlocking {
        // Given
        val items = listOf(
            createQueueItem("001", 0.95f, true),
            createQueueItem("002", 0.85f, false),
            createQueueItem("003", 0.90f, true)
        )
        database.downloadQueueDao().insertAll(items)

        // When
        database.downloadQueueDao().deleteDownloaded()
        val remaining = database.downloadQueueDao().getAll().first()

        // Then
        assertEquals(1, remaining.size)
        assertEquals("002", remaining[0].wallpaperId)
        assertFalse(remaining[0].downloaded)
    }

    // ==================== TypeConverter Tests ====================

    @Test
    fun floatArrayConversion() {
        // Given
        val converters = Converters(Gson())
        val floatArray = FloatArray(576) { it.toFloat() / 576f }

        // When
        val json = converters.fromFloatArray(floatArray)
        val restored = converters.toFloatArray(json)

        // Then
        assertNotNull(restored)
        assertEquals(floatArray.size, restored?.size)
        assertArrayEquals(floatArray, restored)
    }

    @Test
    fun stringListConversion() {
        // Given
        val converters = Converters(Gson())
        val stringList = listOf("id1", "id2", "id3", "id4")

        // When
        val json = converters.fromStringList(stringList)
        val restored = converters.toStringList(json)

        // Then
        assertEquals(stringList, restored)
    }

    @Test
    fun nullSafetyInConverters() {
        // Given
        val converters = Converters(Gson())

        // When
        val emptyList = converters.toStringList(null)
        val emptyArray = converters.toFloatArray(null)

        // Then
        assertTrue(emptyList.isEmpty())
        assertNull(emptyArray)
    }

    // ==================== Helper Methods ====================

    private fun createSampleMetadata(
        id: String,
        category: String,
        source: String
    ) = WallpaperMetadata(
        id = id,
        sourceUrl = "https://example.com/$id.jpg",
        thumbnailUrl = "https://example.com/${id}_thumb.jpg",
        category = category,
        source = source,
        embedding = FloatArray(576) { it.toFloat() / 576f },
        colors = listOf("#FFFFFF", "#000000"),
        brightness = 0.5f,
        contrast = 0.5f,
        dominantColor = "#FFFFFF"
    )

    private fun createSampleHistory(
        wallpaperId: String,
        feedback: String,
        category: String
    ) = WallpaperHistory(
        wallpaperId = wallpaperId,
        appliedAt = System.currentTimeMillis(),
        feedback = feedback,
        source = "github",
        category = category
    )

    private fun createQueueItem(
        wallpaperId: String,
        priority: Float,
        downloaded: Boolean
    ) = DownloadQueueItem(
        wallpaperId = wallpaperId,
        sourceUrl = "https://example.com/$wallpaperId.jpg",
        priority = priority,
        category = "nature",
        addedAt = System.currentTimeMillis(),
        downloaded = downloaded
    )
}
