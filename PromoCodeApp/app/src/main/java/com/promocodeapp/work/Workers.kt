package com.promocodeapp.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.promocodeapp.core.util.today
import com.promocodeapp.data.local.AppDatabase
import com.promocodeapp.data.local.toModel
import com.promocodeapp.data.remote.SessionStore
import com.promocodeapp.data.remote.SupabaseConfig
import com.promocodeapp.data.repository.CouponRepository
import com.promocodeapp.data.repository.LocalChangeListener
import com.promocodeapp.data.settings.SettingsStore
import com.promocodeapp.location.GeofenceRegistrar
import com.promocodeapp.notifications.Notifier
import com.promocodeapp.notifications.ReminderPlanner
import com.promocodeapp.sync.SyncEngine
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Uploads pending changes and downloads remote ones. */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val engine: SyncEngine,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        engine.sync()
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        // Client errors (bad schema, revoked session) will not fix themselves.
        if (e.code() in 400..499 && e.code() != 408 && e.code() != 429) Result.failure() else Result.retry()
    } catch (e: Exception) {
        if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
    }

    private companion object {
        const val MAX_ATTEMPTS = 6
    }
}

/** Once-a-day housekeeping: reminders, auto-archive and a geofence health check. */
@HiltWorker
class DailyWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val db: AppDatabase,
    private val coupons: CouponRepository,
    private val settings: SettingsStore,
    private val notifier: Notifier,
    private val registrar: GeofenceRegistrar,
    private val clock: Clock,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = settings.current()
        val today = clock.today()

        if (prefs.autoArchiveExpired) coupons.archiveExpired(graceDays = ARCHIVE_GRACE_DAYS)

        if (prefs.expiryReminders) {
            val rows = db.couponDao().getAll()
            val due = ReminderPlanner.couponsDue(
                rows.map { it.toModel() }, rows.associate { it.id to it.reminderSentFor }, today, prefs.expiryLeadDays,
            )
            // Only record the reminder as sent if the user could actually see it.
            if (notifier.showExpiring(due, today)) {
                due.forEach { db.couponDao().setReminderSentFor(it.id, it.expiresOn) }
            }
        }

        if (prefs.membershipReminders) {
            val rows = db.membershipDao().getAll()
            ReminderPlanner.renewalsDue(rows.map { it.toModel() }, rows.associate { it.id to it.reminderSentFor }, today)
                .forEach { r ->
                    if (notifier.showRenewal(r.membership, r.renewsOn, r.daysLeft)) {
                        db.membershipDao().setReminderSentFor(r.membership.id, r.renewsOn)
                    }
                }
        }

        // Coupons that expired overnight no longer need a geofence, and this
        // also repairs registrations lost to e.g. a Play services reset.
        registrar.sync(force = true)
        return Result.success()
    }

    private companion object {
        const val ARCHIVE_GRACE_DAYS = 7L
    }
}

@Singleton
class WorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: SupabaseConfig,
    private val sessions: SessionStore,
    private val clock: Clock,
) : LocalChangeListener {
    private val workManager get() = WorkManager.getInstance(context)
    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Debounced upload shortly after an edit; waits for connectivity if offline. */
    override fun onLocalChange() = requestSync(delaySeconds = 5)

    fun requestSync(delaySeconds: Long = 0) {
        if (!config.isConfigured || sessions.session.value == null) return
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(online)
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(SYNC_NOW, ExistingWorkPolicy.REPLACE, request)
    }

    /** Call at app start. Safe to repeat: existing schedules are kept. */
    fun schedulePeriodicWork() {
        // Aim for mid-morning so reminders arrive at a sensible hour.
        val now = LocalDateTime.now(clock)
        var firstRun = now.toLocalDate().atTime(LocalTime.of(9, 0))
        if (!firstRun.isAfter(now)) firstRun = firstRun.plusDays(1)
        val daily = PeriodicWorkRequestBuilder<DailyWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(Duration.between(now, firstRun).toMinutes(), TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.KEEP, daily)

        // Picks up changes made on other devices even if this one is idle.
        val sync = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS).setConstraints(online).build()
        workManager.enqueueUniquePeriodicWork(SYNC_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, sync)
    }

    private companion object {
        const val SYNC_NOW = "sync-now"
        const val SYNC_PERIODIC = "sync-periodic"
        const val DAILY = "daily-housekeeping"
    }
}
