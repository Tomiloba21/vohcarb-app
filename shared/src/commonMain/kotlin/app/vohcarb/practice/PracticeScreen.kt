package app.vohcarb.practice

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.vohcarb.core.ui.DefinitionStyle
import app.vohcarb.core.ui.Load
import app.vohcarb.core.ui.LoadContent
import app.vohcarb.core.ui.LoadingBox
import app.vohcarb.core.ui.MessageBox
import app.vohcarb.practice.srs.Grade
import app.vohcarb.sync.SyncEngine
import app.vohcarb.word.Senses
import app.vohcarb.word.WordHeader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class Round(
    val items: List<PracticeItem>,
    val index: Int = 0,
    /** The option the user picked, once they've answered. */
    val picked: Int? = null,
    /** For words without a question: whether the meaning is showing. */
    val revealed: Boolean = false,
    val correct: Int = 0,
) {
    val current get() = items.getOrNull(index)
    val finished get() = index >= items.size
}

sealed interface PracticeState {
    data class Home(val stats: Load<LocalStats>) : PracticeState
    data object Starting : PracticeState
    data class Playing(val round: Round) : PracticeState
    data class Done(val reviewed: Int, val correct: Int) : PracticeState
}

/**
 * Practice runs on the phone: sessions come from the local deck, answers are graded and scheduled here
 * and uploaded by the sync engine.
 */
class PracticeViewModel(private val practice: PracticeRepository, private val sync: SyncEngine) : ViewModel() {

    private val _state = MutableStateFlow<PracticeState>(PracticeState.Home(Load.Loading))
    val state = _state.asStateFlow()

    fun loadStats() {
        if (_state.value !is PracticeState.Home) _state.value = PracticeState.Home(Load.Loading)
        viewModelScope.launch {
            showStats()
            // Then catch up with the server (new cards, other devices) and show the fresh numbers
            if (sync.syncNow()) showStats()
        }
    }

    private suspend fun showStats() {
        val stats = practice.stats()
        _state.update { if (it is PracticeState.Home) PracticeState.Home(Load.Ready(stats)) else it }
    }

    fun start() {
        _state.value = PracticeState.Starting
        viewModelScope.launch {
            val items = practice.session(SESSION_SIZE)
            _state.value = if (items.isEmpty()) PracticeState.Home(Load.Ready(practice.stats())) else PracticeState.Playing(Round(items))
        }
    }

    fun pick(option: Int) = updateRound { if (it.picked == null) it.copy(picked = option) else it }

    fun reveal() = updateRound { it.copy(revealed = true) }

    fun grade(grade: Grade) {
        val round = (_state.value as? PracticeState.Playing)?.round ?: return
        val item = round.current ?: return
        val wasCorrect = item.question?.let { round.picked == it.correctIndex } ?: (grade != Grade.AGAIN)
        val next = round.copy(index = round.index + 1, picked = null, revealed = false, correct = round.correct + if (wasCorrect) 1 else 0)
        _state.value = if (next.finished) PracticeState.Done(next.items.size, next.correct) else PracticeState.Playing(next)
        viewModelScope.launch {
            practice.grade(item.word.slug, grade)
            if (next.finished) sync.syncNow(full = false)
        }
    }

    private fun updateRound(change: (Round) -> Round) = _state.update {
        if (it is PracticeState.Playing) PracticeState.Playing(change(it.round)) else it
    }

    private companion object {
        const val SESSION_SIZE = 10
    }
}

@Composable
fun PracticeScreen(viewModel: PracticeViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { if (viewModel.state.value is PracticeState.Home) viewModel.loadStats() }

    when (val s = state) {
        is PracticeState.Home -> LoadContent(s.stats, onRetry = viewModel::loadStats) { stats -> PracticeHome(stats, viewModel::start) }
        PracticeState.Starting -> LoadingBox()
        is PracticeState.Playing -> PracticeRound(s.round, viewModel)
        is PracticeState.Done -> PracticeDone(s, onContinue = viewModel::loadStats)
    }
}

