package me.avinas.vanderwaals.data.entity

import androidx.room.ProvidedTypeConverter
import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Type converters for Room database to handle complex types.
 *
 * Provides conversion between Room-compatible primitive types and complex Kotlin types:
 * - List<String> ↔ JSON String (for wallpaper IDs, color palettes)
 * - FloatArray ↔ JSON String (for embedding vectors)
 *
 * Uses Gson for efficient JSON serialization/deserialization.
 * Handles null values gracefully to prevent crashes.
 *
 * Annotated with @ProvidedTypeConverter to allow dependency injection of Gson instance.
 * This enables using a custom-configured Gson instance from the DI container.
 *
 * Usage:
 * ```kotlin
 * @Database(
 *     entities = [...],
 *     version = 1
 * )
 * @TypeConverters(Converters::class)
 * abstract class VanderwaalsDatabase : RoomDatabase() { ... }
 * ```
 *
 * @property gson JSON serializer/deserializer instance
 */
@ProvidedTypeConverter
class Converters(private val gson: Gson) {

    /**
     * Converts a List<String> to a JSON string for storage in Room.
     *
     * Used for storing:
     * - Color palettes (list of hex codes)
     * - Liked/disliked wallpaper IDs
     * - Category lists
     *
     * @param value List of strings to convert, can be null
     * @return JSON string representation, or "[]" for null input
     *
     * Example:
     * ```kotlin
     * listOf("id1", "id2", "id3") -> "[\"id1\",\"id2\",\"id3\"]"
     * null -> "[]"
     * emptyList() -> "[]"
     * ```
     */
    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        return if (value == null) {
            "[]"
        } else {
            gson.toJson(value)
        }
    }

    /**
     * Converts a JSON string back to a List<String>.
     *
     * Handles edge cases:
     * - Null input → empty list
     * - Empty string → empty list
     * - Invalid JSON → empty list (graceful degradation)
     *
     * @param value JSON string from database
     * @return List of strings, never null (returns empty list on error)
     *
     * Example:
     * ```kotlin
     * "[\"id1\",\"id2\",\"id3\"]" -> listOf("id1", "id2", "id3")
     * "[]" -> emptyList()
     * null -> emptyList()
     * "" -> emptyList()
     * ```
     */
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
     * Converts a FloatArray to a JSON string for storage in Room.
     *
     * Used for storing:
     * - MobileNetV3 embedding vectors (576 floats)
     * - User preference vectors (576 floats)
     *
     * FloatArray is more memory-efficient than List<Float> for large vectors.
     *
     * @param value FloatArray to convert, can be null
     * @return JSON string representation, or "[]" for null input
     *
     * Example:
     * ```kotlin
     * floatArrayOf(0.1f, 0.2f, 0.3f) -> "[0.1,0.2,0.3]"
     * null -> "[]"
     * floatArrayOf() -> "[]"
     * ```
     */
    @TypeConverter
    fun fromFloatArray(value: FloatArray?): String {
        return if (value == null) {
            "[]"
        } else {
            // Convert FloatArray to List for Gson serialization
            gson.toJson(value.toList())
        }
    }

    /**
     * Converts a JSON string back to a FloatArray.
     *
     * Handles edge cases:
     * - Null input → empty array
     * - Empty string → empty array
     * - Invalid JSON → empty array (graceful degradation)
     *
     * @param value JSON string from database
     * @return FloatArray, never null (returns empty array on error)
     *
     * Example:
     * ```kotlin
     * "[0.1,0.2,0.3]" -> floatArrayOf(0.1f, 0.2f, 0.3f)
     * "[]" -> floatArrayOf()
     * null -> floatArrayOf()
     * "" -> floatArrayOf()
     * ```
     */
    @TypeConverter
    fun toFloatArray(value: String?): FloatArray {
        if (value.isNullOrEmpty() || value == "[]") {
            return floatArrayOf()
        }
        return try {
            val type = object : TypeToken<List<Float>>() {}.type
            val list: List<Float> = gson.fromJson(value, type) ?: emptyList()
            list.toFloatArray()
        } catch (e: Exception) {
            // Graceful degradation for corrupted data
            floatArrayOf()
        }
    }
    
    /**
     * Converts a FeedbackContext to a JSON string for storage in Room.
     * 
     * Used for storing contextual information when user provides feedback:
     * - Time of day (0-23 hours)
     * - Day of week (1-7)
     * - Battery level (0-100%)
     * - Screen brightness (0-255)
     * 
     * @param value FeedbackContext to convert, can be null
     * @return JSON string representation, or null for null input
     * 
     * Example:
     * ```kotlin
     * FeedbackContext(14, 3, 75, 180) -> "{\"timeOfDay\":14,\"dayOfWeek\":3,\"batteryLevel\":75,\"screenBrightness\":180}"
     * null -> null
     * ```
     */
    @TypeConverter
    fun fromFeedbackContext(value: FeedbackContext?): String? {
        return if (value == null) {
            null
        } else {
            gson.toJson(value)
        }
    }
    
    /**
     * Converts a JSON string back to a FeedbackContext.
     * 
     * Handles edge cases:
     * - Null input → null
     * - Empty string → null
     * - Invalid JSON → null (graceful degradation)
     * 
     * @param value JSON string from database
     * @return FeedbackContext or null if unavailable
     * 
     * Example:
     * ```kotlin
     * "{\"timeOfDay\":14,\"dayOfWeek\":3,\"batteryLevel\":75,\"screenBrightness\":180}" 
     *   -> FeedbackContext(14, 3, 75, 180)
     * null -> null
     * "" -> null
     * ```
     */
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
}
