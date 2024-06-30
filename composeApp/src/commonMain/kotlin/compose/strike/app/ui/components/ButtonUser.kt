package compose.strike.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import compose.strike.app.core.AppGraph
import compose.strike.app.core.Navigator
import compose.strike.app.core.data.AuthState
import compose.strike.app.ui.Theme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ButtonUser(modifier: Modifier = Modifier) {
    var rTarget by remember { mutableFloatStateOf(0f) }
    val animRotation by animateFloatAsState(
        targetValue = rTarget,
        animationSpec = spring(),
        label = "Rotation",
    )
    val scope = rememberCoroutineScope()
    var animateRotation by remember { mutableStateOf(false) }

    LaunchedEffect(animateRotation) {
        while (animateRotation) {
            rTarget += (-360..360).random()
            delay(500)
        }
        rTarget = 0f
    }
    LaunchedEffect(Unit) {
        AppGraph.auth.collect { authState ->
            when (authState) {
                is AuthState.Authenticated -> {
                    animateRotation = false
                }

                AuthState.AuthError -> {
                    animateRotation = false
                }

                AuthState.Authenticating -> {
                    animateRotation = true
                }

                AuthState.Unauthenticated -> {
                }
            }
        }
    }
    IconButton(onClick = {
        scope.launch {
            Navigator.userProfile()
        }
    }, modifier = modifier) {
        Icon(
            Theme.Icons.User,
            contentDescription = "Logout",
            modifier =
                Modifier.graphicsLayer {
                    rotationZ = animRotation
                },
        )
    }
}
