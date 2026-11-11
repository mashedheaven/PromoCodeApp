package com.promocodeapp.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/*
 * Sync bookkeeping shared by every synced table:
 *  - ids are client-generated UUIDs, so rows keep one identity on every device;
 *  - `dirty` marks rows with local changes that still need uploading;
 *  - `deletedAt` is a tombstone, kept until the deletion has been uploaded.
 */

@Entity(tableName = "coupons", indices = [Index("deletedAt"), Index("dirty")])
data class CouponEntity(
    @PrimaryKey val id: String,
    val code: String,
    val merchant: String,
    val discountType: String,
    val discountValue: Double,
    val currency: String,
    val minPurchase: Double?,
    val category: String?,
    val notes: String?,
    val expiresOn: LocalDate?,
    val barcode: String?,
    val barcodeFormat: String?,
    val isFavorite: Boolean,
    val usedAt: Long?,
    val isArchived: Boolean,
    val placeIds: List<String>,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val dirty: Boolean = true,
    /** Device-local: expiry date an "expiring soon" reminder was already sent for. */
    val reminderSentFor: LocalDate? = null,
)

@Entity(tableName = "places", indices = [Index("deletedAt"), Index("dirty")])
data class PlaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val address: String?,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val dirty: Boolean = true,
)

@Entity(tableName = "memberships", indices = [Index("deletedAt"), Index("dirty")])
data class MembershipEntity(
    @PrimaryKey val id: String,
    val organization: String,
    val memberNumber: String,
    val type: String,
    val startedOn: LocalDate?,
    val renewsOn: LocalDate?,
    val fee: Double?,
    val billingCycle: String,
    val currency: String,
    val notes: String?,
    val reminderDays: Int,
    val isActive: Boolean,
    val placeIds: List<String>,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val dirty: Boolean = true,
    /** Device-local: renewal date a reminder was already sent for. */
    val reminderSentFor: LocalDate? = null,
)

/** Device-local geofence bookkeeping; never synced. */
@Entity(tableName = "place_alert_state")
data class PlaceAlertStateEntity(
    @PrimaryKey val placeId: String,
    val inside: Boolean,
    val lastNotifiedAt: Long,
    /** Last enter/exit event; an old "inside" is not trusted (see ProximityAlerter). */
    val updatedAt: Long = 0,
)
