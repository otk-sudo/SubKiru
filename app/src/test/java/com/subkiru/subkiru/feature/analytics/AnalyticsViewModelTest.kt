package com.subkiru.subkiru.feature.analytics

import app.cash.turbine.test
import com.subkiru.subkiru.core.domain.model.BillingInterval
import com.subkiru.subkiru.core.domain.model.BillingIntervalUnit
import com.subkiru.subkiru.core.domain.model.Subscription
import com.subkiru.subkiru.core.domain.usecase.GetSubscriptionsUseCase
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private val subscriptionsFlow = MutableSharedFlow<List<Subscription>>(replay = 1)

    private val getSubscriptionsUseCase: GetSubscriptionsUseCase = mockk {
        every { this@mockk.invoke() } returns subscriptionsFlow
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): AnalyticsViewModel {
        return AnalyticsViewModel(
            getSubscriptionsUseCase = getSubscriptionsUseCase,
        )
    }

    @Test
    fun 初期状態はローディング中である() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.isLoading)
            assertEquals(0L, state.monthlyTotal)
            assertEquals(0, state.subscriptionCount)
            assertTrue(state.breakdowns.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun サブスク一覧から月額合計と年額換算が計算される() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()

            subscriptionsFlow.emit(SAMPLE_SUBSCRIPTIONS)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertFalse(state.isLoading)
            assertEquals(EXPECTED_MONTHLY_TOTAL, state.monthlyTotal)
            assertEquals(EXPECTED_MONTHLY_TOTAL * 12, state.annualTotal)
            assertEquals(SAMPLE_SUBSCRIPTIONS.size, state.subscriptionCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun 内訳が月額降順でソートされる() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()

            subscriptionsFlow.emit(SAMPLE_SUBSCRIPTIONS)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals(SAMPLE_SUBSCRIPTIONS.size, state.breakdowns.size)
            assertEquals("Netflix", state.breakdowns[0].name)
            assertEquals("Spotify", state.breakdowns[1].name)
            assertTrue(state.breakdowns[0].monthlyAmount >= state.breakdowns[1].monthlyAmount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun パーセンテージが正しく計算される() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()

            subscriptionsFlow.emit(SAMPLE_SUBSCRIPTIONS)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            val totalPercentage = state.breakdowns.sumOf { it.percentage.toDouble() }
            assertTrue(totalPercentage in 0.99..1.01)
            state.breakdowns.forEach { breakdown ->
                assertTrue(breakdown.percentage in 0f..1f)
            }
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
            assertEquals(0L, state.monthlyTotal)
            assertEquals(0L, state.annualTotal)
            assertEquals(0, state.subscriptionCount)
            assertTrue(state.breakdowns.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun サブスク取得でエラーが発生するとエラー状態になる() = runTest {
        val errorFlow = flow<List<Subscription>> {
            throw RuntimeException("DB error")
        }
        val errorGetSubscriptionsUseCase: GetSubscriptionsUseCase = mockk {
            every { this@mockk.invoke() } returns errorFlow
        }
        val viewModel = AnalyticsViewModel(
            getSubscriptionsUseCase = errorGetSubscriptionsUseCase,
        )

        viewModel.uiState.test {
            awaitItem()
            advanceUntilIdle()

            val state = awaitItem()
            assertFalse(state.isLoading)
            assertEquals(AnalyticsViewModel.ERROR_MESSAGE_LOAD, state.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun 年額サブスクの月額換算が正しい() = runTest {
        val viewModel = createViewModel()
        val yearlySubscription = listOf(
            createSubscription(
                id = 1L,
                name = "Amazon Prime",
                amountMinor = 5_900L,
                unit = BillingIntervalUnit.YEARLY,
                count = 1,
            ),
        )

        viewModel.uiState.test {
            awaitItem()

            subscriptionsFlow.emit(yearlySubscription)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals(491L, state.monthlyTotal)
            assertEquals(491L * 12, state.annualTotal)
            assertEquals(1, state.breakdowns.size)
            assertEquals(491L, state.breakdowns[0].monthlyAmount)
            assertEquals(1.0f, state.breakdowns[0].percentage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    companion object {
        private const val EXPECTED_MONTHLY_TOTAL = 2_470L

        private val SAMPLE_SUBSCRIPTIONS = listOf(
            createSubscription(
                id = 1L,
                name = "Netflix",
                amountMinor = 1_490L,
                unit = BillingIntervalUnit.MONTHLY,
                count = 1,
            ),
            createSubscription(
                id = 2L,
                name = "Spotify",
                amountMinor = 980L,
                unit = BillingIntervalUnit.MONTHLY,
                count = 1,
            ),
        )

        private fun createSubscription(
            id: Long,
            name: String,
            amountMinor: Long,
            unit: BillingIntervalUnit,
            count: Int,
        ): Subscription = Subscription(
            id = id,
            name = name,
            amountMinor = amountMinor,
            currencyCode = "JPY",
            billingInterval = BillingInterval(unit, count),
            startDate = LocalDate.of(2025, 1, 1),
            nextBillingDate = LocalDate.of(2026, 6, 1),
            categoryId = null,
            templateId = null,
            logoUri = null,
            memo = null,
            isActive = true,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
    }
}
