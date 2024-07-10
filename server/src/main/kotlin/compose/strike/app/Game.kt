package compose.strike.app

import BULLET_RANGE
import Box
import Bullet
import Flag
import GameState
import HitEffect
import Player
import Point
import RANGE
import Size
import Team
import bases
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import speed
import update
import java.util.*
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.sin

class Game {

    private fun generateRandomBoxes() = List(10 * RANGE / 140) {
        Box(
            id = it,
            position = Point((0..RANGE).random() * 1f, (0..RANGE).random() * 1f),
            size = Size((50..140).random().toFloat(), (50..140).random().toFloat())
        )
    }.filter { box ->
        !bases.values.any { base -> abs(box.position.x - base.x) < 100 && abs(box.position.y - base.y) < 100 }
    }

    private fun launchGame() = CoroutineScope(Dispatchers.Default).launch {
        println("Game loop started")
        while (isActive && players.isNotEmpty()) {
            updatePlayers()
            updateBullets()
            updateExplosions()
            checkBulletCollisions()
            updateFlags()
            broadcastGameState()
            delay(16) // Roughly 60 updates per second
        }
        println("Game loop stopped")
    }

    private val gameWorld = GameWorld(RANGE * 1f, RANGE * 1f, generateRandomBoxes())

    class GameWorld(val width: Float, val height: Float, val boxes: List<Box>) {

        fun collidesWithBox(obj: Any, newPosition: Point) =
            boxes.any { box -> collides(obj, newPosition, box) }

        private fun collides(obj: Any, newPosition: Point, box: Box) = when (obj) {
            is Player -> newPosition.x + 20 > box.position.x && newPosition.x - 20 < box.position.x + box.size.width &&
                    newPosition.y + 20 > box.position.y && newPosition.y - 20 < box.position.y + box.size.height

            is Bullet -> newPosition.x + 8 > box.position.x && newPosition.x - 8 < box.position.x + box.size.width &&
                    newPosition.y + 8 > box.position.y && newPosition.y - 8 < box.position.y + box.size.height

            else -> false
        }

        fun collidesWithWall(newPosition: Point) =
            newPosition.x < 0 || newPosition.x > width || newPosition.y < 0 || newPosition.y > height

    }

    private fun updatePlayers() {
        for ((_, player) in players) {
            val newPosition = Point(
                player.x + player.dx * player.speed,
                player.y + player.dy * player.speed
            )
            if (!gameWorld.collidesWithBox(player, newPosition) && !gameWorld.collidesWithWall(newPosition)) {
                player.x = newPosition.x
                player.y = newPosition.y
            }
        }
    }

    private val players = mutableMapOf<String, Player>()

    private var gameLoopJob = launchGame()

    val gameState: GameState get() = GameState(players, bullets, explosions, gameWorld.boxes, flags, scores)

    private val bullets = mutableMapOf<String, Bullet>()
    private val flags = listOf(
        Flag(0, bases[Team.RED]!!.x, bases[Team.RED]!!.y, Team.RED),
        Flag(1, bases[Team.BLUE]!!.x, bases[Team.BLUE]!!.y, Team.BLUE)
    )
    private val explosions = mutableListOf<HitEffect>()
    private val scores = mutableMapOf<Team, Int>()
    private var lastBulletId = 0
    private val mutex = Mutex()

    suspend fun addPlayer(player: Player) {
        println("adding... ${player.id}")
        mutex.withLock {
            players[player.id] = player
            println("Player added: ${player.id}")
            if (players.size > 0 && !gameLoopJob.isActive) {
                println("starting game loop...")
                gameLoopJob = launchGame()
                gameLoopJob.start()
                println("started")
            }
        }
        println("finish Player added: ${player.id}")
    }

    suspend fun removePlayer(playerId: String) {
        mutex.withLock {
            players.remove(playerId)?.removeConnection()
            if (players.isEmpty()) {
                gameLoopJob.cancel()
            }
        }
    }

    suspend fun fireBullet(playerId: String) {
        mutex.withLock {
            players[playerId]?.let { player ->
                val bulletId = "bullet_${lastBulletId++}"
                val bullet = Bullet(
                    bulletId,
                    player.x,
                    player.y,
                    player.x,
                    player.y,
                    player.angle,
                    playerId,
                    player.level
                )
                bullets[bulletId] = bullet
            }
        }
    }

    suspend private fun updateBullets() {
        mutex.withLock {
            for (bullet in bullets.values) {
                val newPosition = Point(
                    bullet.x + bullet.speed * cos(bullet.angle),
                    bullet.y + bullet.speed * sin(bullet.angle)
                )
                if (!gameWorld.collidesWithBox(bullet, newPosition)) {
                    bullet.x = newPosition.x
                    bullet.y = newPosition.y
                } else if ((bullet.x - bullet.xStart).absoluteValue > BULLET_RANGE ||
                    (bullet.y - bullet.yStart).absoluteValue > BULLET_RANGE
                ) {
                    explosions.add(HitEffect(bullet.x, bullet.y, 1f, type = 1))
                }
            }
            bullets.entries.removeAll {
                it.value.x < 0 || it.value.x > RANGE || it.value.y < 0 || it.value.y > RANGE ||
                        (it.value.x - it.value.xStart).absoluteValue > BULLET_RANGE + (it.value.level - 1) * 20 ||
                        (it.value.y - it.value.yStart).absoluteValue > BULLET_RANGE + (it.value.level - 1) * 20
            }
        }
    }

