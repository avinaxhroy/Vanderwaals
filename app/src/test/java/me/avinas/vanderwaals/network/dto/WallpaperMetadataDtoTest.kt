package me.avinas.vanderwaals.network.dto

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class WallpaperMetadataDtoTest {

    // ========== Embedding Dimension Tests ==========

    @Test
    fun `getEmbeddingArray returns empty when no embedding data available`() {
        val dto = createDto(
            embedding = null,
            e = null,
            eMin = null,
            eMax = null
        )

        val result = dto.getEmbeddingArray()

        // empty, not a 576D default
        assertTrue(result.isEmpty())
        assertEquals(0, result.size)
    }

    @Test
    fun `getEmbeddingArray returns full embedding when available (v1 format)`() {
        // Full 576D embedding (v1 legacy format).
        val fullEmbedding = (0 until 576).map { it * 0.001f }
        val dto = createDto(
            embedding = fullEmbedding,
            e = null,
            eMin = null,
            eMax = null
        )

        val result = dto.getEmbeddingArray()

        assertEquals(576, result.size)
        assertEquals(0.0f, result[0], 0.0001f)
        assertEquals(0.001f, result[1], 0.0001f)
    }

    @Test
    fun `getEmbeddingArray returns full 1280D embedding when available`() {
        // Full 1280D embedding (v3 format).
        val fullEmbedding = (0 until 1280).map { it * 0.0001f }
        val dto = createDto(
            embedding = fullEmbedding,
            e = null,
            eMin = null,
            eMax = null
        )

        val result = dto.getEmbeddingArray()

        assertEquals(1280, result.size)
        assertEquals(0.0f, result[0], 0.0001f)
        assertEquals(0.1279f, result[1279], 0.0001f)
    }

    // NOTE: Quantized embedding tests (dequantizeEmbedding) require Android instrumented tests
    // because android.util.Base64 is not available in JVM unit tests.
    // The quantized format is tested via integration tests instead.

    @Test
    fun `getEmbeddingArray returns full embedding when quantized fields are incomplete`() {
        // e set but eMin/eMax missing (incomplete quantized).
        val fullEmbedding = (0 until 576).map { 0.5f }
        val dto = createDto(
            embedding = fullEmbedding,
            e = "someBase64Data",
            eMin = null,  // Missing
            eMax = null   // Missing
        )

        val result = dto.getEmbeddingArray()

        // Falls back to the full embedding; quantized data is incomplete.
        assertEquals(576, result.size)
    }

    @Test
    fun `toEntity handles empty embedding gracefully`() {
        val dto = createDto(
            embedding = null,
            e = null,
            eMin = null,
            eMax = null
        )

        val entity = dto.toEntity()

        assertTrue(entity.embedding.isEmpty())
    }

    @Test
    fun `toEntity preserves all metadata fields`() {
        val embedding = (0 until 1280).map { it * 0.001f }
        val dto = WallpaperMetadataDto(
            id = "test_wallpaper_001",
            url = "https://example.com/wallpaper.jpg",
            thumbnail = "https://example.com/thumb.jpg",
            source = "github",
            repo = "test/walls",
            category = "nature",
            colors = listOf("#282828", "#cc241d", "#98971a"),
            brightness = 65,
            contrast = 50,
            resolution = "3840x2160",
            attribution = "Test Author",
            embedding = embedding,
            e = null,
            eMin = null,
            eMax = null
        )

        val entity = dto.toEntity()

        assertEquals("test_wallpaper_001", entity.id)
        assertEquals("https://example.com/wallpaper.jpg", entity.url)
        assertEquals("https://example.com/thumb.jpg", entity.thumbnailUrl)
        assertEquals("github", entity.source)
        assertEquals("nature", entity.category)
        assertEquals(3, entity.colors.size)
        assertEquals(65, entity.brightness)
        assertEquals(50, entity.contrast)
        assertEquals("3840x2160", entity.resolution)
        assertEquals("Test Author", entity.attribution)
        assertEquals(1280, entity.embedding.size)
    }

    @Test
    fun `toEntity uses url as fallback when thumbnail is null`() {
        val dto = createDto(
            thumbnail = null,
            url = "https://example.com/wallpaper.jpg"
        )

        val entity = dto.toEntity()

        assertEquals("https://example.com/wallpaper.jpg", entity.thumbnailUrl)
    }

    // ========== Gson Deserialization Regression Tests ==========

    @Test
    fun `toEntity survives Gson deserialization when mood and style are absent`() {
        // Gson instantiates DTOs via reflection, bypassing Kotlin default values:
        // fields absent from the JSON are null at runtime even when declared
        // non-null with a default. Community and Bing manifests omit mood/style
        // entirely, which used to crash toEntity() with an NPE.
        val json = """
            {
                "id": "bing_2026-06-30_US_en",
                "url": "https://example.com/wallpaper.jpg",
                "source": "bing",
                "category": "nature",
                "colors": ["#282828"],
                "brightness": 65,
                "contrast": 50,
                "resolution": "1920x1080",
                "attribution": null,
                "embedding": [0.1, 0.2]
            }
        """.trimIndent()

        val dto = Gson().fromJson(json, WallpaperMetadataDto::class.java)
        val entity = dto.toEntity()

        assertEquals("bing_2026-06-30_US_en", entity.id)
        assertTrue(entity.mood.isEmpty())
        assertTrue(entity.style.isEmpty())
        assertEquals(0f, entity.aestheticScore, 0f)
        assertEquals(2, entity.embedding.size)
    }

    @Test
    fun `toEntity preserves mood style and aestheticScore when present`() {
        val json = """
            {
                "id": "vc_001",
                "url": "https://example.com/wallpaper.jpg",
                "source": "vanderwaals",
                "category": "abstract",
                "colors": ["#000000"],
                "brightness": 50,
                "contrast": 50,
                "resolution": "1920x1080",
                "attribution": null,
                "mood": ["surreal", "cozy"],
                "style": ["minimalist"],
                "aestheticScore": 7.5
            }
        """.trimIndent()

        val dto = Gson().fromJson(json, WallpaperMetadataDto::class.java)
        val entity = dto.toEntity()

        assertEquals(listOf("surreal", "cozy"), entity.mood)
        assertEquals(listOf("minimalist"), entity.style)
        assertEquals(7.5f, entity.aestheticScore, 0.001f)
    }

    // ========== Dimension Detection Helper Tests ==========

    @Test
    fun `embedding dimension correctly detected from full embedding`() {
        val dto576 = createDto(embedding = (0 until 576).map { 0.0f })
        val dto1280 = createDto(embedding = (0 until 1280).map { 0.0f })
        val dtoEmpty = createDto(embedding = null)

        val embed576 = dto576.getEmbeddingArray()
        val embed1280 = dto1280.getEmbeddingArray()
        val embedEmpty = dtoEmpty.getEmbeddingArray()

        assertEquals(576, embed576.size)
        assertEquals(1280, embed1280.size)
        assertEquals(0, embedEmpty.size)
    }

    // ========== Helper Functions ==========

    private fun createDto(
        embedding: List<Float>? = null,
        e: String? = null,
        eMin: Float? = null,
        eMax: Float? = null,
        thumbnail: String? = "https://example.com/thumb.jpg",
        url: String = "https://example.com/wallpaper.jpg"
    ): WallpaperMetadataDto {
        return WallpaperMetadataDto(
            id = "test_id",
            url = url,
            thumbnail = thumbnail,
            source = "github",
            repo = "test/walls",
            category = "test",
            colors = listOf("#000000"),
            brightness = 50,
            contrast = 50,
            resolution = "1920x1080",
            attribution = null,
            embedding = embedding,
            e = e,
            eMin = eMin,
            eMax = eMax
        )
    }
}
