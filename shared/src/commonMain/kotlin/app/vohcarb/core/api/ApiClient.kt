package app.vohcarb.core.api

import app.vohcarb.core.session.SessionStore
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Thin wrapper over Ktor for the Vohcarb API: adds the server address and the bearer token,
 * and turns error responses (RFC 9457 problem details) into [ApiException].
 */
class ApiClient(
    @PublishedApi internal val session: SessionStore,
    @PublishedApi internal val http: HttpClient = defaultHttpClient(),
) {

    suspend inline fun <reified T> get(path: String, vararg query: Pair<String, Any?>): T =
        call(HttpMethod.Get, path, query).body()

    suspend inline fun <reified B, reified T> post(path: String, body: B): T =
        call(HttpMethod.Post, path) { jsonBody(body) }.body()

    suspend inline fun <reified B, reified T> put(path: String, body: B): T =
        call(HttpMethod.Put, path) { jsonBody(body) }.body()

    suspend inline fun <reified B, reified T> patch(path: String, body: B): T =
        call(HttpMethod.Patch, path) { jsonBody(body) }.body()

    /** A POST with no body (the action is in the path) that returns JSON. */
    suspend inline fun <reified T> postEmpty(path: String): T = call(HttpMethod.Post, path).body()

    /** For calls with a JSON body whose response doesn't matter (e.g. 204 No Content). */
    suspend inline fun <reified B> sendJson(method: HttpMethod, path: String, body: B) {
        call(method, path) { jsonBody(body) }
    }

    /** For calls whose response body doesn't matter (e.g. 204 No Content). */
    suspend fun send(method: HttpMethod, path: String, vararg query: Pair<String, Any?>) {
        call(method, path, query)
    }

    /**
     * Opens a WebSocket to [path] on the server (http → ws, https → wss), signed in like other calls with the
     * bearer header on the handshake. Returns when the socket closes.
     */
    suspend fun webSocket(path: String, block: suspend DefaultClientWebSocketSession.() -> Unit) {
        val base = session.serverUrl
        if (base.isBlank()) throw ApiException(0, "No server address set")
        val url = base.replaceFirst(Regex("^http"), "ws") + path
        http.webSocket(urlString = url, request = { session.session.value?.let { bearerAuth(it.accessToken) } }, block = block)
    }

    @PublishedApi
    internal inline fun <reified B> HttpRequestBuilder.jsonBody(body: B) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    @PublishedApi
    internal suspend fun call(
        method: HttpMethod,
        path: String,
        query: Array<out Pair<String, Any?>> = emptyArray(),
        block: HttpRequestBuilder.() -> Unit = {},
    ): HttpResponse {
        val base = session.serverUrl
        if (base.isBlank()) throw ApiException(0, "No server address set")
        val response = try {
            http.request(base + path) {
                this.method = method
                query.forEach { (name, value) -> if (value != null) parameter(name, value) }
                session.session.value?.let { bearerAuth(it.accessToken) }
                block()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ApiException(0, "Can't reach the server. Check your connection and the server address.", e)
        }
        if (response.status.isSuccess()) return response

        // A token the server no longer accepts: sign out so the app shows the sign-in screen.
        if (response.status == HttpStatusCode.Unauthorized && session.session.value != null) session.signOut()
        val problem = runCatching { json.decodeFromString<Problem>(response.bodyAsText()) }.getOrNull()
        throw ApiException(response.status.value, problem?.detail ?: problem?.title ?: response.status.description)
    }

    @Serializable
    private data class Problem(val title: String? = null, val detail: String? = null, val status: Int? = null)

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            coerceInputValues = true
        }

        fun defaultHttpClient() = HttpClient {
            expectSuccess = false
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                requestTimeoutMillis = 20_000
            }
            install(WebSockets) { pingIntervalMillis = 20_000 } // live games
        }
    }
}

/** [status] is 0 when the server couldn't be reached at all. */
class ApiException(val status: Int, message: String, cause: Throwable? = null) : Exception(message, cause) {
    val isNotFound get() = status == 404
}

/** A message that can be shown to the user as is. */
val Throwable.userMessage: String
    get() = (this as? ApiException)?.message ?: message ?: "Something went wrong"
