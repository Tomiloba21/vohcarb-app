package app.vohcarb.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.vohcarb.catalog.CatalogApi
import app.vohcarb.core.api.userMessage
import app.vohcarb.core.ui.MessageBox
import app.vohcarb.saved.WordPages
import app.vohcarb.saved.WordPagesList
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchState(val query: String = "", val pages: WordPages = WordPages(endReached = true))

class SearchViewModel(private val catalog: CatalogApi) : ViewModel() {

    private val _state = MutableStateFlow(SearchState())
    val state = _state.asStateFlow()
    private var job: Job? = null

    fun onQuery(query: String) {
        _state.value = SearchState(query, WordPages(endReached = query.isBlank()))
        job?.cancel()
        if (query.isBlank()) return
        job = viewModelScope.launch {
            delay(250) // wait for the user to stop typing
            loadPage()
        }
    }

    fun loadMore() {
        val pages = _state.value.pages
        if (pages.loading || pages.endReached || job?.isActive == true) return
        job = viewModelScope.launch { loadPage() }
    }

    private suspend fun loadPage() {
        val (query, pages) = _state.value
        _state.update { it.copy(pages = pages.copy(loading = true, error = null)) }
        try {
            val page = catalog.search(query, pages.nextPage)
            _state.update {
                it.copy(pages = it.pages.copy(
                    words = it.pages.words + page.items,
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

@Composable
fun SearchScreen(viewModel: SearchViewModel, onBack: () -> Unit, onOpenWord: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                title = {
                    TextField(
                        value = state.query,
                        onValueChange = viewModel::onQuery,
                        placeholder = { Text("Search words") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Search),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.query.isBlank()) {
                MessageBox("Type the start of a word.")
            } else {
                WordPagesList(
                    pages = state.pages,
                    emptyMessage = "No words start with “${state.query.trim()}”.",
                    onLoadMore = viewModel::loadMore,
                    onRetry = { viewModel.onQuery(state.query) },
                    onOpenWord = onOpenWord,
                )
            }
        }
    }
}
