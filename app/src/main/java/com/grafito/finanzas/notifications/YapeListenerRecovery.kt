package com.grafito.finanzas.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.grafito.finanzas.data.UserPreferences
import java.io.IOException
import java.util.concurrent.TimeUnit

class YapeListenerRecovery(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val enabled = try {
            UserPreferences(applicationContext).yapeCaptureSince() > 0
        } catch (_: IOException) {
            return Result.retry()
        }
        if (enabled) YapeNotificationListener.requestConnection(applicationContext)
        return Result.success()
    }

    internal companion object {
        private const val NAME = "yape_notification_listener_recovery"

        fun schedule(context: Context, enabled: Boolean) {
            val manager = WorkManager.getInstance(context)
            if (enabled) {
                manager.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<YapeListenerRecovery>(15, TimeUnit.MINUTES).build())
            } else {
                manager.cancelUniqueWork(NAME)
            }
        }
    }
}
