package me.avinas.vanderwaals.di

import android.content.Context
import androidx.room.Room
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import me.avinas.vanderwaals.data.VanderwaalsDatabase
import me.avinas.vanderwaals.data.dao.CategoryPreferenceDao
import me.avinas.vanderwaals.data.dao.CompositionPreferenceDao
import me.avinas.vanderwaals.data.dao.DownloadQueueDao
import me.avinas.vanderwaals.data.dao.UserPreferenceDao
import me.avinas.vanderwaals.data.dao.WallpaperHistoryDao
import me.avinas.vanderwaals.data.dao.WallpaperMetadataDao
import me.avinas.vanderwaals.data.entity.Converters
import javax.inject.Singleton

/**
 * Hilt/Dagger module for providing database dependencies.
 * 
 * Provides singleton instances of:
 * - [VanderwaalsDatabase]: Main Room database
 * - [Converters]: Type converters for Room
 * - All DAOs: WallpaperMetadataDao, UserPreferenceDao, WallpaperHistoryDao, DownloadQueueDao
 * 
 * **Architecture:**
 * - Singleton scope ensures single database instance app-wide
 * - Converters are injected into database via Room's addTypeConverter
 * - All DAOs are provided from the database instance
 * 
 * **Usage:**
 * This module is automatically discovered by Hilt. Just inject dependencies:
 * ```kotlin
 * @HiltViewModel
 * class WallpaperViewModel @Inject constructor(
 *     private val metadataDao: WallpaperMetadataDao,
 *     private val preferencesDao: UserPreferenceDao
 * ) : ViewModel() {
 *     // Use DAOs
 * }
 * ```
 * 
 * **Development vs Production:**
 * - Development: Uses fallbackToDestructiveMigration() for quick iteration
 * - Production: Remove fallback, use proper migrations in VanderwaalsDatabase.MIGRATIONS
 * 
 * @see VanderwaalsDatabase
 * @see Converters
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    /**
     * Provides a singleton Converters instance.
     * 
     * Injected into Room database via @ProvidedTypeConverter.
     * This allows using a custom-configured Gson instance and
     * makes testing easier by allowing mock converters.
     * 
     * @param gson Gson instance for serialization
     * @return Converters instance with injected Gson
     */
    @Provides
    @Singleton
    fun provideConverters(gson: Gson): Converters {
        return Converters(gson)
    }    /**
     * Provides a singleton VanderwaalsDatabase instance.
     * 
     * Configures Room database with:
     * - Type converters for complex types
     * - Migrations (empty for version 1)
     * - Destructive migration fallback (development only - REMOVE for production)
     * 
     * **Important for Production:**
     * Remove `.fallbackToDestructiveMigration()` and add proper migrations
     * to preserve user data during schema changes.
     * 
     * @param context Application context
     * @param converters Type converters instance
     * @return Configured database instance
     * 
     * Example production configuration:
     * ```kotlin
     * Room.databaseBuilder(context, VanderwaalsDatabase::class.java, VanderwaalsDatabase.DATABASE_NAME)
     *     .addTypeConverter(converters)
     *     .addMigrations(*VanderwaalsDatabase.MIGRATIONS)
     *     .build()
     * ```
     */
    @Provides
    @Singleton
    fun provideVanderwaalsDatabase(
        @ApplicationContext context: Context,
        converters: Converters
    ): VanderwaalsDatabase {
        return Room.databaseBuilder(
            context,
            VanderwaalsDatabase::class.java,
            VanderwaalsDatabase.DATABASE_NAME
        )
        .addTypeConverter(converters)
        .addMigrations(*VanderwaalsDatabase.MIGRATIONS)
        // NOTE: fallbackToDestructiveMigration() is appropriate for version 1.x development
        // Remove this line when version 2+ requires data preservation across updates
        // and add proper migrations to VanderwaalsDatabase.MIGRATIONS array
        .fallbackToDestructiveMigration(dropAllTables = true)
        // CRITICAL: Enable multi-instance invalidation for Workers
        // Workers run in separate processes and need to see main app's database updates
        .enableMultiInstanceInvalidation()
        // CRITICAL: Set WAL journal mode with TRUNCATE for better multi-process sync
        .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .build()
    }
    
    /**
     * Provides WallpaperMetadataDao from the database.
     * 
     * Used for:
     * - Loading wallpapers with embeddings
     * - Filtering by category, source, brightness
     * - Inserting wallpapers from manifest sync
     * 
     * @param database VanderwaalsDatabase instance
     * @return WallpaperMetadataDao
     */
    @Provides
    @Singleton
    fun provideWallpaperMetadataDao(
        database: VanderwaalsDatabase
    ): WallpaperMetadataDao {
        return database.wallpaperMetadataDao
    }
    
    /**
     * Provides UserPreferenceDao from the database.
     * 
     * Used for:
     * - Loading/updating user preference vector
     * - Switching between auto/personalized modes
     * - Tracking feedback count and epsilon
     * 
     * @param database VanderwaalsDatabase instance
     * @return UserPreferenceDao
     */
    @Provides
    @Singleton
    fun provideUserPreferenceDao(
        database: VanderwaalsDatabase
    ): UserPreferenceDao {
        return database.userPreferenceDao
    }
    
    /**
     * Provides WallpaperHistoryDao from the database.
     * 
     * Used for:
     * - Recording wallpaper applications/removals
     * - Tracking user feedback (likes/dislikes)
     * - Displaying history in UI
     * - Learning from implicit feedback
     * 
     * @param database VanderwaalsDatabase instance
     * @return WallpaperHistoryDao
     */
    @Provides
    @Singleton
    fun provideWallpaperHistoryDao(
        database: VanderwaalsDatabase
    ): WallpaperHistoryDao {
        return database.wallpaperHistoryDao
    }
    
    /**
     * Provides DownloadQueueDao from the database.
     * 
     * Used for:
     * - Populating queue with top matches
     * - Retrieving wallpapers to download
     * - Updating priorities and download status
     * - Managing retry logic
     * 
     * @param database VanderwaalsDatabase instance
     * @return DownloadQueueDao
     */
    @Provides
    @Singleton
    fun provideDownloadQueueDao(
        database: VanderwaalsDatabase
    ): DownloadQueueDao {
        return database.downloadQueueDao
    }
    
    /**
     * Provides CategoryPreferenceDao from the database.
     * 
     * Used for:
     * - Tracking category-level preferences (likes, dislikes, views)
     * - Computing category preference scores
     * - Enabling category-aware exploration
     * - Managing temporal diversity across categories
     * 
     * @param database VanderwaalsDatabase instance
     * @return CategoryPreferenceDao
     */
    @Provides
    @Singleton
    fun provideCategoryPreferenceDao(
        database: VanderwaalsDatabase
    ): CategoryPreferenceDao {
        return database.categoryPreferenceDao
    }
    
    /**
     * Provides ColorPreferenceDao from the database.
     * 
     * Used for:
     * - Tracking color-level preferences (likes, dislikes, views)
     * - Computing color preference scores
     * - Fallback personalization when categories are missing
     * - RGB Euclidean distance color similarity matching
     * 
     * @param database VanderwaalsDatabase instance
     * @return ColorPreferenceDao
     */
    @Provides
    @Singleton
    fun provideColorPreferenceDao(
        database: VanderwaalsDatabase
    ): me.avinas.vanderwaals.data.dao.ColorPreferenceDao {
        return database.colorPreferenceDao
    }
    
    /**
     * Provides CompositionPreferenceDao from the database.
     * 
     * Used for:
     * - Tracking composition/layout preferences (symmetry, center weight, etc.)
     * - Computing composition preference scores
     * - Learning visual composition preferences from feedback
     * - Matching wallpapers based on composition similarity
     * 
     * @param database VanderwaalsDatabase instance
     * @return CompositionPreferenceDao
     */
    @Provides
    @Singleton
    fun provideCompositionPreferenceDao(
        database: VanderwaalsDatabase
    ): CompositionPreferenceDao {
        return database.compositionPreferenceDao
    }
}
