@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.promocodeapp

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.promocodeapp.data.local.AppDatabase
import com.promocodeapp.data.repository.CouponRepository
import com.promocodeapp.data.repository.MembershipRepository
import com.promocodeapp.data.repository.PlaceRepository
import com.promocodeapp.data.settings.SettingsStore
import com.promocodeapp.location.ProximityAlerter
import com.promocodeapp.notifications.Notifier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class DataTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = TestClock()
    private val scope = TestScope(UnconfinedTestDispatcher())
    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsStore
    private lateinit var coupons: CouponRepository
    private lateinit var places: PlaceRepository
    private lateinit var memberships: MembershipRepository
    private lateinit var alerter: ProximityAlerter
    private var changes = 0

    private val notifications get() = shadowOf(context.getSystemService(NotificationManager::class.java))

    @Before
    fun setUp() {
        db = inMemoryDb()
        settings = settingsStore(scope.backgroundScope)
        coupons = CouponRepository(db, clock) { changes++ }
        places = PlaceRepository(db, clock) { changes++ }
        memberships = MembershipRepository(db, clock) { changes++ }
        val notifier = Notifier(context).also { it.createChannels() }
        alerter = ProximityAlerter(db, places, coupons, memberships, settings, notifier, clock)
    }

    @After
    fun tearDown() = db.close()

    // --- repositories ---

    @Test
    fun `saving marks rows dirty and notifies the sync scheduler`() = scope.runTest {
        coupons.save(coupon(id = "c1"))
        val row = db.couponDao().get("c1")!!
        assertThat(row.dirty).isTrue()
        assertThat(row.updatedAt).isEqualTo(clock.millis())
        assertThat(changes).isEqualTo(1)
        assertThat(coupons.observeAll().first().map { it.id }).containsExactly("c1")
    }

    @Test
    fun `round trip preserves every coupon field`() = scope.runTest {
        val original = coupon(id = "c1", expiresOn = clock.today.plusDays(3), placeIds = listOf("p1", "p2"), category = "Groceries")
            .copy(minPurchase = 25.0, notes = "In store only", barcode = "SAVE20", barcodeFormat = com.promocodeapp.core.model.BarcodeFormat.CODE_128,
                discount = com.promocodeapp.core.model.Discount.FixedAmount(5.0, "EUR"))
        coupons.save(original)
        assertThat(coupons.get("c1")).isEqualTo(original.copy(updatedAt = clock.millis()))
    }

    @Test
    fun `delete leaves a tombstone that is hidden from the app`() = scope.runTest {
        coupons.save(coupon(id = "c1"))
        coupons.delete("c1")
        assertThat(coupons.observeAll().first()).isEmpty()
        assertThat(coupons.get("c1")).isNull()
        val tombstone = db.couponDao().get("c1")!!
        assertThat(tombstone.deletedAt).isNotNull()
        assertThat(tombstone.dirty).isTrue()
    }

    @Test
    fun `used and favorite flags persist`() = scope.runTest {
        coupons.save(coupon(id = "c1"))
        coupons.setUsed("c1", true)
        coupons.setFavorite("c1", true)
        val saved = coupons.get("c1")!!
        assertThat(saved.isUsed).isTrue()
        assertThat(saved.isFavorite).isTrue()
        coupons.setUsed("c1", false)
        assertThat(coupons.get("c1")!!.isUsed).isFalse()
    }

    @Test
    fun `deleting a place unlinks it from coupons and memberships`() = scope.runTest {
        places.save(place(id = "p1"))
        places.save(place(id = "p2"))
        coupons.save(coupon(id = "c1", placeIds = listOf("p1", "p2")))
        memberships.save(membership(id = "m1", placeIds = listOf("p1")))

        places.delete("p1")

        assertThat(places.observeAll().first().map { it.id }).containsExactly("p2")
        assertThat(coupons.get("c1")!!.placeIds).containsExactly("p2")
        assertThat(memberships.get("m1")!!.placeIds).isEmpty()
        assertThat(db.couponDao().get("c1")!!.dirty).isTrue()
    }

    @Test
    fun `auto archive only touches coupons expired beyond the grace period`() = scope.runTest {
        coupons.save(coupon(id = "old", expiresOn = clock.today.minusDays(10)))
        coupons.save(coupon(id = "recent", expiresOn = clock.today.minusDays(2)))
        coupons.save(coupon(id = "live", expiresOn = clock.today.plusDays(2)))
        assertThat(coupons.archiveExpired(graceDays = 7)).isEqualTo(1)
        assertThat(coupons.get("old")!!.isArchived).isTrue()
        assertThat(coupons.get("recent")!!.isArchived).isFalse()
        assertThat(coupons.get("live")!!.isArchived).isFalse()
    }

    @Test
    fun `editing the expiry date re-arms the reminder`() = scope.runTest {
        val c = coupon(id = "c1", expiresOn = clock.today.plusDays(1))
        coupons.save(c)
        db.couponDao().setReminderSentFor("c1", c.expiresOn)
        coupons.setFavorite("c1", true)
        assertThat(db.couponDao().get("c1")!!.reminderSentFor).isEqualTo(c.expiresOn)
        coupons.save(c.copy(expiresOn = clock.today.plusDays(9)))
        assertThat(db.couponDao().get("c1")!!.reminderSentFor).isNull()
    }

    // --- settings ---

    @Test
    fun `settings persist and are clamped to sane ranges`() = scope.runTest {
        assertThat(settings.current().nearbyAlerts).isTrue()
        settings.update { it.copy(nearbyAlerts = false, defaultRadiusMeters = 5, expiryLeadDays = 99) }
        val s = settings.current()
        assertThat(s.nearbyAlerts).isFalse()
        assertThat(s.defaultRadiusMeters).isEqualTo(100)
        assertThat(s.expiryLeadDays).isEqualTo(14)

        settings.setRegisteredGeofences(mapOf("p1" to "sig"))
        assertThat(settings.registeredGeofences()).containsExactly("p1", "sig")
    }

    // --- nearby alerts ---

    @Test
    fun `arriving at a place with a usable coupon notifies once`() = scope.runTest {
        places.save(place(id = "p1", name = "Target Downtown"))
        coupons.save(coupon(id = "c1", placeIds = listOf("p1")))
        coupons.save(coupon(id = "c2", merchant = "Target", code = "FIVE", placeIds = listOf("p1")))

        assertThat(alerter.onEnter("p1")).isTrue()
        val shown = notifications.allNotifications.single()
        assertThat(shadowOf(shown).contentTitle.toString()).isEqualTo("You're near Target Downtown")
        assertThat(shadowOf(shown).contentText.toString()).isEqualTo("You have 2 coupons to use here")
        assertThat(shown.channelId).isEqualTo(Notifier.CHANNEL_NEARBY)

        // Geofences being re-registered re-fire ENTER while still inside.
        assertThat(alerter.onEnter("p1")).isFalse()
        assertThat(notifications.allNotifications).hasSize(1)
    }

    @Test
    fun `leaving dismisses the alert and a quick return stays quiet`() = scope.runTest {
        places.save(place(id = "p1"))
        coupons.save(coupon(id = "c1", placeIds = listOf("p1")))
        alerter.onEnter("p1")

        alerter.onExit("p1")
        assertThat(notifications.allNotifications).isEmpty()

        clock.advanceMillis(10 * 60 * 1000) // back ten minutes later
        assertThat(alerter.onEnter("p1")).isFalse()

        alerter.onExit("p1")
        clock.advanceMillis(ProximityAlerter.COOLDOWN_MS)
        assertThat(alerter.onEnter("p1")).isTrue()
    }

    @Test
    fun `a lost exit event does not silence a place forever`() = scope.runTest {
        places.save(place(id = "p1"))
        coupons.save(coupon(id = "c1", placeIds = listOf("p1")))
        assertThat(alerter.onEnter("p1")).isTrue()

        // No EXIT ever arrives; a day later re-registration fires ENTER again.
        clock.advanceMillis(24 * 60 * 60 * 1000L)
        assertThat(alerter.onEnter("p1")).isFalse() // still plausibly inside (lives next door)

        // Nothing for well over a day: the old "inside" is no longer trusted.
        clock.advanceMillis(ProximityAlerter.INSIDE_STALE_MS + 1)
        assertThat(alerter.onEnter("p1")).isTrue()
    }

    @Test
    fun `no alert for expired or used coupons or when alerts are off`() = scope.runTest {
        places.save(place(id = "p1"))
        places.save(place(id = "p2"))
        coupons.save(coupon(id = "expired", placeIds = listOf("p1"), expiresOn = clock.today.minusDays(1)))
        coupons.save(coupon(id = "used", placeIds = listOf("p1"), usedAt = 1))
        assertThat(alerter.onEnter("p1")).isFalse()

        coupons.save(coupon(id = "ok", placeIds = listOf("p2")))
        settings.update { it.copy(nearbyAlerts = false) }
        assertThat(alerter.onEnter("p2")).isFalse()
        assertThat(notifications.allNotifications).isEmpty()
    }

    @Test
    fun `a membership alone is worth an alert`() = scope.runTest {
        places.save(place(id = "p1", name = "City Gym"))
        memberships.save(membership(id = "m1", organization = "City Gym", placeIds = listOf("p1")))
        assertThat(alerter.onEnter("p1")).isTrue()
        assertThat(shadowOf(notifications.allNotifications.single()).contentText.toString())
            .isEqualTo("Your City Gym membership is valid here")
    }
}
