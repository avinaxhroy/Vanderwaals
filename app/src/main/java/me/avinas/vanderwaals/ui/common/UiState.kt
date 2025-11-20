package me.avinas.vanderwaals.ui.common

/**
 * Sealed class representing UI state for async operations.
 * 
 * Usage in ViewModels:
 * ```kotlin
 * private val _wallpapers = MutableStateFlow<UiState<List<Wallpaper>>>(UiState.Loading)
 * val wallpapers: StateFlow<UiState<List<Wallpaper>>> = _wallpapers.asStateFlow()
 * 
 * viewModelScope.launch {
 *     _wallpapers.value = UiState.Loading
 *     try {
 *         val data = repository.getWallpapers()
 *         _wallpapers.value = UiState.Success(data)
 *     } catch (e: Exception) {
 *         _wallpapers.value = UiState.Error("Failed to load wallpapers: ${e.message}")
 *     }
 * }
 * ```
 * 
 * Usage in Composables:
 * ```kotlin
 * val wallpapers by viewModel.wallpapers.collectAsState()
 * 
 * when (val state = wallpapers) {
 *     is UiState.Loading -> LoadingContent()
 *     is UiState.Success -> WallpaperList(state.data)
 *     is UiState.Error -> ErrorContent(state.message) { viewModel.retry() }
 * }
 * ```
 */
sealed class UiState<out T> {
    /**
     * Initial loading state.
     */
    object Loading : UiState<Nothing>()
    
    /**
     * Success state with data.
     * 
     * @param data The successfully loaded data
     */
    data class Success<T>(val data: T) : UiState<T>()
    
    /**
     * Error state with user-friendly message.
     * 
     * @param message User-friendly error message
     * @param throwable Optional exception for logging
     */
    data class Error(
        val message: String,
        val throwable: Throwable? = null
    ) : UiState<Nothing>()
    
    /**
     * Empty state (success with no data).
     */
    object Empty : UiState<Nothing>()
}

/**
 * Extension to check if state is loading.
 */
fun <T> UiState<T>.isLoading(): Boolean = this is UiState.Loading

/**
 * Extension to check if state is success.
 */
fun <T> UiState<T>.isSuccess(): Boolean = this is UiState.Success

/**
 * Extension to check if state is error.
 */
fun <T> UiState<T>.isError(): Boolean = this is UiState.Error

/**
 * Extension to get data or null.
 */
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
    
    /**
     * Converts exception to user-friendly message.
     */
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
