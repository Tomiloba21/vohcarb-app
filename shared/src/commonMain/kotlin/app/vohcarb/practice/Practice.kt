package app.vohcarb.practice

import app.vohcarb.catalog.WordDetailDto
import app.vohcarb.core.api.ApiClient
import app.vohcarb.practice.srs.Grade
import kotlinx.serialization.Serializable

enum class QuestionType {
    /** prompt is the word; options are definitions. */
    MEANING_OF_WORD,

    /** prompt is a definition (with the word blanked out); options are words. */
    WORD_FOR_MEANING,
}

@Serializable
data class QuizQuestion(val type: QuestionType, val prompt: String, val options: List<String>, val correctIndex: Int)

@Serializable
data class CardDto(
    val word: String,
    val repetitions: Int,
    val easeFactor: Double,
    val intervalDays: Int,
    val lapses: Int,
    val dueAt: String,
    val lastReviewedAt: String,
)

@Serializable
data class PracticeItem(
    val word: WordDetailDto,
    /** Null for a saved word that has never been reviewed. */
    val card: CardDto? = null,
    /** Null only if the catalog had no other words to use as wrong answers. */
    val question: QuizQuestion? = null,
)

@Serializable
data class PracticeSession(val reviewsDue: Long, val newWords: Long, val items: List<PracticeItem>)

@Serializable
data class ReviewInput(val word: String, val grade: Grade, val reviewedAt: String)

@Serializable
data class ReviewsRequest(val reviews: List<ReviewInput>)

@Serializable
data class ReviewsResponse(
    val applied: Int,
    val ignored: Int,
    val unknownWords: List<String> = emptyList(),
    val cards: List<CardDto> = emptyList(),
)

@Serializable
data class PracticeStats(
    val reviewsDue: Long,
    val newWords: Long,
    val totalCards: Long,
    val reviewedToday: Int,
    val streakDays: Int,
)

class PracticeApi(private val api: ApiClient) {
    /** Due reviews first, then new words; at most 50. */
    suspend fun session(size: Int = 10): PracticeSession = api.get("/api/v1/me/practice/session", "size" to size)

    /** Re-sending the same answer is harmless, so failed uploads can simply be retried. */
    suspend fun review(reviews: List<ReviewInput>): ReviewsResponse =
        api.post("/api/v1/me/practice/reviews", ReviewsRequest(reviews))

    suspend fun stats(): PracticeStats = api.get("/api/v1/me/practice/stats")
}
