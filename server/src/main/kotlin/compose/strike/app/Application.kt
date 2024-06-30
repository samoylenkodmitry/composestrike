@file:Suppress(
    "ktlint:standard:no-wildcard-imports",
    "ktlint:standard:package-name",
    "NestedLambdaShadowedImplicitParameter",
)

package compose.strike.app

import AuthResult
import Challenge
import DIFFICULTY_PREFIX
import EndpointWithArg
import Endpoints
import IS_LOCALHOST
import Player
import ProofOfWork
import RANGE
import SERVER_PORT
import SQUARE_ICON_DATA
import UpdateNickRequest
import User
import challengeHash
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.util.*
import io.ktor.util.collections.*
import io.ktor.websocket.*
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

fun main() {
    initDB()
    println("Starting server at $HOST_LOCAL:$SERVER_PORT")
    embeddedServer(Netty, port = SERVER_PORT, host = HOST_LOCAL, module = Application::module)
        .start(wait = true)
}

private const val CHALLENGE_EXPIRATION_TIME_MS = 60_000 // 1 minute in milliseconds
private const val HOST_LOCAL = "0.0.0.0"
private const val DEFAULT_DB_PASSWORD = "password"
const val CLAIM_USER_ID = "uid"
private val hostRelease =
    System.getenv("DOMAIN").takeIf { !it.isNullOrBlank() }.also {
        println("DOMAIN from env: $it")
    } ?: "composestrike.app"
private val hostName = if (IS_LOCALHOST) HOST_LOCAL else hostRelease
private val hostUrl = if (IS_LOCALHOST) "http://$hostName:$SERVER_PORT" else "https://$hostName"
private val privateKeyPath = if (IS_LOCALHOST) "./server/ktor.pk8" else "/run/secrets/ktor_pk8"
private val certsPath = if (IS_LOCALHOST) "./server/certs" else "/run/secrets/certs"
private val SQUARE_ICON_BYTES = SQUARE_ICON_DATA.decodeBase64Bytes()

object Users : LongIdTable() {
    val nick = varchar("nick", 255)
    val timestamp = long("timestamp")
    val challenge = varchar("challenge", 2048).uniqueIndex()
}

fun initDB() {
    Database.connect(
        url = getDatabaseUrl().also { println("DATABASE_URL: $it") },
        driver = "org.postgresql.Driver",
        user = getDatabaseUser().also { println("DATABASE_USER: $it") },
        password = getDatabasePassword().also { println("DATABASE_PASSWORD is '$DEFAULT_DB_PASSWORD'?: ${it == DEFAULT_DB_PASSWORD}") },
    )

    // create database if needed

    transaction {
        SchemaUtils.create(Users)
    }
}

private fun getDatabasePassword() =
    if (IS_LOCALHOST) {
        DEFAULT_DB_PASSWORD
    } else {
        System
            .getenv("DATABASE_PASSWORD_FILE")
            .takeIf { !it.isNullOrBlank() }
            ?.let { File(it).readText().trim() }
            .takeIf { !it.isNullOrBlank() }
            .also { println("DATABASE_PASSWORD from file isNullOrEmpty?: ${it.isNullOrEmpty()}") }
            ?: DEFAULT_DB_PASSWORD
    }

private fun getDatabaseUser() =
    if (IS_LOCALHOST) {
        "user"
    } else {
        System
            .getenv("DATABASE_USER_FILE")
            .takeIf { !it.isNullOrBlank() }
            ?.let { File(it).readText().trim() }
            .takeIf { !it.isNullOrBlank() }
            .also { println("DATABASE_USER from file: $it") } ?: "user"
    }

private fun getDatabaseUrl() =
    if (IS_LOCALHOST) {
        "jdbc:postgresql://0.0.0.0:5432/strike"
    } else {
        System.getenv("DATABASE_URL").takeIf { !it.isNullOrBlank() }.also { println("DATABASE_URL from env: $it") }
            ?: "jdbc:postgresql://postgres:5432/strike"
    }

