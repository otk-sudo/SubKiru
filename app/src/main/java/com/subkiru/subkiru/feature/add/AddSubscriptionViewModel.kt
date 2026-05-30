package com.subkiru.subkiru.feature.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.subkiru.subkiru.SubKiruApplication
import com.subkiru.subkiru.core.domain.model.BillingInterval
import com.subkiru.subkiru.core.domain.model.BillingIntervalUnit
import com.subkiru.subkiru.core.domain.model.Category
import com.subkiru.subkiru.core.domain.model.Subscription
import com.subkiru.subkiru.core.domain.repository.CategoryRepository
import com.subkiru.subkiru.core.domain.usecase.AddSubscriptionUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.coroutines.cancellation.CancellationException

data class AddSubscriptionUiState(
    val name: String = "",
    val amountText: String = "",
    val billingIntervalUnit: BillingIntervalUnit = BillingIntervalUnit.MONTHLY,
    val billingIntervalCountText: String = "1",
    val startDateText: String = "",
    val nextBillingDateText: String = "",
    val categoryId: Long? = null,
    val categories: List<Category> = emptyList(),
    val memo: String = "",
    val nameError: String? = null,
    val amountError: String? = null,
    val intervalError: String? = null,
    val startDateError: String? = null,
    val isSaving: Boolean = false,
)

