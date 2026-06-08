package com.fitcoach.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.fitcoach.app.presentation.navigation.AppNavHost
import com.fitcoach.app.presentation.navigation.Screen
import com.fitcoach.app.presentation.theme.FitCoachColors
import com.fitcoach.app.presentation.theme.FitCoachTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FitCoachTheme {
                MainAppContent()
            }
        }
    }
}

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector
)

private val bottomNavItems = listOf(
    BottomNavItem(Screen.Dashboard, "Главная", Icons.Default.Home),
    BottomNavItem(Screen.WorkoutHistory, "Тренировки", Icons.Default.FitnessCenter),
    BottomNavItem(Screen.Nutrition, "Питание", Icons.Default.Restaurant),
    BottomNavItem(Screen.Water, "Вода", Icons.Default.WaterDrop),
    BottomNavItem(Screen.Progress, "Прогресс", Icons.Default.TrendingUp)
)

@Composable
private fun MainAppContent() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = bottomNavItems.any { item ->
        currentDestination?.hierarchy?.any { it.route == item.screen.route } == true
    }

    Scaffold(
        containerColor = FitCoachColors.Background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = FitCoachColors.Surface,
                    tonalElevation = 0.dp
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any { it.route == item.screen.route } == true
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            selected = selected,
                            onClick = {
                                navController.navigate(item.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = FitCoachColors.Accent,
                                selectedTextColor = FitCoachColors.Accent,
                                unselectedIconColor = FitCoachColors.TextMuted,
                                unselectedTextColor = FitCoachColors.TextMuted,
                                indicatorColor = FitCoachColors.Accent.copy(alpha = 0.1f)
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        AppNavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }
}
