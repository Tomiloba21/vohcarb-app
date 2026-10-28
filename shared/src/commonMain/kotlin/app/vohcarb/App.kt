package app.vohcarb

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Groups
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import app.vohcarb.core.ui.Backdrop
import app.vohcarb.core.ui.VohcarbTheme
import app.vohcarb.feed.FeedScreen
import app.vohcarb.feed.FeedViewModel
import app.vohcarb.games.DailyGamesViewModel
import app.vohcarb.games.GameActions
import app.vohcarb.games.GameLeaderboardScreen
import app.vohcarb.games.GameLeaderboardViewModel
import app.vohcarb.games.GamePlayScreen
import app.vohcarb.games.GamePlayViewModel
import app.vohcarb.games.GameResultScreen
import app.vohcarb.games.GameResultViewModel
import app.vohcarb.games.GameReviewScreen
import app.vohcarb.games.GameReviewViewModel
import app.vohcarb.games.challengeTitle
import app.vohcarb.groups.GroupScreen
import app.vohcarb.live.LiveLobbyScreen
import app.vohcarb.live.LiveLobbyViewModel
import app.vohcarb.live.LiveRoomScreen
import app.vohcarb.live.LiveRoomViewModel
import app.vohcarb.groups.GroupViewModel
import app.vohcarb.groups.GroupsScreen
import app.vohcarb.groups.GroupsViewModel
import app.vohcarb.practice.PracticeAndGames
import app.vohcarb.practice.PracticeViewModel
import app.vohcarb.saved.SavedScreen
import app.vohcarb.saved.SavedViewModel
import app.vohcarb.search.SearchScreen
import app.vohcarb.search.SearchViewModel
import app.vohcarb.settings.AppearanceScreen
import app.vohcarb.settings.CreditsScreen
import app.vohcarb.settings.SettingsScreen
import app.vohcarb.settings.SettingsViewModel
import app.vohcarb.word.WordScreen
import app.vohcarb.word.WordViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

// Destinations. The four tabs, then screens that open on top of them.
@Serializable object TodayRoute
@Serializable object SavedRoute
@Serializable object PracticeRoute
@Serializable object SettingsRoute
@Serializable object GroupsRoute
@Serializable data class GroupRoute(val id: Long)
@Serializable data class GamePlayRoute(val challenge: String, val groupId: Long? = null)
@Serializable data class GameResultRoute(val playId: Long, val groupId: Long? = null)
@Serializable data class GameBoardRoute(val challenge: String, val groupId: Long? = null)
@Serializable data class GameReviewRoute(val groupId: Long)
@Serializable data class LiveLobbyRoute(val groupId: Long? = null)
@Serializable data class LiveRoomRoute(val roomId: String)
@Serializable data class WordRoute(val slug: String)
@Serializable object SearchRoute
@Serializable object CreditsRoute
@Serializable object AppearanceRoute

private data class Tab(val route: Any, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(TodayRoute, "Today", Icons.Outlined.WbSunny),
    Tab(SavedRoute, "Words", Icons.Outlined.Bookmarks),
    Tab(PracticeRoute, "Practice", Icons.Outlined.School),
    Tab(GroupsRoute, "Groups", Icons.Outlined.Groups),
    Tab(SettingsRoute, "Settings", Icons.Outlined.Settings),
)

/** [wordToOpen]: a word the platform asks to show (e.g. from a home-screen widget); [onWordOpened] clears it. */
@Composable
fun App(container: AppContainer, wordToOpen: StateFlow<String?> = MutableStateFlow(null), onWordOpened: () -> Unit = {}) {
    val theme by container.themes.theme.collectAsStateWithLifecycle()
    val look = theme.look(isSystemInDarkTheme())
    VohcarbTheme(look, theme.font) {
        Box(Modifier.fillMaxSize()) {
            Backdrop(look)
            // See-through, so the backdrop shows behind every screen
            Surface(Modifier.fillMaxSize(), color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground) {
                val session by container.sessions.session.collectAsStateWithLifecycle()
                if (session == null) {
                    SignInScreen(viewModel { SignInViewModel(container.auth, container.sessions) })
                } else {
                    MainScreens(container, wordToOpen, onWordOpened)
                }
            }
        }
    }
}

