# サブスク追加画面 タスクドキュメント（Codex向け）

## 概要

Home画面のFABから遷移するサブスク追加フォーム画面を実装する。
`AddSubscriptionUseCase` の既存バリデーションを活用し、保存成功時は前画面に戻る。

---

## 前提

### 既存リソース（変更不要）
- `AddSubscriptionUseCase` — バリデーション + 保存。`Result.Success` / `Result.ValidationError` を返す
- `Subscription` ドメインモデル — 全フィールド定義済み
- `BillingInterval` / `BillingIntervalUnit` — 請求間隔の型
- `SubKiruApplication` — 手動DI。`addSubscriptionUseCase` / `clock` プロパティ有り
- `Screen.AddSubscription` — ルート定義済み（`"add_subscription"`）

### MVPスコープ
- カテゴリ選択: 未実装（`categoryId = null`）
- テンプレート選択: 未実装（`templateId = null`）
- ロゴ設定: 未実装（`logoUri = null`）
- 通貨選択: JPY固定（`currencyCode = "JPY"`）

---

## タスク1: AddSubscriptionUiState 定義

**ファイル**: `app/src/main/java/com/subkiru/subkiru/feature/add/AddSubscriptionViewModel.kt`

ViewModel ファイルの先頭に UiState を定義する。

```kotlin
package com.subkiru.subkiru.feature.add

import com.subkiru.subkiru.core.domain.model.BillingIntervalUnit
import java.time.LocalDate

data class AddSubscriptionUiState(
    val name: String = "",
    val amountText: String = "",
    val billingIntervalUnit: BillingIntervalUnit = BillingIntervalUnit.MONTHLY,
    val billingIntervalCountText: String = "1",
    val startDateText: String = "",
    val nextBillingDateText: String = "",
    val memo: String = "",
    val nameError: String? = null,
    val amountError: String? = null,
    val intervalError: String? = null,
    val startDateError: String? = null,
    val nextBillingDateError: String? = null,
    val isSaving: Boolean = false,
)
```

### 設計意図
- `amountText` は `String` で保持する。ユーザー入力をそのまま保持し、保存時に `Long` に変換する
- `billingIntervalCountText` は `String` で保持する（W-3対応）。フィールドをクリアして新しい値を入力可能にするため。保存時に `Int` に変換する
- `startDateText` / `nextBillingDateText` は `String` で保持する（C-1対応）。ユーザーが `yyyy/MM/dd` 形式で入力中の中間状態を保持し、保存時にパースする
- `startDateError` / `nextBillingDateError` は日付フィールドごとに分離する（W-1対応）。エラーメッセージが正しいフィールドに表示される
- エラーは各フィールドに個別の `String?` で保持する（インライン表示用）
- `isSaving` は保存中の二重タップ防止用

---

## タスク2: AddSubscriptionViewModel 実装

**ファイル**: `app/src/main/java/com/subkiru/subkiru/feature/add/AddSubscriptionViewModel.kt`

