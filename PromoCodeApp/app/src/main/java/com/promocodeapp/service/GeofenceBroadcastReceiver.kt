package com.promocodeapp.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

class GeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        intent ?: return

        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return

        if (geofencingEvent.hasError()) {
            Log.e(TAG, "Geofencing error: ${geofencingEvent.errorCode}")
            return
        }

        val transitionType = geofencingEvent.geofenceTransition

        when (transitionType) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                handleEnterGeofence(context, geofencingEvent)
            }
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                handleExitGeofence(context, geofencingEvent)
            }
        }
    }

    private fun handleEnterGeofence(context: Context, event: GeofencingEvent) {
        val triggeringGeofences = event.triggeringGeofences

        Log.d(TAG, "Entered geofence: ${triggeringGeofences.map { it.requestId }}")

        // For each geofence, create a notification showing available coupons
        triggeringGeofences.forEach { geofence ->
            // Extract coupon IDs from geofence ID (format: "coupon_${couponId}")
            val couponId = geofence.requestId.substringAfter("coupon_").toLongOrNull()
            if (couponId != null) {
                sendProximityNotification(context, couponId, geofence.requestId)
            }
        }
    }

    private fun handleExitGeofence(context: Context, event: GeofencingEvent) {
        val triggeringGeofences = event.triggeringGeofences

        Log.d(TAG, "Exited geofence: ${triggeringGeofences.map { it.requestId }}")

        // You could send a reminder notification here
        triggeringGeofences.forEach { geofence ->
            Log.d(TAG, "You've exited ${geofence.requestId}")
        }
    }

    private fun sendProximityNotification(context: Context, couponId: Long, geofenceId: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        if (notificationManager != null) {
            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra("coupon_id", couponId)
                putExtra("notification_type", "proximity")
            }

            val pendingIntent = android.app.PendingIntent.getActivity(
                context, couponId.toInt(), intent,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = androidx.core.app.NotificationCompat.Builder(context, "proximity_alerts")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("You're near a store with available coupons!")
                .setContentText("Tap to view your coupons")
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .build()

            notificationManager.notify(couponId.toInt(), notification)
        }
    }

    companion object {
        private const val TAG = "GeofenceBroadcastReceiver"
    }
}

// Placeholder for MainActivity - will be created in the next step
internal class MainActivity
