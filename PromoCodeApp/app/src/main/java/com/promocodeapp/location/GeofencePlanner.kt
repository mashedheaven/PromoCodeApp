package com.promocodeapp.location

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLng(val latitude: Double, val longitude: Double) {
    /** Great-circle distance in metres (haversine). */
    fun distanceTo(other: LatLng): Double {
        val dLat = Math.toRadians(other.latitude - latitude)
        val dLng = Math.toRadians(other.longitude - longitude)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(latitude)) * cos(Math.toRadians(other.latitude)) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    private companion object {
        const val EARTH_RADIUS_M = 6_371_000.0
    }
}

data class Fence(val id: String, val center: LatLng, val radiusMeters: Float) {
    /** Changes whenever the fence must be re-registered. */
    val signature: String get() = "${center.latitude},${center.longitude},$radiusMeters"
}

data class GeofencePlan(
    val fences: List<Fence>,
    /**
     * Present only when there are more places than Android lets one app
     * monitor. Leaving this area means the nearest set must be recomputed.
     */
    val refreshFence: Fence? = null,
)

object GeofencePlanner {
    /** Android allows at most 100 geofences per app. */
    const val MAX_GEOFENCES = 100
    const val REFRESH_FENCE_ID = "__refresh__"
    private const val MIN_REFRESH_RADIUS_M = 500.0

    fun plan(places: List<Fence>, origin: LatLng?, limit: Int = MAX_GEOFENCES): GeofencePlan {
        if (places.size <= limit) return GeofencePlan(places)
        // Without a position we cannot rank by distance; watch as many as allowed.
        if (origin == null) return GeofencePlan(places.take(limit))

        val byDistance = places.sortedBy { edgeDistance(origin, it) }
        val nearest = byDistance.take(limit - 1)
        val firstExcluded = byDistance[limit - 1]
        // Staying within half the gap to the closest unmonitored place guarantees
        // we re-plan before the user can reach it.
        val radius = max(MIN_REFRESH_RADIUS_M, edgeDistance(origin, firstExcluded) / 2)
        return GeofencePlan(nearest, Fence(REFRESH_FENCE_ID, origin, radius.toFloat()))
    }

    private fun edgeDistance(origin: LatLng, fence: Fence): Double =
        max(0.0, origin.distanceTo(fence.center) - fence.radiusMeters)
}
