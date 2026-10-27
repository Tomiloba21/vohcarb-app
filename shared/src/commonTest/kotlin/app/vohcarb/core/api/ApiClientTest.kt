package app.vohcarb.core.api

import app.vohcarb.core.session.Session
import app.vohcarb.core.session.SessionStore
import app.vohcarb.progress.ProgressApi
import app.vohcarb.progress.SyncRequest
import com.russhwolf.settings.MapSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ApiClientTest {

    private val requests = mutableListOf<HttpRequestData>()
    private val sessions = SessionStore(MapSettings(), defaultServerUrl = "http://server")

    private fun client(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): ApiClient {
        val http = HttpClient(MockEngine { request -> requests += request; handler(request) }) {
            expectSuccess = false
            install(ContentNegotiation) { json(ApiClient.json) }
        }
        return ApiClient(sessions, http)
    }

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    @Test
    fun sendsTheTokenAndAJsonBody() = runTest {
        sessions.signIn(Session("abc", 1, "Ada", null))
        val api = ProgressApi(client { json("""{"revision":5,"hasMore":false,"changes":[]}""") })

        val result = api.sync(SyncRequest(sinceRevision = 5, changes = emptyList()))

        val request = requests.single()
        assertEquals("http://server/api/v1/me/sync", request.url.toString())
        assertEquals("Bearer abc", request.headers[HttpHeaders.Authorization])
        assertEquals("""{"sinceRevision":5,"changes":[]}""", request.body.toByteArray().decodeToString())
        assertEquals(5, result.revision)
    }

    @Test
    fun problemDetailsBecomeTheErrorMessage() = runTest {
        val api = client { json("""{"title":"Not Found","status":404,"detail":"No word 'zzz'"}""", HttpStatusCode.NotFound) }

        val error = assertFailsWith<ApiException> { api.get<String>("/api/v1/words/zzz") }

        assertEquals(404, error.status)
        assertEquals("No word 'zzz'", error.message)
    }

    @Test
    fun aRejectedTokenSignsOut() = runTest {
        sessions.signIn(Session("expired", 1, "Ada", null))
        val api = client { json("{}", HttpStatusCode.Unauthorized) }

        assertFailsWith<ApiException> { api.get<String>("/api/v1/me") }

        assertNull(sessions.session.value)
    }

    @Test
    fun leavesOutEmptyQueryParameters() = runTest {
        val api = client { json("[]") }

        api.get<List<String>>("/api/v1/me/feed", "size" to null, "q" to "eph")

        assertEquals("http://server/api/v1/me/feed?q=eph", requests.single().url.toString())
    }
}
