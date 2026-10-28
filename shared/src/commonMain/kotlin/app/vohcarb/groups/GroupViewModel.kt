package app.vohcarb.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vohcarb.catalog.CatalogApi
import app.vohcarb.catalog.WordDetailDto
import app.vohcarb.catalog.WordSummaryDto
import app.vohcarb.core.api.ApiException
import app.vohcarb.core.api.userMessage
import app.vohcarb.core.ui.Load
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class GroupTab(val label: String) { WORDS("Words"), GAMES("Games"), LEADERBOARD("Leaderboard"), MEMBERS("Members") }

data class GroupWords(
    val items: List<GroupWordDto> = emptyList(),
    val nextPage: Int = 0,
    val endReached: Boolean = false,
    val loading: Boolean = false,
)

/** The "add a word" dialog: search the catalog, pick a word, add an optional note. */
data class AddWordState(
    val query: String = "",
    val results: List<WordSummaryDto> = emptyList(),
    val picked: WordSummaryDto? = null,
    val note: String = "",
    val busy: Boolean = false,
)

/** Actions that need a "are you sure?" first. */
sealed interface Confirm {
    data object Leave : Confirm
    data object Delete : Confirm
    data object NewCode : Confirm
    data class RemoveMember(val member: MemberDto) : Confirm
}

data class GroupState(
    val group: Load<GroupDetailDto> = Load.Loading,
    val wordOfTheDay: WordDetailDto? = null,
    val tab: GroupTab = GroupTab.WORDS,
    val words: GroupWords = GroupWords(),
    val leaderboardDays: Int = 7,
    val leaderboard: Load<LeaderboardDto> = Load.Loading,
    val addWord: AddWordState? = null,
    val confirm: Confirm? = null,
)

