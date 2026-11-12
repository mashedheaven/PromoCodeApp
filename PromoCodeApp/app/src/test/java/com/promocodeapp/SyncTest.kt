@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.promocodeapp

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.promocodeapp.data.local.AppDatabase
import com.promocodeapp.data.remote.ApiJson
import com.promocodeapp.data.remote.AuthApi
import com.promocodeapp.data.remote.RestApi
import com.promocodeapp.data.remote.Session
import com.promocodeapp.data.remote.SupabaseConfig
import com.promocodeapp.data.remote.SupabaseHttp
import com.promocodeapp.data.remote.TokenAuthenticator
import com.promocodeapp.data.remote.TokenRefresher
import com.promocodeapp.data.repository.AuthRepository
import com.promocodeapp.data.repository.AuthResult
import com.promocodeapp.data.repository.CouponRepository
import com.promocodeapp.data.repository.PlaceRepository
import com.promocodeapp.data.settings.SettingsStore
import com.promocodeapp.sync.SyncEngine
import com.promocodeapp.sync.SyncOutcome
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A stand-in for Supabase that follows the contract in supabase/schema.sql:
 * upserts keep the row with the newer `updated_at`, every accepted write gets
 * an increasing `server_updated_at`, and reads filter on it.
 */
private class FakeSupabase : Dispatcher() {
    val tables = mutableMapOf<String, MutableMap<String, JsonObject>>()
    val requests = mutableListOf<RecordedRequest>()
    var validToken = "access-1"
    var refreshCount = 0
    private var tick = 0

    private fun stamp() = "2026-03-10T12:00:00.%06d+00:00".format(++tick)

    @Synchronized
    override fun dispatch(request: RecordedRequest): MockResponse {
        requests += request
        val url = request.requestUrl!!
        val path = url.encodedPath
        if (request.getHeader("apikey") != "anon-key") return MockResponse().setResponseCode(401).setBody("""{"message":"No API key"}""")

        if (path == "/auth/v1/token") {
            val body = ApiJson.parseToJsonElement(request.body.readUtf8()).jsonObject
            return when (url.queryParameter("grant_type")) {
                "refresh_token" -> {
                    if (body["refresh_token"]?.jsonPrimitive?.content != "refresh-1") {
                        return MockResponse().setResponseCode(400).setBody("""{"error_description":"Invalid Refresh Token"}""")
                    }
                    refreshCount++
                    validToken = "access-2"
                    json("""{"access_token":"access-2","refresh_token":"refresh-1","expires_in":3600,"user":{"id":"user-1","email":"a@b.co"}}""")
                }
                else -> if (body["password"]?.jsonPrimitive?.content == "secret1") {
                    json("""{"access_token":"$validToken","refresh_token":"refresh-1","expires_in":3600,"user":{"id":"user-1","email":"a@b.co"}}""")
                } else {
                    MockResponse().setResponseCode(400).setBody("""{"error_description":"Invalid login credentials"}""")
                }
            }
        }
        if (path == "/auth/v1/signup") return json("""{"id":"user-2","email":"new@b.co"}""")
        if (path == "/auth/v1/logout") return MockResponse().setResponseCode(204)

        if (request.getHeader("Authorization") != "Bearer $validToken") {
            return MockResponse().setResponseCode(401).setBody("""{"message":"JWT expired"}""")
        }
        val table = tables.getOrPut(path.removePrefix("/rest/v1/")) { mutableMapOf() }
        return when (request.method) {
            "POST" -> {
                check(request.getHeader("Prefer")!!.contains("resolution=merge-duplicates"))
                ApiJson.parseToJsonElement(request.body.readUtf8()).jsonArrayOrSingle().forEach { element ->
                    val row = element.jsonObject
                    check("server_updated_at" !in row) { "clients must not send server_updated_at" }
                    val id = (row["id"] ?: row["token"])!!.jsonPrimitive.content
                    val existing = table[id]
                    val incoming = row["updated_at"]?.jsonPrimitive?.long ?: 0
                    if (existing == null || incoming >= existing["updated_at"]!!.jsonPrimitive.long) {
                        table[id] = JsonObject(row + ("server_updated_at" to JsonPrimitive(stamp())))
                    }
                }
                MockResponse().setResponseCode(201)
            }
            else -> {
                val after = url.queryParameter("server_updated_at")?.removePrefix("gt.")
                val limit = url.queryParameter("limit")!!.toInt()
                val rows = table.values
                    .filter { after == null || it["server_updated_at"]!!.jsonPrimitive.content > after }
                    .sortedBy { it["server_updated_at"]!!.jsonPrimitive.content }
                    .take(limit)
                json(JsonArray(rows).toString())
            }
        }
    }

