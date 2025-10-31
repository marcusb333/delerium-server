/**
 * RateLimiter.kt - Token bucket rate limiting implementation
 * 
 * This file implements a token bucket algorithm for rate limiting API requests.
 * Each client (identified by IP or key) has a bucket that holds tokens. Each request
 * consumes one token. Tokens are refilled over time at a constant rate.
 * 
 * This provides protection against abuse and ensures fair resource usage.
 */

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

/**
 * Token bucket rate limiter
 * 
 * Thread-safe implementation that tracks token buckets per key (typically IP address).
 * Tokens are refilled continuously based on elapsed time.
 * 
 * @property capacity Maximum number of tokens a bucket can hold
 * @property refillPerMinute Number of tokens to add per minute
 */
class TokenBucket(private val capacity: Int, private val refillPerMinute: Int) {
    /**
     * Internal state for a single bucket
     * @property tokens Current number of tokens (can be fractional)
     * @property last Last update timestamp in milliseconds
     */
    private data class State(var tokens: Double, var last: Long)
    
    private val map = ConcurrentHashMap<String, State>()

    /**
     * Check if a request should be allowed
     * 
     * Refills tokens based on elapsed time, then attempts to consume one token.
     * If at least one token is available, the request is allowed and the token
     * count is decremented.
     * 
     * @param key Unique identifier for the client (typically "POST:IP_ADDRESS")
     * @return true if the request is allowed, false if rate limited
     */
    fun allow(key: String): Boolean {
        val nowMs = System.currentTimeMillis()
        val st = map.computeIfAbsent(key) { State(capacity.toDouble(), nowMs) }
        synchronized(st) {
            val elapsedMin = (nowMs - st.last) / 60000.0
            st.tokens = min(capacity.toDouble(), st.tokens + elapsedMin * refillPerMinute)
            st.last = nowMs
            return if (st.tokens >= 1.0) { st.tokens -= 1.0; true } else false
        }
    }
}
