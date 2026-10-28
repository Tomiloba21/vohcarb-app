package app.vohcarb.games

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vohcarb.core.api.userMessage
import app.vohcarb.core.nowMillis
import app.vohcarb.core.parseInstantMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PlayPhase {
    data object Loading : PlayPhase
    data class Failed(val message: String) : PlayPhase
    data object Playing : PlayPhase
    data object Submitting : PlayPhase
    data class SubmitFailed(val message: String) : PlayPhase
    data class Finished(val playId: Long) : PlayPhase
}

data class PlayState(
    val phase: PlayPhase = PlayPhase.Loading,
    val play: PlayDto? = null,
    /** When the current round, word or sprint ends (epoch ms): never after the server's deadline. */
    val stepEndsAt: Long = 0,
    // Name, Place, Animal, Thing
    val round: Int = 0,
    val rounds: List<NpatRoundAnswers> = emptyList(),
    // Category sprint
    val words: List<String> = emptyList(),
    // Complete the word
    val index: Int = 0,
    val hints: List<HintDto> = emptyList(),
    val answers: List<CompleteAnswer> = emptyList(),
    val revealing: Boolean = false,
) {
    val game: Game?
        get() = when {
            play?.letters != null -> Game.NAME_PLACE_ANIMAL_THING
            play?.category != null -> Game.CATEGORY_SPRINT
            play?.words != null -> Game.COMPLETE_THE_WORD
            else -> null
        }
}

/**
 * Plays one daily challenge. The server sets the overall deadline; each round (60 s), word (20 s) or sprint ends
 * at its own time or at the deadline, whichever comes first. Answers are kept here and handed in at the end.
 */
class GamePlayViewModel(private val challenge: String, private val groupId: Long?, private val games: GamesApi) : ViewModel() {

    private val _state = MutableStateFlow(PlayState())
    val state = _state.asStateFlow()

    private var deadline = 0L
    private var wordShownAt = 0L

    init {
        start()
    }

    fun start() {
        _state.value = PlayState()
        viewModelScope.launch {
            try {
                val play = games.start(challenge, groupId)
                deadline = parseInstantMillis(play.deadline)
                val now = nowMillis()
                _state.value = when {
                    play.letters != null -> PlayState(PlayPhase.Playing, play, stepEnd(now, ROUND_MS), rounds = play.letters.map { NpatRoundAnswers() })
                    play.category != null -> PlayState(PlayPhase.Playing, play, deadline)
                    else -> {
                        wordShownAt = now
                        PlayState(PlayPhase.Playing, play, stepEnd(now, WORD_MS), hints = play.words.orEmpty())
                    }
                }
                if (now >= deadline) submit()
            } catch (e: Exception) {
                _state.value = PlayState(PlayPhase.Failed(e.userMessage))
            }
        }
    }

    /** Called by the screen a few times a second: moves on when time runs out. */
    fun tick(now: Long) {
        val s = _state.value
        if (s.phase != PlayPhase.Playing || now < s.stepEndsAt) return
        when (s.game) {
            Game.NAME_PLACE_ANIMAL_THING -> nextRound()
            Game.CATEGORY_SPRINT -> submit()
            Game.COMPLETE_THE_WORD -> nextWord(answer = null)
            null -> Unit
        }
    }

    // Name, Place, Animal, Thing

    fun editRound(change: NpatRoundAnswers.() -> NpatRoundAnswers) = _state.update { s ->
        s.copy(rounds = s.rounds.mapIndexed { i, r -> if (i == s.round) r.change() else r })
    }

    fun nextRound() {
        val s = _state.value
        val now = nowMillis()
        if (s.round >= s.rounds.lastIndex || now >= deadline) return submit()
        _state.update { it.copy(round = it.round + 1, stepEndsAt = stepEnd(now, ROUND_MS)) }
    }

    // Category sprint

    /** Adds a word unless it's empty or already there. */
    fun addWord(text: String) {
        val word = text.trim()
        if (word.isEmpty()) return
        _state.update { s -> if (s.words.any { it.equals(word, ignoreCase = true) }) s else s.copy(words = s.words + word) }
    }

    fun removeWord(word: String) = _state.update { it.copy(words = it.words - word) }

    // Complete the word

    /** Hands in [answer] for the current word (null = skip) and shows the next one. */
    fun nextWord(answer: String?) {
        val s = _state.value
        val now = nowMillis()
        val given = answer?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { CompleteAnswer(s.index, it, (now - wordShownAt).toInt()) }
        val answers = s.answers + listOfNotNull(given)
        if (s.index >= s.hints.lastIndex || now >= deadline) {
            _state.update { it.copy(answers = answers) }
            return submit()
        }
        wordShownAt = now
        _state.update { it.copy(answers = answers, index = it.index + 1, stepEndsAt = stepEnd(now, WORD_MS)) }
    }

    /** Shows one more letter of the current word; costs 3 points if answered right. */
    fun reveal() {
        val s = _state.value
        val play = s.play ?: return
        if (s.revealing) return
        _state.update { it.copy(revealing = true) }
        viewModelScope.launch {
            try {
                val hint = games.reveal(play.playId, s.index)
                _state.update { st -> st.copy(revealing = false, hints = st.hints.map { if (it.index == hint.index) hint else it }) }
            } catch (_: Exception) {
                _state.update { it.copy(revealing = false) }
            }
        }
    }

    // Handing in

    fun submit() {
        val s = _state.value
        val play = s.play ?: return
        if (s.phase == PlayPhase.Submitting || s.phase is PlayPhase.Finished) return
        _state.update { it.copy(phase = PlayPhase.Submitting) }
        val request = when (s.game) {
            Game.NAME_PLACE_ANIMAL_THING -> SubmitRequest(rounds = s.rounds)
            Game.CATEGORY_SPRINT -> SubmitRequest(words = s.words)
            Game.COMPLETE_THE_WORD -> SubmitRequest(answers = s.answers)
            null -> SubmitRequest()
        }
        viewModelScope.launch {
            val phase = try {
                games.submit(play.playId, request)
                PlayPhase.Finished(play.playId)
            } catch (e: Exception) {
                PlayPhase.SubmitFailed(e.userMessage)
            }
            _state.update { it.copy(phase = phase) }
        }
    }

    private fun stepEnd(now: Long, length: Long) = minOf(now + length, deadline)

    private companion object {
        const val ROUND_MS = 60_000L
        const val WORD_MS = 20_000L
    }
}
