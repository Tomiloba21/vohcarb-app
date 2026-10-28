package app.vohcarb.android

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import androidx.glance.appwidget.updateAll
import app.vohcarb.App
import app.vohcarb.android.widget.WordOfTheDayWidget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** A word to open, e.g. tapped on the home-screen widget. */
    private val wordToOpen = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) readWord(intent)
        val container = (application as VohcarbApplication).container
        setContent {
            // Status-bar icons follow the chosen theme, not the phone's dark mode
            val theme by container.themes.theme.collectAsState()
            val systemDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            val dark = theme.look(systemDark).palette.dark
            DisposableEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            App(container, wordToOpen, onWordOpened = { wordToOpen.value = null })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readWord(intent)
    }

    override fun onResume() {
        super.onResume()
        // Keep the widget current (e.g. after midnight UTC, or once the app has fetched today's word)
        lifecycleScope.launch { WordOfTheDayWidget().updateAll(this@MainActivity) }
    }

    private fun readWord(intent: Intent?) {
        intent?.getStringExtra(WordOfTheDayWidget.SLUG.name)?.let { wordToOpen.value = it }
    }
}
