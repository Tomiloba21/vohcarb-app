package app.vohcarb.games

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vohcarb.core.nowMillis
import app.vohcarb.core.ui.DefinitionStyle
import app.vohcarb.core.ui.LoadingBox
import app.vohcarb.core.ui.MessageBox
import kotlinx.coroutines.delay

@Composable
fun GamePlayScreen(viewModel: GamePlayViewModel, title: String, onClose: () -> Unit, onFinished: (playId: Long) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(nowMillis()) }
    LaunchedEffect(viewModel) {
        while (true) {
            now = nowMillis()
            viewModel.tick(now)
            delay(250)
        }
    }
    val finished = state.phase as? PlayPhase.Finished
    LaunchedEffect(finished) { finished?.let { onFinished(it.playId) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close") } },
                actions = {
                    if (state.phase == PlayPhase.Playing) {
                        val seconds = ((state.stepEndsAt - now).coerceAtLeast(0) + 999) / 1000
                        Text(
                            "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}",
                            style = MaterialTheme.typography.titleLarge,
                            color = if (seconds <= 10) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            when (val phase = state.phase) {
                PlayPhase.Loading, PlayPhase.Submitting, is PlayPhase.Finished -> LoadingBox()
                is PlayPhase.Failed -> MessageBox(phase.message, actionLabel = "Back", onAction = onClose)
                is PlayPhase.SubmitFailed -> MessageBox(
                    "Couldn't hand in your answers: ${phase.message}",
                    actionLabel = "Try again",
                    onAction = viewModel::submit,
                )
                PlayPhase.Playing -> when (state.game) {
                    Game.NAME_PLACE_ANIMAL_THING -> NpatPlay(state, viewModel)
                    Game.CATEGORY_SPRINT -> SprintPlay(state, viewModel)
                    Game.COMPLETE_THE_WORD -> CompletePlay(state, viewModel)
                    null -> Unit
                }
            }
        }
    }
}

@Composable
private fun StepProgress(label: String, step: Int, steps: Int) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        LinearProgressIndicator(progress = { step.toFloat() / steps }, modifier = Modifier.fillMaxWidth())
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun NpatPlay(state: PlayState, viewModel: GamePlayViewModel) {
    val letter = state.play?.letters?.getOrNull(state.round).orEmpty()
    val answers = state.rounds.getOrNull(state.round) ?: NpatRoundAnswers()
    val first = remember(state.round) { FocusRequester() }
    LaunchedEffect(state.round) { first.requestFocus() }

    StepProgress("Letter ${state.round + 1} of ${state.rounds.size}", state.round, state.rounds.size)
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(letter, style = MaterialTheme.typography.displayLarge, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text(
            "Everything starts with $letter. Unique answers score 10, answers others also gave score 5.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val fields = listOf(
            Triple("Name", answers.name) { v: String -> viewModel.editRound { copy(name = v) } },
            Triple("Place", answers.place) { v: String -> viewModel.editRound { copy(place = v) } },
            Triple("Animal", answers.animal) { v: String -> viewModel.editRound { copy(animal = v) } },
            Triple("Thing", answers.thing) { v: String -> viewModel.editRound { copy(thing = v) } },
        )
        fields.forEachIndexed { i, (label, value, onChange) ->
            OutlinedTextField(
                value = value.orEmpty(),
                onValueChange = { onChange(it.take(64)) },
                label = { Text(label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = if (i < fields.lastIndex) ImeAction.Next else ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { viewModel.nextRound() }),
                modifier = Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(first) else Modifier),
            )
        }
        Button(onClick = viewModel::nextRound, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(if (state.round < state.rounds.lastIndex) "Next letter" else "Finish")
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SprintPlay(state: PlayState, viewModel: GamePlayViewModel) {
    var input by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val add = {
        viewModel.addWord(input)
        input = ""
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Words about", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(state.play?.category?.name.orEmpty(), style = MaterialTheme.typography.displaySmall)
        Text(
            "Type a word and press enter. Each one in the category scores its level: rarer words score more.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = input,
            onValueChange = { input = it.take(64) },
            label = { Text("Word") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        Text("${state.words.size} word${if (state.words.size == 1) "" else "s"}", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.words.forEach { word ->
                InputChip(
                    selected = false,
                    onClick = { viewModel.removeWord(word) },
                    label = { Text(word) },
                    trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Remove $word") },
                )
            }
        }
        OutlinedButton(onClick = viewModel::submit, modifier = Modifier.fillMaxWidth()) { Text("I'm done") }
    }
}

@Composable
private fun CompletePlay(state: PlayState, viewModel: GamePlayViewModel) {
    val hint = state.hints.getOrNull(state.index) ?: return
    var input by remember(state.index) { mutableStateOf("") }
    val focus = remember(state.index) { FocusRequester() }
    LaunchedEffect(state.index) { focus.requestFocus() }

    StepProgress("Word ${state.index + 1} of ${state.hints.size}", state.index, state.hints.size)
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(hint.partOfSpeech, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        Text(hint.definition, style = DefinitionStyle)
        // Spaced letters, so the blanks are easy to count
        Text(
            hint.mask.uppercase().toList().joinToString(" "),
            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 30.sp, letterSpacing = 2.sp),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Text(
            "${hint.length} letters",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        OutlinedTextField(
            value = input,
            onValueChange = { input = it.take(64) },
            label = { Text("The word") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { viewModel.nextWord(input) }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = viewModel::reveal, enabled = !state.revealing && '_' in hint.mask, modifier = Modifier.weight(1f)) {
                Text("Reveal a letter (−3)")
            }
            TextButton(onClick = { viewModel.nextWord(null) }) { Text("Skip") }
        }
        Button(onClick = { viewModel.nextWord(input) }, enabled = input.isNotBlank(), modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(if (state.index < state.hints.lastIndex) "Answer" else "Answer and finish")
        }
        Spacer(Modifier.height(16.dp))
    }
}
