package com.promocodeapp.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

/** Supabase Auth (GoTrue) endpoints. */
interface AuthApi {
    @POST("auth/v1/signup")
    suspend fun signUp(@Body body: CredentialsBody): AuthResponse

    @POST("auth/v1/token?grant_type=password")
    suspend fun signIn(@Body body: CredentialsBody): AuthResponse

    @POST("auth/v1/token?grant_type=refresh_token")
    suspend fun refresh(@Body body: RefreshBody): AuthResponse

    @POST("auth/v1/logout")
    suspend fun signOut(@Header("Authorization") bearer: String)

    @POST("auth/v1/recover")
    suspend fun recover(@Body body: EmailBody)
}

/**
 * Supabase PostgREST endpoints. Row-level security scopes every request to the
 * signed-in user, so no user id is ever sent. Pushes are upserts keyed by id;
 * the database keeps whichever version has the newer `updated_at`.
 */
interface RestApi {
    @GET("rest/v1/places?select=*")
    suspend fun pullPlaces(
        @Query("server_updated_at") after: String?,
        @Query("limit") limit: Int,
        @Query("order") order: String = ORDER,
    ): List<PlaceDto>

    @Headers(UPSERT)
    @POST("rest/v1/places?on_conflict=id")
    suspend fun pushPlaces(@Body rows: List<PlaceDto>)

    @GET("rest/v1/coupons?select=*")
    suspend fun pullCoupons(
        @Query("server_updated_at") after: String?,
        @Query("limit") limit: Int,
        @Query("order") order: String = ORDER,
    ): List<CouponDto>

    @Headers(UPSERT)
    @POST("rest/v1/coupons?on_conflict=id")
    suspend fun pushCoupons(@Body rows: List<CouponDto>)

    @GET("rest/v1/memberships?select=*")
    suspend fun pullMemberships(
        @Query("server_updated_at") after: String?,
        @Query("limit") limit: Int,
        @Query("order") order: String = ORDER,
    ): List<MembershipDto>

    @Headers(UPSERT)
    @POST("rest/v1/memberships?on_conflict=id")
    suspend fun pushMemberships(@Body rows: List<MembershipDto>)

    @Headers(UPSERT)
    @POST("rest/v1/device_tokens?on_conflict=token")
    suspend fun registerDeviceToken(@Body row: DeviceTokenDto)

    companion object {
        const val ORDER = "server_updated_at.asc"
        const val UPSERT = "Prefer: resolution=merge-duplicates,return=minimal"
    }
}
