@file:Suppress("ktlint:standard:no-wildcard-imports")

package compose.strike.app.core

import Endpoint
import EndpointWithArg
import Endpoints
import ProofOfWork
import RefreshTokenRequest
import UpdateNickRequest
import compose.strike.app.core.data.AuthState
import backendPublicUrl
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import solveChallenge

object Api {
    private val jsonInstance =
        Json {
            isLenient = true
            ignoreUnknownKeys = true
        }
    private val authClient =
        HttpClient {
            install(ContentNegotiation) { json(jsonInstance) }
            defaultRequest { url(backendPublicUrl) }
        }
    private val requestsClient =
        HttpClient {
            install(ContentNegotiation) { json(jsonInstance) }
            defaultRequest { url(backendPublicUrl) }
            install(Auth) {
                bearer {
                    loadTokens {
                        val auth = Storage.loadAuth()
                        if (auth?.sessionToken?.isNotBlank() == true && auth.refreshToken.isNotBlank()) {
                            AppGraph.auth.tryEmit(AuthState.Authenticated(auth))
                            BearerTokens(
                                accessToken = auth.sessionToken,
                                refreshToken = auth.refreshToken,
                            )
                        } else {
                            requestAndSaveNewTokens()
                        }
                    }
                    refreshTokens {
                        oldTokens?.run {
                            BearerTokens(refreshSessionToken(refreshToken).sessionToken, refreshToken)
                        } ?: requestAndSaveNewTokens()
                    }
                }
            }
        }

    private suspend fun requestAndSaveNewTokens(): BearerTokens =
        authenticate().let {
            CoroutineScope(Dispatchers.Default).launch {
                Storage.saveAuth(it)
            }
            BearerTokens(
                accessToken = it.sessionToken,
                refreshToken = it.refreshToken,
            )
        }

    /**
     * Get a proof of work challenge
     *
     * @return [Challenge] with challenge and difficulty prefix
     */
    private suspend fun getPow() = authClient doGet Endpoints.powGet

    /**
     * Post a proof of work solution
     *
     * @param pow ProofOfWork, solution to the challenge
     * @return [AuthResult] with new session token
     */
    private suspend fun postPow(pow: ProofOfWork) = authClient doPost Endpoints.powPost(pow)

    /**
     * Authenticate new user
     *
     * @return [AuthResult] with new session token
     */
    private suspend fun authenticate() =
        run {
            AppGraph.auth.tryEmit(AuthState.Authenticating)
            postPow(solveChallenge(getPow())).also { auth ->
                AppGraph.auth.tryEmit(AuthState.Authenticated(auth))
            }
        }

    /**
     * Refresh token
     *
     * @param refreshToken String refresh token, used to get new session token
     * @return [AuthResult] with new session token
     */
    private suspend fun refreshSessionToken(refreshToken: String) =
        authClient doPost Endpoints.tokenRefresh(RefreshTokenRequest(refreshToken))



    private suspend inline infix fun <reified T : Any> HttpClient.doGet(endpoint: Endpoint<T>): T = get(endpoint.path).body()

    private suspend inline infix fun <reified A : Any, reified T : Any> HttpClient.doPost(endpoint: EndpointWithArg<A, T>): T =
        post(endpoint.path) {
            contentType(ContentType.Application.Json)
            setBody(endpoint.arg)
        }.body()

    suspend fun logout() {
        Storage.clearData()
        requestAndSaveNewTokens()
    }

    suspend fun checkAuth() {
        val auth = Storage.loadAuth()
        if (auth?.sessionToken?.isNotBlank() == true && auth.refreshToken.isNotBlank()) {
            AppGraph.auth.tryEmit(AuthState.Authenticated(auth))
        } else {
            requestAndSaveNewTokens()
        }
    }

    suspend fun doLogin(refreshToken: String): Boolean {
        refreshSessionToken(refreshToken).let {
            CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
                Storage.saveAuth(it)
                AppGraph.auth.tryEmit(AuthState.Authenticated(it))
            }
            return true
        }
    }

    suspend fun updateNick(newNick: String): Boolean =
        (requestsClient doPost Endpoints.updateNick(UpdateNickRequest(newNick))).also {
            if (it) {
                val auth = Storage.loadAuth() ?: return@also
                auth.user.nick = newNick
                auth.let { Storage.saveAuth(it) }
                AppGraph.auth.tryEmit(AuthState.Authenticated(auth))
            }
        }

}
