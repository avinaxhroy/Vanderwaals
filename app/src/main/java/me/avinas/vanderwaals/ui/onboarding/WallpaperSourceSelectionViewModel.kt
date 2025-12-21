package me.avinas.vanderwaals.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.avinas.vanderwaals.data.datastore.SettingsDataStore
import javax.inject.Inject

@HiltViewModel
class WallpaperSourceSelectionViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _communityEnabled = MutableStateFlow(true)
    val communityEnabled: StateFlow<Boolean> = _communityEnabled.asStateFlow()

    private val _bingEnabled = MutableStateFlow(false)
    val bingEnabled: StateFlow<Boolean> = _bingEnabled.asStateFlow()

    private val _bingManifestType = MutableStateFlow("lite")
    val bingManifestType: StateFlow<String> = _bingManifestType.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = settingsDataStore.settings.first()
            _communityEnabled.value = settings.githubEnabled
            _bingEnabled.value = settings.bingEnabled
            _bingManifestType.value = settings.bingManifestType
        }
    }

    fun toggleCommunity(enabled: Boolean) {
        _communityEnabled.value = enabled
    }

    fun toggleBing(enabled: Boolean) {
        _bingEnabled.value = enabled
    }

    fun setBingManifestType(type: String) {
        _bingManifestType.value = type
    }


    
    fun savePreferences(onComplete: () -> Unit) {
        viewModelScope.launch {
            settingsDataStore.toggleSource("github", _communityEnabled.value)
            settingsDataStore.toggleSource("bing", _bingEnabled.value)
            settingsDataStore.updateBingManifestType(_bingManifestType.value)
            onComplete()
        }
    }
}
