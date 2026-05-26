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
