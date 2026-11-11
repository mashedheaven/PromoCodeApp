@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.promocodeapp.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class GeocodedAddress(val label: String, val position: LatLng)

@Singleton
class LocationSource @Inject constructor(@ApplicationContext private val context: Context) {
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(context) }

    /** Cheap, possibly stale position. Null without permission or a cached fix. */
    @SuppressLint("MissingPermission")
    suspend fun lastKnown(): LatLng? {
        if (!Permissions.hasAnyLocation(context)) return null
        return runCatching { fused.lastLocation.await() }.getOrNull()?.let { LatLng(it.latitude, it.longitude) }
    }

    /** Fresh, accurate position for pinning a store. Null if unavailable. */
    @SuppressLint("MissingPermission")
    suspend fun current(): LatLng? {
        if (!Permissions.hasAnyLocation(context)) return null
        val cancel = CancellationTokenSource()
        val fix = withTimeoutOrNull(15_000) {
            runCatching {
                fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancel.token).await(cancel)
            }.getOrNull()
        }
        return fix?.let { LatLng(it.latitude, it.longitude) } ?: lastKnown()
    }

    /** Looks up addresses by text. Empty when offline or nothing matches. */
    suspend fun search(query: String, max: Int = 5): List<GeocodedAddress> {
        if (query.isBlank() || !Geocoder.isPresent()) return emptyList()
        return geocode { geocoder, callback ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocationName(query, max, listener(callback))
            } else {
                @Suppress("DEPRECATION")
                callback(geocoder.getFromLocationName(query, max).orEmpty())
            }
        }.map { GeocodedAddress(it.label(), LatLng(it.latitude, it.longitude)) }
    }

    /** Street address for a position, or null when offline. */
    suspend fun describe(position: LatLng): String? {
        if (!Geocoder.isPresent()) return null
        return geocode { geocoder, callback ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(position.latitude, position.longitude, 1, listener(callback))
            } else {
                @Suppress("DEPRECATION")
                callback(geocoder.getFromLocation(position.latitude, position.longitude, 1).orEmpty())
            }
        }.firstOrNull()?.label()
    }

    private fun listener(callback: (List<Address>) -> Unit) =
        @androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
        object : Geocoder.GeocodeListener {
            override fun onGeocode(addresses: MutableList<Address>) = callback(addresses)
            override fun onError(errorMessage: String?) = callback(emptyList())
        }

    private suspend fun geocode(
        request: (Geocoder, (List<Address>) -> Unit) -> Unit,
    ): List<Address> = withContext(Dispatchers.IO) {
        withTimeoutOrNull(10_000) {
            suspendCancellableCoroutine { continuation ->
                try {
                    request(Geocoder(context)) { if (continuation.isActive) continuation.resume(it) }
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resume(emptyList())
                }
            }
        } ?: emptyList()
    }

    private fun Address.label(): String =
        getAddressLine(0) ?: listOfNotNull(featureName, locality, countryName).joinToString(", ")
}
