package com.subkiru.subkiru.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.subkiru.subkiru.SubKiruApplication
import com.subkiru.subkiru.core.domain.model.UserSettings
import com.subkiru.subkiru.core.domain.repository.SettingsRepository
import com.subkiru.subkiru.notification.ReminderScheduler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

data class SettingsUiState(
    val isReminderEnabled: Boolean = false,
    val reminderDaysBefore: Int = UserSettings.DEFAULT_REMINDER_DAYS_BEFORE,
    val reminderDaysBeforeSlider: Int = UserSettings.DEFAULT_REMINDER_DAYS_BEFORE,
    val appVersion: String = "",
    val isLoading: Boolean = true,
    val error: String? = null,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val reminderScheduler: ReminderScheduler,
    private val appVersion: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    // 保存エラー等の一時的なエラーを通知するイベント（Snackbar 用）
    private val _errorEvent = MutableSharedFlow<String>()
    val errorEvent = _errorEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            settingsRepository.observeSettings()
                .catch {
                    _uiState.update {
                        it.copy(error = ERROR_MESSAGE_LOAD, isLoading = false)
                    }
                }
                .collect { settings ->
                    _uiState.update {
                        it.copy(
                            isReminderEnabled = settings.isReminderEnabled,
                            reminderDaysBefore = settings.reminderDaysBefore,
                            reminderDaysBeforeSlider = settings.reminderDaysBefore,
                            appVersion = appVersion,
                            isLoading = false,
                            error = null,
                        )
                    }
                }
        }
    }

    fun onReminderEnabledChanged(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsRepository.updateReminderEnabled(enabled)
                if (enabled) {
                    reminderScheduler.schedule()
                } else {
                    reminderScheduler.cancel()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _errorEvent.emit(ERROR_MESSAGE_SAVE)
            }
        }
    }

    fun onReminderDaysBeforeSliderChanged(days: Int) {
        _uiState.update { it.copy(reminderDaysBeforeSlider = days) }
    }

    fun onReminderDaysBeforeSliderFinished() {
        val days = _uiState.value.reminderDaysBeforeSlider
        viewModelScope.launch {
            try {
                settingsRepository.updateReminderDaysBefore(days)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _errorEvent.emit(ERROR_MESSAGE_SAVE)
            }
        }
    }

    companion object {
        const val ERROR_MESSAGE_LOAD = "設定の読み込みに失敗しました"
        const val ERROR_MESSAGE_SAVE = "設定の保存に失敗しました"

        fun factory(app: SubKiruApplication): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val appVersion = try {
                    app.packageManager.getPackageInfo(app.packageName, 0).versionName
                        ?: "unknown"
                } catch (_: Exception) {
                    "unknown"
                }
                SettingsViewModel(
                    settingsRepository = app.settingsRepository,
                    reminderScheduler = app.reminderScheduler,
                    appVersion = appVersion,
                )
            }
        }
    }
}
