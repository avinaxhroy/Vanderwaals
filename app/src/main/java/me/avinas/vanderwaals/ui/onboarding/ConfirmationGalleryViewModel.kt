package me.avinas.vanderwaals.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import me.avinas.vanderwaals.domain.usecase.InitializePreferencesUseCase
import javax.inject.Inject

/**
 * First liked wallpaper is applied later in ApplicationSettingsViewModel
 * when the user clicks "Start Using Vanderwaals".
 */
@HiltViewModel
class ConfirmationGalleryViewModel @Inject constructor(
    private val initializePreferencesUseCase: InitializePreferencesUseCase
) : ViewModel() {
    
    private val _displayedWallpapers = MutableStateFlow<List<WallpaperMetadata>>(emptyList())
    val displayedWallpapers: StateFlow<List<WallpaperMetadata>> = _displayedWallpapers.asStateFlow()
    
    private val _likedWallpapers = MutableStateFlow<Set<String>>(emptySet())
    val likedWallpapers: StateFlow<Set<String>> = _likedWallpapers.asStateFlow()
    
    private val _dislikedWallpapers = MutableStateFlow<Set<String>>(emptySet())
    val dislikedWallpapers: StateFlow<Set<String>> = _dislikedWallpapers.asStateFlow()
    
    // Store original embedding from upload/category selection
    private var originalEmbedding: FloatArray? = null
    
    private val _canContinue = MutableStateFlow(false)
    val canContinue: StateFlow<Boolean> = _canContinue.asStateFlow()
    
    private val _finishState = MutableStateFlow<FinishState>(FinishState.Idle)
    val finishState: StateFlow<FinishState> = _finishState.asStateFlow()
    
    // Store all wallpapers for refresh functionality
    private var allWallpapers: List<WallpaperMetadata> = emptyList()
    private var currentOffset = 0  // Track which wallpapers we've shown
    private val wallpapersPerPage = 12  // How many wallpapers to show at once
    
    /**
     * Selects 12 diverse samples for display.
     */
    fun setSimilarWallpapers(wallpapers: List<WallpaperMetadata>, userEmbedding: FloatArray?) {
        if (wallpapers.isEmpty()) {
            android.util.Log.w("ConfirmationGallery", "Received empty wallpapers list!")
            return
        }
        
        android.util.Log.d("ConfirmationGallery", "Received ${wallpapers.size} wallpapers and original embedding")
        
        // Store original embedding for preference initialization
        this.originalEmbedding = userEmbedding
        
        // Store all wallpapers for refresh functionality
        allWallpapers = wallpapers
        currentOffset = 0
        
        displayNextBatch()
    }
    
    /**
     * Does NOT clear the user's previous likes/dislikes.
     */
    fun refreshWallpapers() {
        if (allWallpapers.isEmpty()) {
            android.util.Log.w("ConfirmationGallery", "No wallpapers to refresh!")
            return
        }
        
        currentOffset += wallpapersPerPage
        
        // If we've shown all wallpapers, loop back to start
        if (currentOffset >= allWallpapers.size) {
            currentOffset = 0
            android.util.Log.d("ConfirmationGallery", "Reached end of wallpapers, looping back to start")
        }
        
        android.util.Log.d("ConfirmationGallery", "Refreshing wallpapers (offset: $currentOffset)")
        
        // IMPORTANT: Keep user's likes/dislikes intact
        // Only update canContinue based on current like count
        _canContinue.value = _likedWallpapers.value.size >= 4
        
        displayNextBatch()
    }
    
    // Shows NEW wallpapers on each refresh rather than the same ones shuffled.
    private fun displayNextBatch() {
        if (allWallpapers.isEmpty()) {
            android.util.Log.w("ConfirmationGallery", "Cannot display wallpapers: list is empty")
            _displayedWallpapers.value = emptyList()
            return
        }
        
        val remainingWallpapers = allWallpapers.size - currentOffset
        val countToShow = minOf(wallpapersPerPage, remainingWallpapers)
        
        val batch = if (countToShow >= wallpapersPerPage) {
            allWallpapers.subList(currentOffset, currentOffset + wallpapersPerPage)
        } else {
            val endPart = allWallpapers.subList(currentOffset, allWallpapers.size)
            val startPart = allWallpapers.subList(0, wallpapersPerPage - endPart.size)
            endPart + startPart
        }
        
        android.util.Log.d("ConfirmationGallery", 
            "Displaying ${batch.size} wallpapers (offset: $currentOffset, total: ${allWallpapers.size})")
        
        android.util.Log.d("ConfirmationGallery", "Displayed wallpaper IDs:")
        batch.take(5).forEachIndexed { index, wallpaper ->
            android.util.Log.d("ConfirmationGallery", "  ${index + 1}. ${wallpaper.id} (category: ${wallpaper.category})")
        }
        if (batch.size > 5) {
            android.util.Log.d("ConfirmationGallery", "  ... and ${batch.size - 5} more")
        }
        
        _displayedWallpapers.value = batch
    }
    
    fun toggleLike(wallpaperId: String) {
        val currentLikes = _likedWallpapers.value.toMutableSet()
        val currentDislikes = _dislikedWallpapers.value.toMutableSet()
        
        if (currentLikes.contains(wallpaperId)) {
            currentLikes.remove(wallpaperId)
        } else {
            currentLikes.add(wallpaperId)
            currentDislikes.remove(wallpaperId)
        }
        
        _likedWallpapers.value = currentLikes
        _dislikedWallpapers.value = currentDislikes
        _canContinue.value = currentLikes.size >= 4
    }
    
    fun markDislike(wallpaperId: String) {
        val currentLikes = _likedWallpapers.value.toMutableSet()
        val currentDislikes = _dislikedWallpapers.value.toMutableSet()
        
        currentLikes.remove(wallpaperId)
        currentDislikes.add(wallpaperId)
        
        _likedWallpapers.value = currentLikes
        _dislikedWallpapers.value = currentDislikes
        _canContinue.value = currentLikes.size >= 4
    }
    
    fun finishOnboarding() {
        viewModelScope.launch {
            _finishState.value = FinishState.Initializing
            
            val embedding = originalEmbedding
            if (embedding == null) {
                _finishState.value = FinishState.Error("Original embedding not found")
                return@launch
            }
            
            val likedMetadata = allWallpapers.filter { 
                _likedWallpapers.value.contains(it.id) 
            }
            val dislikedMetadata = allWallpapers.filter { 
                _dislikedWallpapers.value.contains(it.id) 
            }
            
            initializePreferencesUseCase(
                originalEmbedding = embedding,
                likedWallpapers = likedMetadata,
                dislikedWallpapers = dislikedMetadata
            ).fold(
                onSuccess = {
                    _finishState.value = FinishState.Success
                },
                onFailure = { error ->
                    _finishState.value = FinishState.Error(
                        error.message ?: "Failed to initialize preferences"
                    )
                }
            )
        }
    }
    
    fun resetFinishState() {
        _finishState.value = FinishState.Idle
    }
    
    /**
     * Call when navigating back to UploadWallpaperScreen so the user can
     * start fresh with a different wallpaper.
     */
    fun resetStateForBackNavigation() {
        android.util.Log.d("ConfirmationGalleryViewModel", "Resetting state for back navigation")
        _displayedWallpapers.value = emptyList()
        _likedWallpapers.value = emptySet()
        _dislikedWallpapers.value = emptySet()
        _canContinue.value = false
        _finishState.value = FinishState.Idle
        allWallpapers = emptyList()
        currentOffset = 0
        originalEmbedding = null
    }
    
    fun hasWallpapers(): Boolean {
        return allWallpapers.isNotEmpty()
    }
}

sealed class FinishState {
    data object Idle : FinishState()
    
    data object Initializing : FinishState()
    
    data object Success : FinishState()
    
    data class Error(val message: String) : FinishState()
}
