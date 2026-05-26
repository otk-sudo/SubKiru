# カレンダー画面 タスクドキュメント（Codex向け）

## 概要

サブスクの請求日をカレンダー上に可視化する画面を実装する。
月ナビゲーション対応で、`nextBillingDate` と `billingInterval` から表示月の請求日を逆算・投影する。
日付をタップすると、その日に請求されるサブスクの一覧を表示する。
外部カレンダーライブラリは使用せず、標準の Compose UI コンポーネントで実装する。

---

## 前提

### 既存リソース（変更不要）
- `GetSubscriptionsUseCase` — アクティブなサブスク一覧を `Flow<List<Subscription>>` で返す
- `Subscription` ドメインモデル — `id`, `name`, `amountMinor`, `billingInterval`, `nextBillingDate` 等
- `BillingInterval` / `BillingIntervalUnit` — 請求間隔の型（DAILY / WEEKLY / MONTHLY / YEARLY + count）
- `SubKiruApplication` — 手動DI。`getSubscriptionsUseCase` / `clock` プロパティ有り
- `Screen.Calendar` — ルート定義済み（`"calendar"`）
- `formatAmountJpy` — `core/ui/component/SubscriptionCard.kt` に定義済み（`internal`）

### MVPスコープ
- 支払い履歴との連動: 未実装（履歴未蓄積のため）
- 請求日の手動編集: 未実装（カレンダーは読み取り専用）
- 外部カレンダーライブラリ: 未導入

---

## タスク1: CalendarUiState 定義

**ファイル**: `app/src/main/java/com/subkiru/subkiru/feature/calendar/CalendarViewModel.kt`

ViewModel ファイルの先頭に UiState を定義する。

```kotlin
package com.subkiru.subkiru.feature.calendar

import java.time.LocalDate
import java.time.YearMonth

data class BillingEvent(
    val subscriptionId: Long,
    val name: String,
    val amountMinor: Long,
)

data class CalendarUiState(
    val displayedYearMonth: YearMonth = YearMonth.of(2026, 1),
    val today: LocalDate = LocalDate.of(2026, 1, 1),
    val billingDays: Set<Int> = emptySet(),
    val selectedDate: LocalDate? = null,
    val selectedDateBillings: List<BillingEvent> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)
```

### 設計意図
- `BillingEvent` は請求日に紐づくサブスクの概要（id・名前・金額）を保持する
- `displayedYearMonth` は現在表示中の月。デフォルト値はダミーで、ViewModel 初期化時に `Clock` から設定する
- `today` は本日の日付。カレンダーグリッドで「今日」をハイライトするために使用する
- `billingDays` は表示月内で請求がある日のセット（`Set<Int>`）。カレンダーセルにドットを表示する判定に使用する
- `selectedDate` はユーザーがタップした日付。`null` の場合は請求リストを非表示にする
- `selectedDateBillings` は選択日の請求一覧。選択日に請求がなければ空リスト
- 月変更時に `selectedDate` は `null` にリセットする

---

## タスク2: CalendarViewModel 実装

