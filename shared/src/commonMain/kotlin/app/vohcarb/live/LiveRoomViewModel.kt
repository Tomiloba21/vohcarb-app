package app.vohcarb.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vohcarb.core.parseInstantMillis
import app.vohcarb.games.NpatRoundAnswers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the player is typing this round; cleared when a new round starts. */
data class LiveInputs(
    val round: Int = -1,
    val npat: NpatRoundAnswers = NpatRoundAnswers(),
    val words: List<String> = emptyList(),
    val answer: String = "",
    /** Handed in this round (the server confirms with youSubmitted). */
    val sent: Boolean = false,
)

/**
 * A live room. The server runs the match and sends states; this keeps the socket open, holds what's being
 * typed, and hands it in, at the latest when the round's time is up.
 */
class LiveRoomViewModel(private val roomId: String, private val live: LiveApi, private val connection: RoomLink) : ViewModel() {

    val state = connection.state
    val status = connection.status
    val errors = connection.errors

    private val _inputs = MutableStateFlow(LiveInputs())
    val inputs = _inputs.asStateFlow()

    init {
        connection.run(viewModelScope)
        // A new round: start with empty answers
        viewModelScope.launch {
            connection.state.collect { s ->
                if (s != null && s.round != _inputs.value.round) _inputs.value = LiveInputs(round = s.round)
            }
        }
    }

    /** The server's clock, as seen on this phone. */
    fun serverNow() = connection.serverNow()

    /** Milliseconds left in the current phase, by the server's clock. */
    fun millisLeft(): Long? = state.value?.endsAt?.let { parseInstantMillis(it) - serverNow() }

    /** Called a few times a second by the screen: hands answers in when time runs out. */
    fun tick() {
        val s = state.value ?: return
        if (s.phase != RoomPhase.ROUND || s.youSubmitted || _inputs.value.sent) return
        if ((millisLeft() ?: return) > 0) return
        when {
            s.letter != null -> submitNpat()
            s.category != null -> submitWords()
            // Complete the word: an unanswered word just times out
        }
    }

    fun start() = connection.send(ClientMessage("start"))

    fun editNpat(change: NpatRoundAnswers.() -> NpatRoundAnswers) = _inputs.update { if (it.sent) it else it.copy(npat = it.npat.change()) }

    fun submitNpat() = handIn { ClientMessage("npat", round = it.round, answers = it.npat) }

    fun addWord(text: String) {
        val word = text.trim()
        if (word.isEmpty()) return
        _inputs.update { i -> if (i.sent || i.words.any { it.equals(word, ignoreCase = true) }) i else i.copy(words = i.words + word) }
    }

    fun removeWord(word: String) = _inputs.update { if (it.sent) it else it.copy(words = it.words - word) }

    fun submitWords() = handIn { ClientMessage("words", round = it.round, words = it.words) }

    fun editAnswer(text: String) = _inputs.update { if (it.sent) it else it.copy(answer = text.take(64)) }

    fun submitAnswer() {
        if (_inputs.value.answer.isBlank()) return
        handIn { ClientMessage("answer", round = it.round, answer = it.answer.trim()) }
    }

    fun reveal() {
        val s = state.value ?: return
        if (!_inputs.value.sent) connection.send(ClientMessage("reveal", round = s.round))
    }

    fun vote(key: String, accepted: Boolean) = connection.send(ClientMessage("vote", key = key, accepted = accepted))

    fun leave(then: () -> Unit) {
        viewModelScope.launch {
            runCatching { live.leave(roomId) }
            then()
        }
    }

    private fun handIn(message: (LiveInputs) -> ClientMessage) {
        val s = state.value ?: return
        val inputs = _inputs.value
        if (s.phase != RoomPhase.ROUND || inputs.sent || inputs.round != s.round) return
        connection.send(message(inputs))
        _inputs.update { it.copy(sent = true) }
    }
}
