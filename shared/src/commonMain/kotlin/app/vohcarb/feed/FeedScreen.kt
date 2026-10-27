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
import androidx.compose.material3.SnackbarHostState
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
import app.vohcarb.catalog.WordDetailDto
import app.vohcarb.core.api.userMessage
import app.vohcarb.core.ui.LoadingBox
import app.vohcarb.core.ui.MessageBox
import app.vohcarb.progress.ProgressApi
import app.vohcarb.progress.WordFlags
import app.vohcarb.progress.WordStatePatch
import app.vohcarb.word.Senses
import app.vohcarb.word.WordActions
import app.vohcarb.word.WordHeader
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FeedCard(val word: WordDetailDto, val isWordOfTheDay: Boolean = false)

data class FeedState(
    val cards: List<FeedCard> = emptyList(),
    val flags: Map<String, WordFlags> = emptyMap(),
    val loading: Boolean = true,
    val error: String? = null,
    val loadingMore: Boolean = false,
    /** The server had no more unseen words for the user's preferences. */
    val exhausted: Boolean = false,
)

class FeedViewModel(private val progress: ProgressApi, private val catalog: CatalogApi) : ViewModel() {

    private val _state = MutableStateFlow(FeedState())
    val state = _state.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errors = _errors.asSharedFlow()

    /** Words already reported as seen (or being reported), so each is sent once. */
    private val reportedSeen = mutableSetOf<String>()

    init {
        load()
    }

    fun load() {
        _state.value = FeedState(loading = true)
        viewModelScope.launch {
            try {
                val wordOfTheDay = async { runCatching { catalog.wordOfTheDay().word }.getOrNull() }
                val feed = progress.feed(PAGE_SIZE)
                val cards = listOfNotNull(wordOfTheDay.await()?.let { FeedCard(it, isWordOfTheDay = true) }) +
                    feed.filter { it.slug != wordOfTheDay.await()?.slug }.map { FeedCard(it) }
                _state.value = FeedState(cards = cards, loading = false, exhausted = feed.isEmpty())
            } catch (e: Exception) {
                _state.value = FeedState(loading = false, error = e.userMessage)
            }
        }
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || current.loadingMore || current.exhausted) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            try {
                val have = _state.value.cards.map { it.word.slug }.toSet()
                // Words shown but whose "seen" is still in flight can come back; drop them.
                val more = progress.feed(PAGE_SIZE).filter { it.slug !in have }
                _state.update { it.copy(cards = it.cards + more.map(::FeedCard), loadingMore = false, exhausted = more.isEmpty()) }
            } catch (e: Exception) {
                _state.update { it.copy(loadingMore = false) }
                _errors.tryEmit(e.userMessage)
            }
        }
    }

    /** The feed only skips words once they're marked seen, so report each word when it's shown. */
    fun onShown(index: Int) {
        if (index >= _state.value.cards.size - PRELOAD_DISTANCE) loadMore()
        val word = _state.value.cards.getOrNull(index)?.word ?: return
        if (!reportedSeen.add(word.slug)) return
        viewModelScope.launch {
            try {
                val mine = progress.update(word.slug, WordStatePatch(seen = true))
                // Keep flags the user may have toggled meanwhile
                _state.update { it.copy(flags = it.flags + (word.slug to (it.flags[word.slug] ?: mine.flags).copy(seen = true))) }
            } catch (_: Exception) {
                reportedSeen.remove(word.slug) // try again next time it's shown
            }
        }
    }

    fun update(slug: String, patch: WordStatePatch) {
        val before = _state.value.flags[slug] ?: WordFlags()
        setFlags(slug, patch.applyTo(before))
        viewModelScope.launch {
            try {
                setFlags(slug, progress.update(slug, patch).flags)
            } catch (e: Exception) {
                setFlags(slug, before)
                _errors.tryEmit(e.userMessage)
            }
        }
    }

    private fun setFlags(slug: String, flags: WordFlags) = _state.update { it.copy(flags = it.flags + (slug to flags)) }

    private companion object {
        const val PAGE_SIZE = 10
        const val PRELOAD_DISTANCE = 3
    }
}

@Composable
fun FeedScreen(viewModel: FeedViewModel, snackbar: SnackbarHostState, onOpenWord: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.errors.collect { snackbar.showSnackbar(it) } }

    when {
        state.loading -> LoadingBox()
        state.error != null -> MessageBox(state.error!!, actionLabel = "Try again", onAction = viewModel::load)
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
            MessageBox("That's all the new words for your settings.", actionLabel = "Refresh", onAction = onRefresh)
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Finding more words…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
