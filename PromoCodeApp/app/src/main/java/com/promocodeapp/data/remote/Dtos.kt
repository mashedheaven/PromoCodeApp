@file:OptIn(ExperimentalSerializationApi::class)

package com.promocodeapp.data.remote

import com.promocodeapp.data.local.CouponEntity
import com.promocodeapp.data.local.MembershipEntity
import com.promocodeapp.data.local.PlaceEntity
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate

/*
 * Wire format of the Supabase tables in supabase/schema.sql. `server_updated_at`
 * is assigned by the database and only ever read, so it is never sent.
 */

interface RemoteRow {
    val id: String
    val updatedAt: Long
    val deletedAt: Long?
    val serverUpdatedAt: String?
}

@Serializable
data class CouponDto(
    override val id: String,
    val code: String,
    val merchant: String,
    @SerialName("discount_type") val discountType: String,
    @SerialName("discount_value") val discountValue: Double,
    val currency: String,
    @SerialName("min_purchase") val minPurchase: Double?,
    val category: String?,
    val notes: String?,
    @SerialName("expires_on") val expiresOn: String?,
    val barcode: String?,
    @SerialName("barcode_format") val barcodeFormat: String?,
    @SerialName("is_favorite") val isFavorite: Boolean,
    @SerialName("used_at") val usedAt: Long?,
    @SerialName("is_archived") val isArchived: Boolean,
    @SerialName("place_ids") val placeIds: List<String>,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") override val updatedAt: Long,
    @SerialName("deleted_at") override val deletedAt: Long?,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("server_updated_at") override val serverUpdatedAt: String? = null,
) : RemoteRow

@Serializable
data class PlaceDto(
    override val id: String,
    val name: String,
    val address: String?,
    val latitude: Double,
    val longitude: Double,
    @SerialName("radius_meters") val radiusMeters: Int,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") override val updatedAt: Long,
    @SerialName("deleted_at") override val deletedAt: Long?,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("server_updated_at") override val serverUpdatedAt: String? = null,
) : RemoteRow

@Serializable
data class MembershipDto(
    override val id: String,
    val organization: String,
    @SerialName("member_number") val memberNumber: String,
    val type: String,
    @SerialName("started_on") val startedOn: String?,
    @SerialName("renews_on") val renewsOn: String?,
    val fee: Double?,
    @SerialName("billing_cycle") val billingCycle: String,
    val currency: String,
    val notes: String?,
    @SerialName("reminder_days") val reminderDays: Int,
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("place_ids") val placeIds: List<String>,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") override val updatedAt: Long,
    @SerialName("deleted_at") override val deletedAt: Long?,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("server_updated_at") override val serverUpdatedAt: String? = null,
) : RemoteRow

@Serializable
data class DeviceTokenDto(val token: String, val platform: String = "android")

private fun String?.toDate(): LocalDate? = this?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

fun CouponEntity.toDto() = CouponDto(
    id, code, merchant, discountType, discountValue, currency, minPurchase, category, notes,
    expiresOn?.toString(), barcode, barcodeFormat, isFavorite, usedAt, isArchived, placeIds,
    createdAt, updatedAt, deletedAt,
)

/** Remote rows arrive clean; device-local columns are kept from [existing]. */
fun CouponDto.toEntity(existing: CouponEntity?) = CouponEntity(
    id, code, merchant, discountType, discountValue, currency, minPurchase, category, notes,
    expiresOn.toDate(), barcode, barcodeFormat, isFavorite, usedAt, isArchived, placeIds,
    createdAt, updatedAt, deletedAt, dirty = false,
    reminderSentFor = existing?.reminderSentFor,
)

fun PlaceEntity.toDto() = PlaceDto(
    id, name, address, latitude, longitude, radiusMeters, createdAt, updatedAt, deletedAt,
)

fun PlaceDto.toEntity() = PlaceEntity(
    id, name, address, latitude, longitude, radiusMeters, createdAt, updatedAt, deletedAt, dirty = false,
)

fun MembershipEntity.toDto() = MembershipDto(
    id, organization, memberNumber, type, startedOn?.toString(), renewsOn?.toString(), fee,
    billingCycle, currency, notes, reminderDays, isActive, placeIds, createdAt, updatedAt, deletedAt,
)

fun MembershipDto.toEntity(existing: MembershipEntity?) = MembershipEntity(
    id, organization, memberNumber, type, startedOn.toDate(), renewsOn.toDate(), fee,
    billingCycle, currency, notes, reminderDays, isActive, placeIds, createdAt, updatedAt, deletedAt,
    dirty = false, reminderSentFor = existing?.reminderSentFor,
)

// --- auth (Supabase GoTrue) ---

@Serializable
data class CredentialsBody(val email: String, val password: String)

@Serializable
data class RefreshBody(@SerialName("refresh_token") val refreshToken: String)

@Serializable
data class EmailBody(val email: String)

@Serializable
data class AuthUserDto(val id: String? = null, val email: String? = null)

/** Sign-up returns a bare user (no tokens) when e-mail confirmation is required. */
@Serializable
data class AuthResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    val user: AuthUserDto? = null,
    val id: String? = null,
    val email: String? = null,
)

@Serializable
data class ApiErrorBody(
    val msg: String? = null,
    val message: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
    val error: String? = null,
)
