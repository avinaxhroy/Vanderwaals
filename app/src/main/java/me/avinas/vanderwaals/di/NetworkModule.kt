package me.avinas.vanderwaals.di

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import me.avinas.vanderwaals.BuildConfig
import me.avinas.vanderwaals.network.ManifestService
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Qualifier annotation for jsDelivr CDN base URL.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class JsDelivrBaseUrl

/**
 * Qualifier annotation for GitHub raw base URL (fallback).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GitHubRawBaseUrl

/**
 * Hilt/Dagger module for providing network dependencies.
 * 
 * Provides singleton instances of:
 * - [Retrofit]: Configured for manifest downloads from jsDelivr/GitHub
 * - [OkHttpClient]: With timeouts, caching, and logging
 * - [Gson]: JSON serialization for manifest parsing
 * - [ManifestService]: Retrofit interface for manifest API
 * 
 * **Architecture:**
 * - Singleton scope for connection pooling and cache reuse
 * - Separate OkHttpClient instances for different base URLs if needed
 * - Debug logging enabled only in debug builds
 * - HTTP cache for offline access (10 MB)
 * 
 * **CDN Strategy:**
 * - Primary: jsDelivr CDN for fast global delivery
 * - Fallback: GitHub raw URL if CDN fails
 * 
 * **Usage:**
 * This module is automatically discovered by Hilt. Just inject dependencies:
 * ```kotlin
 * @Inject lateinit var manifestService: ManifestService
 * 
 * suspend fun syncManifest() {
 *     val response = manifestService.getManifest()
 *     // ...
 * }
 * ```
 * 
 * @see ManifestService
 * @see Retrofit
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    /**
     * Base URL for jsDelivr CDN (primary).
     * 
     * Benefits:
     * - Faster global delivery
     * - No rate limits
     * - Free forever
     * 
     * Format: `https://cdn.jsdelivr.net/gh/{owner}/{repo}@{branch}/`
     */
    private const val JSDELIVR_BASE_URL = "https://cdn.jsdelivr.net/gh/avinaxhroy/Vanderwaals@main/"
    
    /**
     * Base URL for GitHub raw content (fallback).
     * 
     * Format: `https://raw.githubusercontent.com/{owner}/{repo}/{branch}/`
     */
    private const val GITHUB_RAW_BASE_URL = "https://raw.githubusercontent.com/avinaxhroy/Vanderwaals/main/"
    
    /**
     * Path to manifest file in repository.
     * 
     * This path is appended to the base URL to construct the full manifest URL.
     */
    private const val MANIFEST_PATH = "app/src/main/assets/manifest.json"
    
    /**
     * HTTP cache size in bytes (10 MB).
     * 
     * Caches manifest and thumbnail responses for offline access.
     */
    private const val CACHE_SIZE = 10L * 1024 * 1024 // 10 MB
    
    /**
     * Connection timeout in seconds.
     */
    private const val CONNECT_TIMEOUT = 30L
    
    /**
     * Read timeout in seconds.
     * INCREASED to 5 minutes for large manifest download (65MB).
     * On 3G (~1 Mbps): 65MB takes ~8 minutes
     * On 4G (~10 Mbps): 65MB takes ~1 minute
     * On WiFi (~50 Mbps): 65MB takes ~10 seconds
     */
    private const val READ_TIMEOUT = 300L  // 5 minutes
    
    /**
     * Write timeout in seconds.
     */
    private const val WRITE_TIMEOUT = 60L
    
    /**
     * Provides jsDelivr CDN base URL.
     */
    @Provides
    @Singleton
    @JsDelivrBaseUrl
    fun provideJsDelivrBaseUrl(): String = JSDELIVR_BASE_URL
    
    /**
     * Provides GitHub raw base URL (fallback).
     */
    @Provides
    @Singleton
    @GitHubRawBaseUrl
    fun provideGitHubRawBaseUrl(): String = GITHUB_RAW_BASE_URL
    
    /**
     * Provides manifest URL based on build configuration.
     * 
     * - Debug builds with USE_LOCAL_MANIFEST=true: Use local manifest from assets (for testing)
     * - Release builds: Use GitHub raw content URL (no 50MB size limit like jsDelivr)
     * 
     * This allows developers to test with local data while production
     * builds automatically fetch the latest curated manifest from GitHub.
     * 
     * @return Full URL to manifest.json
     */
    @Provides
    @Singleton
    fun provideManifestUrl(@GitHubRawBaseUrl githubRawUrl: String): String {
        return if (BuildConfig.DEBUG && BuildConfig.USE_LOCAL_MANIFEST) {
            // Local manifest from assets (debug only)
            "file:///android_asset/manifest.json"
        } else {
            // Remote manifest from GitHub raw content (no size limits)
            "$githubRawUrl$MANIFEST_PATH"
        }
    }
    
    /**
     * Provides HTTP cache directory.
     * 
     * Creates a cache directory in the app's cache folder for
     * storing HTTP responses (manifest, thumbnails).
     * 
     * @param context Application context
     * @return Cache file directory
     */
    @Provides
    @Singleton
    fun provideHttpCacheDir(@ApplicationContext context: Context): File {
        return File(context.cacheDir, "http_cache")
    }
    
    /**
     * Provides HTTP cache for OkHttp.
     * 
     * Enables offline access to previously downloaded manifest.
     * Cache is automatically managed by OkHttp based on response headers.
     * 
     * @param cacheDir Cache directory
     * @return Configured cache instance
     */
    @Provides
    @Singleton
    fun provideHttpCache(cacheDir: File): Cache {
        return Cache(cacheDir, CACHE_SIZE)
    }
    
    /**
     * Provides Gson for JSON serialization/deserialization.
     * 
     * Configured with lenient parsing for robustness.
     * 
     * @return Configured Gson instance
     */
    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .create()
    }
    
    /**
     * Provides HTTP logging interceptor.
     * 
     * Logs HTTP requests and responses in debug builds only.
     * Level:
     * - Debug: BODY (full request/response)
     * - Release: NONE (no logging for production)
     * 
     * @return Configured logging interceptor
     */
    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
    }
    
    /**
     * Provides download progress interceptor for tracking real-time download progress.
     * 
     * @param downloadProgressManager Manager for tracking download state
     * @return Progress tracking interceptor
     */
    @Provides
    @Singleton
    fun provideDownloadProgressInterceptor(
        downloadProgressManager: me.avinas.vanderwaals.network.DownloadProgressManager
    ): me.avinas.vanderwaals.network.DownloadProgressInterceptor {
        return me.avinas.vanderwaals.network.DownloadProgressInterceptor { bytesRead, totalBytes, isDone ->
            downloadProgressManager.updateProgress(bytesRead, totalBytes, isDone)
        }
    }
    
    /**
     * Provides configured OkHttpClient.
     * 
     * Configuration:
     * - Connection timeout: 30 seconds
     * - Read timeout: 5 minutes (large manifest file ~65MB)
     * - Write timeout: 60 seconds
     * - HTTP cache: 10 MB for offline access
     * - Download progress tracking: Real-time bytes downloaded
     * - Logging: Full body in debug, none in release
     * - Connection pooling: Default (5 connections)
     * 
     * @param cache HTTP cache
     * @param loggingInterceptor Logging interceptor
     * @param downloadProgressInterceptor Progress tracking interceptor
     * @return Configured OkHttp client
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(
        cache: Cache,
        loggingInterceptor: HttpLoggingInterceptor,
        downloadProgressInterceptor: me.avinas.vanderwaals.network.DownloadProgressInterceptor
    ): OkHttpClient {
        // Configure Dispatcher for higher concurrency
        val dispatcher = okhttp3.Dispatcher().apply {
            maxRequestsPerHost = 20 // Increased from default 5 to support parallel chunks (4 chunks * 3 files = 12)
        }
        
        // Configure ConnectionPool for better reuse
        val connectionPool = okhttp3.ConnectionPool(15, 5, TimeUnit.MINUTES)

        return OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectionPool(connectionPool)
            .protocols(listOf(okhttp3.Protocol.HTTP_2, okhttp3.Protocol.HTTP_1_1)) // Explicitly enable HTTP/2
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            .cache(cache)
            .addNetworkInterceptor(downloadProgressInterceptor) // Track download progress
            .addInterceptor(loggingInterceptor)
            .build()
    }
    

    
    /**
     * Provides Retrofit instance configured for manifest downloads.
     * 
     * Uses GitHub raw content URL as base URL for:
     * - No size limits (jsDelivr has 50MB limit)
     * - Direct GitHub CDN delivery
     * - Reliable for large repositories
     * 
     * @param okHttpClient Configured OkHttp client
     * @param gson Gson for JSON parsing
     * @param baseUrl GitHub raw base URL
     * @return Configured Retrofit instance
     */
    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        gson: Gson,
        @GitHubRawBaseUrl baseUrl: String
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }
    
    /**
     * Provides ManifestService from Retrofit.
     * 
     * Creates a Retrofit implementation of the ManifestService interface
     * for downloading the wallpaper manifest.
     * 
     * @param retrofit Configured Retrofit instance
     * @return ManifestService implementation
     */
    @Provides
    @Singleton
    fun provideManifestService(retrofit: Retrofit): ManifestService {
        return retrofit.create(ManifestService::class.java)
    }
    
    /**
     * Provides BingApiService for fetching Bing daily wallpapers.
     * 
     * Separate Retrofit instance for Bing API (different base URL).
     * 
     * @param okHttpClient Configured OkHttp client
     * @param gson Gson for JSON parsing
     * @return BingApiService implementation
     */
    @Provides
    @Singleton
    fun provideBingApiService(
        okHttpClient: OkHttpClient,
        gson: Gson
    ): me.avinas.vanderwaals.network.BingApiService {
        val bingRetrofit = Retrofit.Builder()
            .baseUrl("https://www.bing.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
        
        return bingRetrofit.create(me.avinas.vanderwaals.network.BingApiService::class.java)
    }
    
    /**
     * Provides BingArchiveService for fetching wallpapers from Bing Wallpaper Archive.
     * 
     * Uses npanuhin's official API endpoints at bing.npanuhin.me for accessing
     * 10,000+ historical Bing wallpapers per region (Tier 2 content).
     * 
     * **Archive Details**:
     * - Repository: https://github.com/npanuhin/Bing-Wallpaper-Archive
     * - API Base: https://bing.npanuhin.me/
     * - API Format: {country}/{language}.json (e.g., US/en.json, ROW/en.json)
     * - Year Format: {country}/{language}.{year}.json (e.g., US/en.2024.json)
     * - Wallpapers: 10,000+ per region in UHD (3840×2160)
     * - Multi-region: US, GB, CA, FR, DE, IT, ES, IN, CN, JP, BR, ROW
     * - Updated: Daily via automated GitHub Actions workflow
     * 
     * **Endpoints**:
     * 1. Daily wallpaper API: https://www.bing.com/HPImageArchive.aspx (Bing's official API)
     * 2. Full archive: https://bing.npanuhin.me/{country}/{language}.json (2-5 MB per region)
     * 3. Year-based archive: https://bing.npanuhin.me/{country}/{language}.{year}.json (100-500 KB)
     * 
     * **Benefits of npanuhin's API**:
     * - Direct access without CDN delays
     * - Year-based APIs for bandwidth efficiency
     * - Complete metadata (title, caption, subtitle, description, copyright)
     * - Multiple regions and languages
     * - Daily automated updates
     * 
     * @param okHttpClient Configured OkHttp client with timeouts
     * @param gson Gson for JSON parsing
     * @return BingArchiveService implementation with multi-endpoint support
     */
    @Provides
    @Singleton
    fun provideBingArchiveService(
        okHttpClient: OkHttpClient,
        gson: Gson
    ): me.avinas.vanderwaals.network.BingArchiveService {
        // Retrofit instance for Bing's official daily wallpaper API
        val bingDailyRetrofit = Retrofit.Builder()
            .baseUrl("https://www.bing.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
        
        // Retrofit instance for npanuhin's Bing Wallpaper Archive API
        val bingArchiveRetrofit = Retrofit.Builder()
            .baseUrl("https://bing.npanuhin.me/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
        
        // Create a composite service that delegates to appropriate Retrofit instance
        return object : me.avinas.vanderwaals.network.BingArchiveService {
            private val dailyService = bingDailyRetrofit.create(me.avinas.vanderwaals.network.BingArchiveService::class.java)
            private val archiveService = bingArchiveRetrofit.create(me.avinas.vanderwaals.network.BingArchiveService::class.java)
            
            override suspend fun getDailyWallpaper(
                format: String,
                idx: Int,
                count: Int,
                market: String
            ) = dailyService.getDailyWallpaper(format, idx, count, market)
            
            override suspend fun getArchiveManifest(
                country: String,
                language: String
            ) = archiveService.getArchiveManifest(country, language)
            
            override suspend fun getArchiveManifestYear(
                country: String,
                language: String,
                year: Int
            ) = archiveService.getArchiveManifestYear(country, language, year)
        }
    }
}