// https://github.com/ktorio/ktor-documentation/blob/2.3.10/codeSnippets/snippets/auth-jwt-rs256/src/main/kotlin/com/example/Application.kt
fun Application.module() {
    val game = Game()
    install(io.ktor.server.websocket.WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    install(CORS) {
        anyHost()
        allowCredentials = true
        allowNonSimpleContentTypes = true
        allowSameOrigin = true
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.AccessControlAllowOrigin)
    }
    install(ContentNegotiation) {
        json()
    }
    val issuer = hostUrl
    val audience = "composestrike.app"
    val myRealm = "composestrike.app"
    val jwtAlgorithm = loadJWTKey()
    val jwtVerifier = JWT.require(jwtAlgorithm).withIssuer(issuer).build()

    install(Authentication) {
        jwt("auth-jwt") {
            realm = myRealm
            verifier(jwtVerifier)
            validate { credential ->
                if (credential.payload.audience.contains(audience)) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, "Token is not valid or expired")
            }
        }
    }
    routing {
        get("/") { call.respondText("hi from backend") }
        get("/favicon.ico") {
            println("Favicon requested")
            call.respondBytes(SQUARE_ICON_BYTES, ContentType.Image.SVG)
        }
        File(certsPath).walkTopDown().forEach { println(it) }
        File(certsPath, "jwks.json").readText().also { println(it) }
        static(".well-known") {
            staticRootFolder = File(certsPath)
            static("jwks.json") {
                files("jwks.json")
            }
        }
        get("/debug/jwks") {
            val jwksFile = File(certsPath, "jwks.json")
            if (jwksFile.exists()) {
                println("Serving JWKS")
                call.respondText(jwksFile.readText())
            } else {
                println("JWKS not found")
                call.respondText("File not found", status = HttpStatusCode.NotFound)
            }
        }
        get(Endpoints.powGet.path) {
            call.respond(issueChallenge(jwtAlgorithm))
        }
        post(Endpoints.powPost()) {
            var respondError = suspend {}
            receiveArgs(it) { proofOfWork ->
                if (validateChallengeSolution(proofOfWork, jwtAlgorithm)) {
                    val createdUser = addUser("user", proofOfWork.challenge)
                    if (createdUser != null) {
                        // create JWT refreshToken, infinity lifetime
                        val refreshToken =
                            JWT
                                .create()
                                .withAudience(audience)
                                .withIssuer(issuer)
                                .withClaim(CLAIM_USER_ID, createdUser.id)
                                .sign(jwtAlgorithm)
                        // create JWT sessionToken
                        val sessionToken =
                            JWT
                                .create()
                                .withAudience(audience)
                                .withIssuer(issuer)
                                .withExpiresAt(
                                    Instant
                                        .now()
                                        .plus(1L, ChronoUnit.DAYS)
                                        .plus((0..1000).random().toLong(), ChronoUnit.MILLIS),
                                ).withClaim(CLAIM_USER_ID, createdUser.id)
                                .sign(jwtAlgorithm)
                        AuthResult(refreshToken, sessionToken, createdUser)
                    } else {
                        respondError = { call.respond(HttpStatusCode.Conflict, "User already exists") }
                        null
                    }
                } else {
                    respondError = { call.respond(HttpStatusCode.BadRequest, "Invalid Proof of Work Solution") }
                    null
                }
            }?.let { authResult -> call.respond(HttpStatusCode.Created, authResult) } ?: respondError()
        }
        post(Endpoints.tokenRefresh()) {
            receiveArgs(it) { refreshToken ->
                val jwt = jwtVerifier.verify(refreshToken.refreshToken)
                val userId = jwt.getClaim(CLAIM_USER_ID).asLong()
                val user = getUser(userId)
                if (user != null) {
                    val sessionToken =
                        JWT
                            .create()
                            .withAudience(audience)
                            .withIssuer(issuer)
                            .withClaim(CLAIM_USER_ID, user.id)
                            .sign(jwtAlgorithm)
                    AuthResult(refreshToken.refreshToken, sessionToken, user)
                } else {
                    null
                }
            }?.let { call.respond(HttpStatusCode.Created, it) } ?: call.respond(
                HttpStatusCode.NotFound,
                "User Not Found",
            )
        }

        authenticate("auth-jwt") {
            postWithAuth(Endpoints.updateNick()) { (endpoint, userId) ->
                receiveArgs(endpoint) { request: UpdateNickRequest ->
                    transaction {
                        Users.update({ Users.id eq userId }) {
                            it[nick] = request.nick
                        } > 0
                    }
                }?.let { success -> call.respond(HttpStatusCode.OK, success) }
                    ?: call.respond(HttpStatusCode.BadRequest, "Invalid request")
            }
        }
        webSocket("/game") {
            val playerId = UUID.randomUUID().toString()
            val player =
                Player(playerId, (0..RANGE).random() * 1f, (0..RANGE).random() * 1f, 0, 0, 0f, 100) // Starting health
            game.addPlayer(player)
            player.setConnection(this, this)
            println("Player $playerId connected")
            outgoing.send(Frame.Text(playerId))

            try {
                outgoing.send(Frame.Text(Json.encodeToString(game.gameState)))

                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val message = frame.readText()
                        val parts = message.split(":")
                        when (parts[0]) {
                            "K" -> {
                                parts[1].toIntOrNull()?.let {
                                    game.updatePlayerKeys(playerId, it)
                                }
                            }

                            "A" -> {
                                parts[1].toFloatOrNull()?.let {
                                    game.updatePlayerAngle(playerId, it)
                                }
                            }

                            "F" -> {
                                game.fireBullet(playerId)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                println("Player $playerId disconnected: ${e.message}")
            } finally {
                game.removePlayer(playerId)
                println("Player removed $playerId ${closeReason.await()}")
            }
        }
    }
}

private fun loadJWTKey(): Algorithm {
    val keyBytes =
        File(privateKeyPath).takeIf { it.exists() }?.readBytes() ?: run {
            File(".").walkTopDown().forEach { println(it) }
            throw Exception("Private key file not found")
        }
    val kf = KeyFactory.getInstance("RSA")
    val privateKey = kf.generatePrivate(PKCS8EncodedKeySpec(keyBytes)) as RSAPrivateCrtKey
    val rsaPublicKey =
        kf.generatePublic(RSAPublicKeySpec(privateKey.modulus, privateKey.publicExponent)) as RSAPublicKey
    val rsaPrivateKey = privateKey as RSAPrivateKey
    return Algorithm.RSA512(
        rsaPublicKey,
        rsaPrivateKey,
    )
}

fun issueChallenge(algorithm: Algorithm): Challenge {
    val bytes = ByteArray((8..32).random())
    SecureRandom().nextBytes(bytes)
    val randomHex = bytes.joinToString("") { "%02x".format(it) }
    val currentTimestamp = Clock.System.now().toEpochMilliseconds() - (0..1000).random()
    return Challenge(
        JWT
            .create()
            .withExpiresAt(Instant.ofEpochMilli(currentTimestamp + CHALLENGE_EXPIRATION_TIME_MS))
            .withIssuer("composestrike.app")
            .withClaim("nonce", "$randomHex.$currentTimestamp")
            .sign(algorithm),
    )
}

fun validateChallengeSolution(
    pow: ProofOfWork,
    algorithm: Algorithm?,
) = validateChallengeSignatureAndTs(pow.challenge, algorithm) &&
        validateSolution(pow)

fun validateChallengeSignatureAndTs(
    challenge: String,
    algorithm: Algorithm?,
): Boolean {
    val jwt =
        try {
            JWT
                .require(algorithm)
                .withIssuer("composestrike.app")
                .build()
                .verify(challenge)
        } catch (e: Exception) {
            return false
        }

    val expiresDateInstant = jwt.expiresAtAsInstant ?: return false
    val currentTimestamp = Clock.System.now().toEpochMilliseconds() - (0..1000).random()
    return expiresDateInstant.toEpochMilli() >= currentTimestamp
}

fun validateSolution(pow: ProofOfWork) =
    pow.prefix == DIFFICULTY_PREFIX &&
            pow.solution.startsWith(pow.challenge) &&
            challengeHash(pow.solution).startsWith(pow.prefix)
val usedUsersChallenges = ConcurrentSet<String>()

fun addUser(
    nick: String,
    challenge: String,
) = // check in memory if a user with the same challenge exists
    if (!usedUsersChallenges.add(challenge)) {
        null
    } else {
        if (usedUsersChallenges.size > 1000) {
            usedUsersChallenges.clear()
        }
        transaction {
            // check if user with the same challenge exists
            val existingUser =
                Users
                    .select { Users.challenge eq challenge }
                    .map {
                        User(it[Users.id].value, it[Users.nick])
                    }.singleOrNull()
            if (existingUser != null) {
                // user with the same challenge exists, don't create a new one
                return@transaction null
            }
            val userId =
                Users.insertAndGetId {
                    it[Users.nick] = nick
                    it[Users.challenge] = challenge
                    it[timestamp] = Clock.System.now().toEpochMilliseconds() - (0..1000).random()
                }
            User(userId.value, nick)
        }
    }

fun getUser(id: Long) =
    transaction {
        Users
            .select { Users.id eq id }
            .map {
                User(it[Users.id].value, it[Users.nick])
            }.singleOrNull()
    }

@Suppress("UNUSED_PARAMETER")
suspend inline fun <reified A : Any, reified R : Any> RoutingContext.receiveArgs(
    e: EndpointWithArg<A, R>,
    makeResult: RoutingContext.(A) -> R?,
): R? = makeResult(call.receive<A>())

inline fun <reified A : Any, reified R : Any> Route.post(
    endpoint: EndpointWithArg<A, R>,
    crossinline body: suspend RoutingContext.(EndpointWithArg<A, R>) -> Unit,
): Route = post(endpoint.path) { body(endpoint) }

inline fun <reified A : Any, reified R : Any> Route.postWithAuth(
    endpoint: EndpointWithArg<A, R>,
    crossinline body: suspend RoutingContext.(Pair<EndpointWithArg<A, R>, Long>) -> Unit,
): Route =
    post(endpoint.path) {
        val userId =
            call
                .principal<JWTPrincipal>()
                ?.payload
                ?.getClaim(CLAIM_USER_ID)
                ?.asLong()
        if (userId == null) {
            call.respond(HttpStatusCode.Unauthorized, "User Not Found or invalid token")
            return@post
        }
        body(endpoint to userId)
    }
