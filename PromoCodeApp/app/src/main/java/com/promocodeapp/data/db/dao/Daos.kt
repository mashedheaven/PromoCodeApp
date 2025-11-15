package com.promocodeapp.data.db.dao

import androidx.room.*
import com.promocodeapp.data.db.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CouponDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCoupon(coupon: CouponEntity): Long

    @Update
    suspend fun updateCoupon(coupon: CouponEntity)

    @Delete
    suspend fun deleteCoupon(coupon: CouponEntity)

    @Query("SELECT * FROM coupons WHERE id = :id")
    fun getCouponById(id: Long): Flow<CouponEntity?>

    @Query("SELECT * FROM coupons WHERE userId = :userId AND isArchived = 0 ORDER BY expirationDate ASC")
    fun getUserCoupons(userId: String): Flow<List<CouponEntity>>

    @Query("SELECT * FROM coupons WHERE userId = :userId AND isFavorite = 1 AND isArchived = 0")
    fun getFavoriteCoupons(userId: String): Flow<List<CouponEntity>>

    @Query("SELECT * FROM coupons WHERE userId = :userId AND merchantName LIKE '%' || :merchantName || '%' AND isArchived = 0")
    fun searchCouponsByMerchant(userId: String, merchantName: String): Flow<List<CouponEntity>>

    @Query("""
        SELECT * FROM coupons 
        WHERE userId = :userId 
        AND expirationDate > :now 
        AND expirationDate < :tomorrow
        AND isArchived = 0
        ORDER BY expirationDate ASC
    """)
    fun getExpiringTodayCoupons(userId: String, now: Long, tomorrow: Long): Flow<List<CouponEntity>>

    @Query("""
        SELECT * FROM coupons 
        WHERE userId = :userId 
        AND expirationDate > :now 
        AND expirationDate < :nextWeek
        AND isArchived = 0
        ORDER BY expirationDate ASC
    """)
    fun getExpiringThisWeekCoupons(userId: String, now: Long, nextWeek: Long): Flow<List<CouponEntity>>

    @Query("""
        SELECT * FROM coupons 
        WHERE userId = :userId 
        AND expirationDate <= :now
        AND isArchived = 0
        ORDER BY expirationDate DESC
    """)
    fun getExpiredCoupons(userId: String, now: Long): Flow<List<CouponEntity>>

    @Query("""
        SELECT * FROM coupons 
        WHERE userId = :userId 
        AND discountType = :discountType
        AND isArchived = 0
    """)
    fun getCouponsByType(userId: String, discountType: String): Flow<List<CouponEntity>>

    @Query("DELETE FROM coupons WHERE userId = :userId AND expirationDate < :thresholdTime")
    suspend fun deleteExpiredCoupons(userId: String, thresholdTime: Long)

    @Query("UPDATE coupons SET isArchived = 1 WHERE id = :couponId")
    suspend fun archiveCoupon(couponId: Long)

    @Query("UPDATE coupons SET isFavorite = :isFavorite WHERE id = :couponId")
    suspend fun updateCouponFavoriteStatus(couponId: Long, isFavorite: Boolean)

    @Query("SELECT COUNT(*) FROM coupons WHERE userId = :userId AND isArchived = 0")
    fun getActiveCouponsCount(userId: String): Flow<Int>
}

@Dao
interface CouponLocationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: CouponLocationEntity): Long

    @Update
    suspend fun updateLocation(location: CouponLocationEntity)

    @Delete
    suspend fun deleteLocation(location: CouponLocationEntity)

    @Query("SELECT * FROM coupon_locations WHERE couponId = :couponId")
    fun getLocationsForCoupon(couponId: Long): Flow<List<CouponLocationEntity>>

    @Query("SELECT * FROM coupon_locations WHERE userId = :userId")
    fun getUserLocations(userId: String): Flow<List<CouponLocationEntity>>

    @Query("DELETE FROM coupon_locations WHERE couponId = :couponId")
    suspend fun deleteLocationsForCoupon(couponId: Long)
}

