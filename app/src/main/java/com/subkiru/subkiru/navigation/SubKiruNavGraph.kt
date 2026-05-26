package com.subkiru.subkiru.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.subkiru.subkiru.SubKiruApplication
import com.subkiru.subkiru.feature.add.AddSubscriptionScreen
import com.subkiru.subkiru.feature.add.AddSubscriptionViewModel
import com.subkiru.subkiru.feature.analytics.AnalyticsScreen
import com.subkiru.subkiru.feature.analytics.AnalyticsViewModel
import com.subkiru.subkiru.feature.calendar.CalendarScreen
import com.subkiru.subkiru.feature.calendar.CalendarViewModel
import com.subkiru.subkiru.feature.home.HomeScreen
import com.subkiru.subkiru.feature.home.HomeViewModel
import com.subkiru.subkiru.feature.settings.SettingsScreen
import com.subkiru.subkiru.feature.settings.SettingsViewModel

@Composable
fun SubKiruNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val app = SubKiruApplication.from(LocalContext.current)

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
            val viewModel: AddSubscriptionViewModel = viewModel(
                factory = AddSubscriptionViewModel.factory(app),
            )
            AddSubscriptionScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable(Screen.Analytics.route) {
            val viewModel: AnalyticsViewModel = viewModel(
                factory = AnalyticsViewModel.factory(app),
            )
            AnalyticsScreen(viewModel = viewModel)
        }
        composable(Screen.Calendar.route) {
            val viewModel: CalendarViewModel = viewModel(
                factory = CalendarViewModel.factory(app),
            )
            CalendarScreen(viewModel = viewModel)
        }
        composable(Screen.Settings.route) {
            val viewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.factory(app),
            )
            SettingsScreen(viewModel = viewModel)
        }
    }
}