```kotlin
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.subkiru.subkiru.SubKiruApplication
import com.subkiru.subkiru.core.domain.model.BillingInterval
import com.subkiru.subkiru.core.domain.model.BillingIntervalUnit
import com.subkiru.subkiru.core.domain.model.Subscription
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

class AddSubscriptionViewModel(
    private val addSubscriptionUseCase: AddSubscriptionUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState: MutableStateFlow<AddSubscriptionUiState>
    val uiState: StateFlow<AddSubscriptionUiState>

    init {
        val today = LocalDate.now(clock)
        _uiState = MutableStateFlow(
            AddSubscriptionUiState(
                startDateText = today.format(DATE_FORMATTER),
                nextBillingDateText = today.plusMonths(1).format(DATE_FORMATTER),
            ),
        )
        uiState = _uiState.asStateFlow()
    }

    // 保存成功イベント（画面遷移トリガー）
    private val _savedEvent = MutableSharedFlow<Unit>()
    val savedEvent = _savedEvent.asSharedFlow()

    // 保存失敗イベント（Snackbar 用）
    private val _errorEvent = MutableSharedFlow<String>()
    val errorEvent = _errorEvent.asSharedFlow()

    fun onNameChange(name: String) {
        _uiState.update { it.copy(name = name, nameError = null) }
    }

    fun onAmountChange(amountText: String) {
        _uiState.update { it.copy(amountText = amountText, amountError = null) }
    }

    fun onBillingIntervalUnitChange(unit: BillingIntervalUnit) {
        _uiState.update { it.copy(billingIntervalUnit = unit) }
    }

    fun onBillingIntervalCountChange(text: String) {
        _uiState.update { it.copy(billingIntervalCountText = text, intervalError = null) }
    }

    fun onStartDateChange(text: String) {
        _uiState.update { it.copy(startDateText = text, startDateError = null) }
    }

    fun onNextBillingDateChange(text: String) {
        _uiState.update { it.copy(nextBillingDateText = text, nextBillingDateError = null) }
    }

    fun onMemoChange(memo: String) {
        _uiState.update { it.copy(memo = memo) }
    }

    fun onSave() {
        val state = _uiState.value
        if (state.isSaving) return

        // クライアント側バリデーション（型変換）
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
        } catch (_: Exception) {
            _uiState.update { it.copy(startDateError = ERROR_INVALID_DATE) }
            return
        }

        val nextBillingDate = try {
            LocalDate.parse(state.nextBillingDateText, DATE_FORMATTER)
        } catch (_: Exception) {
            _uiState.update { it.copy(nextBillingDateError = ERROR_INVALID_DATE) }
            return
        }

        val now = Instant.now(clock)
        val subscription = Subscription(
            id = 0L,
            name = state.name.trim(),
            amountMinor = amount,
            currencyCode = CURRENCY_JPY,
            billingInterval = BillingInterval(state.billingIntervalUnit, intervalCount),
            startDate = startDate,
            nextBillingDate = nextBillingDate,
            categoryId = null,
            templateId = null,
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
                                nameError = if (AddSubscriptionUseCase.Error.EMPTY_NAME in result.errors)
                                    ERROR_EMPTY_NAME else null,
                                amountError = if (AddSubscriptionUseCase.Error.NEGATIVE_AMOUNT in result.errors)
                                    ERROR_NEGATIVE_AMOUNT else null,
                                intervalError = if (AddSubscriptionUseCase.Error.INVALID_INTERVAL_COUNT in result.errors)
                                    ERROR_INVALID_INTERVAL else null,
                                startDateError = if (AddSubscriptionUseCase.Error.START_DATE_AFTER_NEXT_BILLING_DATE in result.errors)
                                    ERROR_DATE_ORDER else null,
                                nextBillingDateError = null,
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
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")

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
                    clock = app.clock,
                )
            }
        }
    }
}
```

### 設計意図
- `savedEvent: SharedFlow<Unit>` で画面遷移をトリガーする（Navigation の `popBackStack` 用）
- `errorEvent: SharedFlow<String>` で保存時の例外を Snackbar 通知する（HomeViewModel と同パターン）
- `onSave` 内で `isSaving` チェックにより二重タップを防止する
- `amountText` のカンマ除去は `replace(",", "")` で対応する（`formatAmountJpy` のカンマ入力を許容）
- `memo` は空文字の場合 `null` に変換する
- バリデーションエラーは `AddSubscriptionUseCase.Error` の各値をチェックし、対応するフィールドエラーに変換する
- `Clock` をコンストラクタで受け取る（W-2対応）。`Instant.now(clock)` / `LocalDate.now(clock)` でテスタビリティを確保する
- `startDateText` / `nextBillingDateText` / `billingIntervalCountText` は `String` で保持し、`onSave` 時にパース・バリデーションする（C-1, W-3対応）
- 日付パースでは `runCatching` を使わず `try-catch` を使用する（S-1対応）

---

## タスク3: AddSubscriptionScreen 実装

