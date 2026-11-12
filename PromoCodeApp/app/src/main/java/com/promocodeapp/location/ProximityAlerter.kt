package com.promocodeapp.location

import com.promocodeapp.core.util.today
import com.promocodeapp.data.local.AppDatabase
import com.promocodeapp.data.local.PlaceAlertStateEntity
import com.promocodeapp.data.repository.CouponRepository
import com.promocodeapp.data.repository.MembershipRepository
import com.promocodeapp.data.repository.PlaceRepository
import com.promocodeapp.data.settings.SettingsStore
import com.promocodeapp.notifications.Notifier
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Decides whether arriving at a place deserves a notification. */
@Singleton
class ProximityAlerter @Inject constructor(
    private val db: AppDatabase,
    private val places: PlaceRepository,
    private val coupons: CouponRepository,
    private val memberships: MembershipRepository,
    private val settings: SettingsStore,
    private val notifier: Notifier,
    private val clock: Clock,
) {
    private val states get() = db.placeAlertStateDao()

    /** @return true if a notification was shown. */
    suspend fun onEnter(placeId: String): Boolean {
        val now = clock.millis()
        val state = states.get(placeId)
        // Re-registering geofences re-fires ENTER for places we are still in.
        // An EXIT can be lost (e.g. location switched off while inside), so a
        // stale "inside" is ignored; daily re-registration keeps a real one fresh.
        if (state?.inside == true && now - state.updatedAt < INSIDE_STALE_MS) {
            states.upsert(state.copy(updatedAt = now))
            return false
        }

        val shown = shouldNotify(state, now) && show(placeId)
        states.upsert(
            PlaceAlertStateEntity(placeId, inside = true, lastNotifiedAt = if (shown) now else state?.lastNotifiedAt ?: 0, updatedAt = now)
        )
        return shown
    }

    suspend fun onExit(placeId: String) {
        val state = states.get(placeId)
        states.upsert(PlaceAlertStateEntity(placeId, inside = false, lastNotifiedAt = state?.lastNotifiedAt ?: 0, updatedAt = clock.millis()))
        // The reminder is only useful while the user is actually there.
        notifier.cancelNearby(placeId)
    }

    private suspend fun shouldNotify(state: PlaceAlertStateEntity?, now: Long): Boolean {
        if (!settings.current().nearbyAlerts) return false
        // GPS jitter at a fence edge, or living next to a store, must not spam.
        return state == null || now - state.lastNotifiedAt >= COOLDOWN_MS
    }

    private suspend fun show(placeId: String): Boolean {
        val place = places.get(placeId) ?: return false
        val today = clock.today()
        val usable = coupons.observeAll().first().filter { placeId in it.placeIds && it.isRedeemable(today) }
            .sortedBy { it.expiresOn ?: LocalDate.MAX }
        val valid = memberships.observeAll().first().filter { placeId in it.placeIds && it.isActive }
        if (usable.isEmpty() && valid.isEmpty()) return false
        return notifier.showNearby(place, usable, valid)
    }

    companion object {
        const val COOLDOWN_MS = 2 * 60 * 60 * 1000L
        const val INSIDE_STALE_MS = 36 * 60 * 60 * 1000L
    }
}
