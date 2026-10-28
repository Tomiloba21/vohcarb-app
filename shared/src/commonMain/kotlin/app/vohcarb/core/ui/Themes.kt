package app.vohcarb.core.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily

// Themes: a few colours, a background drawn behind every screen, and the font words are set in.

/** The handful of colours a theme is made from; the full Material colour scheme is worked out from them. */
data class Palette(
    val dark: Boolean,
    /** What the background is drawn on, and the colour of bars, cards and dialogs. */
    val paper: Color,
    /** Main text. */
    val ink: Color,
    /** Secondary text. */
    val muted: Color,
    /** Buttons and selected things. */
    val accent: Color,
    /** Labels, likes, the second colour. */
    val highlight: Color,
)

/** What's drawn behind the screens. */
sealed interface Backdrop {
    data object Plain : Backdrop
    data class Gradient(val colors: List<Color>) : Backdrop

    /** Faint specks, like paper. */
    data object Grain : Backdrop

    /** Ruled lines, like a notebook. */
    data object Lines : Backdrop

    /** A gradient with small stars. */
    data class Stars(val colors: List<Color>) : Backdrop

    /** The user's photo, darkened by [dim] (0 to 1) so text stays readable. */
    data class Photo(val path: String, val dim: Float) : Backdrop
}

data class Look(val id: String, val name: String, val palette: Palette, val backdrop: Backdrop)

enum class WordFont(val label: String, val family: FontFamily) {
    SERIF("Serif", FontFamily.Serif),
    SANS("Sans", FontFamily.SansSerif),
    MONO("Mono", FontFamily.Monospace),
    HANDWRITING("Handwriting", FontFamily.Cursive),
}

object Looks {
    const val SYSTEM = "system"
    const val CUSTOM = "custom"
    const val PHOTO = "photo"

    private val InkDark = Color(0xFF1D1B18)
    private val InkLight = Color(0xFFEAE4DA)

    val Paper = Look(
        "paper", "Paper",
        Palette(false, Color(0xFFFBF8F3), InkDark, Color(0xFF5B564E), Color(0xFF2E3A59), Color(0xFFB0563B)),
        Backdrop.Grain,
    )
    val Night = Look(
        "night", "Night",
        Palette(true, Color(0xFF151413), InkLight, Color(0xFFC9C2B7), Color(0xFFB7C3E3), Color(0xFFF0A58C)),
        Backdrop.Plain,
    )
    val Sepia = Look(
        "sepia", "Sepia",
        Palette(false, Color(0xFFF4ECD8), Color(0xFF3B2F2F), Color(0xFF6B5B4B), Color(0xFF6B4F3A), Color(0xFFA0522D)),
        Backdrop.Lines,
    )
    val Ocean = Look(
        "ocean", "Ocean",
        Palette(true, Color(0xFF0F2027), Color(0xFFE6F4F1), Color(0xFFA9C7C9), Color(0xFF8FD3F4), Color(0xFFF6C177)),
        Backdrop.Gradient(listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364))),
    )
    val Forest = Look(
        "forest", "Forest",
        Palette(true, Color(0xFF14261E), Color(0xFFE8F1E9), Color(0xFFB4C8B8), Color(0xFFA8D5BA), Color(0xFFE9C46A)),
        Backdrop.Gradient(listOf(Color(0xFF14261E), Color(0xFF1E3B2F), Color(0xFF2F5D46))),
    )
    val Rose = Look(
        "rose", "Rose",
        Palette(false, Color(0xFFFFF1F2), Color(0xFF3A1A24), Color(0xFF7A5560), Color(0xFF9D174D), Color(0xFFDB2777)),
        Backdrop.Gradient(listOf(Color(0xFFFFF1F2), Color(0xFFFCE7F3), Color(0xFFFBCFE8))),
    )
    val Midnight = Look(
        "midnight", "Midnight",
        Palette(true, Color(0xFF0B1026), Color(0xFFECE8FF), Color(0xFFB9B3D9), Color(0xFFC4B5FD), Color(0xFFF0ABFC)),
        Backdrop.Stars(listOf(Color(0xFF0B1026), Color(0xFF1B1446), Color(0xFF2B1055))),
    )
    val Sunset = Look(
        "sunset", "Sunset",
        Palette(false, Color(0xFFFFEDE0), Color(0xFF3A1D12), Color(0xFF7C4A36), Color(0xFF9A3412), Color(0xFFC2410C)),
        Backdrop.Gradient(listOf(Color(0xFFFFF4EC), Color(0xFFFFE0CC), Color(0xFFFFC3A0))),
    )
    val Mono = Look(
        "mono", "Mono",
        Palette(false, Color(0xFFFFFFFF), Color(0xFF111111), Color(0xFF555555), Color(0xFF111111), Color(0xFF555555)),
        Backdrop.Plain,
    )

    val presets = listOf(Paper, Night, Sepia, Ocean, Forest, Rose, Midnight, Sunset, Mono)

    /** Text that reads well on [background]: near-black on light colours, near-white on dark ones. */
    fun inkOn(background: Color) = if (background.luminance() > 0.4f) InkDark else InkLight

    /** A theme from the user's own background and accent colours. */
    fun custom(background: Color, accent: Color): Look {
        val ink = inkOn(background)
        val dark = background.luminance() <= 0.4f
        return Look(
            CUSTOM, "Your colours",
            Palette(dark, background, ink, lerp(ink, background, 0.35f), accent, accent),
            Backdrop.Plain,
        )
    }

    /** The user's photo behind Night's colours: light text on a darkened picture. */
    fun photo(path: String, dim: Float) = Look(PHOTO, "Your photo", Night.palette, Backdrop.Photo(path, dim))

    /** Colours offered in the custom-colour editor. */
    val swatches = listOf(
        0xFFFBF8F3, 0xFFF4ECD8, 0xFFFFF1F2, 0xFFE0F2FE, 0xFFE8F5E9, 0xFFFFFFFF,
        0xFF151413, 0xFF0F2027, 0xFF14261E, 0xFF0B1026, 0xFF2E3A59, 0xFF3B2F2F,
        0xFFB0563B, 0xFF9D174D, 0xFFDB2777, 0xFFC2410C, 0xFFE9C46A, 0xFF16A34A,
        0xFF0EA5E9, 0xFF6366F1, 0xFF8FD3F4, 0xFFC4B5FD, 0xFFF0A58C, 0xFF111111,
    ).map { Color(it) }
}

/** The Material colour scheme for a palette. The background is see-through, so the [Backdrop] shows. */
fun Palette.colorScheme(): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    fun mix(t: Float) = lerp(paper, ink, t)
    return base.copy(
        primary = accent,
        onPrimary = Looks.inkOn(accent),
        primaryContainer = lerp(paper, accent, 0.2f),
        onPrimaryContainer = ink,
        secondary = highlight,
        onSecondary = Looks.inkOn(highlight),
        secondaryContainer = lerp(paper, highlight, 0.22f),
        onSecondaryContainer = ink,
        tertiary = highlight,
        onTertiary = Looks.inkOn(highlight),
        background = Color.Transparent,
        onBackground = ink,
        surface = paper,
        onSurface = ink,
        surfaceVariant = mix(0.08f),
        onSurfaceVariant = muted,
        surfaceContainerLowest = paper,
        surfaceContainerLow = mix(0.03f),
        surfaceContainer = mix(0.05f),
        surfaceContainerHigh = mix(0.08f),
        surfaceContainerHighest = mix(0.11f),
        inverseSurface = ink,
        inverseOnSurface = paper,
        outline = mix(0.4f),
        outlineVariant = mix(0.2f),
    )
}
