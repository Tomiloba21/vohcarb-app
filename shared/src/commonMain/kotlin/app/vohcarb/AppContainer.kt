package app.vohcarb

import app.cash.sqldelight.db.SqlDriver
import app.vohcarb.auth.AuthApi
import app.vohcarb.catalog.CatalogApi
import app.vohcarb.catalog.WordCache
import app.vohcarb.core.api.ApiClient
import app.vohcarb.core.session.SessionStore
import app.vohcarb.core.ui.ThemeStore
import app.vohcarb.db.VohcarbDatabase
import app.vohcarb.feed.FeedQueue
import app.vohcarb.games.GamesApi
import app.vohcarb.groups.GroupsApi
import app.vohcarb.live.LiveApi
import app.vohcarb.live.LiveConnection
import app.vohcarb.practice.PracticeApi
import app.vohcarb.practice.PracticeRepository
import app.vohcarb.progress.ProgressApi
import app.vohcarb.progress.ProgressRepository
import app.vohcarb.sync.SyncEngine
import app.vohcarb.user.UserApi
import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Everything the screens need, created once per app process by the platform (VohcarbApplication on Android). */
class AppContainer(
    settings: Settings,
    defaultServerUrl: String,
    driver: SqlDriver,
    httpClient: HttpClient = ApiClient.defaultHttpClient(),
) {
    /** Lives as long as the app; background syncs run here. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val sessions = SessionStore(settings, defaultServerUrl)
    val themes = ThemeStore(settings)
    private val api = ApiClient(sessions, httpClient)
    private val db = VohcarbDatabase(driver)

    val auth = AuthApi(api, sessions)
    val catalog = CatalogApi(api)
    val user = UserApi(api)
    val groups = GroupsApi(api)
    val games = GamesApi(api)
    val live = LiveApi(api)

    /** A socket for one live room (the room screen runs it while it's open). */
    fun liveConnection(roomId: String) = LiveConnection(api, roomId)
    private val progressApi = ProgressApi(api)
    private val practiceApi = PracticeApi(api)

    // On the phone first; the sync engine exchanges changes with the server.
    val words = WordCache(db)
    val progress = ProgressRepository(db) { sync.requestPush() }
    val feed = FeedQueue(db, words)
    val practice = PracticeRepository(db, words) { sync.requestPush() }
    val sync: SyncEngine = SyncEngine(db, progressApi, practiceApi, catalog, words, feed, practice, scope)

    init {
        sessions.onSignOut { sync.clear() }
    }
}
