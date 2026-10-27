package app.vohcarb.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp

private data class Credit(val title: String, val text: String, val url: String)

// The word data licences require this attribution (see the backend README).
private val credits = listOf(
    Credit(
        "Open English WordNet",
        "Words, definitions and examples. By the Global WordNet community, licensed CC BY 4.0.",
        "https://github.com/globalwordnet/english-wordnet",
    ),
    Credit(
        "FrequencyWords",
        "Word frequencies used for difficulty levels. By Hermit Dave, built from OpenSubtitles 2018, licensed CC BY-SA 4.0.",
        "https://github.com/hermitdave/FrequencyWords",
    ),
    Credit(
        "Vohcarb",
        "Open-source app and backend, licensed Apache 2.0.",
        "https://github.com/Tomiloba21/vohcarb-backend",
    ),
)

@Composable
fun CreditsScreen(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Credits") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            credits.forEach { credit ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(credit.title, style = MaterialTheme.typography.titleLarge)
                    Text(credit.text, style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = { uriHandler.openUri(credit.url) }) { Text(credit.url) }
                }
            }
        }
    }
}
