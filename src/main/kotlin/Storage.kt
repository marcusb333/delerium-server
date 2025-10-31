/**
 * Storage.kt - Database schema and repository for paste storage
 * 
 * This file handles all database operations for storing and retrieving encrypted pastes.
 * Uses Exposed SQL library with SQLite for persistence.
 * 
 * Key features:
 * - Automatic schema creation
 * - Secure deletion token hashing with pepper
 * - View counting and limits
 * - Expiration handling
 * - Single-view paste support
 */

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.security.MessageDigest
import java.time.Instant

/**
 * Database table definition for pastes
 * 
 * All paste content (ct, iv) is stored encrypted. The decryption key never
 * touches the server - it's only present in the URL fragment on the client side.
 */
object Pastes : Table("pastes") {
    val id = varchar("id", 32).uniqueIndex()
    val ct = text("ct")
    val iv = text("iv")
    val expireTs = long("expire_ts")
    val viewsAllowed = integer("views_allowed").nullable()
    val viewsUsed = integer("views_used").default(0)
    val singleView = bool("single_view").default(false)
    val mime = varchar("mime", 128).nullable()
    val deleteTokenHash = varchar("delete_token_hash", 128)
    val createdAt = long("created_at")
}

/**
 * Repository for paste storage operations
 * 
 * Provides a high-level API for creating, retrieving, and deleting pastes.
 * All deletion tokens are hashed with a secret pepper before storage.
 * 
 * @property db Database connection
 * @property pepper Secret value mixed into deletion token hashes
 */
class PasteRepo(private val db: Database, private val pepper: String) {
    init { transaction(db) { SchemaUtils.createMissingTablesAndColumns(Pastes) } }

    /**
     * Hash a deletion token with SHA-256
     * Combines the pepper with the raw token for additional security
     */
    private fun hashToken(raw: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(pepper.toByteArray())
        val out = md.digest(raw.toByteArray())
        return out.joinToString("") { "%02x".format(it) }
    }

    /**
     * Create a new paste in the database
     * 
     * @param id Unique paste identifier
     * @param ct Encrypted content (ciphertext)
     * @param iv Initialization vector
     * @param meta Paste metadata (expiration, view limits, etc.)
     * @param rawDeleteToken Raw deletion token (will be hashed before storage)
     */
    fun create(id: String, ct: String, iv: String, meta: PasteMeta, rawDeleteToken: String) {
        val now = Instant.now().epochSecond
        transaction(db) {
            Pastes.insert {
                it[Pastes.id] = id
                it[Pastes.ct] = ct
                it[Pastes.iv] = iv
                it[Pastes.expireTs] = meta.expireTs
                it[Pastes.viewsAllowed] = meta.viewsAllowed
                it[Pastes.singleView] = meta.singleView ?: false
                it[Pastes.mime] = meta.mime
                it[Pastes.deleteTokenHash] = hashToken(rawDeleteToken)
                it[Pastes.createdAt] = now
            }
        }
    }

    /**
     * Retrieve a paste if it exists and hasn't expired
     * 
     * @param id Paste identifier
     * @return Database row if paste exists and is not expired, null otherwise
     */
    fun getIfAvailable(id: String): ResultRow? = transaction(db) {
        val now = Instant.now().epochSecond
        Pastes.selectAll().where { Pastes.id eq id and (Pastes.expireTs greater now) }.singleOrNull()
    }

    /**
     * Increment the view count for a paste
     * Only increments if view limit hasn't been reached
     * 
     * @param id Paste identifier
     */
    fun incrementViews(id: String) = transaction(db) {
        val row = Pastes.selectAll().where { Pastes.id eq id }.singleOrNull() ?: return@transaction
        val allowed = row[Pastes.viewsAllowed]
        val used = row[Pastes.viewsUsed]
        if (allowed != null && used >= allowed) return@transaction
        Pastes.update({ Pastes.id eq id }) { it[viewsUsed] = used + 1 }
    }

    /**
     * Delete a paste if the provided deletion token is correct
     * 
     * @param id Paste identifier
     * @param rawToken Raw deletion token to verify
     * @return true if paste was deleted, false if token didn't match or paste not found
     */
    fun deleteIfTokenMatches(id: String, rawToken: String): Boolean = transaction(db) {
        val hash = hashToken(rawToken)
        val row = Pastes.selectAll().where { Pastes.id eq id }.singleOrNull() ?: return@transaction false
        if (row[Pastes.deleteTokenHash] != hash) return@transaction false
        Pastes.deleteWhere { Pastes.id eq id } > 0
    }

    /**
     * Delete a paste unconditionally
     * Used for automatic deletion after single-view or when view limit reached
     * 
     * @param id Paste identifier
     * @return true if a row was deleted
     */
    fun delete(id: String): Boolean = transaction(db) {
        Pastes.deleteWhere { Pastes.id eq id } > 0
    }

    /**
     * Determine if a paste should be deleted after the current view
     * 
     * @param row Database row for the paste
     * @return true if paste should be deleted (single-view or view limit reached)
     */
    fun shouldDeleteAfterView(row: ResultRow): Boolean {
        val single = row[Pastes.singleView]
        val allowed = row[Pastes.viewsAllowed]
        val used = row[Pastes.viewsUsed]
        return single || (allowed != null && used + 1 >= allowed)
    }

    /**
     * Convert a database row to an API response payload
     * 
     * @param row Database row
     * @return PastePayload for API response
     */
    fun toPayload(row: ResultRow): PastePayload {
        val allowed = row[Pastes.viewsAllowed]
        val used = row[Pastes.viewsUsed]
        val left = allowed?.let { (it - used).coerceAtLeast(0) }
        return PastePayload(
            ct = row[Pastes.ct],
            iv = row[Pastes.iv],
            meta = PasteMeta(
                expireTs = row[Pastes.expireTs],
                viewsAllowed = row[Pastes.viewsAllowed],
                mime = row[Pastes.mime],
                singleView = row[Pastes.singleView]
            ),
            viewsLeft = left
        )
    }
}
