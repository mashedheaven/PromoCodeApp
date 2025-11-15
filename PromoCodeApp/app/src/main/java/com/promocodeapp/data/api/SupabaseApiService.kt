package com.promocodeapp.data.api

import retrofit2.http.*
import com.google.gson.annotations.SerializedName

// Supabase API Models
data class SupabaseCoupon(
    val id: String,
    @SerializedName("user_id")
    val userId: String,
    val code: String,
    @SerializedName("merchant_name")
    val merchantName: String,
    @SerializedName("discount_type")
    val discountType: String,
    @SerializedName("discount_value")
    val discountValue: Double,
    @SerializedName("discount_value_currency")
    val discountValueCurrency: String? = "USD",
    @SerializedName("min_purchase_amount")
    val minPurchaseAmount: Double? = null,
    val description: String? = null,
    @SerializedName("expiration_date")
    val expirationDate: Long,
    @SerializedName("created_date")
    val createdDate: Long,
    val category: String? = null,
    @SerializedName("is_favorite")
    val isFavorite: Boolean = false,
    @SerializedName("is_used")
    val isUsed: Boolean = false,
    @SerializedName("is_archived")
    val isArchived: Boolean = false,
    @SerializedName("image_url")
    val imageUrl: String? = null,
    @SerializedName("barcode_data")
    val barcodeData: String? = null,
    val notes: String? = null,
    @SerializedName("last_modified")
    val lastModified: Long
)

data class SupabaseCouponLocation(
    val id: String,
    @SerializedName("coupon_id")
    val couponId: String,
    @SerializedName("user_id")
    val userId: String,
    val latitude: Double,
    val longitude: Double,
    val radius: Int = 150,
    @SerializedName("geofence_id")
    val geofenceId: String = "",
    @SerializedName("location_name")
    val locationName: String? = null,
    @SerializedName("created_date")
    val createdDate: Long
)

data class SupabaseMembership(
    val id: String,
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("organization_name")
    val organizationName: String,
    @SerializedName("membership_number")
    val membershipNumber: String,
    @SerializedName("membership_type")
    val membershipType: String,
    @SerializedName("start_date")
    val startDate: Long,
    @SerializedName("renewal_date")
    val renewalDate: Long,
    @SerializedName("annual_fee")
    val annualFee: Double? = null,
    @SerializedName("monthly_fee")
    val monthlyFee: Double? = null,
    val currency: String = "USD",
    val benefits: String? = null,
    val notes: String? = null,
    @SerializedName("is_active")
    val isActive: Boolean = true,
    @SerializedName("reminder_enabled")
    val reminderEnabled: Boolean = true,
    @SerializedName("reminder_days_before_renewal")
    val reminderDaysBeforeRenewal: Int = 7,
    @SerializedName("created_date")
    val createdDate: Long,
    @SerializedName("last_modified")
    val lastModified: Long
)

data class SupabaseUser(
    val id: String,
    val email: String,
    @SerializedName("first_name")
    val firstName: String? = null,
    @SerializedName("last_name")
    val lastName: String? = null,
    @SerializedName("profile_image_url")
    val profileImageUrl: String? = null,
    @SerializedName("fcm_token")
    val fcmToken: String? = null,
    @SerializedName("default_geofence_radius")
    val defaultGeofenceRadius: Int = 150,
    @SerializedName("notifications_enabled")
    val notificationsEnabled: Boolean = true,
    @SerializedName("created_date")
    val createdDate: Long,
    @SerializedName("last_modified")
    val lastModified: Long
)

data class SyncRequest(
    @SerializedName("user_id")
    val userId: String,
    val changes: List<SyncChange>
)

data class SyncChange(
    @SerializedName("change_type")
    val changeType: String, // CREATE, UPDATE, DELETE
    @SerializedName("entity_type")
    val entityType: String, // COUPON, MEMBERSHIP
    @SerializedName("entity_id")
    val entityId: String? = null,
    @SerializedName("change_data")
    val changeData: Map<String, Any>
)

data class SyncResponse(
    val success: Boolean,
    val message: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

// Retrofit API Interface
interface SupabaseApiService {
    @GET("coupons")
    suspend fun getUserCoupons(@Query("user_id") userId: String): List<SupabaseCoupon>

    @POST("coupons")
    suspend fun createCoupon(@Body coupon: SupabaseCoupon): SupabaseCoupon

    @PUT("coupons/{id}")
    suspend fun updateCoupon(@Path("id") couponId: String, @Body coupon: SupabaseCoupon): SupabaseCoupon

    @DELETE("coupons/{id}")
    suspend fun deleteCoupon(@Path("id") couponId: String): SyncResponse

    @GET("coupon_locations")
    suspend fun getCouponLocations(@Query("coupon_id") couponId: String): List<SupabaseCouponLocation>

    @POST("coupon_locations")
    suspend fun createCouponLocation(@Body location: SupabaseCouponLocation): SupabaseCouponLocation

    @DELETE("coupon_locations/{id}")
    suspend fun deleteCouponLocation(@Path("id") locationId: String): SyncResponse

    @GET("memberships")
    suspend fun getUserMemberships(@Query("user_id") userId: String): List<SupabaseMembership>

    @POST("memberships")
    suspend fun createMembership(@Body membership: SupabaseMembership): SupabaseMembership

    @PUT("memberships/{id}")
    suspend fun updateMembership(@Path("id") membershipId: String, @Body membership: SupabaseMembership): SupabaseMembership

    @DELETE("memberships/{id}")
    suspend fun deleteMembership(@Path("id") membershipId: String): SyncResponse

    @GET("users/{id}")
    suspend fun getUser(@Path("id") userId: String): SupabaseUser

    @POST("users")
    suspend fun createUser(@Body user: SupabaseUser): SupabaseUser

    @PUT("users/{id}")
    suspend fun updateUser(@Path("id") userId: String, @Body user: SupabaseUser): SupabaseUser

    @POST("sync")
    suspend fun syncChanges(@Body syncRequest: SyncRequest): SyncResponse

    @POST("notifications/fcm-token")
    suspend fun updateFcmToken(
        @Query("user_id") userId: String,
        @Query("token") token: String
    ): SyncResponse
}
