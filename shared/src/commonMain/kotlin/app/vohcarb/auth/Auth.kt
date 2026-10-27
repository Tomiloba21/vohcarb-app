package app.vohcarb.auth

import app.vohcarb.core.api.ApiClient
import app.vohcarb.core.session.Session
import app.vohcarb.core.session.SessionStore
import app.vohcarb.user.MeDto
import kotlinx.serialization.Serializable

@Serializable
data class DevLoginRequest(val email: String, val displayName: String? = null)

@Serializable
data class TokenResponse(val accessToken: String, val tokenType: String, val expiresIn: Long, val user: MeDto)

class AuthApi(private val api: ApiClient, private val sessions: SessionStore) {

    /** Dev login: only exists while the backend runs with the `dev` profile. Google Sign-In replaces it later. */
    suspend fun devLogin(email: String, displayName: String?) {
        val token: TokenResponse = api.post(
            "/api/v1/auth/dev-login",
            DevLoginRequest(email.trim(), displayName?.trim()?.ifBlank { null }),
        )
        sessions.signIn(Session(token.accessToken, token.user.id, token.user.displayName, token.user.email))
    }
}
