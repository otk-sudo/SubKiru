package com.subkiru.subkiru.core.domain.usecase

import com.subkiru.subkiru.core.domain.model.BillingInterval
import com.subkiru.subkiru.core.domain.model.BillingIntervalUnit
import com.subkiru.subkiru.core.domain.model.Subscription
import com.subkiru.subkiru.core.domain.repository.SubscriptionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class UpdateSubscriptionUseCaseTest {
    private val repository = mockk<SubscriptionRepository>()
    private val useCase = UpdateSubscriptionUseCase(repository)

    @Test
    fun 正常なサブスクを更新するとSuccessを返す() = runTest {
        // Arrange
        coEvery { repository.getSubscriptionById(SUBSCRIPTION_ID) } returns subscription()
        coEvery { repository.updateSubscription(any()) } returns Unit

        // Act
        val result = useCase(subscription(name = "Netflix Premium"))

        // Assert
        assertEquals(UpdateSubscriptionUseCase.Result.Success, result)
        coVerify(exactly = 1) { repository.updateSubscription(any()) }
    }

    @Test
    fun 更新時にidと更新後の値とupdatedAtがそのまま保存される() = runTest {
        // Arrange
        coEvery { repository.getSubscriptionById(SUBSCRIPTION_ID) } returns subscription()
        val saved = slot<Subscription>()
        coEvery { repository.updateSubscription(capture(saved)) } returns Unit
        val edited = subscription(name = "Netflix Premium", amountMinor = 1_990L, updatedAt = EDITED_AT)

        // Act
        useCase(edited)

        // Assert
        assertEquals(SUBSCRIPTION_ID, saved.captured.id)
        assertEquals("Netflix Premium", saved.captured.name)
        assertEquals(1_990L, saved.captured.amountMinor)
        assertEquals(EDITED_AT, saved.captured.updatedAt)
    }

    @Test
    fun 更新時にcreatedAtとisActiveは保存済みの値が維持される() = runTest {
        // Arrange
        coEvery { repository.getSubscriptionById(SUBSCRIPTION_ID) } returns subscription(createdAt = CREATED_AT)
        val saved = slot<Subscription>()
        coEvery { repository.updateSubscription(capture(saved)) } returns Unit
        val edited = subscription(createdAt = EDITED_AT, isActive = false, updatedAt = EDITED_AT)

        // Act
        useCase(edited)

        // Assert
        assertEquals(CREATED_AT, saved.captured.createdAt)
        assertTrue(saved.captured.isActive)
    }

    @Test
    fun 更新時に並び順は保存済みの値が維持される() = runTest {
        // Arrange
        coEvery { repository.getSubscriptionById(SUBSCRIPTION_ID) } returns
            subscription().copy(sortOrder = SAVED_SORT_ORDER)
        val saved = slot<Subscription>()
        coEvery { repository.updateSubscription(capture(saved)) } returns Unit

        // Act
        useCase(subscription(name = "Netflix Premium"))

        // Assert
        assertEquals(SAVED_SORT_ORDER, saved.captured.sortOrder)
    }

    @Test
    fun 対象のサブスクが存在しない場合はNotFoundを返す() = runTest {
        // Arrange
        coEvery { repository.getSubscriptionById(SUBSCRIPTION_ID) } returns null

        // Act
        val result = useCase(subscription())

        // Assert
        assertEquals(UpdateSubscriptionUseCase.Result.NotFound, result)
        coVerify(exactly = 0) { repository.updateSubscription(any()) }
    }

    @Test
    fun 対象のサブスクが削除済みの場合はNotFoundを返す() = runTest {
        // Arrange
        coEvery { repository.getSubscriptionById(SUBSCRIPTION_ID) } returns subscription(isActive = false)

        // Act
        val result = useCase(subscription())

        // Assert
        assertEquals(UpdateSubscriptionUseCase.Result.NotFound, result)
        coVerify(exactly = 0) { repository.updateSubscription(any()) }
    }

    @Test
    fun 追加時と同じ規則で検証し全てのエラーを返す() = runTest {
        // Arrange
        val invalid = subscription(
            name = "",
            amountMinor = -1L,
            billingInterval = BillingInterval(BillingIntervalUnit.MONTHLY, 0),
            startDate = LocalDate.of(2026, 7, 1),
            nextBillingDate = LocalDate.of(2026, 6, 1),
        )

        // Act
        val result = useCase(invalid)

        // Assert
        assertEquals(
            UpdateSubscriptionUseCase.Result.ValidationError(
                listOf(
                    SubscriptionValidationError.EMPTY_NAME,
                    SubscriptionValidationError.NEGATIVE_AMOUNT,
                    SubscriptionValidationError.INVALID_INTERVAL_COUNT,
                    SubscriptionValidationError.START_DATE_AFTER_NEXT_BILLING_DATE,
                ),
            ),
            result,
        )
    }

    @Test
    fun バリデーションエラーの場合はrepositoryのupdateを呼ばない() = runTest {
        // Act
        useCase(subscription(name = ""))

        // Assert
        coVerify(exactly = 0) { repository.updateSubscription(any()) }
    }

    private fun subscription(
        name: String = "Netflix",
        amountMinor: Long = 1_490L,
        billingInterval: BillingInterval = BillingInterval(BillingIntervalUnit.MONTHLY, 1),
        startDate: LocalDate = LocalDate.of(2026, 5, 1),
        nextBillingDate: LocalDate = LocalDate.of(2026, 6, 1),
        isActive: Boolean = true,
        createdAt: Instant = CREATED_AT,
        updatedAt: Instant = CREATED_AT,
    ): Subscription {
        return Subscription(
            id = SUBSCRIPTION_ID,
            name = name,
            amountMinor = amountMinor,
            currencyCode = "JPY",
            billingInterval = billingInterval,
            startDate = startDate,
            nextBillingDate = nextBillingDate,
            categoryId = null,
            templateId = null,
            logoUri = null,
            memo = null,
            isActive = isActive,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    companion object {
        private const val SUBSCRIPTION_ID = 7L
        private const val SAVED_SORT_ORDER = 4
        private val CREATED_AT: Instant = Instant.ofEpochMilli(1_778_760_000_000L)
        private val EDITED_AT: Instant = Instant.ofEpochMilli(1_778_846_400_000L)
    }
}
