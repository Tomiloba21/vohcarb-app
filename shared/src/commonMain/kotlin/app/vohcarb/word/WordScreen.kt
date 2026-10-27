package app.vohcarb.word

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.vohcarb.catalog.CatalogApi
import app.vohcarb.catalog.WordCache
import app.vohcarb.catalog.WordDetailDto
import app.vohcarb.core.api.userMessage
import app.vohcarb.core.ui.Load
import app.vohcarb.core.ui.LoadContent
import app.vohcarb.groups.AddToGroupDialog
import app.vohcarb.groups.GroupsApi
import app.vohcarb.progress.ProgressRepository
import app.vohcarb.progress.WordFlags
import app.vohcarb.progress.WordStatePatch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WordState(val word: WordDetailDto, val flags: WordFlags)

class WordViewModel(
    private val slug: String,
    private val words: WordCache,
    private val catalog: CatalogApi,
    private val progress: ProgressRepository,
) : ViewModel() {

    private val word = MutableStateFlow<Load<WordDetailDto>>(Load.Loading)

    val state = combine(word, progress.flags(slug)) { word, flags ->
        when (word) {
            is Load.Ready -> Load.Ready(WordState(word.value, flags))
            is Load.Failed -> word
            Load.Loading -> Load.Loading
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Load.Loading)

    init {
        load()
    }

    /** The phone's copy first, then a fresh one from the server when online. */
    fun load() {
        viewModelScope.launch {
            val cached = words.detail(slug)
            word.value = cached?.let { Load.Ready(it) } ?: Load.Loading
            try {
                val fresh = catalog.word(slug)
                words.save(fresh)
                word.value = Load.Ready(fresh)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (cached == null) word.value = Load.Failed(e.userMessage)
            }
        }
    }

    fun update(patch: WordStatePatch) {
        viewModelScope.launch { progress.update(slug, patch) }
    }
}

@Composable
fun WordScreen(viewModel: WordViewModel, groups: GroupsApi, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var addingToGroup by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (state is Load.Ready) {
                        IconButton(onClick = { addingToGroup = true }) {
                            Icon(Icons.Outlined.GroupAdd, contentDescription = "Add to group")
                        }
                    }
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

    val ready = (state as? Load.Ready)?.value
    if (addingToGroup && ready != null) {
        AddToGroupDialog(
            slug = ready.word.slug,
            lemma = ready.word.lemma,
            groups = groups,
            onDismiss = { addingToGroup = false },
            onDone = { message ->
                addingToGroup = false
                scope.launch { snackbar.showSnackbar(message) }
            },
        )
    }
}
