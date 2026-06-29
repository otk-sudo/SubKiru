package com.subkiru.subkiru.feature.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.subkiru.subkiru.SubKiruApplication
import com.subkiru.subkiru.core.domain.model.Subscription
import com.subkiru.subkiru.core.domain.model.toMonthlyAmount
import com.subkiru.subkiru.core.domain.usecase.GetSubscriptionsUseCase
import java.time.Clock
import java.time.YearMonth
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SubscriptionBreakdown(
    val id: Long,
    val name: String,
    val originalAmountMinor: Long,
    val currencyCode: String,
    val monthlyAmount: Long,
    val percentage: Float,
)

data class MonthlySpendPoint(
    val label: String,
    val amount: Long,
)

data class AnalyticsUiState(
    val monthlyTotal: Long = 0L,
    val annualTotal: Long = 0L,
    val subscriptionCount: Int = 0,
    val breakdowns: List<SubscriptionBreakdown> = emptyList(),
    val spendTrend: List<MonthlySpendPoint> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

class AnalyticsViewModel(
    getSubscriptionsUseCase: GetSubscriptionsUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            getSubscriptionsUseCase()
                .catch { exception ->
                    if (exception is CancellationException) throw exception
                    _uiState.update { state ->
                        state.copy(error = ERROR_MESSAGE_LOAD, isLoading = false)
                    }
                }
                .collect { subscriptions ->
                    val breakdowns = subscriptions
                        .map { subscription ->
                            SubscriptionBreakdown(
                                id = subscription.id,
                                name = subscription.name,
                                originalAmountMinor = subscription.amountMinor,
                                currencyCode = subscription.currencyCode,
                                monthlyAmount = subscription.toMonthlyAmount(),
                                percentage = 0f,
                            )
                        }
                        .sortedByDescending { it.monthlyAmount }

                    val monthlyTotal = breakdowns.sumOf { it.monthlyAmount }
                    val breakdownsWithPercentage = if (monthlyTotal > 0L) {
                        breakdowns.map {
                            it.copy(percentage = it.monthlyAmount.toFloat() / monthlyTotal)
                        }
                    } else {
                        breakdowns
                    }

                    _uiState.update { state ->
                        state.copy(
                            monthlyTotal = monthlyTotal,
                            annualTotal = monthlyTotal * MONTHS_PER_YEAR,
                            subscriptionCount = subscriptions.size,
                            breakdowns = breakdownsWithPercentage,
                            spendTrend = buildSpendTrend(
                                subscriptions = subscriptions,
                                currentMonth = YearMonth.now(clock),
                            ),
                            isLoading = false,
                        )
                    }
                }
        }
    }

    companion object {
        private const val MONTHS_PER_YEAR = 12L

        const val ERROR_MESSAGE_LOAD = "データの読み込みに失敗しました"

        fun factory(app: SubKiruApplication): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AnalyticsViewModel(
                    getSubscriptionsUseCase = app.getSubscriptionsUseCase,
                    clock = app.clock,
                )
            }
        }
    }
}

private fun buildSpendTrend(
    subscriptions: List<Subscription>,
    currentMonth: YearMonth,
): List<MonthlySpendPoint> = (TREND_MONTH_COUNT - 1 downTo 0).map { monthsAgo ->
    val month = currentMonth.minusMonths(monthsAgo.toLong())
    val amount = subscriptions
        .filter { subscription -> !subscription.startDate.isAfter(month.atEndOfMonth()) }
        .sumOf { subscription -> subscription.toMonthlyAmount() }
    MonthlySpendPoint(
        label = "${month.monthValue}月",
        amount = amount,
    )
}

private const val TREND_MONTH_COUNT = 6
