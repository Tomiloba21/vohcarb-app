package app.vohcarb.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.vohcarb.catalog.CatalogApi
import app.vohcarb.catalog.WordCache
import app.vohcarb.catalog.WordDetailDto
import app.vohcarb.core.ui.LoadingBox
import app.vohcarb.core.ui.MessageBox
import app.vohcarb.progress.ProgressRepository
import app.vohcarb.progress.WordFlags
import app.vohcarb.progress.WordStatePatch
import app.vohcarb.sync.SyncEngine
import app.vohcarb.word.Senses
import app.vohcarb.word.WordActions
import app.vohcarb.word.WordHeader
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FeedCard(val word: WordDetailDto, val isWordOfTheDay: Boolean = false)

data class FeedState(
    val cards: List<FeedCard> = emptyList(),
    val flags: Map<String, WordFlags> = emptyMap(),
    val loading: Boolean = true,
    /** Set when there are no words to show and the last sync failed (e.g. offline). */
    val offlineMessage: String? = null,
    val loadingMore: Boolean = false,
    /** No more unseen words, here or on the server. */
    val exhausted: Boolean = false,
)

/** The feed reads words queued on the phone; the sync engine fetches more ahead of time. */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModel(
    private val queue: FeedQueue,
    private val progress: ProgressRepository,
    private val words: WordCache,
    private val catalog: CatalogApi,
    private val sync: SyncEngine,
) : ViewModel() {

    private val _state = MutableStateFlow(FeedState())
    val state = _state.asStateFlow()

    init {
        load()
        // Flags for the words on screen, live from the database
        viewModelScope.launch {
            _state.map { s -> s.cards.map { it.word.slug } }.distinctUntilChanged()
                .flatMapLatest(progress::flagsFor)
                .collect { flags -> _state.update { it.copy(flags = flags) } }
        }
    }

    fun load() {
        _state.update { FeedState(loading = true) }
        viewModelScope.launch {
            var queued = queue.unseen()
            if (queued.size < PRELOAD_DISTANCE) {
                sync.syncNow()
                queued = queue.unseen()
            }
            val wordOfTheDay = words.wordOfTheDay(catalog)
            val cards = listOfNotNull(wordOfTheDay?.let { FeedCard(it, isWordOfTheDay = true) }) +
                queued.filter { it.slug != wordOfTheDay?.slug }.map { FeedCard(it) }
            _state.update {
                FeedState(
                    cards = cards,
                    flags = it.flags,
                    loading = false,
                    offlineMessage = sync.status.value.error.takeIf { cards.isEmpty() },
                    exhausted = queued.isEmpty(),
                )
            }
        }
    }

    private fun loadMore() {
        val current = _state.value
        if (current.loading || current.loadingMore || current.exhausted) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            val have = _state.value.cards.map { it.word.slug }.toSet()
            var more = queue.unseen().filter { it.slug !in have }
            if (more.size < PRELOAD_DISTANCE) {
                sync.syncNow()
                more = queue.unseen().filter { it.slug !in have }
            }
            _state.update { it.copy(cards = it.cards + more.map(::FeedCard), loadingMore = false, exhausted = more.isEmpty()) }
        }
    }

    /** The feed only skips words once they're marked seen, so mark each word when it's shown. */
    fun onShown(index: Int) {
        if (index >= _state.value.cards.size - PRELOAD_DISTANCE) loadMore()
        val word = _state.value.cards.getOrNull(index)?.word ?: return
        if (_state.value.flags[word.slug]?.seen == true) return
        viewModelScope.launch { progress.update(word.slug, WordStatePatch(seen = true)) }
    }

    fun update(slug: String, patch: WordStatePatch) {
        viewModelScope.launch { progress.update(slug, patch) }
    }

    private companion object {
        const val PRELOAD_DISTANCE = 3
    }
}

@Composable
fun FeedScreen(viewModel: FeedViewModel, onOpenWord: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when {
        state.loading -> LoadingBox()
        state.offlineMessage != null -> MessageBox(
            "${state.offlineMessage}\n\nNew words are downloaded ahead of time once you're online.",
            actionLabel = "Try again",
            onAction = viewModel::load,
        )
        state.cards.isEmpty() -> MessageBox(
            "No new words match your settings. Widen your level range or categories in Settings.",
            actionLabel = "Refresh",
            onAction = viewModel::load,
        )
        else -> {
            // One more page at the end for "loading" or "that's all"
            val pager = rememberPagerState { state.cards.size + 1 }
            LaunchedEffect(pager) {
                snapshotFlow { pager.settledPage }.distinctUntilChanged().collect(viewModel::onShown)
            }
            VerticalPager(pager, Modifier.fillMaxSize(), key = { state.cards.getOrNull(it)?.word?.slug ?: "end" }) { index ->
                val card = state.cards.getOrNull(index)
                if (card == null) {
                    EndPage(state, onRefresh = viewModel::load)
                } else {
                    FeedPage(
                        card = card,
                        position = if (card.isWordOfTheDay) "Word of the day" else null,
                        flags = state.flags[card.word.slug] ?: WordFlags(),
                        showHint = index == 0,
                        onUpdate = { viewModel.update(card.word.slug, it) },
                        onOpen = { onOpenWord(card.word.slug) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedPage(
    card: FeedCard,
    position: String?,
    flags: WordFlags,
    showHint: Boolean,
    onUpdate: (WordStatePatch) -> Unit,
    onOpen: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 16.dp)) {
        Text(
            position.orEmpty(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.height(24.dp),
        )
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).clickable(onClick = onOpen),
            verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
        ) {
            WordHeader(card.word)
            Senses(card.word, maxSenses = 3)
        }
        WordActions(
            flags,
            onLike = { onUpdate(WordStatePatch(liked = it)) },
            onSave = { onUpdate(WordStatePatch(saved = it)) },
            onKnown = { onUpdate(WordStatePatch(known = it)) },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Text(
            if (showHint) "Swipe up for the next word" else "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(20.dp),
        )
    }
}

@Composable
private fun EndPage(state: FeedState, onRefresh: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (state.exhausted) {
            MessageBox("No more new words for now. More are downloaded when you're online.", actionLabel = "Refresh", onAction = onRefresh)
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Finding more words…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
