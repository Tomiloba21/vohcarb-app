package app.vohcarb.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vohcarb.core.ui.DefinitionStyle
import app.vohcarb.core.ui.LoadingBox
import app.vohcarb.core.ui.MessageBox
import app.vohcarb.core.ui.plural
import app.vohcarb.core.ui.rememberShareText
import app.vohcarb.games.Verdict
import kotlinx.coroutines.delay

@Composable
fun LiveRoomScreen(viewModel: LiveRoomViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val inputs by viewModel.inputs.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var leaving by remember { mutableStateOf(false) }
    var left by remember { mutableLongStateOf(0L) }
    LaunchedEffect(viewModel) { viewModel.errors.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(viewModel) {
        while (true) {
            left = viewModel.millisLeft() ?: 0L
            viewModel.tick()
            delay(200)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state?.title ?: "Live game") },
                navigationIcon = {
                    IconButton(onClick = { if (state?.phase == RoomPhase.FINISHED) viewModel.leave(onClose) else leaving = true }) {
                        Icon(Icons.Filled.Close, contentDescription = "Leave")
                    }
                },
                actions = {
                    val s = state
                    if (s != null && s.endsAt != null && s.phase != RoomPhase.FINISHED) {
                        val seconds = ((left.coerceAtLeast(0) + 999) / 1000)
                        Text(
                            "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}",
                            style = MaterialTheme.typography.titleLarge,
                            color = if (seconds <= 5 && s.phase == RoomPhase.ROUND) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            (status as? LinkStatus.Reconnecting)?.let {
                Text(
                    "Reconnecting…",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().padding(4.dp),
                    textAlign = TextAlign.Center,
                )
            }
            val s = state
            when {
                status == LinkStatus.Closed -> MessageBox("You're no longer in this room.", actionLabel = "Back", onAction = onClose)
                s == null -> LoadingBox()
                else -> when (s.phase) {
                    RoomPhase.LOBBY -> Lobby(s, left, viewModel)
                    RoomPhase.COUNTDOWN -> Countdown(left)
                    RoomPhase.ROUND -> Round(s, inputs, viewModel)
                    RoomPhase.VOTING -> Voting(s, viewModel)
                    RoomPhase.RESULTS -> RoundResults(s)
                    RoomPhase.FINISHED -> Finished(s) { viewModel.leave(onClose) }
                }
            }
        }
    }

    if (leaving) {
        AlertDialog(
            onDismissRequest = { leaving = false },
            title = { Text("Leave this room?") },
            text = { Text(if (state?.phase == RoomPhase.LOBBY) "You can join again while it's waiting." else "The match goes on without you.") },
            confirmButton = { TextButton(onClick = { leaving = false; viewModel.leave(onClose) }) { Text("Leave") } },
            dismissButton = { TextButton(onClick = { leaving = false }) { Text("Stay") } },
        )
    }
}

@Composable
private fun Players(state: RoomStateDto, showSubmitted: Boolean) {
    Column(Modifier.fillMaxWidth()) {
        state.players.forEach { p ->
            ListItem(
                headlineContent = {
                    Text(
                        p.name + (if (p.userId == state.you) " (you)" else "") + (if (p.userId == state.hostId) " · host" else ""),
                        fontWeight = if (p.userId == state.you) FontWeight.Bold else null,
                    )
                },
                supportingContent = if (!p.connected) {
                    { Text("Offline", color = MaterialTheme.colorScheme.error) }
                } else {
                    null
                },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (showSubmitted && p.submitted) Icon(Icons.Filled.Check, contentDescription = "Answered", tint = Color(0xFF3C8D5A))
                        Text("${p.score}", style = MaterialTheme.typography.titleMedium)
                    }
                },
            )
        }
    }
}

