# 分析画面 タスクドキュメント（Codex向け）

## 概要

サブスクの支出状況を可視化する分析画面を実装する。
月額合計・年額換算・サブスク数の概要と、サブスク別の月額内訳をバーで表示する。
外部チャートライブラリは使用せず、標準の Compose UI コンポーネントで実装する。

---

## 前提

### 既存リソース（変更不要）
- `GetSubscriptionsUseCase` — アクティブなサブスク一覧を `Flow<List<Subscription>>` で返す
- `CalculateMonthlyTotalUseCase` — 月額合計を `Flow<Long>` で返す
- `Subscription` ドメインモデル — `amountMinor`, `billingInterval`, `name` 等
- `BillingInterval` / `BillingIntervalUnit` — 請求間隔の型
- `SubKiruApplication` — 手動DI。`getSubscriptionsUseCase` / `calculateMonthlyTotalUseCase` プロパティ有り
- `Screen.Analytics` — ルート定義済み（`"analytics"`）
- `formatAmountJpy` — `core/ui/component/SubscriptionCard.kt` に定義済み（`internal`）

### MVPスコープ
- カテゴリ別集計: 未実装（カテゴリ未設定のため）
- 月別推移グラフ: 未実装（支払い履歴未蓄積のため）
- 外部チャートライブラリ: 未導入

---

## タスク1: AnalyticsUiState 定義

**ファイル**: `app/src/main/java/com/subkiru/subkiru/feature/analytics/AnalyticsViewModel.kt`

ViewModel ファイルの先頭に UiState を定義する。

```kotlin
package com.subkiru.subkiru.feature.analytics

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
```

### 設計意図
- `SubscriptionBreakdown` は各サブスクの `id`・月額換算額・パーセンテージを保持する。`id` は LazyColumn の `key` に使用する
- `percentage` は `Float`（0.0〜1.0）。`LinearProgressIndicator` の `progress` にそのまま渡せる
- `annualTotal` は `monthlyTotal * 12` で算出する
- `breakdowns` は月額降順でソートする

---

## タスク2: AnalyticsViewModel 実装

**ファイル**: `app/src/main/java/com/subkiru/subkiru/feature/analytics/AnalyticsViewModel.kt`

```kotlin
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
                        .map { sub ->
                            SubscriptionBreakdown(
                                id = sub.id,
                                name = sub.name,
                                monthlyAmount = toMonthlyAmount(sub),
                                percentage = 0f,
                            )
                        }
                        .sortedByDescending { it.monthlyAmount }

                    val monthlyTotal = breakdowns.sumOf { it.monthlyAmount }

                    val breakdownsWithPercentage = if (monthlyTotal > 0L) {
                        breakdowns.map { it.copy(percentage = it.monthlyAmount.toFloat() / monthlyTotal) }
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
```

### 設計意図
- `GetSubscriptionsUseCase` のみを使用する。`CalculateMonthlyTotalUseCase` は使わず、ViewModel 内で `toMonthlyAmount` を使って個別計算する（内訳データが必要なため）
- `toMonthlyAmount` は `HomeViewModel` と同じロジック。共通化は将来のリファクタリングで対応する
- `breakdowns` は月額降順ソートし、最も高いサブスクが先頭に来る
- `percentage` は `monthlyAmount / monthlyTotal` で算出。`monthlyTotal == 0` の場合は `0f` のまま
- `annualTotal = monthlyTotal * 12` で年額換算する
- エラー時は固定メッセージ `ERROR_MESSAGE_LOAD` を使用する（HomeViewModel と同パターン）

---

## タスク3: AnalyticsScreen 実装

**ファイル**: `app/src/main/java/com/subkiru/subkiru/feature/analytics/AnalyticsScreen.kt`

