package app.vohcarb.feed

import app.vohcarb.catalog.WordCache
import app.vohcarb.catalog.WordDetailDto
import app.vohcarb.db.VohcarbDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * New words fetched ahead of time, so the feed works offline. The sync engine tops it up;
 * words leave it once they're seen (here or on another device).
 */
class FeedQueue(private val db: VohcarbDatabase, private val words: WordCache) {

    private val queries get() = db.vohcarbQueries

    suspend fun unseen(): List<WordDetailDto> = withContext(Dispatchers.IO) {
        queries.unseenFeed().executeAsList().map { WordCache.decode(it.detail) }
    }

    suspend fun size(): Int = unseen().size

    suspend fun add(newWords: List<WordDetailDto>) = withContext(Dispatchers.IO) {
        db.transaction {
            newWords.forEach { word ->
                words.saveBlocking(word)
                queries.addFeedItem(word.slug)
            }
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) { queries.clearFeed() }

    suspend fun removeSeen() = withContext(Dispatchers.IO) { queries.removeSeenFeedItems() }

    companion object {
        /** Top the queue up when fewer than this many words are left. */
        const val LOW_WATER = 15
        const val REFILL = 30
    }
}
