package com.subkiru.subkiru.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.subkiru.subkiru.SubKiruApplication
import com.subkiru.subkiru.core.domain.model.BillingIntervalUnit
import com.subkiru.subkiru.core.domain.model.Subscription
import com.subkiru.subkiru.core.domain.usecase.DeleteSubscriptionUseCase
import com.subkiru.subkiru.core.domain.usecase.GetSubscriptionsUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

data class HomeUiState(
    val subscriptions: List<Subscription> = emptyList(),
    val monthlyTotal: Long = 0L,
    val isLoading: Boolean = true,
    val error: String? = null,
)

class HomeViewModel(
    getSubscriptionsUseCase: GetSubscriptionsUseCase,
    private val deleteSubscriptionUseCase: DeleteSubscriptionUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // 削除結果等の一時的なエラーを通知するイベント（Snackbar 用）
    private val _errorEvent = MutableSharedFlow<String>()
    val errorEvent = _errorEvent.asSharedFlow()

    init {
        // 同一 DAO Flow を shareIn で共有し、DB クエリの重複実行を防止する
        // shareIn は上流の例外を下流に伝播しないため、catch は shareIn の前に配置する
        val sharedSubscriptions = getSubscriptionsUseCase()
            .catch { exception ->
                if (exception is CancellationException) throw exception
                _uiState.update { state ->
                    state.copy(error = ERROR_MESSAGE_LOAD, isLoading = false)
                }
            }
            .shareIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SHARE_TIMEOUT_MS),
                replay = 1,
            )

        viewModelScope.launch {
            sharedSubscriptions
                .collect { subscriptions ->
                    _uiState.update {
                        it.copy(subscriptions = subscriptions, isLoading = false)
                    }
                }
        }

        viewModelScope.launch {
            sharedSubscriptions
                .map { subscriptions -> subscriptions.sumOf { toMonthlyAmount(it) } }
                .collect { total ->
                    _uiState.update { it.copy(monthlyTotal = total) }
                }
        }
    }

    fun onDeleteSubscription(id: Long) {
        viewModelScope.launch {
            try {
                deleteSubscriptionUseCase(id)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 削除エラーはリスト表示を維持し、一時イベントで通知する。
                _errorEvent.emit(ERROR_MESSAGE_DELETE)
            }
        }
    }

    private fun toMonthlyAmount(subscription: Subscription): Long {
        val interval = subscription.billingInterval
        return when (interval.unit) {
            BillingIntervalUnit.DAILY -> subscription.amountMinor * DAYS_PER_MONTH / interval.count
            BillingIntervalUnit.WEEKLY -> subscription.amountMinor * WEEKS_PER_MONTH / interval.count
            BillingIntervalUnit.MONTHLY -> subscription.amountMinor / interval.count
            BillingIntervalUnit.YEARLY -> subscription.amountMinor / (MONTHS_PER_YEAR * interval.count)
        }
    }

    companion object {
        private const val SHARE_TIMEOUT_MS = 5_000L
        private const val DAYS_PER_MONTH = 30L
        private const val WEEKS_PER_MONTH = 4L
        private const val MONTHS_PER_YEAR = 12L

        const val ERROR_MESSAGE_LOAD = "データの読み込みに失敗しました"
        const val ERROR_MESSAGE_DELETE = "削除に失敗しました"

        fun factory(app: SubKiruApplication): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    getSubscriptionsUseCase = app.getSubscriptionsUseCase,
                    deleteSubscriptionUseCase = app.deleteSubscriptionUseCase,
                )
            }
        }
    }
}
