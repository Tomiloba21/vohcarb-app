package app.vohcarb.core.session

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Who is signed in. The token is the backend's own JWT (30 days). */
data class Session(val accessToken: String, val userId: Long, val displayName: String, val email: String?)

/** Keeps the session and the server address on the device. */
class SessionStore(private val settings: Settings, private val defaultServerUrl: String) {

    private val signOutListeners = mutableListOf<() -> Unit>()

    private val _session = MutableStateFlow(load())
    val session: StateFlow<Session?> = _session.asStateFlow()

    var serverUrl: String
        get() = settings.getStringOrNull(SERVER_URL) ?: defaultServerUrl
        set(value) = settings.putString(SERVER_URL, value.trim().trimEnd('/'))

    fun signIn(session: Session) {
        settings.putString(TOKEN, session.accessToken)
        settings.putLong(USER_ID, session.userId)
        settings.putString(DISPLAY_NAME, session.displayName)
        session.email?.let { settings.putString(EMAIL, it) } ?: settings.remove(EMAIL)
        _session.value = session
    }

    fun signOut() {
        listOf(TOKEN, USER_ID, DISPLAY_NAME, EMAIL).forEach(settings::remove)
        signOutListeners.forEach { it() }
        _session.value = null
    }

    /** E.g. to delete the signed-out user's data from the phone. */
    fun onSignOut(listener: () -> Unit) {
        signOutListeners += listener
    }

    private fun load(): Session? {
        val token = settings.getStringOrNull(TOKEN) ?: return null
        return Session(
            accessToken = token,
            userId = settings.getLong(USER_ID, 0),
            displayName = settings.getString(DISPLAY_NAME, ""),
            email = settings.getStringOrNull(EMAIL),
        )
    }

    private companion object {
        const val SERVER_URL = "server_url"
        const val TOKEN = "access_token"
        const val USER_ID = "user_id"
        const val DISPLAY_NAME = "display_name"
        const val EMAIL = "email"
    }
}
