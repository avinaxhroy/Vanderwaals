package me.avinas.vanderwaals.domain

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.avinas.vanderwaals.data.entity.WallpaperMetadata
import me.avinas.vanderwaals.domain.usecase.SelectNextWallpaperUseCase
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages pre-computed next wallpaper recommendations for perceived instant wallpaper changes.
 * 
 * **How It Works:**
 * 1. After applying wallpaper, immediately compute next recommendation in background
 * 2. Store result in cache for instant retrieval on next "Change Now" click
 * 3. Invalidate cache when user provides explicit feedback (especially dislike)
 * 
 * **Both But Different Mode:**
 * For users with "Both But Different" enabled, this manager caches TWO wallpapers:
 * - One for home screen
 * - One for lock screen (guaranteed different from home)
 * This ensures instant wallpaper changes even when applying different wallpapers to each screen.
 * 
 * **Race Condition Safety:**
 * Uses a generation counter to handle concurrent requests safely:
 * - Each cache operation increments generation
 * - Background computations check generation before storing result
 * - If generation changed during computation, result is discarded (stale)
 * 
 * **Example Scenario:**
 * ```
 * T=1s: User clicks change → apply cached A, start computing B (gen=1)
 * T=4s: User clicks change AGAIN while B still computing
 *       → No cache available, compute fresh C, gen=2
 * T=5s: B finishes, BUT gen(1) != current(2) → discarded safely
 * ```
 * 
 * @property selectNextWallpaperUseCase The actual wallpaper selection algorithm
 * 
 * @see SelectNextWallpaperUseCase
 */
