package me.avinas.vanderwaals.core

import android.util.Patterns
import java.net.URL

/**
 * Centralized input validation utility for the Vanderwaals application.
 * 
 * Provides type-safe validation for all critical inputs including:
 * - Wallpaper identifiers and URLs
 * - Color values and numeric ranges
 * - Embedding dimensions
 * - Worker and service parameters
 * 
 * All validation methods return a sealed [ValidationResult] to enable
 * exhaustive when-expressions and type-safe error handling.
 * 
 * **Usage:**
 * ```kotlin
 * when (val result = InputValidator.validateWallpaperId(id)) {
 *     is ValidationResult.Valid -> {
 *         // Proceed with valid ID
 *         useWallpaper(result.value)
 *     }
 *     is ValidationResult.Invalid -> {
 *         // Handle error
 *         Log.e(TAG, "Invalid wallpaper ID: ${result.reason}")
 *         return Result.failure(IllegalArgumentException(result.reason))
 *     }
 * }
 * ```
 * 
 * @see ValidationResult
 */
object InputValidator {
    
    /**
     * Validates a wallpaper ID.
     * 
     * Requirements:
     * - Non-null and non-empty
     * - Contains only alphanumeric characters, hyphens, and underscores
     * - Length between 1 and 255 characters
     * 
     * @param id Wallpaper ID to validate
     * @return ValidationResult.Valid if valid, ValidationResult.Invalid otherwise
     */
    fun validateWallpaperId(id: String?): ValidationResult<String> {
        if (id.isNullOrBlank()) {
            return ValidationResult.Invalid("Wallpaper ID cannot be null or empty")
        }
        
        if (id.length > 255) {
            return ValidationResult.Invalid("Wallpaper ID exceeds maximum length of 255 characters")
        }
        
        // Allow alphanumeric, hyphens, underscores, and periods (for file extensions)
        val validPattern = Regex("^[a-zA-Z0-9._-]+$")
        if (!id.matches(validPattern)) {
            return ValidationResult.Invalid("Wallpaper ID contains invalid characters: $id")
        }
        
        return ValidationResult.Valid(id)
    }
    
    /**
     * Validates a URL string.
     * 
     * Requirements:
     * - Non-null and non-empty
     * - Valid HTTP or HTTPS URL format
     * - Has valid host component
     * 
     * @param url URL string to validate
     * @return ValidationResult.Valid if valid, ValidationResult.Invalid otherwise
     */
    fun validateUrl(url: String?): ValidationResult<String> {
        if (url.isNullOrBlank()) {
            return ValidationResult.Invalid("URL cannot be null or empty")
        }
        
        if (!Patterns.WEB_URL.matcher(url).matches()) {
            return ValidationResult.Invalid("URL is not a valid web URL: $url")
        }
        
        try {
            val parsedUrl = URL(url)
            if (parsedUrl.protocol !in listOf("http", "https")) {
                return ValidationResult.Invalid("URL must use HTTP or HTTPS protocol: $url")
            }
            
            if (parsedUrl.host.isNullOrBlank()) {
                return ValidationResult.Invalid("URL must have a valid host: $url")
            }
        } catch (e: Exception) {
            return ValidationResult.Invalid("Failed to parse URL: $url - ${e.message}")
        }
        
        return ValidationResult.Valid(url)
    }
    
    /**
     * Validates a hex color code.
     * 
     * Requirements:
     * - Non-null and non-empty
     * - Starts with '#'
     * - Followed by 6 (RGB) or 8 (ARGB) hexadecimal digits
     * 
     * Valid formats:
     * - #RRGGBB (e.g., #FF5733)
     * - #AARRGGBB (e.g., #80FF5733)
     * 
     * @param colorHex Hex color code to validate
     * @return ValidationResult.Valid if valid, ValidationResult.Invalid otherwise
     */
    fun validateColorHex(colorHex: String?): ValidationResult<String> {
        if (colorHex.isNullOrBlank()) {
            return ValidationResult.Invalid("Color hex cannot be null or empty")
        }
        
        if (!colorHex.startsWith("#")) {
            return ValidationResult.Invalid("Color hex must start with '#': $colorHex")
        }
        
        val hexPattern = Regex("^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$")
        if (!colorHex.matches(hexPattern)) {
            return ValidationResult.Invalid("Color hex must be in format #RRGGBB or #AARRGGBB: $colorHex")
        }
        
        return ValidationResult.Valid(colorHex)
    }
    
    /**
     * Validates a brightness value.
     * 
     * Requirements:
     * - Must be in range 0-100 inclusive
     * 
     * @param brightness Brightness value to validate
     * @return ValidationResult.Valid if valid, ValidationResult.Invalid otherwise
     */
    fun validateBrightness(brightness: Int): ValidationResult<Int> {
        if (brightness < 0 || brightness > 100) {
            return ValidationResult.Invalid("Brightness must be between 0 and 100, got: $brightness")
        }
        return ValidationResult.Valid(brightness)
    }
    
    /**
     * Validates a contrast value.
     * 
     * Requirements:
     * - Must be in range 0-100 inclusive
     * 
     * @param contrast Contrast value to validate
     * @return ValidationResult.Valid if valid, ValidationResult.Invalid otherwise
     */
    fun validateContrast(contrast: Int): ValidationResult<Int> {
        if (contrast < 0 || contrast > 100) {
            return ValidationResult.Invalid("Contrast must be between 0 and 100, got: $contrast")
        }
        return ValidationResult.Valid(contrast)
    }
    
