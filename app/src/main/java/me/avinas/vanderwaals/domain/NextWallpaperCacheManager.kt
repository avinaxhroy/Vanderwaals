package me.avinas.vanderwaals.domain

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import me.avinas.vanderwaals.domain.usecase.SelectNextWallpaperUseCase
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Caches the next wallpaper recommendation so rotations and changes feel instantaneous.
 */
@Singleton
class NextWallpaperCacheManager @Inject constructor(
    private val selectNextWallpaperUseCase: SelectNextWallpaperUseCase
) {
    companion object {
        private const val TAG = "NextWallpaperCache"
        private const val MAX_CACHE_AGE_MS = 10 * 60 * 1000L
    }
    
    private val mutex = Mutex()
    private var cacheGeneration = 0L
    private var cachedResult: Result<WallpaperMetadata>? = null
    private var cacheTimestamp = 0L
    private var cachedWallpaperId: String? = null
    
    private var cachedPair: WallpaperPair? = null
    private var pairCacheTimestamp = 0L
    
    data class WallpaperPair(
        val homeWallpaper: WallpaperMetadata,
        val lockWallpaper: WallpaperMetadata
    )
    
    suspend fun getNextWallpaper(excludeWallpaperId: String? = null): Result<WallpaperMetadata> {
        mutex.withLock {
            val cached = cachedResult
            
            if (cached != null && isCacheValid()) {
                val cachedId = cachedWallpaperId
                if (cachedId != null && cachedId == excludeWallpaperId) {
                    Log.d(TAG, "Cache contains excluded wallpaper $cachedId - computing fresh")
                    cachedResult = null
                } else {
                    Log.d(TAG, "Cache HIT: $cachedId (gen=$cacheGeneration)")
                    cachedResult = null
                    cachedWallpaperId = null
                    cacheGeneration++
                    return cached
                }
            } else if (cached != null) {
                Log.d(TAG, "Cache STALE - clearing")
                cachedResult = null
            }
        }
        
        return selectNextWallpaperUseCase(excludeWallpaperId)
    }
    
    suspend fun precomputeNextWallpaper(appliedWallpaperId: String? = null) {
        val generationSnapshot: Long
        mutex.withLock {
            generationSnapshot = cacheGeneration
        }
        
        Log.d(TAG, "Pre-computing next wallpaper in background (gen=$generationSnapshot)")
        
        val result = selectNextWallpaperUseCase(excludeWallpaperId = appliedWallpaperId)
        
        mutex.withLock {
            if (cacheGeneration == generationSnapshot) {
                if (result.isSuccess) {
                    val wallpaper = result.getOrNull()
                    cachedResult = result
                    cacheTimestamp = System.currentTimeMillis()
                    cachedWallpaperId = wallpaper?.id
                    Log.d(TAG, "Successfully cached next wallpaper: ${wallpaper?.id}")
                } else {
                    Log.w(TAG, "Pre-computation failed: ${result.exceptionOrNull()?.message}")
                    cachedResult = null
                    cachedWallpaperId = null
                }
            } else {
                Log.d(TAG, "Discarding pre-computed result: generation changed ($generationSnapshot -> $cacheGeneration)")
            }
        }
    }
    
    suspend fun invalidateCache(reason: String? = null) {
        mutex.withLock {
            cachedResult = null
            cachedWallpaperId = null
            cacheTimestamp = 0L
            cachedPair = null
            pairCacheTimestamp = 0L
            cacheGeneration++
            Log.d(TAG, "Cache invalidated${if (reason != null) " (reason: $reason)" else ""} (new gen=$cacheGeneration)")
        }
    }
    
    private fun isCacheValid(): Boolean {
        if (cachedResult == null) return false
        val age = System.currentTimeMillis() - cacheTimestamp
        return age < MAX_CACHE_AGE_MS
    }
    
    private fun isPairCacheValid(): Boolean {
        if (cachedPair == null) return false
        val age = System.currentTimeMillis() - pairCacheTimestamp
        return age < MAX_CACHE_AGE_MS
    }
    
    fun isCacheWarm(): Boolean {
        return cachedResult != null && isCacheValid()
    }
    
    fun isPairCacheWarm(): Boolean {
        return cachedPair != null && isPairCacheValid()
    }
    
    suspend fun getNextWallpaperPair(
        excludeHomeId: String? = null,
        excludeLockId: String? = null
    ): WallpaperPair? {
        mutex.withLock {
            val cached = cachedPair
            if (cached != null && isPairCacheValid()) {
                Log.d(TAG, "Pair cache HIT (home=${cached.homeWallpaper.id}, lock=${cached.lockWallpaper.id})")
                cachedPair = null
                cacheGeneration++
                return cached
            } else if (cached != null) {
                Log.d(TAG, "Pair cache STALE - clearing")
                cachedPair = null
            }
        }
        
        return computeFreshPair(excludeHomeId, excludeLockId)
    }
    
    private suspend fun computeFreshPair(
        excludeHomeId: String? = null,
        excludeLockId: String? = null
    ): WallpaperPair? {
        val homeResult = selectNextWallpaperUseCase(excludeWallpaperId = excludeHomeId)
        if (homeResult.isFailure) {
            return null
        }
        val homeWallpaper = homeResult.getOrNull() ?: return null
        
        val lockResult = selectNextWallpaperUseCase(excludeWallpaperId = homeWallpaper.id)
        val lockWallpaper = if (lockResult.isSuccess) {
            lockResult.getOrNull() ?: homeWallpaper
        } else {
            homeWallpaper
        }
        
        return WallpaperPair(homeWallpaper, lockWallpaper)
    }
    
    suspend fun precomputeNextWallpaperPair(
        appliedHomeId: String? = null,
        appliedLockId: String? = null
    ) {
        val generationSnapshot: Long
        mutex.withLock {
            generationSnapshot = cacheGeneration
        }
        
        Log.d(TAG, "Pre-computing wallpaper pair in background (gen=$generationSnapshot)")
        
        val homeResult = selectNextWallpaperUseCase(excludeWallpaperId = appliedHomeId)
        if (homeResult.isFailure) {
            Log.w(TAG, "Pair pre-computation failed on home wallpaper")
            return
        }
        val homeWallpaper = homeResult.getOrNull() ?: return
        
        val lockResult = selectNextWallpaperUseCase(excludeWallpaperId = homeWallpaper.id)
        val lockWallpaper = if (lockResult.isSuccess) {
            lockResult.getOrNull() ?: homeWallpaper
        } else {
            homeWallpaper
        }
        val pair = WallpaperPair(homeWallpaper, lockWallpaper)
        
        mutex.withLock {
            if (cacheGeneration == generationSnapshot) {
                cachedPair = pair
                pairCacheTimestamp = System.currentTimeMillis()
                Log.d(TAG, "Successfully cached wallpaper pair (home=${homeWallpaper.id}, lock=${lockWallpaper.id})")
            } else {
                Log.d(TAG, "Discarding pre-computed pair: generation changed ($generationSnapshot -> $cacheGeneration)")
            }
        }
    }
    
    suspend fun getNextWallpaperAfterDislike(
        dislikedWallpaperId: String,
        dislikedCategory: String,
        dislikedEmbedding: FloatArray
    ): Result<WallpaperMetadata> {
        invalidateCache()
        return selectNextWallpaperUseCase(excludeWallpaperId = dislikedWallpaperId)
    }
}
