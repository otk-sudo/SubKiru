# Home 画面 実装タスク（Codex向け）

## 前提
- パッケージ: `com.subkiru.subkiru`
- MVVM + Clean Architecture（AGENTS.md 準拠）
- ViewModel は UseCase のみを呼ぶ。DAO を直接触らない
- Composable は ViewModel の uiState を購読するだけ。ロジックを持たない
- 画面の全状態を1つの `UiState` data class に集約する（UDF: 単方向データフロー）
- 手動 DI: `SubKiruApplication` から UseCase を取得し `ViewModelProvider.Factory` で ViewModel を生成
- `@Preview` を全 Screen に必ずつける
- `LazyColumn` の `items` には常に `key` を指定する
- MVP スコープ: 通貨は JPY のみ（金額表示は `amountMinor` をそのまま「¥{amount}」で表示）
- TDD: テストを先に書いてから実装する

## 既存コードの参照

### 利用する UseCase（`SubKiruApplication` に定義済み）
- `GetSubscriptionsUseCase` — `Flow<List<Subscription>>` を返す
- `CalculateMonthlyTotalUseCase` — `Flow<Long>` を返す（月額合計、amountMinor 単位）
- `DeleteSubscriptionUseCase` — `suspend fun invoke(id: Long)` 論理削除

### 利用するドメインモデル
- `Subscription` — id, name, amountMinor, currencyCode, billingInterval, nextBillingDate 等
- `BillingInterval` — unit: BillingIntervalUnit, count: Int
- `BillingIntervalUnit` — DAILY, WEEKLY, MONTHLY, YEARLY

### 変更対象
- `app/build.gradle.kts` — 依存追加
- `gradle/libs.versions.toml` — ライブラリ定義追加
- `app/src/main/java/com/subkiru/subkiru/MainActivity.kt` — BottomNavBar 統合
- `app/src/main/java/com/subkiru/subkiru/navigation/SubKiruNavGraph.kt` — HomeScreen 接続

### 新規作成
- `app/src/main/java/com/subkiru/subkiru/feature/home/HomeViewModel.kt` — ViewModel + UiState
- `app/src/main/java/com/subkiru/subkiru/feature/home/HomeScreen.kt` — 画面 Composable
- `app/src/main/java/com/subkiru/subkiru/core/ui/component/SubscriptionCard.kt` — サブスクカード
- `app/src/main/java/com/subkiru/subkiru/navigation/BottomNavBar.kt` — ボトムナビゲーション
- `app/src/test/java/com/subkiru/subkiru/feature/home/HomeViewModelTest.kt` — ViewModel テスト

---

## タスク1: 依存追加

### libs.versions.toml に追加

`[versions]` セクションを変更:
```toml
# 既存の lifecycleRuntimeKtx = "2.6.1" を削除し、以下に統一する
lifecycle = "2.9.1"
```

`[libraries]` セクションを変更:
```toml
# 既存の androidx-lifecycle-runtime-ktx の version.ref を "lifecycle" に変更する
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }

# 以下を追加
lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended" }
```

**注意点**:
- `material-icons-extended` はバージョン指定なし（Compose BOM が管理する）
- **重要**: 既存の `lifecycleRuntimeKtx = "2.6.1"` を削除し `lifecycle = "2.9.1"` に統一すること。同一アーティファクトグループ（`androidx.lifecycle`）でバージョンが混在するとビルドエラーの原因になる
- 既存の `androidx-lifecycle-runtime-ktx` の `version.ref` も `"lifecycle"` に変更すること

### build.gradle.kts の dependencies に追加

```kotlin
implementation(libs.lifecycle.viewmodel.compose)
implementation(libs.lifecycle.runtime.compose)
implementation(libs.material.icons.extended)
```

---

## タスク2: HomeViewModel + HomeUiState

**ファイル**: `app/src/main/java/com/subkiru/subkiru/feature/home/HomeViewModel.kt`

