package me.avinas.vanderwaals.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks network connectivity state and notifies when connectivity is restored.
 * 
 * This is CRITICAL for fixing the issue where wallpaper rotation continues to use
 * cached/old wallpapers even after internet connectivity is restored.
 * 
 * HOW IT WORKS:
 * 1. Monitors network state changes via ConnectivityManager callbacks
 * 2. When network goes offline, sets wasOffline flag to true
 * 3. When network comes back online (and wasOffline is true), triggers a callback
 * 4. The callback can be used to cancel pending retry work and refresh wallpaper cache
 * 
 * INTEGRATION:
 * - Initialized in VanderwaalsApplication or via Hilt dependency injection
 * - WallpaperChangeWorker checks isOnline() before attempting downloads
 * - WorkScheduler listens for network restoration events
 * 
 * @property context Application context for accessing ConnectivityManager
 */
@Singleton
class NetworkStateTracker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager = 
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    private val _isOnline = MutableStateFlow(checkCurrentConnectivity())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()
    
    private val _wasOffline = MutableStateFlow(false)
    
    /**
     * Callback invoked when network connectivity is restored after being offline.
     * This is the key hook for refreshing wallpaper downloads.
     */
    var onNetworkRestored: (() -> Unit)? = null
    
    /**
     * Timestamp of last successful wallpaper download.
     * Used to determine if we need to refresh after network restoration.
     */
    private var lastSuccessfulDownloadTime: Long = 0L
    
    /**
     * Whether we were using cached wallpapers due to being offline.
     * When true and network is restored, we should attempt fresh downloads.
     */
    private val _isInOfflineMode = MutableStateFlow(false)
    val isInOfflineMode: StateFlow<Boolean> = _isInOfflineMode.asStateFlow()
    
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(TAG, "Network AVAILABLE")
            val wasOfflineBefore = _wasOffline.value
            _isOnline.value = true
            
            // If we were offline and now online, trigger restoration callback
            if (wasOfflineBefore || _isInOfflineMode.value) {
                Log.d(TAG, "🌐 Network RESTORED after being offline!")
                Log.d(TAG, "  Was offline: $wasOfflineBefore")
                Log.d(TAG, "  In offline mode: ${_isInOfflineMode.value}")
                
                // Reset offline tracking
                _wasOffline.value = false
                _isInOfflineMode.value = false
                
                // Notify listeners that network is restored
                scope.launch {
                    onNetworkRestored?.invoke()
                }
            }
        }
        
        override fun onLost(network: Network) {
            Log.d(TAG, "Network LOST")
            // Check if we still have other networks available
            if (!checkCurrentConnectivity()) {
                _isOnline.value = false
                _wasOffline.value = true
                Log.d(TAG, "📴 Device is now OFFLINE - setting wasOffline=true")
            }
        }
        
        override fun onCapabilitiesChanged(
            network: Network, 
            networkCapabilities: NetworkCapabilities
        ) {
            val hasInternet = networkCapabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET
            ) && networkCapabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_VALIDATED
            )
            
            if (hasInternet && _wasOffline.value) {
                Log.d(TAG, "🌐 Network capabilities restored with validated internet!")
                _isOnline.value = true
                _wasOffline.value = false
                
                if (_isInOfflineMode.value) {
                    _isInOfflineMode.value = false
                    scope.launch {
                        onNetworkRestored?.invoke()
                    }
                }
            }
        }
    }
    
    init {
        registerNetworkCallback()
    }
    
    private fun registerNetworkCallback() {
        try {
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
            Log.d(TAG, "Network callback registered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
        }
    }
    
    /**
     * Checks if device currently has internet connectivity.
     */
    fun checkCurrentConnectivity(): Boolean {
        return try {
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            
            capabilities != null &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking connectivity", e)
            false
        }
    }
    
    /**
     * Call this when a download fails due to network issues.
     * Sets the offline mode flag so we know to refresh when network returns.
     */
    fun markAsOfflineMode() {
        Log.d(TAG, "📴 Entering OFFLINE MODE - will refresh when network returns")
        _isInOfflineMode.value = true
        _wasOffline.value = true
    }
    
    /**
     * Call this when a download succeeds.
     * Records the timestamp for tracking cache freshness.
     */
    fun markSuccessfulDownload() {
        lastSuccessfulDownloadTime = System.currentTimeMillis()
        _isInOfflineMode.value = false
        Log.d(TAG, "✅ Download successful at $lastSuccessfulDownloadTime")
    }
    
    /**
     * Checks if we should attempt a fresh download.
     * Returns true if:
     * - Device is online AND
     * - We were previously in offline mode OR
     * - Last download was more than 15 minutes ago
     */
    fun shouldAttemptFreshDownload(): Boolean {
        if (!_isOnline.value) return false
        
        val timeSinceLastDownload = System.currentTimeMillis() - lastSuccessfulDownloadTime
        val staleThreshold = 15 * 60 * 1000L // 15 minutes
        
        return !_isInOfflineMode.value || timeSinceLastDownload > staleThreshold
    }
    
    /**
     * Cleans up resources when no longer needed.
     */
    fun unregister() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            Log.d(TAG, "Network callback unregistered")
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering network callback", e)
        }
    }
    
    companion object {
        private const val TAG = "NetworkStateTracker"
    }
}
