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
import com.subkiru.subkiru.core.domain.model.ServiceTemplate
import com.subkiru.subkiru.core.domain.model.Subscription
import com.subkiru.subkiru.core.domain.repository.CategoryRepository
import com.subkiru.subkiru.core.domain.repository.ServiceTemplateRepository
import com.subkiru.subkiru.core.domain.usecase.AddSubscriptionUseCase
import com.subkiru.subkiru.core.domain.usecase.GetSubscriptionByIdUseCase
import com.subkiru.subkiru.core.domain.usecase.SubscriptionValidationError
import com.subkiru.subkiru.core.domain.usecase.UpdateSubscriptionUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
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
    val templateId: Long? = null,
    val categories: List<Category> = emptyList(),
    val memo: String = "",
    val nameError: String? = null,
    val amountError: String? = null,
    val intervalError: String? = null,
    val startDateError: String? = null,
    val isSaving: Boolean = false,
    // 編集モード（既存サブスクの編集）かどうか
    val isEditMode: Boolean = false,
    // 編集対象の読み込み中
    val isLoading: Boolean = false,
    // 編集対象の読み込みに失敗した場合のメッセージ
    val loadError: String? = null,
    // 追加モードで表示する人気サービスのテンプレート（編集モードでは使わない）
    val templates: List<ServiceTemplate> = emptyList(),
    // テンプレートの読み込み中。一覧と入力欄を同時に表示するため、完了まで画面全体を読み込み中にする
    val isTemplatesLoading: Boolean = false,
)

