# Home 画面 レビュー指摘・対応（Codex向け）

## 対象ファイル
- `app/src/main/java/com/subkiru/subkiru/feature/home/HomeViewModel.kt`
- `app/src/main/java/com/subkiru/subkiru/feature/home/HomeScreen.kt`
- `app/src/main/java/com/subkiru/subkiru/core/ui/component/SubscriptionCard.kt`
- `app/src/main/java/com/subkiru/subkiru/navigation/SubKiruNavGraph.kt`
- `app/src/main/java/com/subkiru/subkiru/SubKiruApplication.kt`
- `app/src/test/java/com/subkiru/subkiru/feature/home/HomeViewModelTest.kt`

---

## 指摘一覧

| ID | 重要度 | 指摘 |
|---|---|---|
| C-1 | Critical | エラーメッセージの生表示（内部情報漏洩リスク） |
| C-2 | Critical | 同一 DAO Flow を2回 collect（DB クエリ重複） |
| W-2 | Warning | 金額にカンマ区切りなし |
| W-4 | Warning | 削除エラー時にリスト全体がエラー画面に置換される |
| W-5 | Warning | `as SubKiruApplication` キャスト散在リスク |
| W-8 | Warning | `calculateMonthlyTotalUseCase` の catch が空 |
| W-9 | Warning | 削除エラー時のテストがない |
| W-10 | Warning | `monthlyTotal` が 0L のまま一瞬 `¥0` 表示（C-2 で解消） |
| S-1 | Suggestion | `formatBillingInterval` の日本語改善 |

---

## 修正1: SubKiruApplication にヘルパー追加（W-5）

**ファイル**: `app/src/main/java/com/subkiru/subkiru/SubKiruApplication.kt`

`companion object` を追加する。既存コードはそのまま維持。

```kotlin
class SubKiruApplication : Application() {

    // --- 既存コードは変更なし ---

    companion object {
        fun from(context: Context): SubKiruApplication =
            context.applicationContext as SubKiruApplication
    }
}
```

**追加する import**:
```kotlin
import android.content.Context
```

---

## 修正2: SubKiruNavGraph のキャスト箇所を置換（W-5）

**ファイル**: `app/src/main/java/com/subkiru/subkiru/navigation/SubKiruNavGraph.kt`

**変更前**:
```kotlin
val app = LocalContext.current.applicationContext as SubKiruApplication
```

**変更後**:
```kotlin
val app = SubKiruApplication.from(LocalContext.current)
```

---

## 修正3: HomeViewModel の全面改修（C-1, C-2, W-4, W-8, W-10）

**ファイル**: `app/src/main/java/com/subkiru/subkiru/feature/home/HomeViewModel.kt`

以下は **修正後のファイル全体**:

```kotlin
package com.subkiru.subkiru.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.subkiru.subkiru.SubKiruApplication
import com.subkiru.subkiru.core.domain.model.BillingIntervalUnit
import com.subkiru.subkiru.core.domain.model.Subscription
import com.subkiru.subkiru.core.domain.usecase.DeleteSubscriptionUseCase
import com.subkiru.subkiru.core.domain.usecase.GetSubscriptionsUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

data class HomeUiState(
    val subscriptions: List<Subscription> = emptyList(),
    val monthlyTotal: Long = 0L,
    val isLoading: Boolean = true,
    val error: String? = null,
)

class HomeViewModel(
    getSubscriptionsUseCase: GetSubscriptionsUseCase,
    private val deleteSubscriptionUseCase: DeleteSubscriptionUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // 削除結果等の一時的なエラーを通知するイベント（Snackbar 用）
    private val _errorEvent = MutableSharedFlow<String>()
    val errorEvent = _errorEvent.asSharedFlow()

    init {
        // 同一 DAO Flow を shareIn で共有し、DB クエリの重複実行を防止する
        val sharedSubscriptions = getSubscriptionsUseCase()
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(SHARE_TIMEOUT_MS), replay = 1)

        viewModelScope.launch {
            sharedSubscriptions
                .catch {
                    _uiState.update {
                        it.copy(error = ERROR_MESSAGE_LOAD, isLoading = false)
                    }
                }
                .collect { subscriptions ->
                    _uiState.update {
                        it.copy(subscriptions = subscriptions, isLoading = false)
                    }
                }
        }

        viewModelScope.launch {
            sharedSubscriptions
                .map { subscriptions -> subscriptions.sumOf { toMonthlyAmount(it) } }
                .catch { /* 月額合計の計算失敗時は 0L のまま（致命的ではない） */ }
                .collect { total ->
                    _uiState.update { it.copy(monthlyTotal = total) }
                }
        }
    }

    fun onDeleteSubscription(id: Long) {
        viewModelScope.launch {
            try {
                deleteSubscriptionUseCase(id)
            } catch (e: CancellationException) {
                throw e // キャンセルは必ず再 throw（AGENTS.md Repository ルール3）
            } catch (_: Exception) {
                // 削除エラーはリスト表示を維持し、一時イベントで通知する
                _errorEvent.emit(ERROR_MESSAGE_DELETE)
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
        private const val SHARE_TIMEOUT_MS = 5000L
        private const val DAYS_PER_MONTH = 30L
        private const val WEEKS_PER_MONTH = 4L
        private const val MONTHS_PER_YEAR = 12L

        const val ERROR_MESSAGE_LOAD = "データの読み込みに失敗しました"
        const val ERROR_MESSAGE_DELETE = "削除に失敗しました"

        fun factory(app: SubKiruApplication): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    getSubscriptionsUseCase = app.getSubscriptionsUseCase,
                    deleteSubscriptionUseCase = app.deleteSubscriptionUseCase,
                )
            }
        }
    }
}
```

