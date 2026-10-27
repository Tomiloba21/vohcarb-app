package app.vohcarb.word

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.vohcarb.catalog.CatalogApi
import app.vohcarb.catalog.WordDetailDto
import app.vohcarb.core.api.userMessage
import app.vohcarb.core.ui.Load
import app.vohcarb.core.ui.LoadContent
import app.vohcarb.progress.ProgressApi
import app.vohcarb.progress.WordFlags
import app.vohcarb.progress.WordStatePatch
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WordState(val word: WordDetailDto, val flags: WordFlags)

class WordViewModel(
    private val slug: String,
    private val catalog: CatalogApi,
    private val progress: ProgressApi,
) : ViewModel() {

    private val _state = MutableStateFlow<Load<WordState>>(Load.Loading)
    val state = _state.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errors = _errors.asSharedFlow()

    init {
        load()
    }

    fun load() {
        _state.value = Load.Loading
        viewModelScope.launch {
            _state.value = try {
                val word = async { catalog.word(slug) }
                val mine = async { progress.state(slug) }
                Load.Ready(WordState(word.await(), mine.await().flags))
            } catch (e: Exception) {
                Load.Failed(e.userMessage)
            }
        }
    }

    fun update(patch: WordStatePatch) {
        val current = (_state.value as? Load.Ready)?.value ?: return
        val before = current.flags
        setFlags(patch.applyTo(before))
        viewModelScope.launch {
            try {
                setFlags(progress.update(slug, patch).flags)
            } catch (e: Exception) {
                setFlags(before)
                _errors.tryEmit(e.userMessage)
            }
        }
    }

    private fun setFlags(flags: WordFlags) = _state.update { load ->
        if (load is Load.Ready) Load.Ready(load.value.copy(flags = flags)) else load
    }
}

@Composable
fun WordScreen(viewModel: WordViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.errors.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LoadContent(state, onRetry = viewModel::load) { (word, flags) ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    WordHeader(word)
                    WordActions(
                        flags,
                        onLike = { viewModel.update(WordStatePatch(liked = it)) },
                        onSave = { viewModel.update(WordStatePatch(saved = it)) },
                        onKnown = { viewModel.update(WordStatePatch(known = it)) },
                    )
                    Senses(word)
                    Categories(word)
                    if (word.senses.isEmpty()) Text("No definitions yet.")
                }
            }
        }
    }
}
