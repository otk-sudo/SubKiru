package com.subkiru.subkiru.navigation

// 編集画面のルート引数名
const val ARG_SUBSCRIPTION_ID = "subscriptionId"

// 画面遷移ルート定義
// 新しい画面を追加する場合はここにルートを追加する
sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object AddSubscription : Screen("add_subscription")
    data object EditSubscription : Screen("edit_subscription/{$ARG_SUBSCRIPTION_ID}") {
        fun createRoute(subscriptionId: Long): String = "edit_subscription/$subscriptionId"
    }
    data object Analytics : Screen("analytics")
    data object Calendar : Screen("calendar")
    data object Settings : Screen("settings")
}
