/**
 * Pow.kt - Proof-of-Work service for rate limiting and spam prevention
 * 
 * This file implements a proof-of-work (PoW) system that requires clients to solve
 * a computational puzzle before creating a paste. The puzzle involves finding a nonce
 * that, when hashed with the challenge using SHA-256, produces a hash with a minimum
 * number of leading zero bits.
 * 
 * This provides protection against automated spam and abuse by making paste creation
 * computationally expensive.
 */

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * A proof-of-work challenge issued to a client
 * 
 * @property challenge Random challenge string (base64url encoded)
 * @property difficulty Number of leading zero bits required in the hash
 * @property expiresAt Unix timestamp when this challenge expires
 */
data class PowChallenge(val challenge: String, val difficulty: Int, val expiresAt: Long)

/**
 * Service for generating and verifying proof-of-work challenges
 * 
 * Difficulty guidelines:
 * - 8-10 bits: Light protection, ~256-1024 attempts (fast, good for production)
 * - 12-14 bits: Medium protection, ~4K-16K attempts (balanced)
 * - 16-18 bits: Strong protection, ~64K-256K attempts (slower, anti-spam)
 * - 20+ bits: Very strong, exponentially more expensive (testing/extreme cases)
 * 
 * @property difficulty Number of leading zero bits required in solutions
 * @property ttlSeconds Time-to-live for challenges in seconds
 */
class PowService(private val difficulty: Int, private val ttlSeconds: Int) {
    private val rand = SecureRandom()
    private val cache = ConcurrentHashMap<String, Long>() // challenge -> expiry timestamp
    private val maxOutstandingChallenges = 10_000

    /**
     * Generate a new proof-of-work challenge
     * 
     * Creates a random 16-byte challenge encoded as base64url and stores it
     * in the cache with an expiration timestamp.
     * 
     * @return A new PowChallenge object
     */
    fun newChallenge(): PowChallenge {
        val now = Instant.now().epochSecond
        cleanup(now)
        enforceCapacity(now)
        val bytes = ByteArray(16)
        rand.nextBytes(bytes)
        val ch = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val exp = now + ttlSeconds
        cache[ch] = exp
        return PowChallenge(ch, difficulty, exp)
    }

    /**
     * Verify a proof-of-work solution
     * 
     * Checks that:
     * 1. The challenge exists in the cache
     * 2. The challenge has not expired
     * 3. SHA-256(challenge:nonce) has at least 'difficulty' leading zero bits
     * 
     * If verification succeeds, the challenge is removed from the cache to prevent reuse.
     * 
     * @param challenge The challenge string from the original request
     * @param nonce The nonce value found by the client
     * @return true if the solution is valid, false otherwise
     */
    fun verify(challenge: String, nonce: Long): Boolean {
        val now = Instant.now().epochSecond
        val exp = cache[challenge] ?: return false
        if (now > exp) {
            cache.remove(challenge, exp)
            return false
        }
        val md = MessageDigest.getInstance("SHA-256")
        val input = "$challenge:$nonce".toByteArray()
        val digest = md.digest(input)
        val bits = leadingZeroBits(digest)
        val ok = bits >= difficulty
        if (ok) {
            cache.remove(challenge)
        }
        return ok
    }

    /**
     * Count the number of leading zero bits in a byte array
     * 
     * Used to determine if a hash meets the difficulty requirement.
     * 
     * @param b Byte array (typically a SHA-256 hash)
     * @return Number of leading zero bits
     */
    private fun leadingZeroBits(b: ByteArray): Int {
        var bits = 0
        for (by in b) {
            val v = by.toInt() and 0xff
            if (v == 0) { bits += 8; continue }
            bits += Integer.numberOfLeadingZeros(v) - 24
            break
        }
        return bits
    }

    private fun cleanup(now: Long) {
        cache.entries.removeIf { (_, expires) -> expires <= now }
    }

    private fun enforceCapacity(now: Long) {
        if (cache.size <= maxOutstandingChallenges) {
            return
        }
        // Remove expired entries first
        cleanup(now)
        if (cache.size <= maxOutstandingChallenges) {
            return
        }
        val overflow = cache.size - maxOutstandingChallenges
        if (overflow <= 0) return
        val entriesByExpiry = cache.entries.sortedBy { it.value }
        var removed = 0
        for (entry in entriesByExpiry) {
            if (removed >= overflow) break
            if (cache.remove(entry.key, entry.value)) {
                removed++
            }
        }
    }
}
