package com.subkiru.subkiru.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
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
    ANALYTICS(Screen.Analytics, "分析", Icons.AutoMirrored.Filled.ShowChart),
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
                            // バックスタックが積み上がらないようにする。
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
