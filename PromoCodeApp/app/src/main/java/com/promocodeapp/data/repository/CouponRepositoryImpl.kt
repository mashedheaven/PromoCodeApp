package com.promocodeapp.data.repository

import com.promocodeapp.data.api.SupabaseApiService
import com.promocodeapp.data.db.dao.CouponDao
import com.promocodeapp.data.db.dao.CouponLocationDao
import com.promocodeapp.data.db.dao.PendingChangeDao
import com.promocodeapp.data.db.entity.*
import com.promocodeapp.domain.model.Coupon
import com.promocodeapp.domain.model.DiscountType
import com.promocodeapp.domain.model.Location
import com.promocodeapp.domain.repository.CouponRepository
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import android.util.Log

class CouponRepositoryImpl @Inject constructor(
    private val couponDao: CouponDao,
    private val couponLocationDao: CouponLocationDao,
    private val pendingChangeDao: PendingChangeDao,
    private val apiService: SupabaseApiService,
    private val gson: Gson
) : CouponRepository {

    override suspend fun createCoupon(coupon: Coupon): Result<Coupon> = try {
        val entity = couponToDatabaseEntity(coupon)
        val id = couponDao.insertCoupon(entity)

        // Save locations
        coupon.locations.forEach { location ->
            val locationEntity = locationToDatabaseEntity(location.copy(couponId = id))
            couponLocationDao.insertLocation(locationEntity)
        }

        // Add to pending changes
        pendingChangeDao.insertPendingChange(
            PendingChangeEntity(
                userId = coupon.userId,
                changeType = "CREATE",
                entityType = "COUPON",
                entityId = id,
                changeData = gson.toJson(coupon)
            )
        )

        Result.success(coupon.copy(id = id))
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun updateCoupon(coupon: Coupon): Result<Coupon> = try {
        val entity = couponToDatabaseEntity(coupon)
        couponDao.updateCoupon(entity)

        pendingChangeDao.insertPendingChange(
            PendingChangeEntity(
                userId = coupon.userId,
                changeType = "UPDATE",
                entityType = "COUPON",
                entityId = coupon.id,
                changeData = gson.toJson(coupon)
            )
        )

        Result.success(coupon)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun deleteCoupon(couponId: Long): Result<Unit> = try {
        val coupon = couponDao.getCouponById(couponId)
        couponDao.deleteCoupon(CouponEntity(id = couponId, userId = "", code = "", merchantName = "", discountType = "", discountValue = 0.0, expirationDate = 0))

        pendingChangeDao.insertPendingChange(
            PendingChangeEntity(
                userId = "",
                changeType = "DELETE",
                entityType = "COUPON",
                entityId = couponId,
                changeData = ""
            )
        )

        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override fun getUserCoupons(userId: String): Flow<List<Coupon>> =
        couponDao.getUserCoupons(userId).map { entities ->
            entities.map { databaseEntityToCoupon(it) }
        }

    override fun getCouponById(couponId: Long): Flow<Coupon?> =
        couponDao.getCouponById(couponId).map { entity ->
            entity?.let { databaseEntityToCoupon(it) }
        }

    override fun getFavoriteCoupons(userId: String): Flow<List<Coupon>> =
        couponDao.getFavoriteCoupons(userId).map { entities ->
            entities.map { databaseEntityToCoupon(it) }
        }

    override fun searchCoupons(userId: String, query: String): Flow<List<Coupon>> =
        couponDao.searchCouponsByMerchant(userId, query).map { entities ->
            entities.map { databaseEntityToCoupon(it) }
        }

    override fun getExpiringCoupons(userId: String, days: Int): Flow<List<Coupon>> {
        val now = System.currentTimeMillis()
        val daysFromNow = now + (days * 24 * 60 * 60 * 1000)
        return couponDao.getExpiringThisWeekCoupons(userId, now, daysFromNow).map { entities ->
            entities.map { databaseEntityToCoupon(it) }
        }
    }

    override fun getCouponsByCategory(userId: String, category: String): Flow<List<Coupon>> =
        couponDao.getCouponsByType(userId, category).map { entities ->
            entities.map { databaseEntityToCoupon(it) }
        }

    override suspend fun archiveCoupon(couponId: Long): Result<Unit> = try {
        couponDao.archiveCoupon(couponId)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun toggleFavorite(couponId: Long, isFavorite: Boolean): Result<Unit> = try {
        couponDao.updateCouponFavoriteStatus(couponId, isFavorite)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun updateCouponUsedStatus(couponId: Long, isUsed: Boolean): Result<Unit> = try {
        // This would need an additional DAO method
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun deleteExpiredCoupons(userId: String): Result<Unit> = try {
        couponDao.deleteExpiredCoupons(userId, System.currentTimeMillis())
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Sync a coupon to remote backend
     */
    suspend fun syncCouponToRemote(coupon: Coupon): Result<Coupon> = try {
        Log.d("CouponRepository", "Syncing coupon ${coupon.code} to remote")
        
        val remoteCoupon = couponToRemoteCoupon(coupon)
        val result = if (coupon.id == 0L) {
            apiService.createCoupon(remoteCoupon)
        } else {
            apiService.updateCoupon(coupon.id.toString(), remoteCoupon)
        }
        
        Log.d("CouponRepository", "Successfully synced coupon ${coupon.code}")
        Result.success(remoteCouponToCoupon(result))
    } catch (e: Exception) {
        Log.e("CouponRepository", "Failed to sync coupon: ${e.message}", e)
        Result.failure(e)
    }

    /**
     * Delete a coupon from remote backend
     */
    suspend fun deleteCouponFromRemote(couponId: Long, userId: String): Result<Unit> = try {
        Log.d("CouponRepository", "Deleting coupon $couponId from remote")
        apiService.deleteCoupon(couponId.toString())
        Log.d("CouponRepository", "Successfully deleted coupon from remote")
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e("CouponRepository", "Failed to delete coupon from remote: ${e.message}", e)
        Result.failure(e)
    }

    /**
     * Fetch coupons from remote backend
     */
    suspend fun fetchRemoteCoupons(userId: String): Result<List<Coupon>> = try {
        Log.d("CouponRepository", "Fetching coupons from remote for user $userId")
        val remoteCoupons = apiService.getUserCoupons(userId)
        val coupons = remoteCoupons.map { remoteCouponToCoupon(it) }
        Log.d("CouponRepository", "Fetched ${coupons.size} coupons from remote")
        Result.success(coupons)
    } catch (e: Exception) {
        Log.e("CouponRepository", "Failed to fetch remote coupons: ${e.message}", e)
        Result.failure(e)
    }

    private fun locationToDatabaseEntity(location: Location): CouponLocationEntity
        id = coupon.id,
        userId = coupon.userId,
        code = coupon.code,
        merchantName = coupon.merchantName,
        discountType = when (coupon.discountType) {
            is DiscountType.Percentage -> "PERCENTAGE"
            is DiscountType.FixedAmount -> "FIXED_AMOUNT"
            DiscountType.BuyOneGetOne -> "BOGO"
            DiscountType.FreeShipping -> "FREE_SHIPPING"
        },
        discountValue = coupon.discountValue,
        minPurchaseAmount = coupon.minPurchaseAmount,
        description = coupon.description,
        expirationDate = coupon.expirationDate,
        createdDate = coupon.createdDate,
        category = coupon.category,
        isFavorite = coupon.isFavorite,
        isArchived = coupon.isArchived,
        imageUrl = coupon.imageUrl,
        barcodeData = coupon.barcodeData,
        notes = coupon.notes,
        lastModified = coupon.lastModified
    )

    private fun databaseEntityToCoupon(entity: CouponEntity): Coupon = Coupon(
        id = entity.id,
        userId = entity.userId,
        code = entity.code,
        merchantName = entity.merchantName,
        discountType = when (entity.discountType) {
            "PERCENTAGE" -> DiscountType.Percentage(entity.discountValue)
            "FIXED_AMOUNT" -> DiscountType.FixedAmount(entity.discountValue, entity.discountValueCurrency ?: "USD")
            "BOGO" -> DiscountType.BuyOneGetOne
            "FREE_SHIPPING" -> DiscountType.FreeShipping
            else -> DiscountType.Percentage(entity.discountValue)
        },
        discountValue = entity.discountValue,
        minPurchaseAmount = entity.minPurchaseAmount,
        description = entity.description,
        expirationDate = entity.expirationDate,
        createdDate = entity.createdDate,
        category = entity.category,
        isFavorite = entity.isFavorite,
        isArchived = entity.isArchived,
        imageUrl = entity.imageUrl,
        barcodeData = entity.barcodeData,
        notes = entity.notes,
        lastModified = entity.lastModified
    )

    /**
     * Convert domain coupon to remote coupon model
     */
    private fun couponToRemoteCoupon(coupon: Coupon): com.promocodeapp.data.api.SupabaseCoupon {
        val discountType = when (coupon.discountType) {
            is DiscountType.Percentage -> "PERCENTAGE"
            is DiscountType.FixedAmount -> "FIXED_AMOUNT"
            DiscountType.BuyOneGetOne -> "BOGO"
            DiscountType.FreeShipping -> "FREE_SHIPPING"
        }
        
        val currency = when (coupon.discountType) {
            is DiscountType.FixedAmount -> coupon.discountType.currency
            else -> "USD"
        }

        return com.promocodeapp.data.api.SupabaseCoupon(
            id = coupon.id.toString(),
            userId = coupon.userId,
            code = coupon.code,
            merchantName = coupon.merchantName,
            discountType = discountType,
            discountValue = coupon.discountValue,
            discountValueCurrency = currency,
            minPurchaseAmount = coupon.minPurchaseAmount,
            description = coupon.description,
            expirationDate = coupon.expirationDate,
            createdDate = coupon.createdDate,
            category = coupon.category,
            isFavorite = coupon.isFavorite,
            imageUrl = coupon.imageUrl,
            barcodeData = coupon.barcodeData,
            notes = coupon.notes,
            lastModified = coupon.lastModified
        )
    }

    /**
     * Convert remote coupon to domain coupon model
     */
    private fun remoteCouponToCoupon(remote: com.promocodeapp.data.api.SupabaseCoupon): Coupon = Coupon(
        id = remote.id.toLongOrNull() ?: 0,
        userId = remote.userId,
        code = remote.code,
        merchantName = remote.merchantName,
        discountType = when (remote.discountType) {
            "PERCENTAGE" -> DiscountType.Percentage(remote.discountValue)
            "FIXED_AMOUNT" -> DiscountType.FixedAmount(remote.discountValue, remote.discountValueCurrency ?: "USD")
            "BOGO" -> DiscountType.BuyOneGetOne
            "FREE_SHIPPING" -> DiscountType.FreeShipping
            else -> DiscountType.Percentage(remote.discountValue)
        },
        discountValue = remote.discountValue,
        minPurchaseAmount = remote.minPurchaseAmount,
        description = remote.description,
        expirationDate = remote.expirationDate,
        createdDate = remote.createdDate,
        category = remote.category,
        isFavorite = remote.isFavorite,
        isArchived = remote.isArchived,
        imageUrl = remote.imageUrl,
        barcodeData = remote.barcodeData,
        notes = remote.notes,
        lastModified = remote.lastModified,
        locations = emptyList() // Locations will be loaded separately
    )
}

    private fun locationToDatabaseEntity(location: Location): CouponLocationEntity = CouponLocationEntity(
        id = location.id,
        couponId = location.couponId ?: 0,
        userId = location.userId,
        latitude = location.latitude,
        longitude = location.longitude,
        radius = location.radius,
        geofenceId = location.geofenceId,
        locationName = location.locationName,
        createdDate = location.createdDate
    )
}
