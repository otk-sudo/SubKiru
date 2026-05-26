package com.subkiru.subkiru.core.domain.usecase

import com.subkiru.subkiru.core.domain.model.BillingInterval
import com.subkiru.subkiru.core.domain.model.BillingIntervalUnit
import com.subkiru.subkiru.core.domain.model.Subscription
import com.subkiru.subkiru.core.domain.repository.SubscriptionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class AddSubscriptionUseCaseTest {
    private val repository = mockk<SubscriptionRepository>()
    private val useCase = AddSubscriptionUseCase(repository)

    @Test
    fun 正常なサブスクを追加するとSuccessを返す() = runTest {
        val subscription = subscription()
        coEvery { repository.addSubscription(subscription) } returns ADDED_ID

        val result = useCase(subscription)

        assertEquals(AddSubscriptionUseCase.Result.Success(ADDED_ID), result)
        coVerify(exactly = 1) { repository.addSubscription(subscription) }
    }

    @Test
    fun 名前が空文字の場合はEMPTY_NAMEエラーを返す() = runTest {
        val result = useCase(subscription(name = ""))

        assertValidationErrors(result, listOf(AddSubscriptionUseCase.Error.EMPTY_NAME))
    }

    @Test
    fun 金額が負の場合はNEGATIVE_AMOUNTエラーを返す() = runTest {
        val result = useCase(subscription(amountMinor = -1L))

        assertValidationErrors(result, listOf(AddSubscriptionUseCase.Error.NEGATIVE_AMOUNT))
    }

    @Test
    fun 請求間隔countが0以下の場合はINVALID_INTERVAL_COUNTエラーを返す() = runTest {
        val result = useCase(
            subscription(billingInterval = BillingInterval(BillingIntervalUnit.MONTHLY, 0))
        )

        assertValidationErrors(result, listOf(AddSubscriptionUseCase.Error.INVALID_INTERVAL_COUNT))
    }

    @Test
    fun 開始日が次回請求日より後の場合はSTART_DATE_AFTER_NEXT_BILLING_DATEエラーを返す() = runTest {
        val result = useCase(
            subscription(
                startDate = LocalDate.of(2026, 6, 2),
                nextBillingDate = LocalDate.of(2026, 6, 1),
            )
        )

        assertValidationErrors(
            result,
            listOf(AddSubscriptionUseCase.Error.START_DATE_AFTER_NEXT_BILLING_DATE),
        )
    }

    @Test
    fun 複数のバリデーションエラーがある場合は全てのエラーを返す() = runTest {
        val result = useCase(
            subscription(
                name = "",
                amountMinor = -1L,
                billingInterval = BillingInterval(BillingIntervalUnit.MONTHLY, 0),
                startDate = LocalDate.of(2026, 6, 2),
                nextBillingDate = LocalDate.of(2026, 6, 1),
            )
        )

        assertValidationErrors(
            result,
            listOf(
                AddSubscriptionUseCase.Error.EMPTY_NAME,
                AddSubscriptionUseCase.Error.NEGATIVE_AMOUNT,
                AddSubscriptionUseCase.Error.INVALID_INTERVAL_COUNT,
                AddSubscriptionUseCase.Error.START_DATE_AFTER_NEXT_BILLING_DATE,
            ),
        )
    }

    @Test
    fun バリデーションエラーの場合はrepositoryのaddを呼ばない() = runTest {
        useCase(subscription(name = ""))

        coVerify(exactly = 0) { repository.addSubscription(any()) }
    }

    private fun assertValidationErrors(
        result: AddSubscriptionUseCase.Result,
        expected: List<AddSubscriptionUseCase.Error>,
    ) {
        assertTrue(result is AddSubscriptionUseCase.Result.ValidationError)
        val validationError = result as AddSubscriptionUseCase.Result.ValidationError
        assertEquals(expected, validationError.errors)
    }

    private fun subscription(
        name: String = "Netflix",
        amountMinor: Long = 1_490L,
        billingInterval: BillingInterval = BillingInterval(BillingIntervalUnit.MONTHLY, 1),
        startDate: LocalDate = LocalDate.of(2026, 5, 1),
        nextBillingDate: LocalDate = LocalDate.of(2026, 6, 1),
    ): Subscription {
        return Subscription(
            id = 0L,
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
            isActive = true,
            createdAt = Instant.ofEpochMilli(1_778_760_000_000L),
            updatedAt = Instant.ofEpochMilli(1_778_760_000_000L),
        )
    }

    companion object {
        private const val ADDED_ID = 1L
    }
}
