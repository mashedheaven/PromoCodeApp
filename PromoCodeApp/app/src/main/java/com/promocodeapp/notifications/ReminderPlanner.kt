package com.promocodeapp.notifications

import com.promocodeapp.core.model.Coupon
import com.promocodeapp.core.model.Expiry
import com.promocodeapp.core.model.Membership
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Pure rules for which reminders are due; kept free of Android for testing. */
object ReminderPlanner {

    /**
     * Coupons expiring within [leadDays] that have not been reminded about yet.
     * [alreadySentFor] maps coupon id to the expiry date a reminder went out for.
     */
    fun couponsDue(
        coupons: List<Coupon>,
        alreadySentFor: Map<String, LocalDate?>,
        today: LocalDate,
        leadDays: Int,
    ): List<Coupon> = coupons.filter { c ->
        val dueSoon = when (val e = c.expiry(today)) {
            Expiry.Today -> true
            is Expiry.InDays -> e.days <= leadDays
            else -> false
        }
        dueSoon && c.isRedeemable(today) && alreadySentFor[c.id] != c.expiresOn
    }.sortedBy { it.expiresOn }

    data class Renewal(val membership: Membership, val renewsOn: LocalDate, val daysLeft: Long)

    fun renewalsDue(
        memberships: List<Membership>,
        alreadySentFor: Map<String, LocalDate?>,
        today: LocalDate,
    ): List<Renewal> = memberships.mapNotNull { m ->
        if (!m.isActive || m.reminderDays <= 0) return@mapNotNull null
        val next = m.nextRenewal(today) ?: return@mapNotNull null
        val days = ChronoUnit.DAYS.between(today, next)
        if (days < 0 || days > m.reminderDays || alreadySentFor[m.id] == next) null
        else Renewal(m, next, days)
    }
}
