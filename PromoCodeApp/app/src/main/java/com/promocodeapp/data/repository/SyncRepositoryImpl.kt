package com.promocodeapp.data.repository

import com.promocodeapp.data.api.SupabaseApiService
import com.promocodeapp.data.api.SupabaseCoupon
import com.promocodeapp.data.api.SyncChange
import com.promocodeapp.data.api.SyncRequest
import com.promocodeapp.data.db.dao.CouponDao
import com.promocodeapp.data.db.dao.CouponLocationDao
import com.promocodeapp.data.db.dao.PendingChangeDao
import com.promocodeapp.data.db.entity.CouponEntity
import com.promocodeapp.data.db.entity.CouponLocationEntity
import com.promocodeapp.data.db.entity.PendingChangeEntity
import com.promocodeapp.domain.repository.SyncRepository
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import android.util.Log

class SyncRepositoryImpl @Inject constructor(
    private val couponDao: CouponDao,
    private val couponLocationDao: CouponLocationDao,
    private val pendingChangeDao: PendingChangeDao,
    private val apiService: SupabaseApiService,
    private val gson: Gson
) : SyncRepository {

    companion object {
        private const val TAG = "SyncRepository"
    }

    override suspend fun syncCoupons(userId: String): Result<Unit> = try {
        Log.d(TAG, "Starting coupon sync for user: $userId")
        
        // Step 1: Upload pending changes
        uploadPendingChanges(userId)
        
        // Step 2: Download coupons from server
        val remoteCoupons = apiService.getUserCoupons(userId)
        Log.d(TAG, "Downloaded ${remoteCoupons.size} coupons from server")
        
        // Step 3: Save to local database
        remoteCoupons.forEach { remoteCoupon ->
            saveCouponLocally(remoteCoupon, userId)
        }
        
        // Step 4: Mark local pending changes as synced
        pendingChangeDao.markPendingChangesAsNotPending(userId, "COUPON")
        
        Log.d(TAG, "Coupon sync completed successfully")
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "Coupon sync failed: ${e.message}", e)
        Result.failure(e)
    }

    override suspend fun syncMemberships(userId: String): Result<Unit> = try {
        Log.d(TAG, "Starting membership sync for user: $userId")
        
        // Download memberships from server
        val remoteMemberships = apiService.getUserMemberships(userId)
        Log.d(TAG, "Downloaded ${remoteMemberships.size} memberships from server")
        
        // TODO: Save memberships to local database
        // This would require implementing MembershipRepository
        
        Log.d(TAG, "Membership sync completed successfully")
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "Membership sync failed: ${e.message}", e)
        Result.failure(e)
    }

    override suspend fun fullSync(userId: String): Result<Unit> = try {
        Log.d(TAG, "Starting full sync for user: $userId")
        
        val couponResult = syncCoupons(userId)
        val membershipResult = syncMemberships(userId)
        
        return if (couponResult.isSuccess && membershipResult.isSuccess) {
            Log.d(TAG, "Full sync completed successfully")
            Result.success(Unit)
        } else {
            val errors = listOfNotNull(
                couponResult.exceptionOrNull()?.message,
                membershipResult.exceptionOrNull()?.message
            ).joinToString(", ")
            Log.e(TAG, "Full sync failed: $errors")
            Result.failure(Exception("Sync failed: $errors"))
        }
    } catch (e: Exception) {
        Log.e(TAG, "Full sync encountered error: ${e.message}", e)
        Result.failure(e)
    }

    override fun getPendingChanges(userId: String): Flow<Int> =
        pendingChangeDao.getPendingChangesCount(userId)

    override suspend fun markChangeAsSynced(changeId: Long): Result<Unit> = try {
        pendingChangeDao.markAsSynced(changeId)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Upload pending local changes to the server
     */
    private suspend fun uploadPendingChanges(userId: String) {
        try {
            val pendingChanges = pendingChangeDao.getPendingChangesByUser(userId)
            
            if (pendingChanges.isEmpty()) {
                Log.d(TAG, "No pending changes to upload")
                return
            }
            
            Log.d(TAG, "Uploading ${pendingChanges.size} pending changes")
            
            // Convert entities to sync changes
            val syncChanges = pendingChanges.map { change ->
                SyncChange(
                    changeType = change.changeType,
                    entityType = change.entityType,
                    entityId = change.entityId,
                    changeData = gson.fromJson(change.changeData, Map::class.java) as? Map<String, Any>
                        ?: emptyMap()
                )
            }
            
            // Send sync request to server
            val syncRequest = SyncRequest(userId = userId, changes = syncChanges)
            val response = apiService.syncChanges(syncRequest)
            
            if (response.success) {
                // Mark all pending changes as synced
                pendingChanges.forEach { change ->
                    pendingChangeDao.markAsSynced(change.id)
                }
                Log.d(TAG, "Successfully uploaded pending changes")
            } else {
                Log.e(TAG, "Server rejected sync changes: ${response.message}")
                throw Exception("Sync failed: ${response.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload pending changes: ${e.message}", e)
            throw e
        }
    }

    /**
     * Save a remote coupon to local database
     */
    private suspend fun saveCouponLocally(remoteCoupon: SupabaseCoupon, userId: String) {
        try {
            val localCoupon = CouponEntity(
                id = remoteCoupon.id.toLongOrNull() ?: 0,
                userId = remoteCoupon.userId,
                code = remoteCoupon.code,
                merchantName = remoteCoupon.merchantName,
                discountType = remoteCoupon.discountType,
                discountValue = remoteCoupon.discountValue,
                discountValueCurrency = remoteCoupon.discountValueCurrency ?: "USD",
                minPurchaseAmount = remoteCoupon.minPurchaseAmount,
                description = remoteCoupon.description,
                expirationDate = remoteCoupon.expirationDate,
                createdDate = remoteCoupon.createdDate,
                category = remoteCoupon.category,
                isFavorite = remoteCoupon.isFavorite,
                isUsed = remoteCoupon.isUsed,
                isArchived = remoteCoupon.isArchived,
                imageUrl = remoteCoupon.imageUrl,
                barcodeData = remoteCoupon.barcodeData,
                notes = remoteCoupon.notes,
                lastModified = remoteCoupon.lastModified
            )
            
            // Insert or update
            couponDao.insertCoupon(localCoupon)
            
            // Fetch and save locations
            val locations = apiService.getCouponLocations(remoteCoupon.id)
            locations.forEach { remoteLocation ->
                val localLocation = CouponLocationEntity(
                    id = remoteLocation.id.toLongOrNull() ?: 0,
                    couponId = localCoupon.id,
                    userId = remoteLocation.userId,
                    latitude = remoteLocation.latitude,
                    longitude = remoteLocation.longitude,
                    radius = remoteLocation.radius,
                    geofenceId = remoteLocation.geofenceId,
                    locationName = remoteLocation.locationName,
                    createdDate = remoteLocation.createdDate
                )
                couponLocationDao.insertLocation(localLocation)
            }
            
            Log.d(TAG, "Saved coupon ${remoteCoupon.code} locally with ${locations.size} locations")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save coupon locally: ${e.message}", e)
            throw e
        }
    }
}
