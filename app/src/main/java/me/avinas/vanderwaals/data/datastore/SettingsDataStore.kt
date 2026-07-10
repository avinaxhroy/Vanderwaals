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

        // Vanderwaals Collection-specific preferences
        private val VANDERWAALS_COLLECTION_ENABLED = booleanPreferencesKey("vanderwaals_collection_enabled")
        private val VANDERWAALS_COLLECTION_LAST_SYNC_TIMESTAMP = androidx.datastore.preferences.core.longPreferencesKey("vanderwaals_collection_last_sync_timestamp")
        private val VANDERWAALS_COLLECTION_MANIFEST_LAST_MODIFIED = stringPreferencesKey("vanderwaals_collection_manifest_last_modified")
        private val VANDERWAALS_COLLECTION_MANIFEST_TYPE = stringPreferencesKey("vanderwaals_collection_manifest_type") // "lite" or "full"
        
        // App version tracking for migrations
        private val LAST_KNOWN_VERSION_CODE = androidx.datastore.preferences.core.intPreferencesKey("last_known_version_code")
        private val MANIFEST_VERSION = androidx.datastore.preferences.core.intPreferencesKey("manifest_version")
        private val MANIFEST_MIGRATION_PENDING = booleanPreferencesKey("manifest_migration_pending")
        private val MANIFEST_MIGRATION_DISMISSED = booleanPreferencesKey("manifest_migration_dismissed")
        
        // Embedding dimension migration (MobileNetV3 576D → MobileNetV4 1280D)
        private val EMBEDDING_DIMENSION = androidx.datastore.preferences.core.intPreferencesKey("embedding_dimension")
        private val EMBEDDING_MIGRATION_PENDING = booleanPreferencesKey("embedding_migration_pending")
        private val EMBEDDING_MIGRATION_DISMISSED = booleanPreferencesKey("embedding_migration_dismissed")
        
        // Version code thresholds for migrations
        const val MANIFEST_V2_MIN_VERSION_CODE = 400  // v4.0.0 requires manifest v2
        const val MANIFEST_V3_MIN_VERSION_CODE = 450  // v4.5.0 requires manifest v3 (MobileNetV4)
        
        // Embedding dimensions
        const val EMBEDDING_DIM_LEGACY = 576   // MobileNetV3
        const val EMBEDDING_DIM_CURRENT = 1280 // MobileNetV4-Conv-Small
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
            val githubEnabled = prefs[GITHUB_ENABLED] ?: false
            val bingEnabled = prefs[BING_ENABLED] ?: false
            val vanderwaalsCollectionEnabled = prefs[VANDERWAALS_COLLECTION_ENABLED] ?: false

            Settings(
                mode = prefs[MODE] ?: "personalized",
                changeInterval = prefs[CHANGE_INTERVAL] ?: "daily",
                dailyTime = prefs[DAILY_TIME]?.let { timeStr ->
                    try { LocalTime.parse(timeStr) } catch (e: Exception) { null }
                },
                applyTo = prefs[APPLY_TO] ?: "lock_screen",
                githubEnabled = githubEnabled,
                bingEnabled = bingEnabled,
                vanderwaalsCollectionEnabled = vanderwaalsCollectionEnabled,
                lastKnownVersionCode = prefs[LAST_KNOWN_VERSION_CODE] ?: 0,
                manifestVersion = prefs[MANIFEST_VERSION] ?: 1,
                manifestMigrationPending = prefs[MANIFEST_MIGRATION_PENDING] ?: false,
                manifestMigrationDismissed = prefs[MANIFEST_MIGRATION_DISMISSED] ?: false,
                embeddingDimension = prefs[EMBEDDING_DIMENSION] ?: 0,  // 0 = unknown/fresh install
                embeddingMigrationPending = prefs[EMBEDDING_MIGRATION_PENDING] ?: false,
                embeddingMigrationDismissed = prefs[EMBEDDING_MIGRATION_DISMISSED] ?: false,
                onboardingCompleted = prefs[ONBOARDING_COMPLETED] ?: false,
                lastSyncTimestamp = prefs[LAST_SYNC_TIMESTAMP] ?: 0L,
                themeMode = prefs[THEME_MODE] ?: "system",
                dailyPlaylistSize = prefs[DAILY_PLAYLIST_SIZE] ?: 15,
                lastPlaylistUpdate = prefs[LAST_PLAYLIST_UPDATE] ?: 0L,
                bingLastSyncTimestamp = prefs[BING_LAST_SYNC_TIMESTAMP] ?: 0L,
                bingManifestLastModified = prefs[BING_MANIFEST_LAST_MODIFIED],
                bingManifestType = prefs[BING_MANIFEST_TYPE] ?: "lite",  // Default to lite
                vanderwaalsCollectionLastSyncTimestamp = prefs[VANDERWAALS_COLLECTION_LAST_SYNC_TIMESTAMP] ?: 0L,
                vanderwaalsCollectionManifestLastModified = prefs[VANDERWAALS_COLLECTION_MANIFEST_LAST_MODIFIED],
                vanderwaalsCollectionManifestType = prefs[VANDERWAALS_COLLECTION_MANIFEST_TYPE] ?: "lite"  // Default to lite
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
                "vanderwaals" -> it[VANDERWAALS_COLLECTION_ENABLED] = enabled
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

    // Vanderwaals Collection-specific methods

    suspend fun updateVanderwaalsCollectionLastSyncTimestamp(timestamp: Long) {
        context.dataStore.edit { it[VANDERWAALS_COLLECTION_LAST_SYNC_TIMESTAMP] = timestamp }
    }

    suspend fun updateVanderwaalsCollectionManifestLastModified(lastModified: String?) {
        context.dataStore.edit {
            if (lastModified != null) {
                it[VANDERWAALS_COLLECTION_MANIFEST_LAST_MODIFIED] = lastModified
            } else {
                it.remove(VANDERWAALS_COLLECTION_MANIFEST_LAST_MODIFIED)
            }
        }
    }

    suspend fun updateVanderwaalsCollectionManifestType(type: String) {
        context.dataStore.edit { it[VANDERWAALS_COLLECTION_MANIFEST_TYPE] = type }
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
    
    // =========================================================================
    // EMBEDDING DIMENSION MIGRATION METHODS (MobileNetV3 576D → MobileNetV4 1280D)
    // =========================================================================
    
    /**
     * Updates the stored embedding dimension.
     * Called after successful onboarding or migration.
     */
    suspend fun updateEmbeddingDimension(dimension: Int) {
        context.dataStore.edit { it[EMBEDDING_DIMENSION] = dimension }
    }
    
    /**
     * Sets whether an embedding migration is pending.
     * Set to true when app detects user has legacy 576D preferences.
     */
    suspend fun setEmbeddingMigrationPending(pending: Boolean) {
        context.dataStore.edit { it[EMBEDDING_MIGRATION_PENDING] = pending }
    }
    
    /**
     * Sets whether user has dismissed the embedding migration dialog.
     * If dismissed, user continues with legacy preferences until they choose to migrate.
     */
    suspend fun setEmbeddingMigrationDismissed(dismissed: Boolean) {
        context.dataStore.edit { it[EMBEDDING_MIGRATION_DISMISSED] = dismissed }
    }
    
    /**
     * Clears embedding migration flags after successful migration.
     * Also updates embedding dimension to current (1280D).
     */
    suspend fun clearEmbeddingMigrationFlags() {
        context.dataStore.edit {
            it[EMBEDDING_MIGRATION_PENDING] = false
            it[EMBEDDING_MIGRATION_DISMISSED] = false
            it[EMBEDDING_DIMENSION] = EMBEDDING_DIM_CURRENT
        }
    }
    
    /**
     * Checks if embedding migration is needed based on stored dimension.
     * 
     * @param hasPreferences Whether user has existing preferences in database
     * @return true if embedding migration dialog should be shown
     */
    suspend fun checkEmbeddingMigrationNeeded(hasPreferences: Boolean): Boolean {
        val prefs = context.dataStore.data.first()
        val embeddingDim = prefs[EMBEDDING_DIMENSION] ?: 0
        val migrationDismissed = prefs[EMBEDDING_MIGRATION_DISMISSED] ?: false
        val onboardingCompleted = prefs[ONBOARDING_COMPLETED] ?: false
        
        // If user already dismissed, don't show again
        if (migrationDismissed) {
            return false
        }
        
        // Fresh install (no dimension set, no preferences) - set to current and skip
        if (embeddingDim == 0 && !hasPreferences) {
            context.dataStore.edit { it[EMBEDDING_DIMENSION] = EMBEDDING_DIM_CURRENT }
            return false
        }
        
        // Existing user with legacy dimension needs migration
        if (embeddingDim == EMBEDDING_DIM_LEGACY && hasPreferences && onboardingCompleted) {
            context.dataStore.edit { it[EMBEDDING_MIGRATION_PENDING] = true }
            return true
        }
        
        // Unknown dimension but has preferences - assume legacy and prompt migration
        if (embeddingDim == 0 && hasPreferences && onboardingCompleted) {
            context.dataStore.edit { 
                it[EMBEDDING_DIMENSION] = EMBEDDING_DIM_LEGACY  // Mark as legacy
                it[EMBEDDING_MIGRATION_PENDING] = true
            }
            return true
        }
        
        return false
    }
    
    /**
     * Checks if a manifest migration is needed based on version upgrade.
     * 
     * @param currentVersionCode Current app version code
     * @param databaseHasWallpapers Whether the database contains wallpapers (indicates existing user)
     * @return true if migration dialog should be shown
     */
    suspend fun checkAndSetMigrationNeeded(
        currentVersionCode: Int,
        databaseHasWallpapers: Boolean
    ): Boolean {
        val prefs = context.dataStore.data.first()
        val lastKnownVersion = prefs[LAST_KNOWN_VERSION_CODE] ?: 0
        val manifestVersion = prefs[MANIFEST_VERSION] ?: 1
        val migrationDismissed = prefs[MANIFEST_MIGRATION_DISMISSED] ?: false
        
        // If user already dismissed, don't show again
        if (migrationDismissed) {
            context.dataStore.edit { it[LAST_KNOWN_VERSION_CODE] = currentVersionCode }
            return false
        }
        
        // TRUE fresh install: no version tracking AND no database wallpapers
        val isTrueFreshInstall = lastKnownVersion == 0 && !databaseHasWallpapers
        
        if (isTrueFreshInstall) {
            // Fresh install - no migration needed, set to v3 manifest (MobileNetV4)
            context.dataStore.edit { 
                it[LAST_KNOWN_VERSION_CODE] = currentVersionCode
                it[MANIFEST_VERSION] = 3  // Fresh installs use v3 (MobileNetV4 1280D)
                it[EMBEDDING_DIMENSION] = EMBEDDING_DIM_CURRENT  // Set to 1280D
            }
            return false
        }
        
        // Existing user check for v5.0.0+ (MobileNetV4 with 1280D embeddings)
        val isUpgradingToV5 = currentVersionCode >= MANIFEST_V3_MIN_VERSION_CODE
        val hasOldManifest = manifestVersion < 3  // v1 or v2 manifests need update to v3
        
        // Show migration if:
        // 1. Upgrading to v5.0.0+ AND
        // 2. Has old manifest (v1 or v2) AND
        // 3. Has wallpapers in database (existing user)
        if (isUpgradingToV5 && hasOldManifest && databaseHasWallpapers) {
            context.dataStore.edit { 
                it[MANIFEST_MIGRATION_PENDING] = true
                it[LAST_KNOWN_VERSION_CODE] = currentVersionCode
            }
            return true
        }
        
        // No migration needed
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
    val vanderwaalsCollectionEnabled: Boolean = false,
    val lastKnownVersionCode: Int = 0,
    val manifestVersion: Int = 1,
    val manifestMigrationPending: Boolean = false,
    val manifestMigrationDismissed: Boolean = false,
    // Embedding dimension migration (MobileNetV3 576D → MobileNetV4 1280D)
    val embeddingDimension: Int = 0,  // 0 = unknown, 576 = legacy, 1280 = current
    val embeddingMigrationPending: Boolean = false,
    val embeddingMigrationDismissed: Boolean = false,
    val onboardingCompleted: Boolean,
    val lastSyncTimestamp: Long,
    val themeMode: String,
    val dailyPlaylistSize: Int,
    val lastPlaylistUpdate: Long,
    val bingLastSyncTimestamp: Long = 0L,
    val bingManifestLastModified: String? = null,
    val bingManifestType: String = "lite",  // "lite" or "full"
    val vanderwaalsCollectionLastSyncTimestamp: Long = 0L,
    val vanderwaalsCollectionManifestLastModified: String? = null,
    val vanderwaalsCollectionManifestType: String = "lite"  // "lite" or "full"
) {
    /**
     * Returns true if user needs embedding migration (has legacy 576D preferences).
     */
    val needsEmbeddingMigration: Boolean
        get() = embeddingDimension == SettingsDataStore.EMBEDDING_DIM_LEGACY && 
                !embeddingMigrationDismissed && 
                onboardingCompleted
}
