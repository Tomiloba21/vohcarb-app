package app.vohcarb.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.vohcarb.catalog.CatalogApi
import app.vohcarb.catalog.CategoryDto
import app.vohcarb.catalog.levelName
import app.vohcarb.core.api.userMessage
import app.vohcarb.core.deviceTimeZone
import app.vohcarb.core.session.Session
import app.vohcarb.core.session.SessionStore
import app.vohcarb.core.ui.Load
import app.vohcarb.core.ui.LoadContent
import app.vohcarb.user.PreferencesDto
import app.vohcarb.user.UserApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class SettingsData(
    val saved: PreferencesDto,
    val draft: PreferencesDto,
    val categories: List<CategoryDto>,
    val saving: Boolean = false,
) {
    val changed get() = saved != draft
}

class SettingsViewModel(
    private val user: UserApi,
    private val catalog: CatalogApi,
    private val sessions: SessionStore,
) : ViewModel() {

    private val _state = MutableStateFlow<Load<SettingsData>>(Load.Loading)
    val state = _state.asStateFlow()
    val session = sessions.session
    val serverUrl get() = sessions.serverUrl

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()

    init {
        load()
    }

    fun load() {
        _state.value = Load.Loading
        viewModelScope.launch {
            _state.value = try {
                val categories = async { catalog.categories() }
                val prefs = user.preferences()
                Load.Ready(SettingsData(prefs, prefs, categories.await()))
            } catch (e: Exception) {
                Load.Failed(e.userMessage)
            }
        }
    }

    fun edit(change: PreferencesDto.() -> PreferencesDto) = updateData { it.copy(draft = it.draft.change()) }

    fun save() {
        val data = (_state.value as? Load.Ready)?.value ?: return
        updateData { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                // The phone's time zone always wins, so reminders follow the user when they travel.
                val saved = user.updatePreferences(data.draft.copy(timezone = deviceTimeZone()))
                updateData { it.copy(saved = saved, draft = saved, saving = false) }
                _messages.tryEmit("Saved")
            } catch (e: Exception) {
                updateData { it.copy(saving = false) }
                _messages.tryEmit(e.userMessage)
            }
        }
    }

    fun signOut() = sessions.signOut()

    private fun updateData(change: (SettingsData) -> SettingsData) = _state.update {
        if (it is Load.Ready) Load.Ready(change(it.value)) else it
    }
}

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, snackbar: SnackbarHostState, onOpenCredits: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    LoadContent(state, onRetry = viewModel::load) { data ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Settings", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(horizontal = 24.dp))
            Preferences(data, viewModel)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Account(session, viewModel.serverUrl, onSignOut = viewModel::signOut)
            ListItem(
                headlineContent = { Text("Credits and licences") },
                supportingContent = { Text("Open English WordNet, FrequencyWords") },
                modifier = Modifier.padding(horizontal = 8.dp),
                trailingContent = { TextButton(onClick = onOpenCredits) { Text("View") } },
            )
        }
    }
}

@Composable
private fun Preferences(data: SettingsData, viewModel: SettingsViewModel) {
    val prefs = data.draft
    var addingTime by remember { mutableStateOf(false) }

    Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Word level")
        Text(
            if (prefs.minDifficulty == prefs.maxDifficulty) levelName(prefs.minDifficulty).orEmpty()
            else "${levelName(prefs.minDifficulty)} to ${levelName(prefs.maxDifficulty)}",
            style = MaterialTheme.typography.bodyLarge,
        )
        RangeSlider(
            value = prefs.minDifficulty.toFloat()..prefs.maxDifficulty.toFloat(),
            onValueChange = { range ->
                viewModel.edit { copy(minDifficulty = range.start.roundToInt(), maxDifficulty = range.endInclusive.roundToInt()) }
            },
            valueRange = 1f..5f,
            steps = 3,
        )

        SectionTitle("Words per day")
        Text("${prefs.wordsPerDay}", style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = prefs.wordsPerDay.toFloat(),
            onValueChange = { viewModel.edit { copy(wordsPerDay = it.roundToInt()) } },
            valueRange = 1f..50f,
        )

        SectionTitle("Topics")
        Text(
            if (prefs.categories.isEmpty()) "All topics. Pick some to narrow your feed." else "${prefs.categories.size} picked",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            data.categories.forEach { category ->
                val on = category.slug in prefs.categories
                FilterChip(
                    selected = on,
                    onClick = {
                        viewModel.edit { copy(categories = if (on) categories - category.slug else categories + category.slug) }
                    },
                    label = { Text(category.name) },
                )
            }
        }

        SectionTitle("Reminders")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
            prefs.reminderTimes.sorted().forEach { time ->
                InputChip(
                    selected = false,
                    onClick = { viewModel.edit { copy(reminderTimes = reminderTimes - time) } },
                    label = { Text(time) },
                    trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Remove $time") },
                )
            }
            if (prefs.reminderTimes.size < 5) {
                TextButton(onClick = { addingTime = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("Add time")
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = viewModel::save, enabled = data.changed && !data.saving) { Text(if (data.saving) "Saving…" else "Save") }
        }
    }

    if (addingTime) {
        val picker = rememberTimePickerState(initialHour = 8, initialMinute = 0, is24Hour = true)
        AlertDialog(
            onDismissRequest = { addingTime = false },
            title = { Text("Remind me at") },
            text = { TimePicker(picker) },
            confirmButton = {
                TextButton(onClick = {
                    val time = "${picker.hour.toString().padStart(2, '0')}:${picker.minute.toString().padStart(2, '0')}"
                    viewModel.edit { copy(reminderTimes = (reminderTimes + time).distinct()) }
                    addingTime = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { addingTime = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Account(session: Session?, serverUrl: String, onSignOut: () -> Unit) {
    ListItem(
        overlineContent = { Text("Signed in as") },
        headlineContent = { Text(session?.displayName.orEmpty()) },
        supportingContent = { Text(listOfNotNull(session?.email, serverUrl).joinToString("\n")) },
        trailingContent = { TextButton(onClick = onSignOut) { Text("Sign out") } },
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp),
    )
}