class AddSubscriptionViewModel(
    private val addSubscriptionUseCase: AddSubscriptionUseCase,
    private val categoryRepository: CategoryRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState: MutableStateFlow<AddSubscriptionUiState>
    val uiState: StateFlow<AddSubscriptionUiState>

    private val _savedEvent = MutableSharedFlow<Unit>()
    val savedEvent = _savedEvent.asSharedFlow()

    private val _errorEvent = MutableSharedFlow<String>()
    val errorEvent = _errorEvent.asSharedFlow()

    init {
        val today = LocalDate.now(clock)
        val startDateText = today.format(DATE_FORMATTER)
        val nextBillingDateText = requireNotNull(
            calculateNextBillingDate(
                startDateText = startDateText,
                unit = BillingIntervalUnit.MONTHLY,
                countText = "1",
            ),
        ) { "初期値での次回請求日計算は失敗しない想定" }
        _uiState = MutableStateFlow(
            AddSubscriptionUiState(
                startDateText = startDateText,
                nextBillingDateText = nextBillingDateText,
            ),
        )
        uiState = _uiState.asStateFlow()

        viewModelScope.launch {
            categoryRepository.observeAllCategories().collect { categories ->
                _uiState.update { it.copy(categories = categories) }
            }
        }
    }

    fun onNameChange(name: String) {
        _uiState.update { it.copy(name = name, nameError = null) }
    }

    fun onAmountChange(amountText: String) {
        _uiState.update { it.copy(amountText = amountText, amountError = null) }
    }

    fun onBillingIntervalUnitChange(unit: BillingIntervalUnit) {
        _uiState.update { state ->
            val nextBilling = calculateNextBillingDate(state.startDateText, unit, state.billingIntervalCountText)
            state.copy(
                billingIntervalUnit = unit,
                nextBillingDateText = nextBilling ?: state.nextBillingDateText,
            )
        }
    }

    fun onBillingIntervalCountChange(text: String) {
        _uiState.update { state ->
            val nextBilling = calculateNextBillingDate(state.startDateText, state.billingIntervalUnit, text)
            state.copy(
                billingIntervalCountText = text,
                intervalError = null,
                nextBillingDateText = nextBilling ?: state.nextBillingDateText,
            )
        }
    }

    fun onStartDateChange(text: String) {
        _uiState.update { state ->
            val nextBilling = calculateNextBillingDate(text, state.billingIntervalUnit, state.billingIntervalCountText)
            state.copy(
                startDateText = text,
                startDateError = null,
                nextBillingDateText = nextBilling ?: state.nextBillingDateText,
            )
        }
    }

    fun onCategoryChange(categoryId: Long?) {
        _uiState.update { it.copy(categoryId = categoryId) }
    }

    fun applyTemplate(
        name: String,
        amountMinor: Long,
        unit: BillingIntervalUnit,
        count: Int,
        categoryId: Long,
    ) {
        _uiState.update { state ->
            val nextBilling = calculateNextBillingDate(state.startDateText, unit, count.toString())
            state.copy(
                name = name,
                amountText = amountMinor.toString(),
                billingIntervalUnit = unit,
                billingIntervalCountText = count.toString(),
                categoryId = categoryId,
                nextBillingDateText = nextBilling ?: state.nextBillingDateText,
                nameError = null,
                amountError = null,
                intervalError = null,
            )
        }
    }

    fun onMemoChange(memo: String) {
        _uiState.update { it.copy(memo = memo) }
    }

    private fun calculateNextBillingDate(
        startDateText: String,
        unit: BillingIntervalUnit,
        countText: String,
    ): String? {
        val startDate = try {
            LocalDate.parse(startDateText, DATE_FORMATTER)
        } catch (_: java.time.format.DateTimeParseException) {
            return null
        }
        val count = countText.toIntOrNull() ?: return null
        if (count <= 0) return null

        val nextDate = when (unit) {
            BillingIntervalUnit.DAILY -> startDate.plusDays(count.toLong())
            BillingIntervalUnit.WEEKLY -> startDate.plusWeeks(count.toLong())
            BillingIntervalUnit.MONTHLY -> startDate.plusMonths(count.toLong())
            BillingIntervalUnit.YEARLY -> startDate.plusYears(count.toLong())
        }
        return nextDate.format(DATE_FORMATTER)
    }

    fun onSave() {
        val state = _uiState.value
        if (state.isSaving) return

        val amount = state.amountText.replace(",", "").toLongOrNull()
        if (amount == null) {
            _uiState.update { it.copy(amountError = ERROR_INVALID_AMOUNT) }
            return
        }

        val intervalCount = state.billingIntervalCountText.toIntOrNull()
        if (intervalCount == null) {
            _uiState.update { it.copy(intervalError = ERROR_INVALID_INTERVAL) }
            return
        }

        val startDate = try {
            LocalDate.parse(state.startDateText, DATE_FORMATTER)
        } catch (_: java.time.format.DateTimeParseException) {
            _uiState.update { it.copy(startDateError = ERROR_INVALID_DATE) }
            return
        }

        // 自動計算値なのでパース失敗はバグ
        val nextBillingDate = LocalDate.parse(state.nextBillingDateText, DATE_FORMATTER)

        val now = Instant.now(clock)
        val subscription = Subscription(
            id = UNSAVED_SUBSCRIPTION_ID,
            name = state.name.trim(),
            amountMinor = amount,
            currencyCode = CURRENCY_JPY,
            billingInterval = BillingInterval(state.billingIntervalUnit, intervalCount),
            startDate = startDate,
            nextBillingDate = nextBillingDate,
            categoryId = state.categoryId,
            templateId = null,
            // 外部ロゴURL（旧Clearbit）は保存しない。ロゴはサービス名から解決する
            logoUri = null,
            memo = state.memo.trim().ifEmpty { null },
            isActive = true,
            createdAt = now,
            updatedAt = now,
        )

        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            try {
                when (val result = addSubscriptionUseCase(subscription)) {
                    is AddSubscriptionUseCase.Result.Success -> {
                        _savedEvent.emit(Unit)
                    }

                    is AddSubscriptionUseCase.Result.ValidationError -> {
                        _uiState.update { current ->
                            current.copy(
                                isSaving = false,
                                nameError = if (AddSubscriptionUseCase.Error.EMPTY_NAME in result.errors) {
                                    ERROR_EMPTY_NAME
                                } else {
                                    null
                                },
                                amountError = if (AddSubscriptionUseCase.Error.NEGATIVE_AMOUNT in result.errors) {
                                    ERROR_NEGATIVE_AMOUNT
                                } else {
                                    null
                                },
                                intervalError = if (
                                    AddSubscriptionUseCase.Error.INVALID_INTERVAL_COUNT in result.errors
                                ) {
                                    ERROR_INVALID_INTERVAL
                                } else {
                                    null
                                },
                                startDateError = if (
                                    AddSubscriptionUseCase.Error.START_DATE_AFTER_NEXT_BILLING_DATE in result.errors
                                ) {
                                    ERROR_DATE_ORDER
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(isSaving = false) }
                _errorEvent.emit(ERROR_SAVE_FAILED)
            }
        }
    }

    companion object {
        private const val CURRENCY_JPY = "JPY"
        private const val UNSAVED_SUBSCRIPTION_ID = 0L
        internal val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")

        const val ERROR_EMPTY_NAME = "サービス名を入力してください"
        const val ERROR_INVALID_AMOUNT = "有効な金額を入力してください"
        const val ERROR_NEGATIVE_AMOUNT = "金額は0以上で入力してください"
        const val ERROR_INVALID_INTERVAL = "請求間隔は1以上の整数で入力してください"
        const val ERROR_INVALID_DATE = "日付はyyyy/MM/dd形式で入力してください"
        const val ERROR_DATE_ORDER = "開始日は次回請求日より前にしてください"
        const val ERROR_SAVE_FAILED = "保存に失敗しました"

        fun factory(app: SubKiruApplication): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AddSubscriptionViewModel(
                    addSubscriptionUseCase = app.addSubscriptionUseCase,
                    categoryRepository = app.categoryRepository,
                    clock = app.clock,
                )
            }
        }
    }
}
