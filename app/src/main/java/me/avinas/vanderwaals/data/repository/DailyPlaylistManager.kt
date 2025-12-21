package me.avinas.vanderwaals.data.repository

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the daily wallpaper playlist.
 * 
 * Responsibilities:
 * - Persisting the list of wallpaper IDs for the current day
 * - Providing access to the playlist
 * - Managing the "current index" for rotation
 */
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
    
    /**
     * Saves a new list of wallpaper IDs as the daily playlist.
     * Resets the current index to 0.
     */
    suspend fun setPlaylist(wallpaperIds: List<String>) = withContext(Dispatchers.IO) {
        val jsonArray = JSONArray(wallpaperIds)
        prefs.edit()
            .putString(KEY_PLAYLIST, jsonArray.toString())
            .putInt(KEY_CURRENT_INDEX, 0)
            .apply()
    }
    
    /**
     * Retrieves the current playlist of wallpaper IDs.
     */
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
    
    /**
     * Gets the next wallpaper ID from the playlist and advances the index.
     * Cycles back to the beginning if the end is reached.
     */
    suspend fun getNextWallpaperId(): String? = withContext(Dispatchers.IO) {
        val playlist = getPlaylist()
        if (playlist.isEmpty()) return@withContext null
        
        var currentIndex = prefs.getInt(KEY_CURRENT_INDEX, 0)
        
        // Safety check if playlist size changed or index is out of bounds
        if (currentIndex >= playlist.size) {
            currentIndex = 0
        }
        
        val wallpaperId = playlist[currentIndex]
        
        // Advance index for next time
        val nextIndex = (currentIndex + 1) % playlist.size
        prefs.edit().putInt(KEY_CURRENT_INDEX, nextIndex).apply()
        
        wallpaperId
    }
    
    /**
     * Clears the current playlist.
     */
    suspend fun clearPlaylist() = withContext(Dispatchers.IO) {
        prefs.edit().clear().apply()
    }
    
    /**
     * Checks if a playlist exists and has items.
     */
    suspend fun hasPlaylist(): Boolean = withContext(Dispatchers.IO) {
        getPlaylist().isNotEmpty()
    }
}
