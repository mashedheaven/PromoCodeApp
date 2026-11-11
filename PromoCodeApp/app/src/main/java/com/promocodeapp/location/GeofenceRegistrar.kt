package com.promocodeapp.location

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.promocodeapp.core.model.Coupon
import com.promocodeapp.core.model.Membership
import com.promocodeapp.core.model.Place
import com.promocodeapp.core.util.today
import com.promocodeapp.data.repository.CouponRepository
import com.promocodeapp.data.repository.MembershipRepository
import com.promocodeapp.data.repository.PlaceRepository
import com.promocodeapp.data.settings.SettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Places worth watching: those with something redeemable attached. */
fun placesToWatch(
    places: List<Place>,
    coupons: List<Coupon>,
    memberships: List<Membership>,
    today: LocalDate,
): List<Place> {
    val wanted = HashSet<String>()
    coupons.filter { it.isRedeemable(today) }.forEach { wanted += it.placeIds }
    memberships.filter { it.isActive }.forEach { wanted += it.placeIds }
    return places.filter { it.id in wanted }
}

/**
 * Keeps the geofences registered with Google Play services in step with the
 * database. Safe to call as often as needed: only differences are applied.
 */
@Singleton
class GeofenceRegistrar @Inject constructor(
    @ApplicationContext private val context: Context,
    private val places: PlaceRepository,
    private val coupons: CouponRepository,
    private val memberships: MembershipRepository,
    private val settings: SettingsStore,
    private val locations: LocationSource,
    private val clock: Clock,
) {
    private val client by lazy { LocationServices.getGeofencingClient(context) }
    private val mutex = Mutex()

    private val pendingIntent: PendingIntent by lazy {
        // Play services fills in the transition details, so the intent must be mutable.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        PendingIntent.getBroadcast(context, 0, Intent(context, GeofenceBroadcastReceiver::class.java), flags)
    }

    /** Emits whenever the set of geofences that should exist may have changed. */
    fun desiredFences(): Flow<List<Fence>> = combine(
        places.observeAll(), coupons.observeAll(), memberships.observeAll(), settings.settings,
    ) { p, c, m, s ->
        if (!s.nearbyAlerts) emptyList() else placesToWatch(p, c, m, clock.today()).map { it.toFence() }
    }.distinctUntilChanged()

    /**
     * @param origin a fresh position, when the caller has one.
     * @param force re-register everything, e.g. after a reboot wiped the system's list.
     * @return true if the registered geofences now match the database.
     */
    @SuppressLint("MissingPermission")
    suspend fun sync(origin: LatLng? = null, force: Boolean = false): Boolean = mutex.withLock {
        val registered = if (force) emptyMap() else settings.registeredGeofences()
        try {
            if (!Permissions.canGeofence(context)) {
                // Without permission the system has already dropped our geofences.
                settings.setRegisteredGeofences(emptyMap())
                return@withLock false
            }
            val wanted = desiredFences().first()
            val position = origin ?: if (wanted.size > GeofencePlanner.MAX_GEOFENCES) locations.lastKnown() else null
            val plan = GeofencePlanner.plan(wanted, position)
            val all = plan.fences + listOfNotNull(plan.refreshFence)
            val desired = all.associate { it.id to it.signature }

            val stale = (settings.registeredGeofences().keys - desired.keys).toList()
            if (stale.isNotEmpty()) client.removeGeofences(stale).await()

            val changed = all.filter { registered[it.id] != it.signature }
            // The API takes at most 100 per request, which is also the total cap.
            if (changed.isNotEmpty()) {
                val request = GeofencingRequest.Builder()
                    // Fire straight away if the user is already inside a new fence.
                    .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                    .addGeofences(changed.map { it.toGeofence() })
                    .build()
                client.addGeofences(request, pendingIntent).await()
            }
            settings.setRegisteredGeofences(desired)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Typically location is switched off. Forget what we think is
            // registered so the next attempt starts from scratch.
            Log.w(TAG, "Geofence sync failed", e)
            settings.setRegisteredGeofences(emptyMap())
            false
        }
    }

    /** The system reported that geofencing stopped (e.g. location turned off). */
    suspend fun onGeofencingUnavailable() {
        settings.setRegisteredGeofences(emptyMap())
    }

    private fun Place.toFence() = Fence(
        id = id,
        center = LatLng(latitude, longitude),
        radiusMeters = radiusMeters.coerceAtLeast(Place.MIN_RADIUS_METERS).toFloat(),
    )

    private fun Fence.toGeofence(): Geofence {
        val isRefresh = id == GeofencePlanner.REFRESH_FENCE_ID
        return Geofence.Builder()
            .setRequestId(id)
            .setCircularRegion(center.latitude, center.longitude, radiusMeters)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(
                if (isRefresh) Geofence.GEOFENCE_TRANSITION_EXIT
                else Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT
            )
            // Batching location checks saves battery; a store visit outlasts a minute.
            .setNotificationResponsiveness(RESPONSIVENESS_MS)
            .build()
    }

    private companion object {
        const val TAG = "GeofenceRegistrar"
        const val RESPONSIVENESS_MS = 60_000
    }
}