**ファイル**: `app/src/main/java/com/subkiru/subkiru/feature/add/AddSubscriptionScreen.kt`

```kotlin
package com.subkiru.subkiru.feature.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.subkiru.subkiru.core.domain.model.BillingIntervalUnit
import com.subkiru.subkiru.ui.theme.SubKiruTheme

@Composable
fun AddSubscriptionScreen(
    viewModel: AddSubscriptionViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.savedEvent.collect {
            onNavigateBack()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.errorEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    AddSubscriptionContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onNameChange = viewModel::onNameChange,
        onAmountChange = viewModel::onAmountChange,
        onBillingIntervalUnitChange = viewModel::onBillingIntervalUnitChange,
        onBillingIntervalCountChange = viewModel::onBillingIntervalCountChange,
        onStartDateChange = viewModel::onStartDateChange,
        onNextBillingDateChange = viewModel::onNextBillingDateChange,
        onMemoChange = viewModel::onMemoChange,
        onSave = viewModel::onSave,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSubscriptionContent(
    uiState: AddSubscriptionUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onBillingIntervalUnitChange: (BillingIntervalUnit) -> Unit,
    onBillingIntervalCountChange: (String) -> Unit,
    onStartDateChange: (String) -> Unit,
    onNextBillingDateChange: (String) -> Unit,
    onMemoChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("サブスク追加") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "戻る",
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = SCREEN_HORIZONTAL_PADDING)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(FIELD_SPACING),
        ) {
            Spacer(modifier = Modifier.height(SECTION_TOP_PADDING))

            // サービス名
            OutlinedTextField(
                value = uiState.name,
                onValueChange = onNameChange,
                label = { Text("サービス名") },
                placeholder = { Text("例: Netflix") },
                isError = uiState.nameError != null,
                supportingText = uiState.nameError?.let { error -> { Text(error) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // 金額（円）
            OutlinedTextField(
                value = uiState.amountText,
                onValueChange = onAmountChange,
                label = { Text("金額（円）") },
                placeholder = { Text("例: 1490") },
                isError = uiState.amountError != null,
                supportingText = uiState.amountError?.let { error -> { Text(error) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // 請求間隔
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(FIELD_SPACING),
            ) {
                // 間隔数（String で管理。フィールドクリア可能）
                OutlinedTextField(
                    value = uiState.billingIntervalCountText,
                    onValueChange = onBillingIntervalCountChange,
                    label = { Text("間隔") },
                    isError = uiState.intervalError != null,
                    supportingText = uiState.intervalError?.let { error -> { Text(error) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )

                // 間隔単位ドロップダウン
                BillingIntervalUnitDropdown(
                    selected = uiState.billingIntervalUnit,
                    onSelect = onBillingIntervalUnitChange,
                    modifier = Modifier.weight(1f),
                )
            }

            // 開始日（String で管理。入力中の中間状態を保持する）
            OutlinedTextField(
                value = uiState.startDateText,
                onValueChange = onStartDateChange,
                label = { Text("開始日") },
                placeholder = { Text("yyyy/MM/dd") },
                isError = uiState.startDateError != null,
                supportingText = uiState.startDateError?.let { error -> { Text(error) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // 次回請求日（String で管理。入力中の中間状態を保持する）
            OutlinedTextField(
                value = uiState.nextBillingDateText,
                onValueChange = onNextBillingDateChange,
                label = { Text("次回請求日") },
                placeholder = { Text("yyyy/MM/dd") },
                isError = uiState.nextBillingDateError != null,
                supportingText = uiState.nextBillingDateError?.let { error -> { Text(error) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // メモ
            OutlinedTextField(
                value = uiState.memo,
                onValueChange = onMemoChange,
                label = { Text("メモ（任意）") },
                placeholder = { Text("例: 家族プラン") },
                minLines = MIN_MEMO_LINES,
                maxLines = MAX_MEMO_LINES,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(SECTION_TOP_PADDING))

            // 保存ボタン
            Button(
                onClick = onSave,
                enabled = !uiState.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = PROGRESS_STROKE_WIDTH,
                        modifier = Modifier.size(PROGRESS_SIZE),
                    )
                } else {
                    Text("保存")
                }
            }

            Spacer(modifier = Modifier.height(SCREEN_BOTTOM_PADDING))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BillingIntervalUnitDropdown(
    selected: BillingIntervalUnit,
    onSelect: (BillingIntervalUnit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected.toDisplayLabel(),
            onValueChange = {},
            readOnly = true,
            label = { Text("単位") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            BillingIntervalUnit.entries.forEach { unit ->
                DropdownMenuItem(
                    text = { Text(unit.toDisplayLabel()) },
                    onClick = {
                        onSelect(unit)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun BillingIntervalUnit.toDisplayLabel(): String = when (this) {
    BillingIntervalUnit.DAILY -> "日"
    BillingIntervalUnit.WEEKLY -> "週"
    BillingIntervalUnit.MONTHLY -> "月"
    BillingIntervalUnit.YEARLY -> "年"
}

private val SCREEN_HORIZONTAL_PADDING = 16.dp
private val SECTION_TOP_PADDING = 8.dp
private val SCREEN_BOTTOM_PADDING = 24.dp
private val FIELD_SPACING = 12.dp
private val PROGRESS_SIZE = 24.dp
private val PROGRESS_STROKE_WIDTH = 2.dp
private const val MIN_MEMO_LINES = 2
private const val MAX_MEMO_LINES = 4

@Preview(showBackground = true)
@Composable
private fun AddSubscriptionContentPreview() {
    SubKiruTheme {
        AddSubscriptionContent(
            uiState = AddSubscriptionUiState(
                name = "Netflix",
                amountText = "1490",
                startDateText = "2026/05/24",
                nextBillingDateText = "2026/06/24",
            ),
            snackbarHostState = SnackbarHostState(),
            onNavigateBack = {},
            onNameChange = {},
            onAmountChange = {},
            onBillingIntervalUnitChange = {},
            onBillingIntervalCountChange = {},
            onStartDateChange = {},
            onNextBillingDateChange = {},
            onMemoChange = {},
            onSave = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AddSubscriptionContentErrorPreview() {
    SubKiruTheme {
        AddSubscriptionContent(
            uiState = AddSubscriptionUiState(
                nameError = "サービス名を入力してください",
                amountError = "有効な金額を入力してください",
                startDateText = "2026/05/24",
                nextBillingDateText = "2026/06/24",
            ),
            snackbarHostState = SnackbarHostState(),
            onNavigateBack = {},
            onNameChange = {},
            onAmountChange = {},
            onBillingIntervalUnitChange = {},
            onBillingIntervalCountChange = {},
            onStartDateChange = {},
            onNextBillingDateChange = {},
            onMemoChange = {},
            onSave = {},
        )
    }
}
```

