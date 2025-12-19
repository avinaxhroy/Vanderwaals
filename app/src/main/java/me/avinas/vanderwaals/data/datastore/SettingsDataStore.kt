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
import kotlinx.coroutines.flow.first
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
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val DAILY_PLAYLIST_SIZE = androidx.datastore.preferences.core.intPreferencesKey("daily_playlist_size")
        private val LAST_PLAYLIST_UPDATE = androidx.datastore.preferences.core.longPreferencesKey("last_playlist_update")
        private val DAILY_PLAYLIST_ENABLED = booleanPreferencesKey("daily_playlist_enabled")
        
        // Bing-specific preferences
        private val BING_LAST_SYNC_TIMESTAMP = androidx.datastore.preferences.core.longPreferencesKey("bing_last_sync_timestamp")
        private val BING_MANIFEST_LAST_MODIFIED = stringPreferencesKey("bing_manifest_last_modified")
        private val BING_MANIFEST_TYPE = stringPreferencesKey("bing_manifest_type") // "lite" or "full"
        
        // App version tracking for migrations
        private val LAST_KNOWN_VERSION_CODE = androidx.datastore.preferences.core.intPreferencesKey("last_known_version_code")
        private val MANIFEST_VERSION = androidx.datastore.preferences.core.intPreferencesKey("manifest_version")
        private val MANIFEST_MIGRATION_PENDING = booleanPreferencesKey("manifest_migration_pending")
        private val MANIFEST_MIGRATION_DISMISSED = booleanPreferencesKey("manifest_migration_dismissed")
        
        // Version code thresholds for migrations
        const val MANIFEST_V2_MIN_VERSION_CODE = 400  // v4.0.0 requires manifest v2
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
                bingEnabled = prefs[BING_ENABLED] ?: false,
                lastKnownVersionCode = prefs[LAST_KNOWN_VERSION_CODE] ?: 0,
                manifestVersion = prefs[MANIFEST_VERSION] ?: 1,
                manifestMigrationPending = prefs[MANIFEST_MIGRATION_PENDING] ?: false,
                manifestMigrationDismissed = prefs[MANIFEST_MIGRATION_DISMISSED] ?: false,
                onboardingCompleted = prefs[ONBOARDING_COMPLETED] ?: false,
                lastSyncTimestamp = prefs[LAST_SYNC_TIMESTAMP] ?: 0L,
                themeMode = prefs[THEME_MODE] ?: "system",
                dailyPlaylistSize = prefs[DAILY_PLAYLIST_SIZE] ?: 15,
                lastPlaylistUpdate = prefs[LAST_PLAYLIST_UPDATE] ?: 0L,
                bingLastSyncTimestamp = prefs[BING_LAST_SYNC_TIMESTAMP] ?: 0L,
                bingManifestLastModified = prefs[BING_MANIFEST_LAST_MODIFIED],
                bingManifestType = prefs[BING_MANIFEST_TYPE] ?: "lite"  // Default to lite
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

    suspend fun resetOnboarding() {
        context.dataStore.edit { it[ONBOARDING_COMPLETED] = false }
    }
    
    suspend fun updateLastSyncTimestamp(timestamp: Long) {
        context.dataStore.edit { it[LAST_SYNC_TIMESTAMP] = timestamp }
    }
    
    suspend fun updateThemeMode(themeMode: String) {
        context.dataStore.edit { it[THEME_MODE] = themeMode }
    }

    suspend fun updateDailyPlaylistEnabled(enabled: Boolean) {
        context.dataStore.edit { it[DAILY_PLAYLIST_ENABLED] = enabled }
    }

    suspend fun updateDailyPlaylistSize(size: Int) {
        context.dataStore.edit { it[DAILY_PLAYLIST_SIZE] = size }
    }

    suspend fun updateLastPlaylistUpdate(timestamp: Long) {
        context.dataStore.edit { it[LAST_PLAYLIST_UPDATE] = timestamp }
    }
    
    // Bing-specific methods
    
    suspend fun updateBingLastSyncTimestamp(timestamp: Long) {
        context.dataStore.edit { it[BING_LAST_SYNC_TIMESTAMP] = timestamp }
    }
    
    suspend fun updateBingManifestLastModified(lastModified: String?) {
        context.dataStore.edit {
            if (lastModified != null) {
                it[BING_MANIFEST_LAST_MODIFIED] = lastModified
            } else {
                it.remove(BING_MANIFEST_LAST_MODIFIED)
            }
        }
    }
    
    suspend fun updateBingManifestType(type: String) {
        context.dataStore.edit { it[BING_MANIFEST_TYPE] = type }
    }
    
    // =========================================================================
    // VERSION TRACKING AND MIGRATION METHODS
    // =========================================================================
    
    /**
     * Updates the last known version code.
     * Called on app startup to detect version upgrades.
     */
    suspend fun updateLastKnownVersionCode(versionCode: Int) {
        context.dataStore.edit { it[LAST_KNOWN_VERSION_CODE] = versionCode }
    }
    
    /**
     * Updates the current manifest version.
     * Called after successful manifest sync.
     */
    suspend fun updateManifestVersion(version: Int) {
        context.dataStore.edit { it[MANIFEST_VERSION] = version }
    }
    
    /**
     * Sets whether a manifest migration is pending.
     * Set to true when app detects upgrade from old version.
     */
    suspend fun setManifestMigrationPending(pending: Boolean) {
        context.dataStore.edit { it[MANIFEST_MIGRATION_PENDING] = pending }
    }
    
    /**
     * Sets whether user has dismissed the migration dialog.
     * Used to avoid showing the dialog repeatedly.
     */
    suspend fun setManifestMigrationDismissed(dismissed: Boolean) {
        context.dataStore.edit { it[MANIFEST_MIGRATION_DISMISSED] = dismissed }
    }
    
    /**
     * Clears migration flags after successful migration.
     */
    suspend fun clearMigrationFlags() {
        context.dataStore.edit {
            it[MANIFEST_MIGRATION_PENDING] = false
            it[MANIFEST_MIGRATION_DISMISSED] = false
        }
    }
    
    /**
     * Checks if a manifest migration is needed based on version upgrade.
     * 
     * @param currentVersionCode Current app version code
     * @return true if migration dialog should be shown
     */
    suspend fun checkAndSetMigrationNeeded(currentVersionCode: Int): Boolean {
        val prefs = context.dataStore.data.first()
        val lastKnownVersion = prefs[LAST_KNOWN_VERSION_CODE] ?: 0
        val manifestVersion = prefs[MANIFEST_VERSION] ?: 1
        val migrationDismissed = prefs[MANIFEST_MIGRATION_DISMISSED] ?: false
        
        // If this is a fresh install or already dismissed, no migration needed
        if (lastKnownVersion == 0 || migrationDismissed) {
            // Update version code for future checks
            context.dataStore.edit { it[LAST_KNOWN_VERSION_CODE] = currentVersionCode }
            return false
        }
        
        // Check if upgrading from old version AND manifest is old format
        val isUpgradingToV4 = lastKnownVersion < MANIFEST_V2_MIN_VERSION_CODE && 
                              currentVersionCode >= MANIFEST_V2_MIN_VERSION_CODE
        val hasOldManifest = manifestVersion < 2
        
        if (isUpgradingToV4 && hasOldManifest) {
            // Mark migration as pending
            context.dataStore.edit { 
                it[MANIFEST_MIGRATION_PENDING] = true
                it[LAST_KNOWN_VERSION_CODE] = currentVersionCode
            }
            return true
        }
        
        // Update version code
        context.dataStore.edit { it[LAST_KNOWN_VERSION_CODE] = currentVersionCode }
        return false
    }
}


data class Settings(
    val mode: String,
    val changeInterval: String,
    val dailyTime: LocalTime?,
    val applyTo: String,
    val githubEnabled: Boolean,
    val bingEnabled: Boolean,
    val lastKnownVersionCode: Int = 0,
    val manifestVersion: Int = 1,
    val manifestMigrationPending: Boolean = false,
    val manifestMigrationDismissed: Boolean = false,
    val onboardingCompleted: Boolean,
    val lastSyncTimestamp: Long,
    val themeMode: String,
    val dailyPlaylistSize: Int,
    val lastPlaylistUpdate: Long,
    val bingLastSyncTimestamp: Long = 0L,
    val bingManifestLastModified: String? = null,
    val bingManifestType: String = "lite"  // "lite" or "full"
)
