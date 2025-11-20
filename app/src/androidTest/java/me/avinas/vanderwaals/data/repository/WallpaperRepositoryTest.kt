package me.avinas.vanderwaals.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.avinas.vanderwaals.data.VanderwaalsDatabase
import me.avinas.vanderwaals.data.entity.Converters
import me.avinas.vanderwaals.data.entity.DownloadQueueItem
import me.avinas.vanderwaals.data.entity.WallpaperHistory
import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Instrumented tests for WallpaperRepository.
 *
 * Tests repository layer business logic:
 * - CRUD operations through repository interface
 * - Data transformation and mapping
 * - Transaction management
 * - Flow reactivity and updates
 * - Error handling
 */
@RunWith(AndroidJUnit4::class)
class WallpaperRepositoryTest {

    private lateinit var database: VanderwaalsDatabase
    private lateinit var repository: WallpaperRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            VanderwaalsDatabase::class.java
        )
            .addTypeConverter(Converters(Gson()))
            .allowMainThreadQueries()
            .build()

        repository = WallpaperRepositoryImpl(
            wallpaperDao = database.wallpaperMetadataDao(),
            queueDao = database.downloadQueueDao(),
            historyDao = database.wallpaperHistoryDao()
        )
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        database.close()
    }

    // ==================== Metadata Operations ====================

    @Test
    fun getAllMetadata_returnsEmptyInitially() = runBlocking {
        // When
        val metadata = repository.getAllWallpapers().first()

        // Then
        assertTrue("Should return empty list initially", metadata.isEmpty())
    }

    @Test
    fun insertMetadata_andRetrieve() = runBlocking {
        // Given
        val metadata = createSampleMetadata("001", "nature", "github")

        // When
        repository.insertWallpaper(metadata)
        val retrieved = repository.getWallpaperById("001")

        // Then
        assertNotNull("Should retrieve inserted metadata", retrieved)
        assertEquals("001", retrieved?.id)
        assertEquals("nature", retrieved?.category)
    }

    @Test
    fun insertMultipleMetadata_andFilter() = runBlocking {
        // Given
        val metadata1 = createSampleMetadata("001", "nature", "github")
        val metadata2 = createSampleMetadata("002", "minimal", "github")
        val metadata3 = createSampleMetadata("003", "nature", "bing")

        // When
        repository.insertWallpapers(listOf(metadata1, metadata2, metadata3))
        val natureWallpapers = repository.getWallpapersByCategory("nature").first()

        // Then
        assertEquals(2, natureWallpapers.size)
        assertTrue(natureWallpapers.all { it.category == "nature" })
    }

    @Test
    fun deleteAllMetadata() = runBlocking {
        // Given
        val metadata1 = createSampleMetadata("001", "nature", "github")
        val metadata2 = createSampleMetadata("002", "minimal", "github")
        repository.insertWallpapers(listOf(metadata1, metadata2))

        // When
        repository.deleteAllWallpapers()
        val remaining = repository.getAllWallpapers().first()

        // Then
        assertTrue("Should delete all metadata", remaining.isEmpty())
    }

    // ==================== History Operations ====================

    @Test
    fun recordHistory_andRetrieve() = runBlocking {
        // Given
        val wallpaperId = "001"
        val feedback = "like"

        // When
        repository.recordHistory(wallpaperId, feedback, "github", "nature")
        val history = repository.getAllHistory().first()

        // Then
        assertEquals(1, history.size)
        assertEquals(wallpaperId, history[0].wallpaperId)
        assertEquals(feedback, history[0].feedback)
    }

    @Test
    fun filterHistoryByFeedback() = runBlocking {
        // Given
        repository.recordHistory("001", "like", "github", "nature")
        repository.recordHistory("002", "dislike", "github", "minimal")
        repository.recordHistory("003", "like", "bing", "dark")

        // When
        val likedHistory = repository.getHistoryByFeedback("like").first()

        // Then
        assertEquals(2, likedHistory.size)
        assertTrue(likedHistory.all { it.feedback == "like" })
    }

    @Test
    fun updateHistoryFeedback() = runBlocking {
        // Given
        repository.recordHistory("001", null, "github", "nature")
        val history = repository.getAllHistory().first()
        val historyId = history[0].id

        // When
        repository.updateHistoryFeedback(historyId, "like")
        val updated = repository.getAllHistory().first()

        // Then
        assertEquals("like", updated[0].feedback)
    }

    // ==================== Download Queue Operations ====================

    @Test
    fun addToQueue_andRetrieve() = runBlocking {
        // Given
        val queueItem = createQueueItem("001", 0.95f, false)

        // When
        repository.addToQueue(queueItem)
        val queue = repository.getQueue().first()

        // Then
        assertEquals(1, queue.size)
        assertEquals("001", queue[0].wallpaperId)
        assertEquals(0.95f, queue[0].priority, 0.001f)
    }

    @Test
    fun queueOrderedByPriority() = runBlocking {
        // Given
        val items = listOf(
            createQueueItem("001", 0.85f, false),
            createQueueItem("002", 0.95f, false),
            createQueueItem("003", 0.90f, false)
        )

        // When
        items.forEach { repository.addToQueue(it) }
        val queue = repository.getUndownloadedQueue().first()

        // Then
        assertEquals(3, queue.size)
        assertEquals("002", queue[0].wallpaperId) // Highest priority
        assertTrue("Should order by priority desc", 
            queue[0].priority >= queue[1].priority && 
            queue[1].priority >= queue[2].priority)
    }

    @Test
    fun markAsDownloaded_removesFromUndownloadedQueue() = runBlocking {
        // Given
        repository.addToQueue(createQueueItem("001", 0.95f, false))
        repository.addToQueue(createQueueItem("002", 0.85f, false))

        // When
        repository.markQueueItemDownloaded("001")
        val undownloaded = repository.getUndownloadedQueue().first()

        // Then
        assertEquals(1, undownloaded.size)
        assertEquals("002", undownloaded[0].wallpaperId)
    }

    @Test
    fun clearDownloadedItems() = runBlocking {
        // Given
        repository.addToQueue(createQueueItem("001", 0.95f, true))
        repository.addToQueue(createQueueItem("002", 0.85f, false))
        repository.addToQueue(createQueueItem("003", 0.90f, true))

        // When
        repository.clearDownloadedFromQueue()
        val remaining = repository.getQueue().first()

        // Then
        assertEquals(1, remaining.size)
        assertEquals("002", remaining[0].wallpaperId)
    }

    // ==================== Transaction Tests ====================

    @Test
    fun replaceAllMetadata_inSingleTransaction() = runBlocking {
        // Given - Initial data
        val initial = listOf(
            createSampleMetadata("001", "nature", "github"),
            createSampleMetadata("002", "minimal", "github")
        )
        repository.insertWallpapers(initial)

        // When - Replace with new data
        val newMetadata = listOf(
            createSampleMetadata("003", "dark", "bing"),
            createSampleMetadata("004", "nature", "bing")
        )
        repository.replaceAllWallpapers(newMetadata)

        val allMetadata = repository.getAllWallpapers().first()

        // Then
        assertEquals(2, allMetadata.size)
        assertFalse(allMetadata.any { it.id == "001" || it.id == "002" })
        assertTrue(allMetadata.any { it.id == "003" })
        assertTrue(allMetadata.any { it.id == "004" })
    }

    // ==================== Flow Reactivity Tests ====================

    @Test
    fun flowUpdates_whenDataChanges() = runBlocking {
        // Given
        val flow = repository.getAllWallpapers()
        
        // Initial state
        val initial = flow.first()
        assertEquals(0, initial.size)

        // When - Insert data
        repository.insertWallpaper(createSampleMetadata("001", "nature", "github"))
        
        // Then - Flow emits new value
        val updated = flow.first()
        assertEquals(1, updated.size)
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
