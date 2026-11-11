package com.promocodeapp.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import com.promocodeapp.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** Runs [block] past onReceive's return, within the ~10 s a receiver is allowed. */
private fun BroadcastReceiver.async(scope: CoroutineScope, block: suspend () -> Unit) {
    val pending = goAsync()
    scope.launch {
        try {
            withTimeoutOrNull(9_000) { block() }
        } catch (e: Exception) {
            Log.w("Receivers", "Background handler failed", e)
        } finally {
            pending.finish()
        }
    }
}

@AndroidEntryPoint
class GeofenceBroadcastReceiver : BroadcastReceiver() {
    @Inject lateinit var alerter: ProximityAlerter
    @Inject lateinit var registrar: GeofenceRegistrar
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            Log.w(TAG, "Geofencing error: ${GeofenceStatusCodes.getStatusCodeString(event.errorCode)}")
            if (event.errorCode == GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE) {
                async(scope) { registrar.onGeofencingUnavailable() }
            }
            return
        }
        val ids = event.triggeringGeofences.orEmpty().map { it.requestId }
        val transition = event.geofenceTransition
        val position = event.triggeringLocation?.let { LatLng(it.latitude, it.longitude) }

        async(scope) {
            for (id in ids) {
                when {
                    id == GeofencePlanner.REFRESH_FENCE_ID -> registrar.sync(origin = position)
                    transition == Geofence.GEOFENCE_TRANSITION_ENTER -> alerter.onEnter(id)
                    transition == Geofence.GEOFENCE_TRANSITION_EXIT -> alerter.onExit(id)
                }
            }
        }
    }

    private companion object {
        const val TAG = "GeofenceReceiver"
    }
}

/** The system forgets geofences on reboot and on app update; put them back. */
@AndroidEntryPoint
class RestoreGeofencesReceiver : BroadcastReceiver() {
    @Inject lateinit var registrar: GeofenceRegistrar
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        async(scope) { registrar.sync(force = true) }
    }
}
