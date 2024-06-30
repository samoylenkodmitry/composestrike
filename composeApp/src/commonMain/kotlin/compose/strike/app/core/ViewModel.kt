package compose.strike.app.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext

object ViewModel {



    suspend fun logout() {
        withContext(Dispatchers.Default + SupervisorJob()) {
            try {
                Api.logout()
            } catch (e: Exception) {
                e.message ?: "Error $e"
            }
        }
    }

    suspend fun checkAuth() {
        withContext(Dispatchers.Default + SupervisorJob()) {
            try {
                Api.checkAuth()
            } catch (e: Exception) {
                e.message ?: "Error $e"
            }
        }
    }

    suspend fun doLogin(userId: String): Boolean =
        withContext(Dispatchers.Default + SupervisorJob()) {
            try {
                Api.doLogin(userId)
            } catch (e: Exception) {
                e.message ?: "Error $e"
                false
            }
        }

    suspend fun updateNick(newNick: String): Boolean =
        withContext(Dispatchers.Default + SupervisorJob()) {
            try {
                Api.updateNick(newNick)
            } catch (e: Exception) {
                e.message ?: "Error $e"
                false
            }
        }

}
