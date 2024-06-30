@file:OptIn(ExperimentalComposeUiApi::class)

package compose.strike.app.ui.screen

import Bullet
import GameState
import Player
import RANGE
import SERVER_PORT
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
import androidx.compose.ui.unit.dp
import compose.strike.app.core.AppGraph
import compose.strike.app.core.Navigator
import compose.strike.app.core.data.Notification
import hostName
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

@OptIn(DelicateCoroutinesApi::class)
@Composable
fun GameScreen() {
    Box(modifier = Modifier.background(color = Color.White).fillMaxSize()) {

        var gameState by remember { mutableStateOf(GameState()) }
        var playerId by remember { mutableStateOf("") }
        var player by remember { mutableStateOf(Player("", 0f, 0f, 0, 0, 0f, 100, 1)) }
        val scope = rememberCoroutineScope()
        val client = HttpClient { install(WebSockets) }
        var connectedToServer by remember { mutableStateOf(false) }
        var nextFrame by remember { mutableStateOf(0L) }
        var socketSession by remember { mutableStateOf<DefaultClientWebSocketSession?>(null) }

        LaunchedEffect(Unit) {
            try {
                client.webSocket(method = HttpMethod.Get, host = hostName, port = SERVER_PORT, path = "/game") {
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

                    // Drawing players
                    gameState.players.values.forEach { player ->
                        drawCircle(
                            color = if (player.id == playerId) Color.Green else Color.Blue,
                            radius = 20f,
                            center = Offset(player.x, player.y)
                        )
                        // Draw health bar
                        drawArc(
                            brush = Brush.sweepGradient(listOf(Color.Red, Color.Green), center = Offset(player.x, player.y)),
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
                
            }
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

val bulletColors = mutableMapOf<Int, Color>()
fun Bullet.color() = bulletColors.getOrPut(level) {
    Color.hsv(((level * 30) % 360).toFloat(), 0.8f, 0.8f)
}