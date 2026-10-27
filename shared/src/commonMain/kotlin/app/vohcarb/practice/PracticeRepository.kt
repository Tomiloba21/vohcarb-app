package app.vohcarb.practice

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneNotNull
import app.vohcarb.catalog.WordCache
import app.vohcarb.core.MINUTE_MILLIS
import app.vohcarb.core.api.ApiClient
import app.vohcarb.core.isoInstant
import app.vohcarb.core.nowMillis
import app.vohcarb.core.parseInstantMillis
import app.vohcarb.db.VohcarbDatabase
import app.vohcarb.practice.srs.CardState
import app.vohcarb.practice.srs.Grade
import app.vohcarb.practice.srs.Scheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** Counts for the practice screen, worked out on the phone; streak and today's answers come from the last sync. */
data class LocalStats(
    val reviewsDue: Long,
    val newWords: Long,
    val totalCards: Long,
    val reviewedToday: Int,
    val streakDays: Int,
)

/**
 * Practice without a connection: the deck is the user's saved, not-known words (as on the server), cards are
 * scheduled with the same [Scheduler] the server uses, and answers queue up for upload.
 */
class PracticeRepository(
    private val db: VohcarbDatabase,
    private val words: WordCache,
    private val onLocalChange: () -> Unit,
) {

    private val queries get() = db.vohcarbQueries

    /** Due reviews first (most overdue first), then new words (most recently saved first). */
    suspend fun session(size: Int): List<PracticeItem> = withContext(Dispatchers.IO) {
        val now = nowMillis()
        val due = queries.dueSlugs(now, size.toLong()).executeAsList()
        val new = queries.newSlugs((size - due.size).toLong()).executeAsList()
        (due + new).mapNotNull { slug ->
            val word = words.detail(slug) ?: return@mapNotNull null // details not fetched yet: next time
            PracticeItem(word, card(slug), question(slug))
        }
    }

    suspend fun grade(slug: String, grade: Grade) {
        withContext(Dispatchers.IO) {
            db.transaction {
                val now = nowMillis()
                val current = queries.card(slug).executeAsOneOrNull()
                val state = current?.let { CardState(it.repetitions.toInt(), it.ease_factor, it.interval_days.toInt(), it.lapses.toInt()) }
                    ?: CardState.NEW
                val schedule = Scheduler.review(state, grade)
                queries.setCard(
                    slug,
                    schedule.state.repetitions.toLong(),
                    schedule.state.easeFactor,
                    schedule.state.intervalDays.toLong(),
                    schedule.state.lapses.toLong(),
                    now + schedule.dueInMinutes * MINUTE_MILLIS,
                    now,
                )
                queries.removeCardElsewhere(slug)
                queries.addPendingReview(slug, grade.name, now)
            }
        }
        onLocalChange()
    }

    suspend fun stats(): LocalStats = withContext(Dispatchers.IO) {
        val counts = queries.deckCounts(nowMillis()).executeAsOne()
        val server = serverStats()
        // Answers given since the server's numbers were fetched aren't in them yet.
        val unsentToday = server?.let { queries.countPendingReviewsSince(it.fetchedAt).executeAsOne().toInt() } ?: 0
        LocalStats(
            reviewsDue = counts.due,
            newWords = counts.fresh,
            totalCards = counts.total,
            reviewedToday = (server?.stats?.reviewedToday ?: 0) + unsentToday,
            streakDays = server?.stats?.streakDays ?: 0,
        )
    }

    fun pendingReviewCount(): Flow<Long> = queries.countPendingReviews().asFlow().mapToOneNotNull(Dispatchers.IO)

    // Used by the sync engine

    /** Stores cards and questions from a server session; [complete] = it listed every due or new word. */
    fun storeSession(session: PracticeSession, complete: Boolean) = db.transaction {
        session.items.forEach { item ->
            words.saveBlocking(item.word)
            item.question?.let { queries.setQuestion(item.word.slug, ApiClient.json.encodeToString(QuizQuestion.serializer(), it)) }
            item.card?.let(::storeCardIfNoPending)
        }
        if (complete) {
            // Saved words the server didn't list and we have no card for have a card that isn't due yet.
            queries.clearCardElsewhere()
            queries.markCardsElsewhere(session.items.map { it.word.slug })
        }
    }

    fun pendingReviews(limit: Int) = queries.pendingReviews(limit.toLong()).executeAsList()

    /** After an upload: forget the sent answers and take the server's cards (the same schedule, worked out there). */
    fun reviewsUploaded(upToId: Long, cards: List<CardDto>) = db.transaction {
        queries.deletePendingReviewsUpTo(upToId)
        cards.forEach(::storeCardIfNoPending)
    }

    private fun storeCardIfNoPending(card: CardDto) {
        // A newer answer is still waiting to be sent; the local card already includes it.
        if (queries.countPendingReviewsFor(card.word).executeAsOne() > 0) return
        queries.setCard(
            card.word, card.repetitions.toLong(), card.easeFactor, card.intervalDays.toLong(), card.lapses.toLong(),
            parseInstantMillis(card.dueAt), parseInstantMillis(card.lastReviewedAt),
        )
        queries.removeCardElsewhere(card.word)
    }

    fun storeServerStats(stats: PracticeStats) =
        queries.setMeta(STATS_KEY, ApiClient.json.encodeToString(CachedStats.serializer(), CachedStats(stats, nowMillis())))

    private fun serverStats(): CachedStats? = queries.meta(STATS_KEY).executeAsOneOrNull()
        ?.let { runCatching { ApiClient.json.decodeFromString(CachedStats.serializer(), it) }.getOrNull() }

    private fun card(slug: String) = queries.card(slug).executeAsOneOrNull()?.let {
        CardDto(slug, it.repetitions.toInt(), it.ease_factor, it.interval_days.toInt(), it.lapses.toInt(), isoInstant(it.due_at), isoInstant(it.last_reviewed_at))
    }

    private fun question(slug: String) = queries.question(slug).executeAsOneOrNull()
        ?.let { runCatching { ApiClient.json.decodeFromString(QuizQuestion.serializer(), it) }.getOrNull() }

    @kotlinx.serialization.Serializable
    private data class CachedStats(val stats: PracticeStats, val fetchedAt: Long)

    private companion object {
        const val STATS_KEY = "practice_stats"
    }
}
