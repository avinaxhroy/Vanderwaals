package me.avinas.vanderwaals.data.entity

import androidx.room.ProvidedTypeConverter
import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.nio.ByteBuffer

/**
 * Room type converters. Annotated @ProvidedTypeConverter so a custom-configured
 * Gson instance from the DI container is injected.
 */
@ProvidedTypeConverter
class Converters(private val gson: Gson) {

    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        return if (value == null) {
            "[]"
        } else {
            gson.toJson(value)
        }
    }

    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value.isNullOrEmpty() || value == "[]") {
            return emptyList()
        }
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(value, type) ?: emptyList()
        } catch (e: Exception) {
            // Graceful degradation for corrupted data
            emptyList()
        }
    }

    /**
     * Converts a FloatArray to a little-endian float32 BLOB for storage in
     * Room.
     *
     * Used for storing:
     * - MobileNetV4 embedding vectors (1280 floats)
     * - User preference vectors (1280 floats)
     *
     * Performance: embeddings are read back on every wallpaper selection
     * (the full catalog is scored).  A raw float32 BLOB deserialises with
     * zero parsing — Room wraps the bytes and one bulk buffer read fills
     * the FloatArray — where the previous JSON TEXT column had to parse
     * ~4.7M number literals per catalog load.  Storage also drops from
     * ~14 KB of JSON to 5,120 bytes per embedding.  Values round-trip
     * bit-exactly (raw IEEE-754 bits, NaN payloads included).
     *
     * Byte order is pinned to little-endian (not native) so database files
     * stay portable across device architectures and backups.
     *
     * @param value FloatArray to convert, can be null
     * @return float32 BLOB, or an empty BLOB for null/empty input
     */
    @TypeConverter
    fun floatArrayToBlob(value: FloatArray?): ByteArray {
        if (value == null || value.isEmpty()) {
            return ByteArray(0)
        }
        return ByteBuffer.allocate(4 * value.size)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .apply { for (element in value) putFloat(element) }
            .array()
    }

    /**
     * Converts a float32 BLOB back to a FloatArray.
     *
     * Handles edge cases:
     * - Null input → empty array
     * - Empty BLOB → empty array
     * - Corrupt length (not a multiple of 4) → empty array (graceful
     *   degradation, matching the legacy JSON converter's behaviour)
     *
     * @param value BLOB from database
     * @return FloatArray, never null
     */
    @TypeConverter
    fun blobToFloatArray(value: ByteArray?): FloatArray {
        if (value == null || value.isEmpty() || value.size % 4 != 0) {
            return FloatArray(0)
        }
        val result = FloatArray(value.size / 4)
        ByteBuffer.wrap(value)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .asFloatBuffer()
            .get(result)
        return result
    }

    // Legacy JSON float codec. Not registered with Room since the v13 BLOB
    // migration; MIGRATION_12_13 uses them to translate existing TEXT-JSON rows
    // into BLOBs, and they define the historical on-disk format for external
    // tooling that reads pre-v13 databases.

    /**
     * Converts a FloatArray to its legacy JSON string form (pre-v13
     * storage format).  See [floatArrayToBlob] for the current format.
     */
    internal fun fromFloatArray(value: FloatArray?): String {
        if (value == null || value.isEmpty()) {
            return "[]"
        }
        val builder = StringBuilder(value.size * 10)
        builder.append('[')
        for (i in value.indices) {
            if (i > 0) builder.append(',')
            val element = value[i]
            if (!element.isFinite()) {
                // Preserve Gson's handling of NaN/Infinity byte-for-byte.
                return gson.toJson(value.toList())
            }
            builder.append(element)
        }
        builder.append(']')
        return builder.toString()
    }

    /**
     * Converts a legacy (pre-v13) JSON string back to a FloatArray.
     *
     * Handles edge cases:
     * - Null input → empty array
     * - Empty string → empty array
     * - Invalid JSON → empty array (graceful degradation)
     * - Lenient/corrupt input the fast parser rejects → Gson fallback with
     *   the exact legacy behaviour
     *
     * @param value JSON string from a pre-v13 database
     * @return FloatArray, never null (returns empty array on error)
     */
    internal fun toFloatArray(value: String?): FloatArray {
        if (value.isNullOrEmpty() || value == "[]") {
            return FloatArray(0)
        }
        parseCompactFloatArray(value)?.let { return it }
        return try {
            val type = object : TypeToken<List<Float>>() {}.type
            val list: List<Float> = gson.fromJson(value, type) ?: emptyList()
            list.toFloatArray()
        } catch (e: Exception) {
            // Graceful degradation for corrupted data
            floatArrayOf()
        }
    }
    
    @TypeConverter
    fun fromFeedbackContext(value: FeedbackContext?): String? {
        return if (value == null) {
            null
        } else {
            gson.toJson(value)
        }
    }
    
    @TypeConverter
    fun toFeedbackContext(value: String?): FeedbackContext? {
        if (value.isNullOrEmpty()) {
            return null
        }
        return try {
            gson.fromJson(value, FeedbackContext::class.java)
        } catch (e: Exception) {
            // Graceful degradation for corrupted data
            null
        }
    }

    /** Serializes mood/style affinity maps for [UserPreferences]. */
    @TypeConverter
    fun fromStringFloatMap(value: Map<String, Float>?): String {
        return if (value == null) {
            "{}"
        } else {
            gson.toJson(value)
        }
    }

    @TypeConverter
    fun toStringFloatMap(value: String?): Map<String, Float> {
        if (value.isNullOrEmpty() || value == "{}") {
            return emptyMap()
        }
        return try {
            val type = object : TypeToken<Map<String, Float>>() {}.type
            gson.fromJson(value, type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }
}

// Compact float-array JSON codec. Room stores every embedding as a JSON
// number array, and full-catalog reads deserialise thousands of them per
// selection.  These helpers parse/emit the compact shape ([0.1,-2.5e-3,…])
// without boxing or per-element strings; anything else is left to Gson via
// the converters' fallback.

/**
 * Parses a JSON array of decimal numbers directly into a FloatArray.
 *
 * Two passes over the input: the first validates the exact grammar and
 * counts elements (so the array is allocated once, at the right size), the
 * second parses each number in place.  Accepts optional JSON whitespace
 * between tokens.  Returns null for any other shape (nested arrays, string
 * or null elements, trailing commas, junk after `]`, …) so the caller can
 * fall back to Gson and preserve legacy lenient parsing.
 */
private fun parseCompactFloatArray(json: String): FloatArray? {
    val n = json.length
    var i = skipWs(json, 0)
    if (i >= n || json[i] != '[') return null
    i = skipWs(json, i + 1)
    if (i < n && json[i] == ']') {
        return if (skipWs(json, i + 1) == n) FloatArray(0) else null
    }

    // Pass 1: validate and count.
    var count = 0
    var j = i
    while (true) {
        j = scanJsonNumber(json, j) ?: return null
        count++
        j = skipWs(json, j)
        if (j >= n) return null
        when (json[j]) {
            ',' -> j = skipWs(json, j + 1)
            ']' -> if (skipWs(json, j + 1) == n) break else return null
            else -> return null
        }
    }

    // Pass 2: parse values into the exactly-sized result.
    val result = FloatArray(count)
    var k = i
    for (index in 0 until count) {
        val end = scanJsonNumber(json, k)!!
        result[index] = parseDecimalToFloat(json, k, end)
        k = skipWs(json, end)
        if (index < count - 1) {
            k = skipWs(json, k + 1) // step over the ',' validated in pass 1
        }
    }
    return result
}

/**
 * Scans one JSON number starting at [from]; returns the index one past its
 * last character, or null when the text at [from] is not a decimal number
 * (`-?digits(.digits)?([eE][+-]?digits)?`).
 */
private fun scanJsonNumber(json: String, from: Int): Int? {
    val n = json.length
    var i = from
    if (i < n && json[i] == '-') i++
    var digits = 0
    while (i < n && json[i].isDigitAscii()) { i++; digits++ }
    if (i < n && json[i] == '.') {
        i++
        while (i < n && json[i].isDigitAscii()) { i++; digits++ }
    }
    if (digits == 0) return null
    if (i < n && (json[i] == 'e' || json[i] == 'E')) {
        i++
        if (i < n && (json[i] == '+' || json[i] == '-')) i++
        var expDigits = 0
        while (i < n && json[i].isDigitAscii()) { i++; expDigits++ }
        if (expDigits == 0) return null
    }
    return i
}

/**
 * Decimal → float for the range [from, until) validated by
 * [scanJsonNumber], matching `Float.parseFloat` on the same text.
 *
 * The significand (≤ 9 digits for Float.toString round-trips; exact in a
 * double up to 15+) is accumulated as a double and scaled by a correctly
 * rounded power of ten, then narrowed — the same decimal→double→float
 * strategy the JDK's own parser uses.
 */
private fun parseDecimalToFloat(s: String, from: Int, until: Int): Float {
    var i = from
    var negative = false
    if (s[i] == '-') { negative = true; i++ }

    var mantissa = 0.0
    var exponent = 0 // power of ten applied to `mantissa`
    var seenDot = false
    scan@ while (i < until) {
        val c = s[i]
        when {
            c.isDigitAscii() -> {
                mantissa = mantissa * 10.0 + (c - '0')
                if (seenDot) exponent--
            }
            c == '.' -> seenDot = true
            else -> { // 'e' / 'E' — scanJsonNumber guaranteed ≥1 exponent digit
                i++
                val expNegative = if (s[i] == '-') {
                    i++; true
                } else {
                    if (s[i] == '+') i++
                    false
                }
                var exp = 0
                while (i < until) {
                    if (exp < 10_000) exp = exp * 10 + (s[i] - '0') // saturate; ±Inf/0 either way
                    i++
                }
                exponent += if (expNegative) -exp else exp
                break@scan
            }
        }
        i++
    }

    // Scale into the table's [-45, 38] range; extreme exponents saturate to
    // Infinity / 0.0 exactly as Float.parseFloat does.
    var value = mantissa
    while (exponent > 38) { value *= 1e38; exponent -= 38; if (value.isInfinite()) break }
    while (exponent < -45 && value != 0.0) { value *= 1e-45; exponent += 45; if (value == 0.0) break }
    if (value.isFinite() && value != 0.0) {
        value *= POW10[exponent + 45]
    }

    val result = value.toFloat()
    return if (negative) -result else result
}

private fun skipWs(s: String, from: Int): Int {
    var i = from
    val n = s.length
    while (i < n) {
        when (s[i]) {
            ' ', '\t', '\n', '\r' -> i++
            else -> return i
        }
    }
    return i
}

private fun Char.isDigitAscii(): Boolean = this in '0'..'9'

/** Correctly rounded powers of ten for exponents −45…38, indexed by `exp + 45`. */
private val POW10 = doubleArrayOf(
    1e-45, 1e-44, 1e-43, 1e-42, 1e-41, 1e-40, 1e-39, 1e-38, 1e-37, 1e-36,
    1e-35, 1e-34, 1e-33, 1e-32, 1e-31, 1e-30, 1e-29, 1e-28, 1e-27, 1e-26,
    1e-25, 1e-24, 1e-23, 1e-22, 1e-21, 1e-20, 1e-19, 1e-18, 1e-17, 1e-16,
    1e-15, 1e-14, 1e-13, 1e-12, 1e-11, 1e-10, 1e-9, 1e-8, 1e-7, 1e-6,
    1e-5, 1e-4, 1e-3, 1e-2, 1e-1, 1e0, 1e1, 1e2, 1e3, 1e4,
    1e5, 1e6, 1e7, 1e8, 1e9, 1e10, 1e11, 1e12, 1e13, 1e14,
    1e15, 1e16, 1e17, 1e18, 1e19, 1e20, 1e21, 1e22, 1e23, 1e24,
    1e25, 1e26, 1e27, 1e28, 1e29, 1e30, 1e31, 1e32, 1e33, 1e34,
    1e35, 1e36, 1e37, 1e38
)
