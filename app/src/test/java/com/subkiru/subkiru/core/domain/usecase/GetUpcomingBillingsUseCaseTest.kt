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
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class GetUpcomingBillingsUseCaseTest {
    private val repository = mockk<SubscriptionRepository>()
    private val fixedDate = LocalDate.of(2026, 5, 21)
    private val clock = Clock.fixed(
        fixedDate.atStartOfDay(ZoneId.systemDefault()).toInstant(),
        ZoneId.systemDefault(),
    )
    private val useCase = GetUpcomingBillingsUseCase(repository, clock)

    @Test
    fun 指定日数以内のサブスクのみ返す() = runTest {
        val included = subscription(id = 1L, nextBillingDate = fixedDate.plusDays(3))
        val excluded = subscription(id = 2L, nextBillingDate = fixedDate.plusDays(8))
        every { repository.observeActiveSubscriptions() } returns flowOf(listOf(included, excluded))

        useCase(withinDays = 7).test {
            assertEquals(listOf(included), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun 期限外のサブスクは除外される() = runTest {
        val excluded = subscription(id = 1L, nextBillingDate = fixedDate.plusDays(8))
        every { repository.observeActiveSubscriptions() } returns flowOf(listOf(excluded))

        useCase(withinDays = 7).test {
            assertEquals(emptyList<Subscription>(), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun 当日が次回請求日のサブスクは含まれる() = runTest {
        val included = subscription(id = 1L, nextBillingDate = fixedDate)
        every { repository.observeActiveSubscriptions() } returns flowOf(listOf(included))

        useCase(withinDays = 7).test {
            assertEquals(listOf(included), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun サブスクが空の場合は空リストを返す() = runTest {
        every { repository.observeActiveSubscriptions() } returns flowOf(emptyList())

        useCase(withinDays = 7).test {
            assertEquals(emptyList<Subscription>(), awaitItem())
            awaitComplete()
        }
    }

    private fun subscription(id: Long, nextBillingDate: LocalDate): Subscription {
        return Subscription(
            id = id,
            name = "Netflix",
            amountMinor = 1_490L,
            currencyCode = "JPY",
            billingInterval = BillingInterval(BillingIntervalUnit.MONTHLY, 1),
            startDate = fixedDate.minusDays(1),
            nextBillingDate = nextBillingDate,
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