    private suspend fun checkBulletCollisions() {
        mutex.withLock {
            val bulletsToRemove = mutableSetOf<String>()
            for (bullet in bullets.values) {
                for (player in players.values) {
                    if (player.id != bullet.playerId && distance(bullet.x, bullet.y, player.x, player.y) < 20) {
                        player.health -= 10 // Bullet damage
                        explosions.add(HitEffect(bullet.x, bullet.y, 1f, type = 1))
                        bulletsToRemove.add(bullet.id)

                        if (player.health <= 0) {
                            explosions.add(HitEffect(player.x, player.y, 2f, type = 2))
                            // Player is dead, respawn
                            player.respawn()
                            players[bullet.playerId]?.levelUp() // Level up the player who shot the bullet
                        }
                    }
                }
            }
            bulletsToRemove.forEach { bullets.remove(it) }
        }
    }

    private suspend fun updateFlags() {
        mutex.withLock {
            for (flag in flags) {
                val flagBase = bases[flag.team]!!
                for (player in players.values) {

                    if (flag.holderId == null)
                        if (flag.team != player.team || flagBase.x != flag.x || flagBase.y != flag.y)
                            if (distance(player.x, player.y, flag.x, flag.y) < 20)
                                flag.holderId = player.id

                    if (flag.holderId == player.id) {
                        // move flag with player
                        flag.x = player.x
                        flag.y = player.y

                        // Check if the flag was delivered
                        val playerBase = bases[player.team]!!
                        if (distance(flag.x, flag.y, playerBase.x, playerBase.y) < 20) {
                            // Flag was delivered
                            flag.holderId = null
                            flag.x = flagBase.x
                            flag.y = flagBase.y

                            if (flag.team != player.team) {
                                scores[player.team] = 1 + (scores[player.team] ?: 0)
                                resetFlags()
                                players.values.forEach { it.respawn() }
                                // add explosion effect on the losing team flag
                                explosions.add(HitEffect(flag.x, flag.y, 2f, type = 2))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun resetFlags() {
        for (flag in flags) {
            flag.holderId = null
            val flagBase = bases[flag.team]!!
            flag.x = flagBase.x
            flag.y = flagBase.y
        }
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float =
        kotlin.math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1))

    private suspend fun broadcastGameState() {
        mutex.withLock {
            val gameStateJson = Json.encodeToString(gameState)
            for ((_, player) in players) {
                player.connection?.let {
                    it.coroutineScope.launch {
                        try {
                            it.session?.outgoing?.send(Frame.Text(gameStateJson))
                        } catch (e: Exception) {
                            println("Error sending game state to player ${player.id}: ${e.message}")
                        }
                    }
                }
            }
        }
    }

    suspend fun updatePlayerKeys(playerId: String, keys: Int) {
        val w = keys and 1 != 0
        val s = keys and 2 != 0
        val a = keys and 4 != 0
        val d = keys and 8 != 0
        val dx = if (a && !d) -1 else if (d && !a) 1 else 0
        val dy = if (w && !s) -1 else if (s && !w) 1 else 0
        mutex.withLock {
            players[playerId]?.let {
                it.dx = dx
                it.dy = dy
            }
        }
    }

    suspend fun updatePlayerAngle(playerId: String, angle: Float) {
        mutex.withLock {
            players[playerId]?.let {
                it.angle = angle
            }
        }
    }

    suspend fun updateExplosions() {
        mutex.withLock {
            explosions.forEach { it.update() }
            explosions.removeAll { it.scale <= 0f }
        }
    }

    suspend fun nextTeam(): Team =
        mutex.withLock {
            val teamCounts = players.values.groupingBy { it.team }.eachCount()
            if (teamCounts.getOrDefault(Team.RED, 0) > teamCounts.getOrDefault(
                    Team.BLUE,
                    0
                )
            ) Team.BLUE else Team.RED
        }

    suspend fun createPlayer() =
        Player(UUID.randomUUID().toString(), 0f, 0f, nextTeam(), 0, 0, 0f, 1)
            .apply {
                respawn()
                addPlayer(this)
            }
}

fun Player.levelUp() {
    level++
}

fun Player.respawn() {
    health = 100
    val spawnPoint = bases[team]!!
    x = spawnPoint.x
    y = spawnPoint.y
}

private data class PlayerConnection(val coroutineScope: CoroutineScope, val session: DefaultWebSocketServerSession?)

fun Player.setConnection(coroutineScope: CoroutineScope, session: DefaultWebSocketServerSession?) {
    connections[id] = PlayerConnection(coroutineScope, session)
}

fun Player.removeConnection() {
    CoroutineScope(Dispatchers.IO).launch {
        connections.remove(id)?.session?.close(
            CloseReason(
                CloseReason.Codes.NORMAL,
                "Player disconnected #removeConnection"
            )
        )
    }
}

private val connections = mutableMapOf<String, PlayerConnection>()
private val Player.connection get() = connections[id]
