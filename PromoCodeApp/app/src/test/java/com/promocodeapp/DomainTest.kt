package com.promocodeapp

import com.google.common.truth.Truth.assertThat
import com.promocodeapp.core.model.BarcodeFormat
import com.promocodeapp.core.model.BillingCycle
import com.promocodeapp.core.model.CouponFilter
import com.promocodeapp.core.model.CouponQuery
import com.promocodeapp.core.model.CouponSort
import com.promocodeapp.core.model.Discount
import com.promocodeapp.core.model.Expiry
import com.promocodeapp.core.model.Money
import com.promocodeapp.location.Fence
import com.promocodeapp.location.GeofencePlanner
import com.promocodeapp.location.LatLng
import com.promocodeapp.location.placesToWatch
import com.promocodeapp.notifications.ReminderPlanner
import com.promocodeapp.ui.components.BarcodeRenderer
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class DomainTest {
    private val today = LocalDate.of(2026, 3, 10)

    // --- expiry ---

    @Test
    fun `coupon stays valid through its expiry day`() {
        assertThat(Expiry.of(today, today)).isEqualTo(Expiry.Today)
        assertThat(coupon(expiresOn = today).isRedeemable(today)).isTrue()
        assertThat(Expiry.of(today.minusDays(1), today)).isEqualTo(Expiry.Expired(1))
        assertThat(coupon(expiresOn = today.minusDays(1)).isRedeemable(today)).isFalse()
        assertThat(Expiry.of(today.plusDays(3), today)).isEqualTo(Expiry.InDays(3))
        assertThat(Expiry.of(null, today)).isEqualTo(Expiry.Never)
    }

    @Test
    fun `used and archived coupons are not redeemable`() {
        assertThat(coupon(usedAt = 1).isRedeemable(today)).isFalse()
        assertThat(coupon(isArchived = true).isRedeemable(today)).isFalse()
        assertThat(coupon().isRedeemable(today)).isTrue()
    }

    @Test
    fun `discount labels`() {
        assertThat(Discount.Percentage(20.0).label(Locale.US)).isEqualTo("20% OFF")
        assertThat(Discount.Percentage(12.5).label(Locale.US)).isEqualTo("12.5% OFF")
        assertThat(Discount.FixedAmount(5.0, "USD").label(Locale.US)).isEqualTo("$5 OFF")
        assertThat(Discount.FixedAmount(5.5, "USD").label(Locale.US)).isEqualTo("$5.50 OFF")
        assertThat(coupon(code = "").headline).isEqualTo(Discount.Percentage(20.0).label())
    }

    @Test
    fun `money parsing accepts comma decimals and rejects junk`() {
        assertThat(Money.parse("12,50")).isEqualTo(12.5)
        assertThat(Money.parse(" 7 ")).isEqualTo(7.0)
        assertThat(Money.parse("abc")).isNull()
        assertThat(Money.parse("-3")).isNull()
        assertThat(Money.parse("")).isNull()
    }

    // --- filtering and sorting ---

    @Test
    fun `filters partition coupons by state`() {
        val active = coupon(merchant = "A", expiresOn = today.plusDays(30))
        val soon = coupon(merchant = "B", expiresOn = today.plusDays(2), isFavorite = true)
        val lastDay = coupon(merchant = "C", expiresOn = today)
        val expired = coupon(merchant = "D", expiresOn = today.minusDays(1))
        val used = coupon(merchant = "E", usedAt = 5)
        val archived = coupon(merchant = "F", isArchived = true)
        val all = listOf(active, soon, lastDay, expired, used, archived)

        fun names(filter: CouponFilter) = CouponQuery(filter = filter, sort = CouponSort.MERCHANT).apply(all, today).map { it.merchant }

        assertThat(names(CouponFilter.ACTIVE)).containsExactly("A", "B", "C").inOrder()
        assertThat(names(CouponFilter.FAVORITES)).containsExactly("B")
        assertThat(names(CouponFilter.EXPIRING_SOON)).containsExactly("B", "C").inOrder()
        assertThat(names(CouponFilter.EXPIRED)).containsExactly("D")
        assertThat(names(CouponFilter.USED)).containsExactly("E")
        assertThat(names(CouponFilter.ARCHIVED)).containsExactly("F")
    }

    @Test
    fun `search matches store code category and notes`() {
        val all = listOf(
            coupon(merchant = "Target", code = "SAVE20"),
            coupon(merchant = "Starbucks", code = "LATTE", category = "Restaurants"),
        )
        fun find(text: String) = CouponQuery(text = text).apply(all, today).map { it.merchant }
        assertThat(find("targ")).containsExactly("Target")
        assertThat(find("latte")).containsExactly("Starbucks")
        assertThat(find("restau")).containsExactly("Starbucks")
        assertThat(find("zzz")).isEmpty()
    }

    @Test
    fun `expiry sort puts soonest first and undated last`() {
        val all = listOf(
            coupon(merchant = "Never"),
            coupon(merchant = "Later", expiresOn = today.plusDays(20)),
            coupon(merchant = "Soon", expiresOn = today.plusDays(1)),
        )
        assertThat(CouponQuery(sort = CouponSort.EXPIRY).apply(all, today).map { it.merchant })
            .containsExactly("Soon", "Later", "Never").inOrder()
    }

    @Test
    fun `place filter narrows to coupons usable there`() {
        val all = listOf(coupon(merchant = "Here", placeIds = listOf("p1")), coupon(merchant = "Elsewhere", placeIds = listOf("p2")))
        assertThat(CouponQuery(placeId = "p1").apply(all, today).map { it.merchant }).containsExactly("Here")
    }

    // --- geofence planning ---

    @Test
    fun `only places with something redeemable are watched`() {
        val a = place(id = "a"); val b = place(id = "b"); val c = place(id = "c"); val d = place(id = "d")
        val watched = placesToWatch(
            places = listOf(a, b, c, d),
            coupons = listOf(
                coupon(placeIds = listOf("a")),
                coupon(placeIds = listOf("b"), expiresOn = today.minusDays(1)), // expired
                coupon(placeIds = listOf("b"), usedAt = 1),                     // used
            ),
            memberships = listOf(membership(placeIds = listOf("c"))),
            today = today,
        )
        assertThat(watched.map { it.id }).containsExactly("a", "c")
    }

    @Test
    fun `plan registers everything when under the limit`() {
        val fences = (1..5).map { Fence("p$it", LatLng(40.0 + it, -74.0), 150f) }
        val plan = GeofencePlanner.plan(fences, origin = null)
        assertThat(plan.fences).hasSize(5)
        assertThat(plan.refreshFence).isNull()
    }

    @Test
    fun `plan keeps the nearest places and adds a refresh fence when over the limit`() {
        val origin = LatLng(40.0, -74.0)
        // 150 places strung out eastwards, roughly 850 m apart.
        val fences = (1..150).map { Fence("p$it", LatLng(40.0, -74.0 + it * 0.01), 150f) }
        val plan = GeofencePlanner.plan(fences.shuffled(), origin)

        assertThat(plan.fences).hasSize(99)
        assertThat(plan.fences.map { it.id }).containsExactlyElementsIn((1..99).map { "p$it" })
        val refresh = plan.refreshFence!!
        assertThat(refresh.id).isEqualTo(GeofencePlanner.REFRESH_FENCE_ID)
        assertThat(refresh.center).isEqualTo(origin)
        // The user must leave the refresh area before reaching the first unwatched place.
        val gap = origin.distanceTo(LatLng(40.0, -73.0)) - 150
        assertThat(refresh.radiusMeters.toDouble()).isLessThan(gap)
        assertThat(plan.fences.size + 1).isAtMost(GeofencePlanner.MAX_GEOFENCES)
    }

    @Test
    fun `distance is accurate to within a percent`() {
        // New York to Los Angeles is about 3,936 km.
        val d = LatLng(40.7128, -74.0060).distanceTo(LatLng(34.0522, -118.2437))
        assertThat(d).isWithin(40_000.0).of(3_936_000.0)
    }

    // --- reminders ---

    @Test
    fun `expiry reminder is due inside the lead window and only once`() {
        val soon = coupon(id = "soon", expiresOn = today.plusDays(2))
        val later = coupon(id = "later", expiresOn = today.plusDays(10))
        val gone = coupon(id = "gone", expiresOn = today.minusDays(1))
        val used = coupon(id = "used", expiresOn = today.plusDays(1), usedAt = 1)
        val all = listOf(soon, later, gone, used)

        assertThat(ReminderPlanner.couponsDue(all, emptyMap(), today, leadDays = 2).map { it.id }).containsExactly("soon")
        // Already reminded for this expiry date: stay quiet.
        assertThat(ReminderPlanner.couponsDue(all, mapOf("soon" to soon.expiresOn), today, 2)).isEmpty()
        // The date was pushed back and the new one is near: remind again.
        assertThat(ReminderPlanner.couponsDue(all, mapOf("soon" to today.minusDays(5)), today, 2).map { it.id }).containsExactly("soon")
    }

    @Test
    fun `membership renewal rolls forward and reminds inside its window`() {
        val monthly = membership(id = "m").copy(renewsOn = today.minusDays(27), billingCycle = BillingCycle.MONTHLY, fee = 10.0, reminderDays = 7)
        val next = monthly.nextRenewal(today)!!
        assertThat(next.isBefore(today)).isFalse()
        assertThat(monthly.annualCost).isEqualTo(120.0)

        val due = ReminderPlanner.renewalsDue(listOf(monthly), emptyMap(), today)
        assertThat(due.map { it.renewsOn }).containsExactly(next)
        assertThat(ReminderPlanner.renewalsDue(listOf(monthly), mapOf("m" to next), today)).isEmpty()
        assertThat(ReminderPlanner.renewalsDue(listOf(monthly.copy(reminderDays = 0)), emptyMap(), today)).isEmpty()
        assertThat(ReminderPlanner.renewalsDue(listOf(monthly.copy(isActive = false)), emptyMap(), today)).isEmpty()
    }

    // --- barcodes ---

    @Test
    fun `barcodes encode for valid content and fail safely otherwise`() {
        assertThat(BarcodeRenderer.encode("SAVE20", BarcodeFormat.QR_CODE)).isNotNull()
        assertThat(BarcodeRenderer.encode("SAVE20", BarcodeFormat.CODE_128)).isNotNull()
        assertThat(BarcodeRenderer.encode("5901234123457", BarcodeFormat.EAN_13)).isNotNull()
        // EAN-13 only takes digits.
        assertThat(BarcodeRenderer.encode("not-a-number", BarcodeFormat.EAN_13)).isNull()
        BarcodeFormat.entries.forEach { assertThat(com.google.zxing.BarcodeFormat.valueOf(it.name)).isNotNull() }
    }
}