    private fun kotlinx.serialization.json.JsonElement.jsonArrayOrSingle() = if (this is JsonArray) jsonArray else JsonArray(listOf(this))
    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
}

@RunWith(AndroidJUnit4::class)
class SyncTest {
    private val server = MockWebServer()
    private val backend = FakeSupabase()
    private val clock = TestClock()
    private val scope = TestScope(UnconfinedTestDispatcher())
    private val session = Session("access-1", "refresh-1", Long.MAX_VALUE, "user-1", "a@b.co")

    /** One simulated phone. */
    private inner class Device(signedIn: Boolean = true) {
        val db: AppDatabase = inMemoryDb()
        val settings: SettingsStore = settingsStore(scope.backgroundScope)
        val sessions = FakeSessionStore(if (signedIn) session else null)
        // MockWebServer speaks plain HTTP; production config insists on HTTPS.
        val config = object {
            val value = SupabaseConfig("https://example.supabase.co", "anon-key")
        }.value
        private val base = SupabaseHttp.baseClient(config, sessions, debug = false)
        private val rewrite = okhttp3.Interceptor { chain ->
            val url = chain.request().url.newBuilder().scheme("http").host(server.hostName).port(server.port).build()
            chain.proceed(chain.request().newBuilder().url(url).build())
        }
        val authApi: AuthApi = SupabaseHttp.retrofit(config, base.newBuilder().addInterceptor(rewrite).build()).create(AuthApi::class.java)
        val restApi: RestApi = SupabaseHttp.retrofit(
            config,
            base.newBuilder().addInterceptor(rewrite).authenticator(TokenAuthenticator(TokenRefresher(authApi, sessions, clock))).build(),
        ).create(RestApi::class.java)
        val engine = SyncEngine(config, sessions, restApi, db, settings, clock)
        val coupons = CouponRepository(db, clock) {}
        val places = PlaceRepository(db, clock) {}
        val auth = AuthRepository(config, authApi, sessions, db, settings, clock) {}
    }

