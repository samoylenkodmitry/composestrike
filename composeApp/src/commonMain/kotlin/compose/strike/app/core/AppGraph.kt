package compose.strike.app.core

import compose.strike.app.core.data.AuthState
import compose.strike.app.core.data.Notification
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

object AppGraph {
    val auth =
        MutableSharedFlow<AuthState>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST).apply {
            tryEmit(AuthState.Unauthenticated)
        }
    val notifications = MutableSharedFlow<Notification>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
}
