package com.promocodeapp.data.repository

import androidx.room.withTransaction
import com.promocodeapp.core.model.Coupon
import com.promocodeapp.core.model.Expiry
import com.promocodeapp.core.model.Membership
import com.promocodeapp.core.model.Place
import com.promocodeapp.core.util.today
import com.promocodeapp.data.local.AppDatabase
import com.promocodeapp.data.local.toEntity
import com.promocodeapp.data.local.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** Told whenever local data changes so pending edits get uploaded. */
fun interface LocalChangeListener {
    fun onLocalChange()
}

@Singleton
class CouponRepository @Inject constructor(
    private val db: AppDatabase,
    private val clock: Clock,
    private val changes: LocalChangeListener,
) {
    private val dao = db.couponDao()

    fun observeAll(): Flow<List<Coupon>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    fun observe(id: String): Flow<Coupon?> = dao.observe(id).map { it?.toModel() }

    suspend fun get(id: String): Coupon? = dao.get(id)?.takeIf { it.deletedAt == null }?.toModel()

    /** Creates or replaces a coupon, stamping the modification time. */
    suspend fun save(coupon: Coupon) {
        val existing = dao.get(coupon.id)
        dao.upsert(coupon.copy(updatedAt = clock.millis()).toEntity(existing))
        changes.onLocalChange()
    }

    suspend fun setFavorite(id: String, favorite: Boolean) = edit(id) { it.copy(isFavorite = favorite) }

    suspend fun setUsed(id: String, used: Boolean) =
        edit(id) { it.copy(usedAt = if (used) clock.millis() else null) }

    suspend fun setArchived(id: String, archived: Boolean) = edit(id) { it.copy(isArchived = archived) }

    suspend fun delete(id: String) {
        val existing = dao.get(id) ?: return
        val now = clock.millis()
        dao.upsert(existing.copy(deletedAt = now, updatedAt = now, dirty = true))
        changes.onLocalChange()
    }

    /** Archives coupons that expired more than [graceDays] ago. Returns how many. */
    suspend fun archiveExpired(graceDays: Long): Int {
        val today = clock.today()
        val stale = dao.getAll().map { it.toModel() }.filter { c ->
            !c.isArchived && (c.expiry(today) as? Expiry.Expired)?.let { it.daysAgo > graceDays } == true
        }
        stale.forEach { save(it.copy(isArchived = true)) }
        return stale.size
    }

    private suspend fun edit(id: String, transform: (Coupon) -> Coupon) {
        val current = get(id) ?: return
        save(transform(current))
    }
}

@Singleton
class PlaceRepository @Inject constructor(
    private val db: AppDatabase,
    private val clock: Clock,
    private val changes: LocalChangeListener,
) {
    private val dao = db.placeDao()

    fun observeAll(): Flow<List<Place>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    fun observe(id: String): Flow<Place?> = dao.observe(id).map { it?.toModel() }

    suspend fun get(id: String): Place? = dao.get(id)?.takeIf { it.deletedAt == null }?.toModel()

    suspend fun save(place: Place) {
        dao.upsert(place.copy(updatedAt = clock.millis()).toEntity())
        changes.onLocalChange()
    }

    /** Deletes a place and unlinks it from every coupon and membership. */
    suspend fun delete(id: String) {
        val now = clock.millis()
        db.withTransaction {
            val existing = dao.get(id) ?: return@withTransaction
            dao.upsert(existing.copy(deletedAt = now, updatedAt = now, dirty = true))
            db.couponDao().getAll().filter { id in it.placeIds }.forEach {
                db.couponDao().upsert(it.copy(placeIds = it.placeIds - id, updatedAt = now, dirty = true))
            }
            db.membershipDao().getAll().filter { id in it.placeIds }.forEach {
                db.membershipDao().upsert(it.copy(placeIds = it.placeIds - id, updatedAt = now, dirty = true))
            }
            db.placeAlertStateDao().deleteOrphans()
        }
        changes.onLocalChange()
    }
}

@Singleton
class MembershipRepository @Inject constructor(
    private val db: AppDatabase,
    private val clock: Clock,
    private val changes: LocalChangeListener,
) {
    private val dao = db.membershipDao()

    fun observeAll(): Flow<List<Membership>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    fun observe(id: String): Flow<Membership?> = dao.observe(id).map { it?.toModel() }

    suspend fun get(id: String): Membership? = dao.get(id)?.takeIf { it.deletedAt == null }?.toModel()

    suspend fun save(membership: Membership) {
        val existing = dao.get(membership.id)
        dao.upsert(membership.copy(updatedAt = clock.millis()).toEntity(existing))
        changes.onLocalChange()
    }

    suspend fun delete(id: String) {
        val existing = dao.get(id) ?: return
        val now = clock.millis()
        dao.upsert(existing.copy(deletedAt = now, updatedAt = now, dirty = true))
        changes.onLocalChange()
    }
}
