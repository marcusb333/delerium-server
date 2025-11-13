/**
 * Routes.kt - HTTP API endpoint definitions
 * 
 * This file defines all the REST API endpoints for the delerium-paste-server application:
 * - GET  /api/pow - Request a proof-of-work challenge
 * - POST /api/pastes - Create a new encrypted paste
 * - GET  /api/pastes/{id} - Retrieve an encrypted paste
 * - DELETE /api/pastes/{id}?token=... - Delete a paste with deletion token
 * 
 * All endpoints include appropriate validation and error handling.
 */

import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.origin
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.application.ApplicationCall

/**
 * Configure all API routes
 * 
 * Sets up the /api route group with all paste management endpoints.
 * 
 * @param repo Paste repository for database operations
 * @param rl Optional token bucket rate limiter
 * @param pow Optional proof-of-work service
 * @param cfg Application configuration
 */
private val trustedProxyIps: Set<String> =
    System.getenv("TRUSTED_PROXY_IPS")
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.toSet()
        ?: emptySet()

private fun clientIp(call: ApplicationCall): String {
    val remoteHost = call.request.origin.remoteHost
    if (trustedProxyIps.isEmpty() || remoteHost !in trustedProxyIps) {
        return remoteHost
    }
    val header = call.request.headers["X-Forwarded-For"] ?: return remoteHost
    return header.split(",")
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() }
        ?: remoteHost
}

fun Routing.apiRoutes(repo: PasteRepo, rl: TokenBucket?, pow: PowService?, cfg: AppConfig) {
    route("/api") {
        /**
         * GET /api/pow
         * Request a new proof-of-work challenge
         * Returns 204 No Content if PoW is disabled
         */
        get("/pow") {
            if (cfg.powEnabled && pow != null) call.respond(pow.newChallenge())
            else call.respond(HttpStatusCode.NoContent)
        }
        /**
         * POST /api/pastes
         * Create a new encrypted paste
         * 
         * Performs the following checks:
         * 1. Rate limiting (if enabled)
         * 2. JSON parsing and validation
         * 3. Proof-of-work verification (if enabled)
         * 4. Size validation (content and IV)
         * 5. Expiration time validation
         * 
         * Returns 201 with paste ID and deletion token on success
         */
        post("/pastes") {
            if (rl != null) {
                val ip = clientIp(call)
                if (!rl.allow("POST:$ip")) {
                    call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limited")); return@post
                }
            }
            val body = try { call.receive<CreatePasteRequest>() } catch (_: Exception) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_json")); return@post
            }
            if (cfg.powEnabled && pow != null) {
                val sub = body.pow ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("pow_required")); return@post
                }
                if (!pow.verify(sub.challenge, sub.nonce)) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("pow_invalid")); return@post
                }
            }
            val ctSize = base64UrlSize(body.ct)
            val ivSize = base64UrlSize(body.iv)
            if (ctSize <= 0 || ivSize !in 12..64 || ctSize > cfg.maxSizeBytes) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("size_invalid")); return@post
            }
            if (body.meta.expireTs <= (System.currentTimeMillis()/1000L) + 10) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("expiry_too_soon")); return@post
            }
            val id = Ids.randomId(cfg.idLength)
            val deleteToken = Ids.randomId(24)
            try {
                repo.create(id, body.ct, body.iv, body.meta, deleteToken)
                call.respond(CreatePasteResponse(id, deleteToken))
            } catch (_: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("db_error"))
            }
        }
        /**
         * GET /api/pastes/{id}
         * Retrieve an encrypted paste
         * 
         * Returns 404 if the paste doesn't exist or has expired.
         * Increments view count and may delete the paste if:
         * - singleView is true, or
         * - viewsAllowed limit has been reached
         */
        get("/pastes/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val row = repo.getIfAvailable(id) ?: return@get call.respond(HttpStatusCode.NotFound)
            val payload = repo.toPayload(row)
            if (repo.shouldDeleteAfterView(row)) {
                repo.delete(id)
            } else {
                repo.incrementViews(id)
            }
            call.respond(payload)
        }
        /**
         * DELETE /api/pastes/{id}?token=...
         * Delete a paste using its deletion token
         * 
         * Returns 403 Forbidden if the token doesn't match.
         * Returns 204 No Content on successful deletion.
         */
        delete("/pastes/{id}") {
            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            val token = call.request.queryParameters["token"] ?: return@delete call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("missing_token"))
            val ok = repo.deleteIfTokenMatches(id, token)
            if (!ok) call.respond(HttpStatusCode.Forbidden, ErrorResponse("invalid_token"))
            else call.respond(HttpStatusCode.NoContent)
        }
    }
}