### 変更点まとめ

| 変更 | 対応ID | 内容 |
|------|--------|------|
| `shareIn` 導入 | C-2, W-10 | `getSubscriptionsUseCase()` を `shareIn` で共有。月額合計は `map` で導出。DB クエリ重複を排除し、monthlyTotal のフリッカーも解消 |
| `CalculateMonthlyTotalUseCase` 削除 | C-2 | UseCase を使わず ViewModel 内の `toMonthlyAmount` で計算（`CalculateMonthlyTotalUseCase` の内部ロジックと同一）。コンストラクタ引数から除外 |
| 固定エラーメッセージ | C-1 | `e.message` → `ERROR_MESSAGE_LOAD` / `ERROR_MESSAGE_DELETE`。内部情報を表示しない |
| `errorEvent: SharedFlow` | W-4 | 削除エラーは `_uiState.error` ではなく `_errorEvent` で一時通知。リスト表示を維持する |
| catch フォールバック | W-8 | 月額合計の catch は `0L` のまま維持（sharedSubscriptions の map エラーなので subscribe 側のリスト表示には影響しない） |

### `CalculateMonthlyTotalUseCase` の扱いについて

`shareIn` 導入により `getSubscriptionsUseCase` の Flow を共有するため、`CalculateMonthlyTotalUseCase` を経由すると別の Flow collect が発生してしまう。月額合計の計算ロジックは ViewModel 内に `toMonthlyAmount` として移動した。

**注意**: `CalculateMonthlyTotalUseCase` 自体は削除しない（他画面で使用する可能性があるため）。`SubKiruApplication` からの参照も残す。HomeViewModel のコンストラクタ引数から除外するのみ。

---

## 修正4: HomeScreen に Snackbar 対応追加（W-4）

**ファイル**: `app/src/main/java/com/subkiru/subkiru/feature/home/HomeScreen.kt`

`HomeScreen` 関数の `errorEvent` 購読を追加する。

**変更前**:
```kotlin
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(
        uiState = uiState,
        modifier = modifier,
    )
}
```

**変更後**:
```kotlin
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.errorEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier,
    ) { innerPadding ->
        HomeContent(
            uiState = uiState,
            modifier = Modifier.padding(innerPadding),
        )
    }
}
```

**追加する import**:
```kotlin
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
```

---

## 修正5: 金額フォーマットにカンマ区切り追加（W-2）

**ファイル**: `app/src/main/java/com/subkiru/subkiru/core/ui/component/SubscriptionCard.kt`

ファイル末尾に関数を追加し、金額表示箇所を差し替える。

**追加する関数**:
```kotlin
import java.text.NumberFormat
import java.util.Locale

private val AMOUNT_FORMATTER: NumberFormat = NumberFormat.getNumberInstance(Locale.JAPAN)

internal fun formatAmountJpy(amountMinor: Long): String {
    return "¥${AMOUNT_FORMATTER.format(amountMinor)}"
}
```

**SubscriptionCard 内の変更**:
```kotlin
// 変更前
text = "¥${subscription.amountMinor}",

// 変更後
text = formatAmountJpy(subscription.amountMinor),
```

**ファイル**: `app/src/main/java/com/subkiru/subkiru/feature/home/HomeScreen.kt`

