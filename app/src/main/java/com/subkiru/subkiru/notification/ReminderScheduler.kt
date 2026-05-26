package com.subkiru.subkiru.notification

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class ReminderScheduler(
    private val context: Context,
) {

    fun schedule() {
        val request = PeriodicWorkRequestBuilder<BillingReminderWorker>(
            repeatInterval = 1,
            repeatIntervalTimeUnit = TimeUnit.DAYS,
        ).build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
    }

    fun cancel() {
        WorkManager.getInstance(context)
            .cancelUniqueWork(WORK_NAME)
    }

    companion object {
        internal const val WORK_NAME = "billing_reminder"
    }
}
