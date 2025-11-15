package com.promocodeapp.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.promocodeapp.domain.repository.SyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Background service for synchronizing data with Supabase
 * Uses WorkManager for reliable background execution
 */
@AndroidEntryPoint
class DataSyncService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): DataSyncService = this@DataSyncService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("DataSyncService", "Service started")
        return START_STICKY
    }

    companion object {
        private const val TAG = "DataSyncService"
    }
}

/**
 * Worker for background data synchronization
 */
class SyncWorker(context: android.content.Context, params: WorkerParameters) : Worker(context, params) {

    @Inject
    lateinit var syncRepository: SyncRepository

    companion object {
        private const val TAG = "SyncWorker"
    }

    override fun doWork(): Result {
        return try {
            Log.d(TAG, "Starting background sync")
            
            val userId = inputData.getString("user_id") ?: return Result.retry()
            
            // Use coroutine to run the sync
            var syncResult = Result.retry()
            val job = CoroutineScope(Dispatchers.IO).launch {
                val result = syncRepository.fullSync(userId)
                syncResult = if (result.isSuccess) {
                    Log.d(TAG, "Sync completed successfully")
                    Result.success()
                } else {
                    Log.e(TAG, "Sync failed: ${result.exceptionOrNull()?.message}")
                    Result.retry()
                }
            }
            
            // Wait for coroutine to complete (with timeout)
            Thread.sleep(30000) // 30 second timeout
            syncResult
        } catch (e: Exception) {
            Log.e(TAG, "Sync worker error: ${e.message}", e)
            Result.retry()
        }
    }
}

/**
 * Manager for scheduling periodic sync tasks
 */
object SyncScheduler {
    private const val TAG = "SyncScheduler"
    private const val SYNC_WORK_TAG = "data_sync_work"

    /**
     * Schedule periodic data synchronization
     * @param userId The user ID to sync data for
     * @param intervalMinutes How often to sync (in minutes, default 30)
     */
    fun schedulePeriodicSync(context: android.content.Context, userId: String, intervalMinutes: Long = 30) {
        Log.d(TAG, "Scheduling periodic sync every $intervalMinutes minutes for user $userId")
        
        val syncConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncWorkRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(syncConstraints)
            .setBackoffPolicy(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .setInitialDelay(intervalMinutes, TimeUnit.MINUTES)
            .addTag(SYNC_WORK_TAG)
            .setInputData(
                androidx.work.Data.Builder()
                    .putString("user_id", userId)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "${SYNC_WORK_TAG}_$userId",
            androidx.work.ExistingWorkPolicy.KEEP,
            syncWorkRequest
        )

        Log.d(TAG, "Sync scheduled successfully")
    }

    /**
     * Trigger immediate synchronization
     * @param userId The user ID to sync data for
     */
    fun triggerImmediateSync(context: android.content.Context, userId: String) {
        Log.d(TAG, "Triggering immediate sync for user $userId")
        
        val syncConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncWorkRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(syncConstraints)
            .setBackoffPolicy(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .addTag(SYNC_WORK_TAG)
            .setInputData(
                androidx.work.Data.Builder()
                    .putString("user_id", userId)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "${SYNC_WORK_TAG}_immediate_$userId",
            androidx.work.ExistingWorkPolicy.REPLACE,
            syncWorkRequest
        )

        Log.d(TAG, "Immediate sync triggered")
    }

    /**
     * Cancel all sync tasks
     */
    fun cancelAllSyncs(context: android.content.Context) {
        Log.d(TAG, "Cancelling all sync tasks")
        WorkManager.getInstance(context).cancelAllWorkByTag(SYNC_WORK_TAG)
    }

    /**
     * Cancel sync for specific user
     */
    fun cancelSync(context: android.content.Context, userId: String) {
        Log.d(TAG, "Cancelling sync for user $userId")
        WorkManager.getInstance(context).cancelUniqueWork("${SYNC_WORK_TAG}_$userId")
    }
}
