package me.avinas.vanderwaals.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore("vanderwaals_settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    
    companion object {
        private val MODE = stringPreferencesKey("mode")
        private val CHANGE_INTERVAL = stringPreferencesKey("change_interval")
        private val DAILY_TIME = stringPreferencesKey("daily_time")
        private val APPLY_TO = stringPreferencesKey("apply_to")
        private val GITHUB_ENABLED = booleanPreferencesKey("github_enabled")
        private val BING_ENABLED = booleanPreferencesKey("bing_enabled")
        private val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private val LAST_SYNC_TIMESTAMP = androidx.datastore.preferences.core.longPreferencesKey("last_sync_timestamp")
    }
    
    val settings: Flow<Settings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            Settings(
                mode = prefs[MODE] ?: "personalized",
                changeInterval = prefs[CHANGE_INTERVAL] ?: "daily",
                dailyTime = prefs[DAILY_TIME]?.let { LocalTime.parse(it) },
                applyTo = prefs[APPLY_TO] ?: "lock_screen",
                githubEnabled = prefs[GITHUB_ENABLED] ?: true,
                bingEnabled = prefs[BING_ENABLED] ?: false,  // Disabled by default, only enabled in auto mode
                onboardingCompleted = prefs[ONBOARDING_COMPLETED] ?: false,
                lastSyncTimestamp = prefs[LAST_SYNC_TIMESTAMP] ?: 0L
            )
        }
    
    suspend fun updateMode(mode: String) {
        context.dataStore.edit { it[MODE] = mode }
    }
    
    suspend fun updateInterval(interval: String, time: LocalTime?) {
        context.dataStore.edit {
            it[CHANGE_INTERVAL] = interval
            if (time != null) it[DAILY_TIME] = time.toString()
        }
    }
    
    suspend fun updateApplyTo(applyTo: String) {
        context.dataStore.edit { it[APPLY_TO] = applyTo }
    }
    
    suspend fun toggleSource(source: String, enabled: Boolean) {
        context.dataStore.edit {
            when (source) {
                "github" -> it[GITHUB_ENABLED] = enabled
                "bing" -> it[BING_ENABLED] = enabled
            }
        }
    }
    
    suspend fun markOnboardingComplete() {
        context.dataStore.edit { it[ONBOARDING_COMPLETED] = true }
    }
    
    suspend fun updateLastSyncTimestamp(timestamp: Long) {
        context.dataStore.edit { it[LAST_SYNC_TIMESTAMP] = timestamp }
    }
}

data class Settings(
    val mode: String,
    val changeInterval: String,
    val dailyTime: LocalTime?,
    val applyTo: String,
    val githubEnabled: Boolean,
    val bingEnabled: Boolean,
    val onboardingCompleted: Boolean,
    val lastSyncTimestamp: Long
)
