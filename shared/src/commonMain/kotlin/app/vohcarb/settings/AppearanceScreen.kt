package app.vohcarb.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vohcarb.core.ui.Backdrop
import app.vohcarb.core.ui.Look
import app.vohcarb.core.ui.Looks
import app.vohcarb.core.ui.ThemeStore
import app.vohcarb.core.ui.WordFont
import app.vohcarb.core.ui.photoBackgroundsSupported
import app.vohcarb.core.ui.rememberPhotoPicker

/** Themes, backgrounds and the word font. Changes apply straight away. */
@Composable
fun AppearanceScreen(themes: ThemeStore, onBack: () -> Unit) {
    val settings by themes.theme.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val pickPhoto = rememberPhotoPicker { path -> themes.update { copy(themeId = Looks.PHOTO, photoPath = path) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appearance") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionLabel("Theme")
            val tiles = listOf(
                Tile(Looks.SYSTEM, "Match my phone", settings.copy(themeId = Looks.SYSTEM).look(systemDark)),
            ) + Looks.presets.map { Tile(it.id, it.name, it) }
            ThemeGrid(tiles, settings.themeId, settings.font) { id -> themes.update { copy(themeId = id) } }

            SectionLabel("Word font")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WordFont.entries.forEach { font ->
                    FilterChip(
                        selected = settings.font == font,
                        onClick = { themes.update { copy(font = font) } },
                        label = { Text(font.label, style = TextStyle(fontFamily = font.family, fontSize = 16.sp)) },
                    )
                }
            }

            if (photoBackgroundsSupported) {
                SectionLabel("Your photo")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    settings.photoPath?.let { path ->
                        val look = Looks.photo(path, settings.photoDim)
                        PreviewTile(look, "Your photo", settings.themeId == Looks.PHOTO, settings.font, Modifier.weight(1f)) {
                            themes.update { copy(themeId = Looks.PHOTO) }
                        }
                    }
                    OutlinedButton(onClick = pickPhoto, modifier = Modifier.weight(1f)) {
                        Text(if (settings.photoPath == null) "Choose a photo" else "Choose another")
                    }
                }
                if (settings.themeId == Looks.PHOTO && settings.photoPath != null) {
                    Text("Darken the photo so words stay easy to read", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = settings.photoDim,
                        onValueChange = { v -> themes.update { copy(photoDim = v) } },
                        valueRange = 0.2f..0.85f,
                    )
                }
            }

            SectionLabel("Your colours")
            val custom = Looks.custom(settings.customBackground, settings.customAccent)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PreviewTile(custom, custom.name, settings.themeId == Looks.CUSTOM, settings.font, Modifier.weight(1f)) {
                    themes.update { copy(themeId = Looks.CUSTOM) }
                }
                Box(Modifier.weight(1f))
            }
            Text("Background", style = MaterialTheme.typography.bodyMedium)
            Swatches(settings.customBackground) { c -> themes.update { copy(themeId = Looks.CUSTOM, customBackground = c) } }
            Text("Accent", style = MaterialTheme.typography.bodyMedium)
            Swatches(settings.customAccent) { c -> themes.update { copy(themeId = Looks.CUSTOM, customAccent = c) } }
            Text(
                "Text colour is picked for you so it stays readable. The home-screen widget follows your theme too.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

private data class Tile(val id: String, val name: String, val look: Look)

@Composable
private fun ThemeGrid(tiles: List<Tile>, selected: String, font: WordFont, onPick: (String) -> Unit) {
    // Three per row
    tiles.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { tile -> PreviewTile(tile.look, tile.name, tile.id == selected, font, Modifier.weight(1f)) { onPick(tile.id) } }
            repeat(3 - row.size) { Box(Modifier.weight(1f)) }
        }
    }
}

/** A small picture of the theme: its background with a word on it. */
@Composable
private fun PreviewTile(look: Look, name: String, selected: Boolean, font: WordFont, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.8f).clip(shape)
                .border(
                    if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape,
                )
                .clickable(onClick = onClick),
        ) {
            Backdrop(look)
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Aa", style = TextStyle(fontFamily = font.family, fontSize = 26.sp, color = look.palette.ink))
                Box(Modifier.padding(top = 6.dp).size(width = 28.dp, height = 6.dp).clip(CircleShape).background(look.palette.accent))
            }
            if (selected) {
                Icon(
                    Icons.Filled.Check, contentDescription = "Selected", tint = look.palette.accent,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp),
                )
            }
        }
        Text(name, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun Swatches(selected: Color, onPick: (Color) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Looks.swatches.forEach { color ->
            Box(
                Modifier.size(36.dp).clip(CircleShape)
                    .border(if (color == selected) 3.dp else 1.dp, if (color == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .clickable { onPick(color) },
            ) {
                Box(Modifier.fillMaxSize().padding(3.dp).clip(CircleShape).background(color))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
}
