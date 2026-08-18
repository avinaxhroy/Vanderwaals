package me.avinas.vanderwaals.ui.history

import android.content.Context
import android.util.Log
import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import me.avinas.vanderwaals.core.MediaSaver
import me.avinas.vanderwaals.data.dao.WallpaperHistoryDao
import me.avinas.vanderwaals.data.entity.WallpaperHistory
import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import me.avinas.vanderwaals.data.repository.WallpaperRepository
import me.avinas.vanderwaals.domain.usecase.FeedbackType
import me.avinas.vanderwaals.domain.usecase.UpdatePreferencesUseCase
import me.avinas.vanderwaals.worker.WallpaperApplicator
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

enum class HistoryFilter(val label: String) {
    ALL("All"),
    LIKED("Liked"),
    HIDDEN("Hidden"),
    SAVED("Saved")
}

data class HistoryStats(
    val totalCount: Int = 0,
    val likedCount: Int = 0,
    val dislikedCount: Int = 0,
    val savedCount: Int = 0,
    val topCategory: String? = null
)

data class HistoryItemUiState(
    val id: Long,
    val wallpaper: WallpaperMetadata,
    val appliedAt: String,
    val localCroppedPath: String,
    val feedback: FeedbackType?,
    val isDownloaded: Boolean = false,
    val rawTimestamp: Long = 0L
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyDao: WallpaperHistoryDao,
    private val wallpaperRepository: WallpaperRepository,
    private val updatePreferencesUseCase: UpdatePreferencesUseCase,
    private val wallpaperApplicator: WallpaperApplicator,
    private val mediaSaver: MediaSaver,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    sealed interface HistoryUiState {
        data object Loading : HistoryUiState
        data class Success(
            val allGroups: List<Pair<String, List<HistoryItemUiState>>>,
            val filteredGroups: List<Pair<String, List<HistoryItemUiState>>>,
            val stats: HistoryStats,
            val selectedFilter: HistoryFilter
        ) : HistoryUiState
    }

    private val _selectedFilter = MutableStateFlow(HistoryFilter.ALL)
    val selectedFilter: StateFlow<HistoryFilter> = _selectedFilter.asStateFlow()

    private val _isApplying = MutableStateFlow<String?>(null)
    val isApplying: StateFlow<String?> = _isApplying.asStateFlow()

    /**
     * History items grouped by date headers and filtered by active category/signal.
     */
    val historyGroups: StateFlow<HistoryUiState> =
        combine(
            historyDao.getHistory(),
            wallpaperRepository.getAllWallpaperSummaries(),
            _selectedFilter
        ) { historyList, wallpapers, filter ->
            if (historyList.isNotEmpty() && wallpapers.isEmpty()) {
                HistoryUiState.Loading
            } else {
                val wallpaperMap = wallpapers.associateBy { it.id }

                val allUiItems = historyList
                    .mapNotNull { history ->
                        wallpaperMap[history.wallpaperId]?.let { wallpaper ->
                            Pair(
                                HistoryItemUiState(
                                    id = history.id,
                                    wallpaper = wallpaper,
                                    appliedAt = formatRelativeTime(history.appliedAt),
                                    localCroppedPath = wallpaperRepository.getCroppedWallpaperFile(wallpaper).absolutePath,
                                    feedback = when (history.userFeedback) {
                                        WallpaperHistory.FEEDBACK_LIKE -> FeedbackType.LIKE
                                        WallpaperHistory.FEEDBACK_DISLIKE -> FeedbackType.DISLIKE
                                        else -> null
                                    },
                                    isDownloaded = history.downloadedToStorage,
                                    rawTimestamp = history.appliedAt
                                ),
                                history.appliedAt
                            )
                        }
                    }
                    .sortedByDescending { (_, timestamp) -> timestamp }

                val likedCount = historyList.count { it.userFeedback == WallpaperHistory.FEEDBACK_LIKE }
                val dislikedCount = historyList.count { it.userFeedback == WallpaperHistory.FEEDBACK_DISLIKE }
                val savedCount = historyList.count { it.downloadedToStorage }
                val topCat = allUiItems
                    .map { it.first.wallpaper.category.trim() }
                    .filter { it.isNotEmpty() }
                    .groupingBy { it }
                    .eachCount()
                    .maxByOrNull { it.value }?.key

                val stats = HistoryStats(
                    totalCount = historyList.size,
                    likedCount = likedCount,
                    dislikedCount = dislikedCount,
                    savedCount = savedCount,
                    topCategory = topCat
                )

                val allGroups = allUiItems
                    .groupBy { (_, timestamp) -> getDateHeader(timestamp) }
                    .map { (header, items) -> header to items.map { (uiState, _) -> uiState } }
                    .sortedWith(compareBy { (header, _) ->
                        when (header) {
                            "Today" -> 0
                            "Yesterday" -> 1
                            else -> 2
                        }
                    })

                val filteredUiItems = when (filter) {
                    HistoryFilter.ALL -> allUiItems
                    HistoryFilter.LIKED -> allUiItems.filter { it.first.feedback == FeedbackType.LIKE }
                    HistoryFilter.HIDDEN -> allUiItems.filter { it.first.feedback == FeedbackType.DISLIKE }
                    HistoryFilter.SAVED -> allUiItems.filter { it.first.isDownloaded || it.first.feedback == FeedbackType.DOWNLOAD }
                }

                val filteredGroups = filteredUiItems
                    .groupBy { (_, timestamp) -> getDateHeader(timestamp) }
                    .map { (header, items) -> header to items.map { (uiState, _) -> uiState } }
                    .sortedWith(compareBy { (header, _) ->
                        when (header) {
                            "Today" -> 0
                            "Yesterday" -> 1
                            else -> 2
                        }
                    })

                HistoryUiState.Success(
                    allGroups = allGroups,
                    filteredGroups = filteredGroups,
                    stats = stats,
                    selectedFilter = filter
                )
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HistoryUiState.Loading
        )

    fun setFilter(filter: HistoryFilter) {
        _selectedFilter.value = filter
    }

    /**
     * Updates feedback for a history item and triggers preference learning.
     */
    fun updateFeedback(historyId: Long, feedback: FeedbackType, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val history = historyDao.getHistory().first().find { it.id == historyId }
                    ?: return@launch

                val wallpaper = wallpaperRepository.getAllWallpapers().first()
                    .find { it.id == history.wallpaperId }
                    ?: return@launch

                val result = updatePreferencesUseCase(wallpaper, feedback)
                result.fold(
                    onSuccess = {
                        val updatedHistory = history.copy(
                            userFeedback = when (feedback) {
                                FeedbackType.LIKE, FeedbackType.DOWNLOAD -> WallpaperHistory.FEEDBACK_LIKE
                                FeedbackType.DISLIKE -> WallpaperHistory.FEEDBACK_DISLIKE
                            }
                        )
                        historyDao.update(updatedHistory)
                        onSuccess()
                    },
                    onFailure = { error ->
                        Log.e("HistoryViewModel", "Failed to update preferences from history action", error)
                    }
                )
            } catch (e: Exception) {
                Log.e("HistoryViewModel", "Error updating history feedback", e)
            }
        }
    }

    /**
     * Downloads a wallpaper to device storage and marks it in history.
     */
    fun downloadWallpaper(historyId: Long, wallpaperId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val fullWallpaper = wallpaperRepository.getAllWallpapers().first()
                    .find { it.id == wallpaperId }
                    ?: return@launch

                val downloadResult = wallpaperRepository.downloadWallpaper(fullWallpaper)
                downloadResult.onSuccess { file ->
                    if (!file.exists() || file.length() <= 0) {
                        onError("Download failed: Empty file")
                        return@onSuccess
                    }

                    val saveResult = mediaSaver.saveImageToGallery(file, fullWallpaper.id)
                    if (saveResult.isSuccess) {
                        val history = historyDao.getHistory().first().find { it.id == historyId }
                        if (history != null) {
                            historyDao.update(
                                history.copy(
                                    downloadedToStorage = true,
                                    userFeedback = history.userFeedback ?: WallpaperHistory.FEEDBACK_LIKE
                                )
                            )
                        }

                        if (fullWallpaper.embedding.isNotEmpty()) {
                            updatePreferencesUseCase(fullWallpaper, FeedbackType.DOWNLOAD)
                        }
                        onSuccess()
                    } else {
                        onError("Failed to save to gallery")
                    }
                }
                downloadResult.onFailure {
                    onError("Download failed: ${it.message}")
                }
            } catch (e: Exception) {
                Log.e("HistoryViewModel", "Error downloading wallpaper from history", e)
                onError("Error: ${e.message}")
            }
        }
    }

    fun applyWallpaper(
        wallpaper: WallpaperMetadata,
        targetScreen: String = "both",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isApplying.value = wallpaper.id
            try {
                val downloadResult = wallpaperRepository.downloadWallpaper(wallpaper)
                downloadResult.fold(
                    onSuccess = { file ->
                        val applyResult = wallpaperApplicator.apply(file, targetScreen)
                        when (applyResult) {
                            is WallpaperApplicator.ApplyResult.Success -> {
                                val newHistory = WallpaperHistory(
                                    wallpaperId = wallpaper.id,
                                    appliedAt = System.currentTimeMillis(),
                                    removedAt = null,
                                    userFeedback = null,
                                    downloadedToStorage = false
                                )
                                historyDao.insert(newHistory)
                                onSuccess()
                            }
                            is WallpaperApplicator.ApplyResult.BlockedByLiveWallpaper -> {
                                onError("Blocked by live wallpaper: ${applyResult.serviceName}")
                            }
                            is WallpaperApplicator.ApplyResult.DecodeFailed -> {
                                onError("Failed to decode wallpaper bitmap")
                            }
                            is WallpaperApplicator.ApplyResult.InvalidTarget -> {
                                onError("Invalid screen target: $targetScreen")
                            }
                            is WallpaperApplicator.ApplyResult.Error -> {
                                onError(applyResult.exception.message ?: "Failed to set wallpaper")
                            }
                        }
                    },
                    onFailure = { error ->
                        onError(error.message ?: "Failed to download wallpaper for application")
                    }
                )
            } catch (e: Exception) {
                Log.e("HistoryViewModel", "Error applying wallpaper from history", e)
                onError("Error: ${e.message}")
            } finally {
                _isApplying.value = null
            }
        }
    }

    fun showSnackbar(snackbarHostState: SnackbarHostState, message: String) {
        viewModelScope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }

    private fun formatRelativeTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = (now - timestamp).milliseconds

        return when {
            diff < 24.hours -> {
                when {
                    diff.inWholeHours < 1 -> {
                        val mins = diff.inWholeMinutes.coerceAtLeast(1)
                        if (mins == 1L) "Applied 1 min ago" else "Applied $mins mins ago"
                    }
                    diff.inWholeHours == 1L -> "Applied 1 hour ago"
                    else -> "Applied ${diff.inWholeHours} hours ago"
                }
            }
            diff < 48.hours -> {
                val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                "Yesterday at ${timeFormat.format(Date(timestamp))}"
            }
            else -> {
                val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                dateFormat.format(Date(timestamp))
            }
        }
    }

    private fun getDateHeader(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = (now - timestamp).milliseconds

        return when {
            diff < 24.hours -> "Today"
            diff < 48.hours -> "Yesterday"
            else -> {
                val dateFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                dateFormat.format(Date(timestamp))
            }
        }
    }
}
