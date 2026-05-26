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
                val showBottomBar = currentRoute != Screen.AddSubscription.route

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (showBottomBar) {
                            BottomNavBar(navController = navController)
                        }
                    },
                    floatingActionButton = {
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
