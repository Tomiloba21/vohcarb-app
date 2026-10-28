package app.vohcarb.live

import app.vohcarb.catalog.CategoryRef
import app.vohcarb.core.api.ApiClient
import app.vohcarb.games.HintDto
import app.vohcarb.games.NpatRoundAnswers
import app.vohcarb.games.Verdict
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable

// Live play, mirroring the backend's game/live/LiveDtos. Rooms over REST; the match over a WebSocket (LiveConnection).

enum class RoomKind { FRIENDS, PUBLIC, QUICK }

enum class RoomPhase { LOBBY, COUNTDOWN, ROUND, VOTING, RESULTS, FINISHED }

@Serializable
data class RoomSummaryDto(
    val id: String,
    val code: String,
    val challenge: String,
    val title: String,
    val kind: RoomKind,
    val groupId: Long? = null,
    val hostName: String,
    val players: Int,
    val maxPlayers: Int,
    val phase: RoomPhase,
)

@Serializable
data class LobbyDto(
    val open: List<RoomSummaryDto> = emptyList(),
    val groups: List<RoomSummaryDto> = emptyList(),
    val mine: List<RoomSummaryDto> = emptyList(),
)

@Serializable
data class LivePlayerDto(val userId: Long, val name: String, val score: Int, val connected: Boolean, val submitted: Boolean)

@Serializable
data class LiveVoteDto(
    val key: String,
    val playerName: String,
    val category: String,
    val answer: String,
    val accepts: Int,
    val rejects: Int,
    val myVote: Boolean? = null,
    val mine: Boolean,
)

@Serializable
data class LiveAnswerDto(val category: String? = null, val answer: String, val verdict: Verdict, val stands: Boolean, val points: Int)

@Serializable
data class LiveRoundResultDto(val userId: Long, val name: String, val roundPoints: Int, val answers: List<LiveAnswerDto> = emptyList())

@Serializable
data class RoomStateDto(
    val roomId: String,
    val code: String,
    val challenge: String,
    val title: String,
    val kind: RoomKind,
    val groupId: Long? = null,
    val hostId: Long,
    val you: Long,
    val phase: RoomPhase,
    val round: Int,
    val rounds: Int,
    val endsAt: String? = null,
    val serverTime: String,
    val minPlayers: Int,
    val maxPlayers: Int,
    val players: List<LivePlayerDto> = emptyList(),
    val letter: String? = null,
    val category: CategoryRef? = null,
    val hint: HintDto? = null,
    val youSubmitted: Boolean = false,
    val votes: List<LiveVoteDto>? = null,
    val results: List<LiveRoundResultDto>? = null,
    val correctAnswer: String? = null,
) {
    val isHost get() = hostId == you
}

/** What the phone sends over the socket; `type` says which fields are used. */
@Serializable
data class ClientMessage(
    val type: String,
    val round: Int? = null,
    val answers: NpatRoundAnswers? = null,
    val words: List<String>? = null,
    val answer: String? = null,
    val key: String? = null,
    val accepted: Boolean? = null,
)

/** Server messages: a state, or an error for something this phone asked. */
@Serializable
data class ServerMessage(val type: String, val state: RoomStateDto? = null, val message: String? = null)

@Serializable
private data class CreateRoomRequest(val challenge: String, val kind: RoomKind, val groupId: Long? = null)

@Serializable
private data class JoinByCodeRequest(val code: String)

@Serializable
private data class QuickMatchRequest(val challenge: String)

class LiveApi(private val api: ApiClient) {
    suspend fun lobby(): LobbyDto = api.get("/api/v1/games/live/rooms")

    suspend fun create(challenge: String, kind: RoomKind, groupId: Long?): RoomSummaryDto =
        api.post("/api/v1/games/live/rooms", CreateRoomRequest(challenge, kind, groupId))

    suspend fun joinByCode(code: String): RoomSummaryDto = api.post("/api/v1/games/live/rooms/join", JoinByCodeRequest(code.trim()))

    suspend fun join(roomId: String): RoomSummaryDto = api.postEmpty("/api/v1/games/live/rooms/$roomId/join")

    suspend fun quick(challenge: String): RoomSummaryDto = api.post("/api/v1/games/live/quick", QuickMatchRequest(challenge))

    suspend fun leave(roomId: String) = api.send(HttpMethod.Post, "/api/v1/games/live/rooms/$roomId/leave")
}

/** "ABC123" → "ABC 123", easier to read out. */
fun formatRoomCode(code: String) = if (code.length == 6) "${code.take(3)} ${code.drop(3)}" else code
