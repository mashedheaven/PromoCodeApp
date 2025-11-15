package com.promocodeapp.domain.repository

import com.promocodeapp.domain.model.Coupon
import com.promocodeapp.domain.model.Location
import com.promocodeapp.domain.model.Membership
import com.promocodeapp.domain.model.User
import kotlinx.coroutines.flow.Flow

interface CouponRepository {
    suspend fun createCoupon(coupon: Coupon): Result<Coupon>
    suspend fun updateCoupon(coupon: Coupon): Result<Coupon>
    suspend fun deleteCoupon(couponId: Long): Result<Unit>

    fun getUserCoupons(userId: String): Flow<List<Coupon>>
    fun getCouponById(couponId: Long): Flow<Coupon?>
    fun getFavoriteCoupons(userId: String): Flow<List<Coupon>>
    fun searchCoupons(userId: String, query: String): Flow<List<Coupon>>
    fun getExpiringCoupons(userId: String, days: Int): Flow<List<Coupon>>
    fun getCouponsByCategory(userId: String, category: String): Flow<List<Coupon>>

    suspend fun archiveCoupon(couponId: Long): Result<Unit>
    suspend fun toggleFavorite(couponId: Long, isFavorite: Boolean): Result<Unit>
    suspend fun updateCouponUsedStatus(couponId: Long, isUsed: Boolean): Result<Unit>

    suspend fun deleteExpiredCoupons(userId: String): Result<Unit>
}

interface LocationRepository {
    suspend fun addLocationToCoupon(couponId: Long, location: Location): Result<Location>
    suspend fun removeLocationFromCoupon(couponId: Long, locationId: Long): Result<Unit>
    fun getLocationsForCoupon(couponId: Long): Flow<List<Location>>
    fun getAllUserLocations(userId: String): Flow<List<Location>>
}

interface MembershipRepository {
    suspend fun createMembership(membership: Membership): Result<Membership>
    suspend fun updateMembership(membership: Membership): Result<Membership>
    suspend fun deleteMembership(membershipId: Long): Result<Unit>

    fun getUserMemberships(userId: String): Flow<List<Membership>>
    fun getActiveMemberships(userId: String): Flow<List<Membership>>
    fun getMembershipById(membershipId: Long): Flow<Membership?>
    fun getMembershipsNeedingReminder(userId: String): Flow<List<Membership>>

    suspend fun deactivateMembership(membershipId: Long): Result<Unit>
    fun getTotalAnnualCost(userId: String): Flow<Double>
}

interface UserRepository {
    suspend fun createUser(user: User): Result<User>
    suspend fun updateUser(user: User): Result<User>
    fun getUserById(userId: String): Flow<User?>

    suspend fun updateFcmToken(userId: String, token: String): Result<Unit>
    suspend fun updateNotificationPreferences(
        userId: String,
        notificationsEnabled: Boolean,
        proximityNotificationsEnabled: Boolean,
        expirationNotificationsEnabled: Boolean,
        membershipNotificationsEnabled: Boolean
    ): Result<Unit>

    suspend fun updateLocationPermissionStatus(userId: String, granted: Boolean): Result<Unit>
    suspend fun updateBackgroundLocationPermissionStatus(userId: String, granted: Boolean): Result<Unit>
}

interface SyncRepository {
    suspend fun syncCoupons(userId: String): Result<Unit>
    suspend fun syncMemberships(userId: String): Result<Unit>
    suspend fun fullSync(userId: String): Result<Unit>
    fun getPendingChanges(userId: String): Flow<Int>
    suspend fun markChangeAsSynced(changeId: Long): Result<Unit>
}

interface AuthRepository {
    suspend fun signUp(email: String, password: String): Result<User>
    suspend fun signIn(email: String, password: String): Result<User>
    suspend fun signOut(): Result<Unit>
    suspend fun getCurrentUser(): Result<User?>
    suspend fun updatePassword(newPassword: String): Result<Unit>
    suspend fun resetPassword(email: String): Result<Unit>
}