**ファイル**: `app/src/main/java/com/subkiru/subkiru/feature/calendar/CalendarViewModel.kt`

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
import com.subkiru.subkiru.core.domain.usecase.GetSubscriptionsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock

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
                // 表示月内の全請求日を計算する
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
                .catch {
                    _uiState.update {
                        it.copy(error = ERROR_MESSAGE_LOAD, isLoading = false)
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
    // 不正な interval を早期に弾く（count <= 0 だと無限ループになるため）
    if (interval.count <= 0) return emptyList()

    val monthStart = yearMonth.atDay(1)
    val monthEnd = yearMonth.atEndOfMonth()

    // nextBillingDate から逆方向にステップして monthStart 以前の日付を見つける
    var date = subscription.nextBillingDate
    while (date.isAfter(monthStart)) {
        date = subtractInterval(date, interval)
    }

    // 順方向にステップして月内の請求日を収集する
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
```

### 設計意図
- `combine(subscriptions, yearMonth, selectedDate)` で3つの Flow を結合する。いずれかが更新されると再計算される
- `computeBillingDatesInMonth` は `interval.count <= 0` を早期に弾いた上で、`nextBillingDate` から逆方向にステップして月開始前の日付を見つけ、順方向にステップして月内の請求日を収集する。`LocalDate.plusMonths()` が月末日を自動調整する（1/31 + 1ヶ月 = 2/28）
- `addInterval` / `subtractInterval` は `BillingIntervalUnit` に応じて `LocalDate` を加減算するヘルパー
- `computeBillingDatesInMonth` は `internal` にする（テストからアクセスするため）
- 月変更時に `selectedDate` を `null` にリセットする（前月の選択状態が残らないようにする）
- `Clock` を注入してテスタブルにする（`YearMonth.now(clock)` / `LocalDate.now(clock)`）
- `catch` は `combine` の下流に配置する（collector 側パターン）
- エラー時は固定メッセージ `ERROR_MESSAGE_LOAD` を使用する（HomeViewModel / AnalyticsViewModel と同パターン）

---

## タスク3: CalendarScreen 実装

**ファイル**: `app/src/main/java/com/subkiru/subkiru/feature/calendar/CalendarScreen.kt`

```kotlin
package com.subkiru.subkiru.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.subkiru.subkiru.core.ui.component.formatAmountJpy
import com.subkiru.subkiru.ui.theme.SubKiruTheme
import com.subkiru.subkiru.ui.theme.TextSecondary
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CalendarContent(
        uiState = uiState,
        onPreviousMonth = viewModel::onPreviousMonth,
        onNextMonth = viewModel::onNextMonth,
        onDateSelected = viewModel::onDateSelected,
        modifier = modifier,
    )
}

@Composable
private fun CalendarContent(
    uiState: CalendarUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        uiState.isLoading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        uiState.error != null -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = uiState.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        else -> {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = SCREEN_HORIZONTAL_PADDING),
            ) {
                Spacer(modifier = Modifier.height(SECTION_TOP_PADDING))

                MonthHeader(
                    yearMonth = uiState.displayedYearMonth,
                    onPreviousMonth = onPreviousMonth,
                    onNextMonth = onNextMonth,
                )

                Spacer(modifier = Modifier.height(CALENDAR_VERTICAL_SPACING))

                DayOfWeekHeader()

                CalendarGrid(
                    yearMonth = uiState.displayedYearMonth,
                    billingDays = uiState.billingDays,
                    selectedDate = uiState.selectedDate,
                    today = uiState.today,
                    onDateSelected = onDateSelected,
                )

                if (uiState.selectedDate != null) {
                    Spacer(modifier = Modifier.height(SELECTED_SECTION_TOP_SPACING))

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Spacer(modifier = Modifier.height(SELECTED_SECTION_TOP_SPACING))

                    SelectedDateSection(
                        selectedDate = uiState.selectedDate,
                        billings = uiState.selectedDateBillings,
                    )
                }

                Spacer(modifier = Modifier.height(SCREEN_BOTTOM_PADDING))
            }
        }
    }
}

@Composable
private fun MonthHeader(
    yearMonth: YearMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPreviousMonth) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "前の月",
            )
        }
        Text(
            text = "${yearMonth.year}年${yearMonth.monthValue}月",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        IconButton(onClick = onNextMonth) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "次の月",
            )
        }
    }
}

