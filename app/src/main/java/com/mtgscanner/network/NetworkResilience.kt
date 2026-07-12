package com.mtgscanner.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * NetworkStateManager: Monitors device network connectivity and provides reactive updates.
 * Allows UI to respond to connection state changes (online ↔ offline).
 */
class NetworkStateManager(context: Context) {

    companion object {
        private const val TAG = "NetworkStateManager"
    }

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val _isNetworkAvailable = MutableStateFlow(isNetworkConnected())
    val isNetworkAvailable: StateFlow<Boolean> = _isNetworkAvailable.asStateFlow()

    /**
     * Check if device has active network connection.
     * Returns true if device has any active network (WiFi, cellular, etc).
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
     * Update network state (call this from connectivity listener).
     */
    fun updateNetworkState(isConnected: Boolean) {
        _isNetworkAvailable.value = isConnected
        Log.d(TAG, "Network state updated: $isConnected")
    }
}

/**
 * RetryPolicy: Implements exponential backoff retry strategy.
 * Respects Scryfall rate limit: 100 requests/second.
 */
data class RetryPolicy(
    val maxRetries: Int = 3,
    val initialDelayMs: Long = 100L,
    val maxDelayMs: Long = 5000L,
    val backoffMultiplier: Float = 2f,
    val jitterFactor: Float = 0.1f  // ±10% random jitter to avoid thundering herd
) {
    /**
     * Calculate delay for retry attempt with exponential backoff + jitter.
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
    return kotlin.math.pow(this, exponent.toFloat())
}

/**
 * RetryableCall: Wrapper for suspendable network calls with automatic retry.
 * Example:
 *   val card = retryableCall(RetryPolicy()) {
 *       scryfallApiClient.getCardByFuzzyName("Black Lotus")
 *   }
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
