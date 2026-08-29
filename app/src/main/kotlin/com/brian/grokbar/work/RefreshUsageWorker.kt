package com.brian.grokbar.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.brian.grokbar.data.UsageRepository
import com.brian.grokbar.widget.WidgetUpdater
import java.util.concurrent.TimeUnit

class RefreshUsageWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            UsageRepository.get(applicationContext).refresh(force = true)
            WidgetUpdater.updateAll(applicationContext)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

object RefreshScheduler {
    private const val UNIQUE_NAME = "grok_usage_refresh"

    fun ensure(context: Context) {
        val request = PeriodicWorkRequestBuilder<RefreshUsageWorker>(15, TimeUnit.MINUTES)
            .setInitialDelay(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
