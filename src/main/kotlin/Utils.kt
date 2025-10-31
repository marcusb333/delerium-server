/**
 * Utils.kt - Utility functions for ID generation and encoding
 * 
 * This file provides helper functions for:
 * - Generating random alphanumeric IDs
 * - Calculating the decoded size of base64url strings
 */

import java.security.SecureRandom

/**
 * Utility object for generating random identifiers
 */
object Ids {
    private val alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private val rnd = SecureRandom()
    
    /**
     * Generate a cryptographically random alphanumeric ID
     * 
     * @param len Length of the ID to generate
     * @return Random string of the specified length using [0-9a-zA-Z]
     */
    fun randomId(len: Int): String =
        (0 until len).map { alphabet[rnd.nextInt(alphabet.length)] }.joinToString("")
}

/**
 * Calculate the decoded byte size of a base64url-encoded string
 * 
 * Converts base64url to standard base64, adds padding, and calculates
 * the original byte size. Used for validating paste size limits.
 * 
 * @param bytesB64Url Base64url-encoded string
 * @return Size in bytes after decoding
 */
fun base64UrlSize(bytesB64Url: String): Int {
    val s = bytesB64Url.replace("-", "+").replace("_", "/")
    val pad = (4 - s.length % 4) % 4
    val total = s.length + pad
    return (total / 4) * 3
}
