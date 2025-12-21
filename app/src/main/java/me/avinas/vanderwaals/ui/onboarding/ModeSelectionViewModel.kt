package me.avinas.vanderwaals.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.avinas.vanderwaals.data.datastore.SettingsDataStore
import javax.inject.Inject

/**
 * ViewModel for mode selection screen.
 * 
 * Manages user's choice between Auto Mode and Personalize mode:
 * - **Auto Mode**: Starts diverse, learns from likes/dislikes. After 10-15 likes, equally personalized.
 * - **Personalize Mode**: User uploads favorite, starts personalized immediately, continues learning
 * 
 * **State:**
 * - selectedMode: Currently selected mode (null = not selected)
 * 
 * **Actions:**
 * - selectMode(): Update selected mode and save to DataStore
 * 
 * @see ModeSelectionScreen
 */
@HiltViewModel
class ModeSelectionViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {
    
    private val _selectedMode = MutableStateFlow<OnboardingMode?>(null)
    val selectedMode: StateFlow<OnboardingMode?> = _selectedMode.asStateFlow()
    
    /**
     * Select onboarding mode and save to DataStore.
     * 
     * @param mode Selected mode (Auto or Personalize)
     */
    fun selectMode(mode: OnboardingMode, onComplete: () -> Unit) {
        _selectedMode.value = mode
        
        // Save mode to DataStore
        viewModelScope.launch {
            val modeString = when (mode) {
                OnboardingMode.AUTO -> "auto"
                OnboardingMode.PERSONALIZE -> "personalized"
            }
            settingsDataStore.updateMode(modeString)
            
            // Note: Source selection (Bing/GitHub) is now handled exclusively 
            // by WallpaperSourceSelectionScreen to avoid overwriting user choices.
            
            onComplete()
        }
    }
    
    /**
     * Reset mode selection.
     * 
     * Called when user navigates back from subsequent screens to
     * allow them to select a different mode.
     */
    fun resetModeSelection() {
        _selectedMode.value = null
    }
}

/**
 * Onboarding mode selection.
 */
enum class OnboardingMode {
    /**
     * Auto Mode: Algorithm selects wallpapers automatically.
     * Learns from implicit feedback (usage duration).
     */
    AUTO,
    
    /**
     * Personalize Mode: User uploads sample wallpaper.
     * Algorithm matches similar wallpapers immediately.
     */
    PERSONALIZE
}
