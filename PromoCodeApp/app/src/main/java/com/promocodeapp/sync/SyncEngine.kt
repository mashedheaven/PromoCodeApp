package com.promocodeapp.sync

import com.promocodeapp.data.local.AppDatabase
import com.promocodeapp.data.remote.DeviceTokenDto
import com.promocodeapp.data.remote.RemoteRow
import com.promocodeapp.data.remote.RestApi
import com.promocodeapp.data.remote.SessionStore
import com.promocodeapp.data.remote.SupabaseConfig
import com.promocodeapp.data.remote.toDto
import com.promocodeapp.data.remote.toEntity
import com.promocodeapp.data.settings.SettingsStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

enum class SyncOutcome { SYNCED, SKIPPED }

/**
 * Two-way sync between Room and Supabase.
 *
 * Push: rows flagged `dirty` are upserted; the server keeps whichever version
 * has the newer `updated_at` (last write wins). The flag is cleared only if the
 * row was not edited again while the upload was in flight.
 *
 * Pull: rows changed on the server since the saved cursor are applied unless a
 * newer local edit is still waiting to be pushed. Deletions travel as
 * tombstones (`deleted_at`) so other devices learn about them.
 */
@Singleton
class SyncEngine @Inject constructor(
    private val config: SupabaseConfig,
    private val sessions: SessionStore,
    private val api: RestApi,
    private val db: AppDatabase,
    private val settings: SettingsStore,
    private val clock: Clock,
) {
    private val mutex = Mutex()

    /** @throws Exception on network or server errors; callers decide whether to retry. */
    suspend fun sync(): SyncOutcome = mutex.withLock {
        if (!config.isConfigured || sessions.session.value == null) return SyncOutcome.SKIPPED
        // Places first, so coupons arriving in the same run can resolve them.
        tables().forEach { table ->
            push(table)
            pull(table)
            table.purgeSyncedTombstones()
        }
        db.placeAlertStateDao().deleteOrphans()
        settings.fcmToken()?.let { token -> runCatching { api.registerDeviceToken(DeviceTokenDto(token)) } }
        settings.setLastSyncAt(clock.millis())
        SyncOutcome.SYNCED
    }

    private suspend fun <E, D : RemoteRow> push(table: Table<E, D>) {
        while (true) {
            val batch = table.dirty(PAGE)
            if (batch.isEmpty()) return
            table.push(batch.map(table.toDto))
            batch.forEach { table.clearDirty(table.id(it), table.updatedAt(it)) }
            if (batch.size < PAGE) return
        }
    }

    private suspend fun <E, D : RemoteRow> pull(table: Table<E, D>) {
        var cursor = settings.syncCursor(table.name)
        while (true) {
            val page = table.pull(cursor?.let { "gt.$it" }, PAGE)
            if (page.isEmpty()) return
            val accepted = page.mapNotNull { remote ->
                val local = table.get(remote.id)
                val localWins = local != null && table.isDirty(local) && table.updatedAt(local) > remote.updatedAt
                if (localWins) null else table.fromDto(remote, local)
            }
            table.upsertAll(accepted)
            cursor = page.last().serverUpdatedAt ?: return
            settings.setSyncCursor(table.name, cursor)
            if (page.size < PAGE) return
        }
    }

    private class Table<E, D : RemoteRow>(
        val name: String,
        val dirty: suspend (Int) -> List<E>,
        val get: suspend (String) -> E?,
        val upsertAll: suspend (List<E>) -> Unit,
        val clearDirty: suspend (String, Long) -> Unit,
        val purgeSyncedTombstones: suspend () -> Unit,
        val push: suspend (List<D>) -> Unit,
        val pull: suspend (String?, Int) -> List<D>,
        val toDto: (E) -> D,
        val fromDto: (D, E?) -> E,
        val id: (E) -> String,
        val updatedAt: (E) -> Long,
        val isDirty: (E) -> Boolean,
    )

    private fun tables(): List<Table<*, *>> {
        val places = db.placeDao()
        val coupons = db.couponDao()
        val memberships = db.membershipDao()
        return listOf(
            Table(
                "places", places::dirty, places::get, places::upsertAll, places::clearDirty,
                places::purgeSyncedTombstones, api::pushPlaces, { after, limit -> api.pullPlaces(after, limit) },
                { it.toDto() }, { dto, _ -> dto.toEntity() }, { it.id }, { it.updatedAt }, { it.dirty },
            ),
            Table(
                "coupons", coupons::dirty, coupons::get, coupons::upsertAll, coupons::clearDirty,
                coupons::purgeSyncedTombstones, api::pushCoupons, { after, limit -> api.pullCoupons(after, limit) },
                { it.toDto() }, { dto, existing -> dto.toEntity(existing) }, { it.id }, { it.updatedAt }, { it.dirty },
            ),
            Table(
                "memberships", memberships::dirty, memberships::get, memberships::upsertAll, memberships::clearDirty,
                memberships::purgeSyncedTombstones, api::pushMemberships,
                { after, limit -> api.pullMemberships(after, limit) },
                { it.toDto() }, { dto, existing -> dto.toEntity(existing) }, { it.id }, { it.updatedAt }, { it.dirty },
            ),
        )
    }

    private companion object {
        const val PAGE = 200
    }
}