    /**
     * Validates a priority/similarity score.
     * 
     * Requirements:
     * - Must be in range 0.0-1.0 inclusive
     * - Must not be NaN or Infinite
     * 
     * @param priority Priority score to validate
     * @return ValidationResult.Valid if valid, ValidationResult.Invalid otherwise
     */
    fun validatePriority(priority: Float): ValidationResult<Float> {
        if (priority.isNaN()) {
            return ValidationResult.Invalid("Priority cannot be NaN")
        }
        
        if (priority.isInfinite()) {
            return ValidationResult.Invalid("Priority cannot be Infinite")
        }
        
        if (priority < 0.0f || priority > 1.0f) {
            return ValidationResult.Invalid("Priority must be between 0.0 and 1.0, got: $priority")
        }
        
        return ValidationResult.Valid(priority)
    }
    
    /**
     * Validates an embedding vector.
     * 
     * Requirements:
     * - Non-null
     * - Must have exactly 576 dimensions (MobileNetV3 embedding size)
     * - Must not contain NaN or Infinite values
     * 
     * @param embedding Embedding vector to validate
     * @return ValidationResult.Valid if valid, ValidationResult.Invalid otherwise
     */
    fun validateEmbedding(embedding: FloatArray?): ValidationResult<FloatArray> {
        if (embedding == null) {
            return ValidationResult.Invalid("Embedding cannot be null")
        }
        
        if (embedding.size != 576) {
            return ValidationResult.Invalid("Embedding must have 576 dimensions, got: ${embedding.size}")
        }
        
        if (embedding.any { it.isNaN() || it.isInfinite() }) {
            return ValidationResult.Invalid("Embedding contains NaN or Infinite values")
        }
        
        return ValidationResult.Valid(embedding)
    }
    
    /**
     * Validates a target screen parameter.
     * 
     * Requirements:
     * - Must be one of: "home", "lock", "both", "both_different"
     * 
     * @param targetScreen Target screen value to validate
     * @return ValidationResult.Valid if valid, ValidationResult.Invalid otherwise
     */
    fun validateTargetScreen(targetScreen: String?): ValidationResult<String> {
        if (targetScreen.isNullOrBlank()) {
            return ValidationResult.Invalid("Target screen cannot be null or empty")
        }
        
        val validTargets = setOf("home", "lock", "both", "both_different")
        if (targetScreen !in validTargets) {
            return ValidationResult.Invalid(
                "Target screen must be one of $validTargets, got: $targetScreen"
            )
        }
        
        return ValidationResult.Valid(targetScreen)
    }
    
    /**
     * Validates a playlist size.
     * 
     * Requirements:
     * - Must be in range 5-50 inclusive
     * 
     * @param playlistSize Playlist size to validate
     * @return ValidationResult.Valid if valid, ValidationResult.Invalid otherwise
     */
    fun validatePlaylistSize(playlistSize: Int): ValidationResult<Int> {
        if (playlistSize < 5 || playlistSize > 50) {
            return ValidationResult.Invalid("Playlist size must be between 5 and 50, got: $playlistSize")
        }
        return ValidationResult.Valid(playlistSize)
    }
    
    /**
     * Validates a retry count.
     * 
     * Requirements:
     * - Must be non-negative
     * - Must not exceed reasonable maximum (10)
     * 
     * @param retryCount Retry count to validate
     * @return ValidationResult.Valid if valid, ValidationResult.Invalid otherwise
     */
    fun validateRetryCount(retryCount: Int): ValidationResult<Int> {
        if (retryCount < 0) {
            return ValidationResult.Invalid("Retry count cannot be negative, got: $retryCount")
        }
        
        if (retryCount > 10) {
            return ValidationResult.Invalid("Retry count exceeds maximum of 10, got: $retryCount")
        }
        
        return ValidationResult.Valid(retryCount)
    }
    
    /**
     * Validates a list of color hex codes.
     * 
     * @param colors List of hex color codes to validate
     * @return ValidationResult.Valid if all valid, ValidationResult.Invalid otherwise
     */
    fun validateColorList(colors: List<String>?): ValidationResult<List<String>> {
        if (colors == null) {
            return ValidationResult.Invalid("Color list cannot be null")
        }
        
        if (colors.isEmpty()) {
            return ValidationResult.Invalid("Color list cannot be empty")
        }
        
        colors.forEachIndexed { index, color ->
            when (val result = validateColorHex(color)) {
                is ValidationResult.Invalid -> {
                    return ValidationResult.Invalid("Color at index $index is invalid: ${result.reason}")
                }
                is ValidationResult.Valid -> { /* Continue */ }
            }
        }
        
        return ValidationResult.Valid(colors)
    }
}

/**
 * Sealed class representing the result of a validation operation.
 * 
 * Allows for exhaustive when-expressions and type-safe error handling.
 * 
 * @param T Type of the value being validated
 */
sealed class ValidationResult<out T> {
    /**
     * Indicates the value is valid.
     * 
     * @property value The validated value
     */
    data class Valid<T>(val value: T) : ValidationResult<T>()
    
    /**
     * Indicates the value is invalid.
     * 
     * @property reason Human-readable explanation of why validation failed
     */
    data class Invalid(val reason: String) : ValidationResult<Nothing>()
    
   /**
     * Returns the validated value if Valid, or null if Invalid.
     */
    fun getOrNull(): T? = when (this) {
        is Valid -> value
        is Invalid -> null
    }
    
    /**
     * Returns the validated value if Valid, or throws IllegalArgumentException if Invalid.
     */
    fun getOrThrow(): T = when (this) {
        is Valid -> value
        is Invalid -> throw IllegalArgumentException(reason)
    }
    
    /**
     * Returns true if the validation was successful.
     */
    fun isValid(): Boolean = this is Valid
    
    /**
     * Returns true if the validation failed.
     */
    fun isInvalid(): Boolean = this is Invalid
}
