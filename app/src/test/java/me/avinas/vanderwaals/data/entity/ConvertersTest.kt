package me.avinas.vanderwaals.data.entity

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [Converters] type converter functions.
 * 
 * Tests comprehensive scenarios including:
 * - Normal conversion cases
 * - Null handling
 * - Empty collections
 * - Large data sets (embedding vectors)
 * - Round-trip conversion (serialize → deserialize)
 * - Edge cases and error handling
 */
class ConvertersTest {

    private lateinit var converters: Converters
    private lateinit var gson: Gson

    @Before
    fun setup() {
        gson = Gson()
        converters = Converters(gson)
    }

    // ========== List<String> Conversion Tests ==========

    @Test
    fun `fromStringList converts normal list to JSON string`() {
        // Given
        val list = listOf("id1", "id2", "id3")

        // When
        val result = converters.fromStringList(list)

        // Then
        assertEquals("[\"id1\",\"id2\",\"id3\"]", result)
    }

    @Test
    fun `fromStringList converts empty list to empty JSON array`() {
        // Given
        val list = emptyList<String>()

        // When
        val result = converters.fromStringList(list)

        // Then
        assertEquals("[]", result)
    }

    @Test
    fun `fromStringList converts null to empty JSON array`() {
        // Given
        val list: List<String>? = null

        // When
        val result = converters.fromStringList(list)

        // Then
        assertEquals("[]", result)
    }

    @Test
    fun `fromStringList handles list with special characters`() {
        // Given
        val list = listOf("#282828", "#cc241d", "color/blue")

        // When
        val result = converters.fromStringList(list)

        // Then
        assertTrue(result.contains("#282828"))
        assertTrue(result.contains("#cc241d"))
        assertTrue(result.contains("color/blue"))
    }

    @Test
    fun `toStringList converts JSON string to list`() {
        // Given
        val json = "[\"id1\",\"id2\",\"id3\"]"

        // When
        val result = converters.toStringList(json)

        // Then
        assertEquals(3, result.size)
        assertEquals("id1", result[0])
        assertEquals("id2", result[1])
        assertEquals("id3", result[2])
    }

