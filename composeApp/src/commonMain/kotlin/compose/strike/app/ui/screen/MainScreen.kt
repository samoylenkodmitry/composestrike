package compose.strike.app.ui.screen

import AUTH_ENABLED
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import compose.strike.app.core.Navigator
import compose.strike.app.ui.components.ButtonUser
import compose.strike.app.util.HazeStyle
import compose.strike.app.util.haze
import compose.strike.app.util.hazeChild
import compose.strike.app.util.rememberHaze
import compose.strike.app.util.shader.BLACK_CHERRY_COSMOS_2_PLUS_EFFECT
import compose.strike.app.util.shader.explosionShader
import compose.strike.app.util.shader.shaderBackground

@Suppress("ktlint:standard:function-naming")
@Composable
fun MainScreen() {
    val hazeState = rememberHaze()
    Box(
        modifier =
        Modifier
            .fillMaxSize(),
    ) {
        Box(
            modifier =
            Modifier.haze(state = hazeState).fillMaxSize().shaderBackground(BLACK_CHERRY_COSMOS_2_PLUS_EFFECT, 0.009f),
        )
        if (AUTH_ENABLED) {
            ButtonUser(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 20.dp, end = 20.dp)
                    .hazeChild(
                        state = hazeState,
                        shape = CircleShape,
                        style =
                        HazeStyle(
                            blurRadius = 16.dp,
                            tint = Color.White.copy(alpha = 0.4f),
                        ),
                    ),
            )
        }
        Column(
            modifier =
            Modifier
                .fillMaxWidth(fraction = 0.7f)
                .align(Alignment.Center)
                .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {

            Box(
                modifier =
                Modifier.align(Alignment.CenterHorizontally),
            ) {
                val scrollState = rememberLazyListState()
                LazyColumn(
                    state = scrollState,
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    item {
                            Text(
                                "Compose Strike",
                                style = MaterialTheme.typography.h3,
                                modifier = Modifier.align(Alignment.Center),
                            )
                    }
                    item {
                        Button(onClick = {
                            Navigator.game()
                        }) {
                            Text("Join Game")
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.height(48.dp))
                    }
                }
            }
        }
    }
}
