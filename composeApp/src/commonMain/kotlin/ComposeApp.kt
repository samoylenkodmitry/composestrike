@file:Suppress("ktlint:standard:no-wildcard-imports", "ktlint:standard:function-naming")

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.strike.app.core.AppGraph
import compose.strike.app.core.Navigator
import compose.strike.app.core.ViewModel
import compose.strike.app.core.data.AuthState
import compose.strike.app.core.data.Notification
import compose.strike.app.ui.components.NotificationPopup
import compose.strike.app.ui.screen.*
import kotlinx.coroutines.*
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    MaterialTheme {
        val screenState = Navigator.screenFlow.collectAsState(Screen.Main)
        val scope = rememberCoroutineScope()
        LaunchedEffect(Unit) {
            scope.launch {
                ViewModel.checkAuth()
            }
            scope.launch {
                AppGraph.auth.collect { authState ->
                    when (authState) {
                        is AuthState.Authenticated -> {
                            AppGraph.notifications.tryEmit(Notification.Info("Hello!"))
                            Navigator.main()
                        }

                        AuthState.AuthError -> {
                            Navigator.error("Could not authenticate")
                        }

                        AuthState.Authenticating -> {
                            Navigator.splash()
                        }

                        AuthState.Unauthenticated -> {
                            Navigator.splash()
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when (val screen = screenState.value) {
                Screen.Splash -> SplashScreen()
                Screen.Main -> MainScreen()
                Screen.UserProfile -> UserProfileScreen()
                Screen.Login -> LoginScreen()
                Screen.Game -> GameScreen()
                is Screen.Error -> ErrorScreen(screen.message)
            }
            Box(modifier = Modifier.width(300.dp).height(100.dp).align(Alignment.BottomEnd)) {
                NotificationPopup()
            }
        }
    }
}
