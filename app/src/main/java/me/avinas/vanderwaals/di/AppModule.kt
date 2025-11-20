package me.avinas.vanderwaals.di

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import me.avinas.vanderwaals.data.dao.CategoryPreferenceDao
import me.avinas.vanderwaals.data.dao.ColorPreferenceDao
import me.avinas.vanderwaals.data.dao.CompositionPreferenceDao
import me.avinas.vanderwaals.data.dao.DownloadQueueDao
import me.avinas.vanderwaals.data.dao.UserPreferenceDao
import me.avinas.vanderwaals.data.dao.WallpaperHistoryDao
import me.avinas.vanderwaals.data.dao.WallpaperMetadataDao
import me.avinas.vanderwaals.data.repository.CategoryPreferenceRepository
import me.avinas.vanderwaals.data.repository.CategoryPreferenceRepositoryImpl
import me.avinas.vanderwaals.data.repository.ColorPreferenceRepository
import me.avinas.vanderwaals.data.repository.ColorPreferenceRepositoryImpl
import me.avinas.vanderwaals.data.repository.CompositionPreferenceRepository
import me.avinas.vanderwaals.data.repository.FeedbackRepository
import me.avinas.vanderwaals.data.repository.FeedbackRepositoryImpl
import me.avinas.vanderwaals.data.repository.ManifestRepository
import me.avinas.vanderwaals.data.repository.PreferenceRepository
import me.avinas.vanderwaals.data.repository.PreferenceRepositoryImpl
import me.avinas.vanderwaals.data.repository.WallpaperRepository
import me.avinas.vanderwaals.data.repository.WallpaperRepositoryImpl
import me.avinas.vanderwaals.network.LocalManifestService
import me.avinas.vanderwaals.network.ManifestService
import okhttp3.OkHttpClient
import javax.inject.Singleton
import me.avinas.vanderwaals.data.VanderwaalsDatabase
import me.avinas.vanderwaals.algorithm.SimilarityCalculator
import me.avinas.vanderwaals.algorithm.PreferenceUpdater

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideContext(
        @ApplicationContext context: Context
    ): Context = context
    
    @Provides
    @Singleton
    fun provideWorkManager(
        @ApplicationContext context: Context
    ): WorkManager = WorkManager.getInstance(context)
    
    // Database and DAOs are provided by DatabaseModule
    
    @Provides
    @Singleton
    fun provideWallpaperRepository(
        @ApplicationContext context: Context,
        wallpaperMetadataDao: WallpaperMetadataDao,
        downloadQueueDao: DownloadQueueDao,
        wallpaperHistoryDao: WallpaperHistoryDao,
        okHttpClient: OkHttpClient,
        segmentedDownloader: me.avinas.vanderwaals.network.SegmentedDownloader
    ): WallpaperRepository {
        return WallpaperRepositoryImpl(
            context,
            wallpaperMetadataDao,
            downloadQueueDao,
            wallpaperHistoryDao,
            okHttpClient,
            segmentedDownloader
        )
    }
    
    @Provides
    @Singleton
    fun provideManifestRepository(
        @ApplicationContext context: Context,
        manifestService: ManifestService,
        localManifestService: LocalManifestService,
        wallpaperDao: WallpaperMetadataDao
    ): ManifestRepository {
        return ManifestRepository(manifestService, localManifestService, wallpaperDao, context)
    }
    
    @Provides
    @Singleton
    fun providePreferenceRepository(
        userPreferenceDao: UserPreferenceDao,
        database: VanderwaalsDatabase
    ): PreferenceRepository {
        return PreferenceRepositoryImpl(userPreferenceDao, database)
    }
    
    @Provides
    @Singleton
    fun provideFeedbackRepository(
        userPreferenceDao: UserPreferenceDao,
        wallpaperHistoryDao: WallpaperHistoryDao,
        downloadQueueDao: DownloadQueueDao
    ): FeedbackRepository {
        return FeedbackRepositoryImpl(userPreferenceDao, wallpaperHistoryDao, downloadQueueDao)
    }
    
    @Provides
    @Singleton
    fun provideCategoryPreferenceRepository(
        categoryPreferenceDao: CategoryPreferenceDao
    ): CategoryPreferenceRepository {
        return CategoryPreferenceRepositoryImpl(categoryPreferenceDao)
    }
    
    @Provides
    @Singleton
    fun provideCompositionPreferenceRepository(
        compositionPreferenceDao: CompositionPreferenceDao
    ): CompositionPreferenceRepository {
        return CompositionPreferenceRepository(compositionPreferenceDao)
    }
    
    @Provides
    @Singleton
    fun provideColorPreferenceRepository(
        colorPreferenceDao: ColorPreferenceDao
    ): ColorPreferenceRepository {
        return ColorPreferenceRepositoryImpl(colorPreferenceDao)
    }
    
    @Provides
    @Singleton
    fun provideWorkScheduler(
        @ApplicationContext context: Context,
        engagementTracker: me.avinas.vanderwaals.domain.usecase.UserEngagementTracker
    ): me.avinas.vanderwaals.worker.WorkScheduler {
        return me.avinas.vanderwaals.worker.WorkScheduler(context, engagementTracker)
    }
    
    @Provides
    @Singleton
    fun provideSimilarityCalculator(): SimilarityCalculator = SimilarityCalculator()
    
    @Provides
    @Singleton
    fun providePreferenceUpdater(): PreferenceUpdater = PreferenceUpdater()
    
    @Provides
    @Singleton
    fun provideEnhancedImageAnalyzer(): me.avinas.vanderwaals.algorithm.EnhancedImageAnalyzer = 
        me.avinas.vanderwaals.algorithm.EnhancedImageAnalyzer()

    @Provides
    @Singleton
    fun provideSegmentedDownloader(okHttpClient: OkHttpClient): me.avinas.vanderwaals.network.SegmentedDownloader {
        return me.avinas.vanderwaals.network.SegmentedDownloader(okHttpClient)
    }
}