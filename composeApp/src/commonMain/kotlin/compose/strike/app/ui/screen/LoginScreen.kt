package compose.strike.app.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import compose.strike.app.core.AppGraph
import compose.strike.app.core.Navigator
import compose.strike.app.core.ViewModel
import compose.strike.app.core.data.Notification
import compose.strike.app.ui.Theme
import compose.strike.app.util.HazeStyle
import compose.strike.app.util.haze
import compose.strike.app.util.hazeChild
import compose.strike.app.util.rememberHaze
import compose.strike.app.util.shader.BLACK_CHERRY_COSMOS_2_PLUS_EFFECT
import compose.strike.app.util.shader.shaderBackground
import kotlinx.coroutines.launch

@Composable
fun BoxScope.LoginScreen() {
    val hazeState = rememberHaze()
    Box(
        modifier =
            Modifier.fillMaxSize(),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .haze(
                        hazeState,
                    ).shaderBackground(BLACK_CHERRY_COSMOS_2_PLUS_EFFECT, 0.2f),
        )
        // Back button
        IconButton(
            onClick = { Navigator.userProfile() },
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .hazeChild(
                        state = hazeState,
                        shape = CircleShape,
                        style =
                            HazeStyle(
                                blurRadius = 16.dp,
                                tint = Color.White.copy(alpha = 0.4f),
                            ),
                    ),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Column(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .padding(16.dp)
                    .hazeChild(
                        state = hazeState,
                        shape = RoundedCornerShape(16.dp),
                        style =
                            HazeStyle(
                                blurRadius = 16.dp,
                                tint = Color.White.copy(alpha = 0.4f),
                            ),
                    ),
        ) {
            // Text field for user ID
            var userId by remember { mutableStateOf("") }
            Box {
                TextField(
                    value = userId,
                    onValueChange = { userId = it },
                    label = { Text("User ID") },
                )
                val clipboardManager = LocalClipboardManager.current
                val scope = rememberCoroutineScope()
                // Paste button
                IconButton(
                    onClick = {
                        scope.launch {
                            clipboardManager.getText()?.let { userId = it.text }
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    Icon(Theme.Icons.Clipboard, contentDescription = "Paste")
                }
            }
            // Button to login
            val scope = rememberCoroutineScope()
            Button(onClick = {
                scope.launch {
                    if (!ViewModel.doLogin(userId)) {
                        AppGraph.notifications.tryEmit(Notification.Error("Could not login"))
                    } else {
                        AppGraph.notifications.tryEmit(Notification.Info("Logged in"))
                        Navigator.userProfile()
                    }
                }
            }, modifier = Modifier.padding(36.dp).align(Alignment.CenterHorizontally)) {
                Text("Login")
            }
        }
    }
}
