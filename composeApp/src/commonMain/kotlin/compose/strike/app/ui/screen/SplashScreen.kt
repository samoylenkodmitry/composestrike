package compose.strike.app.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.strike.app.util.shader.BLACK_CHERRY_COSMOS_2_PLUS_EFFECT
import compose.strike.app.util.shader.shaderBackground

@Composable
fun SplashScreen() {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .shaderBackground(BLACK_CHERRY_COSMOS_2_PLUS_EFFECT, 0.1f)
                .padding(16.dp),
    ) {
        Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Compose Strike", style = MaterialTheme.typography.h1)
        }
    }
}
