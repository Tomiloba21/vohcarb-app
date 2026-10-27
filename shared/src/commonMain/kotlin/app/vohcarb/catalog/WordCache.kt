package app.vohcarb.catalog

import app.vohcarb.core.api.ApiClient
import app.vohcarb.core.isoInstant
import app.vohcarb.core.nowMillis
import app.vohcarb.db.VohcarbDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/** Words kept on the phone, so they can be shown offline. */
class WordCache(private val db: VohcarbDatabase) {

    private val queries get() = db.vohcarbQueries

    suspend fun detail(slug: String): WordDetailDto? = withContext(Dispatchers.IO) {
        queries.wordDetail(slug).executeAsOneOrNull()?.detail?.let(::decode)
    }

    suspend fun save(word: WordDetailDto) = withContext(Dispatchers.IO) { saveBlocking(word) }

    /** For callers already inside a database transaction. */
    fun saveBlocking(word: WordDetailDto) = db.transaction {
        val summary = word.toSummary()
        val json = ApiClient.json.encodeToString(WordDetailDto.serializer(), word)
        queries.insertWordIfMissing(
            summary.slug, summary.lemma, summary.difficulty?.toLong(), summary.shortDefinition,
            summary.partsOfSpeech.joinToString(","), json,
        )
        queries.updateWordSummary(
            summary.lemma, summary.difficulty?.toLong(), summary.shortDefinition,
            summary.partsOfSpeech.joinToString(","), summary.slug,
        )
        queries.updateWordDetail(json, summary.slug)
    }

    /** Today's word of the day (UTC date, as the server picks it), fetched once and then read from here. */
    suspend fun wordOfTheDay(api: CatalogApi): WordDetailDto? {
        val today = isoInstant(nowMillis()).take(10)
        val cached = withContext(Dispatchers.IO) { queries.meta(WORD_OF_THE_DAY_KEY).executeAsOneOrNull() }
        if (cached?.substringBefore('|') == today) detail(cached.substringAfter('|'))?.let { return it }
        return try {
            val fresh = api.wordOfTheDay()
            save(fresh.word)
            withContext(Dispatchers.IO) { queries.setMeta(WORD_OF_THE_DAY_KEY, "${fresh.date}|${fresh.word.slug}") }
            fresh.word
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null // offline: the feed simply starts without it
        }
    }

    companion object {
        private const val WORD_OF_THE_DAY_KEY = "word_of_the_day"

        fun decode(json: String): WordDetailDto = ApiClient.json.decodeFromString(WordDetailDto.serializer(), json)
    }
}

fun WordDetailDto.toSummary() = WordSummaryDto(
    slug = slug,
    lemma = lemma,
    difficulty = difficulty,
    partsOfSpeech = senses.map { it.partOfSpeech }.distinct(),
    shortDefinition = senses.firstOrNull()?.definition,
)
