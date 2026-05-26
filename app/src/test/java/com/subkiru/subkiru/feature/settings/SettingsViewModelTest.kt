package com.subkiru.subkiru.feature.settings

import app.cash.turbine.test
import com.subkiru.subkiru.core.domain.model.UserSettings
import com.subkiru.subkiru.core.domain.repository.SettingsRepository
import com.subkiru.subkiru.notification.ReminderScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private val settingsFlow = MutableSharedFlow<UserSettings>(replay = 1)

    private val settingsRepository: SettingsRepository = mockk {
        every { observeSettings() } returns settingsFlow
        coEvery { updateReminderEnabled(any()) } returns Unit
        coEvery { updateReminderDaysBefore(any()) } returns Unit
    }

    private val reminderScheduler: ReminderScheduler = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): SettingsViewModel {
        return SettingsViewModel(
            settingsRepository = settingsRepository,
            reminderScheduler = reminderScheduler,
            appVersion = TEST_APP_VERSION,
        )
    }

    @Test
    fun 初期状態はローディング中である() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.isLoading)
            assertEquals("", state.appVersion)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun 設定値が正しく読み込まれる() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()

            settingsFlow.emit(
                UserSettings(isReminderEnabled = true, reminderDaysBefore = 3),
            )
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertFalse(state.isLoading)
            assertTrue(state.isReminderEnabled)
            assertEquals(3, state.reminderDaysBefore)
            assertEquals(TEST_APP_VERSION, state.appVersion)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun リマインダーをONに変更するとリポジトリが呼ばれる() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()

            settingsFlow.emit(UserSettings())
            advanceUntilIdle()
            expectMostRecentItem()

            viewModel.onReminderEnabledChanged(true)
            advanceUntilIdle()

            coVerify { settingsRepository.updateReminderEnabled(true) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun リマインダー日数を変更するとリポジトリが呼ばれる() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()

            settingsFlow.emit(UserSettings(isReminderEnabled = true))
            advanceUntilIdle()
            expectMostRecentItem()

            viewModel.onReminderDaysBeforeSliderChanged(5)
            viewModel.onReminderDaysBeforeSliderFinished()
            advanceUntilIdle()

            coVerify { settingsRepository.updateReminderDaysBefore(5) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun 設定読み込みでエラーが発生するとエラー状態になる() = runTest {
        val errorFlow = flow<UserSettings> {
            throw RuntimeException("DataStore error")
        }
        val errorSettingsRepository: SettingsRepository = mockk {
            every { observeSettings() } returns errorFlow
        }
        val viewModel = SettingsViewModel(
            settingsRepository = errorSettingsRepository,
            reminderScheduler = reminderScheduler,
            appVersion = TEST_APP_VERSION,
        )

        viewModel.uiState.test {
            awaitItem()
            advanceUntilIdle()

            val state = awaitItem()
            assertFalse(state.isLoading)
            assertEquals(SettingsViewModel.ERROR_MESSAGE_LOAD, state.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun リマインダー変更でエラーが発生するとSnackbarイベントが送信される() = runTest {
        coEvery { settingsRepository.updateReminderEnabled(any()) } throws RuntimeException("write error")
        val viewModel = createViewModel()

        settingsFlow.emit(UserSettings())
        advanceUntilIdle()

        viewModel.errorEvent.test {
            viewModel.onReminderEnabledChanged(true)
            advanceUntilIdle()

            assertEquals(SettingsViewModel.ERROR_MESSAGE_SAVE, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun リマインダー日数保存でエラーが発生するとSnackbarイベントが送信される() = runTest {
        coEvery { settingsRepository.updateReminderDaysBefore(any()) } throws RuntimeException("write error")
        val viewModel = createViewModel()

        settingsFlow.emit(UserSettings(isReminderEnabled = true))
        advanceUntilIdle()

        viewModel.errorEvent.test {
            viewModel.onReminderDaysBeforeSliderChanged(5)
            viewModel.onReminderDaysBeforeSliderFinished()
            advanceUntilIdle()

            assertEquals(SettingsViewModel.ERROR_MESSAGE_SAVE, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun スライダー操作中は表示値のみが更新されDataStoreには書き込まない() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()

            settingsFlow.emit(UserSettings(isReminderEnabled = true, reminderDaysBefore = 1))
            advanceUntilIdle()
            expectMostRecentItem()

            viewModel.onReminderDaysBeforeSliderChanged(5)

            val state = expectMostRecentItem()
            assertEquals(5, state.reminderDaysBeforeSlider)
            coVerify(exactly = 0) { settingsRepository.updateReminderDaysBefore(any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun DataStoreの値が変更されるとUIが自動更新される() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()

            settingsFlow.emit(UserSettings(isReminderEnabled = false, reminderDaysBefore = 1))
            advanceUntilIdle()

            val firstState = expectMostRecentItem()
            assertFalse(firstState.isReminderEnabled)
            assertEquals(1, firstState.reminderDaysBefore)

            settingsFlow.emit(UserSettings(isReminderEnabled = true, reminderDaysBefore = 5))
            advanceUntilIdle()

            val secondState = expectMostRecentItem()
            assertTrue(secondState.isReminderEnabled)
            assertEquals(5, secondState.reminderDaysBefore)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun リマインダーをONにするとスケジューラが呼ばれる() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()

            settingsFlow.emit(UserSettings())
            advanceUntilIdle()
            expectMostRecentItem()

            viewModel.onReminderEnabledChanged(true)
            advanceUntilIdle()

            verify { reminderScheduler.schedule() }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun リマインダーをOFFにするとスケジューラがキャンセルされる() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()

            settingsFlow.emit(UserSettings(isReminderEnabled = true))
            advanceUntilIdle()
            expectMostRecentItem()

            viewModel.onReminderEnabledChanged(false)
            advanceUntilIdle()

            verify { reminderScheduler.cancel() }
            cancelAndIgnoreRemainingEvents()
        }
    }

    companion object {
        private const val TEST_APP_VERSION = "1.0.0-test"
    }
}
