/**
 * App.kt - Main application configuration and initialization
 * 
 * This file contains the entry point for the ZKPaste Ktor server application.
 * It handles:
 * - Application configuration loading from application.conf
 * - Database connection pooling with HikariCP
 * - Security headers (CSP, CORS, X-Content-Type-Options, etc.)
 * - Plugin installation (compression, content negotiation, logging, CORS)
 * - Initialization of core services (rate limiter, proof-of-work, paste repository)
 * - Routing setup
 */

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.routing.routing
import org.jetbrains.exposed.sql.Database

/**
 * Application configuration data class
 * Holds all runtime configuration values loaded from application.conf and environment variables
 * 
 * @property dbPath Database JDBC connection string
 * @property deletionPepper Secret pepper value for hashing deletion tokens (from env var)
 * @property powEnabled Whether proof-of-work is enabled for paste creation
 * @property powDifficulty Number of leading zero bits required in PoW solution
 * @property powTtl Time-to-live for PoW challenges in seconds
 * @property rlEnabled Whether rate limiting is enabled
 * @property rlCapacity Maximum number of tokens in the rate limiter bucket
 * @property rlRefill Number of tokens to refill per minute
 * @property maxSizeBytes Maximum allowed size for paste content in bytes
 * @property idLength Length of randomly generated paste IDs
 */
data class AppConfig(
    val dbPath: String,
    val deletionPepper: String,
    val powEnabled: Boolean,
    val powDifficulty: Int,
    val powTtl: Int,
    val rlEnabled: Boolean,
    val rlCapacity: Int,
    val rlRefill: Int,
    val maxSizeBytes: Int,
    val idLength: Int
)

/**
 * Main application module function
 * 
 * This extension function on Application is the entry point for the Ktor server.
 * It performs the following initialization steps:
 * 1. Loads configuration from application.conf and environment variables
 * 2. Installs HTTP plugins (compression, JSON serialization, logging, CORS)
 * 3. Adds security headers to all responses
 * 4. Sets up database connection pool
 * 5. Initializes services (repository, rate limiter, proof-of-work)
 * 6. Configures API routes
 */
fun Application.module() {
    val cfg = environment.config
    val appCfg = AppConfig(
        dbPath = cfg.property("storage.dbPath").getString(),
        deletionPepper = System.getenv("DELETION_TOKEN_PEPPER") ?: "dev-pepper-change-me",
        powEnabled = cfg.propertyOrNull("storage.pow.enabled")?.getString()?.toBoolean() ?: true,
        powDifficulty = cfg.property("storage.pow.difficulty").getString().toInt(),
        powTtl = cfg.property("storage.pow.ttlSeconds").getString().toInt(),
        rlEnabled = cfg.propertyOrNull("storage.rateLimit.enabled")?.getString()?.toBoolean() ?: true,
        rlCapacity = cfg.property("storage.rateLimit.capacity").getString().toInt(),
        rlRefill = cfg.property("storage.rateLimit.refillPerMinute").getString().toInt(),
        maxSizeBytes = cfg.property("storage.paste.maxSizeBytes").getString().toInt(),
        idLength = cfg.property("storage.paste.idLength").getString().toInt()
    )

    install(Compression)
    install(ContentNegotiation) { jackson() }
    install(CallLogging) { level = org.slf4j.event.Level.INFO }
    install(CORS) {
        allowMethod(HttpMethod.Get); allowMethod(HttpMethod.Post); allowMethod(HttpMethod.Delete)
        anyHost(); allowHeaders { true }; exposeHeader(HttpHeaders.ContentType)
    }
    intercept(ApplicationCallPipeline.Setup) {
        call.response.headers.append("Referrer-Policy", "no-referrer")
        call.response.headers.append("X-Content-Type-Options", "nosniff")
        call.response.headers.append("Content-Security-Policy",
            "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; base-uri 'none'; frame-ancestors 'none';")
        call.response.headers.append("Permissions-Policy", "accelerometer=(), geolocation=(), camera=(), microphone=()")
    }

    val hikari = HikariDataSource(HikariConfig().apply {
        jdbcUrl = appCfg.dbPath
        maximumPoolSize = 5
    })
    val db = Database.connect(hikari)
    val repo = PasteRepo(db, appCfg.deletionPepper)
    val rl = if (appCfg.rlEnabled) TokenBucket(appCfg.rlCapacity, appCfg.rlRefill) else null
    val pow = if (appCfg.powEnabled) PowService(appCfg.powDifficulty, appCfg.powTtl) else null

    routing {
        apiRoutes(repo, rl, pow, appCfg)
    }
}
