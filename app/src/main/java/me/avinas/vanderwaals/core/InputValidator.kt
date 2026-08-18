package me.avinas.vanderwaals.core

import java.net.URL
import java.net.URLEncoder

/**
 * Validates wallpaper IDs, URLs, colors, embedding dimensions, and worker
 * parameters. Returns [ValidationResult] (Valid/Invalid) for exhaustive handling.
 */
object InputValidator {
    
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
     * Validates a URL, encoding unencoded characters (like spaces) first — some
     * wallpaper sources have folder/file names with spaces (e.g. "Rain Dark/06. Rain Dark.jpg").
     */
    fun validateUrl(url: String?): ValidationResult<String> {
        if (url.isNullOrBlank()) {
            return ValidationResult.Invalid("URL cannot be null or empty")
        }
        
        try {
            val parsedUrl = URL(url)
            
            if (parsedUrl.protocol != "https") {
                return ValidationResult.Invalid("URL must use HTTPS")
            }
            
            if (parsedUrl.host.isNullOrBlank()) {
                return ValidationResult.Invalid("URL must have a valid host")
            }
            
            val encodedUrl = encodeUrlPath(url)
            return ValidationResult.Valid(encodedUrl)
            
        } catch (e: Exception) {
            // Try to encode and re-parse for URLs with unencoded special characters
            try {
                val encodedUrl = encodeUrlPath(url)
                val reParsedUrl = URL(encodedUrl)
                
                if (reParsedUrl.protocol != "https") {
                    return ValidationResult.Invalid("URL must use HTTPS")
                }
                
                if (reParsedUrl.host.isNullOrBlank()) {
                    return ValidationResult.Invalid("URL must have a valid host")
                }
                
                return ValidationResult.Valid(encodedUrl)
            } catch (e2: Exception) {
                return ValidationResult.Invalid("Failed to parse URL")
            }
        }
    }
    
    /**
     * Encodes the path segment of a URL to handle special characters like spaces,
     * preserving the scheme, host, and query parameters.
     */
    fun encodeUrlPath(url: String): String {
        return try {
            val parsed = URL(url)
            val encodedPath = parsed.path
                .split("/")
                .joinToString("/") { segment ->
                    URLEncoder.encode(segment, "UTF-8")
                        .replace("+", "%20") // URLEncoder encodes spaces as +, but we want %20
                }
            
            val port = if (parsed.port == -1 || parsed.port == parsed.defaultPort) "" else ":${parsed.port}"
            val query = if (parsed.query != null) "?${parsed.query}" else ""
            val ref = if (parsed.ref != null) "#${parsed.ref}" else ""
            
            "${parsed.protocol}://${parsed.host}$port$encodedPath$query$ref"
        } catch (e: Exception) {
            // Fallback: just encode spaces
            url.replace(" ", "%20")
        }
    }
    
    /** Validates a hex color code in #RRGGBB or #AARRGGBB format. */
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
    
    fun validateBrightness(brightness: Int): ValidationResult<Int> {
        if (brightness < 0 || brightness > 100) {
            return ValidationResult.Invalid("Brightness must be between 0 and 100, got: $brightness")
        }
        return ValidationResult.Valid(brightness)
    }
    
    fun validateContrast(contrast: Int): ValidationResult<Int> {
        if (contrast < 0 || contrast > 100) {
            return ValidationResult.Invalid("Contrast must be between 0 and 100, got: $contrast")
        }
        return ValidationResult.Valid(contrast)
    }
    
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
    
    /** Requires exactly 1280 dimensions (MobileNetV4-Conv-Small embedding size). */
    fun validateEmbedding(embedding: FloatArray?): ValidationResult<FloatArray> {
        if (embedding == null) {
            return ValidationResult.Invalid("Embedding cannot be null")
        }
        
        if (embedding.size != 1280) {
            return ValidationResult.Invalid("Embedding must have 1280 dimensions, got: ${embedding.size}")
        }
        
        if (embedding.any { it.isNaN() || it.isInfinite() }) {
            return ValidationResult.Invalid("Embedding contains NaN or Infinite values")
        }
        
        return ValidationResult.Valid(embedding)
    }
    
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
    
    fun validatePlaylistSize(playlistSize: Int): ValidationResult<Int> {
        if (playlistSize < 5 || playlistSize > 50) {
            return ValidationResult.Invalid("Playlist size must be between 5 and 50, got: $playlistSize")
        }
        return ValidationResult.Valid(playlistSize)
    }
    
    fun validateRetryCount(retryCount: Int): ValidationResult<Int> {
        if (retryCount < 0) {
            return ValidationResult.Invalid("Retry count cannot be negative, got: $retryCount")
        }
        
        if (retryCount > 10) {
            return ValidationResult.Invalid("Retry count exceeds maximum of 10, got: $retryCount")
        }
        
        return ValidationResult.Valid(retryCount)
    }
    
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

/** Result of a validation operation; enables exhaustive handling. */
sealed class ValidationResult<out T> {
    data class Valid<T>(val value: T) : ValidationResult<T>()
    
    data class Invalid(val reason: String) : ValidationResult<Nothing>()
    
    fun getOrNull(): T? = when (this) {
        is Valid -> value
        is Invalid -> null
    }

    fun getOrThrow(): T = when (this) {
        is Valid -> value
        is Invalid -> throw IllegalArgumentException(reason)
    }

    fun isValid(): Boolean = this is Valid

    fun isInvalid(): Boolean = this is Invalid
}
