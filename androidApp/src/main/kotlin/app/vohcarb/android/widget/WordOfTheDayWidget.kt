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

/** Home-screen widget: today's word (the same for everyone) and its first meaning. Tapping opens the word. */
class WordOfTheDayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as VohcarbApplication).container
        // From the phone's cache when it's today's; otherwise fetched (no sign-in needed)
        val word = container.words.wordOfTheDay(container.catalog)
        provideContent { Content(word) }
    }

    @Composable
    private fun Content(word: WordDetailDto?) {
        val open = if (word != null) {
            actionStartActivity<MainActivity>(actionParametersOf(SLUG to word.slug))
        } else {
            actionStartActivity<MainActivity>()
        }
        Column(
            GlanceModifier.fillMaxSize().background(Paper).cornerRadius(24.dp).padding(16.dp).clickable(open),
        ) {
            Text("Word of the day", style = TextStyle(color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Medium))
            Spacer(GlanceModifier.height(4.dp))
            if (word == null) {
                Text("Open Vohcarb to get today's word.", style = TextStyle(color = Ink, fontSize = 15.sp))
                return@Column
            }
            Text(word.lemma, style = TextStyle(color = Ink, fontSize = 28.sp, fontFamily = FontFamily.Serif), maxLines = 1)
            val sense = word.senses.firstOrNull()
            val details = listOfNotNull(word.pronunciations.firstOrNull()?.ipa?.let { "/$it/" }, sense?.partOfSpeech).joinToString("  ·  ")
            if (details.isNotEmpty()) Text(details, style = TextStyle(color = Muted, fontSize = 12.sp), maxLines = 1)
            if (sense != null) {
                Spacer(GlanceModifier.height(6.dp))
                Text(
                    sense.definition,
                    style = TextStyle(color = Ink, fontSize = 15.sp, fontFamily = FontFamily.Serif),
                    maxLines = 3,
                )
            }
        }
    }

    companion object {
        /** The word to open, passed to MainActivity as an intent extra of this name. */
        val SLUG = ActionParameters.Key<String>("slug")

        // The app's paper-and-ink colours (light, dark)
        private val Paper = ColorProvider(day = Color(0xFFFBF8F3), night = Color(0xFF201E1C))
        private val Ink = ColorProvider(day = Color(0xFF1D1B18), night = Color(0xFFEAE4DA))
        private val Muted = ColorProvider(day = Color(0xFF5B564E), night = Color(0xFFC9C2B7))
        private val Accent = ColorProvider(day = Color(0xFFB0563B), night = Color(0xFFF0A58C))
    }
}

class WordOfTheDayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WordOfTheDayWidget()
}
