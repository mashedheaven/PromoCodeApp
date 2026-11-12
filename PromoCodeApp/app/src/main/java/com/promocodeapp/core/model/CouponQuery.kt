package com.promocodeapp.core.model

import java.time.LocalDate

enum class CouponFilter(val label: String) {
    ACTIVE("Active"),
    FAVORITES("Favorites"),
    EXPIRING_SOON("Expiring soon"),
    USED("Used"),
    EXPIRED("Expired"),
    ARCHIVED("Archived"),
}

enum class CouponSort(val label: String) {
    EXPIRY("Expiring first"),
    MERCHANT("Store name"),
    NEWEST("Recently added"),
    DISCOUNT("Biggest discount"),
}

data class CouponQuery(
    val text: String = "",
    val filter: CouponFilter = CouponFilter.ACTIVE,
    val sort: CouponSort = CouponSort.EXPIRY,
    val category: String? = null,
    val placeId: String? = null,
) {
    fun apply(coupons: List<Coupon>, today: LocalDate): List<Coupon> {
        val needle = text.trim()
        return coupons.asSequence()
            .filter { matchesFilter(it, today) }
            .filter { category == null || it.category.equals(category, ignoreCase = true) }
            .filter { placeId == null || placeId in it.placeIds }
            .filter { needle.isEmpty() || matchesText(it, needle) }
            .sortedWith(comparator())
            .toList()
    }

    private fun matchesFilter(c: Coupon, today: LocalDate): Boolean = when (filter) {
        CouponFilter.ACTIVE -> c.isRedeemable(today)
        CouponFilter.FAVORITES -> c.isFavorite && c.isRedeemable(today)
        CouponFilter.EXPIRING_SOON -> c.isRedeemable(today) && when (val e = c.expiry(today)) {
            Expiry.Today -> true
            is Expiry.InDays -> e.days <= EXPIRING_SOON_DAYS
            else -> false
        }
        CouponFilter.USED -> c.isUsed && !c.isArchived
        CouponFilter.EXPIRED -> !c.isUsed && !c.isArchived && c.expiry(today).isExpired
        CouponFilter.ARCHIVED -> c.isArchived
    }

    private fun matchesText(c: Coupon, needle: String): Boolean =
        c.merchant.contains(needle, ignoreCase = true) ||
            c.code.contains(needle, ignoreCase = true) ||
            c.category?.contains(needle, ignoreCase = true) == true ||
            c.notes?.contains(needle, ignoreCase = true) == true

    private fun comparator(): Comparator<Coupon> = when (sort) {
        // Coupons without an expiry date go last; ties fall back to store name.
        CouponSort.EXPIRY -> compareBy<Coupon> { it.expiresOn ?: LocalDate.MAX }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.merchant }
        CouponSort.MERCHANT -> compareBy<Coupon, String>(String.CASE_INSENSITIVE_ORDER) { it.merchant }
            .thenBy { it.expiresOn ?: LocalDate.MAX }
        CouponSort.NEWEST -> compareByDescending { it.createdAt }
        CouponSort.DISCOUNT -> compareByDescending<Coupon> { discountRank(it.discount) }
            .thenBy { it.expiresOn ?: LocalDate.MAX }
    }

    /** Percentages and amounts are not comparable, so percentages rank first. */
    private fun discountRank(d: Discount): Double = when (d) {
        is Discount.Percentage -> 1_000_000 + d.percent
        is Discount.FixedAmount -> d.amount
        Discount.BuyOneGetOne -> 0.5
        Discount.FreeShipping -> 0.25
    }

    companion object {
        const val EXPIRING_SOON_DAYS = 7L
    }
}
