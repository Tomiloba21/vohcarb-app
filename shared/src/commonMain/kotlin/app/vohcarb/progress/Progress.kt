package app.vohcarb.progress

import app.vohcarb.catalog.WordDetailDto
import app.vohcarb.core.api.ApiClient
import kotlinx.serialization.Serializable

/** The four flags a user can have on a word. */
data class WordFlags(
    val seen: Boolean = false,
    val liked: Boolean = false,
    val saved: Boolean = false,
    val known: Boolean = false,
)

/** A change to some flags; null fields keep their value. */
data class WordStatePatch(
    val seen: Boolean? = null,
    val liked: Boolean? = null,
    val saved: Boolean? = null,
    val known: Boolean? = null,
) {
    fun applyTo(flags: WordFlags) = WordFlags(
        seen = seen ?: flags.seen,
        liked = liked ?: flags.liked,
        saved = saved ?: flags.saved,
        known = known ?: flags.known,
    )
}

enum class WordList(val label: String) { SAVED("Saved"), LIKED("Liked"), KNOWN("Known"), SEEN("History") }

// Offline sync (POST /api/v1/me/sync), mirroring the backend's ProgressDtos.

/** A word's full flags as this phone last set them. */
@Serializable
data class SyncChange(
    val word: String,
    val seen: Boolean,
    val liked: Boolean,
    val saved: Boolean,
    val known: Boolean,
    val changedAt: String,
)

@Serializable
data class SyncRequest(val sinceRevision: Long, val changes: List<SyncChange>)

@Serializable
data class SyncedWordDto(
    val word: String,
    val seen: Boolean,
    val liked: Boolean,
    val saved: Boolean,
    val known: Boolean,
    val changedAt: String,
    val revision: Long,
)

@Serializable
data class SyncResponse(
    val revision: Long,
    val hasMore: Boolean,
    val changes: List<SyncedWordDto> = emptyList(),
    val unknownWords: List<String> = emptyList(),
)

class ProgressApi(private val api: ApiClient) {
    /** Random unseen words matching the user's preferences. */
    suspend fun feed(size: Int): List<WordDetailDto> = api.get("/api/v1/me/feed", "size" to size)

    /** Pushes local changes (at most 500) and pulls everything changed after [SyncRequest.sinceRevision]. */
    suspend fun sync(request: SyncRequest): SyncResponse = api.post("/api/v1/me/sync", request)

    companion object {
        const val MAX_SYNC_CHANGES = 500
    }
}
