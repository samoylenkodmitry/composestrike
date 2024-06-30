import kotlinx.serialization.Serializable
import kotlin.math.max

@Serializable
data class RefreshTokenRequest(
    val refreshToken: String,
)

/**
 * challenge is a signed JWT token, has timestamp inside it
 */
@Serializable
data class Challenge(
    val challenge: String,
    val prefix: String = DIFFICULTY_PREFIX,
)

@Serializable
data class ProofOfWork(
    val challenge: String,
    val solution: String,
    val prefix: String,
)

@Serializable
data class User(
    val id: Long,
    var nick: String,
)

@Serializable
data class AuthResult(
    val refreshToken: String,
    val sessionToken: String,
    val user: User,
)

@Serializable
data class UpdateNickRequest(
    val nick: String,
)

@Serializable
data class GameState(
    val players: Map<String, Player> = emptyMap(),
    val bullets: Map<String, Bullet> = emptyMap()
)

@Serializable
data class Player(
    val id: String,
    var x: Float,
    var y: Float,
    var dx: Int = 0,
    var dy: Int = 0,
    var angle: Float,
    var health: Int = 100,
    var level: Int = 1
)

@Serializable
data class Bullet(
    val id: String,
    var x: Float,
    var y: Float,
    val angle: Float,
    val playerId: String,
    val level: Int
)

val Bullet.speed
    get() = max(1, 10 - level)

val Player.speed
    get() = max(10, 25 - level) * 0.1f

const val RANGE = 3000