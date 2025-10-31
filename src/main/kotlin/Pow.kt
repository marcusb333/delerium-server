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

import java.security.SecureRandom
import java.time.Instant
import java.security.MessageDigest
import java.util.Base64

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
 * @property difficulty Number of leading zero bits required in solutions
 * @property ttlSeconds Time-to-live for challenges in seconds
 */
class PowService(private val difficulty: Int, private val ttlSeconds: Int) {
    private val rand = SecureRandom()
    private val cache = mutableMapOf<String, Long>() // challenge -> expiry timestamp

    /**
     * Generate a new proof-of-work challenge
     * 
     * Creates a random 16-byte challenge encoded as base64url and stores it
     * in the cache with an expiration timestamp.
     * 
     * @return A new PowChallenge object
     */
    fun newChallenge(): PowChallenge {
        val bytes = ByteArray(16)
        rand.nextBytes(bytes)
        val ch = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val exp = Instant.now().epochSecond + ttlSeconds
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
        val exp = cache[challenge] ?: return false
        if (Instant.now().epochSecond > exp) return false
        val md = MessageDigest.getInstance("SHA-256")
        val input = "$challenge:$nonce".toByteArray()
        val digest = md.digest(input)
        val bits = leadingZeroBits(digest)
        val ok = bits >= difficulty
        if (ok) cache.remove(challenge)
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
}
