package app.vohcarb.sync

import app.vohcarb.catalog.CatalogApi
import app.vohcarb.catalog.WordCache
import app.vohcarb.core.api.ApiException
import app.vohcarb.core.api.userMessage
import app.vohcarb.core.isoInstant
import app.vohcarb.core.nowMillis
import app.vohcarb.core.parseInstantMillis
import app.vohcarb.db.VohcarbDatabase
import app.vohcarb.feed.FeedQueue
import app.vohcarb.practice.PracticeApi
import app.vohcarb.practice.PracticeRepository
import app.vohcarb.practice.ReviewInput
import app.vohcarb.progress.ProgressApi
import app.vohcarb.progress.ProgressRepository
import app.vohcarb.progress.SyncChange
import app.vohcarb.progress.SyncRequest
import app.vohcarb.progress.long
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class SyncStatus(
    val running: Boolean = false,
    /** Epoch millis of the last sync that finished without errors. */
    val lastSyncedAt: Long? = null,
    /** Why the last sync stopped, e.g. offline. */
    val error: String? = null,
)

/**
 * Keeps the phone and the server in step. Each run:
 * 1. pushes flag changes made here and pulls changes from other devices (`/me/sync`, revision counter,
 *    the later change wins on the server);
 * 2. uploads practice answers given here;
 * 3. fetches details of words the user has but the phone doesn't (e.g. saved on another device);
 * 4. tops up the feed queue;
 * 5. refreshes practice cards, quiz questions and stats.
 * Anything that fails is retried on the next run. Full runs happen on app start/resume and on demand; after
 * local changes only steps 1 and 2 run, a few seconds later.
 */
class SyncEngine(
    private val db: VohcarbDatabase,
    private val progressApi: ProgressApi,
    private val practiceApi: PracticeApi,
    private val catalogApi: CatalogApi,
    private val words: WordCache,
    private val feed: FeedQueue,
    private val practice: PracticeRepository,
    private val scope: CoroutineScope,
) {

    private val queries get() = db.vohcarbQueries
    private val mutex = Mutex()
    private var scheduled: Job? = null
    private var scheduledFull = false

    private val _status = MutableStateFlow(SyncStatus(lastSyncedAt = queries.meta(LAST_SYNC_KEY).executeAsOneOrNull()?.toLongOrNull()))
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    /** Flag changes and answers still waiting to be sent. */
    fun pendingCount(progress: ProgressRepository) =
        combine(progress.pendingCount(), practice.pendingReviewCount()) { flags, reviews -> flags + reviews }

    /** Sends local changes after [delayMillis]; calls in the meantime are merged into one run. */
    fun requestPush(delayMillis: Long = DEBOUNCE_MILLIS) = schedule(delayMillis, full = false)

    /** A full sync, in the background. */
    fun requestSync() = schedule(0, full = true)

    private fun schedule(delayMillis: Long, full: Boolean) {
        // A pending full run already includes a push
        if (!full && scheduled?.isActive == true && scheduledFull) return
        scheduled?.cancel()
        scheduledFull = full
        scheduled = scope.launch {
            delay(delayMillis)
            syncNow(full)
        }
    }


    /** Syncs now (after any run in progress). [full] = false only exchanges changes. Returns false on an error. */
    suspend fun syncNow(full: Boolean = true): Boolean = mutex.withLock {
        _status.update { it.copy(running = true) }
        try {
            withContext(Dispatchers.IO) {
                pushAndPullFlags()
                uploadReviews()
                if (full) {
                    fetchMissingDetails()
                    refillFeed()
                    refreshPractice()
                }
            }
            val now = nowMillis()
            queries.setMeta(LAST_SYNC_KEY, now.toString())
            _status.value = SyncStatus(lastSyncedAt = now)
            true
        } catch (e: CancellationException) {
            _status.update { it.copy(running = false) }
            throw e
        } catch (e: Exception) {
            _status.update { it.copy(running = false, error = e.userMessage) }
            false
        }
    }

    /** Sign-out: stop and forget everything stored for this user. */
    fun clear() {
        scheduled?.cancel()
        queries.clearAll()
        _status.value = SyncStatus()
    }

    private suspend fun pushAndPullFlags() {
        while (true) {
            val since = queries.meta(REVISION_KEY).executeAsOneOrNull()?.toLongOrNull() ?: 0
            val dirty = queries.dirtyFlags(ProgressApi.MAX_SYNC_CHANGES.toLong()).executeAsList()
            val response = progressApi.sync(
                SyncRequest(
                    sinceRevision = since,
                    changes = dirty.map {
                        SyncChange(it.slug, it.seen == 1L, it.liked == 1L, it.saved == 1L, it.known == 1L, isoInstant(it.changed_at))
                    },
                ),
            )
            db.transaction {
                // Sent rows are now on the server, unless they changed again meanwhile (then they stay dirty).
                dirty.forEach { queries.markClean(it.slug, it.changed_at) }
                // Words the catalog doesn't have (anymore) can never sync.
                response.unknownWords.forEach(queries::deleteFlags)
                // The server's result for every word changed since `since`, ours included, after last-write-wins.
                // A row changed here during the request keeps its local value; it's sent on the next run.
                response.changes.forEach { change ->
                    val local = queries.flags(change.word).executeAsOneOrNull()
                    if (local == null || local.dirty == 0L) {
                        queries.setFlags(
                            change.word, change.seen.long, change.liked.long, change.saved.long, change.known.long,
                            parseInstantMillis(change.changedAt), 0,
                        )
                    }
                }
                queries.setMeta(REVISION_KEY, response.revision.toString())
            }
            if (!response.hasMore && dirty.size < ProgressApi.MAX_SYNC_CHANGES) break
        }
    }

    private suspend fun uploadReviews() {
        while (true) {
            val batch = practice.pendingReviews(MAX_REVIEWS_PER_UPLOAD)
            if (batch.isEmpty()) return
            val response = practiceApi.review(batch.map { ReviewInput(it.slug, enumValueOf(it.grade), isoInstant(it.reviewed_at)) })
            practice.reviewsUploaded(upToId = batch.last().id, cards = response.cards)
        }
    }

    private suspend fun fetchMissingDetails() {
        val missing = queries.wordsMissingDetail(MAX_DETAILS_PER_SYNC.toLong()).executeAsList()
        missing.chunked(PARALLEL_FETCHES).forEach { chunk ->
            withContext(Dispatchers.IO) {
                chunk.map { slug ->
                    async {
                        try {
                            words.save(catalogApi.word(slug))
                        } catch (e: ApiException) {
                            if (!e.isNotFound) throw e
                            queries.deleteFlags(slug) // gone from the catalog: it can never be shown or synced
                        }
                    }
                }.awaitAll()
            }
        }
    }

    private suspend fun refillFeed() {
        feed.removeSeen()
        if (feed.size() >= FeedQueue.LOW_WATER) return
        feed.add(progressApi.feed(FeedQueue.REFILL))
    }

    private suspend fun refreshPractice() {
        val session = practiceApi.session(SESSION_FETCH_SIZE)
        practice.storeSession(session, complete = session.items.size < SESSION_FETCH_SIZE)
        practice.storeServerStats(practiceApi.stats())
    }

    private companion object {
        const val REVISION_KEY = "sync_revision"
        const val LAST_SYNC_KEY = "last_sync_at"
        const val DEBOUNCE_MILLIS = 5_000L
        const val MAX_REVIEWS_PER_UPLOAD = 200
        const val MAX_DETAILS_PER_SYNC = 60
        const val PARALLEL_FETCHES = 6
        const val SESSION_FETCH_SIZE = 50 // the server's maximum
    }
}