@Composable
private fun PracticeHome(stats: LocalStats, onStart: () -> Unit) {
    if (stats.totalCards == 0L && stats.newWords == 0L) {
        MessageBox("Save words from your feed and they'll show up here as flashcards.")
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Practice", style = MaterialTheme.typography.displaySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Due now", stats.reviewsDue.toString(), Modifier.weight(1f))
            StatCard("New", stats.newWords.toString(), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Streak", plural(stats.streakDays.toLong(), "day"), Modifier.weight(1f))
            StatCard("Today", "${stats.reviewedToday} answered", Modifier.weight(1f))
        }
        val ready = stats.reviewsDue + stats.newWords
        Button(onClick = onStart, enabled = ready > 0, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(if (ready > 0) "Start practice" else "Nothing due. Come back later!")
        }
        Text(
            "${plural(stats.totalCards + stats.newWords, "word")} in your deck. Words come back just before you'd forget them.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun PracticeRound(round: Round, viewModel: PracticeViewModel) {
    val item = round.current ?: return
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp)) {
        LinearProgressIndicator(progress = { round.index.toFloat() / round.items.size }, modifier = Modifier.fillMaxWidth())
        Text(
            "${round.index + 1} of ${round.items.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val question = item.question
            if (question != null) {
                QuestionView(question, round.picked, viewModel::pick)
            } else {
                WordHeader(item.word)
                if (round.revealed) Senses(item.word, maxSenses = 3)
            }
        }
        GradeBar(item, round, viewModel)
    }
}

@Composable
private fun QuestionView(question: QuizQuestion, picked: Int?, onPick: (Int) -> Unit) {
    when (question.type) {
        QuestionType.MEANING_OF_WORD -> {
            Text("What does this word mean?", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(question.prompt, style = MaterialTheme.typography.displaySmall)
        }
        QuestionType.WORD_FOR_MEANING -> {
            Text("Which word means…", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(question.prompt, style = DefinitionStyle)
        }
    }
    question.options.forEachIndexed { index, option ->
        val color = when {
            picked == null -> null
            index == question.correctIndex -> Color(0xFF3C8D5A)
            index == picked -> MaterialTheme.colorScheme.error
            else -> null
        }
        OutlinedCard(
            onClick = { onPick(index) },
            enabled = picked == null,
            border = BorderStroke(if (color != null) 2.dp else 1.dp, color ?: MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                option,
                style = if (question.type == QuestionType.WORD_FOR_MEANING) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                color = color ?: MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun GradeBar(item: PracticeItem, round: Round, viewModel: PracticeViewModel) {
    val question = item.question
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            question != null && round.picked == null -> Unit
            question != null && round.picked != question.correctIndex ->
                Button(onClick = { viewModel.grade(Grade.AGAIN) }, modifier = Modifier.fillMaxWidth()) { Text("Next (I'll see it again soon)") }
            question == null && !round.revealed ->
                OutlinedButton(onClick = viewModel::reveal, modifier = Modifier.fillMaxWidth()) { Text("Show meaning") }
            else -> {
                Text(
                    "How easy was that?",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (question == null) GradeButton("Forgot", Grade.AGAIN, viewModel, Modifier.weight(1f))
                    GradeButton("Hard", Grade.HARD, viewModel, Modifier.weight(1f))
                    GradeButton("Good", Grade.GOOD, viewModel, Modifier.weight(1f))
                    GradeButton("Easy", Grade.EASY, viewModel, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun GradeButton(label: String, grade: Grade, viewModel: PracticeViewModel, modifier: Modifier) {
    if (grade == Grade.GOOD) {
        Button(onClick = { viewModel.grade(grade) }, modifier = modifier) { Text(label) }
    } else {
        OutlinedButton(onClick = { viewModel.grade(grade) }, modifier = modifier, contentPadding = ButtonDefaults.TextButtonContentPadding) {
            Text(label)
        }
    }
}

@Composable
private fun PracticeDone(done: PracticeState.Done, onContinue: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Nice work!", style = MaterialTheme.typography.displaySmall)
        Text(
            "${done.correct} of ${done.reviewed} right",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Your answers are saved on the phone and sent to the server when you're online.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onContinue) { Text("Continue") }
    }
}

private fun plural(count: Long, noun: String) = "$count $noun${if (count == 1L) "" else "s"}"
