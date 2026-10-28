package app.vohcarb.games

import app.vohcarb.catalog.CategoryRef
import app.vohcarb.core.api.ApiClient
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable

// Daily word-game challenges, mirroring the backend's game/GameDtos. Online only: the server times plays and
// keeps the answers.

enum class Game { NAME_PLACE_ANIMAL_THING, CATEGORY_SPRINT, COMPLETE_THE_WORD }

enum class HintLevel { EASY, MEDIUM, HARD }

enum class PlayStatus { NOT_STARTED, IN_PROGRESS, DONE }

enum class Verdict { VALID, INVALID, UNLISTED }

@Serializable
data class DailyChallengeDto(
    /** e.g. NAME_PLACE_ANIMAL_THING, COMPLETE_THE_WORD_EASY: used in URLs. */
    val challenge: String,
    val game: Game,
    val hint: HintLevel? = null,
    val title: String,
    val description: String,
    val timeLimitSeconds: Int,
    val status: PlayStatus,
    val score: Int? = null,
    /** Your play, once started. */
    val playId: Long? = null,
)

@Serializable
data class DailyOverviewDto(val date: String, val groupId: Long? = null, val challenges: List<DailyChallengeDto>)

@Serializable
data class HintDto(val index: Int, val definition: String, val partOfSpeech: String, val length: Int, val mask: String)

@Serializable
data class PlayDto(
    val playId: Long,
    val challenge: String,
    val date: String,
    val startedAt: String,
    val deadline: String,
    val letters: List<String>? = null,
    val category: CategoryRef? = null,
    val words: List<HintDto>? = null,
)

@Serializable
data class NpatRoundAnswers(val name: String? = null, val place: String? = null, val animal: String? = null, val thing: String? = null)

@Serializable
data class CompleteAnswer(val index: Int, val answer: String, val elapsedMs: Int? = null)

@Serializable
data class SubmitRequest(
    val rounds: List<NpatRoundAnswers>? = null,
    val words: List<String>? = null,
    val answers: List<CompleteAnswer>? = null,
)

@Serializable
data class AnswerResultDto(
    val id: Long,
    val item: Int,
    val category: String? = null,
    val answer: String,
    val verdict: Verdict,
    val stands: Boolean,
    val points: Int,
    val correctAnswer: String? = null,
)

@Serializable
data class ResultDto(
    val playId: Long,
    val challenge: String,
    val date: String,
    val score: Int,
    val late: Boolean,
    val answers: List<AnswerResultDto> = emptyList(),
)

@Serializable
data class GameLeaderboardRow(val rank: Int, val userId: Long, val displayName: String, val score: Int, val isMe: Boolean)

@Serializable
data class GameLeaderboardDto(
    val challenge: String,
    val date: String,
    val groupId: Long? = null,
    val players: Int,
    val rows: List<GameLeaderboardRow> = emptyList(),
)

@Serializable
data class ReviewItemDto(
    val answerId: Long,
    val playerName: String,
    val letter: String,
    val category: String,
    val answer: String,
    val accepts: Int,
    val rejects: Int,
    val myVote: Boolean? = null,
)

@Serializable
private data class RevealRequest(val index: Int)

@Serializable
private data class VoteRequest(val accepted: Boolean)

class GamesApi(private val api: ApiClient) {

    /** [groupId]: the group's own challenges; null for everyone's. */
    suspend fun daily(groupId: Long?): DailyOverviewDto = api.get("/api/v1/games/daily", "group" to groupId)

    /** Starts the challenge, or resumes it if already started today (the clock keeps running). */
    suspend fun start(challenge: String, groupId: Long?): PlayDto =
        api.postEmpty("/api/v1/games/daily/$challenge/start" + groupQuery(groupId))

    suspend fun reveal(playId: Long, index: Int): HintDto = api.post("/api/v1/games/plays/$playId/reveal", RevealRequest(index))

    suspend fun submit(playId: Long, request: SubmitRequest): ResultDto = api.post("/api/v1/games/plays/$playId/submit", request)

    suspend fun result(playId: Long): ResultDto = api.get("/api/v1/games/plays/$playId")

    suspend fun leaderboard(challenge: String, groupId: Long?): GameLeaderboardDto =
        api.get("/api/v1/games/daily/$challenge/leaderboard", "group" to groupId)

    suspend fun review(groupId: Long): List<ReviewItemDto> = api.get("/api/v1/games/daily/review", "group" to groupId)

    suspend fun vote(answerId: Long, accepted: Boolean) {
        api.sendJson(HttpMethod.Put, "/api/v1/games/answers/$answerId/vote", VoteRequest(accepted))
    }

    private fun groupQuery(groupId: Long?) = groupId?.let { "?group=$it" }.orEmpty()
}
