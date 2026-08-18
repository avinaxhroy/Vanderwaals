package me.avinas.vanderwaals.data.entity

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

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
        val list = listOf("id1", "id2", "id3")

        val result = converters.fromStringList(list)

        assertEquals("[\"id1\",\"id2\",\"id3\"]", result)
    }

    @Test
    fun `fromStringList converts empty list to empty JSON array`() {
        val list = emptyList<String>()

        val result = converters.fromStringList(list)

        assertEquals("[]", result)
    }

    @Test
    fun `fromStringList converts null to empty JSON array`() {
        val list: List<String>? = null

        val result = converters.fromStringList(list)

        assertEquals("[]", result)
    }

    @Test
    fun `fromStringList handles list with special characters`() {
        val list = listOf("#282828", "#cc241d", "color/blue")

        val result = converters.fromStringList(list)

        assertTrue(result.contains("#282828"))
        assertTrue(result.contains("#cc241d"))
        assertTrue(result.contains("color/blue"))
    }

    @Test
    fun `toStringList converts JSON string to list`() {
        val json = "[\"id1\",\"id2\",\"id3\"]"

        val result = converters.toStringList(json)

        assertEquals(3, result.size)
        assertEquals("id1", result[0])
        assertEquals("id2", result[1])
        assertEquals("id3", result[2])
    }

    @Test
    fun `toStringList converts empty JSON array to empty list`() {
        val json = "[]"

        val result = converters.toStringList(json)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `toStringList handles null input gracefully`() {
        val json: String? = null

        val result = converters.toStringList(json)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `toStringList handles empty string gracefully`() {
        val json = ""

        val result = converters.toStringList(json)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `toStringList handles invalid JSON gracefully`() {
        val json = "not valid json"

        val result = converters.toStringList(json)

        assertTrue(result.isEmpty()) // Graceful degradation
    }

    @Test
    fun `round trip conversion for string lists preserves data`() {
        val original = listOf("wallpaper1", "wallpaper2", "wallpaper3")

        val json = converters.fromStringList(original)
        val restored = converters.toStringList(json)

        assertEquals(original, restored)
    }

    @Test
    fun `string list conversion handles large lists`() {
        val largeList = (1..1000).map { "wallpaper_$it" }

        val json = converters.fromStringList(largeList)
        val restored = converters.toStringList(json)

        assertEquals(largeList.size, restored.size)
        assertEquals(largeList.first(), restored.first())
        assertEquals(largeList.last(), restored.last())
    }

    // ========== FloatArray Conversion Tests ==========

    @Test
    fun `fromFloatArray converts normal array to JSON string`() {
        val array = floatArrayOf(0.1f, 0.2f, 0.3f)

        val result = converters.fromFloatArray(array)

        assertTrue(result.contains("0.1"))
        assertTrue(result.contains("0.2"))
        assertTrue(result.contains("0.3"))
    }

    @Test
    fun `fromFloatArray converts empty array to empty JSON array`() {
        val array = floatArrayOf()

        val result = converters.fromFloatArray(array)

        assertEquals("[]", result)
    }

    @Test
    fun `fromFloatArray converts null to empty JSON array`() {
        val array: FloatArray? = null

        val result = converters.fromFloatArray(array)

        assertEquals("[]", result)
    }

    @Test
    fun `fromFloatArray handles negative values`() {
        val array = floatArrayOf(-0.5f, 0.0f, 0.5f)

        val result = converters.fromFloatArray(array)

        assertTrue(result.contains("-0.5"))
        assertTrue(result.contains("0.0"))
        assertTrue(result.contains("0.5"))
    }

    @Test
    fun `toFloatArray converts JSON string to float array`() {
        val json = "[0.1,0.2,0.3]"

        val result = converters.toFloatArray(json)

        assertEquals(3, result.size)
        assertEquals(0.1f, result[0], 0.001f)
        assertEquals(0.2f, result[1], 0.001f)
        assertEquals(0.3f, result[2], 0.001f)
    }

    @Test
    fun `toFloatArray converts empty JSON array to empty array`() {
        val json = "[]"

        val result = converters.toFloatArray(json)

        assertEquals(0, result.size)
    }

    @Test
    fun `toFloatArray handles null input gracefully`() {
        val json: String? = null

        val result = converters.toFloatArray(json)

        assertEquals(0, result.size)
    }

    @Test
    fun `toFloatArray handles empty string gracefully`() {
        val json = ""

        val result = converters.toFloatArray(json)

        assertEquals(0, result.size)
    }

    @Test
    fun `toFloatArray handles invalid JSON gracefully`() {
        val json = "not valid json"

        val result = converters.toFloatArray(json)

        assertEquals(0, result.size) // Graceful degradation
    }

    @Test
    fun `round trip conversion for float arrays preserves data`() {
        val original = floatArrayOf(0.123f, 0.456f, 0.789f)

        val json = converters.fromFloatArray(original)
        val restored = converters.toFloatArray(json)

        assertEquals(original.size, restored.size)
        assertArrayEquals(original, restored, 0.0001f)
    }

    @Test
    fun `float array conversion handles large embedding vectors`() {
        // MobileNetV4 embedding vector (1280 dimensions)
        val embedding = FloatArray(1280) { it * 0.001f }

        val json = converters.fromFloatArray(embedding)
        val restored = converters.toFloatArray(json)

        assertEquals(1280, restored.size)
        assertEquals(embedding[0], restored[0], 0.00001f)
        assertEquals(embedding[1279], restored[1279], 0.00001f)
    }

    @Test
    fun `float array conversion preserves precision`() {
        val array = floatArrayOf(
            0.123456789f,
            -0.987654321f,
            0.0f,
            Float.MIN_VALUE,
            1.0f
        )

        val json = converters.fromFloatArray(array)
        val restored = converters.toFloatArray(json)

        assertEquals(array.size, restored.size)
        for (i in array.indices) {
            assertEquals(array[i], restored[i], 0.00001f)
        }
    }

    // ========== Fast-Path Float Codec Tests (embeddings) ==========

    @Test
    fun `toFloatArray parses exponent notation like Float-dot-parseFloat`() {
        val json = "[1.5e-3,-2.5E2,1e0,3E+2]"
        val restored = converters.toFloatArray(json)

        assertEquals(4, restored.size)
        assertEquals("1.5e-3".toFloat(), restored[0], 0f)
        assertEquals("-2.5E2".toFloat(), restored[1], 0f)
        assertEquals("1e0".toFloat(), restored[2], 0f)
        assertEquals("3E+2".toFloat(), restored[3], 0f)
    }

    @Test
    fun `toFloatArray tolerates whitespace between tokens`() {
        val restored = converters.toFloatArray(" [ 0.5 , 1.25 , -3 ]\n")

        assertEquals(3, restored.size)
        assertEquals(0.5f, restored[0], 0f)
        assertEquals(1.25f, restored[1], 0f)
        assertEquals(-3f, restored[2], 0f)
    }

    @Test
    fun `toFloatArray parses single element array`() {
        val restored = converters.toFloatArray("[42.0]")

        assertEquals(1, restored.size)
        assertEquals(42.0f, restored[0], 0f)
    }

    @Test
    fun `toFloatArray saturates extreme exponents like Float-dot-parseFloat`() {
        val restored = converters.toFloatArray("[1e-400,1e40,-1e-46]")

        assertEquals(0.0f, restored[0], 0f)                       // underflow to zero
        assertTrue(restored[1].isInfinite())                       // overflow to infinity
        assertEquals("-1e-46".toFloat(), restored[2], 0f)  // 0.0 with sign preserved
    }

    @Test
    fun `float round trip is bit-exact for the full float range`() {
        // Float.toString → parse must return the identical float for every
        // finite value (shortest round-trip guarantee), including
        // subnormals and values near Float.MIN_VALUE / MAX_VALUE.
        val random = java.util.Random(42)
        val values = ArrayList<Float>(5000)
        while (values.size < 5000) {
            val f = Float.fromBits(random.nextInt())
            if (f.isFinite()) values.add(f)
        }
        values.add(Float.MIN_VALUE)
        values.add(-Float.MIN_VALUE)
        values.add(Float.MAX_VALUE)
        values.add(-Float.MAX_VALUE)
        values.add(0.023456789f) // typical L2-normalised embedding magnitude

        val original = values.toFloatArray()
        val restored = converters.toFloatArray(converters.fromFloatArray(original))

        assertEquals(original.size, restored.size)
        for (i in original.indices) {
            assertEquals("bit-exact round trip failed at $i (${original[i]})", original[i], restored[i], 0f)
            assertEquals("sign of zero flipped at $i", original[i].toRawBits(), restored[i].toRawBits())
        }
    }

    @Test
    fun `toFloatArray falls back to Gson for non-numeric elements`() {
        // null elements previously degraded to an empty array via the Gson
        // path's NPE catch; the fast path must not change that.
        assertEquals(0, converters.toFloatArray("[null]").size)
        assertEquals(0, converters.toFloatArray("[1,\"a\"]").size)
    }

    @Test
    fun `toFloatArray rejects junk after array via fallback to empty`() {
        // Gson throws on trailing garbage → legacy graceful degradation.
        assertEquals(0, converters.toFloatArray("[1] x").size)
    }

    @Test
    fun `fromFloatArray output is valid JSON for Gson consumers`() {
        // The fast writer's output must be readable by a plain Gson reader,
        // since legacy code paths may still parse these strings.
        val array = floatArrayOf(0.5f, -0.25f, 1e-8f, 0.0f)
        val json = converters.fromFloatArray(array)

        @Suppress("UNCHECKED_CAST")
        val parsed = gson.fromJson(json, List::class.java) as List<Double>
        assertEquals(array.size, parsed.size)
        assertEquals(0.5, parsed[0], 1e-7)
        assertEquals(-0.25, parsed[1], 1e-7)
    }

    @Test
    fun `converters handle mixed type conversions correctly`() {
        val stringList = listOf("id1", "id2")
        val floatArray = floatArrayOf(0.1f, 0.2f)

        val stringJson = converters.fromStringList(stringList)
        val floatJson = converters.fromFloatArray(floatArray)

        val restoredStrings = converters.toStringList(stringJson)
        val restoredFloats = converters.toFloatArray(floatJson)

        assertEquals(stringList, restoredStrings)
        assertArrayEquals(floatArray, restoredFloats, 0.0001f)
    }

    // ========== BLOB Float Codec Tests (v13+ storage format) ==========

    @Test
    fun `float array to blob and back is bit-exact for the full float range`() {
        val random = java.util.Random(1234)
        val values = ArrayList<Float>(5000)
        while (values.size < 5000) {
            values.add(Float.fromBits(random.nextInt())) // non-finite values survive bit-exactly
        }
        values.add(Float.MIN_VALUE)
        values.add(-Float.MIN_VALUE)
        values.add(Float.MAX_VALUE)
        values.add(-Float.MAX_VALUE)
        values.add(0.0f)
        values.add(-0.0f)
        values.add(Float.NaN)
        values.add(Float.POSITIVE_INFINITY)
        values.add(0.023456789f)

        val original = values.toFloatArray()
        val restored = converters.blobToFloatArray(converters.floatArrayToBlob(original))

        assertEquals(original.size, restored.size)
        for (i in original.indices) {
            assertEquals(
                "bit-exact BLOB round trip failed at $i (${original[i]})",
                original[i].toRawBits(),
                restored[i].toRawBits()
            )
        }
    }

    @Test
    fun `float array to blob uses compact little-endian float32 layout`() {
        val blob = converters.floatArrayToBlob(floatArrayOf(1.0f))
        // IEEE-754 float32 1.0 = 0x3F800000, little-endian.
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x80.toByte(), 0x3F), blob)

        val embedding = FloatArray(1280) { it * 0.001f }
        assertEquals(4 * 1280, converters.floatArrayToBlob(embedding).size)
    }

    @Test
    fun `blob converter handles null, empty and corrupt inputs`() {
        assertEquals(0, converters.floatArrayToBlob(null).size)
        assertEquals(0, converters.floatArrayToBlob(floatArrayOf()).size)
        assertEquals(0, converters.blobToFloatArray(null).size)
        assertEquals(0, converters.blobToFloatArray(ByteArray(0)).size)
        // Corrupt length (not a multiple of 4) degrades to empty, matching
        // the legacy JSON converter's graceful degradation.
        val corrupt = converters.floatArrayToBlob(floatArrayOf(1f)).copyOf(3)
        assertEquals(0, converters.blobToFloatArray(corrupt).size)
    }

    @Test
    fun `legacy json codec round trips through the migration path`() {
        // MIGRATION_12_13: JSON (v12 rows) → FloatArray → BLOB (v13 rows).
        val embedding = FloatArray(1280) { it * 0.0001f - 0.064f }
        val legacyJson = converters.fromFloatArray(embedding) // what v12 rows contain

        val migrated = converters.blobToFloatArray(
            converters.floatArrayToBlob(converters.toFloatArray(legacyJson))
        )
        assertArrayEquals(embedding, migrated, 0f)
    }

    @Test
    fun `converters are thread-safe for concurrent access`() {
        val testData = listOf("test1", "test2", "test3")
        val threads = mutableListOf<Thread>()
        val results = mutableListOf<List<String>>()

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

        threads.forEach { it.join() }

        assertEquals(10, results.size)
        results.forEach { result ->
            assertEquals(testData, result)
        }
    }

    // ========== Helper Functions ==========

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
