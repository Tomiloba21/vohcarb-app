package app.vohcarb.user

import app.vohcarb.core.api.ApiClient
import kotlinx.serialization.Serializable

@Serializable
data class MeDto(val id: Long, val email: String? = null, val displayName: String, val createdAt: String)

@Serializable
data class PreferencesDto(
    val minDifficulty: Int,
    val maxDifficulty: Int,
    val wordsPerDay: Int,
    val timezone: String,
    /** Category slugs for the feed; empty means all categories. */
    val categories: List<String> = emptyList(),
    /** Local times of day ("HH:mm") for reminders. */
    val reminderTimes: List<String> = emptyList(),
)

class UserApi(private val api: ApiClient) {
    suspend fun me(): MeDto = api.get("/api/v1/me")

    suspend fun preferences(): PreferencesDto = api.get("/api/v1/me/preferences")

    /** Replaces all preferences (PUT); the request has the same shape as the response. */
    suspend fun updatePreferences(preferences: PreferencesDto): PreferencesDto =
        api.put("/api/v1/me/preferences", preferences)
}
