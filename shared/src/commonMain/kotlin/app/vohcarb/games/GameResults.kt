package app.vohcarb.games

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.vohcarb.core.api.userMessage
import app.vohcarb.core.ui.Load
import app.vohcarb.core.ui.LoadContent
import app.vohcarb.core.ui.MessageBox
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** "COMPLETE_THE_WORD_EASY" → "Complete the word: easy" (the server's titles, for screens that only have the code). */
fun challengeTitle(challenge: String): String = when (challenge) {
    "NAME_PLACE_ANIMAL_THING" -> "Name, Place, Animal, Thing"
    "CATEGORY_SPRINT" -> "Category sprint"
    else -> if (challenge.startsWith("COMPLETE_THE_WORD_")) {
        "Complete the word: " + challenge.removePrefix("COMPLETE_THE_WORD_").lowercase()
    } else {
        challenge
    }
}

/** Loads one thing for a screen, with retry. */
open class LoadViewModel<T>(private val fetch: suspend () -> T) : ViewModel() {
    private val _state = MutableStateFlow<Load<T>>(Load.Loading)
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        if (_state.value !is Load.Ready) _state.value = Load.Loading
        viewModelScope.launch {
            _state.value = try {
                Load.Ready(fetch())
            } catch (e: Exception) {
                Load.Failed(e.userMessage)
            }
        }
    }
}

class GameResultViewModel(playId: Long, games: GamesApi) : LoadViewModel<ResultDto>({ games.result(playId) })

class GameLeaderboardViewModel(challenge: String, groupId: Long?, games: GamesApi) :
    LoadViewModel<GameLeaderboardDto>({ games.leaderboard(challenge, groupId) })

class GameReviewViewModel(groupId: Long, private val games: GamesApi) : LoadViewModel<List<ReviewItemDto>>({ games.review(groupId) }) {
    fun vote(answerId: Long, accepted: Boolean) {
        viewModelScope.launch {
            runCatching { games.vote(answerId, accepted) }
            load()
        }
    }
}

@Composable
private fun GameScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding -> Box(Modifier.fillMaxSize().padding(padding)) { content() } }
}

@Composable
fun GameResultScreen(viewModel: GameResultViewModel, onBack: () -> Unit, onLeaderboard: (challenge: String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val title = (state as? Load.Ready)?.value?.challenge?.let(::challengeTitle) ?: "Your answers"
    GameScaffold(title, onBack) {
        LoadContent(state, onRetry = viewModel::load) { result ->
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${result.score}", style = MaterialTheme.typography.displayLarge)
                        Text("points", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (result.late) {
                            Text(
                                "Time ran out before your answers reached the server, so they didn't count.",
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        if (result.challenge == "NAME_PLACE_ANIMAL_THING") {
                            Text(
                                "Scores can change as others play: answers others also gave score 5 instead of 10.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        Button(onClick = { onLeaderboard(result.challenge) }, modifier = Modifier.padding(top = 12.dp)) { Text("Leaderboard") }
                    }
                }
                if (result.answers.isEmpty()) item { MessageBox("No answers this time.") }
                items(result.answers, key = { it.id }) { AnswerRow(result.challenge, it) }
            }
        }
    }
}

@Composable
private fun AnswerRow(challenge: String, a: AnswerResultDto) {
    val label = when {
        a.category != null -> "Letter ${a.item + 1} · ${a.category.lowercase().replaceFirstChar { it.uppercase() }}"
        challenge.startsWith("COMPLETE") -> "Word ${a.item + 1}"
        else -> null
    }
    val note = when {
        a.verdict == Verdict.UNLISTED && a.stands -> "Not in our lists yet: counts unless friends reject it"
        a.verdict == Verdict.UNLISTED -> "Not in our lists"
        a.correctAnswer != null && a.verdict == Verdict.INVALID -> "It was “${a.correctAnswer}”"
        a.verdict == Verdict.INVALID -> "Doesn't count"
        else -> null
    }
    ListItem(
        overlineContent = label?.let { { Text(it) } },
        headlineContent = { Text(a.answer, style = MaterialTheme.typography.titleMedium) },
        supportingContent = note?.let { { Text(it) } },
        trailingContent = {
            Text(
                if (a.points > 0) "+${a.points}" else "0",
                style = MaterialTheme.typography.titleMedium,
                color = if (a.points > 0) Color(0xFF3C8D5A) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}

@Composable
fun GameLeaderboardScreen(viewModel: GameLeaderboardViewModel, challenge: String, inGroup: Boolean, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    GameScaffold(challengeTitle(challenge), onBack) {
        LoadContent(state, onRetry = viewModel::load) { board ->
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Text(
                        "${if (inGroup) "Your group" else "Everyone"} · ${board.date} · ${board.players} played",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                }
                if (board.rows.isEmpty()) item { MessageBox("Nobody has played this one yet today.") }
                items(board.rows, key = { it.userId }) { row ->
                    ListItem(
                        leadingContent = { Rank(row.rank, row.isMe) },
                        headlineContent = {
                            Text(if (row.isMe) "${row.displayName} (you)" else row.displayName, fontWeight = if (row.isMe) FontWeight.Bold else null)
                        },
                        trailingContent = { Text("${row.score}", style = MaterialTheme.typography.titleMedium) },
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Rank(rank: Int, highlight: Boolean) {
    Box(
        Modifier.size(36.dp).background(if (highlight) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$rank",
            style = MaterialTheme.typography.titleMedium,
            color = if (highlight) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun GameReviewScreen(viewModel: GameReviewViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    GameScaffold("Friends' answers", onBack) {
        LoadContent(state, onRetry = viewModel::load) { items ->
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Text(
                        "Names and places from today's Name, Place, Animal, Thing that aren't in our lists. " +
                            "Each counts unless most of you reject it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
                if (items.isEmpty()) item { MessageBox("Nothing to review right now.") }
                items(items, key = { it.answerId }) { item ->
                    ListItem(
                        overlineContent = { Text("${item.playerName} · ${item.category.lowercase()} with ${item.letter}") },
                        headlineContent = { Text(item.answer, style = MaterialTheme.typography.titleLarge) },
                        supportingContent = { Text("${item.accepts} accepted · ${item.rejects} rejected") },
                        trailingContent = {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                FilterChip(selected = item.myVote == true, onClick = { viewModel.vote(item.answerId, true) }, label = { Text("OK") })
                                FilterChip(selected = item.myVote == false, onClick = { viewModel.vote(item.answerId, false) }, label = { Text("No") })
                            }
                        },
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
}
