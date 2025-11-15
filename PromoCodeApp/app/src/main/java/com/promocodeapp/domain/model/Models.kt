package com.promocodeapp.domain.model

import java.time.LocalDateTime

sealed class DiscountType {
    data class Percentage(val percentage: Double) : DiscountType()
    data class FixedAmount(val amount: Double, val currency: String = "USD") : DiscountType()
    object BuyOneGetOne : DiscountType()
    object FreeShipping : DiscountType()
}

data class Coupon(
    val id: Long,
    val userId: String,
    val code: String,
    val merchantName: String,
    val discountType: DiscountType,
    val discountValue: Double,
    val minPurchaseAmount: Double? = null,
    val description: String? = null,
    val expirationDate: Long, // milliseconds
    val createdDate: Long,
    val category: String? = null,
    val isFavorite: Boolean = false,
    val isUsed: Boolean = false,
    val isArchived: Boolean = false,
    val imageUrl: String? = null,
    val barcodeData: String? = null,
    val notes: String? = null,
    val locations: List<Location> = emptyList(),
    val lastModified: Long
) {
    val isExpired: Boolean
        get() = System.currentTimeMillis() > expirationDate

    val daysUntilExpiration: Long
        get() = (expirationDate - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)

    val expiresInHours: Long
        get() = (expirationDate - System.currentTimeMillis()) / (1000 * 60 * 60)
}

data class Location(
    val id: Long,
    val couponId: Long? = null,
    val membershipId: Long? = null,
    val userId: String,
    val latitude: Double,
    val longitude: Double,
    val radius: Int = 150, // meters
    val geofenceId: String = "",
    val locationName: String? = null,
    val address: String? = null,
    val createdDate: Long
)

data class Membership(
    val id: Long,
    val userId: String,
    val organizationName: String,
    val membershipNumber: String,
    val membershipType: String, // GYM, SUBSCRIPTION, LOYALTY_PROGRAM, etc.
    val startDate: Long,
    val renewalDate: Long,
    val annualFee: Double? = null,
    val monthlyFee: Double? = null,
    val currency: String = "USD",
    val benefits: String? = null,
    val notes: String? = null,
    val isActive: Boolean = true,
    val reminderEnabled: Boolean = true,
    val reminderDaysBeforeRenewal: Int = 7,
    val locations: List<Location> = emptyList(),
    val createdDate: Long,
    val lastModified: Long
) {
    val daysUntilRenewal: Long
        get() = (renewalDate - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)

    val needsRenewalReminder: Boolean
        get() = reminderEnabled && daysUntilRenewal <= reminderDaysBeforeRenewal && daysUntilRenewal > 0

    val totalAnnualCost: Double
        get() = annualFee ?: (monthlyFee?.times(12) ?: 0.0)
}

data class User(
    val userId: String,
    val email: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val profileImageUrl: String? = null,
    val fcmToken: String? = null,
    val defaultGeofenceRadius: Int = 150,
    val notificationsEnabled: Boolean = true,
    val proximityNotificationsEnabled: Boolean = true,
    val expirationNotificationsEnabled: Boolean = true,
    val membershipNotificationsEnabled: Boolean = true,
    val locationPermissionGranted: Boolean = false,
    val backgroundLocationPermissionGranted: Boolean = false,
    val createdDate: Long,
    val lastModified: Long,
    val lastSyncDate: Long? = null
) {
    val displayName: String
        get() = listOfNotNull(firstName, lastName).joinToString(" ").takeIf { it.isNotBlank() } ?: email
}

sealed class SyncChange {
    data class CreateCoupon(val coupon: Coupon) : SyncChange()
    data class UpdateCoupon(val coupon: Coupon) : SyncChange()
    data class DeleteCoupon(val couponId: Long) : SyncChange()
    data class CreateMembership(val membership: Membership) : SyncChange()
    data class UpdateMembership(val membership: Membership) : SyncChange()
    data class DeleteMembership(val membershipId: Long) : SyncChange()
}
