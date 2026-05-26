# Navigation セットアップ レビュー指摘・対応（Codex向け）

## 対象ファイル
- `app/src/main/java/com/subkiru/subkiru/navigation/SubKiruNavGraph.kt`

## 指摘内容

### [W-1] ルート定義と NavGraph 登録の不一致（Warning）

**問題**: `Screen` sealed class に `Analytics` / `Calendar` / `Settings` の3ルートが定義されているが、`SubKiruNavGraph` の `NavHost` には `Home` と `AddSubscription` の2つしか `composable()` 登録されていない。他コードから `navController.navigate(Screen.Analytics.route)` 等を呼んだ場合、ルートが未登録のため `IllegalArgumentException` でクラッシュする。

**対応**: 未実装3ルートもプレースホルダーで `composable()` 登録する。

---

## 修正内容

### SubKiruNavGraph.kt の修正

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
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

@Composable
fun SubKiruNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier,
    ) {
        composable(Screen.Home.route) {
            // Home 画面（次タスクで実装）
            ScreenPlaceholder(label = "Home")
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

### 変更点まとめ

| 変更 | 内容 |
|------|------|
| `composable()` 追加 | `Screen.Analytics.route` / `Screen.Calendar.route` / `Screen.Settings.route` の3ルートを登録 |
| プレースホルダー統合 | `HomePlaceholder` / `AddSubscriptionPlaceholder` を `ScreenPlaceholder(label: String)` に統合。重複コードを排除 |

**プレースホルダー統合による差し替えへの影響**: 統合しても各 `composable` ブロック内の `ScreenPlaceholder(label = "...")` を実画面の Composable（例: `HomeScreen(navController)`）に1行差し替えるだけでよい。`ScreenPlaceholder` 自体は全画面の差し替え完了後に削除する。

### 変更しないもの
- `Screen.kt` — 変更なし
- `MainActivity.kt` — 変更なし

### 既存ドキュメントとの関係
- `navigation-setup-task.md` の前提セクション（8行目）「Analytics / Calendar / Settings はルート定義のみ。NavGraph への composable 登録は行わない」は本修正により無効化される。必要に応じて更新すること。
- `navigation-setup-task.md` の TODO（53行目）「AGENTS.md のファイル構成に `navigation/Screen.kt` を追記する必要あり」は未対応のまま残っている。本修正のスコープ外。

---

## 検証
- `./gradlew.bat assembleDebug` が成功すること
- アプリ起動時に画面中央に「Home」テキストが表示されること（既存動作と同じ）