MonthlyTotalHeader 内の変更:
```kotlin
// 変更前
text = "¥${monthlyTotal}",

// 変更後
text = formatAmountJpy(monthlyTotal),
```

**追加する import（HomeScreen.kt）**:
```kotlin
import com.subkiru.subkiru.core.ui.component.formatAmountJpy
```

**注意点**:
- `formatAmountJpy` は `internal` で SubscriptionCard.kt に定義する（HomeScreen からも参照可能）
- MVP は JPY のみ。多通貨対応時に `currencyCode` を受け取る関数に拡張する

---

## 修正6: `formatBillingInterval` の日本語改善（S-1）

**ファイル**: `app/src/main/java/com/subkiru/subkiru/core/ui/component/SubscriptionCard.kt`

**変更前**:
```kotlin
private fun formatBillingInterval(interval: BillingInterval): String {
    val unitLabel = when (interval.unit) {
        BillingIntervalUnit.DAILY -> "日"
        BillingIntervalUnit.WEEKLY -> "週"
        BillingIntervalUnit.MONTHLY -> "月"
        BillingIntervalUnit.YEARLY -> "年"
    }
    return if (interval.count == 1) {
        "${unitLabel}額"
    } else {
        "${interval.count}${unitLabel}ごと"
    }
}
```

**変更後**:
```kotlin
private fun formatBillingInterval(interval: BillingInterval): String {
    if (interval.count == 1) {
        return when (interval.unit) {
            BillingIntervalUnit.DAILY -> "日額"
            BillingIntervalUnit.WEEKLY -> "週額"
            BillingIntervalUnit.MONTHLY -> "月額"
            BillingIntervalUnit.YEARLY -> "年額"
        }
    }
    val unitLabel = when (interval.unit) {
        BillingIntervalUnit.DAILY -> "日"
        BillingIntervalUnit.WEEKLY -> "週間"
        BillingIntervalUnit.MONTHLY -> "ヶ月"
        BillingIntervalUnit.YEARLY -> "年"
    }
    return "${interval.count}${unitLabel}ごと"
}
```

**表示例**:
- `count=1, MONTHLY` → `"月額"`
- `count=3, MONTHLY` → `"3ヶ月ごと"`
- `count=2, WEEKLY` → `"2週間ごと"`

---

## 修正7: HomeViewModelTest の修正（C-2 対応 + W-9 追加）

**ファイル**: `app/src/test/java/com/subkiru/subkiru/feature/home/HomeViewModelTest.kt`

以下は **修正後のファイル全体**:

