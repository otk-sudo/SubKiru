package com.subkiru.subkiru.notification

import com.subkiru.subkiru.core.domain.model.BillingInterval
import com.subkiru.subkiru.core.domain.model.BillingIntervalUnit
import com.subkiru.subkiru.core.domain.model.NotifiedBilling
import com.subkiru.subkiru.core.domain.model.Subscription
import com.subkiru.subkiru.core.domain.model.UserSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class BillingReminderWorkerTest {

    @Test
    fun リマインダーが無効の場合は空リストを返す() {
        val settings = UserSettings(isReminderEnabled = false, reminderDaysBefore = 3)
        val subscriptions = listOf(createSubscription(nextBillingDate = TODAY.plusDays(1)))

        val result = BillingReminderWorker.getSubscriptionsToNotify(
            settings = settings,
            subscriptions = subscriptions,
            today = TODAY,
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun 請求予定がない場合は空リストを返す() {
        val settings = UserSettings(isReminderEnabled = true, reminderDaysBefore = 3)
        val subscriptions = emptyList<Subscription>()

        val result = BillingReminderWorker.getSubscriptionsToNotify(
            settings = settings,
            subscriptions = subscriptions,
            today = TODAY,
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun 請求日が範囲内の場合はリストに含まれる() {
        val settings = UserSettings(isReminderEnabled = true, reminderDaysBefore = 3)
        val subscription = createSubscription(
            name = "Netflix",
            nextBillingDate = TODAY.plusDays(2),
        )

        val result = BillingReminderWorker.getSubscriptionsToNotify(
            settings = settings,
            subscriptions = listOf(subscription),
            today = TODAY,
        )

        assertEquals(1, result.size)
        assertEquals("Netflix", result[0].subscription.name)
    }

    @Test
    fun 請求日が範囲外の場合はリストに含まれない() {
        val settings = UserSettings(isReminderEnabled = true, reminderDaysBefore = 3)
        val subscription = createSubscription(nextBillingDate = TODAY.plusDays(5))

        val result = BillingReminderWorker.getSubscriptionsToNotify(
            settings = settings,
            subscriptions = listOf(subscription),
            today = TODAY,
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun 複数の請求予定がある場合は全て返す() {
        val settings = UserSettings(isReminderEnabled = true, reminderDaysBefore = 3)
        val subscriptions = listOf(
            createSubscription(name = "Netflix", nextBillingDate = TODAY.plusDays(1)),
            createSubscription(name = "Spotify", nextBillingDate = TODAY.plusDays(2)),
            createSubscription(name = "YouTube", nextBillingDate = TODAY.plusDays(5)),
        )

        val result = BillingReminderWorker.getSubscriptionsToNotify(
            settings = settings,
            subscriptions = subscriptions,
            today = TODAY,
        )

        assertEquals(2, result.size)
        assertEquals("Netflix", result[0].subscription.name)
        assertEquals("Spotify", result[1].subscription.name)
    }

    @Test
    fun 請求日までの日数が正しく計算される() {
        val settings = UserSettings(isReminderEnabled = true, reminderDaysBefore = 7)
        val subscription = createSubscription(nextBillingDate = TODAY.plusDays(5))

        val result = BillingReminderWorker.getSubscriptionsToNotify(
            settings = settings,
            subscriptions = listOf(subscription),
            today = TODAY,
        )

        assertEquals(1, result.size)
        assertEquals(5L, result[0].daysUntilBilling)
    }

    @Test
    fun 今日が請求日の場合もリストに含まれる() {
        val settings = UserSettings(isReminderEnabled = true, reminderDaysBefore = 1)
        val subscription = createSubscription(nextBillingDate = TODAY)

        val result = BillingReminderWorker.getSubscriptionsToNotify(
            settings = settings,
            subscriptions = listOf(subscription),
            today = TODAY,
        )

        assertEquals(1, result.size)
        assertEquals(0L, result[0].daysUntilBilling)
    }

    @Test
    fun 過去の請求日はリストに含まれない() {
        val settings = UserSettings(isReminderEnabled = true, reminderDaysBefore = 3)
        val subscription = createSubscription(nextBillingDate = TODAY.minusDays(1))

        val result = BillingReminderWorker.getSubscriptionsToNotify(
            settings = settings,
            subscriptions = listOf(subscription),
            today = TODAY,
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun 通知済みのサブスクIDと請求日の組はリストに含まれない() {
        // Arrange
        val settings = UserSettings(isReminderEnabled = true, reminderDaysBefore = 3)
        val billingDate = TODAY.plusDays(2)
        val subscription = createSubscription(id = 1L, nextBillingDate = billingDate)
        val notified = setOf(NotifiedBilling(subscriptionId = 1L, billingDate = billingDate))

        // Act
        val result = BillingReminderWorker.getSubscriptionsToNotify(
            settings = settings,
            subscriptions = listOf(subscription),
            today = TODAY,
            notifiedBillings = notified,
        )

        // Assert
        assertTrue(result.isEmpty())
    }

    @Test
    fun 同じサブスクでも請求日が異なれば通知済みでもリストに含まれる() {
        // Arrange
        val settings = UserSettings(isReminderEnabled = true, reminderDaysBefore = 3)
        val subscription = createSubscription(id = 1L, nextBillingDate = TODAY.plusDays(2))
        val notified = setOf(NotifiedBilling(subscriptionId = 1L, billingDate = TODAY.minusMonths(1)))

        // Act
        val result = BillingReminderWorker.getSubscriptionsToNotify(
            settings = settings,
            subscriptions = listOf(subscription),
            today = TODAY,
            notifiedBillings = notified,
        )

        // Assert
        assertEquals(1, result.size)
    }

    @Test
    fun 請求日が同じでもサブスクIDが異なれば通知済みでもリストに含まれる() {
        // Arrange
        val settings = UserSettings(isReminderEnabled = true, reminderDaysBefore = 3)
        val billingDate = TODAY.plusDays(2)
        val subscription = createSubscription(id = 2L, nextBillingDate = billingDate)
        val notified = setOf(NotifiedBilling(subscriptionId = 1L, billingDate = billingDate))

        // Act
        val result = BillingReminderWorker.getSubscriptionsToNotify(
            settings = settings,
            subscriptions = listOf(subscription),
            today = TODAY,
            notifiedBillings = notified,
        )

        // Assert
        assertEquals(1, result.size)
    }

    @Test
    fun 通知済みと未通知が混在する場合は未通知のみ返す() {
        // Arrange
        val settings = UserSettings(isReminderEnabled = true, reminderDaysBefore = 3)
        val subscriptions = listOf(
            createSubscription(id = 1L, name = "Netflix", nextBillingDate = TODAY.plusDays(1)),
            createSubscription(id = 2L, name = "Spotify", nextBillingDate = TODAY.plusDays(2)),
        )
        val notified = setOf(NotifiedBilling(subscriptionId = 1L, billingDate = TODAY.plusDays(1)))

        // Act
        val result = BillingReminderWorker.getSubscriptionsToNotify(
            settings = settings,
            subscriptions = subscriptions,
            today = TODAY,
            notifiedBillings = notified,
        )

        // Assert
        assertEquals(listOf("Spotify"), result.map { it.subscription.name })
    }

    companion object {
        private val TODAY = LocalDate.of(2026, 1, 15)
        private val NOW = Instant.parse("2026-01-15T00:00:00Z")

        private fun createSubscription(
            id: Long = 1L,
            name: String = "TestService",
            amountMinor: Long = 980L,
            currencyCode: String = "JPY",
            nextBillingDate: LocalDate = TODAY.plusDays(1),
        ): Subscription {
            return Subscription(
                id = id,
                name = name,
                amountMinor = amountMinor,
                currencyCode = currencyCode,
                billingInterval = BillingInterval(
                    unit = BillingIntervalUnit.MONTHLY,
                    count = 1,
                ),
                startDate = TODAY.minusMonths(1),
                nextBillingDate = nextBillingDate,
                categoryId = null,
                templateId = null,
                logoUri = null,
                memo = null,
                isActive = true,
                createdAt = NOW,
                updatedAt = NOW,
            )
        }
    }
}
