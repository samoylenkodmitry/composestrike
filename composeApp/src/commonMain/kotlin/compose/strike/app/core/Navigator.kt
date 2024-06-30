package compose.strike.app.core

import compose.strike.app.ui.screen.Screen
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

object Navigator {
    val screenFlow = MutableSharedFlow<Screen>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    fun main() {
        screenFlow.tryEmit(Screen.Main)
    }

    fun userProfile() {
        screenFlow.tryEmit(Screen.UserProfile)
    }
    
    fun game() {
        screenFlow.tryEmit(Screen.Game)
    }

    fun error(message: String) {
        screenFlow.tryEmit(Screen.Error(message))
    }

    fun splash() {
        screenFlow.tryEmit(Screen.Splash)
    }

    fun login() {
        screenFlow.tryEmit(Screen.Login)
    }

}
