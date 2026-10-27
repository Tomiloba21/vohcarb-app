package app.vohcarb.word

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import app.vohcarb.catalog.WordDetailDto
import app.vohcarb.catalog.levelName
import app.vohcarb.core.ui.DefinitionStyle
import app.vohcarb.progress.WordFlags

/** The word, its pronunciation and level, set large. */
@Composable
fun WordHeader(word: WordDetailDto, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(word.lemma, style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onBackground)
        val ipa = word.pronunciations.firstOrNull()?.ipa
        val details = listOfNotNull(ipa?.let { "/$it/" }, levelName(word.difficulty)).joinToString("  ·  ")
        if (details.isNotEmpty()) {
            Text(details, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Definitions grouped by part of speech, each with its examples.
 * [maxSenses] keeps the feed card short; the word screen shows them all.
 */
@Composable
fun Senses(word: WordDetailDto, modifier: Modifier = Modifier, maxSenses: Int = Int.MAX_VALUE) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        val shown = word.senses.take(maxSenses)
        shown.groupBy { it.partOfSpeech }.forEach { (partOfSpeech, senses) ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    partOfSpeech,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                )
                senses.forEachIndexed { index, sense ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (senses.size > 1) "${index + 1}. ${sense.definition}" else sense.definition,
                            style = DefinitionStyle,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        sense.examples.take(2).forEach { example ->
                            Text(
                                "“${example.sentence}”",
                                style = MaterialTheme.typography.bodyLarge,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                }
            }
        }
        val hidden = word.senses.size - shown.size
        if (hidden > 0) {
            Text(
                "+ $hidden more meaning${if (hidden > 1) "s" else ""}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
fun Categories(word: WordDetailDto, modifier: Modifier = Modifier) {
    if (word.categories.isEmpty()) return
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        word.categories.forEach { AssistChip(onClick = {}, label = { Text(it.name) }) }
    }
}

/** Like, save and "I know this" toggles. */
@Composable
fun WordActions(
    flags: WordFlags,
    onLike: (Boolean) -> Unit,
    onSave: (Boolean) -> Unit,
    onKnown: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
        FlagToggle("Like", flags.liked, Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder, onLike)
        FlagToggle("Save", flags.saved, Icons.Filled.Bookmark, Icons.Outlined.BookmarkBorder, onSave)
        FlagToggle("I know it", flags.known, Icons.Filled.CheckCircle, Icons.Outlined.CheckCircle, onKnown)
    }
}

@Composable
private fun FlagToggle(label: String, on: Boolean, onIcon: ImageVector, offIcon: ImageVector, onChange: (Boolean) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconToggleButton(checked = on, onCheckedChange = onChange) {
            Icon(
                if (on) onIcon else offIcon,
                contentDescription = label,
                tint = if (on) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
    }
}
