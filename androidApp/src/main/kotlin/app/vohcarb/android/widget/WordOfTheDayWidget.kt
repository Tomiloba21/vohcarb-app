package app.vohcarb.android.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.vohcarb.android.MainActivity
import app.vohcarb.android.VohcarbApplication
import app.vohcarb.catalog.WordDetailDto
import app.vohcarb.core.ui.Looks
import app.vohcarb.core.ui.ThemeSettings
import app.vohcarb.core.ui.WordFont
import androidx.glance.unit.ColorProvider

/** Home-screen widget: today's word (the same for everyone) and its first meaning. Tapping opens the word. */
class WordOfTheDayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as VohcarbApplication).container
        // From the phone's cache when it's today's; otherwise fetched (no sign-in needed)
        val word = container.words.wordOfTheDay(container.catalog)
        val colors = WidgetColors.of(container.themes.theme.value)
        provideContent { Content(word, colors) }
    }

    @Composable
    private fun Content(word: WordDetailDto?, colors: WidgetColors) {
        val (paper, ink, muted, accent) = colors
        val open = if (word != null) {
            actionStartActivity<MainActivity>(actionParametersOf(SLUG to word.slug))
        } else {
            actionStartActivity<MainActivity>()
        }
        Column(
            GlanceModifier.fillMaxSize().background(paper).cornerRadius(24.dp).padding(16.dp).clickable(open),
        ) {
            Text("Word of the day", style = TextStyle(color = accent, fontSize = 12.sp, fontWeight = FontWeight.Medium))
            Spacer(GlanceModifier.height(4.dp))
            if (word == null) {
                Text("Open Vohcarb to get today's word.", style = TextStyle(color = ink, fontSize = 15.sp))
                return@Column
            }
            Text(word.lemma, style = TextStyle(color = ink, fontSize = 28.sp, fontFamily = colors.font), maxLines = 1)
            val sense = word.senses.firstOrNull()
            val details = listOfNotNull(word.pronunciations.firstOrNull()?.ipa?.let { "/$it/" }, sense?.partOfSpeech).joinToString("  ·  ")
            if (details.isNotEmpty()) Text(details, style = TextStyle(color = muted, fontSize = 12.sp), maxLines = 1)
            if (sense != null) {
                Spacer(GlanceModifier.height(6.dp))
                Text(
                    sense.definition,
                    style = TextStyle(color = ink, fontSize = 15.sp, fontFamily = colors.font),
                    maxLines = 3,
                )
            }
        }
    }

    companion object {
        /** The word to open, passed to MainActivity as an intent extra of this name. */
        val SLUG = ActionParameters.Key<String>("slug")
    }
}

/** The widget in the app's theme: "Match my phone" switches with dark mode, other themes keep their colours. */
private data class WidgetColors(
    val paper: ColorProvider,
    val ink: ColorProvider,
    val muted: ColorProvider,
    val accent: ColorProvider,
    val font: FontFamily,
) {
    companion object {
        fun of(theme: ThemeSettings): WidgetColors {
            val font = when (theme.font) {
                WordFont.SERIF -> FontFamily.Serif
                WordFont.SANS -> FontFamily.SansSerif
                WordFont.MONO -> FontFamily.Monospace
                WordFont.HANDWRITING -> FontFamily.Cursive
            }
            if (theme.themeId == Looks.SYSTEM) {
                val day = Looks.Paper.palette
                val night = Looks.Night.palette
                return WidgetColors(
                    ColorProvider(day.paper, night.paper), ColorProvider(day.ink, night.ink),
                    ColorProvider(day.muted, night.muted), ColorProvider(day.highlight, night.highlight), font,
                )
            }
            // A photo can't go on a widget; it uses the photo theme's own colours.
            val p = theme.look(systemDark = false).palette
            return WidgetColors(fixed(p.paper), fixed(p.ink), fixed(p.muted), fixed(p.highlight), font)
        }

        private fun fixed(color: Color) = ColorProvider(color, color)
    }
}

class WordOfTheDayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WordOfTheDayWidget()
}
