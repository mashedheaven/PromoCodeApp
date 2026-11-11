package com.promocodeapp.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface CouponDao {
    @Query("SELECT * FROM coupons WHERE deletedAt IS NULL")
    fun observeAll(): Flow<List<CouponEntity>>

    @Query("SELECT * FROM coupons WHERE id = :id AND deletedAt IS NULL")
    fun observe(id: String): Flow<CouponEntity?>

    @Query("SELECT * FROM coupons WHERE deletedAt IS NULL")
    suspend fun getAll(): List<CouponEntity>

    @Query("SELECT * FROM coupons WHERE id = :id")
    suspend fun get(id: String): CouponEntity?

    @Upsert
    suspend fun upsert(entity: CouponEntity)

    @Upsert
    suspend fun upsertAll(entities: List<CouponEntity>)

    @Query("UPDATE coupons SET reminderSentFor = :date WHERE id = :id")
    suspend fun setReminderSentFor(id: String, date: LocalDate?)

    // --- sync ---
    @Query("SELECT * FROM coupons WHERE dirty = 1 LIMIT :limit")
    suspend fun dirty(limit: Int): List<CouponEntity>

    @Query("UPDATE coupons SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun clearDirty(id: String, updatedAt: Long)

    @Query("UPDATE coupons SET dirty = 1")
    suspend fun markAllDirty()

    @Query("DELETE FROM coupons WHERE deletedAt IS NOT NULL AND dirty = 0")
    suspend fun purgeSyncedTombstones()

    @Query("DELETE FROM coupons WHERE deletedAt IS NOT NULL")
    suspend fun purgeAllTombstones()

    @Query("DELETE FROM coupons")
    suspend fun clear()
}

@Dao
interface PlaceDao {
    @Query("SELECT * FROM places WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<PlaceEntity>>

    @Query("SELECT * FROM places WHERE id = :id AND deletedAt IS NULL")
    fun observe(id: String): Flow<PlaceEntity?>

    @Query("SELECT * FROM places WHERE deletedAt IS NULL")
    suspend fun getAll(): List<PlaceEntity>

    @Query("SELECT * FROM places WHERE id = :id")
    suspend fun get(id: String): PlaceEntity?

    @Upsert
    suspend fun upsert(entity: PlaceEntity)

    @Upsert
    suspend fun upsertAll(entities: List<PlaceEntity>)

    @Query("SELECT * FROM places WHERE dirty = 1 LIMIT :limit")
    suspend fun dirty(limit: Int): List<PlaceEntity>

    @Query("UPDATE places SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun clearDirty(id: String, updatedAt: Long)

    @Query("UPDATE places SET dirty = 1")
    suspend fun markAllDirty()

    @Query("DELETE FROM places WHERE deletedAt IS NOT NULL AND dirty = 0")
    suspend fun purgeSyncedTombstones()

    @Query("DELETE FROM places WHERE deletedAt IS NOT NULL")
    suspend fun purgeAllTombstones()

    @Query("DELETE FROM places")
    suspend fun clear()
}

@Dao
interface MembershipDao {
    @Query("SELECT * FROM memberships WHERE deletedAt IS NULL ORDER BY organization COLLATE NOCASE")
    fun observeAll(): Flow<List<MembershipEntity>>

    @Query("SELECT * FROM memberships WHERE id = :id AND deletedAt IS NULL")
    fun observe(id: String): Flow<MembershipEntity?>

    @Query("SELECT * FROM memberships WHERE deletedAt IS NULL")
    suspend fun getAll(): List<MembershipEntity>

    @Query("SELECT * FROM memberships WHERE id = :id")
    suspend fun get(id: String): MembershipEntity?

    @Upsert
    suspend fun upsert(entity: MembershipEntity)

    @Upsert
    suspend fun upsertAll(entities: List<MembershipEntity>)

    @Query("UPDATE memberships SET reminderSentFor = :date WHERE id = :id")
    suspend fun setReminderSentFor(id: String, date: LocalDate?)

    @Query("SELECT * FROM memberships WHERE dirty = 1 LIMIT :limit")
    suspend fun dirty(limit: Int): List<MembershipEntity>

    @Query("UPDATE memberships SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun clearDirty(id: String, updatedAt: Long)

    @Query("UPDATE memberships SET dirty = 1")
    suspend fun markAllDirty()

    @Query("DELETE FROM memberships WHERE deletedAt IS NOT NULL AND dirty = 0")
    suspend fun purgeSyncedTombstones()

    @Query("DELETE FROM memberships WHERE deletedAt IS NOT NULL")
    suspend fun purgeAllTombstones()

    @Query("DELETE FROM memberships")
    suspend fun clear()
}

@Dao
interface PlaceAlertStateDao {
    @Query("SELECT * FROM place_alert_state WHERE placeId = :placeId")
    suspend fun get(placeId: String): PlaceAlertStateEntity?

    @Upsert
    suspend fun upsert(state: PlaceAlertStateEntity)

    @Query("DELETE FROM place_alert_state WHERE placeId NOT IN (SELECT id FROM places WHERE deletedAt IS NULL)")
    suspend fun deleteOrphans()

    @Query("DELETE FROM place_alert_state")
    suspend fun clear()
}