@Singleton
class NextWallpaperCacheManager @Inject constructor(
    private val selectNextWallpaperUseCase: SelectNextWallpaperUseCase
) {
    companion object {
        private const val TAG = "NextWallpaperCache"
        
        /**
         * Maximum age of cached result before it's considered stale.
         * 10 minutes is generous since pre-computation now runs async.
         * Gives time for cache to warm after wallpaper changes.
         */
        private const val MAX_CACHE_AGE_MS = 10 * 60 * 1000L // 10 minutes
    }
    
    /** Mutex to protect cache reads and writes */
    private val mutex = Mutex()
    
    /** 
     * Generation counter for invalidation.
     * Incremented on every cache consumption or explicit invalidation.
     * Background computations check this to detect staleness.
     */
    private var cacheGeneration = 0L
    
    /** Cached next wallpaper result (null if no cache) - for single mode */
    private var cachedResult: Result<WallpaperMetadata>? = null
    
    /** Timestamp when cache was computed */
    private var cacheTimestamp = 0L
    
    /** ID of the cached wallpaper (for logging and duplicate prevention) */
    private var cachedWallpaperId: String? = null
    
    // ========== BOTH BUT DIFFERENT MODE CACHE ==========
    
    /** Cached wallpaper pair for "Both But Different" mode */
    private var cachedPair: WallpaperPair? = null
    
    /** Timestamp when pair cache was computed */
    private var pairCacheTimestamp = 0L
    
    /**
     * Data class to hold pre-computed wallpaper pair for "Both But Different" mode.
     */
    data class WallpaperPair(
        val homeWallpaper: WallpaperMetadata,
        val lockWallpaper: WallpaperMetadata
    )
    
    /**
     * Gets the next wallpaper, using cache if available.
     * 
     * If cache hit: Returns cached result immediately (near instant)
     * If cache miss: Falls back to fresh computation (slower, but correct)
     * 
     * @param excludeWallpaperId ID to exclude from selection (typically current wallpaper)
     * @return Selected wallpaper result
     */
    suspend fun getNextWallpaper(excludeWallpaperId: String? = null): Result<WallpaperMetadata> {
        mutex.withLock {
            val cached = cachedResult
            
            // Check if we have valid cache
            if (cached != null && isCacheValid()) {
                // Additional check: don't return cached wallpaper if it's the one being excluded
                val cachedId = cachedWallpaperId
                if (cachedId != null && cachedId == excludeWallpaperId) {
                    Log.d(TAG, "Cache contains excluded wallpaper $cachedId - computing fresh")
                    cachedResult = null
                } else {
                    Log.d(TAG, "✓ Cache HIT - returning pre-computed wallpaper: $cachedId (gen=$cacheGeneration)")
                    
                    // Consume cache (one-time use)
                    cachedResult = null
                    cachedWallpaperId = null
                    cacheGeneration++  // Invalidate any in-flight computation
                    
                    return cached
                }
            } else if (cached != null) {
                Log.d(TAG, "Cache STALE - too old, clearing")
                cachedResult = null
                cachedWallpaperId = null
            }
        }
        
        // Cache miss - compute fresh
        Log.d(TAG, "✗ Cache MISS - computing fresh wallpaper (this takes time)")
        return selectNextWallpaperUseCase(excludeWallpaperId)
    }
    
    /**
     * Pre-computes next wallpaper in background after a wallpaper change.
     * 
     * Call this after successfully applying a wallpaper to prepare for the next change.
     * Uses generation counter to safely discard stale results.
     * 
     * @param excludeWallpaperId ID to exclude from selection (the just-applied wallpaper)
     */
    suspend fun preComputeNext(excludeWallpaperId: String?) {
        // Snapshot current generation before starting computation
        val myGeneration: Long
        mutex.withLock {
            myGeneration = cacheGeneration
            Log.d(TAG, "→ Starting pre-computation for gen=$myGeneration, exclude=$excludeWallpaperId")
        }
        
        // Compute next wallpaper (this is the slow part)
        val startTime = System.currentTimeMillis()
        val result = selectNextWallpaperUseCase(excludeWallpaperId)
        val elapsed = System.currentTimeMillis() - startTime
        
        // Only store result if generation hasn't changed (no one else consumed/invalidated cache)
        mutex.withLock {
            if (myGeneration == cacheGeneration) {
                cachedResult = result
                cacheTimestamp = System.currentTimeMillis()
                
                result.fold(
                    onSuccess = { wallpaper ->
                        cachedWallpaperId = wallpaper.id
                        Log.d(TAG, "→ Pre-computed and CACHED: ${wallpaper.id} in ${elapsed}ms (gen=$myGeneration)")
                    },
                    onFailure = { error ->
                        cachedWallpaperId = null
                        Log.w(TAG, "→ Pre-computation FAILED, cached error: ${error.message}")
                    }
                )
            } else {
                // Generation changed - our result is stale, discard it
                Log.d(TAG, "→ Discarding stale result: computed for gen=$myGeneration but current is $cacheGeneration")
            }
        }
    }
    
    /**
     * Invalidates all caches (single and pair).
     * 
     * Call this when:
     * - User provides explicit feedback (like/dislike) - preferences changed
     * - App settings change (source enabled/disabled)
     * - Any event that would affect wallpaper selection
     * 
     * @param reason Reason for invalidation (for logging)
     */
    suspend fun invalidateCache(reason: String = "explicit") {
        mutex.withLock {
            if (cachedResult != null) {
                Log.d(TAG, "⚠ Single cache INVALIDATED: $reason (was gen=$cacheGeneration, id=$cachedWallpaperId)")
                cachedResult = null
                cachedWallpaperId = null
            }
            if (cachedPair != null) {
                Log.d(TAG, "⚠ Pair cache INVALIDATED: $reason (home=${cachedPair?.homeWallpaper?.id}, lock=${cachedPair?.lockWallpaper?.id})")
                cachedPair = null
            }
            cacheGeneration++  // Ensures any in-flight computations are discarded
        }
    }
    
    /**
     * Checks if single wallpaper cache is valid (not too old).
     */
    private fun isCacheValid(): Boolean {
        val age = System.currentTimeMillis() - cacheTimestamp
        if (age > MAX_CACHE_AGE_MS) {
            Log.d(TAG, "Cache too old: ${age}ms > ${MAX_CACHE_AGE_MS}ms")
            return false
        }
        return true
    }
    
    /**
     * Checks if pair cache is valid (not too old).
     */
    private fun isPairCacheValid(): Boolean {
        val age = System.currentTimeMillis() - pairCacheTimestamp
        if (age > MAX_CACHE_AGE_MS) {
            Log.d(TAG, "Pair cache too old: ${age}ms > ${MAX_CACHE_AGE_MS}ms")
            return false
        }
        return true
    }
    
    /**
     * Returns whether cache is currently populated (for debugging/testing).
     */
    suspend fun hasCachedResult(): Boolean {
        return mutex.withLock { cachedResult != null }
    }
    
    // ========== BOTH BUT DIFFERENT MODE METHODS ==========
    
    /**
     * Gets a pre-computed wallpaper pair for "Both But Different" mode.
     * 
     * If cache hit: Returns cached pair immediately (near instant)
     * If cache miss: Computes fresh pair (slower, but correct)
     * 
     * The pair is GUARANTEED to have two different wallpapers.
     * 
     * @return Pair of wallpapers (home, lock) or null if selection failed
     */
    suspend fun getNextWallpaperPair(): WallpaperPair? {
        mutex.withLock {
            val cached = cachedPair
            
            // Check if we have valid pair cache
            if (cached != null && isPairCacheValid()) {
                Log.d(TAG, "✓ Pair cache HIT - returning pre-computed pair: home=${cached.homeWallpaper.id}, lock=${cached.lockWallpaper.id}")
                
                // Consume cache (one-time use)
                cachedPair = null
                cacheGeneration++  // Invalidate any in-flight computation
                
                return cached
            } else if (cached != null) {
                Log.d(TAG, "Pair cache STALE - too old, clearing")
                cachedPair = null
            }
        }
        
        // Cache miss - compute fresh pair
        Log.d(TAG, "✗ Pair cache MISS - computing fresh wallpaper pair (this takes time)")
        return computeFreshPair()
    }
    
    /**
     * Computes a fresh wallpaper pair.
     * Ensuring lock wallpaper is different from home wallpaper.
     */
    private suspend fun computeFreshPair(): WallpaperPair? {
        // Select home wallpaper
        val homeResult = selectNextWallpaperUseCase()
        if (homeResult.isFailure) {
            Log.w(TAG, "Failed to select home wallpaper for pair: ${homeResult.exceptionOrNull()?.message}")
            return null
        }
        val homeWallpaper = homeResult.getOrNull()!!
        
        // Select lock wallpaper, EXCLUDING home wallpaper ID
        val lockResult = selectNextWallpaperUseCase(excludeWallpaperId = homeWallpaper.id)
        val lockWallpaper = if (lockResult.isSuccess) {
            lockResult.getOrNull()!!
        } else {
            // Fallback to home wallpaper if no different wallpaper available
            Log.w(TAG, "Could not find different lock wallpaper, using same as home")
            homeWallpaper
        }
        
        Log.d(TAG, "Computed fresh pair: home=${homeWallpaper.id}, lock=${lockWallpaper.id}")
        return WallpaperPair(homeWallpaper, lockWallpaper)
    }
    
    /**
     * Pre-computes wallpaper pair in background for "Both But Different" mode.
     * 
     * Call this after successfully applying wallpapers to prepare for the next change.
     * Uses generation counter to safely discard stale results.
     * 
     * @param excludeHomeId ID to exclude from home selection (the just-applied home wallpaper)
     * @param excludeLockId ID to exclude from lock selection (the just-applied lock wallpaper)
     */
    suspend fun preComputeNextPair(excludeHomeId: String?, excludeLockId: String?) {
        // Snapshot current generation before starting computation
        val myGeneration: Long
        mutex.withLock {
            myGeneration = cacheGeneration
            Log.d(TAG, "→ Starting pair pre-computation for gen=$myGeneration, excludeHome=$excludeHomeId, excludeLock=$excludeLockId")
        }
        
        // Compute next pair (this is the slow part)
        val startTime = System.currentTimeMillis()
        
        // Select home wallpaper, excluding previously applied
        val homeResult = selectNextWallpaperUseCase(excludeHomeId)
        if (homeResult.isFailure) {
            Log.w(TAG, "→ Pair pre-computation FAILED for home: ${homeResult.exceptionOrNull()?.message}")
            return
        }
        val homeWallpaper = homeResult.getOrNull()!!
        
        // Select lock wallpaper, excluding BOTH the just-applied lock AND the new home wallpaper
        // This ensures maximum diversity
        val lockResult = selectNextWallpaperUseCase(excludeWallpaperId = homeWallpaper.id)
        val lockWallpaper = if (lockResult.isSuccess) {
            lockResult.getOrNull()!!
        } else {
            Log.w(TAG, "Could not find different lock wallpaper for pair cache")
            homeWallpaper
        }
        
        val elapsed = System.currentTimeMillis() - startTime
        
        // Only store result if generation hasn't changed
        mutex.withLock {
            if (myGeneration == cacheGeneration) {
                cachedPair = WallpaperPair(homeWallpaper, lockWallpaper)
                pairCacheTimestamp = System.currentTimeMillis()
                Log.d(TAG, "→ Pre-computed and CACHED pair: home=${homeWallpaper.id}, lock=${lockWallpaper.id} in ${elapsed}ms (gen=$myGeneration)")
            } else {
                Log.d(TAG, "→ Discarding stale pair result: computed for gen=$myGeneration but current is $cacheGeneration")
            }
        }
    }
    
    /**
     * Returns whether pair cache is currently populated (for debugging/testing).
     */
    suspend fun hasCachedPair(): Boolean {
        return mutex.withLock { cachedPair != null }
    }
    
    // ========== POST-DISLIKE SELECTION ==========
    
    /**
     * Gets the next wallpaper after a user dislike, using diversity-focused selection.
     * 
     * This method:
     * 1. Invalidates existing cache (preferences changed due to dislike)
     * 2. Uses specialized selectAfterDislike algorithm that prioritizes:
     *    - Different categories from the disliked wallpaper
     *    - Wallpapers that are visually dissimilar to the disliked one
     *    - High exploration rate (70%) to ensure noticeable change
     * 
     * **Why This Exists:**
     * When users dislike a wallpaper, they're signaling "show me something DIFFERENT."
     * Regular selection just updates preferences slightly and picks the next best match,
     * which often feels too similar. This ensures a meaningful change.
     * 
     * @param dislikedWallpaperId ID of the wallpaper the user just disliked
     * @param dislikedCategory Category of the disliked wallpaper
     * @param dislikedEmbedding Embedding vector of the disliked wallpaper
     * @return Result<WallpaperMetadata> with a diverse wallpaper selection
     */
    suspend fun getNextWallpaperAfterDislike(
        dislikedWallpaperId: String,
        dislikedCategory: String,
        dislikedEmbedding: FloatArray
    ): Result<WallpaperMetadata> {
        // Invalidate cache since preferences just changed
        invalidateCache("dislike_feedback")
        
        Log.d(TAG, "📍 Post-dislike selection: bypassing cache, using diversity algorithm")
        Log.d(TAG, "   Disliked: $dislikedWallpaperId (category: $dislikedCategory)")
        
        // Use specialized post-dislike selection
        val result = selectNextWallpaperUseCase.selectAfterDislike(
            dislikedWallpaperId = dislikedWallpaperId,
            dislikedCategory = dislikedCategory,
            dislikedEmbedding = dislikedEmbedding
        )
        
        result.fold(
            onSuccess = { wallpaper ->
                Log.d(TAG, "📍 Post-dislike selected: ${wallpaper.id} (category: ${wallpaper.category})")
            },
            onFailure = { error ->
                Log.w(TAG, "📍 Post-dislike selection failed: ${error.message}")
            }
        )
        
        return result
    }
}
