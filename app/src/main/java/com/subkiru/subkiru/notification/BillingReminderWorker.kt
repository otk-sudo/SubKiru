package com.subkiru.subkiru.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.subkiru.subkiru.SubKiruApplication
import com.subkiru.subkiru.core.domain.model.Subscription
import com.subkiru.subkiru.core.domain.model.UserSettings
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class BillingReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as SubKiruApplication

        val settings = app.settingsRepository.observeSettings().first()
        if (!settings.isReminderEnabled) return Result.success()

        val subscriptions = app.subscriptionRepository
            .observeActiveSubscriptions().first()

        val reminders = getSubscriptionsToNotify(
            settings = settings,
            subscriptions = subscriptions,
            today = LocalDate.now(app.clock),
        )

        if (reminders.isNotEmpty()) {
            NotificationHelper.showBillingReminders(applicationContext, reminders)
        }

        return Result.success()
    }

    companion object {
        /**
         * 通知対象のサブスクリプションを抽出する。
         * 純粋関数としてテスト可能にする。
         */
        internal fun getSubscriptionsToNotify(
            settings: UserSettings,
            subscriptions: List<Subscription>,
            today: LocalDate,
        ): List<BillingReminder> {
            if (!settings.isReminderEnabled) return emptyList()

            val deadline = today.plusDays(settings.reminderDaysBefore.toLong())
            return subscriptions
                .filter { it.nextBillingDate in today..deadline }
                .map { subscription ->
                    BillingReminder(
                        subscription = subscription,
                        daysUntilBilling = ChronoUnit.DAYS.between(today, subscription.nextBillingDate),
                    )
                }
        }
    }
}
