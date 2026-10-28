package app.vohcarb.live

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.vohcarb.core.api.userMessage
import app.vohcarb.core.ui.Load
import app.vohcarb.games.challengeTitle
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** The five things a live match can be (the server's Challenge names). */
val liveChallenges = listOf(
    "NAME_PLACE_ANIMAL_THING", "CATEGORY_SPRINT", "COMPLETE_THE_WORD_EASY", "COMPLETE_THE_WORD_MEDIUM", "COMPLETE_THE_WORD_HARD",
)

sealed interface LobbyDialog {
    data object Quick : LobbyDialog
    data object Create : LobbyDialog
    data object Code : LobbyDialog
}

/** Live play's front door. [groupId]: opened from a group, so new rooms are for that group. */
class LiveLobbyViewModel(val groupId: Long?, private val live: LiveApi) : ViewModel() {

    private val _lobby = MutableStateFlow<Load<LobbyDto>>(Load.Loading)
    val lobby = _lobby.asStateFlow()

    private val _dialog = MutableStateFlow<LobbyDialog?>(null)
    val dialog = _dialog.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    private val _opened = Channel<String>(Channel.BUFFERED)

    /** Rooms to open, by id. */
    val opened = _opened.receiveAsFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun load() {
        if (_lobby.value !is Load.Ready) _lobby.value = Load.Loading
        viewModelScope.launch {
            _lobby.value = try {
                Load.Ready(live.lobby())
            } catch (e: Exception) {
                Load.Failed(e.userMessage)
            }
        }
    }

    fun openDialog(dialog: LobbyDialog?) {
        _dialog.value = dialog
    }

    fun quick(challenge: String) = enter { live.quick(challenge) }

    fun create(challenge: String, public: Boolean) =
        enter { live.create(challenge, if (public && groupId == null) RoomKind.PUBLIC else RoomKind.FRIENDS, groupId) }

    fun joinByCode(code: String) = enter { live.joinByCode(code) }

    fun join(room: RoomSummaryDto) = enter { live.join(room.id) }

    /** Rooms you're already in open straight away. */
    fun rejoin(room: RoomSummaryDto) {
        _opened.trySend(room.id)
    }

    private fun enter(call: suspend () -> RoomSummaryDto) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                val room = call()
                _dialog.value = null
                _opened.trySend(room.id)
            } catch (e: Exception) {
                _messages.trySend(e.userMessage)
            } finally {
                _busy.value = false
            }
        }
    }
}

@Composable
fun LiveLobbyScreen(viewModel: LiveLobbyViewModel, onBack: () -> Unit, onOpenRoom: (String) -> Unit) {
    val lobby by viewModel.lobby.collectAsStateWithLifecycle()
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.load() }
    LaunchedEffect(viewModel) { viewModel.opened.collect(onOpenRoom) }
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.groupId != null) "Play live with your group" else "Play live") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = { TextButton(onClick = viewModel::load) { Text("Refresh") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Play the word games against 1 to 5 others, at the same time. Everyone gets the same letters and words.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (viewModel.groupId == null) {
                        Button(onClick = { viewModel.openDialog(LobbyDialog.Quick) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Quick match with anyone")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { viewModel.openDialog(LobbyDialog.Create) }, modifier = Modifier.weight(1f)) { Text("New room") }
                        OutlinedButton(onClick = { viewModel.openDialog(LobbyDialog.Code) }, modifier = Modifier.weight(1f)) { Text("Join with code") }
                    }
                }
            }
            when (val l = lobby) {
                Load.Loading -> item { Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() } }
                is Load.Failed -> item {
                    ListItem(headlineContent = { Text(l.message) }, trailingContent = { TextButton(onClick = viewModel::load) { Text("Retry") } })
                }
                is Load.Ready -> {
                    rooms("Your rooms", l.value.mine, "Open") { viewModel.rejoin(it) }
                    rooms("Your groups' rooms", l.value.groups, "Join") { viewModel.join(it) }
                    if (viewModel.groupId == null) rooms("Open rooms", l.value.open, "Join") { viewModel.join(it) }
                    if (l.value.mine.isEmpty() && l.value.groups.isEmpty() && (viewModel.groupId != null || l.value.open.isEmpty())) {
                        item {
                            Text(
                                "No rooms waiting right now. Start one and share its code, or try a quick match.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(24.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    when (dialog) {
        LobbyDialog.Quick -> ChallengeDialog("Quick match", "Find players", busy, showPublic = false, onDismiss = { viewModel.openDialog(null) }) { c, _ ->
            viewModel.quick(c)
        }
        LobbyDialog.Create -> ChallengeDialog(
            "New room", "Create", busy, showPublic = viewModel.groupId == null, onDismiss = { viewModel.openDialog(null) },
        ) { c, public -> viewModel.create(c, public) }
        LobbyDialog.Code -> CodeDialog(busy, onDismiss = { viewModel.openDialog(null) }, onJoin = viewModel::joinByCode)
        null -> Unit
    }
}

private fun LazyListScope.rooms(title: String, rooms: List<RoomSummaryDto>, action: String, onClick: (RoomSummaryDto) -> Unit) {
    if (rooms.isEmpty()) return
    item {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 24.dp, top = 12.dp))
    }
    items(rooms, key = { "$title:${it.id}" }) { room ->
        ListItem(
            headlineContent = { Text(room.title) },
            supportingContent = { Text("${room.hostName}'s room · ${room.players}/${room.maxPlayers} players" + if (room.phase != RoomPhase.LOBBY) " · playing" else "") },
            trailingContent = { TextButton(onClick = { onClick(room) }) { Text(action) } },
            modifier = Modifier.clickable { onClick(room) }.padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun ChallengeDialog(
    title: String,
    confirm: String,
    busy: Boolean,
    showPublic: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (challenge: String, public: Boolean) -> Unit,
) {
    var challenge by remember { mutableStateOf(liveChallenges.first()) }
    var public by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = {
            Column {
                liveChallenges.forEach { c ->
                    Row(Modifier.fillMaxWidth().selectable(selected = c == challenge, onClick = { challenge = c }), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = c == challenge, onClick = { challenge = c })
                        Text(challengeTitle(c))
                    }
                }
                if (showPublic) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        FilterChip(selected = !public, onClick = { public = false }, label = { Text("Friends (code)") })
                        FilterChip(selected = public, onClick = { public = true }, label = { Text("Anyone (listed)") })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(challenge, public) }, enabled = !busy) { Text(if (busy) "…" else confirm) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}

@Composable
private fun CodeDialog(busy: Boolean, onDismiss: () -> Unit, onJoin: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Join a room") },
        text = {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.take(12) },
                label = { Text("Room code, e.g. ABC 123") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            )
        },
        confirmButton = { TextButton(onClick = { onJoin(code) }, enabled = code.isNotBlank() && !busy) { Text("Join") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}