@Composable
private fun MainScreens(container: AppContainer, wordToOpen: StateFlow<String?>, onWordOpened: () -> Unit) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val currentTab = tabs.firstOrNull { tab -> destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true }
    val openWord: (String) -> Unit = { nav.navigate(WordRoute(it)) }
    fun gameActions(groupId: Long?) = GameActions(
        play = { nav.navigate(GamePlayRoute(it, groupId)) },
        result = { nav.navigate(GameResultRoute(it, groupId)) },
        leaderboard = { nav.navigate(GameBoardRoute(it, groupId)) },
        review = groupId?.let { id -> { nav.navigate(GameReviewRoute(id)) } },
        live = { nav.navigate(LiveLobbyRoute(groupId)) },
    )
    // Catch up with the server whenever the app comes to the front (also right after sign-in)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { container.sync.requestSync() }
    val pendingWord by wordToOpen.collectAsStateWithLifecycle()
    LaunchedEffect(pendingWord) {
        pendingWord?.let {
            openWord(it)
            onWordOpened()
        }
    }

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
                PracticeAndGames(
                    practice = viewModel { PracticeViewModel(container.practice, container.sync) },
                    games = viewModel { DailyGamesViewModel(null, container.games) },
                    gameActions = gameActions(null),
                )
            }
            composable<GroupsRoute> {
                GroupsScreen(viewModel { GroupsViewModel(container.groups) }, onOpenGroup = { nav.navigate(GroupRoute(it)) })
            }
            composable<GroupRoute> { backStackEntry ->
                val id = backStackEntry.toRoute<GroupRoute>().id
                val me = container.sessions.session.value?.userId ?: 0
                GroupScreen(
                    viewModel { GroupViewModel(id, me, container.groups, container.catalog) },
                    games = viewModel { DailyGamesViewModel(id, container.games) },
                    gameActions = gameActions(id),
                    onBack = nav::popBackStack,
                    onOpenWord = openWord,
                )
            }
            composable<GamePlayRoute> { entry ->
                val route = entry.toRoute<GamePlayRoute>()
                GamePlayScreen(
                    viewModel { GamePlayViewModel(route.challenge, route.groupId, container.games) },
                    title = challengeTitle(route.challenge),
                    onClose = nav::popBackStack,
                    onFinished = { playId ->
                        nav.navigate(GameResultRoute(playId, route.groupId)) { popUpTo<GamePlayRoute> { inclusive = true } }
                    },
                )
            }
            composable<GameResultRoute> { entry ->
                val route = entry.toRoute<GameResultRoute>()
                GameResultScreen(
                    viewModel { GameResultViewModel(route.playId, container.games) },
                    onBack = nav::popBackStack,
                    onLeaderboard = { nav.navigate(GameBoardRoute(it, route.groupId)) },
                )
            }
            composable<GameBoardRoute> { entry ->
                val route = entry.toRoute<GameBoardRoute>()
                GameLeaderboardScreen(
                    viewModel { GameLeaderboardViewModel(route.challenge, route.groupId, container.games) },
                    challenge = route.challenge,
                    inGroup = route.groupId != null,
                    onBack = nav::popBackStack,
                )
            }
            composable<LiveLobbyRoute> { entry ->
                val route = entry.toRoute<LiveLobbyRoute>()
                LiveLobbyScreen(
                    viewModel { LiveLobbyViewModel(route.groupId, container.live) },
                    onBack = nav::popBackStack,
                    onOpenRoom = { id -> nav.navigate(LiveRoomRoute(id)) },
                )
            }
            composable<LiveRoomRoute> { entry ->
                val route = entry.toRoute<LiveRoomRoute>()
                LiveRoomScreen(
                    viewModel { LiveRoomViewModel(route.roomId, container.live, container.liveConnection(route.roomId)) },
                    onClose = nav::popBackStack,
                )
            }
            composable<GameReviewRoute> { entry ->
                val route = entry.toRoute<GameReviewRoute>()
                GameReviewScreen(viewModel { GameReviewViewModel(route.groupId, container.games) }, onBack = nav::popBackStack)
            }
            composable<SettingsRoute> {
                SettingsScreen(
                    viewModel {
                        SettingsViewModel(container.user, container.catalog, container.sessions, container.sync, container.feed, container.progress)
                    },
                    snackbar,
                    onOpenAppearance = { nav.navigate(AppearanceRoute) },
                    onOpenCredits = { nav.navigate(CreditsRoute) },
                )
            }
            composable<WordRoute> { backStackEntry ->
                val slug = backStackEntry.toRoute<WordRoute>().slug
                WordScreen(
                    viewModel { WordViewModel(slug, container.words, container.catalog, container.progress) },
                    groups = container.groups,
                    onBack = nav::popBackStack,
                )
            }
            composable<SearchRoute> {
                SearchScreen(viewModel { SearchViewModel(container.catalog) }, onBack = nav::popBackStack, onOpenWord = openWord)
            }
            composable<AppearanceRoute> {
                AppearanceScreen(container.themes, onBack = nav::popBackStack)
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
