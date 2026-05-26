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

    @Test
    fun 開始日変更時に次回請求日がMONTHLY1ヶ月後に自動計算される() = runTest {
        // Arrange
        val viewModel = createViewModel()
        // billingIntervalUnit = MONTHLY, billingIntervalCountText = "1" がデフォルト

        // Act
        viewModel.onStartDateChange("2026/03/15")

        // Assert
        val state = viewModel.uiState.value
        assertEquals("2026/04/15", state.nextBillingDateText)
    }

    @Test
    fun 請求単位をYEARLYに変更すると次回請求日が再計算される() = runTest {
        // Arrange
        val viewModel = createViewModel()
        viewModel.onStartDateChange("2026/03/15")
        // この時点では MONTHLY 1ヶ月後 = 2026/04/15

        // Act
        viewModel.onBillingIntervalUnitChange(com.subkiru.subkiru.core.domain.model.BillingIntervalUnit.YEARLY)

        // Assert
        val state = viewModel.uiState.value
        assertEquals("2027/03/15", state.nextBillingDateText)
    }

    @Test
    fun 請求間隔を3に変更すると次回請求日が3ヶ月後に再計算される() = runTest {
        // Arrange
        val viewModel = createViewModel()
        viewModel.onStartDateChange("2026/03/15")
        // billingIntervalUnit = MONTHLY

        // Act
        viewModel.onBillingIntervalCountChange("3")

        // Assert
        val state = viewModel.uiState.value
        assertEquals("2026/06/15", state.nextBillingDateText)
    }

    @Test
    fun 月末エッジケース_1月31日にMONTHLY1を加算すると2月28日になる() = runTest {
        // Arrange
        val viewModel = createViewModel()
        // billingIntervalUnit = MONTHLY, billingIntervalCountText = "1"

        // Act
        viewModel.onStartDateChange("2026/01/31")

        // Assert: 2月は28日までなので2026/02/28
        val state = viewModel.uiState.value
        assertEquals("2026/02/28", state.nextBillingDateText)
    }

    @Test
    fun 閏年エッジケース_2024年2月29日にYEARLY1を加算すると2025年2月28日になる() = runTest {
        // Arrange
        val viewModel = createViewModel()
        viewModel.onBillingIntervalUnitChange(com.subkiru.subkiru.core.domain.model.BillingIntervalUnit.YEARLY)

        // Act
        viewModel.onStartDateChange("2024/02/29")

        // Assert: 2025年は閏年ではないので2025/02/28
        val state = viewModel.uiState.value
        assertEquals("2025/02/28", state.nextBillingDateText)
    }

    @Test
    fun DAILY_開始日からcount日後が次回請求日になる() = runTest {
        // Arrange
        val viewModel = createViewModel()
        viewModel.onBillingIntervalUnitChange(com.subkiru.subkiru.core.domain.model.BillingIntervalUnit.DAILY)
        viewModel.onBillingIntervalCountChange("10")

        // Act
        viewModel.onStartDateChange("2026/05/01")

        // Assert: 2026/05/01 + 10日 = 2026/05/11
        val state = viewModel.uiState.value
        assertEquals("2026/05/11", state.nextBillingDateText)
    }

    @Test
    fun WEEKLY_開始日からcount週後が次回請求日になる() = runTest {
        // Arrange
        val viewModel = createViewModel()
        viewModel.onBillingIntervalUnitChange(com.subkiru.subkiru.core.domain.model.BillingIntervalUnit.WEEKLY)
        viewModel.onBillingIntervalCountChange("2")

        // Act
        viewModel.onStartDateChange("2026/05/01")

        // Assert: 2026/05/01 + 2週 = 2026/05/15
        val state = viewModel.uiState.value
        assertEquals("2026/05/15", state.nextBillingDateText)
    }

    @Test
    fun 不正な開始日入力時に次回請求日が変更されない() = runTest {
        // Arrange
        val viewModel = createViewModel()
        val previousNextBillingDate = viewModel.uiState.value.nextBillingDateText

        // Act: パースできない文字列を入力
        viewModel.onStartDateChange("abc")

        // Assert
        val state = viewModel.uiState.value
        assertEquals(previousNextBillingDate, state.nextBillingDateText)
    }

    @Test
    fun intervalCountが空文字の時に次回請求日が変更されない() = runTest {
        // Arrange
        val viewModel = createViewModel()
        viewModel.onStartDateChange("2026/05/01")
        val nextBillingDateAfterValidStart = viewModel.uiState.value.nextBillingDateText

        // Act: intervalCount を空文字にしてから開始日を変更
        viewModel.onBillingIntervalCountChange("")
        viewModel.onStartDateChange("2026/06/01")

        // Assert: 空文字は無効なので次回請求日は変更されない
        val state = viewModel.uiState.value
        assertEquals(nextBillingDateAfterValidStart, state.nextBillingDateText)
    }

    @Test
    fun intervalCountが0の時に次回請求日が変更されない() = runTest {
        // Arrange
        val viewModel = createViewModel()
        viewModel.onStartDateChange("2026/05/01")
        val nextBillingDateAfterValidStart = viewModel.uiState.value.nextBillingDateText

        // Act: intervalCount を "0" にしてから開始日を変更
        viewModel.onBillingIntervalCountChange("0")
        viewModel.onStartDateChange("2026/06/01")

        // Assert: 0は無効（1以上の整数が必要）なので次回請求日は変更されない
        val state = viewModel.uiState.value
        assertEquals(nextBillingDateAfterValidStart, state.nextBillingDateText)
    }

    @Test
    fun initブロックで次回請求日がcalculateNextBillingDateで計算される() = runTest {
        // Arrange: Clock を 2026-05-26 に固定（MONTHLY 1ヶ月後 = 2026/06/26 を期待）
        val clock2026_05_26: Clock = Clock.fixed(
            Instant.parse("2026-05-26T00:00:00Z"),
            ZoneId.of("Asia/Tokyo"),
        )

        // Act: ViewModel 生成時の init ブロックで自動計算される
        val viewModel = AddSubscriptionViewModel(
            addSubscriptionUseCase = addSubscriptionUseCase,
            clock = clock2026_05_26,
        )

        // Assert: startDate = 2026/05/26、MONTHLY count=1 → nextBillingDate = 2026/06/26
        val state = viewModel.uiState.value
        assertEquals("2026/05/26", state.startDateText)
        assertEquals("2026/06/26", state.nextBillingDateText)
    }
}
