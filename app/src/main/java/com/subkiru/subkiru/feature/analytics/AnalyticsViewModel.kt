package com.subkiru.subkiru.feature.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.subkiru.subkiru.SubKiruApplication
import com.subkiru.subkiru.core.domain.model.BillingIntervalUnit
import com.subkiru.subkiru.core.domain.model.Subscription
import com.subkiru.subkiru.core.domain.usecase.GetSubscriptionsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SubscriptionBreakdown(
    val id: Long,
    val name: String,
    val monthlyAmount: Long,
    val percentage: Float,
)

data class AnalyticsUiState(
    val monthlyTotal: Long = 0L,
    val annualTotal: Long = 0L,
    val subscriptionCount: Int = 0,
    val breakdowns: List<SubscriptionBreakdown> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

class AnalyticsViewModel(
    getSubscriptionsUseCase: GetSubscriptionsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            getSubscriptionsUseCase()
                .catch {
                    _uiState.update {
                        it.copy(error = ERROR_MESSAGE_LOAD, isLoading = false)
                    }
                }
                .collect { subscriptions ->
                    val breakdowns = subscriptions
                        .map { subscription ->
                            SubscriptionBreakdown(
                                id = subscription.id,
                                name = subscription.name,
                                monthlyAmount = toMonthlyAmount(subscription),
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

                    _uiState.update {
                        it.copy(
                            monthlyTotal = monthlyTotal,
                            annualTotal = monthlyTotal * MONTHS_PER_YEAR,
                            subscriptionCount = subscriptions.size,
                            breakdowns = breakdownsWithPercentage,
                            isLoading = false,
                        )
                    }
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
        private const val DAYS_PER_MONTH = 30L
        private const val WEEKS_PER_MONTH = 4L
        private const val MONTHS_PER_YEAR = 12L

        const val ERROR_MESSAGE_LOAD = "データの読み込みに失敗しました"

        fun factory(app: SubKiruApplication): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AnalyticsViewModel(
                    getSubscriptionsUseCase = app.getSubscriptionsUseCase,
                )
            }
        }
    }
}
