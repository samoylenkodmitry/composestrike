package compose.strike.app.ui.screen

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import compose.strike.app.core.Navigator
import compose.strike.app.util.shader.BLACK_CHERRY_COSMOS_2_PLUS_EFFECT
import compose.strike.app.util.shader.shaderBackground

@Composable
fun BoxScope.ErrorScreen(message: String) {
    Column(
        modifier =
            Modifier
                .align(Alignment.Center)
                .shaderBackground(BLACK_CHERRY_COSMOS_2_PLUS_EFFECT, 0.05f),
    ) {
        Text("Error: $message")
        Button(onClick = {
            Navigator.main()
        }) {
            Text("Back")
        }
    }
}
