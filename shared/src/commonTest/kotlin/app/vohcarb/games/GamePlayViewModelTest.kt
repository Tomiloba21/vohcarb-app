package app.vohcarb.games

import app.vohcarb.core.api.ApiClient
import app.vohcarb.core.isoInstant
import app.vohcarb.core.nowMillis
import app.vohcarb.core.session.Session
import app.vohcarb.core.session.SessionStore
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class GamePlayViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val bodies = mutableMapOf<String, String>() // path → last request body
    private val routes = mutableMapOf<String, String>()
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private val api = GamesApi(
        ApiClient(
            SessionStore(MapSettings(), "http://server").apply { signIn(Session("token", 1, "Ada", null)) },
            HttpClient(MockEngine { request -> handle(request) }) {
                expectSuccess = false
                install(ContentNegotiation) { json(ApiClient.json) }
            },
        ),
    )

    private suspend fun MockRequestHandleScope.handle(request: HttpRequestData): HttpResponseData {
        val path = request.url.encodedPath
        bodies[path] = request.body.toByteArray().decodeToString()
        val body = routes[path] ?: return respond("""{"detail":"nope"}""", HttpStatusCode.NotFound, jsonHeaders)
        return respond(body, HttpStatusCode.OK, jsonHeaders)
    }

    private val deadline = isoInstant(nowMillis() + 300_000)
    private val result = """{"playId":7,"challenge":"X","date":"2026-10-27","score":0,"late":false,"answers":[]}"""

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        routes["/api/v1/games/plays/7/submit"] = result
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun GamePlayViewModel.awaitPhase(match: (PlayPhase) -> Boolean) = state.first { match(it.phase) }

    @Test
    fun sprintKeepsEachWordOnceAndHandsThemIn() = runTest(dispatcher) {
        routes["/api/v1/games/daily/CATEGORY_SPRINT/start"] =
            """{"playId":7,"challenge":"CATEGORY_SPRINT","date":"2026-10-27","startedAt":"2026-10-27T10:00:00Z","deadline":"$deadline","category":{"slug":"food","name":"Food & drink"}}"""
        val vm = GamePlayViewModel("CATEGORY_SPRINT", null, api)
        vm.awaitPhase { it == PlayPhase.Playing }

        vm.addWord(" bread ")
        vm.addWord("Bread")
        vm.addWord("  ")
        vm.addWord("rice")
        vm.submit()
        vm.awaitPhase { it is PlayPhase.Finished }

        assertEquals("""{"words":["bread","rice"]}""", bodies["/api/v1/games/plays/7/submit"])
    }

    @Test
    fun npatHandsInEveryRoundAfterTheLastLetter() = runTest(dispatcher) {
        routes["/api/v1/games/daily/NAME_PLACE_ANIMAL_THING/start"] = // ?group=3 is a query parameter, not in the path
            """{"playId":7,"challenge":"NAME_PLACE_ANIMAL_THING","date":"2026-10-27","startedAt":"2026-10-27T10:00:00Z","deadline":"$deadline","letters":["B","T"]}"""
        val vm = GamePlayViewModel("NAME_PLACE_ANIMAL_THING", 3, api)
        vm.awaitPhase { it == PlayPhase.Playing }

        vm.editRound { copy(name = "Bola", animal = "bear") }
        vm.nextRound()
        assertEquals(1, vm.state.value.round)
        vm.editRound { copy(place = "Tokyo") }
        vm.nextRound() // the last letter: hands in
        vm.awaitPhase { it is PlayPhase.Finished }

        val rounds = Json.parseToJsonElement(bodies.getValue("/api/v1/games/plays/7/submit")).jsonObject["rounds"]!!.jsonArray
        assertEquals("Bola", rounds[0].jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals("Tokyo", rounds[1].jsonObject["place"]!!.jsonPrimitive.content)
    }

    @Test
    fun completeTheWordRevealsLettersAndSkipsBlankAnswers() = runTest(dispatcher) {
        routes["/api/v1/games/daily/COMPLETE_THE_WORD_HARD/start"] =
            """{"playId":7,"challenge":"COMPLETE_THE_WORD_HARD","date":"2026-10-27","startedAt":"2026-10-27T10:00:00Z","deadline":"$deadline",
               "words":[{"index":0,"definition":"a large animal","partOfSpeech":"noun","length":4,"mask":"b___"},
                        {"index":1,"definition":"feeling good","partOfSpeech":"adjective","length":5,"mask":"h____"}]}"""
        routes["/api/v1/games/plays/7/reveal"] = """{"index":0,"definition":"a large animal","partOfSpeech":"noun","length":4,"mask":"be__"}"""
        val vm = GamePlayViewModel("COMPLETE_THE_WORD_HARD", null, api)
        vm.awaitPhase { it == PlayPhase.Playing }

        vm.reveal()
        vm.state.first { it.hints[0].mask == "be__" }
        vm.nextWord("bear")
        vm.nextWord("   ") // skipped
        vm.awaitPhase { it is PlayPhase.Finished }

        val answers = Json.parseToJsonElement(bodies.getValue("/api/v1/games/plays/7/submit")).jsonObject["answers"]!!.jsonArray
        assertEquals(1, answers.size)
        assertEquals("bear", answers[0].jsonObject["answer"]!!.jsonPrimitive.content)
    }

    @Test
    fun aChallengeAlreadyPlayedTodayShowsTheServersMessage() = runTest(dispatcher) {
        val vm = GamePlayViewModel("CATEGORY_SPRINT", null, api) // no route: the mock answers 404
        assertEquals(PlayPhase.Failed("nope"), vm.awaitPhase { it is PlayPhase.Failed }.phase)
    }
}