@Composable
private fun Lobby(state: RoomStateDto, left: Long, viewModel: LiveRoomViewModel) {
    val share = rememberShareText()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.kind != RoomKind.QUICK) {
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Room code", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatRoomCode(state.code), style = MaterialTheme.typography.headlineMedium)
                    }
                    OutlinedButton(onClick = { share("Play ${state.title} with me on Vohcarb: room code ${formatRoomCode(state.code)}") }) {
                        Icon(Icons.Filled.Share, contentDescription = null)
                        Text("Invite", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        Text(
            "${plural(state.players.size, "player")} of up to ${state.maxPlayers}",
            style = MaterialTheme.typography.titleMedium,
        )
        Players(state, showSubmitted = false)
        when {
            state.kind == RoomKind.QUICK && state.endsAt != null ->
                Text("Starting in ${(left.coerceAtLeast(0) + 999) / 1000} s. More players may join.", style = MaterialTheme.typography.bodyLarge)
            state.kind == RoomKind.QUICK ->
                Text("Waiting for another player to join…", style = MaterialTheme.typography.bodyLarge)
            state.isHost -> Button(
                onClick = viewModel::start,
                enabled = state.players.size >= state.minPlayers,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(if (state.players.size >= state.minPlayers) "Start the match" else "Waiting for players…")
            }
            else -> Text("Waiting for the host to start…", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun Countdown(left: Long) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("${((left.coerceAtLeast(0) + 999) / 1000).coerceAtLeast(1)}", style = MaterialTheme.typography.displayLarge.copy(fontSize = 96.sp))
    }
}

@Composable
private fun Round(state: RoomStateDto, inputs: LiveInputs, viewModel: LiveRoomViewModel) {
    val done = inputs.sent || state.youSubmitted
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LinearProgressIndicator(progress = { (state.round + 1f) / state.rounds.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
        Text("Round ${state.round + 1} of ${state.rounds}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when {
            state.letter != null -> NpatInputs(state.letter, inputs, done, viewModel)
            state.category != null -> SprintInputs(state.category.name, inputs, done, viewModel)
            state.hint != null -> CompleteInputs(state, inputs, done, viewModel)
        }
        if (done) {
            Text("Answers in. Waiting for the others…", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Players(state, showSubmitted = true)
    }
}

@Composable
private fun NpatInputs(letter: String, inputs: LiveInputs, done: Boolean, viewModel: LiveRoomViewModel) {
    Text(letter, style = MaterialTheme.typography.displayLarge, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    val fields = listOf(
        Triple("Name", inputs.npat.name) { v: String -> viewModel.editNpat { copy(name = v) } },
        Triple("Place", inputs.npat.place) { v: String -> viewModel.editNpat { copy(place = v) } },
        Triple("Animal", inputs.npat.animal) { v: String -> viewModel.editNpat { copy(animal = v) } },
        Triple("Thing", inputs.npat.thing) { v: String -> viewModel.editNpat { copy(thing = v) } },
    )
    fields.forEachIndexed { i, (label, value, onChange) ->
        OutlinedTextField(
            value = value.orEmpty(),
            onValueChange = { onChange(it.take(64)) },
            label = { Text(label) },
            enabled = !done,
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = if (i < fields.lastIndex) ImeAction.Next else ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { viewModel.submitNpat() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Button(onClick = viewModel::submitNpat, enabled = !done, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Done") }
}

@Composable
private fun SprintInputs(category: String, inputs: LiveInputs, done: Boolean, viewModel: LiveRoomViewModel) {
    var input by remember { mutableStateOf("") }
    Text("Words about", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(category, style = MaterialTheme.typography.displaySmall)
    OutlinedTextField(
        value = input,
        onValueChange = { input = it.take(64) },
        label = { Text("Word") },
        enabled = !done,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            viewModel.addWord(input)
            input = ""
        }),
        modifier = Modifier.fillMaxWidth(),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        inputs.words.forEach { word ->
            InputChip(selected = false, onClick = { viewModel.removeWord(word) }, label = { Text(word) }, trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Remove $word") })
        }
    }
    Text("${plural(inputs.words.size, "word")} · handed in when time's up", style = MaterialTheme.typography.bodyMedium)
    OutlinedButton(onClick = viewModel::submitWords, enabled = !done, modifier = Modifier.fillMaxWidth()) { Text("I'm done") }
}

@Composable
private fun CompleteInputs(state: RoomStateDto, inputs: LiveInputs, done: Boolean, viewModel: LiveRoomViewModel) {
    val hint = state.hint ?: return
    Text(hint.partOfSpeech, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
    Text(hint.definition, style = DefinitionStyle)
    Text(
        hint.mask.uppercase().toList().joinToString(" "),
        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 30.sp, letterSpacing = 2.sp),
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
    OutlinedTextField(
        value = inputs.answer,
        onValueChange = viewModel::editAnswer,
        label = { Text("The word") },
        enabled = !done,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { viewModel.submitAnswer() }),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = viewModel::reveal, enabled = !done && '_' in hint.mask, modifier = Modifier.weight(1f)) { Text("Reveal a letter (−3)") }
        Button(onClick = viewModel::submitAnswer, enabled = !done && inputs.answer.isNotBlank(), modifier = Modifier.weight(1f)) { Text("Answer") }
    }
    Text("The quicker you answer, the more it's worth.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Voting(state: RoomStateDto, viewModel: LiveRoomViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Do these count?", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Names and places that aren't in our lists. Each counts unless most of you reject it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.votes.orEmpty().forEach { v ->
            ListItem(
                overlineContent = { Text("${v.playerName} · ${v.category.lowercase()}") },
                headlineContent = { Text(v.answer, style = MaterialTheme.typography.titleLarge) },
                supportingContent = { Text("${v.accepts} yes · ${v.rejects} no") },
                trailingContent = {
                    if (v.mine) {
                        Text("Yours", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(selected = v.myVote == true, onClick = { viewModel.vote(v.key, true) }, label = { Text("Yes") })
                            FilterChip(selected = v.myVote == false, onClick = { viewModel.vote(v.key, false) }, label = { Text("No") })
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun RoundResults(state: RoomStateDto) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Round ${state.round + 1} of ${state.rounds}", style = MaterialTheme.typography.headlineSmall)
        RoundBreakdown(state)
    }
}

/** Each player's answers and points in the round just played. */
@Composable
private fun RoundBreakdown(state: RoomStateDto) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.correctAnswer?.let { Text("The word was “$it”", style = DefinitionStyle) }
        state.results.orEmpty().sortedByDescending { it.roundPoints }.forEach { r ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (r.userId == state.you) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(r.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text("+${r.roundPoints}", style = MaterialTheme.typography.titleMedium)
                    }
                    if (r.answers.isEmpty()) Text("No answer", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    r.answers.forEach { a ->
                        val label = a.category?.lowercase()?.replaceFirstChar { it.uppercase() }?.let { "$it: " }.orEmpty()
                        val note = when {
                            a.verdict == Verdict.UNLISTED && !a.stands -> " (rejected)"
                            a.verdict == Verdict.INVALID -> " ✗"
                            else -> ""
                        }
                        Text("$label${a.answer}$note  +${a.points}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun Finished(state: RoomStateDto, onDone: () -> Unit) {
    val best = state.players.maxOfOrNull { it.score } ?: 0
    val winners = state.players.filter { it.score == best }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(16.dp))
        Text(
            when {
                winners.size > 1 -> "It's a draw!"
                winners.single().userId == state.you -> "You won!"
                else -> "${winners.single().name} won"
            },
            style = MaterialTheme.typography.displaySmall,
        )
        Players(state, showSubmitted = false)
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        if (!state.results.isNullOrEmpty()) {
            Text(
                if (state.rounds > 1) "Last round" else "Answers",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            RoundBreakdown(state)
        }
    }
}
