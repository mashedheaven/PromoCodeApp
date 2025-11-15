package com.promocodeapp.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "coupons")
data class CouponEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: String,
    val code: String,
    val merchantName: String,
    val discountType: String, // PERCENTAGE, FIXED_AMOUNT, BOGO, FREE_SHIPPING
    val discountValue: Double,
    val discountValueCurrency: String = "USD",
    val minPurchaseAmount: Double? = null,
    val description: String? = null,
    val expirationDate: Long, // milliseconds since epoch
    val createdDate: Long = System.currentTimeMillis(),
    val category: String? = null,
    val isFavorite: Boolean = false,
    val isUsed: Boolean = false,
    val isArchived: Boolean = false,
    val imageUrl: String? = null,
    val barcodeData: String? = null,
    val notes: String? = null,
    val lastModified: Long = System.currentTimeMillis()
)

@Entity(tableName = "coupon_locations")
data class CouponLocationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val couponId: Long,
    val userId: String,
    val latitude: Double,
    val longitude: Double,
    val radius: Int = 150, // in meters, minimum recommended is 100-150
    val geofenceId: String = "", // for identification in geofencing system
    val locationName: String? = null,
    val createdDate: Long = System.currentTimeMillis()
)

@Entity(tableName = "memberships")
data class MembershipEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: String,
    val organizationName: String,
    val membershipNumber: String,
    val membershipType: String, // GYM, SUBSCRIPTION, LOYALTY_PROGRAM, etc.
    val startDate: Long, // milliseconds since epoch
    val renewalDate: Long, // milliseconds since epoch
    val annualFee: Double? = null,
    val monthlyFee: Double? = null,
    val currency: String = "USD",
    val benefits: String? = null, // JSON or comma-separated list
    val notes: String? = null,
    val isActive: Boolean = true,
    val reminderEnabled: Boolean = true,
    val reminderDaysBeforeRenewal: Int = 7,
    val createdDate: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis()
)

@Entity(tableName = "membership_locations")
data class MembershipLocationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val membershipId: Long,
    val userId: String,
    val latitude: Double,
    val longitude: Double,
    val radius: Int = 150,
    val locationName: String? = null,
    val address: String? = null,
    val createdDate: Long = System.currentTimeMillis()
)

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
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
    val createdDate: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis(),
    val lastSyncDate: Long? = null
)

@Entity(tableName = "pending_changes")
data class PendingChangeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: String,
    val changeType: String, // CREATE, UPDATE, DELETE
    val entityType: String, // COUPON, MEMBERSHIP, etc.
    val entityId: Long,
    val changeData: String, // JSON string of changes
    val timestamp: Long = System.currentTimeMillis(),
    val synced: Boolean = false
)

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey
    val key: String,
    val value: String,
    val lastUpdated: Long = System.currentTimeMillis()
)
