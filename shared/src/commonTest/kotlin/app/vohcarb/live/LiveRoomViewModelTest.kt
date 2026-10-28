package app.vohcarb.live

import app.vohcarb.core.api.ApiClient
import app.vohcarb.core.isoInstant
import app.vohcarb.core.session.SessionStore
import app.vohcarb.games.NpatRoundAnswers
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LiveRoomViewModelTest {

    /** Stands in for the socket: tests set states and the clock, and read what was sent. */
    private class FakeLink : RoomLink {
        override val state = MutableStateFlow<RoomStateDto?>(null)
        override val status = MutableStateFlow<LinkStatus>(LinkStatus.Connected)
        override val errors = emptyFlow<String>()
        val sent = mutableListOf<ClientMessage>()
        var now = 1_000_000L
        override fun send(message: ClientMessage) {
            sent += message
        }
        override fun serverNow() = now
        override fun run(scope: CoroutineScope) = Job()
    }

    private val dispatcher = StandardTestDispatcher()
    private val link = FakeLink()
    private val api = LiveApi(ApiClient(SessionStore(MapSettings(), "http://server")))

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun state(round: Int, endsAt: Long, letter: String? = "B", category: String? = null, submitted: Boolean = false) = RoomStateDto(
        roomId = "r", code = "ABC123", challenge = "NAME_PLACE_ANIMAL_THING", title = "NPAT", kind = RoomKind.FRIENDS,
        hostId = 1, you = 1, phase = RoomPhase.ROUND, round = round, rounds = 5, endsAt = isoInstant(endsAt),
        serverTime = isoInstant(link.now), minPlayers = 2, maxPlayers = 6, letter = letter,
        category = category?.let { app.vohcarb.catalog.CategoryRef("x", it) }, youSubmitted = submitted,
    )

    @Test
    fun answersAreHandedInWhenTimeRunsOutAndOnlyOnce() = runTest(dispatcher) {
        val vm = LiveRoomViewModel("r", api, link)
        link.state.value = state(round = 0, endsAt = link.now + 5_000)
        advanceUntilIdle()

        vm.editNpat { copy(name = "Bola", animal = "bear") }
        vm.tick()
        assertTrue(link.sent.isEmpty()) // time left

        link.now += 6_000
        vm.tick()
        vm.tick()
        assertEquals(listOf(ClientMessage("npat", round = 0, answers = NpatRoundAnswers(name = "Bola", animal = "bear"))), link.sent)

        vm.editNpat { copy(place = "Benin") } // too late: answers are final
        assertEquals(null, vm.inputs.value.npat.place)
    }

    @Test
    fun aNewRoundStartsWithEmptyAnswers() = runTest(dispatcher) {
        val vm = LiveRoomViewModel("r", api, link)
        link.state.value = state(round = 0, endsAt = link.now + 5_000)
        advanceUntilIdle()
        vm.editNpat { copy(name = "Bola") }
        vm.submitNpat()

        link.state.value = state(round = 1, endsAt = link.now + 5_000, letter = "T")
        advanceUntilIdle()
        assertEquals(LiveInputs(round = 1), vm.inputs.value)
    }

    @Test
    fun sprintWordsAreKeptOnceAndSentTogether() = runTest(dispatcher) {
        val vm = LiveRoomViewModel("r", api, link)
        link.state.value = state(round = 0, endsAt = link.now + 5_000, letter = null, category = "Food")
        advanceUntilIdle()
        vm.addWord(" rice ")
        vm.addWord("Rice")
        vm.addWord("bread")
        link.now += 5_000
        vm.tick()
        assertEquals(listOf("rice", "bread"), link.sent.single().words)
    }
}
