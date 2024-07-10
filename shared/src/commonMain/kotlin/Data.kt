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
    val bullets: Map<String, Bullet> = emptyMap(),
    val explosions: List<HitEffect> = emptyList(),
    val boxes: List<Box> = emptyList(),
    val flags: List<Flag> = emptyList(),
    val scores: Map<Team, Int> = mapOf(Team.BLUE to 0, Team.RED to 0),
)

@Serializable
data class Player(
    val id: String,
    var x: Float,
    var y: Float,
    val team: Team,
    var dx: Int = 0,
    var dy: Int = 0,
    var angle: Float,
    var health: Int = 100,
    var level: Int = 1
)

@Serializable
data class Bullet(
    val id: String,
    val xStart: Float,
    val yStart: Float,
    var x: Float,
    var y: Float,
    val angle: Float,
    val playerId: String,
    val level: Int
)
@Serializable
data class Box(
    val id: Int,
    val position: Point,
    val size: Size
)
val Bullet.speed
    get() = max(1, 10 - level)

val Player.speed
    get() = max(10, 25 - level) * 0.1f

@Serializable
data class HitEffect(
    val x: Float,
    val y: Float,
    var scale: Float,
    var progress: Float = 0f,
    val type: Int
)
@Serializable
data class Point(val x: Float, val y: Float)
@Serializable
data class Size(val width: Float, val height: Float)
fun HitEffect.update() {
    scale -= 0.01f
    progress += 0.1f
}
const val RANGE = 3000
const val BULLET_RANGE = 500

@Serializable
data class Flag(
    val id: Int,
    var x: Float,
    var y: Float,
    val team: Team,
    var holderId: String? = null,
)

enum class Team {
    BLUE, RED
}

val bases = mapOf(
    Team.BLUE to Point(150f, 150f),
    Team.RED to Point(RANGE - 150f, RANGE - 150f)
)