```kotlin
package com.subkiru.subkiru.feature.home

import app.cash.turbine.test
import com.subkiru.subkiru.core.domain.model.BillingInterval
import com.subkiru.subkiru.core.domain.model.BillingIntervalUnit
import com.subkiru.subkiru.core.domain.model.Subscription
import com.subkiru.subkiru.core.domain.usecase.DeleteSubscriptionUseCase
import com.subkiru.subkiru.core.domain.usecase.GetSubscriptionsUseCase
import io.mockk.coEvery
import io.mockk.coVerify
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
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private val subscriptionsFlow = MutableSharedFlow<List<Subscription>>(replay = 1)

    private val getSubscriptionsUseCase: GetSubscriptionsUseCase = mockk {
        every { this@mockk.invoke() } returns subscriptionsFlow
    }
    private val deleteSubscriptionUseCase: DeleteSubscriptionUseCase = mockk(relaxUnitFun = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): HomeViewModel {
        return HomeViewModel(
            getSubscriptionsUseCase = getSubscriptionsUseCase,
            deleteSubscriptionUseCase = deleteSubscriptionUseCase,
        )
    }

    @Test
    fun 初期状態はローディング中である() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.isLoading)
            assertEquals(emptyList<Subscription>(), state.subscriptions)
            assertEquals(0L, state.monthlyTotal)
            assertNull(state.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun サブスク一覧を取得するとローディングが解除される() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()

            subscriptionsFlow.emit(SAMPLE_SUBSCRIPTIONS)
            advanceUntilIdle()

            val state = awaitItem()
            assertFalse(state.isLoading)
            assertEquals(SAMPLE_SUBSCRIPTIONS.size, state.subscriptions.size)
            assertEquals(SAMPLE_SUBSCRIPTIONS[0].name, state.subscriptions[0].name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun 月額合計がサブスク一覧から自動計算される() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()

            subscriptionsFlow.emit(SAMPLE_SUBSCRIPTIONS)
            advanceUntilIdle()

            // subscriptions と monthlyTotal が同時に更新される（shareIn 経由）
            val state = expectMostRecentItem()
            assertEquals(EXPECTED_MONTHLY_TOTAL, state.monthlyTotal)
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
            assertTrue(state.subscriptions.isEmpty())
            assertEquals(0L, state.monthlyTotal)
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
        val viewModel = HomeViewModel(
            getSubscriptionsUseCase = errorGetSubscriptionsUseCase,
            deleteSubscriptionUseCase = deleteSubscriptionUseCase,
        )

        viewModel.uiState.test {
            awaitItem()
            advanceUntilIdle()

            val state = awaitItem()
            assertFalse(state.isLoading)
            // 生のエラーメッセージではなく固定メッセージが設定される
            assertEquals(HomeViewModel.ERROR_MESSAGE_LOAD, state.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun サブスク削除がUseCaseに委譲される() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onDeleteSubscription(TARGET_SUBSCRIPTION_ID)
        advanceUntilIdle()

        coVerify(exactly = 1) { deleteSubscriptionUseCase.invoke(TARGET_SUBSCRIPTION_ID) }
    }

    @Test
    fun サブスク削除でエラーが発生するとエラーイベントが発行される() = runTest {
        coEvery { deleteSubscriptionUseCase.invoke(any()) } throws RuntimeException("削除失敗")

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.errorEvent.test {
            viewModel.onDeleteSubscription(TARGET_SUBSCRIPTION_ID)
            advanceUntilIdle()

            val message = awaitItem()
            assertEquals(HomeViewModel.ERROR_MESSAGE_DELETE, message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    companion object {
        private const val TARGET_SUBSCRIPTION_ID = 1L
        private const val EXPECTED_MONTHLY_TOTAL = 2_470L

        private val SAMPLE_SUBSCRIPTIONS = listOf(
            Subscription(
                id = 1L,
                name = "Netflix",
                amountMinor = 1_490L,
                currencyCode = "JPY",
                billingInterval = BillingInterval(BillingIntervalUnit.MONTHLY, 1),
                startDate = LocalDate.of(2025, 1, 1),
                nextBillingDate = LocalDate.of(2026, 6, 1),
                categoryId = null,
                templateId = null,
                logoUri = null,
                memo = null,
                isActive = true,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            ),
            Subscription(
                id = 2L,
                name = "Spotify",
                amountMinor = 980L,
                currencyCode = "JPY",
                billingInterval = BillingInterval(BillingIntervalUnit.MONTHLY, 1),
                startDate = LocalDate.of(2025, 3, 15),
                nextBillingDate = LocalDate.of(2026, 6, 15),
                categoryId = null,
                templateId = null,
                logoUri = null,
                memo = null,
                isActive = true,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            ),
        )
    }
}
```

### テスト変更点

| 変更 | 対応ID | 内容 |
|------|--------|------|
| `CalculateMonthlyTotalUseCase` モック削除 | C-2 | コンストラクタから除外されたため |
| `月額合計が更新される` → `月額合計がサブスク一覧から自動計算される` | C-2 | shareIn + map 経由のテストに変更。`expectMostRecentItem()` で最新状態を取得 |
| `空のサブスク一覧` に `monthlyTotal = 0L` アサーション追加 | C-2 | 空リスト時に合計が0であることを検証 |
| エラーテストで `ERROR_MESSAGE_LOAD` を検証 | C-1 | 固定メッセージが設定されることを検証 |
| 削除エラーテスト追加 | W-9 | `coEvery { throws }` で例外を発生させ、`errorEvent` に `ERROR_MESSAGE_DELETE` が発行されることを検証 |

---

## 変更しないもの
- `MainActivity.kt` — 変更なし
- `BottomNavBar.kt` — 変更なし
- `Screen.kt` — 変更なし
- `SubKiruApplication.kt` の既存プロパティ（`calculateMonthlyTotalUseCase` 含む）— 削除しない
- `CalculateMonthlyTotalUseCase.kt` — 削除しない（他画面で使用する可能性があるため）

---

## 検証
- `./gradlew.bat assembleDebug` が成功すること
- `./gradlew.bat test` で HomeViewModelTest の全7テストが成功すること
- アプリ起動時に金額が `¥1,490` のようにカンマ区切りで表示されること
- 請求間隔が「月額」「3ヶ月ごと」のように自然な日本語で表示されること
