package me.avinas.vanderwaals.ui.history

import android.content.Context
import android.os.Environment
import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import me.avinas.vanderwaals.data.dao.WallpaperHistoryDao
import me.avinas.vanderwaals.data.entity.WallpaperHistory
import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import me.avinas.vanderwaals.data.repository.WallpaperRepository
import me.avinas.vanderwaals.domain.usecase.FeedbackType
import me.avinas.vanderwaals.domain.usecase.UpdatePreferencesUseCase
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import me.avinas.vanderwaals.core.MediaSaver

/**
 * ViewModel for history screen state management.
 * 
 * Manages:
 * - Feedback history list (grouped by date)
 * - User feedback actions (like, dislike, download)
 * - Preference vector updates from feedback
 * - Queue reranking after significant updates
 * - History item deletion
 * 
 * StateFlow emissions:
 * - HistoryState: List of feedback entries grouped by date
 * - LoadingState: Processing state for feedback actions
 * - UpdateState: Confirmation of preference updates
 * - EmptyState: Whether history is empty
 * 
 * Uses cases:
 * - ProcessFeedbackUseCase: Update preferences from likes/dislikes
 * - GetRankedWallpapersUseCase: Rerank queue after feedback
 * 
 * Data sources:
 * - FeedbackRepository: Load history, record feedback
 * - PreferenceRepository: Update preference vector
 * 
 * Observes:
 * - Feedback history Flow from Room database (reactive updates)
 * - Preference update events
 * 
 * @see HistoryScreen
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyDao: WallpaperHistoryDao,
    private val wallpaperRepository: WallpaperRepository,
    private val updatePreferencesUseCase: UpdatePreferencesUseCase,
    private val mediaSaver: MediaSaver,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    /**
     * History items grouped by date headers (Today, Yesterday, Month Year).
     * Each group contains a list of HistoryItemUiState.
     */
    val historyGroups: StateFlow<List<Pair<String, List<HistoryItemUiState>>>> =
        historyDao.getHistory()
            .combine(wallpaperRepository.getAllWallpapers()) { historyList, wallpapers ->
                // Create map for quick wallpaper lookup
                val wallpaperMap = wallpapers.associateBy { it.id }
                
                // Convert to UI state with timestamp tracking for grouping
                val uiItems = historyList
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
                                    }
                                ),
                                history.appliedAt  // Track original timestamp
                            )
                        }
                    }
                    .sortedByDescending { (_, timestamp) -> timestamp } // Sort by timestamp descending - most recent first
                    .groupBy { (_, timestamp) -> getDateHeader(timestamp) }
                    .map { (header, items) -> header to items.map { (uiState, _) -> uiState } }
                    .sortedBy { (header, _) ->
                        // Sort groups: Today, Yesterday, then by date descending
                        when (header) {
                            "Today" -> 0
                            "Yesterday" -> 1
                            else -> 2
                        }
                    }
                uiItems
            }
            .flowOn(Dispatchers.Default) // CRITICAL: Run heavy sorting/grouping on Default dispatcher
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    /**
     * Updates feedback for a history item and triggers preference learning.
     * 
     * @param historyId The ID of the history entry
     * @param feedback The type of feedback (LIKE or DISLIKE)
     * @param onSuccess Callback invoked on successful update
     */
    fun updateFeedback(historyId: Long, feedback: FeedbackType, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                // Get the history entry
                val history = historyDao.getHistory().first().find { it.id == historyId }
                    ?: return@launch
                
                // Get the wallpaper metadata
                val wallpaper = wallpaperRepository.getAllWallpapers().first()
                    .find { it.id == history.wallpaperId }
                    ?: return@launch
                
                // Update preferences using the use case
                val result = updatePreferencesUseCase(wallpaper, feedback)
                
                result.fold(
                    onSuccess = {
                        // Update history with feedback
                        val updatedHistory = history.copy(
                            userFeedback = when (feedback) {
                                FeedbackType.LIKE -> WallpaperHistory.FEEDBACK_LIKE
                                FeedbackType.DISLIKE -> WallpaperHistory.FEEDBACK_DISLIKE
                            }
                        )
                        historyDao.update(updatedHistory)
                        onSuccess()
                    },
                    onFailure = { error ->
                        // Log error or show toast
                        println("Failed to update preferences: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                println("Error updating feedback: ${e.message}")
            }
        }
    }

    /**
     * Downloads a wallpaper to device storage.
     * 
     * @param wallpaperId The ID of the wallpaper to download
     * @param onSuccess Callback invoked on successful download
     */
    fun downloadWallpaper(wallpaperId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val wallpaper = wallpaperRepository.getAllWallpapers().first()
                    .find { it.id == wallpaperId }
                    ?: return@launch
                
                // Download/Get from cache
                val downloadResult = wallpaperRepository.downloadWallpaper(wallpaper)
                
                downloadResult.onSuccess { file ->
                    // Save to gallery
                    val saveResult = mediaSaver.saveImageToGallery(file, wallpaper.id)
                    if (saveResult.isSuccess) {
                        onSuccess()
                    }
                }
            } catch (e: Exception) {
                println("Error downloading wallpaper: ${e.message}")
            }
        }
    }

    /**
     * Shows a snackbar message.
     */
    fun showSnackbar(snackbarHostState: SnackbarHostState, message: String) {
        viewModelScope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }

    /**
     * Formats a timestamp as relative time.
     * 
     * @param timestamp Milliseconds since epoch
     * @return Formatted string like "2 hours ago", "Yesterday at 8:15 PM", "May 15"
     */
    private fun formatRelativeTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = (now - timestamp).milliseconds
        
        return when {
            diff < 24.hours -> {
                // Today - show relative time
                when {
                    diff.inWholeHours < 1 -> "Applied ${diff.inWholeMinutes} minutes ago"
                    diff.inWholeHours == 1L -> "Applied 1 hour ago"
                    else -> "Applied ${diff.inWholeHours} hours ago"
                }
            }
            diff < 48.hours -> {
                // Yesterday - show time
                val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                "Applied Yesterday at ${timeFormat.format(Date(timestamp))}"
            }
            else -> {
                // Older - show date
                val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
                "Applied ${dateFormat.format(Date(timestamp))}"
            }
        }
    }

    /**
     * Gets the date header for grouping.
     * 
     * @param timestamp Milliseconds since epoch
     * @return "Today", "Yesterday", or "Month Year" (e.g., "May 2024")
     */
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

/**
 * UI state for a single history item.
 * 
 * @property id History entry ID
 * @property wallpaper Full wallpaper metadata
 * @property appliedAt Formatted relative time string
 * @property feedback Current user feedback (LIKE, DISLIKE, or null)
 */
data class HistoryItemUiState(
    val id: Long,
    val wallpaper: WallpaperMetadata,
    val appliedAt: String,
    val localCroppedPath: String,
    val feedback: FeedbackType?
)
