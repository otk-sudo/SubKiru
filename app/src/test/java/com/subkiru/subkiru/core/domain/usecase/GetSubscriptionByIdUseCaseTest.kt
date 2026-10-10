package com.subkiru.subkiru.core.domain.usecase

import com.subkiru.subkiru.core.domain.model.BillingInterval
import com.subkiru.subkiru.core.domain.model.BillingIntervalUnit
import com.subkiru.subkiru.core.domain.model.Subscription
import com.subkiru.subkiru.core.domain.repository.SubscriptionRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class GetSubscriptionByIdUseCaseTest {
    private val repository = mockk<SubscriptionRepository>()
    private val useCase = GetSubscriptionByIdUseCase(repository)

    @Test
    fun 有効なサブスクが存在する場合はそのサブスクを返す() = runTest {
        // Arrange
        val subscription = subscription()
        coEvery { repository.getSubscriptionById(SUBSCRIPTION_ID) } returns subscription

        // Act
        val result = useCase(SUBSCRIPTION_ID)

        // Assert
        assertEquals(subscription, result)
    }

    @Test
    fun サブスクが存在しない場合はnullを返す() = runTest {
        // Arrange
        coEvery { repository.getSubscriptionById(SUBSCRIPTION_ID) } returns null

        // Act
        val result = useCase(SUBSCRIPTION_ID)

        // Assert
        assertNull(result)
    }

    @Test
    fun 削除済みのサブスクはnullを返す() = runTest {
        // Arrange
        coEvery { repository.getSubscriptionById(SUBSCRIPTION_ID) } returns subscription(isActive = false)

        // Act
        val result = useCase(SUBSCRIPTION_ID)

        // Assert
        assertNull(result)
    }

    private fun subscription(isActive: Boolean = true): Subscription {
        return Subscription(
            id = SUBSCRIPTION_ID,
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
            isActive = isActive,
            createdAt = Instant.ofEpochMilli(1_778_760_000_000L),
            updatedAt = Instant.ofEpochMilli(1_778_760_000_000L),
        )
    }

    companion object {
        private const val SUBSCRIPTION_ID = 7L
    }
}
