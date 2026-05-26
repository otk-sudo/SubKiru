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

class GetSubscriptionsUseCaseTest {
    private val repository = mockk<SubscriptionRepository>()
    private val useCase = GetSubscriptionsUseCase(repository)

    @Test
    fun アクティブなサブスク一覧を取得できる() = runTest {
        val subscriptions = listOf(subscription(id = 1L), subscription(id = 2L))
        every { repository.observeActiveSubscriptions() } returns flowOf(subscriptions)

        useCase().test {
            assertEquals(subscriptions, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun サブスクが空の場合は空リストを返す() = runTest {
        every { repository.observeActiveSubscriptions() } returns flowOf(emptyList())

        useCase().test {
            assertEquals(emptyList<Subscription>(), awaitItem())
            awaitComplete()
        }
    }

    private fun subscription(id: Long): Subscription {
        return Subscription(
            id = id,
            name = "Netflix",
            amountMinor = 1_490L,
            currencyCode = "JPY",
            billingInterval = BillingInterval(BillingIntervalUnit.MONTHLY, 1),
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
