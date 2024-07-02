package compose.strike.app

import BULLET_RANGE
import Bullet
import GameState
import HitEffect
import Player
import RANGE
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import speed
import update
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.sin

class Game {
    private fun launchGame() = CoroutineScope(Dispatchers.Default).launch {
        println("Game loop started")
        while (isActive && players.isNotEmpty()) {
            updatePlayers()
            updateBullets()
            updateExplosions()
            checkBulletCollisions()
            broadcastGameState()
            delay(16) // Roughly 60 updates per second
        }
        println("Game loop stopped")
    }

    private fun updatePlayers() {
        with(players) {
            forEach { (_, player) ->
                player.x += player.dx * player.speed
                player.y += player.dy * player.speed
            }
        }
    }

    private val players = mutableMapOf<String, Player>()
    private var gameLoopJob = launchGame()

    val gameState: GameState
        get() = GameState(players, bullets, explosions)
    private val bullets = mutableMapOf<String, Bullet>()
    private val explosions = mutableListOf<HitEffect>()
    private var lastBulletId = 0
    private val mutex = Mutex()

    suspend fun addPlayer(player: Player) {
        mutex.withLock {
            players[player.id] = player
            if (players.size > 0 && !gameLoopJob.isActive) {
                gameLoopJob = launchGame()
                gameLoopJob.start()
            }
        }
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
                bullet.x += bullet.speed * cos(bullet.angle)
                bullet.y += bullet.speed * sin(bullet.angle)
                if ((bullet.x - bullet.xStart).absoluteValue > BULLET_RANGE ||
                    (bullet.y - bullet.yStart).absoluteValue > BULLET_RANGE
                ) {
                    explosions.add(HitEffect(bullet.x, bullet.y, 1f, type = 1 ))
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

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return kotlin.math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1))
    }

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
}

fun Player.levelUp() {
    level++
}

fun Player.respawn() {
    health = 100
    x = (0..RANGE).random() * 1f
    y = (0..RANGE).random() * 1f
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
