package app.vohcarb.practice

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vohcarb.games.DailyGamesScreen
import app.vohcarb.games.DailyGamesViewModel
import app.vohcarb.games.GameActions

/** The Practice tab: flashcards (spaced repetition) and the daily word games. */
@Composable
fun PracticeAndGames(practice: PracticeViewModel, games: DailyGamesViewModel, gameActions: GameActions) {
    var showGames by rememberSaveable { mutableStateOf(false) }
    val gamesState by games.state.collectAsStateWithLifecycle()
    // Fresh each time it's shown: statuses and scores change after playing
    LaunchedEffect(showGames) { if (showGames) games.load() }

    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = if (showGames) 1 else 0) {
            Tab(selected = !showGames, onClick = { showGames = false }, text = { Text("Flashcards") })
            Tab(selected = showGames, onClick = { showGames = true }, text = { Text("Games") })
        }
        Box(Modifier.weight(1f)) {
            if (showGames) DailyGamesScreen(gamesState, onRetry = games::load, actions = gameActions) else PracticeScreen(practice)
        }
    }
}
