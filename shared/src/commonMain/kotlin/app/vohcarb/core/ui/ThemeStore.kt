package app.vohcarb.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The user's appearance choices. A device preference: kept when signing out. */
data class ThemeSettings(
    /** A preset's id, or [Looks.SYSTEM], [Looks.CUSTOM], [Looks.PHOTO]. */
    val themeId: String = Looks.SYSTEM,
    val font: WordFont = WordFont.SERIF,
    val customBackground: Color = Color(0xFF2E3A59),
    val customAccent: Color = Color(0xFFF0A58C),
    /** A copy of the chosen photo in the app's own files. */
    val photoPath: String? = null,
    val photoDim: Float = 0.55f,
) {
    /** The theme to draw; "Match my phone" follows the system's dark mode. */
    fun look(systemDark: Boolean): Look = when (themeId) {
        Looks.SYSTEM -> if (systemDark) Looks.Night else Looks.Paper
        Looks.CUSTOM -> Looks.custom(customBackground, customAccent)
        Looks.PHOTO -> photoPath?.let { Looks.photo(it, photoDim) } ?: Looks.Night
        else -> Looks.presets.firstOrNull { it.id == themeId } ?: Looks.Paper
    }
}

class ThemeStore(private val settings: Settings) {

    private val _theme = MutableStateFlow(load())
    val theme: StateFlow<ThemeSettings> = _theme.asStateFlow()

    fun update(change: ThemeSettings.() -> ThemeSettings) {
        val next = _theme.value.change()
        settings.putString(THEME, next.themeId)
        settings.putString(FONT, next.font.name)
        settings.putInt(CUSTOM_BACKGROUND, next.customBackground.toArgb())
        settings.putInt(CUSTOM_ACCENT, next.customAccent.toArgb())
        next.photoPath?.let { settings.putString(PHOTO, it) } ?: settings.remove(PHOTO)
        settings.putFloat(PHOTO_DIM, next.photoDim)
        _theme.value = next
    }

    private fun load(): ThemeSettings {
        val defaults = ThemeSettings()
        return ThemeSettings(
            themeId = settings.getString(THEME, defaults.themeId),
            font = WordFont.entries.firstOrNull { it.name == settings.getStringOrNull(FONT) } ?: defaults.font,
            customBackground = settings.getIntOrNull(CUSTOM_BACKGROUND)?.let { Color(it) } ?: defaults.customBackground,
            customAccent = settings.getIntOrNull(CUSTOM_ACCENT)?.let { Color(it) } ?: defaults.customAccent,
            photoPath = settings.getStringOrNull(PHOTO),
            photoDim = settings.getFloat(PHOTO_DIM, defaults.photoDim),
        )
    }

    private companion object {
        const val THEME = "theme"
        const val FONT = "theme_font"
        const val CUSTOM_BACKGROUND = "theme_custom_background"
        const val CUSTOM_ACCENT = "theme_custom_accent"
        const val PHOTO = "theme_photo"
        const val PHOTO_DIM = "theme_photo_dim"
    }
}
