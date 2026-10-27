package app.vohcarb.saved

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.vohcarb.catalog.WordSummaryDto
import app.vohcarb.catalog.levelName
import app.vohcarb.core.api.userMessage
import app.vohcarb.core.ui.LoadingBox
import app.vohcarb.core.ui.MessageBox
import app.vohcarb.progress.ProgressApi
import app.vohcarb.progress.WordList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A list of words that loads page by page. */
data class WordPages(
    val words: List<WordSummaryDto> = emptyList(),
    val nextPage: Int = 0,
    val endReached: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
)

data class SavedState(val list: WordList = WordList.SAVED, val pages: WordPages = WordPages())

class SavedViewModel(private val progress: ProgressApi) : ViewModel() {

    private val _state = MutableStateFlow(SavedState())
    val state = _state.asStateFlow()
    private var job: Job? = null

    fun select(list: WordList) {
        if (list == _state.value.list) return
        _state.value = SavedState(list)
        refresh()
    }

    /** Reloads from the first page, e.g. when coming back after saving words elsewhere. */
    fun refresh() {
        job?.cancel()
        _state.update { it.copy(pages = WordPages()) }
        loadMore()
    }

    fun loadMore() {
        val (list, pages) = _state.value
        if (pages.loading || pages.endReached) return
        _state.update { it.copy(pages = pages.copy(loading = true, error = null)) }
        job = viewModelScope.launch {
            try {
                val page = progress.list(list, pages.nextPage)
                _state.update {
                    it.copy(pages = it.pages.copy(
                        words = it.pages.words + page.items.map { mine -> mine.word },
                        nextPage = page.page + 1,
                        endReached = page.isLast,
                        loading = false,
                    ))
                }
            } catch (e: Exception) {
                _state.update { it.copy(pages = it.pages.copy(loading = false, error = e.userMessage)) }
            }
        }
    }
}

@Composable
fun SavedScreen(viewModel: SavedViewModel, onOpenWord: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Every time the screen is shown, so words saved elsewhere appear
    LaunchedEffect(viewModel) { viewModel.refresh() }

    Column(Modifier.fillMaxSize()) {
        PrimaryScrollableTabRow(selectedTabIndex = state.list.ordinal, edgePadding = 16.dp) {
            WordList.entries.forEach { list ->
                Tab(selected = list == state.list, onClick = { viewModel.select(list) }, text = { Text(list.label) })
            }
        }
        WordPagesList(
            pages = state.pages,
            emptyMessage = when (state.list) {
                WordList.SAVED -> "Words you save show up here, and become flashcards in Practice."
                WordList.LIKED -> "Words you like show up here."
                WordList.KNOWN -> "Words you mark as known show up here. They won't appear in your feed again."
                WordList.SEEN -> "Words you've seen in your feed show up here."
            },
            onLoadMore = viewModel::loadMore,
            onRetry = viewModel::refresh,
            onOpenWord = onOpenWord,
        )
    }
}

@Composable
fun WordPagesList(
    pages: WordPages,
    emptyMessage: String,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onOpenWord: (String) -> Unit,
) {
    when {
        pages.words.isEmpty() && pages.loading -> LoadingBox()
        pages.words.isEmpty() && pages.error != null -> MessageBox(pages.error, actionLabel = "Try again", onAction = onRetry)
        pages.words.isEmpty() && pages.endReached -> MessageBox(emptyMessage)
        else -> {
            val listState = rememberLazyListState()
            val nearEnd by remember {
                derivedStateOf { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= listState.layoutInfo.totalItemsCount - 5 }
            }
            LaunchedEffect(nearEnd, pages.words.size) { if (nearEnd) onLoadMore() }

            LazyColumn(Modifier.fillMaxSize(), state = listState) {
                items(pages.words, key = { it.slug }) { word ->
                    WordRow(word, onClick = { onOpenWord(word.slug) })
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                }
                if (pages.loading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                }
            }
        }
    }
}

@Composable
fun WordRow(word: WordSummaryDto, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(word.lemma, style = MaterialTheme.typography.titleLarge) },
        supportingContent = word.shortDefinition?.let { { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
        overlineContent = {
            Text(listOfNotNull(word.partsOfSpeech.joinToString(", ").ifEmpty { null }, levelName(word.difficulty)).joinToString(" · "))
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