@Dao
interface MembershipDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMembership(membership: MembershipEntity): Long

    @Update
    suspend fun updateMembership(membership: MembershipEntity)

    @Delete
    suspend fun deleteMembership(membership: MembershipEntity)

    @Query("SELECT * FROM memberships WHERE id = :id")
    fun getMembershipById(id: Long): Flow<MembershipEntity?>

    @Query("SELECT * FROM memberships WHERE userId = :userId AND isActive = 1 ORDER BY renewalDate ASC")
    fun getActiveMemberships(userId: String): Flow<List<MembershipEntity>>

    @Query("SELECT * FROM memberships WHERE userId = :userId ORDER BY renewalDate ASC")
    fun getAllMemberships(userId: String): Flow<List<MembershipEntity>>

    @Query("""
        SELECT * FROM memberships 
        WHERE userId = :userId 
        AND isActive = 1
        AND renewalDate > :now
        AND renewalDate < :daysFromNow
        AND reminderEnabled = 1
    """)
    fun getMembershipsNeedingRenewalReminder(
        userId: String,
        now: Long,
        daysFromNow: Long
    ): Flow<List<MembershipEntity>>

    @Query("SELECT COALESCE(SUM(annualFee), 0) FROM memberships WHERE userId = :userId AND isActive = 1")
    fun getTotalAnnualMembershipCost(userId: String): Flow<Double>

    @Query("UPDATE memberships SET isActive = 0 WHERE id = :membershipId")
    suspend fun deactivateMembership(membershipId: Long)

    @Query("SELECT COUNT(*) FROM memberships WHERE userId = :userId AND isActive = 1")
    fun getActiveMembershipsCount(userId: String): Flow<Int>
}

@Dao
interface MembershipLocationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: MembershipLocationEntity): Long

    @Update
    suspend fun updateLocation(location: MembershipLocationEntity)

    @Delete
    suspend fun deleteLocation(location: MembershipLocationEntity)

    @Query("SELECT * FROM membership_locations WHERE membershipId = :membershipId")
    fun getLocationsForMembership(membershipId: Long): Flow<List<MembershipLocationEntity>>

    @Query("DELETE FROM membership_locations WHERE membershipId = :membershipId")
    suspend fun deleteLocationsForMembership(membershipId: Long)
}

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(user: UserEntity)

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query("SELECT * FROM users WHERE userId = :userId")
    fun getUserById(userId: String): Flow<UserEntity?>

    @Query("DELETE FROM users")
    suspend fun deleteAllUsers()

    @Query("UPDATE users SET fcmToken = :fcmToken WHERE userId = :userId")
    suspend fun updateFcmToken(userId: String, fcmToken: String)

    @Query("UPDATE users SET notificationsEnabled = :enabled WHERE userId = :userId")
    suspend fun updateNotificationsEnabled(userId: String, enabled: Boolean)

    @Query("UPDATE users SET locationPermissionGranted = :granted WHERE userId = :userId")
    suspend fun updateLocationPermissionStatus(userId: String, granted: Boolean)

    @Query("UPDATE users SET backgroundLocationPermissionGranted = :granted WHERE userId = :userId")
    suspend fun updateBackgroundLocationPermissionStatus(userId: String, granted: Boolean)

    @Query("UPDATE users SET lastSyncDate = :timestamp WHERE userId = :userId")
    suspend fun updateLastSyncDate(userId: String, timestamp: Long)
}

@Dao
interface PendingChangeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingChange(change: PendingChangeEntity): Long

    @Delete
    suspend fun deletePendingChange(change: PendingChangeEntity)

    @Query("SELECT * FROM pending_changes WHERE userId = :userId AND synced = 0 ORDER BY timestamp ASC")
    fun getPendingChanges(userId: String): Flow<List<PendingChangeEntity>>

    @Query("SELECT * FROM pending_changes WHERE userId = :userId AND synced = 0 ORDER BY timestamp ASC")
    suspend fun getPendingChangesByUser(userId: String): List<PendingChangeEntity>

    @Query("SELECT COUNT(*) FROM pending_changes WHERE userId = :userId AND synced = 0")
    fun getPendingChangesCount(userId: String): Flow<Int>

    @Query("UPDATE pending_changes SET synced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: Long)

    @Query("UPDATE pending_changes SET synced = 0 WHERE userId = :userId AND entityType = :entityType")
    suspend fun markPendingChangesAsNotPending(userId: String, entityType: String)

    @Query("DELETE FROM pending_changes WHERE userId = :userId AND synced = 1")
    suspend fun deleteSyncedChanges(userId: String)

    @Query("DELETE FROM pending_changes WHERE userId = :userId AND synced = 1 AND entityType = :entityType")
    suspend fun deleteSyncedChangesByType(userId: String, entityType: String)
}

@Dao
interface SyncMetadataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(metadata: SyncMetadataEntity)

    @Query("SELECT * FROM sync_metadata WHERE key = :key")
    fun getMetadata(key: String): Flow<SyncMetadataEntity?>

    @Query("SELECT value FROM sync_metadata WHERE key = :key")
    suspend fun getMetadataValue(key: String): String?

    @Delete
    suspend fun deleteMetadata(metadata: SyncMetadataEntity)
}
