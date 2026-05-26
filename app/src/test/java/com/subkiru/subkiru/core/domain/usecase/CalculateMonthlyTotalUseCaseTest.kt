package com.subkiru.subkiru.core.domain.usecase

import app.cash.turbine.test
import com.subkiru.subkiru.core.domain.model.BillingInterval
import com.subkiru.subkiru.core.domain.model.BillingIntervalUnit
import com.subkiru.subkiru.core.domain.model.Subscription
import com.subkiru.subkiru.core.domain.repository.SubscriptionRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class CalculateMonthlyTotalUseCaseTest {
    private val repository = mockk<SubscriptionRepository>()
    private val useCase = CalculateMonthlyTotalUseCase(repository)

    @Test
    fun 月額サブスクの合計が正しく計算される() = runTest {
        assertMonthlyTotal(subscription(amountMinor = 1_000L), expected = 1_000L)
    }

    @Test
    fun 年額サブスクが月額に換算される() = runTest {
        assertMonthlyTotal(
            subscription(amountMinor = 12_000L, interval = BillingInterval(BillingIntervalUnit.YEARLY, 1)),
            expected = 1_000L,
        )
    }

    @Test
    fun 週額サブスクが月額に換算される() = runTest {
        assertMonthlyTotal(
            subscription(amountMinor = 500L, interval = BillingInterval(BillingIntervalUnit.WEEKLY, 1)),
            expected = 2_000L,
        )
    }

    @Test
    fun 日額サブスクが月額に換算される() = runTest {
        assertMonthlyTotal(
            subscription(amountMinor = 100L, interval = BillingInterval(BillingIntervalUnit.DAILY, 1)),
            expected = 3_000L,
        )
    }

    @Test
    fun 複数間隔のサブスクが混在する場合の合計が正しい() = runTest {
        every { repository.observeActiveSubscriptions() } returns flowOf(
            listOf(
                subscription(amountMinor = 1_000L),
                subscription(amountMinor = 12_000L, interval = BillingInterval(BillingIntervalUnit.YEARLY, 1)),
                subscription(amountMinor = 500L, interval = BillingInterval(BillingIntervalUnit.WEEKLY, 1)),
                subscription(amountMinor = 100L, interval = BillingInterval(BillingIntervalUnit.DAILY, 1)),
            )
        )

        useCase().test {
            assertEquals(7_000L, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun サブスクが空の場合は0を返す() = runTest {
        every { repository.observeActiveSubscriptions() } returns flowOf(emptyList())

        useCase().test {
            assertEquals(0L, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `3ヶ月ごとのサブスクが正しく月額換算される`() = runTest {
        assertMonthlyTotal(
            subscription(amountMinor = 3_000L, interval = BillingInterval(BillingIntervalUnit.MONTHLY, 3)),
            expected = 1_000L,
        )
    }

    private suspend fun assertMonthlyTotal(subscription: Subscription, expected: Long) {
        every { repository.observeActiveSubscriptions() } returns flowOf(listOf(subscription))

        useCase().test {
            assertEquals(expected, awaitItem())
            awaitComplete()
        }
    }

    private fun subscription(
        amountMinor: Long,
        interval: BillingInterval = BillingInterval(BillingIntervalUnit.MONTHLY, 1),
    ): Subscription {
        return Subscription(
            id = 1L,
            name = "Netflix",
            amountMinor = amountMinor,
            currencyCode = "JPY",
            billingInterval = interval,
            startDate = LocalDate.of(2026, 5, 1),
            nextBillingDate = LocalDate.of(2026, 6, 1),
            categoryId = null,
            templateId = null,
            logoUri = null,
            memo = null,
            isActive = true,
            createdAt = Instant.ofEpochMilli(1_778_760_000_000L),
            updatedAt = Instant.ofEpochMilli(1_778_760_000_000L),
        )
    }
}