```kotlin
package com.subkiru.subkiru.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.subkiru.subkiru.SubKiruApplication
import com.subkiru.subkiru.core.domain.model.Subscription
import com.subkiru.subkiru.core.domain.usecase.CalculateMonthlyTotalUseCase
import com.subkiru.subkiru.core.domain.usecase.DeleteSubscriptionUseCase
import com.subkiru.subkiru.core.domain.usecase.GetSubscriptionsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
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
    calculateMonthlyTotalUseCase: CalculateMonthlyTotalUseCase,
    private val deleteSubscriptionUseCase: DeleteSubscriptionUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            getSubscriptionsUseCase()
                .catch { e ->
                    _uiState.update { it.copy(error = e.message, isLoading = false) }
                }
                .collect { subscriptions ->
                    _uiState.update {
                        it.copy(subscriptions = subscriptions, isLoading = false)
                    }
                }
        }

        viewModelScope.launch {
            calculateMonthlyTotalUseCase()
                .catch { /* 月額合計の取得失敗は致命的ではないため現時点では無視。Timber 導入後にログ出力を追加する */ }
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
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    companion object {
        fun factory(app: SubKiruApplication): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    getSubscriptionsUseCase = app.getSubscriptionsUseCase,
                    calculateMonthlyTotalUseCase = app.calculateMonthlyTotalUseCase,
                    deleteSubscriptionUseCase = app.deleteSubscriptionUseCase,
                )
            }
        }
    }
}
```

**注意点**:
- `MutableStateFlow` は `private`（AGENTS.md UDF ルール）
- UseCase からの `Flow` は `init` ブロックで `viewModelScope.launch` して購読開始
- `catch` で Flow のエラーを捕捉（UiState の `error` に設定）
- `onDeleteSubscription` は AGENTS.md の命名規則 `on{動詞}{名詞}` に準拠
- `GetUpcomingBillingsUseCase` は MVP スコープ外（将来のホーム画面拡張で追加）

---

## タスク3: SubscriptionCard コンポーネント

**ファイル**: `app/src/main/java/com/subkiru/subkiru/core/ui/component/SubscriptionCard.kt`

```kotlin
package com.subkiru.subkiru.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.subkiru.subkiru.core.domain.model.BillingInterval
import com.subkiru.subkiru.core.domain.model.BillingIntervalUnit
import com.subkiru.subkiru.core.domain.model.Subscription
import com.subkiru.subkiru.ui.theme.SubKiruTheme
import com.subkiru.subkiru.ui.theme.TextSecondary
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun SubscriptionCard(
    subscription: Subscription,
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = subscription.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = formatBillingInterval(subscription.billingInterval),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Text(
                    text = "次回: ${subscription.nextBillingDate.format(DATE_FORMATTER)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "¥${subscription.amountMinor}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

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

private val CARD_PADDING = 16.dp
private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")

@Preview(showBackground = true)
@Composable
private fun SubscriptionCardPreview() {
    SubKiruTheme {
        SubscriptionCard(
            subscription = Subscription(
                id = 1L,
                name = "Netflix",
                amountMinor = 1490L,
                currencyCode = "JPY",
                billingInterval = BillingInterval(
                    unit = BillingIntervalUnit.MONTHLY,
                    count = 1,
                ),
                startDate = LocalDate.of(2025, 1, 1),
                nextBillingDate = LocalDate.of(2026, 6, 1),
                categoryId = null,
                templateId = null,
                logoUri = null,
                memo = null,
                isActive = true,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            ),
            modifier = Modifier.padding(16.dp),
        )
    }
}
```

**注意点**:
- `surfaceContainerLowest` は AGENTS.md のカード背景（`#FFFFFF`）にマッピング済み
- `TextSecondary` はカスタムカラー（`#6AADA0`）。Material 3 の `onSurfaceVariant` ではなくカスタム参照
- 日付フォーマットは `yyyy/MM/dd` 形式（日本語圏向け）
- `formatBillingInterval` は `private` 関数としてファイル内に定義
- `@Preview` を含む（AGENTS.md Compose ルール2）

---

## タスク4: HomeScreen

**ファイル**: `app/src/main/java/com/subkiru/subkiru/feature/home/HomeScreen.kt`