### 設計意図
- `AddSubscriptionScreen` と `AddSubscriptionContent` を分離する（HomeScreen と同パターン）
- `savedEvent` を `LaunchedEffect` で購読し、保存成功時に `onNavigateBack()` を呼ぶ
- `errorEvent` を `LaunchedEffect` で購読し、保存失敗時に Snackbar 表示する
- 日付フィールドは `OutlinedTextField` + `String` 管理で、入力中の中間状態を保持する（C-1対応）。バリデーションは `onSave` 時に実行する。`DatePickerDialog` は後続タスクで対応する
- 間隔数フィールドも `String` 管理で、フィールドクリア→新しい値入力が可能（W-3対応）
- `BillingIntervalUnitDropdown` は `ExposedDropdownMenuBox` で実装する
- `@Preview` はフォーム入力状態とエラー状態の2つを用意する
- `DateField` Composable は廃止し、通常の `OutlinedTextField` を直接使用する（C-1, S-1対応）

---

## タスク4: MainActivity 更新（W-1対応）

**ファイル**: `app/src/main/java/com/subkiru/subkiru/MainActivity.kt`

サブスク追加画面では BottomNavBar と FAB を非表示にする。

**変更前**:
```kotlin
Scaffold(
    modifier = Modifier.fillMaxSize(),
    bottomBar = {
        BottomNavBar(navController = navController)
    },
    floatingActionButton = {
        if (currentRoute == Screen.Home.route) {
            FloatingActionButton(
```

