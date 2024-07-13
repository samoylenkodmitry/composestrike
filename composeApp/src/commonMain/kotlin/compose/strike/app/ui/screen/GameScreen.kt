@file:OptIn(ExperimentalComposeUiApi::class)

package compose.strike.app.ui.screen

import Bullet
import FRONT_TALK_TO_LOCALHOST
import GameState
import Player
import RANGE
import Team
import WSS_PORT
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import backendPublicHost
import compose.strike.app.core.AppGraph
import compose.strike.app.core.Navigator
import compose.strike.app.core.data.Notification
import compose.strike.app.util.shader.BLACK_CHERRY_COSMOS_2_PLUS_EFFECT
import compose.strike.app.util.shader.ExplosionShader
import compose.strike.app.util.shader.explosionShader
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@OptIn(DelicateCoroutinesApi::class)
@Composable
fun GameScreen() {
    Box(modifier = Modifier.background(color = Color.White).fillMaxSize()) {

        var gameState by remember { mutableStateOf(GameState()) }
        var playerId by remember { mutableStateOf("") }
        var player by remember { mutableStateOf(Player("", 0f, 0f, Team.BLUE, 0, 0, 0f, 100, 1)) }
        val scope = rememberCoroutineScope()
        val client = HttpClient { install(WebSockets) }
        var connectedToServer by remember { mutableStateOf(false) }
        var nextFrame by remember { mutableStateOf(0L) }
        var socketSession by remember { mutableStateOf<DefaultClientWebSocketSession?>(null) }
        val explosionShader = remember { ExplosionShader(explosionShader) }
        val playerExplosionShader = remember { ExplosionShader(BLACK_CHERRY_COSMOS_2_PLUS_EFFECT) }

        LaunchedEffect(Unit) {
            try {
                val block: suspend DefaultClientWebSocketSession.() -> Unit = {
                    socketSession = this
                    val playerIdFrame = incoming.receive()
                    if (playerIdFrame is Frame.Text) {
                        playerId = playerIdFrame.readText()
                        connectedToServer = true
                        println("Connected to server with player ID: $playerId")
                    }

                    // Receive the initial game state
                    val initialGameStateFrame = incoming.receive()
                    if (initialGameStateFrame is Frame.Text) {
                        gameState = Json.decodeFromString<GameState>(initialGameStateFrame.readText())
                    }

                    while (connectedToServer && !incoming.isClosedForReceive) {
                        try {
                            for (frame in incoming) {
                                if (frame is Frame.Text) {
                                    val receivedGameState = Json.decodeFromString<GameState>(frame.readText())
                                    if (receivedGameState.scores != gameState.scores) {
                                        AppGraph.notifications.tryEmit(
                                            Notification.Info(
                                                "Blue: ${receivedGameState.scores[Team.BLUE] ?: 0} Red: ${receivedGameState.scores[Team.RED] ?: 0}"
                                            )
                                        )
                                    }
                                    gameState = receivedGameState

                                    // Update the player's position based on the server's state
                                    gameState.players[playerId]?.let { updated ->
                                        if (updated.level > player.level) {
                                            AppGraph.notifications.tryEmit(Notification.Info("Level up! You are now level ${updated.level}"))
                                        }
                                        player = updated
                                    }
                                    nextFrame++
                                }
                            }
                        } catch (e: Exception) {
                            Notification.Error("Error receiving message from server: ${e.message}")
                            println("Error receiving message from server: ${e.message}")
                            connectedToServer = false
                        }
                    }
                }
                if (FRONT_TALK_TO_LOCALHOST) {
                    client.webSocket(
                        method = HttpMethod.Get,
                        host = backendPublicHost,
                        port = WSS_PORT,
                        path = "/game",
                        block = block
                    )
                } else {
                    client.wss(
                        method = HttpMethod.Get,
                        host = backendPublicHost,
                        port = WSS_PORT,
                        path = "/game",
                        block = block
                    )
                }
            } catch (e: Exception) {
                Notification.Error("Error connecting to server: ${e.message}")
                println("Error connecting to server: ${e.message}")
                connectedToServer = false
            } finally {
                println("client end listening from server $playerId")
                client.close()
            }

        }

        if (connectedToServer) {
            var pressedKeys by remember { mutableIntStateOf(0) }
            val textMeasurer = rememberTextMeasurer()
            Canvas(modifier = Modifier
                .fillMaxSize()
                .background(Color.LightGray)
                .pointerInput(Unit) {
                    // Calculate angle based on mouse position
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val mouse = event.changes.firstOrNull()?.position
                            if (mouse != null && mouse.isSpecified) {
                                val screenCenter = Offset(size.width * 1f / 2, size.height * 1f / 2)
                                val a = atan2(mouse.y - screenCenter.y, mouse.x - screenCenter.x)
                                player.angle = a
                                scope.launch { socketSession?.send(Frame.Text("A:${a}")) }
                            }
                        }
                    }

                }
                .clickable(interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        scope.launch { socketSession?.send(Frame.Text("F")) }
                    })
                .onKeyEvent {
                    println(it)
                    val isPressed = it.type == KeyEventType.KeyDown
                    pressedKeys = when (it.key) {
                        Key.W -> if (isPressed) pressedKeys or 1 else pressedKeys and 0b1110
                        Key.S -> if (isPressed) pressedKeys or 2 else pressedKeys and 0b1101
                        Key.A -> if (isPressed) pressedKeys or 4 else pressedKeys and 0b1011
                        Key.D -> if (isPressed) pressedKeys or 8 else pressedKeys and 0b0111
                        else -> return@onKeyEvent false
                    }
                    scope.launch {
                        socketSession?.send(Frame.Text("K:$pressedKeys"))
                    }
                    true // Consume the key event
                }
            ) {
                nextFrame // Trigger recomposition


                // Keep the player at the center of the screen
                val offsetX = size.width / 2 - player.x
                val offsetY = size.height / 2 - player.y
                translate(offsetX, offsetY) {
                    // Drawing the background
                    drawRect(Color.White, size = Size(RANGE * 1f, RANGE * 1f))
                    // Draw floor grid lines
                    val gridSize = 100
                    val gridColor = Color.Gray
                    for (i in 0 until RANGE step gridSize) {
                        drawLine(color = gridColor, Offset(i * 1f, 0f), Offset(i * 1f, RANGE * 1f), strokeWidth = 1f)
                        drawLine(color = gridColor, Offset(0f, i * 1f), Offset(RANGE * 1f, i * 1f), strokeWidth = 1f)
                    }

                    // Drawing players
                    gameState.players.values.forEach { player ->
                        drawCircle(
                            color = if (player.id == playerId) Color.Green else Color.Gray,
                            radius = 20f,
                            center = Offset(player.x, player.y)
                        )
                        // Draw health bar
                        drawArc(
                            brush = Brush.sweepGradient(
                                listOf(Color.Red, Color.Green),
                                center = Offset(player.x, player.y)
                            ),
                            startAngle = 0f,
                            sweepAngle = 360f * (player.health / 100f),
                            useCenter = false,
                            topLeft = Offset(player.x - 20, player.y - 20),
                            size = Size(40f, 40f),
                            style = Stroke(4f)
                        )

                        // Draw the gun
                        val gunLength = 20f
                        val gunStartX = player.x + 10 * cos(player.angle)
                        val gunStartY = player.y + 10 * sin(player.angle)
                        val gunEndX = gunStartX + gunLength * cos(player.angle)
                        val gunEndY = gunStartY + gunLength * sin(player.angle)
                        drawLine(
                            color = Color.Black,
                            start = Offset(gunStartX, gunStartY),
                            end = Offset(gunEndX, gunEndY),
                            strokeWidth = 6f
                        )

                        translate(player.x, player.y - 60f) {
                            // draw level
                            drawText(
                                text = player.level.toString(),
                                textMeasurer = textMeasurer,
                            )
                        }
                    }

                    // Drawing bullets
                    gameState.bullets.forEach { (id, bullet) ->
                        drawCircle(
                            color = bullet.color(),
                            radius = 8f,
                            center = Offset(bullet.x, bullet.y)
                        )
                    }

                    // Drawing explosions
                    gameState.explosions.forEach { explosion ->
                        translate(explosion.x - 50f, explosion.y - 50f) {
                            val shader = if (explosion.type == 1) explosionShader else playerExplosionShader
                            drawRect(
                                shader.drawRectShader(explosion.progress * 0.08f, 100f, 100f),
                                topLeft = Offset.Zero,
                                size = Size(100f, 100f),
                            )
                        }
                    }
                    // Drawing boxes
                    gameState.boxes.forEach { box ->
                        drawRect(
                            color = Color.DarkGray,
                            topLeft = Offset(box.position.x, box.position.y),
                            size = Size(box.size.width, box.size.height)
                        )
                    }
                    // Drawing flags
                    gameState.flags.forEach { flag ->
                        drawCircle(
                            color = flag.team.color,
                            radius = 10f,
                            center = Offset(flag.x, flag.y)
                        )
                    }
                }


                // minimap
                drawRect(Color.Black, size = Size(100f, 100f))
                drawRect(Color.White, size = Size(100f, 100f), style = Stroke(2f))
                gameState.players.values.forEach { player ->
                    drawCircle(
                        color = if (player.id == playerId) Color.Green else Color.Blue,
                        radius = 2f,
                        center = Offset(player.x / (RANGE / 100), player.y / (RANGE / 100))
                    )
                }
                // game score on the center top
                // flags
                gameState.flags.forEach { flag ->
                    drawCircle(
                        color = flag.team.color,
                        radius = 2f,
                        center = Offset(flag.x / (RANGE / 100), flag.y / (RANGE / 100))
                    )
                }

            }
            Text(
                text = "Blue: ${gameState.scores[Team.BLUE] ?: 0} Red: ${gameState.scores[Team.RED] ?: 0}",
                style = MaterialTheme.typography.h5,
                color = Color.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Connecting to server...", style = MaterialTheme.typography.h5)
            }
        }
        // Back button
        IconButton(
            onClick = { Navigator.main() },
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.padding(24.dp))
        }
    }
}

private val Team.color: Color
    get() = when (this) {
        Team.BLUE -> Color.Blue
        Team.RED -> Color.Red
    }
val bulletColors = mutableMapOf<Int, Color>()
fun Bullet.color() = bulletColors.getOrPut(level) {
    Color.hsv(((level * 30) % 360).toFloat(), 0.8f, 0.8f)
}

val gameMutex = kotlinx.coroutines.sync.Mutex()