    @Before
    fun setUp() {
        server.dispatcher = backend
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `local changes are uploaded and flagged clean`() = scope.runTest {
        val phone = Device()
        phone.places.save(place(id = "p1"))
        phone.coupons.save(coupon(id = "c1", placeIds = listOf("p1"), expiresOn = clock.today.plusDays(5)))

        assertThat(phone.engine.sync()).isEqualTo(SyncOutcome.SYNCED)

        val remote = backend.tables["coupons"]!!["c1"]!!
        assertThat(remote["merchant"]!!.jsonPrimitive.content).isEqualTo("Target")
        assertThat(remote["expires_on"]!!.jsonPrimitive.content).isEqualTo(clock.today.plusDays(5).toString())
        assertThat(remote["place_ids"]!!.jsonArray.map { it.jsonPrimitive.content }).containsExactly("p1")
        assertThat(remote["notes"]).isEqualTo(JsonNull) // nulls are sent so fields can be cleared
        assertThat("user_id" in remote).isFalse()       // ownership comes from the JWT, not the client
        assertThat(backend.tables["places"]!!.keys).containsExactly("p1")
        assertThat(phone.db.couponDao().get("c1")!!.dirty).isFalse()
        assertThat(phone.settings.lastSyncAt.first()).isEqualTo(clock.millis())

        // Nothing changed, so a second run uploads nothing.
        val before = backend.requests.count { it.method == "POST" }
        phone.engine.sync()
        assertThat(backend.requests.count { it.method == "POST" }).isEqualTo(before)
    }

    @Test
    fun `a second device receives data and later edits flow both ways`() = scope.runTest {
        val phone = Device()
        val tablet = Device()
        phone.places.save(place(id = "p1", name = "Target Downtown"))
        phone.coupons.save(coupon(id = "c1", placeIds = listOf("p1")))
        phone.engine.sync()

        tablet.engine.sync()
        assertThat(tablet.coupons.get("c1")!!.placeIds).containsExactly("p1")
        assertThat(tablet.places.get("p1")!!.name).isEqualTo("Target Downtown")
        assertThat(tablet.db.couponDao().get("c1")!!.dirty).isFalse()

        clock.advanceMillis(1000)
        tablet.coupons.setUsed("c1", true)
        tablet.engine.sync()
        phone.engine.sync()
        assertThat(phone.coupons.get("c1")!!.isUsed).isTrue()
    }

    @Test
    fun `deletions propagate as tombstones and are then purged`() = scope.runTest {
        val phone = Device()
        val tablet = Device()
        phone.coupons.save(coupon(id = "c1"))
        phone.engine.sync()
        tablet.engine.sync()

        clock.advanceMillis(1000)
        phone.coupons.delete("c1")
        phone.engine.sync()
        assertThat(phone.db.couponDao().get("c1")).isNull() // tombstone no longer needed locally

        tablet.engine.sync()
        assertThat(tablet.coupons.observeAll().first()).isEmpty()
        assertThat(tablet.db.couponDao().get("c1")).isNull()
    }

    @Test
    fun `the most recent edit wins a conflict on both devices`() = scope.runTest {
        val phone = Device()
        val tablet = Device()
        phone.coupons.save(coupon(id = "c1", code = "ORIGINAL"))
        phone.engine.sync()
        tablet.engine.sync()

        // Both edit offline; the tablet's edit is the later one.
        clock.advanceMillis(1000)
        phone.coupons.save(phone.coupons.get("c1")!!.copy(code = "PHONE"))
        clock.advanceMillis(1000)
        tablet.coupons.save(tablet.coupons.get("c1")!!.copy(code = "TABLET"))

        tablet.engine.sync()
        phone.engine.sync() // the older edit must not overwrite the newer one
        assertThat(backend.tables["coupons"]!!["c1"]!!["code"]!!.jsonPrimitive.content).isEqualTo("TABLET")
        assertThat(phone.coupons.get("c1")!!.code).isEqualTo("TABLET")

        tablet.engine.sync()
        assertThat(tablet.coupons.get("c1")!!.code).isEqualTo("TABLET")
    }

    @Test
    fun `a newer unsynced local edit survives pulling an older remote version`() = scope.runTest {
        val phone = Device()
        val tablet = Device()
        phone.coupons.save(coupon(id = "c1", code = "ORIGINAL"))
        phone.engine.sync()
        tablet.engine.sync()

        clock.advanceMillis(1000)
        tablet.coupons.save(tablet.coupons.get("c1")!!.copy(code = "TABLET"))
        tablet.engine.sync()
        clock.advanceMillis(1000)
        phone.coupons.save(phone.coupons.get("c1")!!.copy(code = "PHONE"))

        phone.engine.sync()
        assertThat(phone.coupons.get("c1")!!.code).isEqualTo("PHONE")
        assertThat(backend.tables["coupons"]!!["c1"]!!["code"]!!.jsonPrimitive.content).isEqualTo("PHONE")
    }

    @Test
    fun `device-only reminder state is not lost when a row is pulled`() = scope.runTest {
        val phone = Device()
        val tablet = Device()
        val expires = clock.today.plusDays(1)
        phone.coupons.save(coupon(id = "c1", expiresOn = expires))
        phone.engine.sync()
        phone.db.couponDao().setReminderSentFor("c1", expires)

        tablet.engine.sync()
        clock.advanceMillis(1000)
        tablet.coupons.setFavorite("c1", true)
        tablet.engine.sync()
        phone.engine.sync()

        val row = phone.db.couponDao().get("c1")!!
        assertThat(row.isFavorite).isTrue()
        assertThat(row.reminderSentFor).isEqualTo(expires)
    }

    @Test
    fun `sync is skipped when signed out`() = scope.runTest {
        val phone = Device(signedIn = false)
        phone.coupons.save(coupon(id = "c1"))
        assertThat(phone.engine.sync()).isEqualTo(SyncOutcome.SKIPPED)
        assertThat(backend.requests).isEmpty()
        assertThat(phone.db.couponDao().get("c1")!!.dirty).isTrue()
    }

    @Test
    fun `an expired access token is refreshed once and the request retried`() = scope.runTest {
        val phone = Device()
        backend.validToken = "access-0" // the server no longer accepts access-1
        phone.coupons.save(coupon(id = "c1"))

        // The fake hands out "access-2" on refresh and accepts it from then on.
        assertThat(phone.engine.sync()).isEqualTo(SyncOutcome.SYNCED)
        assertThat(backend.refreshCount).isEqualTo(1)
        assertThat(phone.sessions.session.value!!.accessToken).isEqualTo("access-2")
        assertThat(backend.tables["coupons"]!!.keys).containsExactly("c1")
    }

    @Test
    fun `a rejected refresh token signs the user out instead of looping`() = scope.runTest {
        val phone = Device()
        phone.sessions.set(session.copy(refreshToken = "revoked"))
        backend.validToken = "access-0"
        phone.coupons.save(coupon(id = "c1"))

        val failure = runCatching { phone.engine.sync() }.exceptionOrNull()
        assertThat(failure).isInstanceOf(retrofit2.HttpException::class.java)
        assertThat(phone.sessions.session.value).isNull()
        assertThat(phone.db.couponDao().get("c1")!!.dirty).isTrue() // nothing lost
    }

    @Test
    fun `signing in uploads data created before the account existed`() = scope.runTest {
        val phone = Device(signedIn = false)
        phone.coupons.save(coupon(id = "c1"))
        phone.db.couponDao().clearDirty("c1", phone.db.couponDao().get("c1")!!.updatedAt)

        assertThat(phone.auth.signIn("a@b.co", "secret1")).isEqualTo(AuthResult.SignedIn)
        assertThat(phone.sessions.session.value!!.userId).isEqualTo("user-1")
        assertThat(phone.db.couponDao().get("c1")!!.dirty).isTrue()
        phone.engine.sync()
        assertThat(backend.tables["coupons"]!!.keys).containsExactly("c1")
    }

    @Test
    fun `sign in failures and unconfirmed sign ups are reported clearly`() = scope.runTest {
        val phone = Device(signedIn = false)
        assertThat(phone.auth.signIn("a@b.co", "wrong-password")).isEqualTo(AuthResult.Failed("Invalid login credentials"))
        assertThat(phone.auth.signUp("new@b.co", "secret1")).isEqualTo(AuthResult.ConfirmationRequired)
        assertThat(phone.sessions.session.value).isNull()
    }

    @Test
    fun `switching accounts does not leak the previous user's data`() = scope.runTest {
        val phone = Device(signedIn = false)
        phone.settings.setLastAccountId("someone-else")
        phone.coupons.save(coupon(id = "theirs"))

        phone.auth.signIn("a@b.co", "secret1")
        phone.engine.sync()
        assertThat(phone.coupons.observeAll().first()).isEmpty()
        assertThat(backend.tables["coupons"].orEmpty()).isEmpty()
    }

    @Test
    fun `signing out keeps local data`() = scope.runTest {
        val phone = Device()
        phone.coupons.save(coupon(id = "c1"))
        phone.auth.signOut()
        assertThat(phone.sessions.session.value).isNull()
        assertThat(phone.coupons.get("c1")).isNotNull()
    }
}
