package app.vohcarb.groups

import app.vohcarb.catalog.CatalogApi
import app.vohcarb.catalog.WordSummaryDto
import app.vohcarb.core.api.ApiClient
import app.vohcarb.core.session.Session
import app.vohcarb.core.session.SessionStore
import app.vohcarb.core.ui.Load
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
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GroupViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    // The mock engine answers on its own thread, so the list of requests is guarded.
    private val requestsLock = Mutex()
    private val requests = mutableListOf<Pair<String, String>>() // "METHOD path" to body

    private suspend fun recorded() = requestsLock.withLock { requests.toList() }
    private val routes = mutableMapOf<String, suspend MockRequestHandleScope.() -> HttpResponseData>()

    private val api = ApiClient(
        SessionStore(MapSettings(), "http://server").apply { signIn(Session("token", 1, "Ada", null)) },
        HttpClient(MockEngine { request -> handle(request) }) {
            expectSuccess = false
            install(ContentNegotiation) { json(ApiClient.json) }
        },
    )

    private suspend fun MockRequestHandleScope.handle(request: HttpRequestData): HttpResponseData {
        val key = "${request.method.value} ${request.url.encodedPath}"
        val body = request.body.toByteArray().decodeToString()
        requestsLock.withLock { requests += key to body }
        return routes[key]?.invoke(this) ?: respond("""{"title":"Not Found","detail":"No group 9 found"}""", HttpStatusCode.NotFound, jsonHeaders)
    }

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun route(method: HttpMethod, path: String, body: String, status: HttpStatusCode = HttpStatusCode.OK) {
        routes["${method.value} $path"] = { respond(body, status, jsonHeaders) }
    }

    private fun viewModel(id: Long = 9) = GroupViewModel(id, myUserId = 1, GroupsApi(api), CatalogApi(api))

    // The mock engine answers on its own thread, so wait for the state rather than for the test dispatcher to idle.
    private suspend fun GroupViewModel.awaitLoaded() =
        state.first { it.group is Load.Ready && it.leaderboard is Load.Ready && it.words.endReached }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        route(HttpMethod.Get, "/api/v1/groups/9", """{"id":9,"name":"Book club","inviteCode":"DK6HGQFC","myRole":"MEMBER","createdAt":"2026-10-27T10:00:00Z",
            "members":[{"userId":1,"displayName":"Ada","role":"MEMBER","joinedAt":"2026-10-27T10:00:00Z"},
                       {"userId":2,"displayName":"Bo","role":"OWNER","joinedAt":"2026-10-27T09:00:00Z"}]}""")
        route(HttpMethod.Get, "/api/v1/groups/9/word-of-the-day", """{"date":"2026-10-27","word":{"slug":"lucid","lemma":"lucid"}}""")
        route(HttpMethod.Get, "/api/v1/groups/9/words", """{"items":[
            {"word":{"slug":"terse","lemma":"terse"},"addedBy":{"userId":2,"displayName":"Bo"},"addedAt":"2026-10-27T10:00:00Z"},
            {"word":{"slug":"wry","lemma":"wry"},"addedBy":{"userId":1,"displayName":"Ada"},"note":"from a novel","addedAt":"2026-10-27T09:00:00Z"}],
            "page":0,"size":50,"totalItems":2,"totalPages":1}""")
        route(HttpMethod.Get, "/api/v1/groups/9/leaderboard", """{"days":7,"since":"2026-10-20T10:00:00Z","rows":[
            {"rank":1,"userId":2,"displayName":"Bo","reviews":40,"streakDays":5,"wordsMastered":3,"isMe":false}]}""")
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun loadsTheGroupWithItsWordOfTheDayWordsAndLeaderboard() = runTest(dispatcher) {
        val vm = viewModel()
        val state = vm.awaitLoaded()

        assertEquals("Book club", (state.group as Load.Ready).value.name)
        assertEquals("lucid", state.wordOfTheDay?.slug)
        assertEquals(listOf("terse", "wry"), state.words.items.map { it.word.slug })
        assertEquals(1, (state.leaderboard as Load.Ready).value.rows.size)
        // A member (not the owner) can remove only the words they added
        assertFalse(vm.canRemove(state.words.items[0]))
        assertTrue(vm.canRemove(state.words.items[1]))
    }

    @Test
    fun addsAPickedWordWithItsNote() = runTest(dispatcher) {
        route(HttpMethod.Post, "/api/v1/groups/9/words", """{"word":{"slug":"lucid","lemma":"lucid"},"addedBy":{"userId":1,"displayName":"Ada"},"addedAt":"2026-10-27T11:00:00Z"}""")
        val vm = viewModel().also { it.awaitLoaded() }

        vm.openAddWord()
        vm.pickWord(WordSummaryDto("lucid", "lucid"))
        vm.editNote("  clear  ")
        vm.addPickedWord()
        vm.state.first { it.addWord == null }

        assertEquals("""{"word":"lucid","note":"clear"}""", recorded().single { it.first == "POST /api/v1/groups/9/words" }.second)
        assertEquals(null, vm.state.value.addWord)
    }

    @Test
    fun aGroupWeAreNoLongerInClosesTheScreen() = runTest(dispatcher) {
        routes.clear() // every call answers 404, as for outsiders
        val vm = viewModel()
        val closed = async { vm.closed.first() }

        assertEquals(Unit, closed.await())
    }

    @Test
    fun invitesAreFormattedForReadingOut() {
        assertEquals("DK6H-GQFC", formatInviteCode("DK6HGQFC"))
    }
}
