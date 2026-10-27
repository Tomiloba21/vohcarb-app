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
import app.vohcarb.core.ui.LoadingBox
import app.vohcarb.core.ui.MessageBox
import app.vohcarb.progress.ProgressRepository
import app.vohcarb.progress.WordList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/** A list of words that loads page by page from the server (search). */
data class WordPages(
    val words: List<WordSummaryDto> = emptyList(),
    val nextPage: Int = 0,
    val endReached: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
)

/** The user's lists, read from the phone; the sync engine keeps them current. */
@OptIn(ExperimentalCoroutinesApi::class)
class SavedViewModel(private val progress: ProgressRepository) : ViewModel() {

    val selected = MutableStateFlow(WordList.SAVED)

    /** null while loading */
    val words = selected.flatMapLatest(progress::list)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun SavedScreen(viewModel: SavedViewModel, onOpenWord: (String) -> Unit) {
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val words by viewModel.words.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        PrimaryScrollableTabRow(selectedTabIndex = selected.ordinal, edgePadding = 16.dp) {
            WordList.entries.forEach { list ->
                Tab(selected = list == selected, onClick = { viewModel.selected.value = list }, text = { Text(list.label) })
            }
        }
        val list = words
        when {
            list == null -> LoadingBox()
            list.isEmpty() -> MessageBox(
                when (selected) {
                    WordList.SAVED -> "Words you save show up here, and become flashcards in Practice."
                    WordList.LIKED -> "Words you like show up here."
                    WordList.KNOWN -> "Words you mark as known show up here. They won't appear in your feed again."
                    WordList.SEEN -> "Words you've seen in your feed show up here."
                },
            )
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(list, key = { it.slug }) { word ->
                    WordRow(word, onClick = { onOpenWord(word.slug) })
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                }
            }
        }
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