```kotlin
package com.subkiru.subkiru.feature.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.subkiru.subkiru.core.ui.component.formatAmountJpy
import com.subkiru.subkiru.ui.theme.SubKiruTheme
import com.subkiru.subkiru.ui.theme.TextSecondary

@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AnalyticsContent(
        uiState = uiState,
        modifier = modifier,
    )
}

@Composable
private fun AnalyticsContent(
    uiState: AnalyticsUiState,
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

        uiState.subscriptionCount == 0 -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "分析するサブスクがありません",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "サブスクを追加すると分析が表示されます",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }
        }

        else -> {
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = SCREEN_HORIZONTAL_PADDING,
                    end = SCREEN_HORIZONTAL_PADDING,
                    top = SCREEN_TOP_PADDING,
                    bottom = SCREEN_BOTTOM_PADDING,
                ),
                verticalArrangement = Arrangement.spacedBy(LIST_ITEM_SPACING),
            ) {
                // 概要カード
                item(key = "summary") {
                    SummaryCard(
                        monthlyTotal = uiState.monthlyTotal,
                        annualTotal = uiState.annualTotal,
                        subscriptionCount = uiState.subscriptionCount,
                    )
                }

                // セクションヘッダー
                item(key = "breakdown_header") {
                    Text(
                        text = "サブスク別内訳",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = SECTION_TOP_PADDING),
                    )
                }

                // サブスク別内訳
                items(
                    items = uiState.breakdowns,
                    key = { it.id },
                ) { breakdown ->
                    BreakdownItem(breakdown = breakdown)
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    monthlyTotal: Long,
    annualTotal: Long,
    subscriptionCount: Int,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
    ) {
        Column(
            modifier = Modifier.padding(CARD_PADDING),
            verticalArrangement = Arrangement.spacedBy(CARD_ITEM_SPACING),
        ) {
            // 月額合計
            SummaryRow(label = "月額合計", value = formatAmountJpy(monthlyTotal))

            // 年額換算
            SummaryRow(label = "年額換算", value = formatAmountJpy(annualTotal))

            // サブスク数
            SummaryRow(label = "サブスク数", value = "${subscriptionCount}件")
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun BreakdownItem(
    breakdown: SubscriptionBreakdown,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
    ) {
        Column(
            modifier = Modifier.padding(CARD_PADDING),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = breakdown.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = formatAmountJpy(breakdown.monthlyAmount),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(PROGRESS_TOP_SPACING))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LinearProgressIndicator(
                    progress = { breakdown.percentage },
                    modifier = Modifier
                        .weight(1f)
                        .height(PROGRESS_HEIGHT),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
                Text(
                    text = "${(breakdown.percentage * PERCENTAGE_MULTIPLIER).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(start = PERCENTAGE_START_PADDING),
                )
            }
        }
    }
}

private val SCREEN_HORIZONTAL_PADDING = 16.dp
private val SCREEN_TOP_PADDING = 8.dp
private val SCREEN_BOTTOM_PADDING = 24.dp
private val LIST_ITEM_SPACING = 12.dp
private val SECTION_TOP_PADDING = 8.dp
private val CARD_PADDING = 16.dp
private val CARD_ITEM_SPACING = 12.dp
private val PROGRESS_TOP_SPACING = 8.dp
private val PROGRESS_HEIGHT = 8.dp
private val PERCENTAGE_START_PADDING = 8.dp
private const val PERCENTAGE_MULTIPLIER = 100

@Preview(showBackground = true)
@Composable
private fun AnalyticsContentPreview() {
    SubKiruTheme {
        AnalyticsContent(
            uiState = AnalyticsUiState(
                monthlyTotal = 3_750L,
                annualTotal = 45_000L,
                subscriptionCount = 3,
                breakdowns = listOf(
                    SubscriptionBreakdown(1L, "Netflix", 1_490L, 0.40f),
                    SubscriptionBreakdown(2L, "YouTube Premium", 1_280L, 0.34f),
                    SubscriptionBreakdown(3L, "Spotify", 980L, 0.26f),
                ),
                isLoading = false,
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AnalyticsContentEmptyPreview() {
    SubKiruTheme {
        AnalyticsContent(
            uiState = AnalyticsUiState(
                subscriptionCount = 0,
                isLoading = false,
            ),
        )
    }
}
```