**変更後**:
```kotlin
val showBottomBar = currentRoute != Screen.AddSubscription.route

Scaffold(
    modifier = Modifier.fillMaxSize(),
    bottomBar = {
        if (showBottomBar) {
            BottomNavBar(navController = navController)
        }
    },
    floatingActionButton = {
        if (currentRoute == Screen.Home.route) {
            FloatingActionButton(
```

### 設計意図
- サブスク追加画面はタブ遷移ではなくプッシュ遷移であり、BottomNavBar を表示する必要がない
- FAB は既に `currentRoute == Screen.Home.route` で制御されているため変更不要
- `showBottomBar` を変数化して可読性を確保する

---

## タスク5: SubKiruNavGraph 更新

**ファイル**: `app/src/main/java/com/subkiru/subkiru/navigation/SubKiruNavGraph.kt`

AddSubscription のプレースホルダーを実画面に差し替える。

**変更前**:
```kotlin
composable(Screen.AddSubscription.route) {
    // サブスク追加画面（後続タスクで実装）
    ScreenPlaceholder(label = "Add Subscription")
}
```

**変更後**:
```kotlin
composable(Screen.AddSubscription.route) {
    val viewModel: AddSubscriptionViewModel = viewModel(
        factory = AddSubscriptionViewModel.factory(app),
    )
    AddSubscriptionScreen(
        viewModel = viewModel,
        onNavigateBack = { navController.popBackStack() },
    )
}
```

**追加する import**:
```kotlin
import com.subkiru.subkiru.feature.add.AddSubscriptionScreen
import com.subkiru.subkiru.feature.add.AddSubscriptionViewModel
```

### 設計意図
- `onNavigateBack` は `navController.popBackStack()` を渡す。Home 画面の FAB から遷移しているため、戻り先は Home になる
- `SubKiruNavGraph` の引数に `navController` が既にあるためそのまま使用する

---

## タスク6: AddSubscriptionViewModelTest 実装

**ファイル**: `app/src/test/java/com/subkiru/subkiru/feature/add/AddSubscriptionViewModelTest.kt`

