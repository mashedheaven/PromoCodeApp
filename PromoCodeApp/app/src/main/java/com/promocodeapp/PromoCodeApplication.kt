package com.promocodeapp

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.promocodeapp.di.ApplicationScope
import com.promocodeapp.location.GeofenceRegistrar
import com.promocodeapp.notifications.Notifier
import com.promocodeapp.work.WorkScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PromoCodeApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var notifier: Notifier
    @Inject lateinit var scheduler: WorkScheduler
    @Inject lateinit var registrar: GeofenceRegistrar
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Channels must exist before a geofence or worker posts anything.
        notifier.createChannels()
        scheduler.schedulePeriodicWork()
        scheduler.requestSync()
        // Re-register geofences whenever places, coupons or settings change.
        // The first pass re-registers everything, which also heals state after a
        // reboot or a restore onto a new device.
        scope.launch {
            var first = true
            registrar.desiredFences().collectLatest {
                registrar.sync(force = first)
                first = false
            }
        }
    }
}
