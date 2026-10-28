package app.vohcarb.games

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vohcarb.core.api.userMessage
import app.vohcarb.core.ui.Load
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Today's challenges, for everyone ([groupId] null) or one group. */
class DailyGamesViewModel(val groupId: Long?, private val games: GamesApi) : ViewModel() {

    private val _state = MutableStateFlow<Load<DailyOverviewDto>>(Load.Loading)
    val state = _state.asStateFlow()

    fun load() {
        if (_state.value !is Load.Ready) _state.value = Load.Loading
        viewModelScope.launch {
            _state.value = try {
                Load.Ready(games.daily(groupId))
            } catch (e: Exception) {
                Load.Failed(e.userMessage)
            }
        }
    }
}

/** Where a challenge card can take you. */
class GameActions(
    val play: (challenge: String) -> Unit,
    val result: (playId: Long) -> Unit,
    val leaderboard: (challenge: String) -> Unit,
    /** Group only: other members' names and places to accept or reject. */
    val review: (() -> Unit)? = null,
)

/** The Games page in the Practice tab: everyone's daily challenges. */
@Composable
fun DailyGamesScreen(state: Load<DailyOverviewDto>, onRetry: () -> Unit, actions: GameActions) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text(
                "Today's challenges",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 24.dp, top = 16.dp, end = 24.dp),
            )
            Text(
                "Everyone gets the same ones each day; play each once. Play your groups' own challenges from the Groups tab.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
        dailyChallenges(state, onRetry, actions)
    }
}

/** The challenge cards, for the Games page or a group's Games tab. */
fun LazyListScope.dailyChallenges(state: Load<DailyOverviewDto>, onRetry: () -> Unit, actions: GameActions) {
    when (state) {
        Load.Loading -> item {
            Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
        }
        is Load.Failed -> item {
            ListItem(
                headlineContent = { Text(state.message) },
                trailingContent = { TextButton(onClick = onRetry) { Text("Retry") } },
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        is Load.Ready -> {
            actions.review?.let { review ->
                item {
                    TextButton(onClick = review, modifier = Modifier.padding(horizontal = 12.dp)) {
                        Text("Review friends' names and places")
                    }
                }
            }
            items(state.value.challenges, key = { "challenge:" + it.challenge }) { ChallengeCard(it, actions) }
        }
    }
}

@Composable
private fun ChallengeCard(challenge: DailyChallengeDto, actions: GameActions) {
    val done = challenge.status == PlayStatus.DONE
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (done) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(challenge.title, style = MaterialTheme.typography.titleLarge)
            Text(challenge.description, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (challenge.status) {
                    PlayStatus.NOT_STARTED -> Button(onClick = { actions.play(challenge.challenge) }) {
                        Text("Play · ${formatSeconds(challenge.timeLimitSeconds)}")
                    }
                    PlayStatus.IN_PROGRESS -> Button(onClick = { actions.play(challenge.challenge) }) { Text("Continue") }
                    PlayStatus.DONE -> {
                        Text("${challenge.score ?: 0} points", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
                        challenge.playId?.let { id -> OutlinedButton(onClick = { actions.result(id) }) { Text("Your answers") } }
                    }
                }
                TextButton(onClick = { actions.leaderboard(challenge.challenge) }) { Text("Leaderboard") }
            }
        }
    }
}

/** 60 → "1 min", 200 → "3 min 20 s". */
fun formatSeconds(seconds: Int): String {
    val minutes = seconds / 60
    val rest = seconds % 60
    return when {
        minutes == 0 -> "$rest s"
        rest == 0 -> "$minutes min"
        else -> "$minutes min $rest s"
    }
}