class GroupViewModel(
    private val groupId: Long,
    val myUserId: Long,
    private val groups: GroupsApi,
    private val catalog: CatalogApi,
) : ViewModel() {

    private val _state = MutableStateFlow(GroupState())
    val state = _state.asStateFlow()

    // Channels, not shared flows: an event sent before the screen starts listening is kept, not dropped.
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    /** Fires when the group is gone for us (left, deleted, removed): the screen closes. */
    private val _closed = Channel<Unit>(Channel.CONFLATED)
    val closed = _closed.receiveAsFlow()

    private var searchJob: Job? = null

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(group = if (it.group is Load.Ready) it.group else Load.Loading) }
        viewModelScope.launch {
            try {
                val wordOfTheDay = async { runCatching { groups.wordOfTheDay(groupId).word }.getOrNull() }
                val detail = groups.detail(groupId)
                _state.update { it.copy(group = Load.Ready(detail), wordOfTheDay = wordOfTheDay.await()) }
                refreshWords()
                loadLeaderboard()
            } catch (e: Exception) {
                if (gone(e)) return@launch
                _state.update { it.copy(group = Load.Failed(e.userMessage)) }
            }
        }
    }

    fun selectTab(tab: GroupTab) = _state.update { it.copy(tab = tab) }

    // Words

    private suspend fun refreshWords() {
        _state.update { it.copy(words = GroupWords()) }
        loadWordsPage()
    }

    fun loadMoreWords() {
        val words = _state.value.words
        if (words.loading || words.endReached) return
        viewModelScope.launch { loadWordsPage() }
    }

    private suspend fun loadWordsPage() {
        val page = _state.value.words.nextPage
        _state.update { it.copy(words = it.words.copy(loading = true)) }
        try {
            val result = groups.words(groupId, page)
            _state.update {
                it.copy(words = it.words.copy(
                    items = it.words.items + result.items,
                    nextPage = result.page + 1,
                    endReached = result.isLast,
                    loading = false,
                ))
            }
        } catch (e: Exception) {
            _state.update { it.copy(words = it.words.copy(loading = false)) }
            if (!gone(e)) _messages.trySend(e.userMessage)
        }
    }

    fun canRemove(word: GroupWordDto): Boolean {
        val group = (_state.value.group as? Load.Ready)?.value ?: return false
        return group.isOwner || word.addedBy.userId == myUserId
    }

    fun removeWord(word: GroupWordDto) {
        viewModelScope.launch {
            try {
                groups.removeWord(groupId, word.word.slug)
            } catch (e: Exception) {
                // 404: someone else removed it already, which is what we wanted
                if ((e as? ApiException)?.isNotFound != true) {
                    _messages.trySend(e.userMessage)
                    return@launch
                }
            }
            _state.update { s -> s.copy(words = s.words.copy(items = s.words.items.filter { it.word.slug != word.word.slug })) }
        }
    }

    fun openAddWord() = _state.update { it.copy(addWord = AddWordState()) }

    fun closeAddWord() = _state.update { it.copy(addWord = null) }

    fun searchWords(query: String) {
        _state.update { it.copy(addWord = it.addWord?.copy(query = query, picked = null)) }
        searchJob?.cancel()
        if (query.isBlank()) return _state.update { it.copy(addWord = it.addWord?.copy(results = emptyList())) }
        searchJob = viewModelScope.launch {
            delay(250)
            try {
                val results = catalog.search(query, size = 8).items
                _state.update { it.copy(addWord = it.addWord?.copy(results = results)) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // keep the old results; the user can keep typing
            }
        }
    }

    fun pickWord(word: WordSummaryDto) = _state.update { it.copy(addWord = it.addWord?.copy(picked = word, query = word.lemma, results = emptyList())) }

    fun editNote(note: String) = _state.update { it.copy(addWord = it.addWord?.copy(note = note.take(280))) }

    fun addPickedWord() {
        val form = _state.value.addWord ?: return
        val word = form.picked ?: return
        _state.update { it.copy(addWord = form.copy(busy = true)) }
        viewModelScope.launch {
            try {
                groups.addWord(groupId, word.slug, form.note)
                _state.update { it.copy(addWord = null) }
                _messages.trySend("Added “${word.lemma}”")
                refreshWords()
            } catch (e: Exception) {
                _state.update { it.copy(addWord = it.addWord?.copy(busy = false)) }
                if (!gone(e)) _messages.trySend(e.userMessage)
            }
        }
    }

    // Leaderboard

    fun setLeaderboardDays(days: Int) {
        _state.update { it.copy(leaderboardDays = days) }
        viewModelScope.launch { loadLeaderboard() }
    }

    private suspend fun loadLeaderboard() {
        val days = _state.value.leaderboardDays
        _state.update { it.copy(leaderboard = if (it.leaderboard is Load.Ready) it.leaderboard else Load.Loading) }
        val result = try {
            Load.Ready(groups.leaderboard(groupId, days))
        } catch (e: Exception) {
            if (gone(e)) return
            Load.Failed(e.userMessage)
        }
        _state.update { if (it.leaderboardDays == days) it.copy(leaderboard = result) else it }
    }

    fun retryLeaderboard() {
        viewModelScope.launch { loadLeaderboard() }
    }

    // Members and the group itself

    fun ask(confirm: Confirm?) = _state.update { it.copy(confirm = confirm) }

    fun confirmed() {
        val confirm = _state.value.confirm ?: return
        _state.update { it.copy(confirm = null) }
        when (confirm) {
            Confirm.Leave -> act {
                groups.leave(groupId)
                _closed.trySend(Unit)
            }
            Confirm.Delete -> act {
                groups.delete(groupId)
                _closed.trySend(Unit)
            }
            Confirm.NewCode -> act {
                val detail = groups.newInviteCode(groupId)
                _state.update { it.copy(group = Load.Ready(detail)) }
                _messages.trySend("New code: ${formatInviteCode(detail.inviteCode)}. The old one no longer works.")
            }
            is Confirm.RemoveMember -> act {
                groups.removeMember(groupId, confirm.member.userId)
                _state.update { s ->
                    val group = (s.group as? Load.Ready)?.value ?: return@update s
                    s.copy(group = Load.Ready(group.copy(members = group.members.filter { it.userId != confirm.member.userId })))
                }
                loadLeaderboard()
            }
        }
    }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                if (!gone(e)) _messages.trySend(e.userMessage)
            }
        }
    }

    /** Groups we're not in answer 404: deleted, or we were removed. Close the screen. */
    private fun gone(e: Exception): Boolean {
        if ((e as? ApiException)?.isNotFound != true || _state.value.addWord != null) return false
        _messages.trySend("This group no longer exists, or you're no longer in it.")
        _closed.trySend(Unit)
        return true
    }
}
