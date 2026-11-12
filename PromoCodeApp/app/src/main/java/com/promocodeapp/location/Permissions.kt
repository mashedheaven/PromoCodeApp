package com.promocodeapp.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Permissions {
    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun hasFineLocation(context: Context) = granted(context, Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasAnyLocation(context: Context) =
        hasFineLocation(context) || granted(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    /** Before Android 10 there is no separate background permission. */
    fun hasBackgroundLocation(context: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    /** Geofencing needs precise location, all the time. */
    fun canGeofence(context: Context) = hasFineLocation(context) && hasBackgroundLocation(context)

    fun canNotify(context: Context) = NotificationManagerCompat.from(context).areNotificationsEnabled()
}
