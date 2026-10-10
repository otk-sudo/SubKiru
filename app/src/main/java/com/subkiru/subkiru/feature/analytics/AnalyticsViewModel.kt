package com.subkiru.subkiru.feature.analytics

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

// 換算レートを持たないため、通貨をまたいだ比較・合算はしない。
// 割合・「特に高額」・節約シミュレーション・支出推移はすべて通貨ごとに算出する
data class SubscriptionBreakdown(
    val id: Long,
    val name: String,
    val originalAmountMinor: Long,
    val currencyCode: String,
    val monthlyAmount: Long,
    // 同じ通貨の月額合計に占める割合
    val percentage: Float,
    // 同じ通貨のなかで最も月額換算が高いか
    val isHighestInCurrency: Boolean = false,
)

data class MonthlySpendPoint(
    val label: String,
    val amount: Long,
)

data class CurrencySpendTrend(
    val currencyCode: String,
    val points: List<MonthlySpendPoint>,
)

data class AnalyticsUiState(
    val monthlyTotals: List<MoneyAmount> = emptyList(),
    val annualTotals: List<MoneyAmount> = emptyList(),
    val subscriptionCount: Int = 0,
    val breakdowns: List<SubscriptionBreakdown> = emptyList(),
    // 通貨ごとに、最も高い契約を見直した場合の年間削減余地
    val annualSavingPotentials: List<MoneyAmount> = emptyList(),
    val spendTrends: List<CurrencySpendTrend> = emptyList(),
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
                    val monthlyTotals = subscriptions.monthlyTotalsByCurrency()
                    val breakdowns = buildBreakdowns(subscriptions, monthlyTotals)

                    _uiState.update { state ->
                        state.copy(
                            monthlyTotals = monthlyTotals,
                            annualTotals = monthlyTotals.multipliedBy(MONTHS_PER_YEAR),
                            subscriptionCount = subscriptions.size,
                            breakdowns = breakdowns,
                            annualSavingPotentials = breakdowns
                                .filter { it.isHighestInCurrency }
                                .map { MoneyAmount(it.monthlyAmount, it.currencyCode) }
                                .multipliedBy(MONTHS_PER_YEAR),
                            spendTrends = buildSpendTrends(
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

// 内訳を通貨ごとにまとめ、通貨内で月額換算の高い順に並べる（通貨の並びは JPY が先頭）
private fun buildBreakdowns(
    subscriptions: List<Subscription>,
    monthlyTotals: List<MoneyAmount>,
): List<SubscriptionBreakdown> = monthlyTotals.flatMap { total ->
    subscriptions
        .filter { it.currencyCode == total.currencyCode }
        .map { subscription ->
            val monthlyAmount = subscription.toMonthlyAmount()
            SubscriptionBreakdown(
                id = subscription.id,
                name = subscription.name,
                originalAmountMinor = subscription.amountMinor,
                currencyCode = subscription.currencyCode,
                monthlyAmount = monthlyAmount,
                percentage = if (total.amountMinor > 0L) monthlyAmount.toFloat() / total.amountMinor else 0f,
            )
        }
        .sortedByDescending { it.monthlyAmount }
        .mapIndexed { index, breakdown -> breakdown.copy(isHighestInCurrency = index == 0) }
}

private fun buildSpendTrends(
    subscriptions: List<Subscription>,
    currentMonth: YearMonth,
): List<CurrencySpendTrend> = subscriptions
    .groupBy { it.currencyCode }
    .map { (currencyCode, sameCurrencySubscriptions) ->
        CurrencySpendTrend(
            currencyCode = currencyCode,
            points = buildSpendTrend(sameCurrencySubscriptions, currentMonth),
        )
    }
    .sortedWith(compareBy(currencyCodeComparator) { it.currencyCode })

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