```kotlin
package com.subkiru.subkiru.feature.add

import app.cash.turbine.test
import com.subkiru.subkiru.core.domain.usecase.AddSubscriptionUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class AddSubscriptionViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    // テスト用の固定 Clock（2026-05-24T00:00:00Z）
    private val fixedClock: Clock = Clock.fixed(
        Instant.parse("2026-05-24T00:00:00Z"),
        ZoneId.of("Asia/Tokyo"),
    )

    private val addSubscriptionUseCase: AddSubscriptionUseCase = mockk()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): AddSubscriptionViewModel {
        return AddSubscriptionViewModel(
            addSubscriptionUseCase = addSubscriptionUseCase,
            clock = fixedClock,
        )
    }

    @Test
    fun 初期状態が正しい() = runTest {
        val viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertEquals("", state.name)
        assertEquals("", state.amountText)
        assertEquals("1", state.billingIntervalCountText)
        assertEquals("2026/05/24", state.startDateText)
        assertEquals("2026/06/24", state.nextBillingDateText)
        assertFalse(state.isSaving)
        assertNull(state.nameError)
        assertNull(state.amountError)
    }

    @Test
    fun 名前変更でエラーがクリアされる() = runTest {
        val viewModel = createViewModel()

        // エラー状態を作る: 空の amountText で保存を試行
        viewModel.onSave()
        advanceUntilIdle()

        // 名前変更
        viewModel.onNameChange("Netflix")
        val state = viewModel.uiState.value
        assertEquals("Netflix", state.name)
        assertNull(state.nameError)
    }

    @Test
    fun 不正な金額で保存するとエラーになる() = runTest {
        val viewModel = createViewModel()

        viewModel.onNameChange("Netflix")
        viewModel.onAmountChange("abc")
        viewModel.onSave()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(AddSubscriptionViewModel.ERROR_INVALID_AMOUNT, state.amountError)
        assertFalse(state.isSaving)
    }

    @Test
    fun 不正な日付形式で保存するとエラーになる() = runTest {
        val viewModel = createViewModel()

        viewModel.onNameChange("Netflix")
        viewModel.onAmountChange("1490")
        viewModel.onStartDateChange("2026/13/01")
        viewModel.onSave()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(AddSubscriptionViewModel.ERROR_INVALID_DATE, state.startDateError)
        assertFalse(state.isSaving)
    }

    @Test
    fun 不正な次回請求日形式で保存するとエラーになる() = runTest {
        val viewModel = createViewModel()

        viewModel.onNameChange("Netflix")
        viewModel.onAmountChange("1490")
        viewModel.onNextBillingDateChange("invalid")
        viewModel.onSave()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(AddSubscriptionViewModel.ERROR_INVALID_DATE, state.nextBillingDateError)
        assertFalse(state.isSaving)
    }

    @Test
    fun 不正な間隔数で保存するとエラーになる() = runTest {
        val viewModel = createViewModel()

        viewModel.onNameChange("Netflix")
        viewModel.onAmountChange("1490")
        viewModel.onBillingIntervalCountChange("")
        viewModel.onSave()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(AddSubscriptionViewModel.ERROR_INVALID_INTERVAL, state.intervalError)
        assertFalse(state.isSaving)
    }

    @Test
    fun バリデーションエラーがフィールドに表示される() = runTest {
        coEvery { addSubscriptionUseCase.invoke(any()) } returns
            AddSubscriptionUseCase.Result.ValidationError(
                listOf(
                    AddSubscriptionUseCase.Error.EMPTY_NAME,
                    AddSubscriptionUseCase.Error.NEGATIVE_AMOUNT,
                ),
            )

        val viewModel = createViewModel()
        viewModel.onAmountChange("-100")
        viewModel.onSave()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(AddSubscriptionViewModel.ERROR_EMPTY_NAME, state.nameError)
        assertEquals(AddSubscriptionViewModel.ERROR_NEGATIVE_AMOUNT, state.amountError)
        assertFalse(state.isSaving)
    }

    @Test
    fun 保存成功でsavedEventが発行される() = runTest {
        coEvery { addSubscriptionUseCase.invoke(any()) } returns
            AddSubscriptionUseCase.Result.Success(id = 1L)

        val viewModel = createViewModel()
        viewModel.onNameChange("Netflix")
        viewModel.onAmountChange("1490")

        viewModel.savedEvent.test {
            viewModel.onSave()
            advanceUntilIdle()

            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun 保存成功時にUseCaseが呼ばれる() = runTest {
        coEvery { addSubscriptionUseCase.invoke(any()) } returns
            AddSubscriptionUseCase.Result.Success(id = 1L)

        val viewModel = createViewModel()
        viewModel.onNameChange("Netflix")
        viewModel.onAmountChange("1490")
        viewModel.onSave()
        advanceUntilIdle()

        coVerify(exactly = 1) { addSubscriptionUseCase.invoke(any()) }
    }

    @Test
    fun 保存中の例外でエラーイベントが発行される() = runTest {
        coEvery { addSubscriptionUseCase.invoke(any()) } throws RuntimeException("DB error")

        val viewModel = createViewModel()
        viewModel.onNameChange("Netflix")
        viewModel.onAmountChange("1490")

        viewModel.errorEvent.test {
            viewModel.onSave()
            advanceUntilIdle()

            val message = awaitItem()
            assertEquals(AddSubscriptionViewModel.ERROR_SAVE_FAILED, message)
            cancelAndIgnoreRemainingEvents()
        }

        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun メモが空文字の場合nullに変換される() = runTest {
        coEvery { addSubscriptionUseCase.invoke(any()) } returns
            AddSubscriptionUseCase.Result.Success(id = 1L)

        val viewModel = createViewModel()
        viewModel.onNameChange("Netflix")
        viewModel.onAmountChange("1490")
        viewModel.onMemoChange("   ")
        viewModel.onSave()
        advanceUntilIdle()

        coVerify {
            addSubscriptionUseCase.invoke(match { it.memo == null })
        }
    }
}
```

