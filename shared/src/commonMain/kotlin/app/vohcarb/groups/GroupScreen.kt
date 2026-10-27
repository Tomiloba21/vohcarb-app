package app.vohcarb.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vohcarb.catalog.WordDetailDto
import app.vohcarb.core.ui.Load
import app.vohcarb.core.ui.LoadContent
import app.vohcarb.core.ui.plural
import app.vohcarb.core.ui.rememberShareText

@Composable
fun GroupScreen(viewModel: GroupViewModel, onBack: () -> Unit, onOpenWord: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val share = rememberShareText()
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(viewModel) { viewModel.closed.collect { onBack() } }

    val group = (state.group as? Load.Ready)?.value
    val shareInvite = {
        group?.let { share("Join my group “${it.name}” on Vohcarb with the code ${formatInviteCode(it.inviteCode)}") }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(group?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = { if (group != null) GroupMenu(group, viewModel) { shareInvite() } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LoadContent(state.group, onRetry = viewModel::load) { detail ->
                LazyColumn(Modifier.fillMaxSize()) {
                    item { InviteCard(detail, onShare = { shareInvite() }) }
                    state.wordOfTheDay?.let { word -> item { WordOfTheDayCard(word, onClick = { onOpenWord(word.slug) }) } }
                    item {
                        PrimaryTabRow(selectedTabIndex = state.tab.ordinal, modifier = Modifier.padding(top = 8.dp)) {
                            GroupTab.entries.forEach { tab ->
                                Tab(selected = tab == state.tab, onClick = { viewModel.selectTab(tab) }, text = { Text(tab.label) })
                            }
                        }
                    }
                    when (state.tab) {
                        GroupTab.WORDS -> wordsTab(state.words, viewModel, onOpenWord)
                        GroupTab.LEADERBOARD -> leaderboardTab(state, viewModel)
                        GroupTab.MEMBERS -> membersTab(detail, viewModel)
                    }
                }
            }
        }
    }

    state.addWord?.let { AddWordDialog(it, viewModel) }
    state.confirm?.let { ConfirmDialog(it, viewModel) }
}

@Composable
private fun GroupMenu(group: GroupDetailDto, viewModel: GroupViewModel, onShare: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(text = { Text("Share invite") }, onClick = { open = false; onShare() })
        if (group.isOwner) {
            DropdownMenuItem(text = { Text("New invite code") }, onClick = { open = false; viewModel.ask(Confirm.NewCode) })
        }
        DropdownMenuItem(text = { Text("Leave group") }, onClick = { open = false; viewModel.ask(Confirm.Leave) })
        if (group.isOwner) {
            DropdownMenuItem(text = { Text("Delete group") }, onClick = { open = false; viewModel.ask(Confirm.Delete) })
        }
    }
}

