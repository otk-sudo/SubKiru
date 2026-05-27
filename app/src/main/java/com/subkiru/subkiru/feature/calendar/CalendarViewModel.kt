package com.subkiru.subkiru.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.subkiru.subkiru.SubKiruApplication
import com.subkiru.subkiru.core.domain.model.BillingInterval
import com.subkiru.subkiru.core.domain.model.BillingIntervalUnit
import com.subkiru.subkiru.core.domain.model.Subscription
import com.subkiru.subkiru.core.domain.usecase.GetSubscriptionsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

data class BillingEvent(
    val subscriptionId: Long,
    val name: String,
    val amountMinor: Long,
)

data class CalendarUiState(
    val displayedYearMonth: YearMonth = YearMonth.now(),
    val today: LocalDate = LocalDate.now(),
    val billingDays: Set<Int> = emptySet(),
    val selectedDate: LocalDate? = null,
    val selectedDateBillings: List<BillingEvent> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

class CalendarViewModel(
    private val getSubscriptionsUseCase: GetSubscriptionsUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val _displayedYearMonth = MutableStateFlow(YearMonth.now(clock))
    private val _selectedDate = MutableStateFlow<LocalDate?>(null)

    private val _uiState = MutableStateFlow(
        CalendarUiState(
            displayedYearMonth = YearMonth.now(clock),
            today = LocalDate.now(clock),
        ),
    )
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                getSubscriptionsUseCase(),
                _displayedYearMonth,
                _selectedDate,
            ) { subscriptions, yearMonth, selectedDate ->
                val allBillingDates = mutableMapOf<LocalDate, MutableList<BillingEvent>>()
                subscriptions.forEach { subscription ->
                    computeBillingDatesInMonth(subscription, yearMonth).forEach { date ->
                        allBillingDates.getOrPut(date) { mutableListOf() }
                            .add(
                                BillingEvent(
                                    subscriptionId = subscription.id,
                                    name = subscription.name,
                                    amountMinor = subscription.amountMinor,
                                ),
                            )
                    }
                }

                val billingDays = allBillingDates.keys.map { it.dayOfMonth }.toSet()
                val selectedBillings = selectedDate?.let { allBillingDates[it] }.orEmpty()

                CalendarUiState(
                    displayedYearMonth = yearMonth,
                    today = LocalDate.now(clock),
                    billingDays = billingDays,
                    selectedDate = selectedDate,
                    selectedDateBillings = selectedBillings,
                    isLoading = false,
                )
            }
                .catch { exception ->
                    if (exception is CancellationException) throw exception
                    _uiState.update { state ->
                        state.copy(error = ERROR_MESSAGE_LOAD, isLoading = false)
                    }
                }
                .collect { state ->
                    _uiState.value = state
                }
        }
    }

    fun onPreviousMonth() {
        _displayedYearMonth.update { it.minusMonths(1) }
        _selectedDate.value = null
    }

    fun onNextMonth() {
        _displayedYearMonth.update { it.plusMonths(1) }
        _selectedDate.value = null
    }

    fun onGoToCurrentMonth() {
        _displayedYearMonth.value = YearMonth.now(clock)
        _selectedDate.value = null
    }

    fun onDateSelected(date: LocalDate) {
        _selectedDate.value = date
    }

    companion object {
        const val ERROR_MESSAGE_LOAD = "データの読み込みに失敗しました"

        fun factory(app: SubKiruApplication): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                CalendarViewModel(
                    getSubscriptionsUseCase = app.getSubscriptionsUseCase,
                    clock = app.clock,
                )
            }
        }
    }
}

// 指定月内の請求日を nextBillingDate と billingInterval から逆算・投影する
internal fun computeBillingDatesInMonth(
    subscription: Subscription,
    yearMonth: YearMonth,
): List<LocalDate> {
    val interval = subscription.billingInterval
    if (interval.count <= 0) return emptyList()

    val monthStart = yearMonth.atDay(1)
    val monthEnd = yearMonth.atEndOfMonth()

    var date = subscription.nextBillingDate
    while (date.isAfter(monthStart)) {
        date = subtractInterval(date, interval)
    }

    val results = mutableListOf<LocalDate>()
    while (!date.isAfter(monthEnd)) {
        if (!date.isBefore(monthStart)) {
            results.add(date)
        }
        date = addInterval(date, interval)
    }

    return results
}

private fun addInterval(date: LocalDate, interval: BillingInterval): LocalDate =
    when (interval.unit) {
        BillingIntervalUnit.DAILY -> date.plusDays(interval.count.toLong())
        BillingIntervalUnit.WEEKLY -> date.plusWeeks(interval.count.toLong())
        BillingIntervalUnit.MONTHLY -> date.plusMonths(interval.count.toLong())
        BillingIntervalUnit.YEARLY -> date.plusYears(interval.count.toLong())
    }

private fun subtractInterval(date: LocalDate, interval: BillingInterval): LocalDate =
    when (interval.unit) {
        BillingIntervalUnit.DAILY -> date.minusDays(interval.count.toLong())
        BillingIntervalUnit.WEEKLY -> date.minusWeeks(interval.count.toLong())
        BillingIntervalUnit.MONTHLY -> date.minusMonths(interval.count.toLong())
        BillingIntervalUnit.YEARLY -> date.minusYears(interval.count.toLong())
    }
