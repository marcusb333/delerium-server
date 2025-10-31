/**
 * Models.kt - Data models for API request/response objects
 * 
 * This file defines all the data transfer objects (DTOs) used in the ZKPaste API.
 * All encryption happens client-side; the server only stores encrypted content.
 */

/**
 * Request body for creating a new paste
 * 
 * @property ct Ciphertext - the encrypted paste content (base64url encoded)
 * @property iv Initialization vector for AES-GCM encryption (base64url encoded)
 * @property meta Metadata about the paste (expiration, view limits, etc.)
 * @property pow Optional proof-of-work solution (required if PoW is enabled)
 */
data class CreatePasteRequest(
    val ct: String,
    val iv: String,
    val meta: PasteMeta,
    val pow: PowSubmission? = null
)

/**
 * Metadata for a paste
 * 
 * @property expireTs Unix timestamp when the paste should expire
 * @property viewsAllowed Maximum number of views allowed (null = unlimited)
 * @property mime MIME type hint for the content (e.g., "text/plain")
 * @property singleView If true, paste is deleted after first view
 */
data class PasteMeta(
    val expireTs: Long,
    val viewsAllowed: Int? = null,
    val mime: String? = null,
    val singleView: Boolean? = null
)

/**
 * Proof-of-work submission
 * 
 * @property challenge The challenge string received from /api/pow
 * @property nonce The nonce value that produces sufficient leading zero bits
 */
data class PowSubmission(val challenge: String, val nonce: Long)

/**
 * Response after successfully creating a paste
 * 
 * @property id The unique ID for the paste (used in URLs)
 * @property deleteToken Secret token for deleting the paste (should be kept private)
 */
data class CreatePasteResponse(val id: String, val deleteToken: String)

/**
 * Payload returned when retrieving a paste
 * 
 * @property ct Ciphertext - the encrypted paste content
 * @property iv Initialization vector for decryption
 * @property meta Original metadata from paste creation
 * @property viewsLeft Number of views remaining (null if unlimited)
 */
data class PastePayload(val ct: String, val iv: String, val meta: PasteMeta, val viewsLeft: Int?)

/**
 * Error response format
 * 
 * @property error Error code/message string
 */
data class ErrorResponse(val error: String)
