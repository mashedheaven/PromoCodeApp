package com.promocodeapp

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.promocodeapp.core.model.Coupon
import com.promocodeapp.core.model.Discount
import com.promocodeapp.core.model.Membership
import com.promocodeapp.core.model.Place
import com.promocodeapp.data.local.AppDatabase
import com.promocodeapp.data.remote.Session
import com.promocodeapp.data.remote.SessionStore
import com.promocodeapp.data.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

/** A clock tests can move forward. */
class TestClock(start: LocalDate = LocalDate.of(2026, 3, 10)) : Clock() {
    var now: Instant = start.atTime(12, 0).toInstant(ZoneOffset.UTC)
    override fun instant(): Instant = now
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
    fun advanceMillis(ms: Long) { now = now.plusMillis(ms) }
    fun advanceDays(days: Long) { now = now.plusSeconds(days * 86_400) }
    val today: LocalDate get() = LocalDate.now(this)
}

class FakeSessionStore(initial: Session? = null) : SessionStore {
    override val session = MutableStateFlow(initial)
    override fun set(session: Session?) { this.session.value = session }
}

fun inMemoryDb(): AppDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
        .allowMainThreadQueries().build()

fun settingsStore(scope: CoroutineScope): SettingsStore {
    val file = File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "settings-${UUID.randomUUID()}.preferences_pb")
    return SettingsStore(PreferenceDataStoreFactory.create(scope = scope) { file })
}

fun coupon(
    id: String = UUID.randomUUID().toString(),
    merchant: String = "Target",
    code: String = "SAVE20",
    discount: Discount = Discount.Percentage(20.0),
    expiresOn: LocalDate? = null,
    placeIds: List<String> = emptyList(),
    isFavorite: Boolean = false,
    usedAt: Long? = null,
    isArchived: Boolean = false,
    category: String? = null,
    createdAt: Long = 0,
) = Coupon(
    id = id, code = code, merchant = merchant, discount = discount, expiresOn = expiresOn, placeIds = placeIds,
    isFavorite = isFavorite, usedAt = usedAt, isArchived = isArchived, category = category,
    createdAt = createdAt, updatedAt = createdAt,
)

fun place(id: String = UUID.randomUUID().toString(), name: String = "Target Downtown", lat: Double = 40.0, lng: Double = -74.0, radius: Int = 150) =
    Place(id = id, name = name, latitude = lat, longitude = lng, radiusMeters = radius, createdAt = 0, updatedAt = 0)

fun membership(id: String = UUID.randomUUID().toString(), organization: String = "Gym", placeIds: List<String> = emptyList()) =
    Membership(id = id, organization = organization, placeIds = placeIds, createdAt = 0, updatedAt = 0)
