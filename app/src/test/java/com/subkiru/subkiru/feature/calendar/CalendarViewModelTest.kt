package com.subkiru.subkiru.feature.calendar

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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private val fixedClock = Clock.fixed(
        Instant.parse("2026-05-24T00:00:00Z"),
        ZoneId.of("Asia/Tokyo"),
    )

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

    private fun createViewModel(): CalendarViewModel {
        return CalendarViewModel(
            getSubscriptionsUseCase = getSubscriptionsUseCase,
            clock = fixedClock,
        )
    }

    @Test
    fun 初期状態はローディング中である() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.isLoading)
            assertEquals(YearMonth.of(2026, 5), state.displayedYearMonth)
            assertEquals(LocalDate.of(2026, 5, 24), state.today)
            assertTrue(state.billingsByDay.isEmpty())
            assertNull(state.selectedDate)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun サブスクの請求日がカレンダーに反映される() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()

            subscriptionsFlow.emit(SAMPLE_SUBSCRIPTIONS)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertFalse(state.isLoading)
            assertEquals("Netflix", state.billingsByDay.getValue(15).single().name)
            assertEquals("Spotify", state.billingsByDay.getValue(20).single().name)
            assertEquals(2_470L, state.monthlyTotal)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun 次の月に進むと表示月が更新される() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()

            subscriptionsFlow.emit(SAMPLE_SUBSCRIPTIONS)
            advanceUntilIdle()
            expectMostRecentItem()

            viewModel.onNextMonth()
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals(YearMonth.of(2026, 6), state.displayedYearMonth)
            assertNull(state.selectedDate)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun 前の月に戻ると表示月が更新される() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()

            subscriptionsFlow.emit(SAMPLE_SUBSCRIPTIONS)
            advanceUntilIdle()
            expectMostRecentItem()

            viewModel.onPreviousMonth()
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals(YearMonth.of(2026, 4), state.displayedYearMonth)
            assertNull(state.selectedDate)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun 日付を選択すると該当日の請求一覧が表示される() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()

            subscriptionsFlow.emit(SAMPLE_SUBSCRIPTIONS)
            advanceUntilIdle()
            expectMostRecentItem()

            viewModel.onDateSelected(LocalDate.of(2026, 5, 15))
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals(LocalDate.of(2026, 5, 15), state.selectedDate)
            assertEquals(1, state.selectedDateBillings.size)
            assertEquals("Netflix", state.selectedDateBillings[0].name)
            assertEquals(1_490L, state.selectedDateBillings[0].amountMinor)
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
        val viewModel = CalendarViewModel(
            getSubscriptionsUseCase = errorGetSubscriptionsUseCase,
            clock = fixedClock,
        )

        viewModel.uiState.test {
            awaitItem()
            advanceUntilIdle()

            val state = awaitItem()
            assertFalse(state.isLoading)
            assertEquals(CalendarViewModel.ERROR_MESSAGE_LOAD, state.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun 月額サブスクが別の月にも請求日として計算される() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()

            subscriptionsFlow.emit(SAMPLE_SUBSCRIPTIONS)
            advanceUntilIdle()
            expectMostRecentItem()

            viewModel.onNextMonth()
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals(YearMonth.of(2026, 6), state.displayedYearMonth)
            assertTrue(state.billingsByDay.containsKey(15))
            assertTrue(state.billingsByDay.containsKey(20))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun 週次サブスクが月内に複数の請求日を持つ() = runTest {
        val viewModel = createViewModel()
        val weeklySubscription = listOf(
            createSubscription(
                id = 3L,
                name = "Weekly Service",
                amountMinor = 500L,
                unit = BillingIntervalUnit.WEEKLY,
                count = 1,
                nextBillingDate = LocalDate.of(2026, 5, 1),
            ),
        )

        viewModel.uiState.test {
            awaitItem()

            subscriptionsFlow.emit(weeklySubscription)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals(5, state.billingsByDay.size)
            assertTrue(state.billingsByDay.keys.containsAll(setOf(1, 8, 15, 22, 29)))
            assertEquals(2_500L, state.monthlyTotal)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun 年次サブスクが該当月以外では請求日なし() = runTest {
        val viewModel = createViewModel()
        val yearlySubscription = listOf(
            createSubscription(
                id = 4L,
                name = "Yearly Service",
                amountMinor = 12_000L,
                unit = BillingIntervalUnit.YEARLY,
                count = 1,
                nextBillingDate = LocalDate.of(2026, 3, 15),
            ),
        )

        viewModel.uiState.test {
            awaitItem()

            subscriptionsFlow.emit(yearlySubscription)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertTrue(state.billingsByDay.isEmpty())
            assertEquals(0L, state.monthlyTotal)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun 同じ日の複数請求がサービス情報付きでまとめられる() = runTest {
        val viewModel = createViewModel()
        val sameDaySubscriptions = SAMPLE_SUBSCRIPTIONS.map {
            it.copy(nextBillingDate = LocalDate.of(2026, 5, 15))
        }

        viewModel.uiState.test {
            awaitItem()
            subscriptionsFlow.emit(sameDaySubscriptions)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            val marks = state.billingsByDay.getValue(15)
            assertEquals(listOf("Netflix", "Spotify"), marks.map { it.name })
            assertEquals(listOf(1L, 2L), marks.map { it.subscriptionId })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun 今月ボタンを押すと表示月が今月に戻る() = runTest {
        // Arrange
        val viewModel = createViewModel()
        viewModel.uiState.test {
            awaitItem()
            subscriptionsFlow.emit(emptyList())
            advanceUntilIdle()
            expectMostRecentItem()

            // 3ヶ月先に移動
            viewModel.onNextMonth()
            viewModel.onNextMonth()
            viewModel.onNextMonth()
            advanceUntilIdle()
            expectMostRecentItem()

            // Act
            viewModel.onGoToCurrentMonth()
            advanceUntilIdle()

            // Assert
            val state = expectMostRecentItem()
            assertEquals(YearMonth.of(2026, 5), state.displayedYearMonth)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun 今月ボタンを押すと選択日がnullにリセットされる() = runTest {
        // Arrange
        val viewModel = createViewModel()
        viewModel.uiState.test {
            awaitItem()
            subscriptionsFlow.emit(SAMPLE_SUBSCRIPTIONS)
            advanceUntilIdle()
            expectMostRecentItem()

            viewModel.onNextMonth()
            advanceUntilIdle()
            expectMostRecentItem()

            viewModel.onDateSelected(LocalDate.of(2026, 6, 15))
            advanceUntilIdle()
            expectMostRecentItem()

            // Act
            viewModel.onGoToCurrentMonth()
            advanceUntilIdle()

            // Assert
            val state = expectMostRecentItem()
            assertNull(state.selectedDate)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun 既に今月を表示中でも今月ボタンを押してもエラーにならない() = runTest {
        // Arrange
        val viewModel = createViewModel()
        viewModel.uiState.test {
            awaitItem()
            subscriptionsFlow.emit(emptyList())
            advanceUntilIdle()
            expectMostRecentItem()

            // Act: 今月のまま呼び出し（例外が発生しないことを確認）
            viewModel.onGoToCurrentMonth()
            advanceUntilIdle()

            // Assert: 状態変化がないので直接 value を検証
            val state = viewModel.uiState.value
            assertEquals(YearMonth.of(2026, 5), state.displayedYearMonth)
            cancelAndIgnoreRemainingEvents()
        }
    }

    companion object {
        private val SAMPLE_SUBSCRIPTIONS = listOf(
            createSubscription(
                id = 1L,
                name = "Netflix",
                amountMinor = 1_490L,
                unit = BillingIntervalUnit.MONTHLY,
                count = 1,
                nextBillingDate = LocalDate.of(2026, 5, 15),
            ),
            createSubscription(
                id = 2L,
                name = "Spotify",
                amountMinor = 980L,
                unit = BillingIntervalUnit.MONTHLY,
                count = 1,
                nextBillingDate = LocalDate.of(2026, 5, 20),
            ),
        )

        private fun createSubscription(
            id: Long,
            name: String,
            amountMinor: Long,
            unit: BillingIntervalUnit,
            count: Int,
            nextBillingDate: LocalDate,
        ): Subscription = Subscription(
            id = id,
            name = name,
            amountMinor = amountMinor,
            currencyCode = "JPY",
            billingInterval = BillingInterval(unit, count),
            startDate = LocalDate.of(2025, 1, 1),
            nextBillingDate = nextBillingDate,
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