### テスト一覧（11テスト）

| テスト名 | 検証内容 |
|----------|---------|
| 初期状態が正しい | デフォルト値の確認（固定Clock由来の日付含む） |
| 名前変更でエラーがクリアされる | `onNameChange` で `nameError = null` |
| 不正な金額で保存するとエラーになる | `toLongOrNull` 失敗時のクライアント側バリデーション |
| 不正な日付形式で保存するとエラーになる | 開始日の `LocalDate.parse` 失敗時 → `startDateError` |
| 不正な次回請求日形式で保存するとエラーになる | 次回請求日の `LocalDate.parse` 失敗時 → `nextBillingDateError` |
| 不正な間隔数で保存するとエラーになる | 空文字の `toIntOrNull` 失敗時のバリデーション（W-3対応） |
| バリデーションエラーがフィールドに表示される | UseCase の `ValidationError` → 各フィールドエラー変換 |
| 保存成功でsavedEventが発行される | `SharedFlow<Unit>` の発行確認 |
| 保存成功時にUseCaseが呼ばれる | UseCase の `invoke` 呼び出し確認 |
| 保存中の例外でエラーイベントが発行される | 例外時の `errorEvent` 発行 + `isSaving = false` |
| メモが空文字の場合nullに変換される | 空白トリム → `null` 変換 |

---

## 変更しないもの
- `AddSubscriptionUseCase.kt` — 変更なし
- `SubscriptionRepository.kt` / `SubscriptionRepositoryImpl.kt` — 変更なし
- `SubKiruApplication.kt` — `addSubscriptionUseCase` / `clock` プロパティは既に存在する。変更なし
- `HomeScreen.kt` / `HomeViewModel.kt` — 変更なし

---

## レビュー指摘対応まとめ

| 指摘ID | 対応 |
|--------|------|
| C-1 | `startDateText` / `nextBillingDateText` を `String` 管理に変更。`DateField` Composable を廃止し通常の `OutlinedTextField` を使用。バリデーションは `onSave` 時に実行 |
| W-1 | タスク4 追加。`MainActivity` で `currentRoute != Screen.AddSubscription.route` のとき BottomNavBar を非表示 |
| W-2 | `Clock` をコンストラクタで受け取り、`Instant.now(clock)` / `LocalDate.now(clock)` を使用。テストでは `Clock.fixed()` を使用 |
| W-3 | `billingIntervalCountText` を `String` 管理に変更。フィールドクリア→新規入力が可能。バリデーションは `onSave` 時に実行 |
| W-1(再) | `dateError` を `startDateError` / `nextBillingDateError` に分割。各フィールドに正しくエラー表示 |
| S-1 | `runCatching` を `try-catch` に置換。`DateField` の `runCatching` を廃止 |
| S-1(再) | `CircularProgressIndicator` の `Modifier.height()` → `Modifier.size()` |

---

## 検証
- `./gradlew.bat assembleDebug` が成功すること
- `./gradlew.bat test` で AddSubscriptionViewModelTest の全11テストが成功すること
- FAB タップで追加画面に遷移し、BottomNavBar が非表示になること
- 戻るボタンで Home 画面に戻り、BottomNavBar が再表示されること
- サービス名・金額を入力して保存すると Home 画面に戻り、一覧に追加されること
- 空のサービス名で保存するとエラーが表示されること
- 不正な金額（文字列）で保存するとエラーが表示されること
- 不正な日付形式で保存するとエラーが表示されること
- 間隔数フィールドを空にして保存するとエラーが表示されること
