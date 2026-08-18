package me.avinas.vanderwaals.ui.common

sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    
    data class Success<T>(val data: T) : UiState<T>()
    
    data class Error(
        val message: String,
        val throwable: Throwable? = null
    ) : UiState<Nothing>()
    
    /**
     * Empty state (success with no data).
     */
    object Empty : UiState<Nothing>()
}

fun <T> UiState<T>.isLoading(): Boolean = this is UiState.Loading

fun <T> UiState<T>.isSuccess(): Boolean = this is UiState.Success

fun <T> UiState<T>.isError(): Boolean = this is UiState.Error

fun <T> UiState<T>.dataOrNull(): T? = (this as? UiState.Success)?.data

/**
 * User-friendly error messages for common exceptions.
 */
object ErrorMessages {
    const val NETWORK_ERROR = "No internet connection. Please check your network and try again."
    const val SERVER_ERROR = "Server error. Please try again later."
    const val NOT_FOUND = "The requested content was not found."
    const val TIMEOUT = "Request timed out. Please try again."
    const val UNKNOWN = "An unexpected error occurred. Please try again."
    const val DATABASE_ERROR = "Failed to access local database. Please restart the app."
    const val PERMISSION_DENIED = "Permission denied. Please grant the required permissions."
    const val NO_WALLPAPERS = "No wallpapers available. Try syncing your sources."
    const val SYNC_FAILED = "Failed to sync wallpapers. Please check your connection."
    const val WORKER_FAILED = "Background task failed. Please try again."
    const val INVALID_INPUT = "Invalid input. Please check your entries."
    
    fun fromException(exception: Throwable): String {
        return when {
            exception is java.net.UnknownHostException -> NETWORK_ERROR
            exception is java.net.SocketTimeoutException -> TIMEOUT
            exception is java.io.IOException -> NETWORK_ERROR
            exception.message?.contains("404") == true -> NOT_FOUND
            exception.message?.contains("500") == true -> SERVER_ERROR
            else -> "$UNKNOWN (${exception.message})"
        }
    }
}
