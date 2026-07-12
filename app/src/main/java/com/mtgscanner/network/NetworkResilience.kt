package com.mtgscanner.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlin.math.pow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * NetworkStateManager: Reactive device network connectivity monitor.
 * Provides StateFlow<Boolean> that emits connectivity changes for UI responsiveness.
 * Checks for active network (WiFi, cellular, Ethernet) and handles exceptions gracefully.
 *
 * Connectivity Types Detected:
 * - TRANSPORT_CELLULAR: Mobile data (LTE, 5G, etc.)
 * - TRANSPORT_WIFI: WiFi networks
 * - TRANSPORT_ETHERNET: Ethernet (rare on phones, possible on tablets/docks)
 *
 * Usage:
 * - Inject into repository layer to switch between online/offline strategies
 * - Observe StateFlow in UI to show offline notice when disconnected
 * - Used by ScryfallRepositoryResilience to determine fallback chain priority
 *
 * @param context Android Context for ConnectivityManager access
 * @property isNetworkAvailable StateFlow<Boolean>: true if device has active network connection
 */
class NetworkStateManager(context: Context) {

    companion object {
        private const val TAG = "NetworkStateManager"
    }

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val _isNetworkAvailable = MutableStateFlow(isNetworkConnected())
    val isNetworkAvailable: StateFlow<Boolean> = _isNetworkAvailable.asStateFlow()

    /**
     * Check if device currently has active network connection.
     * Queries ConnectivityManager for active network and validates transport capabilities.
     * Returns true if any active network exists (WiFi, cellular, or Ethernet).
     *
     * Exception Safety: Returns false if ConnectivityManager query fails (e.g., permission denied).
     * All exceptions caught and logged to prevent crashes.
     *
     * @return Boolean: true if device has active network, false if disconnected or error occurred
     */
    fun isNetworkConnected(): Boolean {
        return try {
            val activeNetwork = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            
            capabilities != null && (
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            ).also {
                Log.d(TAG, "Network available: $it")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking network: ${e.message}", e)
            false
        }
    }

    /**
     * Update the network state in reactive StateFlow.
     * Called by connectivity listeners (e.g., ConnectivityManager.NetworkCallback) when connection changes.
     * Triggers StateFlow emission, which updates all observers (including UI recomposition).
     *
     * @param isConnected New connectivity state: true if connected, false if disconnected
     */
    fun updateNetworkState(isConnected: Boolean) {
        _isNetworkAvailable.value = isConnected
        Log.d(TAG, "Network state updated: $isConnected")
    }
}

/**
 * RetryPolicy: Exponential backoff retry strategy for network resilience.
 * Implements retry with exponential delay growth and ±10% jitter to avoid thundering herd.
 * Respects Scryfall rate limit (100 requests/sec) by capping maximum delay.
 *
 * Delay Formula:
 * baseDelay = initialDelayMs × (backoffMultiplier ^ attemptNumber)
 * cappedDelay = min(baseDelay, maxDelayMs)
 * finalDelay = cappedDelay + random(-jitterFactor%, +jitterFactor%)
 *
 * Example Delays (default params: init=100ms, multiplier=2x, max=5000ms):
 * - Attempt 0: ~100ms + jitter
 * - Attempt 1: ~200ms + jitter
 * - Attempt 2: ~400ms + jitter
 * - Attempt 3+: ~5000ms + jitter (capped)
 *
 * Total wait time across 3 attempts: ~100 + 200 + 400 = 700ms (plus jitter)
 *
 * @param maxRetries Maximum retry attempts after initial call (default 3; total calls = maxRetries + 1)
 * @param initialDelayMs Starting delay in milliseconds (default 100)
 * @param maxDelayMs Maximum delay cap to prevent excessive waits (default 5000 = 5 seconds)
 * @param backoffMultiplier Exponential multiplier per attempt (default 2.0)
 * @param jitterFactor Random jitter range ±% of delay (default 0.1 = ±10%)
 */
data class RetryPolicy(
    val maxRetries: Int = 3,
    val initialDelayMs: Long = 100L,
    val maxDelayMs: Long = 5000L,
    val backoffMultiplier: Float = 2f,
    val jitterFactor: Float = 0.1f  // ±10% random jitter to avoid thundering herd
) {
    /**
     * Calculate delay for a retry attempt with exponential backoff and jitter.
     * Exponential growth: delay = initialDelayMs × (backoffMultiplier ^ attemptNumber)
     * Capped at maxDelayMs to prevent excessive waits.
     * ±jitterFactor% random jitter prevents thundering herd when many clients retry simultaneously.
     *
     * @param attemptNumber Retry attempt number (0-based; 0 = first retry, 1 = second retry, etc.)
     * @return Delay in milliseconds before next retry attempt
     */
    fun getDelayMs(attemptNumber: Int): Long {
        val baseDelay = (initialDelayMs * backoffMultiplier.pow(attemptNumber)).toLong()
        val cappedDelay = baseDelay.coerceAtMost(maxDelayMs)
        
        // Add jitter: ±jitterFactor%
        val jitterRange = (cappedDelay * jitterFactor).toLong()
        val jitter = (Math.random() * 2 * jitterRange - jitterRange).toLong()
        
        return (cappedDelay + jitter).coerceIn(0, maxDelayMs)
    }
}

/**
 * Extension: Exponentiation for Float
 */
private fun Float.pow(exponent: Int): Float {
    return this.toDouble().pow(exponent.toDouble()).toFloat()
}

/**
 * Retry wrapper for suspendable network calls with automatic exponential backoff.
 * Executes call up to (maxRetries + 1) times with exponential delay between attempts.
 * Returns immediately on success; propagates null as success (no retry for null results).
 *
 * Retry Strategy:
 * - Success: Return result immediately (no retry)
 * - Null result: Return immediately (not treated as failure)
 * - Exception: Retry with exponential backoff, up to maxRetries times
 * - All retries exhausted: Return null and log error
 *
 * Usage Example:
 * ```
 * val card = retryableCall(RetryPolicy(maxRetries=3)) {
 *     scryfallApiClient.getCardByFuzzyName("Black Lotus")
 * }
 * ```
 *
 * @param T Generic return type of the call
 * @param policy RetryPolicy with delay/backoff settings
 * @param call Suspendable lambda returning T? (null is treated as success, not an error)
 * @return Result T? from successful call, or null if all retries exhausted or exception occurs
 */
suspend inline fun <T> retryableCall(
    policy: RetryPolicy = RetryPolicy(),
    crossinline call: suspend () -> T?
): T? {
    var lastException: Exception? = null
    
    repeat(policy.maxRetries + 1) { attemptNumber ->
        try {
            Log.d("RetryableCall", "Attempt ${attemptNumber + 1}/${policy.maxRetries + 1}")
            val result = call()
            if (result != null) {
                return result
            }
            // Null result is not an error; return immediately
            return null
        } catch (e: Exception) {
            lastException = e
            if (attemptNumber < policy.maxRetries) {
                val delayMs = policy.getDelayMs(attemptNumber)
                Log.w("RetryableCall", "Attempt ${attemptNumber + 1} failed: ${e.message}. Retrying in ${delayMs}ms...")
                kotlinx.coroutines.delay(delayMs)
            } else {
                Log.e("RetryableCall", "All ${policy.maxRetries + 1} attempts exhausted", e)
            }
        }
    }
    
    return null
}
