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
 * ViewModel for confirmation gallery screen.
 * 
 * Displays 12 diverse wallpapers from top 20 matches (optimized for onboarding speed).
 * User can:
 * - Tap to like (heart icon)
 * - Long-press to dislike (X icon)
 * - Must like minimum 4 before continuing
 * 
 * **Preference Initialization:**
 * When user finishes:
 * 1. Calculate average embedding of liked wallpapers
 * 2. Initialize preference vector
 * 3. Store initial feedback in database
 * 
 * **State:**
 * - displayedWallpapers: 8 diverse samples
 * - likedWallpapers: User's likes
 * - dislikedWallpapers: User's dislikes
 * - canContinue: True if 3+ likes
 * 
 * @param initializePreferencesUseCase Initializes user preferences from feedback
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
     * Set similar wallpapers and original embedding from upload screen.
     * Selects 12 diverse samples for display.
     * 
     * @param wallpapers Top 50 similar wallpapers (optimized for onboarding speed)
     * @param userEmbedding Original embedding from upload/category (prime reference)
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
        
        // Display initial set of 12 wallpapers
        displayNextBatch()
    }
    
    /**
     * Refresh to show the next batch of unique wallpapers.
     * IMPORTANT: Does NOT clear user's previous likes/dislikes.
     * Shows next 12 wallpapers from the pool.
     */
    fun refreshWallpapers() {
        if (allWallpapers.isEmpty()) {
            android.util.Log.w("ConfirmationGallery", "No wallpapers to refresh!")
            return
        }
        
        // Move to next batch of wallpapers
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
    
    /**
     * Display the next batch of unique wallpapers.
     * Shows wallpapers starting from currentOffset, up to wallpapersPerPage (12).
     * 
     * Logic:
     * - First call (offset=0): Show wallpapers 0-11
     * - Second call (offset=12): Show wallpapers 12-19 (if available)
     * - Third call (offset=24): Loop back to 0 if needed
     * 
     * This ensures users see NEW wallpapers on each refresh, not the same ones shuffled.
     */
    private fun displayNextBatch() {
        if (allWallpapers.isEmpty()) {
            android.util.Log.w("ConfirmationGallery", "Cannot display wallpapers: list is empty")
            _displayedWallpapers.value = emptyList()
            return
        }
        
        // Calculate how many wallpapers we can show from current offset
        val remainingWallpapers = allWallpapers.size - currentOffset
        val countToShow = minOf(wallpapersPerPage, remainingWallpapers)
        
        // Get the next batch of wallpapers
        val batch = if (countToShow >= wallpapersPerPage) {
            // We have enough wallpapers, show full batch
            allWallpapers.subList(currentOffset, currentOffset + wallpapersPerPage)
        } else {
            // Not enough wallpapers remaining, wrap around
            val endPart = allWallpapers.subList(currentOffset, allWallpapers.size)
            val startPart = allWallpapers.subList(0, wallpapersPerPage - endPart.size)
            endPart + startPart
        }
        
        android.util.Log.d("ConfirmationGallery", 
            "Displaying ${batch.size} wallpapers (offset: $currentOffset, total: ${allWallpapers.size})")
        
        // LOG: Show which wallpapers are being displayed
        android.util.Log.d("ConfirmationGallery", "Displayed wallpaper IDs:")
        batch.take(5).forEachIndexed { index, wallpaper ->
            android.util.Log.d("ConfirmationGallery", "  ${index + 1}. ${wallpaper.id} (category: ${wallpaper.category})")
        }
        if (batch.size > 5) {
            android.util.Log.d("ConfirmationGallery", "  ... and ${batch.size - 5} more")
        }
        
        _displayedWallpapers.value = batch
    }
    
    /**
     * Toggle like for a wallpaper.
     * 
     * - If already liked: Remove from likes
     * - If not liked: Add to likes, remove from dislikes
     * 
     * @param wallpaperId Wallpaper ID
     */
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
    
    /**
     * Mark wallpaper as disliked.
     * 
     * Removes from likes if present.
     * 
     * @param wallpaperId Wallpaper ID
     */
    fun markDislike(wallpaperId: String) {
        val currentLikes = _likedWallpapers.value.toMutableSet()
        val currentDislikes = _dislikedWallpapers.value.toMutableSet()
        
        currentLikes.remove(wallpaperId)
        currentDislikes.add(wallpaperId)
        
        _likedWallpapers.value = currentLikes
        _dislikedWallpapers.value = currentDislikes
        _canContinue.value = currentLikes.size >= 4
    }
    
    /**
     * Finish onboarding and initialize preferences with DUAL-ANCHOR system.
     * 
     * Steps:
     * 1. Get embeddings for liked/disliked wallpapers
     * 2. Calculate average embedding of likes (preferenceVector)
     * 3. Store BOTH originalEmbedding (prime reference) and preferenceVector
     * 4. Store feedback in database
     * 
     * @param allWallpapers All 50 similar wallpapers (for lookup)
     */
    fun finishOnboarding(allWallpapers: List<WallpaperMetadata>) {
        viewModelScope.launch {
            _finishState.value = FinishState.Initializing
            
            // Validate original embedding exists
            val embedding = originalEmbedding
            if (embedding == null) {
                _finishState.value = FinishState.Error("Original embedding not found")
                return@launch
            }
            
            // Get liked and disliked wallpaper metadata
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
    
    /**
     * Reset finish state.
     */
    fun resetFinishState() {
        _finishState.value = FinishState.Idle
    }
}

/**
 * Preference initialization state.
 */
sealed class FinishState {
    /**
     * Idle, no initialization started.
     */
    data object Idle : FinishState()
    
    /**
     * Initializing preferences.
     */
    data object Initializing : FinishState()
    
    /**
     * Successfully initialized.
     */
    data object Success : FinishState()
    
    /**
     * Error during initialization.
     * 
     * @param message Error description
     */
    data class Error(val message: String) : FinishState()
}