```kotlin
package com.subkiru.subkiru.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.subkiru.subkiru.core.domain.model.BillingInterval
import com.subkiru.subkiru.core.domain.model.BillingIntervalUnit
import com.subkiru.subkiru.core.domain.model.Subscription
import com.subkiru.subkiru.core.ui.component.SubscriptionCard
import com.subkiru.subkiru.ui.theme.SubKiruTheme
import com.subkiru.subkiru.ui.theme.TextSecondary
import java.time.Instant
import java.time.LocalDate

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

@Composable
private fun HomeContent(
    uiState: HomeUiState,
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
        uiState.subscriptions.isEmpty() -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "サブスクがまだありません",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "右下の＋ボタンから追加しましょう",
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
                item(key = "monthly_total") {
                    MonthlyTotalHeader(monthlyTotal = uiState.monthlyTotal)
                }
                items(
                    items = uiState.subscriptions,
                    key = { it.id },
                ) { subscription ->
                    SubscriptionCard(subscription = subscription)
                }
            }
        }
    }
}

@Composable
private fun MonthlyTotalHeader(
    monthlyTotal: Long,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = HEADER_VERTICAL_PADDING),
    ) {
        Text(
            text = "月額合計",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "¥${monthlyTotal}",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private val SCREEN_HORIZONTAL_PADDING = 16.dp
private val SCREEN_TOP_PADDING = 8.dp
private val SCREEN_BOTTOM_PADDING = 88.dp // FAB + BottomNav の高さ分の余白
private val LIST_ITEM_SPACING = 12.dp
private val HEADER_VERTICAL_PADDING = 16.dp

@Preview(showBackground = true)
@Composable
private fun HomeContentPreview() {
    SubKiruTheme {
        HomeContent(
            uiState = HomeUiState(
                subscriptions = listOf(
                    Subscription(
                        id = 1L,
                        name = "Netflix",
                        amountMinor = 1490L,
                        currencyCode = "JPY",
                        billingInterval = BillingInterval(BillingIntervalUnit.MONTHLY, 1),
                        startDate = LocalDate.of(2025, 1, 1),
                        nextBillingDate = LocalDate.of(2026, 6, 1),
                        categoryId = null,
                        templateId = null,
                        logoUri = null,
                        memo = null,
                        isActive = true,
                        createdAt = Instant.now(),
                        updatedAt = Instant.now(),
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
                        createdAt = Instant.now(),
                        updatedAt = Instant.now(),
                    ),
                ),
                monthlyTotal = 2470L,
                isLoading = false,
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeContentEmptyPreview() {
    SubKiruTheme {
        HomeContent(
            uiState = HomeUiState(
                subscriptions = emptyList(),
                isLoading = false,
            ),
        )
    }
}
```

**注意点**:
- `HomeScreen` は ViewModel を受け取り `collectAsStateWithLifecycle` で UiState を購読する
- `HomeContent` は `HomeUiState` を直接受け取る（Preview で使いやすくするため分離）
- `LazyColumn` の `items` に `key = { it.id }` を指定（AGENTS.md Compose ルール4）
- ローディング / エラー / 空状態 / リスト表示の4状態を `when` で分岐
- `SCREEN_BOTTOM_PADDING = 88.dp` は FAB + BottomNav の高さ分。リストの最後のアイテムが隠れないようにする
- `@Preview` を2つ（データあり / 空状態）含む

---

## タスク5: BottomNavBar

**ファイル**: `app/src/main/java/com/subkiru/subkiru/navigation/BottomNavBar.kt`

```kotlin
package com.subkiru.subkiru.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.subkiru.subkiru.ui.theme.SubKiruTheme

enum class BottomNavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector,
) {
    HOME(Screen.Home, "ホーム", Icons.Default.Home),
    ANALYTICS(Screen.Analytics, "分析", Icons.Default.ShowChart),
    CALENDAR(Screen.Calendar, "カレンダー", Icons.Default.DateRange),
    SETTINGS(Screen.Settings, "設定", Icons.Default.Settings),
}

@Composable
fun BottomNavBar(
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar(modifier = modifier) {
        BottomNavItem.entries.forEach { item ->
            NavigationBarItem(
                icon = { Icon(imageVector = item.icon, contentDescription = item.label) },
                label = { Text(text = item.label) },
                selected = currentRoute == item.screen.route,
                onClick = {
                    if (currentRoute != item.screen.route) {
                        navController.navigate(item.screen.route) {
                            // バックスタックが積み上がらないようにする
                            popUpTo(Screen.Home.route) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BottomNavBarPreview() {
    SubKiruTheme {
        BottomNavBar(navController = rememberNavController())
    }
}
```

**注意点**:
- `BottomNavItem` は `enum class` で定義（ボトムナビのアイテムは固定で、パラメータ付き拡張不要）
- `popUpTo(Screen.Home.route)` でバックスタックの積み上がりを防止
- `saveState = true` / `restoreState = true` でタブ切り替え時の状態を保持
- `launchSingleTop = true` で同じ画面の多重生成を防止
- `currentRoute` と比較して選択状態を判定
- `Icons.Default.ShowChart` は `material-icons-extended` に含まれる。タスク1で依存追加済み

> **TODO**: AGENTS.md のファイル構成に `navigation/BottomNavBar.kt` を追記する必要あり（本タスクのスコープ外）。

---

## タスク6: MainActivity の改修

**ファイル**: `app/src/main/java/com/subkiru/subkiru/MainActivity.kt`

以下は **修正後のファイル全体**:

```kotlin
package com.subkiru.subkiru

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.subkiru.subkiru.navigation.BottomNavBar
import com.subkiru.subkiru.navigation.Screen
import com.subkiru.subkiru.navigation.SubKiruNavGraph
import com.subkiru.subkiru.ui.theme.SubKiruTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SubKiruTheme {
                val navController = rememberNavController()
                val currentRoute = navController
                    .currentBackStackEntryAsState().value?.destination?.route

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        BottomNavBar(navController = navController)
                    },
                    floatingActionButton = {
                        // FAB は Home 画面でのみ表示
                        if (currentRoute == Screen.Home.route) {
                            FloatingActionButton(
                                onClick = {
                                    navController.navigate(Screen.AddSubscription.route)
                                },
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "サブスクを追加",
                                )
                            }
                        }
                    },
                ) { innerPadding ->
                    SubKiruNavGraph(
                        navController = navController,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}
```

**変更点**:
- `BottomNavBar` を `Scaffold` の `bottomBar` に配置
- `FloatingActionButton` を追加（Home 画面でのみ表示）
- FAB タップで `Screen.AddSubscription` に遷移
- `currentBackStackEntryAsState()` で現在のルートを監視し、FAB の表示/非表示を制御
- import 追加: `Icons`, `Add`, `FloatingActionButton`, `Icon`, `MaterialTheme`, `BottomNavBar`, `Screen`, `currentBackStackEntryAsState`

---

## タスク7: SubKiruNavGraph の改修

**ファイル**: `app/src/main/java/com/subkiru/subkiru/navigation/SubKiruNavGraph.kt`

以下は **修正後のファイル全体**:

```kotlin
package com.subkiru.subkiru.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.subkiru.subkiru.SubKiruApplication
import com.subkiru.subkiru.feature.home.HomeScreen
import com.subkiru.subkiru.feature.home.HomeViewModel

@Composable
fun SubKiruNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as SubKiruApplication

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier,
    ) {
        composable(Screen.Home.route) {
            val viewModel: HomeViewModel = viewModel(
                factory = HomeViewModel.factory(app),
            )
            HomeScreen(viewModel = viewModel)
        }
        composable(Screen.AddSubscription.route) {
            // サブスク追加画面（後続タスクで実装）
            ScreenPlaceholder(label = "Add Subscription")
        }
        composable(Screen.Analytics.route) {
            // 分析画面（後続タスクで実装）
            ScreenPlaceholder(label = "Analytics")
        }
        composable(Screen.Calendar.route) {
            // カレンダー画面（後続タスクで実装）
            ScreenPlaceholder(label = "Calendar")
        }
        composable(Screen.Settings.route) {
            // 設定画面（後続タスクで実装）
            ScreenPlaceholder(label = "Settings")
        }
    }
}

// 共通プレースホルダー Composable（後続タスクで実際の Screen に差し替える）
@Composable
private fun ScreenPlaceholder(label: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label)
    }
}
```

**変更点**:
- `LocalContext.current.applicationContext as SubKiruApplication` で手動 DI のアプリインスタンスを取得
- `viewModel(factory = HomeViewModel.factory(app))` で ViewModel を生成
- Home の `ScreenPlaceholder` を `HomeScreen(viewModel)` に差し替え
- import 追加: `LocalContext`, `viewModel`, `SubKiruApplication`, `HomeScreen`, `HomeViewModel`

---

## タスク8: HomeViewModel テスト

**ファイル**: `app/src/test/java/com/subkiru/subkiru/feature/home/HomeViewModelTest.kt`

