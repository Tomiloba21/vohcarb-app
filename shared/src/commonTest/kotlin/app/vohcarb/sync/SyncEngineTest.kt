package app.vohcarb.sync

import app.vohcarb.catalog.CatalogApi
import app.vohcarb.catalog.SenseDto
import app.vohcarb.catalog.WordCache
import app.vohcarb.catalog.WordDetailDto
import app.vohcarb.core.api.ApiClient
import app.vohcarb.core.session.Session
import app.vohcarb.core.session.SessionStore
import app.vohcarb.db.VohcarbDatabase
import app.vohcarb.feed.FeedQueue
import app.vohcarb.practice.PracticeApi
import app.vohcarb.practice.PracticeItem
import app.vohcarb.practice.PracticeRepository
import app.vohcarb.practice.PracticeSession
import app.vohcarb.practice.srs.Grade
import app.vohcarb.progress.ProgressApi
import app.vohcarb.progress.ProgressRepository
import app.vohcarb.progress.WordFlags
import app.vohcarb.progress.WordStatePatch
import app.vohcarb.testDriver
import com.russhwolf.settings.MapSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncEngineTest {

    private val db = VohcarbDatabase(testDriver())
    private val queries = db.vohcarbQueries
    private val sessions = SessionStore(MapSettings(), "http://server").apply { signIn(Session("token", 1, "Ada", null)) }
    private val sent = mutableListOf<Pair<String, JsonObject>>()
    private var handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { error("no response set") }

    private val api = ApiClient(
        sessions,
        HttpClient(MockEngine { request ->
            val body = request.body.toByteArray().decodeToString()
            sent += request.url.encodedPath to (if (body.isBlank()) JsonObject(emptyMap()) else Json.parseToJsonElement(body).jsonObject)
            handler(request)
        }) {
            expectSuccess = false
            install(ContentNegotiation) { json(ApiClient.json) }
        },
    )
    private val words = WordCache(db)
    private val progress = ProgressRepository(db) {}
    private val practice = PracticeRepository(db, words) {}

    private fun engine(scope: kotlinx.coroutines.CoroutineScope) = SyncEngine(
        db, ProgressApi(api), PracticeApi(api), CatalogApi(api), words, FeedQueue(db, words), practice, scope,
    )

    private fun MockRequestHandleScope.json(body: String) =
        respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))

    private fun word(slug: String) = WordDetailDto(slug, slug, 3, senses = listOf(SenseDto("noun", "the meaning of $slug")))

    @Test
    fun pushesLocalChangesAndPullsOtherDevices() = runTest {
        progress.update("ephemeral", WordStatePatch(saved = true))
        handler = {
            json(
                """{"revision":7,"hasMore":false,"unknownWords":[],"changes":[
                {"word":"ephemeral","seen":false,"liked":false,"saved":true,"known":false,"changedAt":"2026-10-27T10:00:00Z","revision":6},
                {"word":"petrichor","seen":true,"liked":true,"saved":false,"known":false,"changedAt":"2026-10-27T09:00:00Z","revision":7}]}""",
            )
        }

        assertTrue(engine(backgroundScope).syncNow(full = false))

        val request = sent.single { it.first == "/api/v1/me/sync" }.second
        assertEquals(0, request["sinceRevision"]!!.jsonPrimitive.long)
        assertEquals("ephemeral", request["changes"]!!.jsonArray.single().jsonObject["word"]!!.jsonPrimitive.content)
        assertEquals(0, queries.countDirty().executeAsOne()) // accepted by the server
        assertEquals(WordFlags(seen = true, liked = true), progress.flags("petrichor").first())
        assertEquals("7", queries.meta("sync_revision").executeAsOne())

        // Next time only changes after revision 7 are asked for
        handler = { json("""{"revision":7,"hasMore":false,"changes":[]}""") }
        engine(backgroundScope).syncNow(full = false)
        assertEquals(7, sent.last().second["sinceRevision"]!!.jsonPrimitive.long)
    }

    @Test
    fun aChangeMadeDuringTheRequestIsKeptAndSentNextTime() = runTest {
        progress.update("ephemeral", WordStatePatch(saved = true))
        handler = {
            // The user unsaves the word while the request is in flight
            progress.update("ephemeral", WordStatePatch(saved = false))
            json(
                """{"revision":3,"hasMore":false,"changes":[
                {"word":"ephemeral","seen":false,"liked":false,"saved":true,"known":false,"changedAt":"2026-10-27T10:00:00Z","revision":3}]}""",
            )
        }

        engine(backgroundScope).syncNow(full = false)

        assertEquals(WordFlags(saved = false), progress.flags("ephemeral").first())
        assertEquals(1, queries.countDirty().executeAsOne())
    }

    @Test
    fun practiceIsGradedOfflineAndTheServerCardWinsAfterUpload() = runTest {
        words.save(word("ephemeral"))
        progress.update("ephemeral", WordStatePatch(saved = true))
        assertEquals(1, practice.stats().newWords)

        val item = practice.session(10).single()
        assertEquals(null, item.card)
        practice.grade(item.word.slug, Grade.GOOD)

        val local = queries.card("ephemeral").executeAsOne()
        assertEquals(1, local.interval_days) // same schedule as the server: GOOD on a new word = 1 day
        assertEquals(0, practice.stats().reviewsDue)
        assertEquals(1, queries.countPendingReviews().executeAsOne())

        handler = { request ->
            if (request.url.encodedPath.endsWith("/reviews")) {
                json(
                    """{"applied":1,"ignored":0,"cards":[{"word":"ephemeral","repetitions":1,"easeFactor":2.5,"intervalDays":1,
                    "lapses":0,"dueAt":"2026-10-28T10:00:00Z","lastReviewedAt":"2026-10-27T10:00:00Z"}]}""",
                )
            } else {
                json("""{"revision":1,"hasMore":false,"changes":[]}""")
            }
        }
        engine(backgroundScope).syncNow(full = false)

        val review = sent.single { it.first.endsWith("/reviews") }.second["reviews"]!!.jsonArray.single().jsonObject
        assertEquals("GOOD", review["grade"]!!.jsonPrimitive.content)
        assertEquals(0, queries.countPendingReviews().executeAsOne())
        assertEquals(1_793_181_600_000, queries.card("ephemeral").executeAsOne().due_at) // 2026-10-28T10:00Z
    }

    @Test
    fun savedWordsWithACardElsewhereAreNotTreatedAsNew() = runTest {
        listOf("due", "later").forEach {
            words.save(word(it))
            progress.update(it, WordStatePatch(saved = true))
        }

        // The server lists only "due": "later" has a card on the server that isn't due yet
        practice.storeSession(PracticeSession(reviewsDue = 0, newWords = 1, items = listOf(PracticeItem(word("due")))), complete = true)

        assertEquals(listOf("due"), practice.session(10).map { it.word.slug })
        assertEquals(1, practice.stats().newWords)
    }
}
