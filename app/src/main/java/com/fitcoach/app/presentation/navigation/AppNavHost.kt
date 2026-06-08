package com.fitcoach.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.fitcoach.app.presentation.screens.chat.ChatScreen
import com.fitcoach.app.presentation.screens.dashboard.DashboardScreen
import com.fitcoach.app.presentation.screens.nutrition.AddFoodScreen
import com.fitcoach.app.presentation.screens.nutrition.NutritionScreen
import com.fitcoach.app.presentation.screens.progress.ProgressScreen
import com.fitcoach.app.presentation.screens.settings.SettingsScreen
import com.fitcoach.app.presentation.screens.water.WaterScreen
import com.fitcoach.app.presentation.screens.workout.active.WorkoutActiveScreen
import com.fitcoach.app.presentation.screens.workout.history.WorkoutHistoryScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onStartWorkout = { workoutId -> navController.navigate(Screen.WorkoutActive.createRoute(workoutId)) },
                onOpenChat = { navController.navigate(Screen.Chat.route) },
                onOpenSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(
            route = Screen.WorkoutActive.route,
            arguments = listOf(navArgument("workoutId") { type = NavType.StringType })
        ) { backStack ->
            val workoutId = backStack.arguments?.getString("workoutId") ?: return@composable
            WorkoutActiveScreen(
                workoutId = workoutId,
                onFinished = { navController.popBackStack() }
            )
        }

        composable(Screen.WorkoutHistory.route) {
            WorkoutHistoryScreen(
                onWorkoutClick = { id -> navController.navigate(Screen.WorkoutDetail.createRoute(id)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Nutrition.route) {
            NutritionScreen(
                onAddFood = { mealType -> navController.navigate(Screen.AddFood.createRoute(mealType)) }
            )
        }

        composable(
            route = Screen.AddFood.route,
            arguments = listOf(navArgument("mealType") { type = NavType.StringType })
        ) { backStack ->
            val mealType = backStack.arguments?.getString("mealType") ?: "SNACK"
            AddFoodScreen(
                mealType = mealType,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Water.route) {
            WaterScreen()
        }

        composable(Screen.Chat.route) {
            ChatScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Progress.route) {
            ProgressScreen()
        }

        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