@Composable
private fun InviteCard(group: GroupDetailDto, onShare: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Invite code", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatInviteCode(group.inviteCode), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
                Text(
                    "${group.members.size} of 50 members",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onShare) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Share", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun WordOfTheDayCard(word: WordDetailDto, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Group word of the day", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(word.lemma, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            word.senses.firstOrNull()?.let {
                Text(it.definition, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun LazyListScope.wordsTab(words: GroupWords, viewModel: GroupViewModel, onOpenWord: (String) -> Unit) {
    item {
        TextButton(onClick = viewModel::openAddWord, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text("Add a word", modifier = Modifier.padding(start = 8.dp))
        }
    }
    if (words.items.isEmpty() && !words.loading) {
        item { EmptyText("No words yet. Add one here, or tap “Add to group” on any word.") }
    }
    items(words.items, key = { "word:" + it.word.slug }) { entry ->
        ListItem(
            headlineContent = { Text(entry.word.lemma, style = MaterialTheme.typography.titleLarge) },
            supportingContent = {
                Column {
                    entry.word.shortDefinition?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    entry.note?.let { Text("“$it”", fontStyle = FontStyle.Italic) }
                    Text(
                        "Added by ${if (entry.addedBy.userId == viewModel.myUserId) "you" else entry.addedBy.displayName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            trailingContent = if (viewModel.canRemove(entry)) {
                { IconButton(onClick = { viewModel.removeWord(entry) }) { Icon(Icons.Filled.Close, contentDescription = "Remove ${entry.word.lemma}") } }
            } else {
                null
            },
            modifier = Modifier.clickable { onOpenWord(entry.word.slug) }.padding(horizontal = 8.dp),
        )
        HorizontalDivider(Modifier.padding(horizontal = 24.dp))
    }
    when {
        words.loading -> item { Spinner() }
        !words.endReached -> item {
            LaunchedEffect(words.items.size) { viewModel.loadMoreWords() }
            Spinner()
        }
    }
}

private fun LazyListScope.leaderboardTab(state: GroupState, viewModel: GroupViewModel) {
    item {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7 to "This week", 30 to "30 days").forEach { (days, label) ->
                FilterChip(selected = state.leaderboardDays == days, onClick = { viewModel.setLeaderboardDays(days) }, label = { Text(label) })
            }
        }
    }
    when (val board = state.leaderboard) {
        Load.Loading -> item { Spinner() }
        is Load.Failed -> item {
            ListItem(
                headlineContent = { Text(board.message) },
                trailingContent = { TextButton(onClick = viewModel::retryLeaderboard) { Text("Retry") } },
            )
        }
        is Load.Ready -> {
            items(board.value.rows, key = { "rank:" + it.userId }) { row ->
                ListItem(
                    leadingContent = { RankBadge(row.rank, highlight = row.isMe) },
                    headlineContent = {
                        Text(
                            if (row.isMe) "${row.displayName} (you)" else row.displayName,
                            fontWeight = if (row.isMe) FontWeight.Bold else null,
                        )
                    },
                    supportingContent = {
                        Text("${plural(row.reviews, "answer")} · ${row.streakDays}-day streak · ${row.wordsMastered} mastered")
                    },
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            item {
                EmptyText("Ranked by practice answers, then streak. A word counts as mastered once its next review is three weeks or more away.")
            }
        }
    }
}

private fun LazyListScope.membersTab(group: GroupDetailDto, viewModel: GroupViewModel) {
    items(group.members, key = { "member:" + it.userId }) { member ->
        val isMe = member.userId == viewModel.myUserId
        ListItem(
            headlineContent = { Text(if (isMe) "${member.displayName} (you)" else member.displayName) },
            supportingContent = { Text(if (member.role == GroupRole.OWNER) "Owner" else "Member") },
            trailingContent = if (group.isOwner && !isMe) {
                { TextButton(onClick = { viewModel.ask(Confirm.RemoveMember(member)) }) { Text("Remove") } }
            } else {
                null
            },
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun RankBadge(rank: Int, highlight: Boolean) {
    Box(
        Modifier.size(36.dp).background(
            if (highlight) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
            CircleShape,
        ),
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
private fun Spinner() {
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun EmptyText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
    )
}

@Composable
private fun AddWordDialog(form: AddWordState, viewModel: GroupViewModel) {
    AlertDialog(
        onDismissRequest = { if (!form.busy) viewModel.closeAddWord() },
        title = { Text("Add a word") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = form.query,
                    onValueChange = viewModel::searchWords,
                    label = { Text("Word") },
                    singleLine = true,
                )
                // Matches while no word is picked yet
                Column(Modifier.heightIn(max = 240.dp)) {
                    form.results.forEach { word ->
                        Text(
                            buildString {
                                append(word.lemma)
                                word.shortDefinition?.let { append(" · ").append(it) }
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().clickable { viewModel.pickWord(word) }.padding(vertical = 10.dp),
                        )
                    }
                }
                if (form.picked != null) {
                    OutlinedTextField(
                        value = form.note,
                        onValueChange = viewModel::editNote,
                        label = { Text("Note (optional)") },
                        supportingText = { Text("Why it's worth knowing, or where you saw it") },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::addPickedWord, enabled = form.picked != null && !form.busy) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = viewModel::closeAddWord, enabled = !form.busy) { Text("Cancel") } },
    )
}

@Composable
private fun ConfirmDialog(confirm: Confirm, viewModel: GroupViewModel) {
    val (title, text, action) = when (confirm) {
        Confirm.Leave -> Triple("Leave this group?", "You can join again with the invite code. If you're the owner, the longest-standing member takes over.", "Leave")
        Confirm.Delete -> Triple("Delete this group?", "This removes it for everyone, with its word list. It can't be undone.", "Delete")
        Confirm.NewCode -> Triple("Replace the invite code?", "The current code stops working. Members already in the group stay.", "Replace")
        is Confirm.RemoveMember -> Triple("Remove ${confirm.member.displayName}?", "They can rejoin only with a valid invite code.", "Remove")
    }
    AlertDialog(
        onDismissRequest = { viewModel.ask(null) },
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = viewModel::confirmed) { Text(action, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { viewModel.ask(null) }) { Text("Cancel") } },
    )
}
