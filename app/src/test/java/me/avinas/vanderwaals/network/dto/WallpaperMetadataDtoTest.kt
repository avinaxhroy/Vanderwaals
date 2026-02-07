package me.avinas.vanderwaals.network.dto

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for WallpaperMetadataDto embedding handling.
 * 
 * Tests cover:
 * - Dynamic embedding dimension detection (576D legacy, 1280D current)
 * - Empty embedding fallback behavior
 * - v1 (full), v2 (quantized 576D), v3 (quantized 1280D) format handling
 */
class WallpaperMetadataDtoTest {

    // ========== Embedding Dimension Tests ==========

    @Test
    fun `getEmbeddingArray returns empty when no embedding data available`() {
        // Given - DTO with no embedding data
        val dto = createDto(
            embedding = null,
            e = null,
            eMin = null,
            eMax = null
        )

        // When
        val result = dto.getEmbeddingArray()

        // Then - Should return empty, not 576D default
        assertTrue(result.isEmpty())
        assertEquals(0, result.size)
    }

    @Test
    fun `getEmbeddingArray returns full embedding when available (v1 format)`() {
        // Given - DTO with full 576D embedding (v1 legacy format)
        val fullEmbedding = (0 until 576).map { it * 0.001f }
        val dto = createDto(
            embedding = fullEmbedding,
            e = null,
            eMin = null,
            eMax = null
        )

        // When
        val result = dto.getEmbeddingArray()

        // Then
        assertEquals(576, result.size)
        assertEquals(0.0f, result[0], 0.0001f)
        assertEquals(0.001f, result[1], 0.0001f)
    }

    @Test
    fun `getEmbeddingArray returns full 1280D embedding when available`() {
        // Given - DTO with full 1280D embedding (v3 format)
        val fullEmbedding = (0 until 1280).map { it * 0.0001f }
        val dto = createDto(
            embedding = fullEmbedding,
            e = null,
            eMin = null,
            eMax = null
        )

        // When
        val result = dto.getEmbeddingArray()

        // Then
        assertEquals(1280, result.size)
        assertEquals(0.0f, result[0], 0.0001f)
        assertEquals(0.1279f, result[1279], 0.0001f)
    }

    // NOTE: Quantized embedding tests (dequantizeEmbedding) require Android instrumented tests
    // because android.util.Base64 is not available in JVM unit tests.
    // The quantized format is tested via integration tests instead.

    @Test
    fun `getEmbeddingArray returns full embedding when quantized fields are incomplete`() {
        // Given - DTO with e but missing eMin/eMax (incomplete quantized)
        val fullEmbedding = (0 until 576).map { 0.5f }
        val dto = createDto(
            embedding = fullEmbedding,
            e = "someBase64Data",
            eMin = null,  // Missing
            eMax = null   // Missing
        )

        // When
        val result = dto.getEmbeddingArray()

        // Then - Should fall back to full embedding since quantized is incomplete
        assertEquals(576, result.size)
    }

    @Test
    fun `toEntity handles empty embedding gracefully`() {
        // Given - DTO with no embedding
        val dto = createDto(
            embedding = null,
            e = null,
            eMin = null,
            eMax = null
        )

        // When
        val entity = dto.toEntity()

        // Then
        assertTrue(entity.embedding.isEmpty())
    }

    @Test
    fun `toEntity preserves all metadata fields`() {
        // Given
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

        // When
        val entity = dto.toEntity()

        // Then
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
        // Given
        val dto = createDto(
            thumbnail = null,
            url = "https://example.com/wallpaper.jpg"
        )

        // When
        val entity = dto.toEntity()

        // Then
        assertEquals("https://example.com/wallpaper.jpg", entity.thumbnailUrl)
    }

    // ========== Dimension Detection Helper Tests ==========

    @Test
    fun `embedding dimension correctly detected from full embedding`() {
        // Given
        val dto576 = createDto(embedding = (0 until 576).map { 0.0f })
        val dto1280 = createDto(embedding = (0 until 1280).map { 0.0f })
        val dtoEmpty = createDto(embedding = null)

        // When
        val embed576 = dto576.getEmbeddingArray()
        val embed1280 = dto1280.getEmbeddingArray()
        val embedEmpty = dtoEmpty.getEmbeddingArray()

        // Then
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