### 設計意図
- `AnalyticsScreen` と `AnalyticsContent` を分離する（HomeScreen と同パターン）
- 4状態を管理する: loading / error / empty / データあり（HomeScreen と同パターン）
- `SummaryCard` は月額合計・年額換算・サブスク数を1枚のカードに表示する
- `BreakdownItem` はサブスク名・月額・パーセンテージバーを表示する。`LinearProgressIndicator` で視覚的に割合を示す
- `formatAmountJpy` を `SubscriptionCard.kt` から再利用する（`internal` スコープ）
- `LazyColumn` の `items` に `key = { it.id }` を指定する（AGENTS.md ルール。`name` はサブスク重複の可能性があるため `id` を使用）
- `SCREEN_BOTTOM_PADDING = 24.dp`。HomeScreen の 88dp は FAB 回避用であり、Analytics 画面には FAB がないため小さい値で十分
- `@Preview` はデータあり状態と空状態の2つを用意する
- Snackbar は不要（読み取り専用画面のためエラーは画面内に表示）

---

## タスク4: SubKiruNavGraph 更新

**ファイル**: `app/src/main/java/com/subkiru/subkiru/navigation/SubKiruNavGraph.kt`

Analytics のプレースホルダーを実画面に差し替える。

**変更前**:
```kotlin
composable(Screen.Analytics.route) {
    // 分析画面（後続タスクで実装）
    ScreenPlaceholder(label = "Analytics")
}
```

**変更後**:
```kotlin
composable(Screen.Analytics.route) {
    val viewModel: AnalyticsViewModel = viewModel(
        factory = AnalyticsViewModel.factory(app),
    )
    AnalyticsScreen(viewModel = viewModel)
}
```

**追加する import**:
```kotlin
import com.subkiru.subkiru.feature.analytics.AnalyticsScreen
import com.subkiru.subkiru.feature.analytics.AnalyticsViewModel
```

---

## タスク5: AnalyticsViewModelTest 実装

**ファイル**: `app/src/test/java/com/subkiru/subkiru/feature/analytics/AnalyticsViewModelTest.kt`

