package app.vohcarb

import app.vohcarb.auth.AuthApi
import app.vohcarb.catalog.CatalogApi
import app.vohcarb.core.api.ApiClient
import app.vohcarb.core.session.SessionStore
import app.vohcarb.practice.PracticeApi
import app.vohcarb.progress.ProgressApi
import app.vohcarb.user.UserApi
import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient

/** Everything the screens need, created once per app process by the platform (VohcarbApplication on Android). */
class AppContainer(
    settings: Settings,
    defaultServerUrl: String,
    httpClient: HttpClient = ApiClient.defaultHttpClient(),
) {
    val sessions = SessionStore(settings, defaultServerUrl)
    private val api = ApiClient(sessions, httpClient)

    val auth = AuthApi(api, sessions)
    val catalog = CatalogApi(api)
    val progress = ProgressApi(api)
    val practice = PracticeApi(api)
    val user = UserApi(api)
}
