package app.vohcarb.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.vohcarb.core.api.ApiException
import app.vohcarb.core.api.userMessage
import app.vohcarb.core.ui.Load
import app.vohcarb.core.ui.LoadContent
import app.vohcarb.core.ui.plural
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which dialog is open, and its state. */
sealed interface GroupDialog {
    data class Create(val name: String = "", val busy: Boolean = false, val error: String? = null) : GroupDialog
    data class Join(val code: String = "", val busy: Boolean = false, val error: String? = null) : GroupDialog
}

class GroupsViewModel(private val groups: GroupsApi) : ViewModel() {

    private val _state = MutableStateFlow<Load<List<GroupSummaryDto>>>(Load.Loading)
    val state = _state.asStateFlow()

    private val _dialog = MutableStateFlow<GroupDialog?>(null)
    val dialog = _dialog.asStateFlow()

    /** Emits the id of a group just created or joined, to open it. */
    private val _opened = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val opened = _opened.asSharedFlow()

    fun load() {
        if (_state.value !is Load.Ready) _state.value = Load.Loading
        viewModelScope.launch {
            _state.value = try {
                Load.Ready(groups.list())
            } catch (e: Exception) {
                Load.Failed(e.userMessage)
            }
        }
    }

    fun openDialog(dialog: GroupDialog?) {
        _dialog.value = dialog
    }

    fun editDialog(change: (GroupDialog) -> GroupDialog) = _dialog.update { it?.let(change) }

    fun submitDialog() {
        val dialog = _dialog.value ?: return
        editDialog { it.withBusy(true) }
        viewModelScope.launch {
            try {
                val group = when (dialog) {
                    is GroupDialog.Create -> groups.create(dialog.name)
                    is GroupDialog.Join -> groups.join(dialog.code)
                }
                _dialog.value = null
                load()
                _opened.tryEmit(group.id)
            } catch (e: Exception) {
                val message = if (dialog is GroupDialog.Join && (e as? ApiException)?.isNotFound == true) {
                    "No group has that code. Check it and try again."
                } else {
                    e.userMessage
                }
                editDialog { it.withBusy(false, message) }
            }
        }
    }

    private fun GroupDialog.withBusy(busy: Boolean, error: String? = null) = when (this) {
        is GroupDialog.Create -> copy(busy = busy, error = error)
        is GroupDialog.Join -> copy(busy = busy, error = error)
    }
}

@Composable
fun GroupsScreen(viewModel: GroupsViewModel, onOpenGroup: (Long) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()
    // Every time the screen is shown, so groups left or joined elsewhere are current
    LaunchedEffect(viewModel) { viewModel.load() }
    LaunchedEffect(viewModel) { viewModel.opened.collect(onOpenGroup) }

    Column(Modifier.fillMaxSize()) {
        Text("Groups", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(start = 24.dp, top = 16.dp, end = 24.dp))
        Text(
            "Learn with friends: share words, get a group word of the day, and see who's practising.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { viewModel.openDialog(GroupDialog.Create()) }, modifier = Modifier.weight(1f)) { Text("New group") }
            OutlinedButton(onClick = { viewModel.openDialog(GroupDialog.Join()) }, modifier = Modifier.weight(1f)) { Text("Join with code") }
        }
        LoadContent(state, onRetry = viewModel::load) { list ->
            if (list.isEmpty()) {
                Text(
                    "You're not in any groups yet. Start one and share its code, or join a friend's.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(list, key = { it.id }) { group ->
                        ListItem(
                            headlineContent = { Text(group.name, style = MaterialTheme.typography.titleLarge) },
                            supportingContent = {
                                val members = plural(group.memberCount, "member")
                                Text(if (group.myRole == GroupRole.OWNER) "$members · you're the owner" else members)
                            },
                            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                            modifier = Modifier.clickable { onOpenGroup(group.id) }.padding(horizontal = 8.dp),
                        )
                        HorizontalDivider(Modifier.padding(horizontal = 24.dp))
                    }
                }
            }
        }
    }

    dialog?.let { GroupDialogView(it, viewModel) }
}

@Composable
private fun GroupDialogView(dialog: GroupDialog, viewModel: GroupsViewModel) {
    val (title, label, value, busy, error) = when (dialog) {
        is GroupDialog.Create -> DialogText("New group", "Name", dialog.name, dialog.busy, dialog.error)
        is GroupDialog.Join -> DialogText("Join a group", "Invite code, e.g. DK6H-GQFC", dialog.code, dialog.busy, dialog.error)
    }
    AlertDialog(
        onDismissRequest = { if (!busy) viewModel.openDialog(null) },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { text ->
                    viewModel.editDialog {
                        when (it) {
                            is GroupDialog.Create -> it.copy(name = text.take(64), error = null)
                            is GroupDialog.Join -> it.copy(code = text.take(32), error = null)
                        }
                    }
                },
                label = { Text(label) },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(
                    capitalization = if (dialog is GroupDialog.Join) KeyboardCapitalization.Characters else KeyboardCapitalization.Sentences,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = viewModel::submitDialog, enabled = value.isNotBlank() && !busy) {
                Text(if (dialog is GroupDialog.Create) "Create" else "Join")
            }
        },
        dismissButton = { TextButton(onClick = { viewModel.openDialog(null) }, enabled = !busy) { Text("Cancel") } },
    )
}

private data class DialogText(val title: String, val label: String, val value: String, val busy: Boolean, val error: String?)
