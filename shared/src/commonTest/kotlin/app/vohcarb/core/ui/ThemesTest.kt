package app.vohcarb.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemesTest {

    @Test
    fun matchMyPhoneFollowsDarkMode() {
        val settings = ThemeSettings(themeId = Looks.SYSTEM)
        assertEquals(Looks.Paper, settings.look(systemDark = false))
        assertEquals(Looks.Night, settings.look(systemDark = true))
    }

    @Test
    fun presetsIgnoreDarkModeAndUnknownIdsFallBackToPaper() {
        assertEquals(Looks.Ocean, ThemeSettings(themeId = "ocean").look(systemDark = false))
        assertEquals(Looks.Paper, ThemeSettings(themeId = "gone").look(systemDark = true))
    }

    @Test
    fun aPhotoThemeWithoutAPhotoFallsBackToNight() {
        assertEquals(Looks.Night, ThemeSettings(themeId = Looks.PHOTO, photoPath = null).look(false))
        val photo = ThemeSettings(themeId = Looks.PHOTO, photoPath = "/bg.jpg", photoDim = 0.4f).look(false)
        assertEquals(Backdrop.Photo("/bg.jpg", 0.4f), photo.backdrop)
    }

    @Test
    fun customColoursGetReadableText() {
        val light = Looks.custom(Color(0xFFFFF1F2), Color(0xFF9D174D))
        val dark = Looks.custom(Color(0xFF0B1026), Color(0xFFC4B5FD))
        assertFalse(light.palette.dark)
        assertTrue(light.palette.ink.luminance() < 0.2f)
        assertTrue(dark.palette.dark)
        assertTrue(dark.palette.ink.luminance() > 0.6f)
    }

    @Test
    fun everyPresetHasReadableText() {
        Looks.presets.forEach { look ->
            val contrast = (maxOf(look.palette.ink.luminance(), look.palette.paper.luminance()) + 0.05f) /
                (minOf(look.palette.ink.luminance(), look.palette.paper.luminance()) + 0.05f)
            assertTrue(contrast >= 7f, "${look.name}: contrast $contrast") // WCAG AAA for body text
        }
    }

    @Test
    fun choicesAreSavedAcrossRestarts() {
        val settings = MapSettings()
        ThemeStore(settings).update {
            copy(themeId = Looks.CUSTOM, font = WordFont.MONO, customBackground = Color(0xFF0EA5E9), photoDim = 0.7f)
        }
        val reloaded = ThemeStore(settings).theme.value
        assertEquals(Looks.CUSTOM, reloaded.themeId)
        assertEquals(WordFont.MONO, reloaded.font)
        assertEquals(Color(0xFF0EA5E9), reloaded.customBackground)
        assertEquals(0.7f, reloaded.photoDim)
    }
}
