package compose.strike.app

import Bullet
import GameState
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
import kotlin.math.cos
import kotlin.math.sin

class Game {
    fun launchGame() = CoroutineScope(Dispatchers.Default).launch {
        println("Game loop started")
        while (isActive && players.isNotEmpty()) {
            updatePlayers()
            updateBullets()
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

    private var gameLoopJob = launchGame()

    private val players = mutableMapOf<String, Player>()
    val gameState: GameState
        get() = GameState(players, bullets)
    private val bullets = mutableMapOf<String, Bullet>()
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
            }
            bullets.entries.removeAll { it.value.x < 0 || it.value.x > RANGE || it.value.y < 0 || it.value.y > RANGE }
        }
    }

    private suspend fun checkBulletCollisions() {
        mutex.withLock {
            val bulletsToRemove = mutableSetOf<String>()
            for (bullet in bullets.values) {
                for (player in players.values) {
                    if (player.id != bullet.playerId && distance(bullet.x, bullet.y, player.x, player.y) < 20) {
                        player.health -= 10 // Bullet damage
                        bulletsToRemove.add(bullet.id)

                        if (player.health <= 0) {
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
                        it.session?.outgoing?.send(Frame.Text(gameStateJson))
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
