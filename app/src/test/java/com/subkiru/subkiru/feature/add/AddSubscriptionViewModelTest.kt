package com.subkiru.subkiru.feature.add

import app.cash.turbine.test
import com.subkiru.subkiru.core.domain.usecase.AddSubscriptionUseCase
import com.subkiru.subkiru.core.domain.usecase.GetSubscriptionByIdUseCase
import com.subkiru.subkiru.core.domain.usecase.SubscriptionValidationError
import com.subkiru.subkiru.core.domain.usecase.UpdateSubscriptionUseCase
import com.subkiru.subkiru.core.domain.model.BillingInterval
import com.subkiru.subkiru.core.domain.model.BillingIntervalUnit
import com.subkiru.subkiru.core.domain.model.Subscription
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertTrue
import java.time.LocalDate
import com.subkiru.subkiru.core.domain.repository.CategoryRepository
import com.subkiru.subkiru.core.domain.repository.ServiceTemplateRepository
import com.subkiru.subkiru.core.domain.model.ServiceTemplate
import io.mockk.verify
import kotlinx.coroutines.flow.flow
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
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
    private val updateSubscriptionUseCase: UpdateSubscriptionUseCase = mockk()
    private val getSubscriptionByIdUseCase: GetSubscriptionByIdUseCase = mockk()
    private val categoryRepository: CategoryRepository = mockk {
        every { observeAllCategories() } returns flowOf(emptyList())
    }
    private val serviceTemplateRepository: ServiceTemplateRepository = mockk {
        every { observeAllTemplates() } returns flowOf(SAMPLE_TEMPLATES)
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(subscriptionId: Long? = null): AddSubscriptionViewModel {
        return AddSubscriptionViewModel(
            addSubscriptionUseCase = addSubscriptionUseCase,
            updateSubscriptionUseCase = updateSubscriptionUseCase,
            getSubscriptionByIdUseCase = getSubscriptionByIdUseCase,
            categoryRepository = categoryRepository,
            serviceTemplateRepository = serviceTemplateRepository,
            clock = fixedClock,
            subscriptionId = subscriptionId,
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
                    SubscriptionValidationError.EMPTY_NAME,
                    SubscriptionValidationError.NEGATIVE_AMOUNT,
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
    fun applyTemplate後にonSaveするとlogoUriがnullで保存される() = runTest {
        // Arrange: テンプレートを適用して保存する（旧仕様では Clearbit URL が logoUri に保存されていた）
        coEvery { addSubscriptionUseCase.invoke(any()) } returns
            AddSubscriptionUseCase.Result.Success(id = 1L)

        val viewModel = createViewModel()
        viewModel.applyTemplate(
            templateId = 10L,
            name = "Netflix",
            amountMinor = 1590L,
            unit = com.subkiru.subkiru.core.domain.model.BillingIntervalUnit.MONTHLY,
            count = 1,
            categoryId = 1L,
        )

        // Act
        viewModel.onSave()
        advanceUntilIdle()

        // Assert: 外部ロゴURL（旧Clearbit）は保存されず logoUri は null になる
        coVerify {
            addSubscriptionUseCase.invoke(match { it.logoUri == null })
        }
    }

    @Test
    fun applyTemplate後にonSaveするとtemplateIdが保存される() = runTest {
        // Arrange
        coEvery { addSubscriptionUseCase.invoke(any()) } returns
            AddSubscriptionUseCase.Result.Success(id = 1L)
        val viewModel = createViewModel()
        viewModel.applyTemplate(
            templateId = 10L,
            name = "Netflix",
            amountMinor = 1590L,
            unit = com.subkiru.subkiru.core.domain.model.BillingIntervalUnit.MONTHLY,
            count = 1,
            categoryId = 1L,
        )

        // Act
        viewModel.onSave()
        advanceUntilIdle()

        // Assert
        coVerify {
            addSubscriptionUseCase.invoke(match { it.templateId == 10L })
        }
    }

    @Test
    fun テンプレート適用後にサービス名を書き換えるとtemplateIdがnullで保存される() = runTest {
        // Arrange
        coEvery { addSubscriptionUseCase.invoke(any()) } returns
            AddSubscriptionUseCase.Result.Success(id = 1L)
        val viewModel = createViewModel()
        viewModel.applyTemplate(
            templateId = 10L,
            name = "Netflix",
            amountMinor = 1590L,
            unit = com.subkiru.subkiru.core.domain.model.BillingIntervalUnit.MONTHLY,
            count = 1,
            categoryId = 1L,
        )

        // Act
        viewModel.onNameChange("自作サービス")
        viewModel.onSave()
        advanceUntilIdle()

        // Assert
        coVerify {
            addSubscriptionUseCase.invoke(match { it.templateId == null })
        }
    }

    @Test
    fun テンプレートを使わずに保存するとtemplateIdはnullになる() = runTest {
        // Arrange
        coEvery { addSubscriptionUseCase.invoke(any()) } returns
            AddSubscriptionUseCase.Result.Success(id = 1L)
        val viewModel = createViewModel()
        viewModel.onNameChange("Netflix")
        viewModel.onAmountChange("1490")

        // Act
        viewModel.onSave()
        advanceUntilIdle()

        // Assert
        coVerify {
            addSubscriptionUseCase.invoke(match { it.templateId == null })
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
            updateSubscriptionUseCase = updateSubscriptionUseCase,
            getSubscriptionByIdUseCase = getSubscriptionByIdUseCase,
            categoryRepository = categoryRepository,
            serviceTemplateRepository = serviceTemplateRepository,
            clock = clock2026_05_26,
        )

        // Assert: startDate = 2026/05/26、MONTHLY count=1 → nextBillingDate = 2026/06/26
        val state = viewModel.uiState.value
        assertEquals("2026/05/26", state.startDateText)
        assertEquals("2026/06/26", state.nextBillingDateText)
    }

    @Test
    fun 追加モードでは編集モードではなく読み込み中でもない() = runTest {
        // Arrange & Act
        val viewModel = createViewModel()

        // Assert
        val state = viewModel.uiState.value
        assertFalse(state.isEditMode)
        assertFalse(state.isLoading)
        assertNull(state.loadError)
    }

    @Test
    fun 追加モードではテンプレートの読み込み完了まで読み込み中になる() = runTest {
        // Arrange & Act
        val viewModel = createViewModel()

        // Assert
        assertTrue(viewModel.uiState.value.isTemplatesLoading)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isTemplatesLoading)
    }

    @Test
    fun 追加モードでは読み込んだテンプレートが一覧に反映される() = runTest {
        // Arrange & Act
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Assert
        assertEquals(SAMPLE_TEMPLATES, viewModel.uiState.value.templates)
    }

    @Test
    fun テンプレートの読み込みに失敗しても読み込み中が解除され一覧は空になる() = runTest {
        // Arrange
        every { serviceTemplateRepository.observeAllTemplates() } returns
            flow { throw RuntimeException("DB error") }

        // Act
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Assert
        assertFalse(viewModel.uiState.value.isTemplatesLoading)
        assertTrue(viewModel.uiState.value.templates.isEmpty())
    }

    @Test
    fun 編集モードではテンプレートを読み込まない() = runTest {
        // Arrange
        coEvery { getSubscriptionByIdUseCase.invoke(EDIT_ID) } returns editTarget()

        // Act
        val viewModel = createViewModel(subscriptionId = EDIT_ID)
        advanceUntilIdle()

        // Assert
        assertFalse(viewModel.uiState.value.isTemplatesLoading)
        assertTrue(viewModel.uiState.value.templates.isEmpty())
        verify(exactly = 0) { serviceTemplateRepository.observeAllTemplates() }
    }

    @Test
    fun 編集モードでは読み込み完了まで読み込み中になる() = runTest {
        // Arrange
        coEvery { getSubscriptionByIdUseCase.invoke(EDIT_ID) } returns editTarget()

        // Act
        val viewModel = createViewModel(subscriptionId = EDIT_ID)

        // Assert
        assertTrue(viewModel.uiState.value.isEditMode)
        assertTrue(viewModel.uiState.value.isLoading)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun 編集モードでは既存の値がフォームに初期表示される() = runTest {
        // Arrange
        coEvery { getSubscriptionByIdUseCase.invoke(EDIT_ID) } returns editTarget(
            billingInterval = BillingInterval(BillingIntervalUnit.YEARLY, 2),
            categoryId = 3L,
            templateId = 10L,
            memo = "家族プラン",
        )

        // Act
        val viewModel = createViewModel(subscriptionId = EDIT_ID)
        advanceUntilIdle()

        // Assert
        val state = viewModel.uiState.value
        assertEquals("Netflix", state.name)
        assertEquals("1490", state.amountText)
        assertEquals(BillingIntervalUnit.YEARLY, state.billingIntervalUnit)
        assertEquals("2", state.billingIntervalCountText)
        assertEquals("2026/04/10", state.startDateText)
        assertEquals(3L, state.categoryId)
        assertEquals(10L, state.templateId)
        assertEquals("家族プラン", state.memo)
    }

    @Test
    fun 編集モードの次回請求日は開始日と請求間隔から再計算される() = runTest {
        // Arrange: 保存済みの次回請求日（2026/12/31）ではなく「開始日 + 1回分」になる
        coEvery { getSubscriptionByIdUseCase.invoke(EDIT_ID) } returns editTarget(
            nextBillingDate = LocalDate.of(2026, 12, 31),
        )

        // Act
        val viewModel = createViewModel(subscriptionId = EDIT_ID)
        advanceUntilIdle()

        // Assert
        assertEquals("2026/05/10", viewModel.uiState.value.nextBillingDateText)
    }

    @Test
    fun 編集モードで対象が存在しない場合は読み込みエラーになる() = runTest {
        // Arrange
        coEvery { getSubscriptionByIdUseCase.invoke(EDIT_ID) } returns null

        // Act
        val viewModel = createViewModel(subscriptionId = EDIT_ID)
        advanceUntilIdle()

        // Assert
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(AddSubscriptionViewModel.ERROR_NOT_FOUND, state.loadError)
    }

    @Test
    fun 編集モードで読み込みに失敗した場合は読み込みエラーになる() = runTest {
        // Arrange
        coEvery { getSubscriptionByIdUseCase.invoke(EDIT_ID) } throws RuntimeException("DB error")

        // Act
        val viewModel = createViewModel(subscriptionId = EDIT_ID)
        advanceUntilIdle()

        // Assert
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(AddSubscriptionViewModel.ERROR_LOAD_FAILED, state.loadError)
    }

    @Test
    fun 編集モードで保存するとUpdateSubscriptionUseCaseが呼ばれAddは呼ばれない() = runTest {
        // Arrange
        coEvery { getSubscriptionByIdUseCase.invoke(EDIT_ID) } returns editTarget()
        coEvery { updateSubscriptionUseCase.invoke(any()) } returns UpdateSubscriptionUseCase.Result.Success
        val viewModel = createViewModel(subscriptionId = EDIT_ID)
        advanceUntilIdle()

        // Act
        viewModel.onSave()
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { updateSubscriptionUseCase.invoke(any()) }
        coVerify(exactly = 0) { addSubscriptionUseCase.invoke(any()) }
    }

    @Test
    fun 編集モードの保存ではid作成日時有効フラグが引き継がれ更新日時は現在時刻になる() = runTest {
        // Arrange
        val target = editTarget()
        coEvery { getSubscriptionByIdUseCase.invoke(EDIT_ID) } returns target
        val saved = slot<Subscription>()
        coEvery { updateSubscriptionUseCase.invoke(capture(saved)) } returns UpdateSubscriptionUseCase.Result.Success
        val viewModel = createViewModel(subscriptionId = EDIT_ID)
        advanceUntilIdle()

        // Act
        viewModel.onAmountChange("1990")
        viewModel.onSave()
        advanceUntilIdle()

        // Assert
        assertEquals(EDIT_ID, saved.captured.id)
        assertEquals(1_990L, saved.captured.amountMinor)
        assertEquals(target.createdAt, saved.captured.createdAt)
        assertTrue(saved.captured.isActive)
        assertEquals(Instant.now(fixedClock), saved.captured.updatedAt)
    }

    @Test
    fun 編集モードで保存に成功すると保存完了イベントが発行される() = runTest {
        // Arrange
        coEvery { getSubscriptionByIdUseCase.invoke(EDIT_ID) } returns editTarget()
        coEvery { updateSubscriptionUseCase.invoke(any()) } returns UpdateSubscriptionUseCase.Result.Success
        val viewModel = createViewModel(subscriptionId = EDIT_ID)
        advanceUntilIdle()

        // Act & Assert
        viewModel.savedEvent.test {
            viewModel.onSave()
            advanceUntilIdle()
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun 編集モードで保存時にバリデーションエラーがあるとフィールドに表示される() = runTest {
        // Arrange
        coEvery { getSubscriptionByIdUseCase.invoke(EDIT_ID) } returns editTarget()
        coEvery { updateSubscriptionUseCase.invoke(any()) } returns
            UpdateSubscriptionUseCase.Result.ValidationError(listOf(SubscriptionValidationError.EMPTY_NAME))
        val viewModel = createViewModel(subscriptionId = EDIT_ID)
        advanceUntilIdle()

        // Act
        viewModel.onSave()
        advanceUntilIdle()

        // Assert
        val state = viewModel.uiState.value
        assertEquals(AddSubscriptionViewModel.ERROR_EMPTY_NAME, state.nameError)
        assertFalse(state.isSaving)
    }

    @Test
    fun 編集モードで保存時に対象が存在しない場合はエラーイベントが発行される() = runTest {
        // Arrange
        coEvery { getSubscriptionByIdUseCase.invoke(EDIT_ID) } returns editTarget()
        coEvery { updateSubscriptionUseCase.invoke(any()) } returns UpdateSubscriptionUseCase.Result.NotFound
        val viewModel = createViewModel(subscriptionId = EDIT_ID)
        advanceUntilIdle()

        // Act & Assert
        viewModel.errorEvent.test {
            viewModel.onSave()
            advanceUntilIdle()
            assertEquals(AddSubscriptionViewModel.ERROR_NOT_FOUND, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun 編集モードでサービス名を書き換えるとtemplateIdがnullで保存される() = runTest {
        // Arrange
        coEvery { getSubscriptionByIdUseCase.invoke(EDIT_ID) } returns editTarget(templateId = 10L)
        val saved = slot<Subscription>()
        coEvery { updateSubscriptionUseCase.invoke(capture(saved)) } returns UpdateSubscriptionUseCase.Result.Success
        val viewModel = createViewModel(subscriptionId = EDIT_ID)
        advanceUntilIdle()

        // Act
        viewModel.onNameChange("自作サービス")
        viewModel.onSave()
        advanceUntilIdle()

        // Assert
        assertNull(saved.captured.templateId)
    }

    @Test
    fun 編集モードでサービス名を変えなければtemplateIdが維持される() = runTest {
        // Arrange
        coEvery { getSubscriptionByIdUseCase.invoke(EDIT_ID) } returns editTarget(templateId = 10L)
        val saved = slot<Subscription>()
        coEvery { updateSubscriptionUseCase.invoke(capture(saved)) } returns UpdateSubscriptionUseCase.Result.Success
        val viewModel = createViewModel(subscriptionId = EDIT_ID)
        advanceUntilIdle()

        // Act
        viewModel.onAmountChange("1990")
        viewModel.onSave()
        advanceUntilIdle()

        // Assert
        assertEquals(10L, saved.captured.templateId)
    }

    private fun editTarget(
        billingInterval: BillingInterval = BillingInterval(BillingIntervalUnit.MONTHLY, 1),
        nextBillingDate: LocalDate = LocalDate.of(2026, 5, 10),
        categoryId: Long? = null,
        templateId: Long? = null,
        memo: String? = null,
    ): Subscription {
        return Subscription(
            id = EDIT_ID,
            name = "Netflix",
            amountMinor = 1_490L,
            currencyCode = "JPY",
            billingInterval = billingInterval,
            startDate = LocalDate.of(2026, 4, 10),
            nextBillingDate = nextBillingDate,
            categoryId = categoryId,
            templateId = templateId,
            logoUri = null,
            memo = memo,
            isActive = true,
            createdAt = Instant.parse("2026-04-10T00:00:00Z"),
            updatedAt = Instant.parse("2026-04-10T00:00:00Z"),
        )
    }

    companion object {
        private const val EDIT_ID = 7L

        private val SAMPLE_TEMPLATES = listOf(
            ServiceTemplate(
                id = 1L,
                name = "Netflix",
                defaultAmountMinor = 1_590L,
                defaultCurrencyCode = "JPY",
                defaultInterval = BillingInterval(BillingIntervalUnit.MONTHLY, 1),
                logoResourceName = "ic_service_default",
                categoryId = 1L,
                searchKeywords = "netflix",
                domain = "netflix.com",
            ),
        )
    }
}
