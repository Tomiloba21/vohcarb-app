package app.vohcarb

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.vohcarb.auth.SignInScreen
import app.vohcarb.auth.SignInViewModel
import app.vohcarb.core.ui.VohcarbTheme
import app.vohcarb.feed.FeedScreen
import app.vohcarb.feed.FeedViewModel
import app.vohcarb.practice.PracticeScreen
import app.vohcarb.practice.PracticeViewModel
import app.vohcarb.saved.SavedScreen
import app.vohcarb.saved.SavedViewModel
import app.vohcarb.search.SearchScreen
import app.vohcarb.search.SearchViewModel
import app.vohcarb.settings.CreditsScreen
import app.vohcarb.settings.SettingsScreen
import app.vohcarb.settings.SettingsViewModel
import app.vohcarb.word.WordScreen
import app.vohcarb.word.WordViewModel
import kotlinx.serialization.Serializable

// Destinations. The four tabs, then screens that open on top of them.
@Serializable object TodayRoute
@Serializable object SavedRoute
@Serializable object PracticeRoute
@Serializable object SettingsRoute
@Serializable data class WordRoute(val slug: String)
@Serializable object SearchRoute
@Serializable object CreditsRoute

private data class Tab(val route: Any, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(TodayRoute, "Today", Icons.Outlined.WbSunny),
    Tab(SavedRoute, "Words", Icons.Outlined.Bookmarks),
    Tab(PracticeRoute, "Practice", Icons.Outlined.School),
    Tab(SettingsRoute, "Settings", Icons.Outlined.Settings),
)

@Composable
fun App(container: AppContainer) {
    VohcarbTheme {
        Surface(Modifier.fillMaxSize()) {
            val session by container.sessions.session.collectAsStateWithLifecycle()
            if (session == null) {
                SignInScreen(viewModel { SignInViewModel(container.auth, container.sessions) })
            } else {
                MainScreens(container)
            }
        }
    }
}

@Composable
private fun MainScreens(container: AppContainer) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val currentTab = tabs.firstOrNull { tab -> destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true }
    val openWord: (String) -> Unit = { nav.navigate(WordRoute(it)) }
    // Catch up with the server whenever the app comes to the front (also right after sign-in)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { container.sync.requestSync() }

    Scaffold(
        topBar = {
            if (currentTab?.route == SavedRoute) {
                TopAppBar(
                    title = { Text("Your words") },
                    actions = {
                        IconButton(onClick = { nav.navigate(SearchRoute) }) { Icon(Icons.Filled.Search, contentDescription = "Search") }
                    },
                )
            }
        },
        bottomBar = { if (currentTab != null) BottomBar(nav, currentTab) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        // Screens with their own top bar must not add the status bar inset a second time
        NavHost(nav, startDestination = TodayRoute, modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
            composable<TodayRoute> {
                FeedScreen(viewModel { FeedViewModel(container.feed, container.progress, container.words, container.catalog, container.sync) }, openWord)
            }
            composable<SavedRoute> {
                SavedScreen(viewModel { SavedViewModel(container.progress) }, openWord)
            }
            composable<PracticeRoute> {
                PracticeScreen(viewModel { PracticeViewModel(container.practice, container.sync) })
            }
            composable<SettingsRoute> {
                SettingsScreen(
                    viewModel {
                        SettingsViewModel(container.user, container.catalog, container.sessions, container.sync, container.feed, container.progress)
                    },
                    snackbar,
                    onOpenCredits = { nav.navigate(CreditsRoute) },
                )
            }
            composable<WordRoute> { backStackEntry ->
                val slug = backStackEntry.toRoute<WordRoute>().slug
                WordScreen(viewModel { WordViewModel(slug, container.words, container.catalog, container.progress) }, onBack = nav::popBackStack)
            }
            composable<SearchRoute> {
                SearchScreen(viewModel { SearchViewModel(container.catalog) }, onBack = nav::popBackStack, onOpenWord = openWord)
            }
            composable<CreditsRoute> {
                CreditsScreen(onBack = nav::popBackStack)
            }
        }
    }
}

@Composable
private fun BottomBar(nav: NavHostController, current: Tab) {
    NavigationBar {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = tab == current,
                onClick = {
                    nav.navigate(tab.route) {
                        // Standard tab behaviour: one copy of each tab, state kept when switching
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label) },
            )
        }
    }
}