```kotlin
package com.subkiru.subkiru.feature.home

import app.cash.turbine.test
import com.subkiru.subkiru.core.domain.model.BillingInterval
import com.subkiru.subkiru.core.domain.model.BillingIntervalUnit
import com.subkiru.subkiru.core.domain.model.Subscription
import com.subkiru.subkiru.core.domain.usecase.CalculateMonthlyTotalUseCase
import com.subkiru.subkiru.core.domain.usecase.DeleteSubscriptionUseCase
import com.subkiru.subkiru.core.domain.usecase.GetSubscriptionsUseCase
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
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

    private val subscriptionsFlow = MutableSharedFlow<List<Subscription>>()
    private val monthlyTotalFlow = MutableSharedFlow<Long>()

    private val getSubscriptionsUseCase: GetSubscriptionsUseCase = mockk {
        every { this@mockk.invoke() } returns subscriptionsFlow
    }
    private val calculateMonthlyTotalUseCase: CalculateMonthlyTotalUseCase = mockk {
        every { this@mockk.invoke() } returns monthlyTotalFlow
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
            calculateMonthlyTotalUseCase = calculateMonthlyTotalUseCase,
            deleteSubscriptionUseCase = deleteSubscriptionUseCase,
        )
    }

    @Test
    fun `初期状態はローディング中である`() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.isLoading)
            assertEquals(emptyList<Subscription>(), state.subscriptions)
            assertEquals(0L, state.monthlyTotal)
            assertNull(state.error)
        }
    }

    @Test
    fun `サブスク一覧を取得するとローディングが解除される`() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            // Arrange: 初期状態（ローディング中）
            awaitItem()

            // Act: サブスクリストを発行
            subscriptionsFlow.emit(SAMPLE_SUBSCRIPTIONS)
            advanceUntilIdle()

            // Assert
            val state = awaitItem()
            assertFalse(state.isLoading)
            assertEquals(SAMPLE_SUBSCRIPTIONS.size, state.subscriptions.size)
            assertEquals(SAMPLE_SUBSCRIPTIONS[0].name, state.subscriptions[0].name)
        }
    }

    @Test
    fun `月額合計が更新される`() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            // Arrange: 初期状態
            awaitItem()

            // Act: 月額合計を発行
            monthlyTotalFlow.emit(EXPECTED_MONTHLY_TOTAL)
            advanceUntilIdle()

            // Assert
            val state = awaitItem()
            assertEquals(EXPECTED_MONTHLY_TOTAL, state.monthlyTotal)
        }
    }

    @Test
    fun `空のサブスク一覧が正しく反映される`() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            // Arrange: 初期状態
            awaitItem()

            // Act: 空リストを発行
            subscriptionsFlow.emit(emptyList())
            advanceUntilIdle()

            // Assert
            val state = awaitItem()
            assertFalse(state.isLoading)
            assertTrue(state.subscriptions.isEmpty())
        }
    }

    @Test
    fun `サブスク取得でエラーが発生するとエラー状態になる`() = runTest {
        // Arrange: エラーを発行する UseCase を構成
        val errorFlow = kotlinx.coroutines.flow.flow<List<Subscription>> {
            throw RuntimeException(ERROR_MESSAGE)
        }
        val errorGetSubscriptionsUseCase: GetSubscriptionsUseCase = mockk {
            every { this@mockk.invoke() } returns errorFlow
        }
        val viewModel = HomeViewModel(
            getSubscriptionsUseCase = errorGetSubscriptionsUseCase,
            calculateMonthlyTotalUseCase = calculateMonthlyTotalUseCase,
            deleteSubscriptionUseCase = deleteSubscriptionUseCase,
        )

        viewModel.uiState.test {
            // Arrange: 初期状態
            awaitItem()
            advanceUntilIdle()

            // Assert
            val state = awaitItem()
            assertFalse(state.isLoading)
            assertEquals(ERROR_MESSAGE, state.error)
        }
    }

    @Test
    fun `サブスク削除がUseCaseに委譲される`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Act
        viewModel.onDeleteSubscription(TARGET_SUBSCRIPTION_ID)
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { deleteSubscriptionUseCase.invoke(TARGET_SUBSCRIPTION_ID) }
    }

    companion object {
        private const val TARGET_SUBSCRIPTION_ID = 1L
        private const val EXPECTED_MONTHLY_TOTAL = 2470L
        private const val ERROR_MESSAGE = "DB error"

        private val SAMPLE_SUBSCRIPTIONS = listOf(
            Subscription(
                id = 1L,
                name = "Netflix",
                amountMinor = 1490L,
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

**注意点**:
- `MutableSharedFlow` を使って UseCase からの Flow 発行を制御する（`MutableStateFlow` ではなく `MutableSharedFlow` を使うことで、初期値なしの状態からテストできる）
- `Dispatchers.setMain(testDispatcher)` で `viewModelScope` のディスパッチャーをテスト用に差し替え
- `advanceUntilIdle()` で保留中のコルーチンを全て実行
- `relaxUnitFun = true` で `DeleteSubscriptionUseCase` の `suspend fun` をモック
- テストデータの `createdAt` / `updatedAt` は `Instant.EPOCH` を使用（テストで意味を持たないため）
- マジックナンバーは `companion object` に定数として定義
- AAAパターン（Arrange / Act / Assert）を全テストで使用

---

## 検証
- `./gradlew.bat assembleDebug` が成功すること
- `./gradlew.bat test` で HomeViewModelTest の全6テストが成功すること
- アプリ起動時にボトムナビゲーションバーが表示されること
- Home タブ選択時に月額合計ヘッダーが表示されること（データなしの場合は空状態メッセージ）
- 右下に FAB（+ボタン）が表示されること
- FAB タップで「Add Subscription」プレースホルダーに遷移すること
- ボトムナビの各タブをタップしてプレースホルダー画面に遷移できること
