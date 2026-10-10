package com.subkiru.subkiru.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.subkiru.subkiru.SubKiruApplication
import com.subkiru.subkiru.core.domain.model.NotifiedBilling
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

        val today = LocalDate.now(app.clock)
        val history = app.reminderHistoryRepository

        // 請求日を過ぎた記録は不要なので先に掃除する
        history.deleteBillingsBefore(today)

        val reminders = getSubscriptionsToNotify(
            settings = settings,
            subscriptions = subscriptions,
            today = today,
            notifiedBillings = history.getNotifiedBillings(),
        )

        // 通知を出せた場合のみ通知済みとして記録する（権限未許可なら次回以降に再試行できる）
        if (NotificationHelper.showBillingReminders(applicationContext, reminders)) {
            history.markAsNotified(
                reminders.map { NotifiedBilling(it.subscription.id, it.subscription.nextBillingDate) },
            )
        }

        return Result.success()
    }

    companion object {
        /**
         * 通知対象のサブスクリプションを抽出する。
         * 通知済みの「サブスクID + 請求日」は対象外にし、1回の請求につき1回だけ通知する。
         * 純粋関数としてテスト可能にする。
         */
        internal fun getSubscriptionsToNotify(
            settings: UserSettings,
            subscriptions: List<Subscription>,
            today: LocalDate,
            notifiedBillings: Set<NotifiedBilling> = emptySet(),
        ): List<BillingReminder> {
            if (!settings.isReminderEnabled) return emptyList()

            val deadline = today.plusDays(settings.reminderDaysBefore.toLong())
            return subscriptions
                .filter { it.nextBillingDate in today..deadline }
                .filter { NotifiedBilling(it.id, it.nextBillingDate) !in notifiedBillings }
                .map { subscription ->
                    BillingReminder(
                        subscription = subscription,
                        daysUntilBilling = ChronoUnit.DAYS.between(today, subscription.nextBillingDate),
                    )
                }
        }
    }
}
