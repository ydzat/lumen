package com.lumen.research.collector

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

actual class PlatformScheduler(private val context: Context) {

    actual fun start(intervalMillis: Long) {
        val intervalMinutes = (intervalMillis / 60_000).coerceAtLeast(15)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<CollectorWorker>(
            intervalMinutes, TimeUnit.MINUTES,
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    actual fun stop() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    actual companion object {
        actual val DEFAULT_INTERVAL: Long = 4 * 60 * 60 * 1000L

        private const val WORK_NAME = "lumen_collection"
    }
}

class CollectorWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val collectorManager: CollectorManager by inject()

    override suspend fun doWork(): Result {
        return try {
            collectorManager.runPipeline()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
