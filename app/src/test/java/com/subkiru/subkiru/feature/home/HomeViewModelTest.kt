package com.subkiru.subkiru.feature.home

import app.cash.turbine.test
import com.subkiru.subkiru.core.domain.repository.CategoryRepository
import com.subkiru.subkiru.core.domain.model.BillingInterval
import com.subkiru.subkiru.core.domain.model.BillingIntervalUnit
import com.subkiru.subkiru.core.domain.model.MoneyAmount
import com.subkiru.subkiru.core.domain.model.Subscription
import com.subkiru.subkiru.core.domain.usecase.DeleteSubscriptionUseCase
import com.subkiru.subkiru.core.domain.usecase.GetSubscriptionsUseCase
import com.subkiru.subkiru.core.domain.usecase.ReorderSubscriptionsUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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
    private val reorderSubscriptionsUseCase: ReorderSubscriptionsUseCase = mockk(relaxUnitFun = true)
    private val categoryRepository: CategoryRepository = mockk {
        every { observeAllCategories() } returns flowOf(emptyList())
    }

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
            reorderSubscriptionsUseCase = reorderSubscriptionsUseCase,
            categoryRepository = categoryRepository,
        )
    }

    @Test
    fun 初期状態はローディング中である() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.isLoading)
            assertEquals(emptyList<Subscription>(), state.subscriptions)
            assertEquals(emptyList<MoneyAmount>(), state.monthlyTotals)
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
            assertEquals(EXPECTED_MONTHLY_TOTALS, state.monthlyTotals)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun 年間合計は月額合計の12倍になる() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()

            subscriptionsFlow.emit(SAMPLE_SUBSCRIPTIONS)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals(listOf(MoneyAmount(2_470L * 12, "JPY")), state.yearlyTotals)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun 異なる通貨のサブスクは通貨ごとに合計される() = runTest {
        val viewModel = createViewModel()
        val usdSubscription = SAMPLE_SUBSCRIPTIONS[0].copy(id = 99L, amountMinor = 999L, currencyCode = "USD")

        viewModel.uiState.test {
            awaitItem()

            subscriptionsFlow.emit(SAMPLE_SUBSCRIPTIONS + usdSubscription)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals(
                listOf(MoneyAmount(2_470L, "JPY"), MoneyAmount(999L, "USD")),
                state.monthlyTotals,
            )
            assertEquals(
                listOf(MoneyAmount(2_470L * 12, "JPY"), MoneyAmount(999L * 12, "USD")),
                state.yearlyTotals,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun 初期の並び順は請求日順である() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()

            subscriptionsFlow.emit(SAMPLE_SUBSCRIPTIONS.reversed())
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals(HomeSortOrder.BILLING_DATE, state.sortOrder)
            assertEquals(listOf(1L, 2L), state.subscriptions.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun 高い順を選ぶと月額換算の降順に並ぶ() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()
            subscriptionsFlow.emit(SAMPLE_SUBSCRIPTIONS.reversed())
            advanceUntilIdle()
            expectMostRecentItem()

            viewModel.onSortOrderChange(HomeSortOrder.AMOUNT_HIGH)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals(HomeSortOrder.AMOUNT_HIGH, state.sortOrder)
            assertEquals(listOf(1L, 2L), state.subscriptions.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun 安い順を選ぶと月額換算の昇順に並ぶ() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()
            subscriptionsFlow.emit(SAMPLE_SUBSCRIPTIONS)
            advanceUntilIdle()
            expectMostRecentItem()

            viewModel.onSortOrderChange(HomeSortOrder.AMOUNT_LOW)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals(listOf(2L, 1L), state.subscriptions.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun 高い順は通貨ごとにまとめて通貨内で降順に並ぶ() = runTest {
        // 金額の数値だけ比べると USD(999) が JPY(980) より上に来てしまう組み合わせ
        val usdSubscription = SAMPLE_SUBSCRIPTIONS[0].copy(id = 99L, amountMinor = 999L, currencyCode = "USD")
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()
            subscriptionsFlow.emit(listOf(usdSubscription) + SAMPLE_SUBSCRIPTIONS.reversed())
            advanceUntilIdle()
            expectMostRecentItem()

            viewModel.onSortOrderChange(HomeSortOrder.AMOUNT_HIGH)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals(listOf(1L, 2L, 99L), state.subscriptions.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun 安い順は通貨ごとにまとめて通貨内で昇順に並ぶ() = runTest {
        // 金額の数値だけ比べると USD(999) が JPY の2件の間に入ってしまう組み合わせ
        val usdSubscription = SAMPLE_SUBSCRIPTIONS[0].copy(id = 99L, amountMinor = 999L, currencyCode = "USD")
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()
            subscriptionsFlow.emit(listOf(usdSubscription) + SAMPLE_SUBSCRIPTIONS)
            advanceUntilIdle()
            expectMostRecentItem()

            viewModel.onSortOrderChange(HomeSortOrder.AMOUNT_LOW)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals(listOf(2L, 1L, 99L), state.subscriptions.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun 新しい順を選ぶと作成日時の降順に並ぶ() = runTest {
        val subscriptions = listOf(
            SAMPLE_SUBSCRIPTIONS[0].copy(createdAt = Instant.parse("2026-01-01T00:00:00Z")),
            SAMPLE_SUBSCRIPTIONS[1].copy(createdAt = Instant.parse("2026-05-01T00:00:00Z")),
        )
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()
            subscriptionsFlow.emit(subscriptions)
            advanceUntilIdle()
            expectMostRecentItem()

            viewModel.onSortOrderChange(HomeSortOrder.NEWEST)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals(listOf(2L, 1L), state.subscriptions.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun カスタム順を選ぶと取得順を維持する() = runTest {
        val sourceOrder = SAMPLE_SUBSCRIPTIONS.reversed()
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()
            subscriptionsFlow.emit(sourceOrder)
            advanceUntilIdle()
            expectMostRecentItem()

            viewModel.onSortOrderChange(HomeSortOrder.CUSTOM)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals(sourceOrder.map { it.id }, state.subscriptions.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun カスタム順を選ぶと並び順の昇順で並ぶ() = runTest {
        val sourceOrder = listOf(
            SAMPLE_SUBSCRIPTIONS[0].copy(sortOrder = 2),
            SAMPLE_SUBSCRIPTIONS[1].copy(sortOrder = 0),
        )
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()
            subscriptionsFlow.emit(sourceOrder)
            advanceUntilIdle()
            expectMostRecentItem()

            viewModel.onSortOrderChange(HomeSortOrder.CUSTOM)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals(listOf(2L, 1L), state.subscriptions.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun 並べ替えがUseCaseに委譲される() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onReorderSubscriptions(listOf(2L, 1L))
        advanceUntilIdle()

        coVerify(exactly = 1) { reorderSubscriptionsUseCase.invoke(listOf(2L, 1L)) }
    }

    @Test
    fun 並べ替えでエラーが発生するとエラーイベントが発行される() = runTest {
        coEvery { reorderSubscriptionsUseCase.invoke(any()) } throws RuntimeException("並べ替え失敗")
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.errorEvent.test {
            viewModel.onReorderSubscriptions(listOf(2L, 1L))
            advanceUntilIdle()

            assertEquals(HomeViewModel.ERROR_MESSAGE_REORDER, awaitItem())
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
            assertEquals(emptyList<MoneyAmount>(), state.monthlyTotals)
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
            reorderSubscriptionsUseCase = reorderSubscriptionsUseCase,
            categoryRepository = categoryRepository,
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(HomeViewModel.ERROR_MESSAGE_LOAD, state.error)
        assertTrue(state.subscriptions.isEmpty())
        assertEquals(emptyList<MoneyAmount>(), state.monthlyTotals)
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
        private val EXPECTED_MONTHLY_TOTALS = listOf(MoneyAmount(2_470L, "JPY"))

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
