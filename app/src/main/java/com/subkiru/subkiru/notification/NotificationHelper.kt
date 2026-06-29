package com.subkiru.subkiru.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.subkiru.subkiru.MainActivity
import com.subkiru.subkiru.R
import com.subkiru.subkiru.core.domain.model.Subscription

object NotificationHelper {

    const val CHANNEL_ID = "billing_reminder"
    private const val GROUP_KEY = "com.subkiru.subkiru.BILLING_REMINDER"
    private const val SUMMARY_NOTIFICATION_ID = 0

    /**
     * 通知チャンネルを作成する。
     * アプリ起動時に毎回呼んでよい（既存チャンネルは上書きされない）。
     */
    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "請求日リマインダー",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "サブスクリプションの請求日をお知らせします"
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    /**
     * 請求予定のサブスクリプションを通知する。
     * 1件の場合は単独通知、複数件の場合はグループ通知（InboxStyle）を使用する。
     */
    fun showBillingReminders(
        context: Context,
        reminders: List<BillingReminder>,
    ) {
        if (reminders.isEmpty()) return
        val isPermissionGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!canPostNotifications(Build.VERSION.SDK_INT, isPermissionGranted)) return

        val notificationManager = NotificationManagerCompat.from(context)

        // 個別通知
        reminders.forEach { reminder ->
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("請求日リマインダー")
                .setContentText(formatReminderText(reminder))
                .setContentIntent(createAppPendingIntent(context))
                .setGroup(GROUP_KEY)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(
                reminder.subscription.id.toInt(),
                notification,
            )
        }

        // 複数件の場合はサマリー通知を追加
        if (reminders.size > 1) {
            val inboxStyle = NotificationCompat.InboxStyle()
                .setBigContentTitle("請求日リマインダー")
                .setSummaryText("${reminders.size}件の請求予定")

            reminders.forEach { reminder ->
                inboxStyle.addLine(formatReminderText(reminder))
            }

            val summaryNotification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("請求日リマインダー")
                .setContentText("${reminders.size}件の請求予定があります")
                .setContentIntent(createAppPendingIntent(context))
                .setGroup(GROUP_KEY)
                .setGroupSummary(true)
                .setStyle(inboxStyle)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(SUMMARY_NOTIFICATION_ID, summaryNotification)
        }
    }

    /**
     * リマインダーのテキストを生成する。
     * 例: "Netflix の請求日が明日です（¥980）"
     */
    internal fun formatReminderText(reminder: BillingReminder): String {
        val timing = when (reminder.daysUntilBilling) {
            0L -> "今日"
            1L -> "明日"
            else -> "${reminder.daysUntilBilling}日後"
        }
        val amount = formatAmount(reminder.subscription.amountMinor, reminder.subscription.currencyCode)
        return "${reminder.subscription.name} の請求日が${timing}です（${amount}）"
    }

    internal fun canPostNotifications(
        sdkInt: Int,
        isPermissionGranted: Boolean,
    ): Boolean {
        return sdkInt < Build.VERSION_CODES.TIRAMISU || isPermissionGranted
    }

    private fun formatAmount(amountMinor: Long, currencyCode: String): String {
        return when (currencyCode) {
            "JPY" -> "¥$amountMinor"
            else -> "$currencyCode $amountMinor"
        }
    }

    private fun createAppPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
