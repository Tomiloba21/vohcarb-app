package app.vohcarb.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.vohcarb.core.api.userMessage
import app.vohcarb.core.ui.Load
import kotlinx.coroutines.launch

/** Shares a word with one of the user's groups, from the word screen. [onDone] gets a message to show. */
@Composable
fun AddToGroupDialog(slug: String, lemma: String, groups: GroupsApi, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<Load<List<GroupSummaryDto>>>(Load.Loading) }
    var picked by remember { mutableStateOf<GroupSummaryDto?>(null) }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        list = try {
            groups.list().also { if (it.size == 1) picked = it.single() }.let { Load.Ready(it) }
        } catch (e: Exception) {
            Load.Failed(e.userMessage)
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Add “$lemma” to a group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (val l = list) {
                    Load.Loading -> CircularProgressIndicator()
                    is Load.Failed -> Text(l.message, color = MaterialTheme.colorScheme.error)
                    is Load.Ready -> if (l.value.isEmpty()) {
                        Text("You're not in any groups yet. Create or join one in the Groups tab.")
                    } else {
                        l.value.forEach { group ->
                            Row(
                                Modifier.fillMaxWidth().selectable(selected = picked == group, onClick = { picked = group }),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = picked == group, onClick = { picked = group })
                                Text(group.name)
                            }
                        }
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it.take(280) },
                            label = { Text("Note (optional)") },
                        )
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = picked != null && !busy,
                onClick = {
                    val group = picked ?: return@TextButton
                    busy = true
                    scope.launch {
                        try {
                            groups.addWord(group.id, slug, note)
                            onDone("Added to ${group.name}")
                        } catch (e: Exception) {
                            error = e.userMessage
                            busy = false
                        }
                    }
                },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}