    @Test
    fun `toStringList converts empty JSON array to empty list`() {
        // Given
        val json = "[]"

        // When
        val result = converters.toStringList(json)

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `toStringList handles null input gracefully`() {
        // Given
        val json: String? = null

        // When
        val result = converters.toStringList(json)

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `toStringList handles empty string gracefully`() {
        // Given
        val json = ""

        // When
        val result = converters.toStringList(json)

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `toStringList handles invalid JSON gracefully`() {
        // Given
        val json = "not valid json"

        // When
        val result = converters.toStringList(json)

        // Then
        assertTrue(result.isEmpty()) // Graceful degradation
    }

    @Test
    fun `round trip conversion for string lists preserves data`() {
        // Given
        val original = listOf("wallpaper1", "wallpaper2", "wallpaper3")

        // When
        val json = converters.fromStringList(original)
        val restored = converters.toStringList(json)

        // Then
        assertEquals(original, restored)
    }

    @Test
    fun `string list conversion handles large lists`() {
        // Given
        val largeList = (1..1000).map { "wallpaper_$it" }

        // When
        val json = converters.fromStringList(largeList)
        val restored = converters.toStringList(json)

        // Then
        assertEquals(largeList.size, restored.size)
        assertEquals(largeList.first(), restored.first())
        assertEquals(largeList.last(), restored.last())
    }

    // ========== FloatArray Conversion Tests ==========

    @Test
    fun `fromFloatArray converts normal array to JSON string`() {
        // Given
        val array = floatArrayOf(0.1f, 0.2f, 0.3f)

        // When
        val result = converters.fromFloatArray(array)

        // Then
        assertTrue(result.contains("0.1"))
        assertTrue(result.contains("0.2"))
        assertTrue(result.contains("0.3"))
    }

    @Test
    fun `fromFloatArray converts empty array to empty JSON array`() {
        // Given
        val array = floatArrayOf()

        // When
        val result = converters.fromFloatArray(array)

        // Then
        assertEquals("[]", result)
    }

    @Test
    fun `fromFloatArray converts null to empty JSON array`() {
        // Given
        val array: FloatArray? = null

        // When
        val result = converters.fromFloatArray(array)

        // Then
        assertEquals("[]", result)
    }

    @Test
    fun `fromFloatArray handles negative values`() {
        // Given
        val array = floatArrayOf(-0.5f, 0.0f, 0.5f)

        // When
        val result = converters.fromFloatArray(array)

        // Then
        assertTrue(result.contains("-0.5"))
        assertTrue(result.contains("0.0"))
        assertTrue(result.contains("0.5"))
    }

    @Test
    fun `toFloatArray converts JSON string to float array`() {
        // Given
        val json = "[0.1,0.2,0.3]"

        // When
        val result = converters.toFloatArray(json)

        // Then
        assertEquals(3, result.size)
        assertEquals(0.1f, result[0], 0.001f)
        assertEquals(0.2f, result[1], 0.001f)
        assertEquals(0.3f, result[2], 0.001f)
    }

    @Test
    fun `toFloatArray converts empty JSON array to empty array`() {
        // Given
        val json = "[]"

        // When
        val result = converters.toFloatArray(json)

        // Then
        assertEquals(0, result.size)
    }

    @Test
    fun `toFloatArray handles null input gracefully`() {
        // Given
        val json: String? = null

        // When
        val result = converters.toFloatArray(json)

        // Then
        assertEquals(0, result.size)
    }

    @Test
    fun `toFloatArray handles empty string gracefully`() {
        // Given
        val json = ""

        // When
        val result = converters.toFloatArray(json)

        // Then
        assertEquals(0, result.size)
    }

    @Test
    fun `toFloatArray handles invalid JSON gracefully`() {
        // Given
        val json = "not valid json"

        // When
        val result = converters.toFloatArray(json)

        // Then
        assertEquals(0, result.size) // Graceful degradation
    }

    @Test
    fun `round trip conversion for float arrays preserves data`() {
        // Given
        val original = floatArrayOf(0.123f, 0.456f, 0.789f)

        // When
        val json = converters.fromFloatArray(original)
        val restored = converters.toFloatArray(json)

        // Then
        assertEquals(original.size, restored.size)
        assertArrayEquals(original, restored, 0.0001f)
    }

    @Test
    fun `float array conversion handles large embedding vectors`() {
        // Given - MobileNetV3 embedding vector (576 dimensions)
        val embedding = FloatArray(576) { it * 0.001f }

        // When
        val json = converters.fromFloatArray(embedding)
        val restored = converters.toFloatArray(json)

        // Then
        assertEquals(576, restored.size)
        assertEquals(embedding[0], restored[0], 0.00001f)
        assertEquals(embedding[575], restored[575], 0.00001f)
    }

    @Test
    fun `float array conversion preserves precision`() {
        // Given
        val array = floatArrayOf(
            0.123456789f,
            -0.987654321f,
            0.0f,
            Float.MIN_VALUE,
            1.0f
        )

        // When
        val json = converters.fromFloatArray(array)
        val restored = converters.toFloatArray(json)

        // Then
        assertEquals(array.size, restored.size)
        for (i in array.indices) {
            assertEquals(array[i], restored[i], 0.00001f)
        }
    }

    @Test
    fun `converters handle mixed type conversions correctly`() {
        // Given
        val stringList = listOf("id1", "id2")
        val floatArray = floatArrayOf(0.1f, 0.2f)

        // When - Convert both types
        val stringJson = converters.fromStringList(stringList)
        val floatJson = converters.fromFloatArray(floatArray)
        
        val restoredStrings = converters.toStringList(stringJson)
        val restoredFloats = converters.toFloatArray(floatJson)

        // Then - Both conversions should work independently
        assertEquals(stringList, restoredStrings)
        assertArrayEquals(floatArray, restoredFloats, 0.0001f)
    }

    @Test
    fun `converters are thread-safe for concurrent access`() {
        // Given
        val testData = listOf("test1", "test2", "test3")
        val threads = mutableListOf<Thread>()
        val results = mutableListOf<List<String>>()

        // When - Multiple threads use the same converter instance
        repeat(10) {
            val thread = Thread {
                val json = converters.fromStringList(testData)
                val restored = converters.toStringList(json)
                synchronized(results) {
                    results.add(restored)
                }
            }
            threads.add(thread)
            thread.start()
        }

        // Wait for all threads to complete
        threads.forEach { it.join() }

        // Then - All results should be correct
        assertEquals(10, results.size)
        results.forEach { result ->
            assertEquals(testData, result)
        }
    }

    // ========== Helper Functions ==========

    /**
     * Helper function to compare float arrays with tolerance.
     */
    private fun assertArrayEquals(expected: FloatArray, actual: FloatArray, delta: Float) {
        assertEquals("Array sizes differ", expected.size, actual.size)
        for (i in expected.indices) {
            assertEquals(
                "Values differ at index $i",
                expected[i],
                actual[i],
                delta
            )
        }
    }
}
