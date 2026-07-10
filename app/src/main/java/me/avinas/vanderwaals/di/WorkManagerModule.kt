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
 * Hilt module providing custom WorkManager configuration with
 * HiltWorkerFactory and a 4-thread executor.
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
