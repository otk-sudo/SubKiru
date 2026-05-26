package com.subkiru.subkiru.feature.add

import app.cash.turbine.test
import com.subkiru.subkiru.core.domain.usecase.AddSubscriptionUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class AddSubscriptionViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val fixedClock: Clock = Clock.fixed(
        Instant.parse("2026-05-24T00:00:00Z"),
        ZoneId.of("Asia/Tokyo"),
    )
    private val addSubscriptionUseCase: AddSubscriptionUseCase = mockk()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): AddSubscriptionViewModel {
        return AddSubscriptionViewModel(
            addSubscriptionUseCase = addSubscriptionUseCase,
            clock = fixedClock,
        )
    }

    @Test
    fun 初期状態が正しい() = runTest {
        val viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertEquals("", state.name)
        assertEquals("", state.amountText)
        assertEquals("1", state.billingIntervalCountText)
        assertEquals("2026/05/24", state.startDateText)
        assertEquals("2026/06/24", state.nextBillingDateText)
        assertFalse(state.isSaving)
        assertNull(state.nameError)
        assertNull(state.amountError)
    }

    @Test
    fun 名前変更でエラーがクリアされる() = runTest {
        val viewModel = createViewModel()

        viewModel.onSave()
        advanceUntilIdle()

        viewModel.onNameChange("Netflix")
        val state = viewModel.uiState.value
        assertEquals("Netflix", state.name)
        assertNull(state.nameError)
    }

    @Test
    fun 不正な金額で保存するとエラーになる() = runTest {
        val viewModel = createViewModel()

        viewModel.onNameChange("Netflix")
        viewModel.onAmountChange("abc")
        viewModel.onSave()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(AddSubscriptionViewModel.ERROR_INVALID_AMOUNT, state.amountError)
        assertFalse(state.isSaving)
    }

    @Test
    fun 不正な日付形式で保存するとエラーになる() = runTest {
        val viewModel = createViewModel()

        viewModel.onNameChange("Netflix")
        viewModel.onAmountChange("1490")
        viewModel.onStartDateChange("2026/13/01")
        viewModel.onSave()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(AddSubscriptionViewModel.ERROR_INVALID_DATE, state.startDateError)
        assertFalse(state.isSaving)
    }

    @Test
    fun 不正な次回請求日形式で保存するとエラーになる() = runTest {
        val viewModel = createViewModel()

        viewModel.onNameChange("Netflix")
        viewModel.onAmountChange("1490")
        viewModel.onNextBillingDateChange("invalid")
        viewModel.onSave()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(AddSubscriptionViewModel.ERROR_INVALID_DATE, state.nextBillingDateError)
        assertFalse(state.isSaving)
    }

    @Test
    fun 不正な間隔数で保存するとエラーになる() = runTest {
        val viewModel = createViewModel()

        viewModel.onNameChange("Netflix")
        viewModel.onAmountChange("1490")
        viewModel.onBillingIntervalCountChange("")
        viewModel.onSave()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(AddSubscriptionViewModel.ERROR_INVALID_INTERVAL, state.intervalError)
        assertFalse(state.isSaving)
    }

    @Test
    fun バリデーションエラーがフィールドに表示される() = runTest {
        coEvery { addSubscriptionUseCase.invoke(any()) } returns
            AddSubscriptionUseCase.Result.ValidationError(
                listOf(
                    AddSubscriptionUseCase.Error.EMPTY_NAME,
                    AddSubscriptionUseCase.Error.NEGATIVE_AMOUNT,
                ),
            )

        val viewModel = createViewModel()
        viewModel.onAmountChange("-100")
        viewModel.onSave()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(AddSubscriptionViewModel.ERROR_EMPTY_NAME, state.nameError)
        assertEquals(AddSubscriptionViewModel.ERROR_NEGATIVE_AMOUNT, state.amountError)
        assertFalse(state.isSaving)
    }

    @Test
    fun 保存成功でsavedEventが発行される() = runTest {
        coEvery { addSubscriptionUseCase.invoke(any()) } returns
            AddSubscriptionUseCase.Result.Success(id = 1L)

        val viewModel = createViewModel()
        viewModel.onNameChange("Netflix")
        viewModel.onAmountChange("1490")

        viewModel.savedEvent.test {
            viewModel.onSave()
            advanceUntilIdle()

            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun 保存成功時にUseCaseが呼ばれる() = runTest {
        coEvery { addSubscriptionUseCase.invoke(any()) } returns
            AddSubscriptionUseCase.Result.Success(id = 1L)

        val viewModel = createViewModel()
        viewModel.onNameChange("Netflix")
        viewModel.onAmountChange("1490")
        viewModel.onSave()
        advanceUntilIdle()

        coVerify(exactly = 1) { addSubscriptionUseCase.invoke(any()) }
    }

    @Test
    fun 保存中の例外でエラーイベントが発行される() = runTest {
        coEvery { addSubscriptionUseCase.invoke(any()) } throws RuntimeException("DB error")

        val viewModel = createViewModel()
        viewModel.onNameChange("Netflix")
        viewModel.onAmountChange("1490")

        viewModel.errorEvent.test {
            viewModel.onSave()
            advanceUntilIdle()

            val message = awaitItem()
            assertEquals(AddSubscriptionViewModel.ERROR_SAVE_FAILED, message)
            cancelAndIgnoreRemainingEvents()
        }

        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun メモが空文字の場合nullに変換される() = runTest {
        coEvery { addSubscriptionUseCase.invoke(any()) } returns
            AddSubscriptionUseCase.Result.Success(id = 1L)

        val viewModel = createViewModel()
        viewModel.onNameChange("Netflix")
        viewModel.onAmountChange("1490")
        viewModel.onMemoChange("   ")
        viewModel.onSave()
        advanceUntilIdle()

        coVerify {
            addSubscriptionUseCase.invoke(match { it.memo == null })
        }
    }
}
