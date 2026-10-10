package com.subkiru.subkiru.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.subkiru.subkiru.SubKiruApplication
import com.subkiru.subkiru.core.domain.model.MoneyAmount
import com.subkiru.subkiru.core.domain.model.Subscription
import com.subkiru.subkiru.core.domain.model.currencyCodeComparator
import com.subkiru.subkiru.core.domain.model.monthlyTotalsByCurrency
import com.subkiru.subkiru.core.domain.model.multipliedBy
import com.subkiru.subkiru.core.domain.model.toMonthlyAmount
import com.subkiru.subkiru.core.domain.repository.CategoryRepository
import com.subkiru.subkiru.core.domain.usecase.DeleteSubscriptionUseCase
import com.subkiru.subkiru.core.domain.usecase.GetSubscriptionsUseCase
import com.subkiru.subkiru.core.domain.usecase.ReorderSubscriptionsUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

enum class DisplayMode { MONTHLY, YEARLY }

enum class HomeSortOrder {
    BILLING_DATE,
    AMOUNT_HIGH,
    AMOUNT_LOW,
    NEWEST,
    CUSTOM,
}

data class HomeUiState(
    val subscriptions: List<Subscription> = emptyList(),
    val monthlyTotals: List<MoneyAmount> = emptyList(),
    val yearlyTotals: List<MoneyAmount> = emptyList(),
    val displayMode: DisplayMode = DisplayMode.MONTHLY,
    val sortOrder: HomeSortOrder = HomeSortOrder.BILLING_DATE,
    val categoryColorMap: Map<Long, String> = emptyMap(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

class HomeViewModel(
    getSubscriptionsUseCase: GetSubscriptionsUseCase,
    private val deleteSubscriptionUseCase: DeleteSubscriptionUseCase,
    private val reorderSubscriptionsUseCase: ReorderSubscriptionsUseCase,
    categoryRepository: CategoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _sortOrder = MutableStateFlow(HomeSortOrder.BILLING_DATE)

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
            combine(sharedSubscriptions, _sortOrder) { subscriptions, sortOrder ->
                subscriptions to sortOrder
            }.collect { (subscriptions, sortOrder) ->
                val totals = subscriptions.monthlyTotalsByCurrency()
                _uiState.update {
                    it.copy(
                        subscriptions = sortSubscriptions(subscriptions, sortOrder),
                        monthlyTotals = totals,
                        yearlyTotals = totals.multipliedBy(MONTHS_PER_YEAR),
                        sortOrder = sortOrder,
                        isLoading = false,
                    )
                }
            }
        }
        viewModelScope.launch {
            categoryRepository.observeAllCategories().collect { categories ->
                val colorMap = categories.associate { it.id to it.colorHex }
                _uiState.update { it.copy(categoryColorMap = colorMap) }
            }
        }
    }

    fun onDisplayModeChange(mode: DisplayMode) {
        _uiState.update { it.copy(displayMode = mode) }
    }

    fun onSortOrderChange(sortOrder: HomeSortOrder) {
        _sortOrder.value = sortOrder
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

    // 「カスタム」並べ替えのドラッグ操作が終わったときに、新しい表示順（ID の並び）を保存する
    fun onReorderSubscriptions(orderedIds: List<Long>) {
        viewModelScope.launch {
            try {
                reorderSubscriptionsUseCase(orderedIds)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _errorEvent.emit(ERROR_MESSAGE_REORDER)
            }
        }
    }

    companion object {
        private const val SHARE_TIMEOUT_MS = 5_000L
        private const val MONTHS_PER_YEAR = 12L

        const val ERROR_MESSAGE_LOAD = "データの読み込みに失敗しました"
        const val ERROR_MESSAGE_DELETE = "削除に失敗しました"
        const val ERROR_MESSAGE_REORDER = "並べ替えに失敗しました"

        fun factory(app: SubKiruApplication): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    getSubscriptionsUseCase = app.getSubscriptionsUseCase,
                    deleteSubscriptionUseCase = app.deleteSubscriptionUseCase,
                    reorderSubscriptionsUseCase = app.reorderSubscriptionsUseCase,
                    categoryRepository = app.categoryRepository,
                )
            }
        }
    }
}

private fun sortSubscriptions(
    subscriptions: List<Subscription>,
    sortOrder: HomeSortOrder,
): List<Subscription> = when (sortOrder) {
    HomeSortOrder.BILLING_DATE -> subscriptions.sortedBy { it.nextBillingDate }
    HomeSortOrder.AMOUNT_HIGH -> subscriptions.sortedWith(
        currencyOrder.thenByDescending { it.toMonthlyAmount() },
    )
    HomeSortOrder.AMOUNT_LOW -> subscriptions.sortedWith(
        currencyOrder.thenBy { it.toMonthlyAmount() },
    )
    HomeSortOrder.NEWEST -> subscriptions.sortedByDescending { it.createdAt }
    HomeSortOrder.CUSTOM -> subscriptions.sortedBy { it.sortOrder }
}

// 換算レートを持たないため、金額の並べ替えは通貨ごとにまとめてから行う（通貨の並びは JPY が先頭）
private val currencyOrder: Comparator<Subscription> =
    compareBy(currencyCodeComparator) { it.currencyCode }
