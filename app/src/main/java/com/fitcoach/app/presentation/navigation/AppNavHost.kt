package com.fitcoach.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.fitcoach.app.presentation.screens.chat.ChatScreen
import com.fitcoach.app.presentation.screens.chef.FoodPhotoScreen
import com.fitcoach.app.presentation.screens.chef.MealPlanScreen
import com.fitcoach.app.presentation.screens.chef.ShoppingListScreen
import com.fitcoach.app.presentation.screens.club.ClubScreen
import com.fitcoach.app.presentation.screens.club.QrPassScreen
import com.fitcoach.app.presentation.screens.dashboard.DashboardScreen
import com.fitcoach.app.presentation.screens.nutrition.AddFoodScreen
import com.fitcoach.app.presentation.screens.nutrition.NutritionScreen
import com.fitcoach.app.presentation.screens.onboarding.OnboardingScreen
import com.fitcoach.app.presentation.screens.programs.ProgramsScreen
import com.fitcoach.app.presentation.screens.progress.ProgressScreen
import com.fitcoach.app.presentation.screens.settings.SettingsScreen
import com.fitcoach.app.presentation.screens.water.WaterScreen
import com.fitcoach.app.presentation.screens.workout.active.WorkoutActiveScreen
import com.fitcoach.app.presentation.screens.workout.detail.WorkoutDetailScreen
import com.fitcoach.app.presentation.screens.workout.history.WorkoutHistoryScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier,
    onOnboardingFinished: () -> Unit = {}
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onFinished = {
                    onOnboardingFinished()
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onStartWorkout = { workoutId -> navController.navigate(Screen.WorkoutActive.createRoute(workoutId)) },
                onOpenChat = { navController.navigate(Screen.Chat.route) },
                onOpenSettings = { navController.navigate(Screen.Settings.route) },
                onOpenWater = { navController.navigate(Screen.Water.route) },
                onOpenQrPass = { navController.navigate(Screen.QrPass.route) },
                onOpenPrograms = { navController.navigate(Screen.Programs.route) },
                onOpenNutrition = {
                    // Как переключение вкладки в нижней навигации (см. MainActivity).
                    navController.navigate(Screen.Nutrition.route) {
                        popUpTo(Screen.Dashboard.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }

        composable(
            route = Screen.WorkoutActive.route,
            arguments = listOf(navArgument("workoutId") { type = NavType.StringType })
        ) { backStack ->
            val workoutId = backStack.arguments?.getString("workoutId") ?: return@composable
            WorkoutActiveScreen(
                workoutId = workoutId,
                onFinished = {
                    // Dashboard всегда есть в стеке под тренировкой: возвращаемся к нему,
                    // не создавая новый экземпляр (иначе Back открывал бы завершённую тренировку).
                    if (!navController.popBackStack(Screen.Dashboard.route, inclusive = false)) {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Dashboard.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                },
                onOpenChat = { navController.navigate(Screen.Chat.route) }
            )
        }

        composable(
            route = Screen.WorkoutDetail.route,
            arguments = listOf(navArgument("workoutId") { type = NavType.StringType })
        ) { backStack ->
            val workoutId = backStack.arguments?.getString("workoutId") ?: return@composable
            WorkoutDetailScreen(workoutId = workoutId, onBack = { navController.popBackStack() })
        }

        composable(Screen.WorkoutHistory.route) {
            WorkoutHistoryScreen(
                onWorkoutClick = { id -> navController.navigate(Screen.WorkoutDetail.createRoute(id)) },
                onOpenPrograms = { navController.navigate(Screen.Programs.route) }
            )
        }

        composable(Screen.Programs.route) {
            ProgramsScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Nutrition.route) {
            NutritionScreen(
                onAddFood = { mealType -> navController.navigate(Screen.AddFood.createRoute(mealType)) },
                onOpenWater = { navController.navigate(Screen.Water.route) },
                onOpenFoodPhoto = { navController.navigate(Screen.FoodPhoto.route) },
                onOpenMealPlan = { navController.navigate(Screen.MealPlan.route) },
                onOpenShoppingList = { navController.navigate(Screen.ShoppingList.route) }
            )
        }

        composable(Screen.MealPlan.route) {
            MealPlanScreen(
                onBack = { navController.popBackStack() },
                onOpenShoppingList = { navController.navigate(Screen.ShoppingList.route) { launchSingleTop = true } }
            )
        }

        composable(Screen.ShoppingList.route) {
            ShoppingListScreen(
                onBack = { navController.popBackStack() },
                onOpenMealPlan = {
                    // Из списка к плану: если план уже в стеке — возвращаемся, иначе открываем.
                    if (!navController.popBackStack(Screen.MealPlan.route, inclusive = false)) {
                        navController.navigate(Screen.MealPlan.route) { launchSingleTop = true }
                    }
                }
            )
        }

        composable(Screen.FoodPhoto.route) {
            FoodPhotoScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.AddFood.route,
            arguments = listOf(navArgument("mealType") { type = NavType.StringType })
        ) { backStack ->
            val mealType = backStack.arguments?.getString("mealType") ?: "SNACK"
            AddFoodScreen(mealType = mealType, onBack = { navController.popBackStack() })
        }

        composable(Screen.Water.route) {
            WaterScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Chat.route) {
            ChatScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Progress.route) {
            ProgressScreen()
        }

        composable(Screen.Club.route) {
            ClubScreen(
                onOpenQrPass = { navController.navigate(Screen.QrPass.route) },
                onOpenChat = { navController.navigate(Screen.Chat.route) }
            )
        }

        composable(Screen.QrPass.route) {
            QrPassScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onRestartOnboarding = {
                    navController.navigate(Screen.Onboarding.route) {
                        // В первой сессии start destination графа — Onboarding, которого уже нет в стеке;
                        // чистим стек по маршруту Dashboard (он всегда в корне после онбординга).
                        popUpTo(Screen.Dashboard.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
