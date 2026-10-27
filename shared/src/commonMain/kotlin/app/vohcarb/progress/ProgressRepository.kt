package app.vohcarb.progress

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneNotNull
import app.vohcarb.catalog.WordSummaryDto
import app.vohcarb.core.nowMillis
import app.vohcarb.db.User_word
import app.vohcarb.db.VohcarbDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The user's flags on words, kept on the phone. Changes are written here first (marked dirty) and
 * pushed by the sync engine; [onLocalChange] asks it to sync soon.
 */
class ProgressRepository(private val db: VohcarbDatabase, private val onLocalChange: () -> Unit) {

    private val queries get() = db.vohcarbQueries

    fun flags(slug: String): Flow<WordFlags> =
        queries.flags(slug).asFlow().map { it.executeAsOneOrNull()?.toFlags() ?: WordFlags() }

    fun flagsFor(slugs: Collection<String>): Flow<Map<String, WordFlags>> =
        queries.flagsIn(slugs).asFlow().mapToList(Dispatchers.IO).map { rows -> rows.associate { it.slug to it.toFlags() } }

    suspend fun update(slug: String, patch: WordStatePatch) {
        withContext(Dispatchers.IO) {
            db.transaction {
                val current = queries.flags(slug).executeAsOneOrNull()
                val before = current?.toFlags() ?: WordFlags()
                val after = patch.applyTo(before)
                if (after == before && current != null) return@transaction
                // Never before the last change, so last-write-wins keeps working if the clock went back.
                val at = maxOf(nowMillis(), (current?.changed_at ?: 0) + 1)
                queries.setFlags(slug, after.seen.long, after.liked.long, after.saved.long, after.known.long, at, 1)
            }
        }
        onLocalChange()
    }

    fun list(list: WordList): Flow<List<WordSummaryDto>> {
        fun row(slug: String, lemma: String?, difficulty: Long?, definition: String?, pos: String?) = WordSummaryDto(
            slug = slug,
            lemma = lemma ?: slug, // details not fetched yet
            difficulty = difficulty?.toInt(),
            partsOfSpeech = pos?.split(',')?.filter { it.isNotBlank() }.orEmpty(),
            shortDefinition = definition,
        )
        val query = when (list) {
            WordList.SAVED -> queries.listSaved(::row)
            WordList.LIKED -> queries.listLiked(::row)
            WordList.KNOWN -> queries.listKnown(::row)
            WordList.SEEN -> queries.listSeen(::row)
        }
        return query.asFlow().mapToList(Dispatchers.IO)
    }

    /** Flag changes not yet accepted by the server. */
    fun pendingCount(): Flow<Long> = queries.countDirty().asFlow().mapToOneNotNull(Dispatchers.IO)
}

fun User_word.toFlags() = WordFlags(seen == 1L, liked == 1L, saved == 1L, known == 1L)

val Boolean.long get() = if (this) 1L else 0L
