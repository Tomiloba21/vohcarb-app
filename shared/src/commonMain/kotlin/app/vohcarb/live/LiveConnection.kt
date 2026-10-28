package app.vohcarb.live

import app.vohcarb.core.api.ApiClient
import app.vohcarb.core.api.userMessage
import app.vohcarb.core.nowMillis
import app.vohcarb.core.parseInstantMillis
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface LinkStatus {
    data object Connecting : LinkStatus
    data object Connected : LinkStatus

    /** Lost; trying again. */
    data class Reconnecting(val reason: String) : LinkStatus

    /** The server won't have us in this room (left, removed, or the room is gone). */
    data object Closed : LinkStatus
}

/** A room's link to the server, as the room screen sees it. */
interface RoomLink {
    val state: StateFlow<RoomStateDto?>
    val status: StateFlow<LinkStatus>

    /** Things the server refused, to show briefly. */
    val errors: Flow<String>

    fun send(message: ClientMessage)

    /** The server's "now", on this phone. */
    fun serverNow(): Long

    /** Keeps the link open until [scope] ends. */
    fun run(scope: CoroutineScope): Job
}

/**
 * One room's socket: keeps it open while [run] is active, reconnecting with a growing pause if it drops.
 * States from the server arrive in [state]; [send] queues actions (they wait for the next connection).
 */
class LiveConnection(private val api: ApiClient, private val roomId: String) : RoomLink {

    private val _state = MutableStateFlow<RoomStateDto?>(null)
    override val state: StateFlow<RoomStateDto?> = _state.asStateFlow()

    private val _status = MutableStateFlow<LinkStatus>(LinkStatus.Connecting)
    override val status: StateFlow<LinkStatus> = _status.asStateFlow()

    private val _errors = Channel<String>(Channel.BUFFERED)
    override val errors = _errors.receiveAsFlow()

    private val queued = Channel<String>(Channel.BUFFERED)

    /** Server clock minus phone clock, from the last state; deadlines are corrected by it. */
    var clockOffsetMillis = 0L
        private set

    override fun send(message: ClientMessage) {
        queued.trySend(ApiClient.json.encodeToString(ClientMessage.serializer(), message))
    }

    override fun serverNow() = nowMillis() + clockOffsetMillis

    override fun run(scope: CoroutineScope): Job = scope.launch {
        var pause = 1_000L
        while (isActive) {
            var refused = false
            try {
                api.webSocket("/ws/games/rooms/$roomId") {
                    _status.value = LinkStatus.Connected
                    pause = 1_000L
                    val sender = launch { for (text in queued) send(Frame.Text(text)) }
                    try {
                        for (frame in incoming) if (frame is Frame.Text) receive(frame.readText())
                    } finally {
                        sender.cancel()
                    }
                    refused = closeReason.await()?.code == NOT_IN_ROOM
                }
                if (refused) {
                    _status.value = LinkStatus.Closed
                    return@launch
                }
                _status.value = LinkStatus.Reconnecting("Connection closed")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _status.value = LinkStatus.Reconnecting(e.userMessage)
            }
            delay(pause)
            pause = (pause * 2).coerceAtMost(10_000L)
        }
    }

    private fun receive(text: String) {
        val message = runCatching { ApiClient.json.decodeFromString(ServerMessage.serializer(), text) }.getOrNull() ?: return
        when (message.type) {
            "state" -> message.state?.let { state ->
                clockOffsetMillis = parseInstantMillis(state.serverTime) - nowMillis()
                _state.value = state
            }
            "error" -> message.message?.let { _errors.trySend(it) }
        }
    }

    private companion object {
        /** The server closes the socket with this when you're not in the room. */
        const val NOT_IN_ROOM: Short = 4404
    }
}
