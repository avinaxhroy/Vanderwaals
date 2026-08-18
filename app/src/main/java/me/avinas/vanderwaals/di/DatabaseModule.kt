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
 * Hilt module providing the Room database, type converters, and all DAOs
 * as singletons.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    /**
     * Injected into Room via @ProvidedTypeConverter so a custom-configured
     * Gson instance can be used and tests can supply mock converters.
     */
    @Provides
    @Singleton
    fun provideConverters(gson: Gson): Converters {
        return Converters(gson)
    }    /**
     * Production note: remove `.fallbackToDestructiveMigration()`
     * and add proper migrations to preserve user data during schema changes.
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
        .enableMultiInstanceInvalidation()
        .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .fallbackToDestructiveMigrationOnDowngrade(true)
        .fallbackToDestructiveMigration(true)
        .build()
    }
    
    @Provides
    @Singleton
    fun provideWallpaperMetadataDao(
        database: VanderwaalsDatabase
    ): WallpaperMetadataDao {
        return database.wallpaperMetadataDao
    }
    
    @Provides
    @Singleton
    fun provideUserPreferenceDao(
        database: VanderwaalsDatabase
    ): UserPreferenceDao {
        return database.userPreferenceDao
    }
    
    @Provides
    @Singleton
    fun provideWallpaperHistoryDao(
        database: VanderwaalsDatabase
    ): WallpaperHistoryDao {
        return database.wallpaperHistoryDao
    }
    
    @Provides
    @Singleton
    fun provideDownloadQueueDao(
        database: VanderwaalsDatabase
    ): DownloadQueueDao {
        return database.downloadQueueDao
    }
    
    @Provides
    @Singleton
    fun provideCategoryPreferenceDao(
        database: VanderwaalsDatabase
    ): CategoryPreferenceDao {
        return database.categoryPreferenceDao
    }
    
    @Provides
    @Singleton
    fun provideColorPreferenceDao(
        database: VanderwaalsDatabase
    ): me.avinas.vanderwaals.data.dao.ColorPreferenceDao {
        return database.colorPreferenceDao
    }
    
    @Provides
    @Singleton
    fun provideCompositionPreferenceDao(
        database: VanderwaalsDatabase
    ): CompositionPreferenceDao {
        return database.compositionPreferenceDao
    }

    @Provides
    @Singleton
    fun provideTasteAnchorDao(
        database: VanderwaalsDatabase
    ): me.avinas.vanderwaals.data.dao.TasteAnchorDao {
        return database.tasteAnchorDao
    }

    }
