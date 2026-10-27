package app.vohcarb.progress

import app.vohcarb.catalog.PageResponse
import app.vohcarb.catalog.WordDetailDto
import app.vohcarb.catalog.WordSummaryDto
import app.vohcarb.core.api.ApiClient
import kotlinx.serialization.Serializable

/** The four flags a user can have on a word. */
data class WordFlags(
    val seen: Boolean = false,
    val liked: Boolean = false,
    val saved: Boolean = false,
    val known: Boolean = false,
)

/** Online update of some flags; null fields are left out of the JSON and keep their value. */
@Serializable
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

@Serializable
data class MyWordDto(
    val word: WordSummaryDto,
    val seen: Boolean,
    val liked: Boolean,
    val saved: Boolean,
    val known: Boolean,
    val changedAt: String? = null,
) {
    val flags get() = WordFlags(seen, liked, saved, known)
}

enum class WordList(val label: String) { SAVED("Saved"), LIKED("Liked"), KNOWN("Known"), SEEN("History") }

class ProgressApi(private val api: ApiClient) {
    /** Random unseen words matching the user's preferences; the server defaults [size] to their words per day. */
    suspend fun feed(size: Int? = null): List<WordDetailDto> = api.get("/api/v1/me/feed", "size" to size)

    suspend fun list(list: WordList, page: Int = 0, size: Int = 30): PageResponse<MyWordDto> =
        api.get("/api/v1/me/words", "list" to list.name.lowercase(), "page" to page, "size" to size)

    suspend fun state(slug: String): MyWordDto = api.get("/api/v1/me/words/$slug")

    suspend fun update(slug: String, patch: WordStatePatch): MyWordDto = api.patch("/api/v1/me/words/$slug", patch)
}
