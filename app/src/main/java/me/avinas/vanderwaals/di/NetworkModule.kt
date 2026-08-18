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
 * Hilt module providing network singletons: Retrofit (jsDelivr CDN primary,
 * GitHub raw fallback), OkHttpClient with 10 MB HTTP cache, and ManifestService.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    /**
     * Base URL for jsDelivr CDN (primary).
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
     * Path to the manifest file in the repository.
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
     * This is the max inactivity between read calls (not total download time),
     * so a slow-but-progressing download still succeeds; only a stalled
     * connection fails. 60s is plenty for the ~10-15MB manifest and UHD images,
     * and keeps the onboarding sync from hanging for minutes (which triggers
     * Google Play's "unresponsive app" Broken Functionality rejection).
     */
    private const val READ_TIMEOUT = 60L
    
    /**
     * Write timeout in seconds.
     */
    private const val WRITE_TIMEOUT = 60L
    
    @Provides
    @Singleton
    @JsDelivrBaseUrl
    fun provideJsDelivrBaseUrl(): String = JSDELIVR_BASE_URL
    
    @Provides
    @Singleton
    @GitHubRawBaseUrl
    fun provideGitHubRawBaseUrl(): String = GITHUB_RAW_BASE_URL
    
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
    
    @Provides
    @Singleton
    fun provideHttpCacheDir(@ApplicationContext context: Context): File {
        return File(context.cacheDir, "http_cache")
    }
    
    /**
     * Enables offline access to previously downloaded manifests; cache
     * eviction is handled by OkHttp based on response headers.
     */
    @Provides
    @Singleton
    fun provideHttpCache(cacheDir: File): Cache {
        return Cache(cacheDir, CACHE_SIZE)
    }
    
    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .create()
    }
    
    /**
     * HEADERS level avoids loading the response body into memory as a
     * String — BODY would exceed heap limits on large downloads (e.g. the
     * ~65 MB manifest).
     */
    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            redactHeader("Authorization")
            redactHeader("Cookie")
            redactHeader("Set-Cookie")
            redactHeader("Proxy-Authorization")
            redactHeader("CF-Access-Client-Id")
            redactHeader("CF-Access-Client-Secret")
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.HEADERS
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
    }
    
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
     * User-Agent interceptor to bypass Cloudflare bot detection.
     * 
     * Cloudflare blocks requests without a valid User-Agent, returning empty responses.
     * This mimics a modern Chrome browser to ensure wallpaper downloads from
     * Cloudflare-protected servers (like bing.npanuhin.me) succeed.
     */
    @Provides
    @Singleton
    fun provideUserAgentInterceptor(): okhttp3.Interceptor {
        return okhttp3.Interceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Accept", "image/webp,image/apng,image/*,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            chain.proceed(request)
        }
    }
    
    @Provides
    @Singleton
    fun provideOkHttpClient(
        cache: Cache,
        loggingInterceptor: HttpLoggingInterceptor,
        downloadProgressInterceptor: me.avinas.vanderwaals.network.DownloadProgressInterceptor,
        userAgentInterceptor: okhttp3.Interceptor
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
            .addInterceptor(userAgentInterceptor)
            .addNetworkInterceptor(downloadProgressInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()
    }
    

    
    /**
     * Uses GitHub raw content as base URL (avoids jsDelivr's 50 MB size limit).
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
    
    @Provides
    @Singleton
    fun provideManifestService(retrofit: Retrofit): ManifestService {
        return retrofit.create(ManifestService::class.java)
    }
    
    /**
     * Separate Retrofit instance — the Bing API uses a different base URL
     * from the GitHub manifest client.
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
     * Separate Retrofit instance for the dedicated Vanderwaals API
     * (`https://vanderwaalsapi.2626688.xyz/`), which serves the curated
     * wallpaper catalog (`cat/lite.json`, `cat/full.json`) in the same
     * ManifestDto format used by the GitHub and Bing sources.
     */
    @Provides
    @Singleton
    fun provideVanderwaalsCollectionService(
        okHttpClient: OkHttpClient,
        gson: Gson
    ): me.avinas.vanderwaals.network.VanderwaalsCollectionService {
        val vanderwaalsRetrofit = Retrofit.Builder()
            .baseUrl("https://vanderwaalsapi.2626688.xyz/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

        return vanderwaalsRetrofit.create(me.avinas.vanderwaals.network.VanderwaalsCollectionService::class.java)
    }
    
    /**
     * Wallpapers from Bing's official daily API and npanuhin's Bing
     * Wallpaper Archive (bing.npanuhin.me, 10,000+ historical wallpapers
     * per region).
     *
     * Archive endpoints: `{country}/{language}.json` (full) and
     * `{country}/{language}.{year}.json` (per-year); e.g. US/en.json,
     * US/en.2024.json. Regions: US, GB, CA, FR, DE, IT, ES, IN, CN, JP, BR, ROW.
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
