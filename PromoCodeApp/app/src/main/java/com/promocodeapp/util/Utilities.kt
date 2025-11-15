package com.promocodeapp.util

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.promocodeapp.service.GeofenceBroadcastReceiver
import kotlinx.coroutines.tasks.await

class GeofenceManager(private val context: Context) {
    private val geofencingClient = LocationServices.getGeofencingClient(context)

    private fun getPendingIntent(): PendingIntent {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    PendingIntent.FLAG_IMMUTABLE
                else
                    0
            )
        )
    }

    suspend fun addGeofences(geofences: List<Geofence>): Result<Unit> = try {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            return Result.failure(SecurityException("Location permission not granted"))
        }

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(geofences)
            .build()

        geofencingClient.addGeofences(request, getPendingIntent()).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun removeGeofences(geofenceIds: List<String>): Result<Unit> = try {
        geofencingClient.removeGeofences(geofenceIds).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun createGeofence(
        id: String,
        latitude: Double,
        longitude: Double,
        radiusInMeters: Float = 150f,
        transitionTypes: Int = Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT
    ): Geofence = Geofence.Builder()
        .setRequestId(id)
        .setCircularRegion(latitude, longitude, radiusInMeters)
        .setTransitionTypes(transitionTypes)
        .setExpirationDuration(Geofence.NEVER_EXPIRE)
        .build()
}

data class LocationPoint(
    val latitude: Double,
    val longitude: Double
) {
    fun distanceTo(other: LocationPoint): Float {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            latitude, longitude,
            other.latitude, other.longitude,
            results
        )
        return results[0]
    }

    fun isWithinRadius(other: LocationPoint, radiusInMeters: Float): Boolean {
        return distanceTo(other) <= radiusInMeters
    }
}

object DateUtils {
    fun getMillisFromNow(days: Int): Long {
        return System.currentTimeMillis() + (days * 24 * 60 * 60 * 1000L)
    }

    fun daysUntil(timeInMillis: Long): Long {
        val diff = timeInMillis - System.currentTimeMillis()
        return diff / (1000 * 60 * 60 * 24)
    }

    fun hoursUntil(timeInMillis: Long): Long {
        val diff = timeInMillis - System.currentTimeMillis()
        return diff / (1000 * 60 * 60)
    }

    fun isExpired(expirationTimeInMillis: Long): Boolean {
        return System.currentTimeMillis() > expirationTimeInMillis
    }

    fun isExpiringToday(expirationTimeInMillis: Long): Boolean {
        val now = System.currentTimeMillis()
        val tomorrow = now + (24 * 60 * 60 * 1000)
        return expirationTimeInMillis in (now + 1)..tomorrow
    }

    fun isExpiringThisWeek(expirationTimeInMillis: Long): Boolean {
        val now = System.currentTimeMillis()
        val nextWeek = now + (7 * 24 * 60 * 60 * 1000)
        return expirationTimeInMillis in (now + 1)..nextWeek
    }
}

object ValidationUtils {
    fun isValidEmail(email: String): Boolean {
        return email.matches(Regex("^[A-Za-z0-9+_.-]+@(.+)$"))
    }

    fun isValidPassword(password: String): Boolean {
        // At least 8 characters, one uppercase, one lowercase, one digit
        return password.length >= 8 &&
                password.any { it.isUpperCase() } &&
                password.any { it.isLowerCase() } &&
                password.any { it.isDigit() }
    }

    fun isValidPromoCode(code: String): Boolean {
        return code.isNotBlank() && code.length >= 3
    }

    fun isValidLatitude(lat: Double): Boolean {
        return lat in -90.0..90.0
    }

    fun isValidLongitude(lon: Double): Boolean {
        return lon in -180.0..180.0
    }
}
