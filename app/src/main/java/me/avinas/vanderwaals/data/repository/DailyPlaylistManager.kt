package me.avinas.vanderwaals.data.repository

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DailyPlaylistManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    
    companion object {
        private const val PREF_NAME = "vanderwaals_daily_playlist"
        private const val KEY_PLAYLIST = "playlist_ids"
        private const val KEY_CURRENT_INDEX = "current_index"
    }
    
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }
    
    // Resets the current index to 0.
    suspend fun setPlaylist(wallpaperIds: List<String>) = withContext(Dispatchers.IO) {
        val jsonArray = JSONArray(wallpaperIds)
        prefs.edit()
            .putString(KEY_PLAYLIST, jsonArray.toString())
            .putInt(KEY_CURRENT_INDEX, 0)
            .apply()
    }
    
    suspend fun getPlaylist(): List<String> = withContext(Dispatchers.IO) {
        val jsonString = prefs.getString(KEY_PLAYLIST, null) ?: return@withContext emptyList()
        try {
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                list.add(jsonArray.getString(i))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    // Cycles back to the beginning if the end is reached.
    suspend fun getNextWallpaperId(): String? = withContext(Dispatchers.IO) {
        val playlist = getPlaylist()
        if (playlist.isEmpty()) return@withContext null
        
        var currentIndex = prefs.getInt(KEY_CURRENT_INDEX, 0)
        
        // Safety check if playlist size changed or index is out of bounds
        if (currentIndex >= playlist.size) {
            currentIndex = 0
        }
        
        val wallpaperId = playlist[currentIndex]
        
        val nextIndex = (currentIndex + 1) % playlist.size
        prefs.edit().putInt(KEY_CURRENT_INDEX, nextIndex).apply()
        
        wallpaperId
    }
    
    suspend fun clearPlaylist() = withContext(Dispatchers.IO) {
        prefs.edit().clear().apply()
    }
    
    suspend fun hasPlaylist(): Boolean = withContext(Dispatchers.IO) {
        getPlaylist().isNotEmpty()
    }
}