```kotlin
package com.subkiru.subkiru.feature.analytics

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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

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

    private fun createViewModel(): AnalyticsViewModel {
        return AnalyticsViewModel(
            getSubscriptionsUseCase = getSubscriptionsUseCase,
        )
    }

    @Test
    fun 初期状態はローディング中である() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.isLoading)
            assertEquals(0L, state.monthlyTotal)
            assertEquals(0, state.subscriptionCount)
            assertTrue(state.breakdowns.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun サブスク一覧から月額合計と年額換算が計算される() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()

            subscriptionsFlow.emit(SAMPLE_SUBSCRIPTIONS)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertFalse(state.isLoading)
            assertEquals(EXPECTED_MONTHLY_TOTAL, state.monthlyTotal)
            assertEquals(EXPECTED_MONTHLY_TOTAL * 12, state.annualTotal)
            assertEquals(SAMPLE_SUBSCRIPTIONS.size, state.subscriptionCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun 内訳が月額降順でソートされる() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()

            subscriptionsFlow.emit(SAMPLE_SUBSCRIPTIONS)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals(SAMPLE_SUBSCRIPTIONS.size, state.breakdowns.size)
            // Netflix(1490) > Spotify(980) の順
            assertEquals("Netflix", state.breakdowns[0].name)
            assertEquals("Spotify", state.breakdowns[1].name)
            assertTrue(state.breakdowns[0].monthlyAmount >= state.breakdowns[1].monthlyAmount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun パーセンテージが正しく計算される() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()

            subscriptionsFlow.emit(SAMPLE_SUBSCRIPTIONS)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            // 合計が正しければ、各パーセンテージの合計は約1.0になる
            val totalPercentage = state.breakdowns.sumOf { it.percentage.toDouble() }
            assertTrue(totalPercentage in 0.99..1.01)
            // 各パーセンテージは0〜1の範囲
            state.breakdowns.forEach { breakdown ->
                assertTrue(breakdown.percentage in 0f..1f)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun 空のサブスク一覧が正しく反映される() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()

            subscriptionsFlow.emit(emptyList())
            advanceUntilIdle()

            val state = awaitItem()
            assertFalse(state.isLoading)
            assertEquals(0L, state.monthlyTotal)
            assertEquals(0L, state.annualTotal)
            assertEquals(0, state.subscriptionCount)
            assertTrue(state.breakdowns.isEmpty())
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
        val viewModel = AnalyticsViewModel(
            getSubscriptionsUseCase = errorGetSubscriptionsUseCase,
        )

        viewModel.uiState.test {
            awaitItem()
            advanceUntilIdle()

            val state = awaitItem()
            assertFalse(state.isLoading)
            assertEquals(AnalyticsViewModel.ERROR_MESSAGE_LOAD, state.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun 年額サブスクの月額換算が正しい() = runTest {
        val viewModel = createViewModel()
        val yearlySubscription = listOf(
            createSubscription(
                id = 1L,
                name = "Amazon Prime",
                amountMinor = 5_900L,
                unit = BillingIntervalUnit.YEARLY,
                count = 1,
            ),
        )

        viewModel.uiState.test {
            awaitItem()

            subscriptionsFlow.emit(yearlySubscription)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            // 5900 / 12 = 491
            assertEquals(491L, state.monthlyTotal)
            assertEquals(491L * 12, state.annualTotal)
            assertEquals(1, state.breakdowns.size)
            assertEquals(491L, state.breakdowns[0].monthlyAmount)
            assertEquals(1.0f, state.breakdowns[0].percentage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    companion object {
        private const val EXPECTED_MONTHLY_TOTAL = 2_470L

        private val SAMPLE_SUBSCRIPTIONS = listOf(
            createSubscription(
                id = 1L,
                name = "Netflix",
                amountMinor = 1_490L,
                unit = BillingIntervalUnit.MONTHLY,
                count = 1,
            ),
            createSubscription(
                id = 2L,
                name = "Spotify",
                amountMinor = 980L,
                unit = BillingIntervalUnit.MONTHLY,
                count = 1,
            ),
        )

        private fun createSubscription(
            id: Long,
            name: String,
            amountMinor: Long,
            unit: BillingIntervalUnit,
            count: Int,
        ): Subscription = Subscription(
            id = id,
            name = name,
            amountMinor = amountMinor,
            currencyCode = "JPY",
            billingInterval = BillingInterval(unit, count),
            startDate = LocalDate.of(2025, 1, 1),
            nextBillingDate = LocalDate.of(2026, 6, 1),
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

### テスト一覧（7テスト）

| テスト名 | 検証内容 |
|----------|---------|
| 初期状態はローディング中である | デフォルト値の確認 |
| サブスク一覧から月額合計と年額換算が計算される | `monthlyTotal` / `annualTotal` / `subscriptionCount` |
| 内訳が月額降順でソートされる | `breakdowns` のソート順 |
| パーセンテージが正しく計算される | 各 `percentage` が0〜1、合計が約1.0 |
| 空のサブスク一覧が正しく反映される | 空リスト時の状態 |
| サブスク取得でエラーが発生するとエラー状態になる | エラー時の固定メッセージ |
| 年額サブスクの月額換算が正しい | YEARLY の `toMonthlyAmount` 計算検証 |

---

## 変更しないもの
- `GetSubscriptionsUseCase.kt` — 変更なし
- `CalculateMonthlyTotalUseCase.kt` — 変更なし（ViewModel で直接計算するため未使用）
- `SubKiruApplication.kt` — `getSubscriptionsUseCase` プロパティは既に存在する。変更なし
- `MainActivity.kt` — 変更なし
- `SubscriptionCard.kt` — 変更なし（`formatAmountJpy` を再利用するのみ）

---

## 検証
- `./gradlew.bat assembleDebug` が成功すること
- `./gradlew.bat test` で AnalyticsViewModelTest の全7テストが成功すること
- BottomNavBar の分析タブをタップして分析画面に遷移すること
- サブスクがない状態で「分析するサブスクがありません」が表示されること
- サブスクがある状態で月額合計・年額換算・サブスク数・内訳が表示されること
- 内訳のパーセンテージバーが月額の割合に応じて表示されること