class AddSubscriptionViewModel(
    private val addSubscriptionUseCase: AddSubscriptionUseCase,
    private val updateSubscriptionUseCase: UpdateSubscriptionUseCase,
    private val getSubscriptionByIdUseCase: GetSubscriptionByIdUseCase,
    private val categoryRepository: CategoryRepository,
    private val serviceTemplateRepository: ServiceTemplateRepository,
    private val clock: Clock,
    // null の場合は追加、指定した場合はそのサブスクの編集
    private val subscriptionId: Long? = null,
) : ViewModel() {

    private val _uiState: MutableStateFlow<AddSubscriptionUiState>
    val uiState: StateFlow<AddSubscriptionUiState>

    private val _savedEvent = MutableSharedFlow<Unit>()
    val savedEvent = _savedEvent.asSharedFlow()

    // 編集モードで読み込んだ元のサブスク（通貨・作成日時・ロゴを保存時に引き継ぐ）
    private var loadedSubscription: Subscription? = null

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
                isEditMode = subscriptionId != null,
                isLoading = subscriptionId != null,
                isTemplatesLoading = subscriptionId == null,
            ),
        )
        uiState = _uiState.asStateFlow()

        viewModelScope.launch {
            categoryRepository.observeAllCategories().collect { categories ->
                _uiState.update { it.copy(categories = categories) }
            }
        }

        if (subscriptionId != null) {
            loadSubscription(subscriptionId)
        } else {
            observeTemplates()
        }
    }

    private fun observeTemplates() {
        viewModelScope.launch {
            serviceTemplateRepository.observeAllTemplates()
                .catch { exception ->
                    if (exception is CancellationException) throw exception
                    // テンプレートは入力補助なので、読み込めなくても手入力で登録できるようにする
                    _uiState.update { it.copy(templates = emptyList(), isTemplatesLoading = false) }
                }
                .collect { templates ->
                    _uiState.update { it.copy(templates = templates, isTemplatesLoading = false) }
                }
        }
    }

    private fun loadSubscription(id: Long) {
        viewModelScope.launch {
            try {
                val subscription = getSubscriptionByIdUseCase(id)
                if (subscription == null) {
                    _uiState.update { it.copy(isLoading = false, loadError = ERROR_NOT_FOUND) }
                    return@launch
                }
                loadedSubscription = subscription
                _uiState.update { it.withSubscription(subscription) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false, loadError = ERROR_LOAD_FAILED) }
            }
        }
    }

    // 既存のサブスクの値をフォームに反映する。次回請求日は追加時と同じく開始日と間隔から自動計算する
    private fun AddSubscriptionUiState.withSubscription(subscription: Subscription): AddSubscriptionUiState {
        val startDateText = subscription.startDate.format(DATE_FORMATTER)
        val countText = subscription.billingInterval.count.toString()
        return copy(
            name = subscription.name,
            amountText = subscription.amountMinor.toString(),
            billingIntervalUnit = subscription.billingInterval.unit,
            billingIntervalCountText = countText,
            startDateText = startDateText,
            nextBillingDateText = calculateNextBillingDate(
                startDateText,
                subscription.billingInterval.unit,
                countText,
            ) ?: subscription.nextBillingDate.format(DATE_FORMATTER),
            categoryId = subscription.categoryId,
            templateId = subscription.templateId,
            memo = subscription.memo.orEmpty(),
            isLoading = false,
        )
    }

    fun onNameChange(name: String) {
        _uiState.update { state ->
            // テンプレート適用後にサービス名を書き換えたら、テンプレートとの紐付けを外す
            val templateId = if (name == state.name) state.templateId else null
            state.copy(name = name, nameError = null, templateId = templateId)
        }
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
        templateId: Long,
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
                templateId = templateId,
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
        if (state.isSaving || state.isLoading || state.loadError != null) return

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
        val original = loadedSubscription
        val subscription = Subscription(
            id = original?.id ?: UNSAVED_SUBSCRIPTION_ID,
            name = state.name.trim(),
            amountMinor = amount,
            currencyCode = original?.currencyCode ?: CURRENCY_JPY,
            billingInterval = BillingInterval(state.billingIntervalUnit, intervalCount),
            startDate = startDate,
            nextBillingDate = nextBillingDate,
            categoryId = state.categoryId,
            templateId = state.templateId,
            // 外部ロゴURL（旧Clearbit）は新たに保存しない（編集時は既存値を引き継ぐ）。ロゴはサービス名から解決する
            logoUri = original?.logoUri,
            memo = state.memo.trim().ifEmpty { null },
            isActive = true,
            createdAt = original?.createdAt ?: now,
            updatedAt = now,
        )

        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            try {
                if (state.isEditMode) {
                    handleUpdateResult(updateSubscriptionUseCase(subscription))
                } else {
                    handleAddResult(addSubscriptionUseCase(subscription))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(isSaving = false) }
                _errorEvent.emit(ERROR_SAVE_FAILED)
            }
        }
    }

    private suspend fun handleAddResult(result: AddSubscriptionUseCase.Result) {
        when (result) {
            is AddSubscriptionUseCase.Result.Success -> _savedEvent.emit(Unit)
            is AddSubscriptionUseCase.Result.ValidationError -> showValidationErrors(result.errors)
        }
    }

    private suspend fun handleUpdateResult(result: UpdateSubscriptionUseCase.Result) {
        when (result) {
            is UpdateSubscriptionUseCase.Result.Success -> _savedEvent.emit(Unit)
            is UpdateSubscriptionUseCase.Result.ValidationError -> showValidationErrors(result.errors)
            is UpdateSubscriptionUseCase.Result.NotFound -> {
                _uiState.update { it.copy(isSaving = false) }
                _errorEvent.emit(ERROR_NOT_FOUND)
            }
        }
    }

    private fun showValidationErrors(errors: List<SubscriptionValidationError>) {
        _uiState.update { current ->
            current.copy(
                isSaving = false,
                nameError = ERROR_EMPTY_NAME.takeIf { SubscriptionValidationError.EMPTY_NAME in errors },
                amountError = ERROR_NEGATIVE_AMOUNT.takeIf {
                    SubscriptionValidationError.NEGATIVE_AMOUNT in errors
                },
                intervalError = ERROR_INVALID_INTERVAL.takeIf {
                    SubscriptionValidationError.INVALID_INTERVAL_COUNT in errors
                },
                startDateError = ERROR_DATE_ORDER.takeIf {
                    SubscriptionValidationError.START_DATE_AFTER_NEXT_BILLING_DATE in errors
                },
            )
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
        const val ERROR_NOT_FOUND = "サブスクが見つかりません。削除された可能性があります"
        const val ERROR_LOAD_FAILED = "データの読み込みに失敗しました"

        fun factory(
            app: SubKiruApplication,
            subscriptionId: Long? = null,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AddSubscriptionViewModel(
                    addSubscriptionUseCase = app.addSubscriptionUseCase,
                    updateSubscriptionUseCase = app.updateSubscriptionUseCase,
                    getSubscriptionByIdUseCase = app.getSubscriptionByIdUseCase,
                    categoryRepository = app.categoryRepository,
                    serviceTemplateRepository = app.serviceTemplateRepository,
                    clock = app.clock,
                    subscriptionId = subscriptionId,
                )
            }
        }
    }
}
