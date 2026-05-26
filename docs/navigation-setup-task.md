# Navigation セットアップ タスク（Codex向け）

## 前提
- パッケージ: `com.subkiru.subkiru`
- Compose Navigation 2.9.0（依存は追加済み。`libs.versions.toml` + `build.gradle.kts` 確認済み。`kotlin-android` プラグインは `kotlin.compose` から推移的に適用される）
- 型安全ルート（`@Serializable`）は使用しない。kotlinx-serialization 未導入のため **文字列ルート** を採用
- MVP スコープ: **Home** と **AddSubscription** の2ルートのみ実装
- Analytics / Calendar / Settings はルート定義のみ。NavGraph への composable 登録は行わない
- ボトムナビゲーションバーは本タスクのスコープ外（Home 画面タスクで実装）
- テストは不要（AGENTS.md「テストなしでの機能追加を禁止」の例外。本タスクはルーティング定義とプレースホルダーのみでビジネスロジックを含まないため対象外とする）

## 既存コードの参照

### 変更対象
- `app/src/main/java/com/subkiru/subkiru/MainActivity.kt` — NavGraph を統合

### 新規作成
- `app/src/main/java/com/subkiru/subkiru/navigation/SubKiruNavGraph.kt` — ルート定義 + NavHost
- `app/src/main/java/com/subkiru/subkiru/navigation/Screen.kt` — ルート定数

### 影響を受けるファイル
- なし（既存の Greeting / GreetingPreview は削除する）

### @Preview について
AGENTS.md「`@Preview` を全 Screen に必ずつける」ルールの例外とする。本タスクのプレースホルダーは一時的なものであり `@Preview` は不要。Home 画面実装タスクで `HomeScreen` に `@Preview` を付与する。

---

## タスク1: Screen.kt（ルート定数）の作成

**ファイル**: `app/src/main/java/com/subkiru/subkiru/navigation/Screen.kt`

```kotlin
package com.subkiru.subkiru.navigation

// 画面遷移ルート定義
// 新しい画面を追加する場合はここにルートを追加する
sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object AddSubscription : Screen("add_subscription")
    data object Analytics : Screen("analytics")
    data object Calendar : Screen("calendar")
    data object Settings : Screen("settings")
}
```

**注意点**:
- `sealed class` で型安全にルートを管理する（`enum class` ではなく `sealed class` を採用する理由: 将来パラメータ付きルート（例: `data class Detail(val id: Long) : Screen("detail/$id")`）に対応できるため）
- `data object` を使用する（Kotlin 1.9+ で推奨。`object` でも可だが `data object` で `toString()` が自動実装される）
- MVP で実際に NavGraph に登録するのは `Home` と `AddSubscription` のみ
- 将来の画面追加に備えて Analytics / Calendar / Settings も定義しておく

> **TODO**: AGENTS.md のファイル構成に `navigation/Screen.kt` を追記する必要あり（本タスクのスコープ外）。

---

## タスク2: SubKiruNavGraph.kt の作成

**ファイル**: `app/src/main/java/com/subkiru/subkiru/navigation/SubKiruNavGraph.kt`

以下は **ファイル全体の完全な最終形**（1つのコードブロックにまとめて記載）:

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
            // Home 画面（次タスクで実装。現時点ではプレースホルダー）
            HomePlaceholder()
        }
        composable(Screen.AddSubscription.route) {
            // サブスク追加画面（後続タスクで実装。現時点ではプレースホルダー）
            AddSubscriptionPlaceholder()
        }
    }
}

// プレースホルダー Composable（後続タスクで実際の Screen に差し替える）
@Composable
private fun HomePlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "Home")
    }
}

@Composable
private fun AddSubscriptionPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "Add Subscription")
    }
}
```

**注意点**:
- `navController` と `modifier` を外部から受け取る（テスタビリティとレイアウト柔軟性のため）
- `startDestination` は `Screen.Home.route`
- プレースホルダーは `private` にする（外部から使用しない）
- プレースホルダーは最小限の内容（画面中央にテキスト表示のみ）

---

## タスク3: MainActivity.kt の改修

**ファイル**: `app/src/main/java/com/subkiru/subkiru/MainActivity.kt`

テンプレートの `Greeting` / `GreetingPreview` を削除し、NavGraph を統合する。

```kotlin
package com.subkiru.subkiru

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.subkiru.subkiru.navigation.SubKiruNavGraph
import com.subkiru.subkiru.ui.theme.SubKiruTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SubKiruTheme {
                val navController = rememberNavController()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
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
- `Greeting` Composable を削除
- `GreetingPreview` Composable を削除
- `rememberNavController()` で NavController を生成
- `Scaffold` の `innerPadding` を `SubKiruNavGraph` の `modifier` に渡す
- `navController` を `Scaffold` の外で生成する（将来 `Scaffold` の `bottomBar` に `NavigationBar` を配置する際、`navController.currentBackStackEntryAsState()` で現在のルートを取得する必要があるため、`navController` は `Scaffold` と同じスコープで生成する）

**参考: 既存コードから削除される import**（上記の最終形コードが正。このリストは差分確認用）:
- `androidx.compose.material3.Text`
- `androidx.compose.runtime.Composable`
- `androidx.compose.ui.tooling.preview.Preview`

---

## 検証
- `./gradlew.bat assembleDebug` が成功すること
- アプリ起動時に画面中央に「Home」テキストが表示されること
- テンプレートの Greeting / GreetingPreview が完全に除去されていること
