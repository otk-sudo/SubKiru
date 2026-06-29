package com.subkiru.subkiru.notification

import com.subkiru.subkiru.core.domain.model.BillingInterval
import com.subkiru.subkiru.core.domain.model.BillingIntervalUnit
import com.subkiru.subkiru.core.domain.model.Subscription
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class NotificationHelperTest {

    @Test
    fun 請求日が今日の場合のテキスト() {
        val reminder = createReminder(daysUntilBilling = 0)

        val text = NotificationHelper.formatReminderText(reminder)

        assertEquals("TestService の請求日が今日です（¥980）", text)
    }

    @Test
    fun 請求日が明日の場合のテキスト() {
        val reminder = createReminder(daysUntilBilling = 1)

        val text = NotificationHelper.formatReminderText(reminder)

        assertEquals("TestService の請求日が明日です（¥980）", text)
    }

    @Test
    fun 請求日が2日以上後の場合のテキスト() {
        val reminder = createReminder(daysUntilBilling = 3)

        val text = NotificationHelper.formatReminderText(reminder)

        assertEquals("TestService の請求日が3日後です（¥980）", text)
    }

    @Test
    fun API33未満では通知権限が未許可でも投稿できる() {
        val canPost = NotificationHelper.canPostNotifications(
            sdkInt = SDK_BEFORE_POST_NOTIFICATIONS_PERMISSION,
            isPermissionGranted = false,
        )

        assertTrue(canPost)
    }

    @Test
    fun API33以上では通知権限が許可されていれば投稿できる() {
        val canPost = NotificationHelper.canPostNotifications(
            sdkInt = SDK_REQUIRING_POST_NOTIFICATIONS_PERMISSION,
            isPermissionGranted = true,
        )

        assertTrue(canPost)
    }

    @Test
    fun API33以上では通知権限が未許可なら投稿できない() {
        val canPost = NotificationHelper.canPostNotifications(
            sdkInt = SDK_REQUIRING_POST_NOTIFICATIONS_PERMISSION,
            isPermissionGranted = false,
        )

        assertFalse(canPost)
    }

    companion object {
        private const val SDK_BEFORE_POST_NOTIFICATIONS_PERMISSION = 32
        private const val SDK_REQUIRING_POST_NOTIFICATIONS_PERMISSION = 33
        private val BASE_DATE = LocalDate.of(2026, 1, 15)
        private val NOW = Instant.parse("2026-01-15T00:00:00Z")

        private fun createReminder(
            name: String = "TestService",
            amountMinor: Long = 980L,
            daysUntilBilling: Long = 1L,
        ): BillingReminder {
            return BillingReminder(
                subscription = Subscription(
                    id = 1L,
                    name = name,
                    amountMinor = amountMinor,
                    currencyCode = "JPY",
                    billingInterval = BillingInterval(
                        unit = BillingIntervalUnit.MONTHLY,
                        count = 1,
                    ),
                    startDate = BASE_DATE.minusMonths(1),
                    nextBillingDate = BASE_DATE.plusDays(daysUntilBilling),
                    categoryId = null,
                    templateId = null,
                    logoUri = null,
                    memo = null,
                    isActive = true,
                    createdAt = NOW,
                    updatedAt = NOW,
                ),
                daysUntilBilling = daysUntilBilling,
            )
        }
    }
}
