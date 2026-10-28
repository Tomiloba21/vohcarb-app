package app.vohcarb.groups

import app.vohcarb.catalog.PageResponse
import app.vohcarb.catalog.WordOfTheDayDto
import app.vohcarb.catalog.WordSummaryDto
import app.vohcarb.core.api.ApiClient
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable

// Friend groups, mirroring the backend's social/GroupDtos. Groups are online only: they're shared, live data.

enum class GroupRole { OWNER, MEMBER }

@Serializable
data class GroupSummaryDto(val id: Long, val name: String, val myRole: GroupRole, val memberCount: Long)

@Serializable
data class MemberDto(val userId: Long, val displayName: String, val role: GroupRole, val joinedAt: String)

@Serializable
data class GroupDetailDto(
    val id: Long,
    val name: String,
    val inviteCode: String,
    val myRole: GroupRole,
    val createdAt: String,
    val members: List<MemberDto> = emptyList(),
) {
    val isOwner get() = myRole == GroupRole.OWNER
}

@Serializable
data class MemberRef(val userId: Long, val displayName: String)

@Serializable
data class GroupWordDto(val word: WordSummaryDto, val addedBy: MemberRef, val note: String? = null, val addedAt: String)

@Serializable
data class LeaderboardRow(
    val rank: Int,
    val userId: Long,
    val displayName: String,
    val reviews: Long,
    val streakDays: Int,
    val wordsMastered: Long,
    val isMe: Boolean,
)

@Serializable
data class LeaderboardDto(val days: Int, val since: String, val rows: List<LeaderboardRow> = emptyList())

@Serializable
private data class CreateGroupRequest(val name: String)

@Serializable
private data class JoinGroupRequest(val code: String)

@Serializable
private data class AddGroupWordRequest(val word: String, val note: String? = null)

/** "DK6HGQFC" → "DK6H-GQFC", easier to read out. The server accepts either. */
fun formatInviteCode(code: String) = if (code.length == 8) "${code.take(4)}-${code.drop(4)}" else code

class GroupsApi(private val api: ApiClient) {

    suspend fun list(): List<GroupSummaryDto> = api.get("/api/v1/groups")

    suspend fun create(name: String): GroupDetailDto = api.post("/api/v1/groups", CreateGroupRequest(name.trim()))

    /** Case, spaces and dashes don't matter. A code nobody has gives a 404. */
    suspend fun join(code: String): GroupDetailDto = api.post("/api/v1/groups/join", JoinGroupRequest(code.trim()))

    suspend fun detail(id: Long): GroupDetailDto = api.get("/api/v1/groups/$id")

    suspend fun leave(id: Long) = api.send(HttpMethod.Post, "/api/v1/groups/$id/leave")

    suspend fun delete(id: Long) = api.send(HttpMethod.Delete, "/api/v1/groups/$id")

    suspend fun removeMember(id: Long, userId: Long) = api.send(HttpMethod.Delete, "/api/v1/groups/$id/members/$userId")

    suspend fun newInviteCode(id: Long): GroupDetailDto = api.postEmpty("/api/v1/groups/$id/invite-code")

    suspend fun words(id: Long, page: Int = 0, size: Int = 50): PageResponse<GroupWordDto> =
        api.get("/api/v1/groups/$id/words", "page" to page, "size" to size)

    /** Adding a word that's already on the list returns the existing entry. */
    suspend fun addWord(id: Long, slug: String, note: String?): GroupWordDto =
        api.post("/api/v1/groups/$id/words", AddGroupWordRequest(slug, note?.trim()?.ifBlank { null }))

    suspend fun removeWord(id: Long, slug: String) = api.send(HttpMethod.Delete, "/api/v1/groups/$id/words/$slug")

    suspend fun wordOfTheDay(id: Long): WordOfTheDayDto = api.get("/api/v1/groups/$id/word-of-the-day")

    suspend fun leaderboard(id: Long, days: Int): LeaderboardDto = api.get("/api/v1/groups/$id/leaderboard", "days" to days)
}