@Composable
private fun DayOfWeekHeader(
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        DAY_OF_WEEK_LABELS.forEach { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CalendarGrid(
    yearMonth: YearMonth,
    billingDays: Set<Int>,
    selectedDate: LocalDate?,
    today: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    // DayOfWeek.value は ISO-8601（月=1..日=7）。% 7 で日曜始まり（日=0..土=6）に変換する
    val firstDayOffset = yearMonth.atDay(1).dayOfWeek.value % DAYS_IN_WEEK
    val daysInMonth = yearMonth.lengthOfMonth()
    val totalCells = firstDayOffset + daysInMonth
    val rows = (totalCells + DAYS_IN_WEEK - 1) / DAYS_IN_WEEK

    Column(modifier = modifier.fillMaxWidth()) {
        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until DAYS_IN_WEEK) {
                    val cellIndex = row * DAYS_IN_WEEK + col
                    val day = cellIndex - firstDayOffset + 1

                    if (day in 1..daysInMonth) {
                        val date = yearMonth.atDay(day)
                        val isSelected = date == selectedDate
                        val isToday = date == today
                        val hasBilling = day in billingDays

                        DayCell(
                            day = day,
                            isSelected = isSelected,
                            isToday = isToday,
                            hasBilling = hasBilling,
                            onClick = { onDateSelected(date) },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    isSelected: Boolean,
    isToday: Boolean,
    hasBilling: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = DAY_CELL_VERTICAL_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(DAY_CELL_SIZE)
                .clip(CircleShape)
                .background(backgroundColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
            )
        }
        // 請求ありドット
        if (hasBilling) {
            Box(
                modifier = Modifier
                    .padding(top = BILLING_DOT_TOP_PADDING)
                    .size(BILLING_DOT_SIZE)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        } else {
            // ドットなしでも高さを揃える
            Spacer(
                modifier = Modifier
                    .padding(top = BILLING_DOT_TOP_PADDING)
                    .size(BILLING_DOT_SIZE),
            )
        }
    }
}

@Composable
private fun SelectedDateSection(
    selectedDate: LocalDate,
    billings: List<BillingEvent>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(BILLING_LIST_ITEM_SPACING),
    ) {
        Text(
            text = "${selectedDate.monthValue}月${selectedDate.dayOfMonth}日の請求",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        if (billings.isEmpty()) {
            Text(
                text = "この日の請求はありません",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(vertical = EMPTY_MESSAGE_VERTICAL_PADDING),
            )
        } else {
            billings.forEach { billing ->
                BillingEventItem(billing = billing)
            }
        }
    }
}

@Composable
private fun BillingEventItem(
    billing: BillingEvent,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CARD_PADDING),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = billing.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = formatAmountJpy(billing.amountMinor),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private val SCREEN_HORIZONTAL_PADDING = 16.dp
private val SECTION_TOP_PADDING = 8.dp
private val SCREEN_BOTTOM_PADDING = 24.dp
private val CALENDAR_VERTICAL_SPACING = 8.dp
private val SELECTED_SECTION_TOP_SPACING = 16.dp
private val DAY_CELL_SIZE = 36.dp
private val DAY_CELL_VERTICAL_PADDING = 4.dp
private val BILLING_DOT_SIZE = 6.dp
private val BILLING_DOT_TOP_PADDING = 2.dp
private val BILLING_LIST_ITEM_SPACING = 8.dp
private val EMPTY_MESSAGE_VERTICAL_PADDING = 8.dp
private val CARD_PADDING = 16.dp
private const val DAYS_IN_WEEK = 7
private val DAY_OF_WEEK_LABELS = listOf("日", "月", "火", "水", "木", "金", "土")

@Preview(showBackground = true)
@Composable
private fun CalendarContentPreview() {
    SubKiruTheme {
        CalendarContent(
            uiState = CalendarUiState(
                displayedYearMonth = YearMonth.of(2026, 5),
                today = LocalDate.of(2026, 5, 24),
                billingDays = setOf(1, 15, 24),
                selectedDate = LocalDate.of(2026, 5, 15),
                selectedDateBillings = listOf(
                    BillingEvent(1L, "Netflix", 1_490L),
                    BillingEvent(2L, "Spotify", 980L),
                ),
                isLoading = false,
            ),
            onPreviousMonth = {},
            onNextMonth = {},
            onDateSelected = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CalendarContentEmptySelectionPreview() {
    SubKiruTheme {
        CalendarContent(
            uiState = CalendarUiState(
                displayedYearMonth = YearMonth.of(2026, 5),
                today = LocalDate.of(2026, 5, 24),
                billingDays = setOf(15),
                selectedDate = LocalDate.of(2026, 5, 10),
                selectedDateBillings = emptyList(),
                isLoading = false,
            ),
            onPreviousMonth = {},
            onNextMonth = {},
            onDateSelected = {},
        )
    }
}
```

### 設計意図
- `CalendarScreen` と `CalendarContent` を分離する（HomeScreen / AnalyticsScreen と同パターン）
- 3状態を管理する: loading / error / normal。空サブスク時もカレンダーは表示する（AnalyticsScreen の empty 状態とは異なる）
- `MonthHeader` は左右の矢印アイコンと「2026年5月」テキストで月ナビゲーションを提供する
- `DayOfWeekHeader` は日〜土の曜日ラベルを表示する。日本のカレンダー慣例に従い日曜始まり
- `CalendarGrid` は `yearMonth.atDay(1).dayOfWeek.value % 7` で日曜=0 のオフセットを計算し、行×列のグリッドを描画する
- `DayCell` は選択状態・今日・請求ありの3つのビジュアル状態を持つ。請求ありの日は小さなドット（`BILLING_DOT_SIZE = 6.dp`）を表示する
- `SelectedDateSection` は選択日の請求リストを表示する。請求がない場合は「この日の請求はありません」を表示する
- `BillingEventItem` はサブスク名と金額を横並びで表示する（AnalyticsScreen の BreakdownItem と類似構造）
- `formatAmountJpy` を `SubscriptionCard.kt` から再利用する（`internal` スコープ）
- `SCREEN_BOTTOM_PADDING = 24.dp`（FAB なし画面。AnalyticsScreen と同値）
- `@Preview` はデータあり状態と空選択状態の2つを用意する

---

## タスク4: SubKiruNavGraph 更新

**ファイル**: `app/src/main/java/com/subkiru/subkiru/navigation/SubKiruNavGraph.kt`

Calendar のプレースホルダーを実画面に差し替える。

**変更前**:
```kotlin
composable(Screen.Calendar.route) {
    // カレンダー画面（後続タスクで実装）
    ScreenPlaceholder(label = "Calendar")
}
```

**変更後**:
```kotlin
composable(Screen.Calendar.route) {
    val viewModel: CalendarViewModel = viewModel(
        factory = CalendarViewModel.factory(app),
    )
    CalendarScreen(viewModel = viewModel)
}
```

**追加する import**:
```kotlin
import com.subkiru.subkiru.feature.calendar.CalendarScreen
import com.subkiru.subkiru.feature.calendar.CalendarViewModel
```

---

## タスク5: CalendarViewModelTest 実装

**ファイル**: `app/src/test/java/com/subkiru/subkiru/feature/calendar/CalendarViewModelTest.kt`

```kotlin
package com.subkiru.subkiru.feature.calendar

import app.cash.turbine.test
import com.subkiru.subkiru.core.domain.model.BillingInterval
import com.subkiru.subkiru.core.domain.model.BillingIntervalUnit
import com.subkiru.subkiru.core.domain.model.Subscription
import com.subkiru.subkiru.core.domain.usecase.GetSubscriptionsUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private val fixedClock = Clock.fixed(
        Instant.parse("2026-05-24T00:00:00Z"),
        ZoneId.of("Asia/Tokyo"),
    )

    private val subscriptionsFlow = MutableSharedFlow<List<Subscription>>(replay = 1)

    private val getSubscriptionsUseCase: GetSubscriptionsUseCase = mockk {
        every { this@mockk.invoke() } returns subscriptionsFlow
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): CalendarViewModel {
        return CalendarViewModel(
            getSubscriptionsUseCase = getSubscriptionsUseCase,
            clock = fixedClock,
        )
    }

    @Test
    fun 初期状態はローディング中である() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.isLoading)
            assertEquals(YearMonth.of(2026, 5), state.displayedYearMonth)
            assertEquals(LocalDate.of(2026, 5, 24), state.today)
            assertTrue(state.billingDays.isEmpty())
            assertNull(state.selectedDate)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun サブスクの請求日がカレンダーに反映される() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()

            subscriptionsFlow.emit(SAMPLE_SUBSCRIPTIONS)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertFalse(state.isLoading)
            // Netflix: nextBillingDate=5/15, Spotify: nextBillingDate=5/20
            assertTrue(state.billingDays.contains(15))
            assertTrue(state.billingDays.contains(20))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun 次の月に進むと表示月が更新される() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()

            subscriptionsFlow.emit(SAMPLE_SUBSCRIPTIONS)
            advanceUntilIdle()
            expectMostRecentItem()

            viewModel.onNextMonth()
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals(YearMonth.of(2026, 6), state.displayedYearMonth)
            assertNull(state.selectedDate)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun 前の月に戻ると表示月が更新される() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()

            subscriptionsFlow.emit(SAMPLE_SUBSCRIPTIONS)
            advanceUntilIdle()
            expectMostRecentItem()

            viewModel.onPreviousMonth()
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals(YearMonth.of(2026, 4), state.displayedYearMonth)
            assertNull(state.selectedDate)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun 日付を選択すると該当日の請求一覧が表示される() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()

            subscriptionsFlow.emit(SAMPLE_SUBSCRIPTIONS)
            advanceUntilIdle()
            expectMostRecentItem()

            viewModel.onDateSelected(LocalDate.of(2026, 5, 15))
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals(LocalDate.of(2026, 5, 15), state.selectedDate)
            assertEquals(1, state.selectedDateBillings.size)
            assertEquals("Netflix", state.selectedDateBillings[0].name)
            assertEquals(1_490L, state.selectedDateBillings[0].amountMinor)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun サブスク取得でエラーが発生するとエラー状態になる() = runTest {
        val errorFlow = flow<List<Subscription>> {
            throw RuntimeException("DB error")
        }
        val errorGetSubscriptionsUseCase: GetSubscriptionsUseCase = mockk {
            every { this@mockk.invoke() } returns errorFlow
        }
        val viewModel = CalendarViewModel(
            getSubscriptionsUseCase = errorGetSubscriptionsUseCase,
            clock = fixedClock,
        )

        viewModel.uiState.test {
            awaitItem()
            advanceUntilIdle()

            val state = awaitItem()
            assertFalse(state.isLoading)
            assertEquals(CalendarViewModel.ERROR_MESSAGE_LOAD, state.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun 月額サブスクが別の月にも請求日として計算される() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()

            subscriptionsFlow.emit(SAMPLE_SUBSCRIPTIONS)
            advanceUntilIdle()
            expectMostRecentItem()

            // 6月に進む — Netflix(毎月15日)が6月にも請求される
            viewModel.onNextMonth()
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals(YearMonth.of(2026, 6), state.displayedYearMonth)
            assertTrue(state.billingDays.contains(15))
            assertTrue(state.billingDays.contains(20))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun 週次サブスクが月内に複数の請求日を持つ() = runTest {
        val viewModel = createViewModel()
        val weeklySubscription = listOf(
            createSubscription(
                id = 3L,
                name = "Weekly Service",
                amountMinor = 500L,
                unit = BillingIntervalUnit.WEEKLY,
                count = 1,
                nextBillingDate = LocalDate.of(2026, 5, 1),
            ),
        )

        viewModel.uiState.test {
            awaitItem()

            subscriptionsFlow.emit(weeklySubscription)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            // 5月に5回の請求 (1, 8, 15, 22, 29)
            assertEquals(5, state.billingDays.size)
            assertTrue(state.billingDays.containsAll(setOf(1, 8, 15, 22, 29)))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun 年次サブスクが該当月以外では請求日なし() = runTest {
        val viewModel = createViewModel()
        val yearlySubscription = listOf(
            createSubscription(
                id = 4L,
                name = "Yearly Service",
                amountMinor = 12_000L,
                unit = BillingIntervalUnit.YEARLY,
                count = 1,
                nextBillingDate = LocalDate.of(2026, 3, 15),
            ),
        )

        viewModel.uiState.test {
            awaitItem()

            subscriptionsFlow.emit(yearlySubscription)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            // 表示月は5月だが、年次サブスクの請求は3月 → 5月には請求なし
            assertTrue(state.billingDays.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    companion object {
        private val SAMPLE_SUBSCRIPTIONS = listOf(
            createSubscription(
                id = 1L,
                name = "Netflix",
                amountMinor = 1_490L,
                unit = BillingIntervalUnit.MONTHLY,
                count = 1,
                nextBillingDate = LocalDate.of(2026, 5, 15),
            ),
            createSubscription(
                id = 2L,
                name = "Spotify",
                amountMinor = 980L,
                unit = BillingIntervalUnit.MONTHLY,
                count = 1,
                nextBillingDate = LocalDate.of(2026, 5, 20),
            ),
        )

        private fun createSubscription(
            id: Long,
            name: String,
            amountMinor: Long,
            unit: BillingIntervalUnit,
            count: Int,
            nextBillingDate: LocalDate,
        ): Subscription = Subscription(
            id = id,
            name = name,
            amountMinor = amountMinor,
            currencyCode = "JPY",
            billingInterval = BillingInterval(unit, count),
            startDate = LocalDate.of(2025, 1, 1),
            nextBillingDate = nextBillingDate,
            categoryId = null,
            templateId = null,
            logoUri = null,
            memo = null,
            isActive = true,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
    }
}
```

### テスト一覧（9テスト）

| テスト名 | 検証内容 |
|----------|---------|
| 初期状態はローディング中である | デフォルト値・表示月・today の確認 |
| サブスクの請求日がカレンダーに反映される | `billingDays` に `nextBillingDate.dayOfMonth` が含まれる |
| 次の月に進むと表示月が更新される | `onNextMonth` → `displayedYearMonth` +1、`selectedDate` リセット |
| 前の月に戻ると表示月が更新される | `onPreviousMonth` → `displayedYearMonth` -1、`selectedDate` リセット |
| 日付を選択すると該当日の請求一覧が表示される | `onDateSelected` → `selectedDateBillings` に該当サブスクが含まれる |
| サブスク取得でエラーが発生するとエラー状態になる | エラー時の固定メッセージ |
| 月額サブスクが別の月にも請求日として計算される | 月ナビ後に `computeBillingDatesInMonth` の投影結果を検証 |
| 週次サブスクが月内に複数の請求日を持つ | WEEKLY の投影で5月に5件（1,8,15,22,29） |
| 年次サブスクが該当月以外では請求日なし | YEARLY で3月請求のサブスクが5月表示時に `billingDays` 空 |

---

## 変更しないもの
- `GetSubscriptionsUseCase.kt` — 変更なし
- `SubKiruApplication.kt` — `getSubscriptionsUseCase` / `clock` プロパティは既に存在する。変更なし
- `MainActivity.kt` — 変更なし
- `SubscriptionCard.kt` — 変更なし（`formatAmountJpy` を再利用するのみ）

---

## 検証
- `./gradlew.bat assembleDebug` が成功すること
- `./gradlew.bat test` で CalendarViewModelTest の全9テストが成功すること
- BottomNavBar のカレンダータブをタップしてカレンダー画面に遷移すること
- 月ヘッダーの矢印で前月・次月にナビゲートできること
- 請求日のあるセルにドットが表示されること
- 日付をタップすると該当日の請求一覧が表示されること
- 請求のない日付をタップすると「この日の請求はありません」が表示されること
- 月を移動しても請求日が正しく投影されること（月額サブスクが毎月表示される）
