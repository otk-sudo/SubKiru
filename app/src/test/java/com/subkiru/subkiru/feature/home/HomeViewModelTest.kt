package com.subkiru.subkiru.feature.home

import app.cash.turbine.test
import com.subkiru.subkiru.core.domain.model.BillingInterval
import com.subkiru.subkiru.core.domain.model.BillingIntervalUnit
import com.subkiru.subkiru.core.domain.model.Subscription
import com.subkiru.subkiru.core.domain.usecase.DeleteSubscriptionUseCase
import com.subkiru.subkiru.core.domain.usecase.GetSubscriptionsUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private val subscriptionsFlow = MutableSharedFlow<List<Subscription>>(replay = 1)

    private val getSubscriptionsUseCase: GetSubscriptionsUseCase = mockk {
        every { this@mockk.invoke() } returns subscriptionsFlow
    }
    private val deleteSubscriptionUseCase: DeleteSubscriptionUseCase = mockk(relaxUnitFun = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): HomeViewModel {
        return HomeViewModel(
            getSubscriptionsUseCase = getSubscriptionsUseCase,
            deleteSubscriptionUseCase = deleteSubscriptionUseCase,
        )
    }

    @Test
    fun 初期状態はローディング中である() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.isLoading)
            assertEquals(emptyList<Subscription>(), state.subscriptions)
            assertEquals(0L, state.monthlyTotal)
            assertNull(state.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun サブスク一覧を取得するとローディングが解除される() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()

            subscriptionsFlow.emit(SAMPLE_SUBSCRIPTIONS)
            advanceUntilIdle()

            val state = awaitItem()
            assertFalse(state.isLoading)
            assertEquals(SAMPLE_SUBSCRIPTIONS.size, state.subscriptions.size)
            assertEquals(SAMPLE_SUBSCRIPTIONS[0].name, state.subscriptions[0].name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun 月額合計がサブスク一覧から自動計算される() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()

            subscriptionsFlow.emit(SAMPLE_SUBSCRIPTIONS)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals(EXPECTED_MONTHLY_TOTAL, state.monthlyTotal)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun 空のサブスク一覧が正しく反映される() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()

            subscriptionsFlow.emit(emptyList())
            advanceUntilIdle()

            val state = awaitItem()
            assertFalse(state.isLoading)
            assertTrue(state.subscriptions.isEmpty())
            assertEquals(0L, state.monthlyTotal)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun サブスク取得でエラーが発生するとエラー状態になる() = runTest {
        val errorGetSubscriptionsUseCase: GetSubscriptionsUseCase = mockk {
            every { this@mockk.invoke() } returns flow { throw RuntimeException("DB error") }
        }
        val viewModel = HomeViewModel(
            getSubscriptionsUseCase = errorGetSubscriptionsUseCase,
            deleteSubscriptionUseCase = deleteSubscriptionUseCase,
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(HomeViewModel.ERROR_MESSAGE_LOAD, state.error)
        assertTrue(state.subscriptions.isEmpty())
        assertEquals(0L, state.monthlyTotal)
    }

    @Test
    fun サブスク削除がUseCaseに委譲される() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onDeleteSubscription(TARGET_SUBSCRIPTION_ID)
        advanceUntilIdle()

        coVerify(exactly = 1) { deleteSubscriptionUseCase.invoke(TARGET_SUBSCRIPTION_ID) }
    }

    @Test
    fun サブスク削除でエラーが発生するとエラーイベントが発行される() = runTest {
        coEvery { deleteSubscriptionUseCase.invoke(any()) } throws RuntimeException("削除失敗")

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.errorEvent.test {
            viewModel.onDeleteSubscription(TARGET_SUBSCRIPTION_ID)
            advanceUntilIdle()

            val message = awaitItem()
            assertEquals(HomeViewModel.ERROR_MESSAGE_DELETE, message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    companion object {
        private const val TARGET_SUBSCRIPTION_ID = 1L
        private const val EXPECTED_MONTHLY_TOTAL = 2_470L

        private val SAMPLE_SUBSCRIPTIONS = listOf(
            Subscription(
                id = 1L,
                name = "Netflix",
                amountMinor = 1_490L,
                currencyCode = "JPY",
                billingInterval = BillingInterval(BillingIntervalUnit.MONTHLY, 1),
                startDate = LocalDate.of(2025, 1, 1),
                nextBillingDate = LocalDate.of(2026, 6, 1),
                categoryId = null,
                templateId = null,
                logoUri = null,
                memo = null,
                isActive = true,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            ),
            Subscription(
                id = 2L,
                name = "Spotify",
                amountMinor = 980L,
                currencyCode = "JPY",
                billingInterval = BillingInterval(BillingIntervalUnit.MONTHLY, 1),
                startDate = LocalDate.of(2025, 3, 15),
                nextBillingDate = LocalDate.of(2026, 6, 15),
                categoryId = null,
                templateId = null,
                logoUri = null,
                memo = null,
                isActive = true,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            ),
        )
    }
}
