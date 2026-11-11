package com.promocodeapp.core.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/** What a coupon is worth. */
sealed interface Discount {
    data class Percentage(val percent: Double) : Discount
    data class FixedAmount(val amount: Double, val currency: String = "USD") : Discount
    data object BuyOneGetOne : Discount
    data object FreeShipping : Discount

    /** Short label for badges, e.g. "20% OFF". */
    fun label(locale: Locale = Locale.getDefault()): String = when (this) {
        is Percentage -> "${trimNumber(percent, locale)}% OFF"
        is FixedAmount -> "${Money.format(amount, currency, locale)} OFF"
        BuyOneGetOne -> "BUY 1 GET 1"
        FreeShipping -> "FREE SHIPPING"
    }

    private fun trimNumber(value: Double, locale: Locale): String =
        if (value % 1.0 == 0.0) String.format(locale, "%.0f", value) else String.format(locale, "%.1f", value)
}

/** Where a coupon stands relative to its expiry date. */
sealed interface Expiry {
    data object Never : Expiry
    data class Expired(val daysAgo: Long) : Expiry
    data object Today : Expiry
    data class InDays(val days: Long) : Expiry

    val isExpired: Boolean get() = this is Expired

    companion object {
        /**
         * A coupon that "expires on" a date stays valid through the end of that
         * day, so expiry is compared by calendar date rather than by instant.
         */
        fun of(expiresOn: LocalDate?, today: LocalDate): Expiry {
            if (expiresOn == null) return Never
            val days = ChronoUnit.DAYS.between(today, expiresOn)
            return when {
                days < 0 -> Expired(-days)
                days == 0L -> Today
                else -> InDays(days)
            }
        }
    }
}

data class Coupon(
    val id: String,
    val code: String,
    val merchant: String,
    val discount: Discount,
    val minPurchase: Double? = null,
    val category: String? = null,
    val notes: String? = null,
    val expiresOn: LocalDate? = null,
    /** Raw barcode contents, when the coupon is redeemed by scanning. */
    val barcode: String? = null,
    val barcodeFormat: BarcodeFormat? = null,
    val isFavorite: Boolean = false,
    /** When the coupon was redeemed (epoch millis), or null if unused. */
    val usedAt: Long? = null,
    val isArchived: Boolean = false,
    /** Places where this coupon can be redeemed; drives nearby alerts. */
    val placeIds: List<String> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
) {
    val isUsed: Boolean get() = usedAt != null

    /** One-line summary, e.g. "20% OFF · SAVE20". Some deals have no code. */
    val headline: String get() = if (code.isBlank()) discount.label() else "${discount.label()} · $code"

    fun expiry(today: LocalDate): Expiry = Expiry.of(expiresOn, today)

    /** Still worth reminding the user about. */
    fun isRedeemable(today: LocalDate): Boolean =
        !isUsed && !isArchived && !expiry(today).isExpired
}

/** Barcode symbologies we can both scan and render. */
enum class BarcodeFormat {
    QR_CODE, AZTEC, DATA_MATRIX, PDF_417,
    CODE_128, CODE_39, CODE_93, CODABAR, ITF,
    EAN_13, EAN_8, UPC_A, UPC_E;

    val isTwoDimensional: Boolean
        get() = this == QR_CODE || this == AZTEC || this == DATA_MATRIX

    companion object {
        fun fromName(name: String?): BarcodeFormat? = entries.firstOrNull { it.name == name }
    }
}

/** A saved store location. One geofence is registered per place. */
data class Place(
    val id: String,
    val name: String,
    val address: String? = null,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int = DEFAULT_RADIUS_METERS,
    val createdAt: Long,
    val updatedAt: Long,
) {
    companion object {
        /** Below ~100 m geofences are unreliable on real devices. */
        const val MIN_RADIUS_METERS = 100
        const val MAX_RADIUS_METERS = 2000
        const val DEFAULT_RADIUS_METERS = 150

        fun isValidCoordinate(latitude: Double, longitude: Double): Boolean =
            latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
                !(latitude == 0.0 && longitude == 0.0)
    }
}

enum class MembershipType(val label: String) {
    GYM("Gym"),
    SUBSCRIPTION("Subscription"),
    LOYALTY("Loyalty program"),
    CLUB("Club"),
    OTHER("Other");

    companion object {
        fun fromName(name: String?): MembershipType = entries.firstOrNull { it.name == name } ?: OTHER
    }
}

enum class BillingCycle(val label: String, val perYear: Int) {
    NONE("Free", 0),
    MONTHLY("Monthly", 12),
    YEARLY("Yearly", 1);

    companion object {
        fun fromName(name: String?): BillingCycle = entries.firstOrNull { it.name == name } ?: NONE
    }
}

data class Membership(
    val id: String,
    val organization: String,
    val memberNumber: String = "",
    val type: MembershipType = MembershipType.OTHER,
    val startedOn: LocalDate? = null,
    val renewsOn: LocalDate? = null,
    val fee: Double? = null,
    val billingCycle: BillingCycle = BillingCycle.NONE,
    val currency: String = "USD",
    val notes: String? = null,
    /** Days before renewal to remind; 0 disables the reminder. */
    val reminderDays: Int = 7,
    val isActive: Boolean = true,
    val placeIds: List<String> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
) {
    val annualCost: Double get() = (fee ?: 0.0) * billingCycle.perYear

    fun daysUntilRenewal(today: LocalDate): Long? =
        renewsOn?.let { ChronoUnit.DAYS.between(today, it) }

    /**
     * The next renewal on or after [today]. Recurring memberships roll forward
     * so a stale date still produces a useful countdown.
     */
    fun nextRenewal(today: LocalDate): LocalDate? {
        var next = renewsOn ?: return null
        if (billingCycle == BillingCycle.NONE) return next
        while (next.isBefore(today)) {
            next = if (billingCycle == BillingCycle.MONTHLY) next.plusMonths(1) else next.plusYears(1)
        }
        return next
    }
}
