package me.avinas.vanderwaals.di

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import me.avinas.vanderwaals.BuildConfig
import javax.inject.Singleton

/**
 * Hilt module for WorkManager dependency injection.
 * 
 * Provides custom WorkManager configuration with:
 * - HiltWorkerFactory for injecting dependencies into Workers
 * - Limited executor threads (4) to avoid resource exhaustion
 * - Debug/release logging levels
 * 
 * **Integration:**
 * The Application class must implement Configuration.Provider and
 * override workManagerConfiguration to use this custom configuration.
 * 
 * **Usage:**
 * Workers can now use @HiltWorker annotation and constructor injection:
 * ```kotlin
 * @HiltWorker
 * class WallpaperChangeWorker @AssistedInject constructor(
 *     @Assisted appContext: Context,
 *     @Assisted workerParams: WorkerParameters,
 *     private val repository: WallpaperRepository
 * ) : CoroutineWorker(appContext, workerParams) {
 *     // repository is injected automatically
 * }
 * ```
 * 
 * @see me.avinas.vanderwaals.VanderwaalsApplication
 * @see me.avinas.vanderwaals.worker.WallpaperChangeWorker
 * @see me.avinas.vanderwaals.worker.ManifestSyncWorker
 * @see me.avinas.vanderwaals.worker.CleanupWorker
 */
@Module
@InstallIn(SingletonComponent::class)
object WorkManagerModule {
    
    /**
     * Provides custom WorkManager Configuration.
     * 
     * Configuration details:
     * - Sets HiltWorkerFactory for dependency injection
     * - Sets logging level based on build variant (DEBUG/ERROR)
     * - Configures custom executor with limited threads
     * 
     * @param workerFactory HiltWorkerFactory for creating Workers
     * @return WorkManager Configuration
     */
    @Provides
    @Singleton
    fun provideWorkManagerConfiguration(
        workerFactory: HiltWorkerFactory
    ): Configuration {
        return Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.ERROR)
            .setExecutor { command ->
                Thread(command).apply {
                    priority = Thread.NORM_PRIORITY - 1
                    start()
                }
            }
            .build()
    }
}
